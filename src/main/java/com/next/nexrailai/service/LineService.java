package com.next.nexrailai.service;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.*;
import com.next.nexrailai.context.ThsrContextHolder;
import com.next.nexrailai.utils.FlexMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class LineService {

    @Value("${app.base-url}")
    private String baseUrl;

    private final MessagingApiClient messagingApiClient;
    private final AiService aiService;

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
        // 1. 顯示 Loading 動畫
        try {
            messagingApiClient.showLoadingAnimation(
                    new ShowLoadingAnimationRequest
                            .Builder(userId)
                            .loadingSeconds(20)
                            .build()
            ).join();
        } catch (Exception e) {
            log.warn(">>>> [LINE Service] Loading 動畫顯示失敗: {}", e.getMessage());
        }

        // 2. 清空 Context 並 Call AI
        ThsrContextHolder.clear();
        String replyMessage = aiService.chat(userId, message);
        ThsrContextHolder.ThsrSearchResult searchResult = ThsrContextHolder.get();

        // 3. 決定回覆訊息 (Flex or Text)
        Message messageToSend;

        if (searchResult != null && searchResult.getBookingLink() != null) {
            log.info(">>>> [LINE Service] 偵測到訂票連結，改為發送 Booking Flex Message");
            messageToSend = FlexMessageUtil.createBookingConfirmationBubble(
                    baseUrl,
                    searchResult.getBookingLink(),
                    searchResult.getOrigin(),
                    searchResult.getDestination(),
                    searchResult.getTrainDate(),
                    searchResult.getTrainTime(),
                    searchResult.getTrainNumber()
            );
        } else if (searchResult != null && searchResult.getTrains() != null && !searchResult.getTrains().isEmpty()) {
            // 只有在使用者不是詢問「票價」時，才顯示時刻表卡片
            if (isPriceInquiry(message)) {
                log.info(">>>> [LINE Service] 使用者詢問票價，嘗試產生 Flex Message");
                Message priceFlexMessage = FlexMessageUtil.createPriceInfoBubble(
                        searchResult.getOrigin(),
                        searchResult.getDestination(),
                        searchResult.getTrains().getFirst().fares()
                );

                // Fallback to text if fares are missing
                messageToSend = Objects.requireNonNullElseGet(priceFlexMessage, () -> new TextMessage(replyMessage));
            } else {
                log.info(">>>> [LINE Service] 偵測到班次查詢結果，改為發送 Timetable Flex Message");
                messageToSend = FlexMessageUtil.createTimetableCarousel(
                        searchResult.getTrains(),
                        searchResult.getOrigin(),
                        searchResult.getDestination()
                );
            }
        } else {
            messageToSend = new TextMessage(replyMessage);
        }

        log.debug(">>>>> [LINE Service] Sending reply message: {}", messageToSend);
        // 4. 回覆訊息
        try{
            messagingApiClient.replyMessage(
                    new ReplyMessageRequest.Builder(
                            replyToken,
                            List.of(messageToSend)
                    ).build()
            ).join();
        }catch (Exception e){
            log.error(">>>> [LINE Service] 回覆訊息失敗: {}", e.getMessage(), e);
        }

        // 5. 清理資源
        ThsrContextHolder.clear();
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
