package com.next.nexrailai.controller;


import com.next.nexrailai.service.AiService;
import com.next.nexrailai.service.StockAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Slf4j
public class AiController {

    private final AiService aiService;
    private final StockAiService stockAiService;

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
            return "抱歉，我的大腦抽筋了，請稍後再試。";
        }
    }

    /**
     * 每日美股報告（Agent 自主查詢工具後分析）
     */
    @PostMapping("/stock/report")
    public String stockReport() {
        log.info(">>>> [Stock AI 請求] 手動觸發每日報告");
        return stockAiService.generateDailyReport();
    }

    /**
     * 即時美股 AI 對話（Agent 自主決定調用哪些工具）
     * @param userId 使用者 ID（用於 ChatMemory 隔離）
     * @param message 問題內容
     */
    @GetMapping("/stock/chat")
    public String stockChat(
            @RequestParam(defaultValue = "dev-user-001") String userId,
            @RequestParam String message) {

        log.info(">>>> [Stock AI 請求] User: {}, Msg: {}", userId, message);
        return stockAiService.chat(userId, message);
    }
}
