package com.next.nexrailai.handler;

import com.linecorp.bot.messaging.model.UserProfileResponse;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.FollowEvent;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import com.linecorp.bot.webhook.model.UnfollowEvent;
import com.linecorp.bot.webhook.model.PostbackEvent;
import com.next.nexrailai.jpa.service.AppUserService;
import com.next.nexrailai.service.LineService;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@LineMessageHandler
@RequiredArgsConstructor
@Slf4j
public class LineBotMessageHandler {

    private final AppUserService appUserService;
    private final LineService lineService;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {
        log.debug(">>>>> [LINE Handler] Message event: {}", JsonUtil.prettyJson(event));

        final String originalMessageText = ((TextMessageContent) event.message()).text();
        log.info(">>>>> [LINE Handler] Received message: [{}]", originalMessageText);
        final String userId = event.source().userId();

        lineService.handleUserMessage(userId, originalMessageText, event.replyToken());
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) {
        log.info(">>>> [LINE Handler] Received postback event: {}", event.postback().data());
        lineService.handlePostback(event.source().userId(), event.postback().data(), event.replyToken());
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) {
        final String userId = event.source().userId();
        log.info(">>>> [LINE Handler] 偵測到 Follow 事件 UserId: {}", userId);

        UserProfileResponse profile = lineService.getUserProfile(userId);

        if (profile != null) {
            appUserService.saveOrUpdateUser(profile);
        }
    }

    @EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        final String userId = event.source().userId();
        log.info(">>>> [LINE Handler] 偵測到 Unfollow 事件 UserId: {}", userId);

        appUserService.setUserDisable(userId);
    }
}
