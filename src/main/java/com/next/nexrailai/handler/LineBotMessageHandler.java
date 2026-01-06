package com.next.nexrailai.handler;

import com.linecorp.bot.messaging.client.MessagingApiClient;
import com.linecorp.bot.messaging.model.ReplyMessageRequest;
import com.linecorp.bot.messaging.model.ShowLoadingAnimationRequest;
import com.linecorp.bot.messaging.model.TextMessage;
import com.linecorp.bot.messaging.model.UserProfileResponse;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.*;
import com.next.nexrailai.service.AiService;
import com.next.nexrailai.service.AppUserService;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@LineMessageHandler
@RequiredArgsConstructor
@Slf4j
public class LineBotMessageHandler {

    private final MessagingApiClient messagingApiClient;
    private final AppUserService appUserService;
    private final AiService aiService;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {
        log.debug(">>>>> [Message Event] Received event: {}", JsonUtil.prettyJson(event) );

        final String originalMessageText = ((TextMessageContent) event.message()).text();
        final String userId = event.source().userId();

        // 1. 顯示 Loading 動畫
        try {
            messagingApiClient.showLoadingAnimation(
                    new ShowLoadingAnimationRequest
                            .Builder(userId)
                            .loadingSeconds(20)
                            .build()
            ).join();
        } catch (Exception e) {
            log.warn("Loading 動畫顯示失敗: {}", e.getMessage());
        }

        // 2. Call AI
        String replyMessage = aiService.chat(userId, originalMessageText);
        // 3. 回覆訊息
        messagingApiClient.replyMessage(
                new ReplyMessageRequest.Builder(
                        event.replyToken(),
                        List.of(new TextMessage(replyMessage))
                ).build()
        ).join();
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) {
        try{
            log.debug(">>>>> [Follow Event] Received event: {}", JsonUtil.prettyJson(event));

            String userId = event.source().userId();

            UserProfileResponse profile = messagingApiClient.getProfile(userId)
                    .join()
                    .body();

            if(profile != null){
                appUserService.saveOrUpdateUser(profile);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        log.debug(">>>>> [Unfollow Event] Received event: {}", JsonUtil.prettyJson(event) );
        String userId = event.source().userId();
        appUserService.setUserDisable(userId);
    }
}
