package com.next.nexrailai.service;

import com.next.nexrailai.config.StockProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockApiService {

    private final StockProperties stockProperties;
    private final RestClient alphaVantageClient;
    private final RestClient finnhubClient;
    private final RestClient cnnClient;

    // Alpha Vantage 免費版限制：1 request/second
    private static final long ALPHA_VANTAGE_MIN_INTERVAL_MS = 1200;
    private final AtomicLong lastAlphaVantageCallMs = new AtomicLong(0);

    public StockApiService(StockProperties stockProperties) {
        this.stockProperties = stockProperties;
        this.alphaVantageClient = RestClient.builder()
            .baseUrl(stockProperties.alphaVantage().baseUrl())
            .build();
        this.finnhubClient = RestClient.builder()
            .baseUrl(stockProperties.finnhub().baseUrl())
            .build();
        this.cnnClient = RestClient.builder()
            .baseUrl("https://production.dataviz.cnn.io")
            .defaultHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .defaultHeader("Referer", "https://edition.cnn.com/markets/fear-and-greed")
            .defaultHeader("Accept", "application/json, text/plain, */*")
            .build();
    }

    /**
     * 取得股票收盤價（Alpha Vantage GLOBAL_QUOTE）
     * @return 現價；失敗時回傳 -1
     */
    public double getStockPrice(String symbol) {
        try {
            // Rate limiting：確保兩次呼叫間距至少 1.2 秒
            long elapsed = System.currentTimeMillis() - lastAlphaVantageCallMs.get();
            if (elapsed < ALPHA_VANTAGE_MIN_INTERVAL_MS) {
                Thread.sleep(ALPHA_VANTAGE_MIN_INTERVAL_MS - elapsed);
            }
            lastAlphaVantageCallMs.set(System.currentTimeMillis());

            Map response = alphaVantageClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/query")
                    .queryParam("function", "GLOBAL_QUOTE")
                    .queryParam("symbol", symbol)
                    .queryParam("apikey", stockProperties.alphaVantage().apiKey())
                    .build())
                .retrieve()
                .body(Map.class);

            if (response == null) return -1;
            Map quote = (Map) response.get("Global Quote");
            if (quote == null || quote.isEmpty()) {
                log.warn(">>>> [Stock API] {} 無法取得報價，API 回應: {}", symbol, response);
                return -1;
            }

            return Double.parseDouble(quote.get("05. price").toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(">>>> [Stock API] 取得股價被中斷: {}", symbol);
            return -1;
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得股價失敗: {} - {}", symbol, e.getMessage());
            return -1;
        }
    }

    /**
     * 取得市場新聞（Finnhub general news，最新 5 則）
     */
    public List<String> getMarketNews() {
        try {
            List<Map> response = finnhubClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/news")
                    .queryParam("category", "general")
                    .queryParam("token", stockProperties.finnhub().apiKey())
                    .build())
                .retrieve()
                .body(List.class);

            if (response == null || response.isEmpty()) return List.of("• 今日無法取得新聞資料");

            return response.stream()
                .limit(5)
                .map(news -> "• " + news.get("headline").toString())
                .toList();
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得新聞失敗: {}", e.getMessage());
            return List.of("• 今日無法取得新聞資料");
        }
    }

    /**
     * 取得 CNN 恐慌貪婪指數
     * @return 例如 "😊 貪婪 (65)"；失敗時回傳 "N/A"
     */
    public String getFearAndGreedIndex() {
        try {
            Map response = cnnClient.get()
                .uri("/index/fearandgreed/graphdata")
                .retrieve()
                .body(Map.class);

            if (response == null) return "N/A";
            Map fg = (Map) response.get("fear_and_greed");
            if (fg == null) return "N/A";

            double score = Double.parseDouble(fg.get("score").toString());
            String rating = fg.get("rating").toString();
            String emoji = score >= 75 ? "🤑" : score >= 55 ? "😊" : score >= 45 ? "😐" : score >= 25 ? "😨" : "😱";

            return String.format("%s %s (%.0f)", emoji, translateRating(rating), score);
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得恐慌指數失敗: {}", e.getMessage());
            return "N/A";
        }
    }

    /**
     * 取得個股公司相關新聞（Finnhub company-news，最近 7 天最多 3 則）
     * @param symbol 股票代碼，例如 AAPL
     * @return 新聞摘要字串（多則合併），失敗時回傳預設訊息
     */
    public String getCompanyNews(String symbol) {
        try {
            LocalDate today = LocalDate.now();
            LocalDate weekAgo = today.minusDays(7);

            List<Map> response = finnhubClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/company-news")
                    .queryParam("symbol", symbol)
                    .queryParam("from", weekAgo.toString())
                    .queryParam("to", today.toString())
                    .queryParam("token", stockProperties.finnhub().apiKey())
                    .build())
                .retrieve()
                .body(List.class);

            if (response == null || response.isEmpty()) {
                return symbol + " 近期無相關新聞";
            }

            return response.stream()
                .limit(3)
                .map(news -> "• " + news.get("headline").toString())
                .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得 {} 個股新聞失敗: {}", symbol, e.getMessage());
            return symbol + " 近期無法取得新聞資料";
        }
    }

    /**
     * 格式化漲跌幅文字（純計算，無網路呼叫）
     */
    public String formatPriceChange(double costPrice, double currentPrice) {
        double changePercent = (currentPrice - costPrice) / costPrice * 100;
        String sign = changePercent >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, changePercent);
    }

    private String translateRating(String rating) {
        return switch (rating.toLowerCase()) {
            case "extreme greed" -> "極度貪婪";
            case "greed" -> "貪婪";
            case "neutral" -> "中性";
            case "fear" -> "恐慌";
            case "extreme fear" -> "極度恐慌";
            default -> rating;
        };
    }
}
