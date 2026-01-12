package com.next.nexrailai.handler;

import com.linecorp.bot.messaging.model.UserProfileResponse;
import com.linecorp.bot.spring.boot.handler.annotation.EventMapping;
import com.linecorp.bot.spring.boot.handler.annotation.LineMessageHandler;
import com.linecorp.bot.webhook.model.FollowEvent;
import com.linecorp.bot.webhook.model.MessageEvent;
import com.linecorp.bot.webhook.model.TextMessageContent;
import com.linecorp.bot.webhook.model.UnfollowEvent;
import com.linecorp.bot.webhook.model.PostbackEvent;
import com.next.nexrailai.jpa.entity.AppUser;
import com.next.nexrailai.jpa.service.AppUserService;
import com.next.nexrailai.service.LineService;
import com.next.nexrailai.service.SystemConfigService;
import com.next.nexrailai.utils.JsonUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@LineMessageHandler
@RequiredArgsConstructor
@Slf4j
public class LineBotMessageHandler {

    private final AppUserService appUserService;
    private final LineService lineService;
    private final SystemConfigService systemConfigService;

    @EventMapping
    public void handleTextMessageEvent(MessageEvent event) {
        log.debug(">>>>> [LINE Handler] Message event: {}", JsonUtil.prettyJson(event));

        final String userId = event.source().userId();
        
        // 檢查使用者狀態
        var status = appUserService.getUserStatus(userId);

        if (status == AppUser.UserStatus.PENDING) {
            lineService.replyText(event.replyToken(),
                    "⚠️ 您的帳號正在審核中\n\n為了確保服務品質，我們採取實名制審核。管理員將在確認後開通您的權限");
            return;
        } else if (status == AppUser.UserStatus.DISABLE) {
            lineService.replyText(event.replyToken(),
                    "⛔ 您的帳號已被停用\n\n為了確保服務品質，請聯絡管理員協助啟用您的帳號。");
            return;
        }

        final String originalMessageText = ((TextMessageContent) event.message()).text();
        log.info(">>>>> [LINE Handler] Received message: [{}]", originalMessageText);

        appUserService.setUserActiveAt(userId);
        lineService.handleUserMessage(userId, originalMessageText, event.replyToken());
    }

    @EventMapping
    public void handlePostbackEvent(PostbackEvent event) {
        log.info(">>>> [LINE Handler] Received postback event: {}", event.postback().data());
        appUserService.setUserActiveAt(event.source().userId());
        lineService.handlePostback(event.source().userId(), event.postback().data(), event.replyToken());
    }

    @EventMapping
    public void handleFollowEvent(FollowEvent event) {
        final String userId = event.source().userId();
        log.info(">>>> [LINE Handler] 偵測到 Follow 事件 UserId: {}", userId);

        UserProfileResponse profile = lineService.getUserProfile(userId);

        if (profile != null) {
            appUserService.saveOrUpdateUser(profile);
            String welcomeMsg = systemConfigService.get("welcome_message");
            lineService.replyText(event.replyToken(), welcomeMsg);
        }
    }

    @EventMapping
    public void handleUnfollowEvent(UnfollowEvent event) {
        final String userId = event.source().userId();
        log.info(">>>> [LINE Handler] 偵測到 Unfollow 事件 UserId: {}", userId);

        appUserService.setUserDisable(userId);
    }
}
