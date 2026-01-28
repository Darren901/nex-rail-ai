package com.next.nexrailai.service;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class LineMessageService {

    private final MessagingApiClient messagingApiClient;

    /**
     * 推播訊息 (Push Message)
     */
    public void pushMessage(String userId, Message message) {
        pushMessages(userId, List.of(message));
    }
    
    public void pushMessages(String userId, List<Message> messages) {
        try {
            messagingApiClient.pushMessage(
                    UUID.randomUUID(),
                    new PushMessageRequest.Builder(userId, messages).build()
            ).join();
            log.debug(">>>> [LineMessageService] Successfully pushed {} messages to user: {}", messages.size(), userId);
        } catch (Exception e) {
            log.error(">>>> [LineMessageService] Push Messages Failed for User: {}", userId, e);
            throw new RuntimeException("Failed to push message", e);
        }
    }

    /**
     * 回覆訊息 (Reply Message)
     */
    public void reply(String replyToken, Message message) {
        reply(replyToken, List.of(message));
    }

    public void reply(String replyToken, List<Message> messages) {
        try {
            messagingApiClient.replyMessage(
                    new ReplyMessageRequest.Builder(replyToken, messages).build()
            ).join();
            log.debug(">>>> [LineMessageService] Successfully replied with {} messages", messages.size());
        } catch (Exception e) {
            log.error(">>>> [LineMessageService] Reply Message Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to reply message", e);
        }
    }

    /**
     * 群播訊息 (Multicast)
     */
    public void multicast(List<String> userIds, Message message) {
        multicast(userIds, List.of(message));
    }

    public void multicast(List<String> userIds, List<Message> messages) {
        if (userIds == null || userIds.isEmpty()) return;
        try {
            // Note: LINE Multicast API has a limit of 500 users per request.
            messagingApiClient.multicast(
                    UUID.randomUUID(),
                    new MulticastRequest.Builder(messages, userIds).build()
            ).join();
            log.info(">>>> [LineMessageService] Successfully multicast to {} users", userIds.size());
        } catch (Exception e) {
            log.error(">>>> [LineMessageService] Multicast Failed: {}", e.getMessage(), e);
            throw new RuntimeException("Multicast failed", e);
        }
    }

    /**
     * 顯示 Loading 動畫
     */
    public void showLoadingAnimation(String userId, int seconds) {
        try {
            messagingApiClient.showLoadingAnimation(
                    new ShowLoadingAnimationRequest.Builder(userId).loadingSeconds(seconds).build()
            ).join();
        } catch (Exception e) {
            log.warn(">>>> [LineMessageService] Failed to show loading animation for user: {}", userId);
        }
    }

    /**
     * 取得使用者 Profile
     */
    public UserProfileResponse getUserProfile(String userId) {
        try {
            return messagingApiClient.getProfile(userId)
                    .join()
                    .body();
        } catch (Exception e) {
            log.error(">>>> [LineMessageService] Failed to get user profile: {}", e.getMessage());
            return null;
        }
    }
}
