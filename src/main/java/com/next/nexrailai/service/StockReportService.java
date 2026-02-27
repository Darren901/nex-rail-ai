package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.service.StockPositionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockReportService {

    private final StockPositionService stockPositionService;
    private final StockApiService stockApiService;

    public String buildDailyReport() {
        log.info(">>>> [Stock Report] 開始組裝每日美股報告");
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        StringBuilder sb = new StringBuilder();

        sb.append("📊 美股日報 ").append(today).append("\n\n");

        // 市場情緒
        String sentiment = stockApiService.getFearAndGreedIndex();
        sb.append("市場情緒：").append(sentiment).append("\n\n");

        // 今日新聞
        sb.append("📰 今日重點新聞\n");
        List<String> news = stockApiService.getMarketNews();
        news.forEach(n -> sb.append(n).append("\n"));

        // 持倉損益
        sb.append("\n💼 我的持倉\n");
        List<StockPosition> positions = stockPositionService.findAll();

        if (positions.isEmpty()) {
            sb.append("尚未設定持倉\n");
            sb.append("請透過後台 API 新增您的持倉資料");
            return sb.toString();
        }

        double totalUnrealizedPnl = 0;
        double totalCostValue = 0;

        for (StockPosition position : positions) {
            double currentPrice = stockApiService.getStockPrice(position.getSymbol());
            if (currentPrice < 0) {
                sb.append(String.format("%-6s 無法取得報價\n", position.getSymbol()));
                continue;
            }

            double cost = position.getCostPrice().doubleValue();
            double shares = position.getShares().doubleValue();
            double changePercent = (currentPrice - cost) / cost * 100;
            double pnl = (currentPrice - cost) * shares;
            String sign = changePercent >= 0 ? "+" : "";
            String pnlSign = pnl >= 0 ? "+" : "";

            sb.append(String.format("%-6s 成本 %.2f → 現價 %.2f  %s%.2f%%  (%s$%.0f)\n",
                position.getSymbol(), cost, currentPrice, sign, changePercent, pnlSign, pnl));

            totalUnrealizedPnl += pnl;
            totalCostValue += cost * shares;
        }

        if (totalCostValue > 0) {
            double totalChangePercent = totalUnrealizedPnl / totalCostValue * 100;
            String totalSign = totalUnrealizedPnl >= 0 ? "+" : "";
            sb.append(String.format("\n總未實現損益：%s$%.0f (%s%.2f%%)",
                totalSign, totalUnrealizedPnl, totalSign, totalChangePercent));
        }

        log.info(">>>> [Stock Report] 報告組裝完成");
        return sb.toString();
    }
}
