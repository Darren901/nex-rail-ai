package com.next.nexrailai.controller;


import com.next.nexrailai.service.AiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;

    /**
     * AI 聊天介面
     * @param userId 使用者 ID
     * @param message 使用者的自然語言
     */
    @GetMapping("/chat")
    public String chat(
            @RequestParam(defaultValue = "dev-user-001") String userId,
            @RequestParam String message) {

        log.info(">>>> [AI 請求] User: {}, Msg: {}", userId, message);

        try {
            return aiService.chat(userId, message);
        } catch (Exception e) {
            log.error(">>>> [AI 異常] : {}", e.getMessage(), e);
            return "抱歉，我的大腦抽筋了，請稍後再試。錯誤訊息：" + e.getMessage();
        }
    }
}
