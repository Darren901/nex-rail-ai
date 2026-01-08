package com.next.nexrailai.service;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.*;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.jpa.entity.ScheduleTask;
import com.next.nexrailai.jpa.entity.UserMemory;
import com.next.nexrailai.jpa.service.UserMemoryService;
import com.next.nexrailai.utils.FlexMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineService {

    @Value("${app.base-url}")
    private String baseUrl;

    private final MessagingApiClient messagingApiClient;
    private final AiService aiService;
    private final ScheduleService scheduleService;
    private final UserMemoryService userMemoryService;
    private final RateLimitService rateLimitService;

    public UserProfileResponse getUserProfile(String userId) {
        try {
            return messagingApiClient.getProfile(userId)
                    .join()
                    .body();
        } catch (Exception e) {
            log.error(">>>> [LINE Service] 取得使用者檔案失敗: {}", e.getMessage());
            return null;
        }
    }

    public void handleUserMessage(String userId, String message, String replyToken) {
        // 0. 指令攔截 (例如 /tasks/reminder) - 指令不扣額度
        if (message.startsWith("/")) {
            handleCommand(userId, message, replyToken);
            return;
        }

        // 1. 檢查額度
        if (!rateLimitService.tryConsume(userId)) {
            reply(replyToken, new TextMessage
                    .Builder("$ 今日訊息額度已用完 (10/10) \n請明天再來，或升級您的方案 (其實是請開發者喝咖啡 $)")
                    .emojis(List.of(
                            new Emoji(0, "670e0cce840a8236ddd4ee4c", "065"),
                            new Emoji(47, "670e0cce840a8236ddd4ee4c", "142")
                    )).build());
            return;
        }


        // 2. 顯示 Loading 動畫
        showLoading(userId);

        // 3. 清空 Context 並 Call AI
        ThsrContextHolder.clear();
        String replyMessage = aiService.chat(userId, message);
        ThsrContextHolder.ThsrSearchResult searchResult = ThsrContextHolder.get();

        // 4. 決定回覆訊息 (Flex or Text)
        Message messageToSend = decideMessage(replyMessage, message, searchResult);

        // 5. 回覆訊息
        reply(replyToken, messageToSend);

        // 6. 清理資源
        ThsrContextHolder.clear();
    }

    private void handleCommand(String userId, String command, String replyToken) {
        log.info(">>>> [LINE Service] 處理指令: {} for User: {}", command, userId);
        Message replyMessage;

        switch (command) {
            case "/tasks/reminder" -> {
                List<ScheduleTask> tasks = scheduleService.getTasksByUserAndType(userId, ScheduleTask.TaskType.REMINDER);
                replyMessage = FlexMessageUtil.createTaskListBubble("待辦提醒", tasks);
            }
            case "/tasks/monitor" -> {
                List<ScheduleTask> tasks = scheduleService.getTasksByUserAndType(userId, ScheduleTask.TaskType.TICKET_MONITOR);
                replyMessage = FlexMessageUtil.createTaskListBubble("搶票監控", tasks);
            }
            case "/memory/list" -> {
                List<UserMemory> memories = userMemoryService.getMemoriesByUser(userId);
                replyMessage = FlexMessageUtil.createMemoryListBubble(memories);
            }
            default -> replyMessage = new TextMessage("未知的指令：" + command);
        }

        reply(replyToken, replyMessage);
    }

    public void handlePostback(String userId, String data, String replyToken) {
        log.info(">>>> [LINE Service] 處理 Postback: {} for User: {}", data, userId);
        
        // 解析 data: action=cancel_task&id=123
        Map<String, String> params = Stream.of(data.split("&"))
                .map(s -> s.split("="))
                .collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));

        String action = params.get("action");
        String idStr = params.get("id");
        Long id = idStr != null ? Long.parseLong(idStr) : null;

        String resultText = "操作成功";

        if ("cancel_task".equals(action)) {
            scheduleService.cancelTask(id, userId);
            resultText = "已取消該項提醒/監控任務。";
        } else if ("delete_memory".equals(action)) {
            userMemoryService.deleteMemory(id, userId);
            resultText = "已刪除該項常用行程。";
        } else if ("use_memory".equals(action)) {
            // 使用記憶：提取記憶內容並交給 AI
            userMemoryService.getMemory(id, userId).ifPresent(m -> {
                // 這裡我們直接把記憶的內容 (JSON) 丟給 AI，並附上一個 Prompt
                String aiMessage = "請根據我儲存的行程資訊幫我查詢班次：" + m.getMemoryValue();
                handleUserMessage(userId, aiMessage, replyToken);
            });
            return; // handleUserMessage 會處理 reply
        }

        reply(replyToken, new TextMessage(resultText));
    }

    private Message decideMessage(String aiReply, String originalInput, ThsrContextHolder.ThsrSearchResult searchResult) {
        if (searchResult != null && searchResult.getBookingLink() != null) {
            return FlexMessageUtil.createBookingConfirmationBubble(
                    baseUrl,
                    searchResult.getBookingLink(),
                    searchResult.getOrigin(),
                    searchResult.getDestination(),
                    searchResult.getTrainDate(),
                    searchResult.getTrainTime(),
                    searchResult.getTrainNumber()
            );
        } else if (searchResult != null && searchResult.getTrains() != null && !searchResult.getTrains().isEmpty()) {
            if (isPriceInquiry(originalInput)) {
                Message priceFlexMessage = FlexMessageUtil.createPriceInfoBubble(
                        searchResult.getOrigin(),
                        searchResult.getDestination(),
                        searchResult.getTrains().getFirst().fares()
                );
                return Objects.requireNonNullElseGet(priceFlexMessage, () -> new TextMessage(aiReply));
            } else {
                return FlexMessageUtil.createTimetableCarousel(
                        searchResult.getTrains(),
                        searchResult.getOrigin(),
                        searchResult.getDestination()
                );
            }
        }
        return new TextMessage(aiReply);
    }

    private void showLoading(String userId) {
        try {
            messagingApiClient.showLoadingAnimation(new ShowLoadingAnimationRequest.Builder(userId).loadingSeconds(20).build()).join();
        } catch (Exception e) {
            log.warn(">>>> [LINE Service] Loading 動畫顯示失敗");
        }
    }

    private void reply(String replyToken, Message message) {
        try {
            messagingApiClient.replyMessage(new ReplyMessageRequest.Builder(replyToken, List.of(message)).build()).join();
        } catch (Exception e) {
            log.error(">>>> [LINE Service] 回覆訊息失敗: {}", e.getMessage(), e);
        }
    }

    private boolean isPriceInquiry(String text) {
        List<String> priceKeywords = List.of(
                "票價", "多少錢", "費用", "價格", "售價",
                "法優", // 特殊案例
                "全票", "半票", "優待票", "兒童票", "敬老票", "愛心票", "軍警票", "學生"
        );
        return priceKeywords.stream().anyMatch(text::contains);
    }
}
