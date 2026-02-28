package com.next.nexrailai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.next.nexrailai.config.StockProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockApiService {

    private final StockProperties stockProperties;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RestClient alphaVantageClient;
    private final RestClient finnhubClient;
    private final RestClient cnnClient;

    // Alpha Vantage 免費版限制：1 request/second
    private static final long ALPHA_VANTAGE_MIN_INTERVAL_MS = 1200;
    private final AtomicLong lastAlphaVantageCallMs = new AtomicLong(0);

    // Redis Key 前綴
    private static final String KEY_STOCK_PRICE   = "stock:price:";
    private static final String KEY_MARKET_NEWS   = "stock:news:market";
    private static final String KEY_COMPANY_NEWS  = "stock:news:company:";
    private static final String KEY_FEAR_GREED    = "stock:fear-greed";

    // TTL 策略
    private static final Duration TTL_PRICE      = Duration.ofMinutes(15);
    private static final Duration TTL_NEWS       = Duration.ofMinutes(30);
    private static final Duration TTL_FEAR_GREED = Duration.ofHours(1);

    public StockApiService(StockProperties stockProperties,
                           StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper) {
        this.stockProperties = stockProperties;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.alphaVantageClient = RestClient.builder()
            .baseUrl(stockProperties.alphaVantage().baseUrl())
            .requestFactory(createRequestFactory())
            .build();
        this.finnhubClient = RestClient.builder()
            .baseUrl(stockProperties.finnhub().baseUrl())
            .requestFactory(createRequestFactory())
            .build();
        this.cnnClient = RestClient.builder()
            .baseUrl("https://production.dataviz.cnn.io")
            .requestFactory(createRequestFactory())
            .defaultHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .defaultHeader("Referer", "https://edition.cnn.com/markets/fear-and-greed")
            .defaultHeader("Accept", "application/json, text/plain, */*")
            .build();
    }

    /**
     * 取得股票收盤價（Alpha Vantage GLOBAL_QUOTE），快取 15 分鐘
     * @return 現價；失敗時回傳 -1
     */
    public double getStockPrice(String symbol) {
        String cacheKey = KEY_STOCK_PRICE + symbol.toUpperCase();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug(">>>> [Stock API] 快取命中 股價: {}", symbol);
            return Double.parseDouble(cached);
        }

        try {
            // Rate limiting：確保兩次 API 呼叫間距至少 1.2 秒
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

            double price = Double.parseDouble(quote.get("05. price").toString());
            redisTemplate.opsForValue().set(cacheKey, String.valueOf(price), TTL_PRICE);
            log.info(">>>> [Stock API] 股價快取更新: {} = {}", symbol, price);
            return price;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn(">>>> [Stock API] 取得股價被中斷: {}", symbol);
            return -1;
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得股價失敗: {} - {}", symbol, e.getMessage(), e);
            return -1;
        }
    }

    /**
     * 取得市場新聞（Finnhub general news，最新 5 則），快取 30 分鐘
     */
    public List<String> getMarketNews() {
        String cached = redisTemplate.opsForValue().get(KEY_MARKET_NEWS);
        if (cached != null) {
            log.debug(">>>> [Stock API] 快取命中 大盤新聞");
            try {
                return objectMapper.readValue(cached, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                log.warn(">>>> [Stock API] 大盤新聞快取解析失敗，重新查詢");
            }
        }

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

            List<String> news = response.stream()
                .limit(5)
                .map(n -> "• " + n.get("headline").toString())
                .toList();

            redisTemplate.opsForValue().set(KEY_MARKET_NEWS, objectMapper.writeValueAsString(news), TTL_NEWS);
            log.info(">>>> [Stock API] 大盤新聞快取更新");
            return news;
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得新聞失敗: {}", e.getMessage(), e);
            return List.of("• 今日無法取得新聞資料");
        }
    }

    /**
     * 取得 CNN 恐慌貪婪指數，快取 1 小時
     * @return 例如 "😊 貪婪 (65)"；失敗時回傳 "N/A"
     */
    public String getFearAndGreedIndex() {
        String cached = redisTemplate.opsForValue().get(KEY_FEAR_GREED);
        if (cached != null) {
            log.debug(">>>> [Stock API] 快取命中 恐慌貪婪指數");
            return cached;
        }

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

            String result = String.format("%s %s (%.0f)", emoji, translateRating(rating), score);
            redisTemplate.opsForValue().set(KEY_FEAR_GREED, result, TTL_FEAR_GREED);
            log.info(">>>> [Stock API] 恐慌貪婪指數快取更新: {}", result);
            return result;
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得恐慌指數失敗: {}", e.getMessage(), e);
            return "N/A";
        }
    }

    /**
     * 取得個股公司相關新聞（Finnhub company-news，最近 7 天最多 3 則），快取 30 分鐘
     * @param symbol 股票代碼，例如 AAPL
     * @return 新聞摘要字串（多則合併），失敗時回傳預設訊息
     */
    public String getCompanyNews(String symbol) {
        String cacheKey = KEY_COMPANY_NEWS + symbol.toUpperCase();

        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            log.debug(">>>> [Stock API] 快取命中 個股新聞: {}", symbol);
            return cached;
        }

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

            // 優先篩選 related 欄位含有該 symbol 的新聞（精確比對，避免 NET 匹配到 INTERNET 等）
            String symbolUpper = symbol.toUpperCase();
            List<Map> filtered = response.stream()
                .filter(news -> {
                    Object related = news.get("related");
                    if (related == null) return false;
                    return Arrays.asList(related.toString().toUpperCase().split("[,\\s]+"))
                        .contains(symbolUpper);
                })
                .limit(3)
                .collect(Collectors.toList());

            // 若過濾後無結果，退回原始清單前 3 則
            if (filtered.isEmpty()) {
                log.warn(">>>> [Stock API] {} 個股新聞無 related 欄位匹配，使用原始清單", symbol);
                filtered = response.stream().limit(3).collect(Collectors.toList());
            }

            String result = filtered.stream()
                .map(news -> {
                    String headline = news.get("headline") != null ? news.get("headline").toString() : "";
                    String summary = news.get("summary") != null ? news.get("summary").toString() : "";
                    if (summary.length() > 150) {
                        summary = summary.substring(0, 150) + "...";
                    }
                    return "• " + headline + (summary.isEmpty() ? "" : "\n  摘要：" + summary);
                })
                .collect(Collectors.joining("\n\n"));

            redisTemplate.opsForValue().set(cacheKey, result, TTL_NEWS);
            log.info(">>>> [Stock API] 個股新聞快取更新: {}", symbol);
            return result;
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得 {} 個股新聞失敗: {}", symbol, e.getMessage(), e);
            return symbol + " 近期無法取得新聞資料";
        }
    }

    /**
     * 格式化漲跌幅文字（純計算，無網路呼叫）
     */
    public String formatPriceChange(double costPrice, double currentPrice) {
        if (costPrice <= 0) {
           return "N/A";
        }

        double changePercent = (currentPrice - costPrice) / costPrice * 100;
        String sign = changePercent >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, changePercent);
    }

    private static SimpleClientHttpRequestFactory createRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return factory;
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
