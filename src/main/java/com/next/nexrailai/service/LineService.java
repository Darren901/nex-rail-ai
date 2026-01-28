package com.next.nexrailai.service;

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

    private final LineMessageService lineMessageService;
    private final AiService aiService;
    private final ScheduleService scheduleService;
    private final UserMemoryService userMemoryService;
    private final RateLimitService rateLimitService;

    /**
     * Fetches the LINE user profile for the given user ID.
     *
     * @return the user's profile information as a UserProfileResponse
     */
    public UserProfileResponse getUserProfile(String userId) {
        return lineMessageService.getUserProfile(userId);
    }

    /**
     * Handles an inbound user message by routing commands, enforcing rate limits, invoking the AI chat flow, and replying.
     *
     * <p>Behavior:
     * - If the message starts with "/" it is treated as a command and delegated to handleCommand.
     * - If the user has exhausted their quota, sends a predefined quota-exceeded text reply and returns.
     * - Otherwise, shows a loading indicator, clears transient context, obtains an AI response, decides the appropriate reply
     *   (text, booking bubble, timetable/price carousel, etc.), sends the reply, and clears transient context again.</p>
     *
     * @param userId     the identifier of the user who sent the message
     * @param message    the raw message text received from the user
     * @param replyToken the LINE reply token used to send a direct reply to the incoming event
     */
    public void handleUserMessage(String userId, String message, String replyToken) {
        // 0. 指令攔截
        if (message.startsWith("/")) {
            handleCommand(userId, message, replyToken);
            return;
        }

        // 1. 檢查額度
        if (!rateLimitService.tryConsume(userId)) {
            reply(replyToken, new TextMessage
                    .Builder("$ 今日訊息額度已用完 (0/10) \n請明天再來，或升級您的方案 (其實是請開發者喝咖啡 $)")
                    .emojis(List.of(
                            new Emoji(0, "670e0cce840a8236ddd4ee4c", "065"),
                            new Emoji(46, "670e0cce840a8236ddd4ee4c", "142")
                    )).build());
            return;
        }

        // 2. 顯示 Loading
        showLoading(userId);

        // 3. AI 處理
        ThsrContextHolder.clear();
        String replyMessage = aiService.chat(userId, message);
        ThsrContextHolder.ThsrSearchResult searchResult = ThsrContextHolder.get();

        // 4. 決定回覆訊息
        Message messageToSend = decideMessage(replyMessage, message, searchResult);

        // 5. 回覆
        reply(replyToken, messageToSend);

        // 6. 清理
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

    /**
     * Handles a LINE postback payload by parsing key=value parameters and executing the specified action,
     * then sends a reply message when the action produces an immediate text response.
     *
     * Supported actions parsed from the `data` string:
     * - "cancel_task" — cancels the scheduled task identified by `id` for the given user and replies with a confirmation.
     * - "delete_memory" — deletes the user memory identified by `id` and replies with a confirmation.
     * - "use_memory" — retrieves the user memory identified by `id` and triggers a follow-up user message (no immediate reply from this method).
     * - any other action — treated as successful and replied to with a generic success text.
     *
     * @param userId     the LINE user ID on whose behalf the action is performed
     * @param data       postback payload formatted as URL-style parameters (e.g., "action=cancel_task&id=123"); this method reads the "action" and optional "id" keys
     * @param replyToken the reply token to use when sending a reply message (or to pass through to subsequent message handling)
     */
    public void handlePostback(String userId, String data, String replyToken) {
        log.info(">>>> [LINE Service] 處理 Postback: {} for User: {}", data, userId);

        Map<String, String> params = Stream.of(data.split("&"))
                .map(s -> s.split("="))
                .collect(Collectors.toMap(a -> a[0], a -> a.length > 1 ? a[1] : ""));

        String action = params.get("action");
        String idStr = params.get("id");
        Long id = idStr != null ? Long.parseLong(idStr) : null;

        String resultText = switch (action) {
            case "cancel_task" -> {
                scheduleService.cancelTask(id, userId);
                yield "已取消該項提醒/監控任務。";
            }
            case "delete_memory" -> {
                userMemoryService.deleteMemory(id, userId);
                yield "已刪除該項常用行程。";
            }
            case "use_memory" -> {
                userMemoryService.getMemory(id, userId).ifPresent(m -> {
                    String aiMessage = "請根據我儲存的行程資訊幫我查詢班次：" + m.getMemoryValue();
                    handleUserMessage(userId, aiMessage, replyToken);
                });
                yield null; // 已經在內部處理了，不需要回傳 text
            }
            default -> "操作成功";
        };

        if (resultText != null) {
            reply(replyToken, new TextMessage(resultText));
        }
    }

    /**
     * Selects the LINE Message to send based on AI reply and search results.
     *
     * @param aiReply       AI-generated text used as a fallback reply.
     * @param originalInput The user's original message; used to detect price-related inquiries.
     * @param searchResult  Search result data that may contain a booking link, train list, fares, and schedule details; may be null.
     * @return              A Message to send: a booking confirmation bubble if a booking link is present; a price info bubble when the input is a price inquiry and bubble creation succeeds; a timetable carousel when train data is available; otherwise a TextMessage containing `aiReply`.
     */
    private Message decideMessage(String aiReply, String originalInput, ThsrContextHolder.ThsrSearchResult searchResult) {
        // 1. 優先檢查是否有訂票連結 (Booking Link)
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
        }

        // 2. 檢查是否有班次資料 (Timetable / Price)
        if (searchResult != null && searchResult.getTrains() != null && !searchResult.getTrains().isEmpty()) {
            
            // 如果是詢問票價
            if (isPriceInquiry(originalInput)) {
                Message priceFlex = FlexMessageUtil.createPriceInfoBubble(
                        searchResult.getOrigin(),
                        searchResult.getDestination(),
                        searchResult.getTrains().getFirst().fares()
                );
                // 如果 Flex 建構失敗 (null)，則 Fallback 回傳 AI 文字
                return Objects.requireNonNullElseGet(priceFlex, () -> new TextMessage(aiReply));
            }
            
            // 一般時刻表查詢
            return FlexMessageUtil.createTimetableCarousel(
                    searchResult.getTrains(),
                    searchResult.getOrigin(),
                    searchResult.getDestination(),
                    searchResult.getTrainDate()
            );
        }

        // 3. 預設：回覆 AI 的純文字內容
        return new TextMessage(aiReply);
    }

    /**
     * Displays a loading animation to the specified LINE user.
     *
     * @param userId the LINE user ID to show the loading animation for
     */
    private void showLoading(String userId) {
        lineMessageService.showLoadingAnimation(userId, 20);
    }

    /**
     * Send a plain text reply message addressing the given reply token.
     *
     * @param replyToken the reply token identifying the conversation to respond to
     * @param text       the text content to send as the reply
     */
    public void replyText(String replyToken, String text) {
        reply(replyToken, new TextMessage(text));
    }

    /**
     * Sends the given reply message to the user identified by the reply token via the LINE messaging service.
     *
     * @param replyToken the reply token received from LINE to route the response to the originating event
     * @param message    the Message payload to send as the reply
     */
    private void reply(String replyToken, Message message) {
        lineMessageService.reply(replyToken, message);
    }

    /**
     * Determine whether the input text inquires about ticket price or fare.
     *
     * @param text the input text to inspect
     * @return `true` if the text contains any ticket- or price-related keyword, `false` otherwise
     */
    private boolean isPriceInquiry(String text) {
        List<String> priceKeywords = List.of(
                "票價", "多少錢", "費用", "價格", "售價",
                "法優",
                "全票", "半票", "優待票", "兒童票", "敬老票", "愛心票", "軍警票", "學生"
        );
        return priceKeywords.stream().anyMatch(text::contains);
    }
}