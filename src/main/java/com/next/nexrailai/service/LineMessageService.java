package com.next.nexrailai.service;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.Message;
import com.linecorp.bot.messaging.model.PushMessageRequest;
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

    public void pushMessage(String userId, Message message) {
        try {
            messagingApiClient.pushMessage(
                    UUID.randomUUID(),
                    new PushMessageRequest.Builder(userId, List.of(message)).build()
            ).join();
            log.debug(">>>> [LineMessageService] Successfully pushed message to user: {}", userId);
        } catch (Exception e) {
            log.error(">>>> [LineMessageService] Push Message Failed for User: {}", userId, e);
            throw new RuntimeException("Failed to push message", e);
        }
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
}
