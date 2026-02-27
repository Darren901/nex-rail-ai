package com.next.nexrailai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockAiService {

    private final ChatClient chatClient;
    private final StockApiService stockApiService;

    private static final String CONVERSATION_PREFIX = "stock-";

    private static final String SYSTEM_PROMPT = """
            你是一位美股投資分析助理，專門提供長線投資者所需的市場資訊。
            回答時保持客觀、簡潔，優先引用最新新聞和數據佐證。
            不要給出明確的買賣建議，僅分析市場現況與可能的影響因素。
            目前市場情緒：{sentiment}
            最新市場新聞：{news}
            """;

    public String chat(String userId, String userMessage) {
        log.info(">>>> [Stock AI] User: {}, Message: {}", userId, userMessage);

        String sentiment = stockApiService.getFearAndGreedIndex();
        List<String> news = stockApiService.getMarketNews();
        String newsText = String.join("\n", news);

        try {
            return chatClient.prompt()
                    .system(sp -> sp.text(SYSTEM_PROMPT)
                            .param("sentiment", sentiment)
                            .param("news", newsText))
                    .user(userMessage)
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, CONVERSATION_PREFIX + userId))
                    .call()
                    .content();
        } catch (Exception e) {
            log.error(">>>> [Stock AI] 對話失敗", e);
            return "抱歉，目前無法處理您的股票查詢，請稍後再試。";
        }
    }
}
