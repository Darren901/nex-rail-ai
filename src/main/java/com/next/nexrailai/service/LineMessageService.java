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
     * Send a single push message to a specific LINE user.
     *
     * @param userId  the target user's LINE userId
     * @param message the message to send
     */
    public void pushMessage(String userId, Message message) {
        pushMessages(userId, List.of(message));
    }
    
    /**
     * Pushes multiple messages to a LINE user.
     *
     * @param userId   the target user's LINE ID
     * @param messages the messages to send to the user
     * @throws RuntimeException if sending the messages fails
     */
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
     * Sends a single reply message using the provided reply token.
     *
     * @param replyToken the reply token received from a LINE webhook identifying the reply target
     * @param message the message to send as the reply
     */
    public void reply(String replyToken, Message message) {
        reply(replyToken, List.of(message));
    }

    /**
     * Sends one or more reply messages to a LINE webhook event identified by the reply token.
     *
     * @param replyToken the LINE reply token received from the webhook for the event to reply to
     * @param messages   the list of messages to send in the reply
     * @throws RuntimeException if sending the reply fails
     */
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
     * Send a single message to multiple users.
     *
     * @param userIds a list of target user IDs; if null or empty the method returns without sending
     * @param message the message to send to each target user
     */
    public void multicast(List<String> userIds, Message message) {
        multicast(userIds, List.of(message));
    }

    /**
     * Sends the given messages to multiple LINE users using the Multicast API.
     *
     * If {@code userIds} is null or empty this method returns without sending anything.
     * Note: the LINE Multicast API limits requests to 500 users per call.
     *
     * @param userIds the list of target user IDs; no action is taken if null or empty
     * @param messages the messages to send to each target user
     * @throws RuntimeException if the multicast request fails
     */
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
         * Requests the LINE client to display a loading animation to the specified user for a given duration.
         *
         * <p>If the request fails, the failure is logged and the method returns without throwing.</p>
         *
         * @param userId  the target user's LINE ID
         * @param seconds the duration of the loading animation in seconds (positive integer)
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
     * Retrieve the LINE user profile for the specified user ID.
     *
     * @param userId the LINE user ID whose profile to retrieve
     * @return the user's profile as a {@code UserProfileResponse}, or {@code null} if retrieval fails
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