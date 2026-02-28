package com.next.nexrailai.service;

import com.next.nexrailai.component.StockTools;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockAiService {

    private final ChatClient chatClient;
    private final StockTools stockTools;

    // 股票 AI 對話使用獨立的 conversationId 前綴，不混用高鐵的 ChatMemory
    private static final String CONVERSATION_PREFIX = "stock-";

    private static final String DAILY_REPORT_PROMPT = """
            你是一位美股投資分析助理，專門服務長線投資者。
            今日任務：產生每日美股投資摘要報告。

            # [重要] 工具使用鐵律
            1. **禁止憑空回答**：所有股價、新聞、市場情緒數據，必須透過工具取得，禁止使用訓練資料中的舊數據。
            2. **結果為王**：禁止在沒有呼叫工具、或工具尚未回傳結果之前，就產生任何數字或分析內容。
            3. **強制刷新**：即使對話歷史中已有舊的股價或新聞，也必須重新呼叫工具取得即時數據。
            4. **全部完成再輸出**：必須完成所有必要工具呼叫後，才能輸出最終報告，禁止邊呼叫邊輸出。

            # 強制執行步驟（不可跳過任何一步）
            1. 呼叫 getPortfolioPositions → 取得持倉清單
            2. 對持倉清單中每支股票呼叫 getStockPrice → 取得最新收盤價（全部呼叫完再繼續）
            3. 對持倉清單中每支股票呼叫 getCompanyNews → 取得個股近期新聞（全部呼叫完再繼續）
            4. 呼叫 getMarketNews → 取得大盤重要新聞
            5. 呼叫 getFearAndGreedIndex → 取得市場情緒指數
            6. 整合以上所有工具結果，撰寫最終報告

            # 報告格式要求
            用繁體中文撰寫，包含：
            - 市場整體情緒與解讀（來自 getFearAndGreedIndex 結果）
            - 重要新聞摘要與潛在影響（來自 getMarketNews 結果）
            - 各持倉股價與損益概況（必須用 getStockPrice 結果計算損益百分比，禁止估算）
            - 各持倉個股近期要聞（來自 getCompanyNews 結果）
            - 整體投資組合評估

            保持客觀，不給出明確買賣建議。
            """;

    private static final String CHAT_SYSTEM_PROMPT = """
            你是一位美股投資分析助理，可使用以下工具查詢即時資訊：
            - getPortfolioPositions：查詢我的持倉清單
            - getStockPrice：查詢個股最新股價
            - getCompanyNews：查詢個股近期新聞
            - getMarketNews：查詢大盤重要新聞
            - getFearAndGreedIndex：查詢市場恐慌貪婪指數

            # [重要] 工具使用鐵律
            1. **禁止憑空回答**：所有股價、新聞、市場情緒，必須透過工具取得後才能回答，禁止使用訓練資料中的舊數據。
            2. **結果為王**：
               - (X) 錯誤：直接說「AAPL 今天是 180 美元」 → (其實沒 call tool)
               - (O) 正確：呼叫 getStockPrice("AAPL") → 等待結果 → 用結果回答
            3. **強制刷新**：即使對話歷史中已有舊的股價，股票價格隨時在變動，每次詢問都必須重新呼叫工具。
            4. **判斷需要哪些工具**：根據使用者問題，自主決定需要調用哪些工具，呼叫完畢後再回答。

            保持客觀，不給出明確買賣建議，用繁體中文回答。
            """;

    /**
     * 產生每日投資分析報告（供 Scheduler、Admin trigger、LINE /stock 呼叫）
     */
    public String generateDailyReport() {
        log.info(">>>> [Stock AI] 開始產生每日美股分析報告");
        try {
            String report = chatClient.prompt()
                    .system(DAILY_REPORT_PROMPT)
                    .user("請產生今日美股投資分析報告")
                    .tools(stockTools)
                    .call()
                    .content();
            log.info(">>>> [Stock AI] 每日美股分析報告產生完成");
            return report;
        } catch (Exception e) {
            log.error(">>>> [Stock AI] 產生每日報告失敗", e);
            return "抱歉，今日無法產生美股報告，請稍後再試或呼叫後台 API 手動觸發。";
        }
    }

    /**
     * 即時 AI 對話（供 LINE /stock [問題] 呼叫）
     */
    public String chat(String userId, String userMessage) {
        log.info(">>>> [Stock AI] 即時對話 - User: {}, Message: {}", userId, userMessage);
        try {
            return chatClient.prompt()
                    .system(CHAT_SYSTEM_PROMPT)
                    .user(userMessage)
                    .tools(stockTools)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_PREFIX + userId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error(">>>> [Stock AI] 對話失敗", e);
            return "抱歉，目前無法處理您的股票查詢，請稍後再試。";
        }
    }
}
