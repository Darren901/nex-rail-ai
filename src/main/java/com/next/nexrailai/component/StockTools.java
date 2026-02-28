package com.next.nexrailai.component;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.service.StockPositionService;
import com.next.nexrailai.service.StockApiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockTools {

    private final StockApiService stockApiService;
    private final StockPositionService stockPositionService;

    @Tool(description = "查詢目前持倉清單，回傳各股票代碼、持股數、平均成本（USD）與備註")
    public String getPortfolioPositions() {
        log.info(">>>> [Stock Tools] 查詢持倉清單");
        List<StockPosition> positions = stockPositionService.findAll();
        if (positions.isEmpty()) {
            return "尚未設定持倉，請透過後台 API 新增持倉資料";
        }
        return positions.stream()
            .map(p -> String.format("股票代碼: %s, 持股數: %s, 平均成本: $%s USD%s",
                p.getSymbol(),
                p.getShares().toPlainString(),
                p.getCostPrice().toPlainString(),
                p.getNote() != null ? ", 備註: " + p.getNote() : ""))
            .collect(Collectors.joining("\n"));
    }

    @Tool(description = "查詢指定股票的最新收盤價（USD）。回傳 -1 表示無法取得報價")
    public double getStockPrice(
            @ToolParam(description = "股票代碼，例如 AAPL、NVDA、MSFT") String symbol) {
        log.info(">>>> [Stock Tools] 查詢股價: {}", symbol);
        return stockApiService.getStockPrice(symbol);
    }

    @Tool(description = "查詢指定公司的最近新聞（最近 7 天，最多 3 則）")
    public String getCompanyNews(
            @ToolParam(description = "股票代碼，例如 AAPL") String symbol) {
        log.info(">>>> [Stock Tools] 查詢個股新聞: {}", symbol);
        return stockApiService.getCompanyNews(symbol);
    }

    @Tool(description = "查詢整體市場最新新聞（最新 5 則大盤重要資訊）")
    public String getMarketNews() {
        log.info(">>>> [Stock Tools] 查詢大盤新聞");
        return String.join("\n", stockApiService.getMarketNews());
    }

    @Tool(description = "查詢 CNN 恐慌貪婪指數，反映整體市場情緒（極度恐慌/恐慌/中性/貪婪/極度貪婪）")
    public String getFearAndGreedIndex() {
        log.info(">>>> [Stock Tools] 查詢恐慌貪婪指數");
        return stockApiService.getFearAndGreedIndex();
    }
}
