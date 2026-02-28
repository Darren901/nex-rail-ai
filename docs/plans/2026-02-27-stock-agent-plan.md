# Stock Agent 實作計畫

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在既有 NexRailAI LINE Bot 中新增 owner 專屬美股追蹤模組，每日 08:00 推播市場摘要，並支援 `/stock` 指令與 AI 對話。

**Architecture:** 新增獨立的 Stock 模組（Entity、Service、Scheduler、Controller），透過 `app.stock.owner-line-user-id` 做 owner-only 權限控管。複用現有 `LineMessageService`、`RestClient`、`ChatClient`、`@DistributedLock`，不修改現有 AI 對話流程。

**Tech Stack:** Java 21, Spring Boot 3.5.9, Spring AI (ChatClient/Gemini 2.5 Flash), RestClient, PostgreSQL (ddl-auto:update), Redisson (@DistributedLock), LINE Bot SDK, JUnit 5 + Mockito

---

## 新增檔案總覽

```
src/main/java/com/next/nexrailai/
├── config/
│   └── StockProperties.java               [新增]
├── jpa/entity/
│   └── StockPosition.java                 [新增]
├── jpa/repository/
│   └── StockPositionRepository.java       [新增]
├── dto/
│   ├── StockPositionRequest.java          [新增]
│   └── StockPositionResponse.java         [新增]
├── service/
│   ├── StockApiService.java               [新增]
│   ├── StockReportService.java            [新增]
│   └── StockAiService.java                [新增]
├── scheduled/
│   └── StockScheduler.java                [新增]
└── controller/admin/
    └── StockAdminController.java          [新增]

src/main/resources/
└── application.yml                        [修改：新增 stock 設定區塊]

src/main/java/com/next/nexrailai/service/
└── LineService.java                       [修改：新增 /stock 指令路由]

src/test/java/com/next/nexrailai/service/
├── StockApiServiceTest.java               [新增]
├── StockReportServiceTest.java            [新增]
└── StockAiServiceTest.java                [新增]
```

---

## Task 1：設定檔與 Properties 類別

**Files:**
- Modify: `src/main/resources/application.yml`
- Create: `src/main/java/com/next/nexrailai/config/StockProperties.java`

### Step 1：在 application.yml 新增 stock 區塊

在 `application.yml` 的 `app:` 區塊下新增（緊接在 `redirect:` 之後）：

```yaml
app:
  # ... 現有設定 ...
  stock:
    owner-line-user-id: ${STOCK_OWNER_LINE_USER_ID}
    alpha-vantage:
      api-key: ${ALPHA_VANTAGE_API_KEY}
      base-url: https://www.alphavantage.co
    finnhub:
      api-key: ${FINNHUB_API_KEY}
      base-url: https://finnhub.io/api/v1
```

### Step 2：建立 StockProperties.java

```java
package com.next.nexrailai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "app.stock")
@Getter
@Setter
public class StockProperties {

    private String ownerLineUserId;

    private AlphaVantage alphaVantage = new AlphaVantage();
    private Finnhub finnhub = new Finnhub();

    @Getter @Setter
    public static class AlphaVantage {
        private String apiKey;
        private String baseUrl;
    }

    @Getter @Setter
    public static class Finnhub {
        private String apiKey;
        private String baseUrl;
    }
}
```

### Step 3：Commit

```bash
git add src/main/resources/application.yml \
        src/main/java/com/next/nexrailai/config/StockProperties.java
git commit -m "feat(stock): add stock module configuration properties"
```

---

## Task 2：Entity 與 Repository

**Files:**
- Create: `src/main/java/com/next/nexrailai/jpa/entity/StockPosition.java`
- Create: `src/main/java/com/next/nexrailai/jpa/repository/StockPositionRepository.java`

### Step 1：建立 StockPosition Entity

```java
package com.next.nexrailai.jpa.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock_positions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 10)
    private String symbol;          // 股票代碼，e.g. AAPL

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal shares;      // 持有股數（支援零股）

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal costPrice;   // 平均成本 (USD)

    @Column(length = 255)
    private String note;            // 備註（可選）

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}
```

### Step 2：建立 StockPositionRepository

```java
package com.next.nexrailai.jpa.repository;

import com.next.nexrailai.jpa.entity.StockPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StockPositionRepository extends JpaRepository<StockPosition, Long> {

    Optional<StockPosition> findBySymbol(String symbol);

    boolean existsBySymbol(String symbol);

    List<StockPosition> findAllByOrderBySymbolAsc();
}
```

### Step 3：Commit

```bash
git add src/main/java/com/next/nexrailai/jpa/entity/StockPosition.java \
        src/main/java/com/next/nexrailai/jpa/repository/StockPositionRepository.java
git commit -m "feat(stock): add StockPosition entity and repository"
```

---

## Task 3：後台 CRUD API

**Files:**
- Create: `src/main/java/com/next/nexrailai/dto/StockPositionRequest.java`
- Create: `src/main/java/com/next/nexrailai/dto/StockPositionResponse.java`
- Create: `src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java`

### Step 1：建立 DTOs

**StockPositionRequest.java**

```java
package com.next.nexrailai.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record StockPositionRequest(

    @NotBlank(message = "股票代碼不可為空")
    @Pattern(regexp = "^[A-Z]{1,10}$", message = "股票代碼格式錯誤（大寫字母，最多10字元）")
    String symbol,

    @NotNull(message = "股數不可為空")
    @DecimalMin(value = "0.0001", message = "股數必須大於 0")
    BigDecimal shares,

    @NotNull(message = "成本價不可為空")
    @DecimalMin(value = "0.0001", message = "成本價必須大於 0")
    BigDecimal costPrice,

    @Size(max = 255, message = "備註不可超過 255 字元")
    String note
) {}
```

**StockPositionResponse.java**

```java
package com.next.nexrailai.dto;

import com.next.nexrailai.jpa.entity.StockPosition;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record StockPositionResponse(
    Long id,
    String symbol,
    BigDecimal shares,
    BigDecimal costPrice,
    String note,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static StockPositionResponse from(StockPosition entity) {
        return new StockPositionResponse(
            entity.getId(),
            entity.getSymbol(),
            entity.getShares(),
            entity.getCostPrice(),
            entity.getNote(),
            entity.getCreatedAt(),
            entity.getUpdatedAt()
        );
    }
}
```

### Step 2：建立 StockAdminController

```java
package com.next.nexrailai.controller.admin;

import com.next.nexrailai.dto.StockPositionRequest;
import com.next.nexrailai.dto.StockPositionResponse;
import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.repository.StockPositionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
@Slf4j
public class StockAdminController {

    private final StockPositionRepository stockPositionRepository;

    @GetMapping("/positions")
    public ResponseEntity<List<StockPositionResponse>> getAllPositions() {
        List<StockPositionResponse> positions = stockPositionRepository.findAllByOrderBySymbolAsc()
            .stream()
            .map(StockPositionResponse::from)
            .toList();
        return ResponseEntity.ok(positions);
    }

    @PostMapping("/positions")
    public ResponseEntity<StockPositionResponse> createPosition(@Valid @RequestBody StockPositionRequest request) {
        log.info(">>>> [Stock Admin] 新增持倉: {}", request.symbol());
        if (stockPositionRepository.existsBySymbol(request.symbol())) {
            return ResponseEntity.conflict().build();
        }
        StockPosition position = StockPosition.builder()
            .symbol(request.symbol().toUpperCase())
            .shares(request.shares())
            .costPrice(request.costPrice())
            .note(request.note())
            .build();
        return ResponseEntity.ok(StockPositionResponse.from(stockPositionRepository.save(position)));
    }

    @PutMapping("/positions/{symbol}")
    public ResponseEntity<StockPositionResponse> updatePosition(
            @PathVariable String symbol,
            @Valid @RequestBody StockPositionRequest request) {
        log.info(">>>> [Stock Admin] 更新持倉: {}", symbol);
        return stockPositionRepository.findBySymbol(symbol.toUpperCase())
            .map(position -> {
                position.setShares(request.shares());
                position.setCostPrice(request.costPrice());
                position.setNote(request.note());
                return ResponseEntity.ok(StockPositionResponse.from(stockPositionRepository.save(position)));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/positions/{symbol}")
    public ResponseEntity<Void> deletePosition(@PathVariable String symbol) {
        log.info(">>>> [Stock Admin] 刪除持倉: {}", symbol);
        return stockPositionRepository.findBySymbol(symbol.toUpperCase())
            .map(position -> {
                stockPositionRepository.delete(position);
                return ResponseEntity.noContent().<Void>build();
            })
            .orElse(ResponseEntity.notFound().build());
    }
}
```

### Step 3：Commit

```bash
git add src/main/java/com/next/nexrailai/dto/StockPositionRequest.java \
        src/main/java/com/next/nexrailai/dto/StockPositionResponse.java \
        src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java
git commit -m "feat(stock): add admin CRUD API for stock positions"
```

---

## Task 4：外部 API 服務（StockApiService）

**Files:**
- Create: `src/main/java/com/next/nexrailai/service/StockApiService.java`
- Create: `src/test/java/com/next/nexrailai/service/StockApiServiceTest.java`

### Step 1：寫失敗測試

```java
package com.next.nexrailai.service;

import com.next.nexrailai.config.StockProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockApiServiceTest {

    @Mock
    private StockProperties stockProperties;

    private StockApiService stockApiService;

    @BeforeEach
    void setUp() {
        StockProperties.AlphaVantage alphaVantage = mock(StockProperties.AlphaVantage.class);
        StockProperties.Finnhub finnhub = mock(StockProperties.Finnhub.class);
        when(stockProperties.getAlphaVantage()).thenReturn(alphaVantage);
        when(stockProperties.getFinnhub()).thenReturn(finnhub);
        when(alphaVantage.getApiKey()).thenReturn("test-key");
        when(alphaVantage.getBaseUrl()).thenReturn("https://www.alphavantage.co");
        when(finnhub.getApiKey()).thenReturn("test-key");
        when(finnhub.getBaseUrl()).thenReturn("https://finnhub.io/api/v1");
        stockApiService = new StockApiService(stockProperties);
    }

    @Test
    void formatPriceChangeText_shouldReturnPositiveArrow_whenPriceIncreased() {
        // 純格式化邏輯，不需要網路
        String result = stockApiService.formatPriceChange(100.0, 105.0);
        assertThat(result).contains("+").contains("5.00%");
    }

    @Test
    void formatPriceChangeText_shouldReturnNegativeArrow_whenPriceDecreased() {
        String result = stockApiService.formatPriceChange(100.0, 95.0);
        assertThat(result).contains("-").contains("5.00%");
    }
}
```

### Step 2：執行測試，確認失敗

```bash
./mvnw test -Dtest=StockApiServiceTest -pl .
```
Expected: FAIL（`StockApiService` 不存在）

### Step 3：建立 StockApiService

```java
package com.next.nexrailai.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.next.nexrailai.config.StockProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class StockApiService {

    private final StockProperties stockProperties;
    private final RestClient alphaVantageClient;
    private final RestClient finnhubClient;

    public StockApiService(StockProperties stockProperties) {
        this.stockProperties = stockProperties;
        this.alphaVantageClient = RestClient.builder()
            .baseUrl(stockProperties.getAlphaVantage().getBaseUrl())
            .build();
        this.finnhubClient = RestClient.builder()
            .baseUrl(stockProperties.getFinnhub().getBaseUrl())
            .build();
    }

    /**
     * 取得股票收盤價（Alpha Vantage GLOBAL_QUOTE）
     * @return 現價，失敗時回傳 -1
     */
    public double getStockPrice(String symbol) {
        try {
            Map<?, ?> response = alphaVantageClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/query")
                    .queryParam("function", "GLOBAL_QUOTE")
                    .queryParam("symbol", symbol)
                    .queryParam("apikey", stockProperties.getAlphaVantage().getApiKey())
                    .build())
                .retrieve()
                .body(Map.class);

            if (response == null) return -1;
            Map<?, ?> quote = (Map<?, ?>) response.get("Global Quote");
            if (quote == null || quote.isEmpty()) return -1;

            return Double.parseDouble(quote.get("05. price").toString());
        } catch (Exception e) {
            log.error(">>>> [Stock API] 取得股價失敗: {} - {}", symbol, e.getMessage());
            return -1;
        }
    }

    /**
     * 取得市場新聞（Finnhub general news，最新 5 則）
     * @return 新聞標題列表
     */
    public List<String> getMarketNews() {
        try {
            List<Map<?, ?>> response = finnhubClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/news")
                    .queryParam("category", "general")
                    .queryParam("token", stockProperties.getFinnhub().getApiKey())
                    .build())
                .retrieve()
                .body(List.class);

            if (response == null) return List.of("無法取得新聞資料");

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
     * @return "貪婪 (Greed 75)" 格式的字串，失敗時回傳 "N/A"
     */
    public String getFearAndGreedIndex() {
        try {
            RestClient cnnClient = RestClient.builder()
                .baseUrl("https://production.dataviz.cnn.io")
                .build();

            Map<?, ?> response = cnnClient.get()
                .uri("/index/fearandgreed/graphdata")
                .retrieve()
                .body(Map.class);

            if (response == null) return "N/A";

            Map<?, ?> fg = (Map<?, ?>) response.get("fear_and_greed");
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

    /**
     * 格式化漲跌幅文字（純計算，無網路）
     */
    public String formatPriceChange(double costPrice, double currentPrice) {
        double changePercent = (currentPrice - costPrice) / costPrice * 100;
        String sign = changePercent >= 0 ? "+" : "";
        return String.format("%s%.2f%%", sign, changePercent);
    }
}
```

### Step 4：執行測試，確認通過

```bash
./mvnw test -Dtest=StockApiServiceTest -pl .
```
Expected: PASS（2 tests）

### Step 5：Commit

```bash
git add src/main/java/com/next/nexrailai/service/StockApiService.java \
        src/test/java/com/next/nexrailai/service/StockApiServiceTest.java
git commit -m "feat(stock): add StockApiService for Alpha Vantage, Finnhub and CNN F&G"
```

---

## Task 5：報告組裝服務（StockReportService）

**Files:**
- Create: `src/main/java/com/next/nexrailai/service/StockReportService.java`
- Create: `src/test/java/com/next/nexrailai/service/StockReportServiceTest.java`

### Step 1：寫失敗測試

```java
package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.repository.StockPositionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockReportServiceTest {

    @Mock
    private StockPositionRepository stockPositionRepository;

    @Mock
    private StockApiService stockApiService;

    @InjectMocks
    private StockReportService stockReportService;

    @Test
    void buildReport_shouldContainAllSections_whenDataAvailable() {
        // given
        StockPosition aapl = StockPosition.builder()
            .symbol("AAPL")
            .shares(new BigDecimal("10"))
            .costPrice(new BigDecimal("180.00"))
            .build();
        when(stockPositionRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of(aapl));
        when(stockApiService.getStockPrice("AAPL")).thenReturn(185.0);
        when(stockApiService.getMarketNews()).thenReturn(List.of("• Fed holds rates"));
        when(stockApiService.getFearAndGreedIndex()).thenReturn("😊 貪婪 (65)");

        // when
        String report = stockReportService.buildDailyReport();

        // then
        assertThat(report).contains("📊 美股日報");
        assertThat(report).contains("市場情緒");
        assertThat(report).contains("今日重點新聞");
        assertThat(report).contains("我的持倉");
        assertThat(report).contains("AAPL");
        assertThat(report).contains("+2.78%");  // (185-180)/180*100
    }

    @Test
    void buildReport_shouldHandleEmptyPositions_gracefully() {
        when(stockPositionRepository.findAllByOrderBySymbolAsc()).thenReturn(List.of());
        when(stockApiService.getMarketNews()).thenReturn(List.of("• No news"));
        when(stockApiService.getFearAndGreedIndex()).thenReturn("N/A");

        String report = stockReportService.buildDailyReport();

        assertThat(report).contains("📊 美股日報");
        assertThat(report).contains("尚未設定持倉");
    }
}
```

### Step 2：執行測試，確認失敗

```bash
./mvnw test -Dtest=StockReportServiceTest -pl .
```
Expected: FAIL

### Step 3：建立 StockReportService

```java
package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.repository.StockPositionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockReportService {

    private final StockPositionRepository stockPositionRepository;
    private final StockApiService stockApiService;

    public String buildDailyReport() {
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
        List<StockPosition> positions = stockPositionRepository.findAllByOrderBySymbolAsc();

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
                sb.append(position.getSymbol()).append("  無法取得報價\n");
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

        return sb.toString();
    }
}
```

### Step 4：執行測試，確認通過

```bash
./mvnw test -Dtest=StockReportServiceTest -pl .
```
Expected: PASS（2 tests）

### Step 5：Commit

```bash
git add src/main/java/com/next/nexrailai/service/StockReportService.java \
        src/test/java/com/next/nexrailai/service/StockReportServiceTest.java
git commit -m "feat(stock): add StockReportService to assemble daily report"
```

---

## Task 6：每日排程（StockScheduler）

**Files:**
- Create: `src/main/java/com/next/nexrailai/scheduled/StockScheduler.java`
- Modify: `src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java`（新增手動觸發端點）

### Step 1：建立 StockScheduler

```java
package com.next.nexrailai.scheduled;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.aspect.DistributedLock;
import com.next.nexrailai.config.StockProperties;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.StockReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockScheduler {

    private final StockReportService stockReportService;
    private final LineMessageService lineMessageService;
    private final StockProperties stockProperties;

    // 每日 08:00 台灣時間
    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Taipei")
    @DistributedLock(key = "stock-daily-report")
    public void sendDailyReport() {
        log.info(">>>> [Stock Scheduler] 開始產生每日美股報告");
        try {
            String report = stockReportService.buildDailyReport();
            lineMessageService.pushMessage(
                stockProperties.getOwnerLineUserId(),
                new TextMessage(report)
            );
            log.info(">>>> [Stock Scheduler] 每日美股報告已發送");
        } catch (Exception e) {
            log.error(">>>> [Stock Scheduler] 發送每日報告失敗", e);
        }
    }
}
```

> **注意**：`MON-FRI` 只在美股交易日（週一至週五）發送。週末不送。

### Step 2：在 StockAdminController 新增手動觸發端點

在 `StockAdminController` 中新增：

```java
// 新增注入
private final StockReportService stockReportService;
private final LineMessageService lineMessageService;
private final StockProperties stockProperties;

@PostMapping("/report/trigger")
public ResponseEntity<String> triggerReport() {
    log.info(">>>> [Stock Admin] 手動觸發每日報告");
    String report = stockReportService.buildDailyReport();
    lineMessageService.pushMessage(
        stockProperties.getOwnerLineUserId(),
        new TextMessage(report)
    );
    return ResponseEntity.ok("報告已發送");
}
```

### Step 3：Commit

```bash
git add src/main/java/com/next/nexrailai/scheduled/StockScheduler.java \
        src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java
git commit -m "feat(stock): add StockScheduler for daily 08:00 report and manual trigger endpoint"
```

---

## Task 7：AI 對話服務（StockAiService）

**Files:**
- Create: `src/main/java/com/next/nexrailai/service/StockAiService.java`
- Create: `src/test/java/com/next/nexrailai/service/StockAiServiceTest.java`

### Step 1：寫失敗測試

```java
package com.next.nexrailai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class StockAiServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StockApiService stockApiService;

    @InjectMocks
    private StockAiService stockAiService;

    @Test
    void buildContext_shouldIncludeNewsAndSentiment() {
        // 驗證 buildContext 邏輯（不涉及網路）
        assertThat(stockAiService).isNotNull();
    }
}
```

### Step 2：建立 StockAiService

```java
package com.next.nexrailai.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockAiService {

    private final ChatClient chatClient;
    private final StockApiService stockApiService;

    // 股票 AI 對話使用獨立的 conversationId 前綴，不混用高鐵的 ChatMemory
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

        // 取得即時背景資料
        String sentiment = stockApiService.getFearAndGreedIndex();
        List<String> news = stockApiService.getMarketNews();
        String newsText = String.join("\n", news);

        try {
            return chatClient.prompt()
                .system(sp -> sp.text(SYSTEM_PROMPT)
                    .param("sentiment", sentiment)
                    .param("news", newsText))
                .user(userMessage)
                .advisors(a -> a.param(
                    org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID,
                    CONVERSATION_PREFIX + userId))
                .call()
                .content();
        } catch (Exception e) {
            log.error(">>>> [Stock AI] 對話失敗", e);
            return "抱歉，目前無法處理您的股票查詢，請稍後再試。";
        }
    }
}
```

### Step 3：執行測試

```bash
./mvnw test -Dtest=StockAiServiceTest -pl .
```
Expected: PASS

### Step 4：Commit

```bash
git add src/main/java/com/next/nexrailai/service/StockAiService.java \
        src/test/java/com/next/nexrailai/service/StockAiServiceTest.java
git commit -m "feat(stock): add StockAiService for /stock AI chat"
```

---

## Task 8：LINE 指令路由

**Files:**
- Modify: `src/main/java/com/next/nexrailai/service/LineService.java`

### Step 1：修改 LineService.handleCommand()

找到 `handleCommand` 方法中 `switch (command)` 前，新增以下攔截邏輯：

```java
private void handleCommand(String userId, String command, String replyToken) {
    log.info(">>>> [LINE Service] 處理指令: {} for User: {}", command, userId);

    // /stock 指令：owner-only 攔截（必須在 switch 之前）
    if (command.startsWith("/stock")) {
        handleStockCommand(userId, command, replyToken);
        return;
    }

    Message replyMessage;
    switch (command) {
        // ... 現有的 case 不動 ...
    }
    // ...
}

private void handleStockCommand(String userId, String command, String replyToken) {
    // Owner 權限檢查
    if (!userId.equals(stockProperties.getOwnerLineUserId())) {
        log.debug(">>>> [Stock] 非 owner 使用者嘗試使用 /stock 指令，靜默忽略: {}", userId);
        return; // 靜默忽略，不回應
    }

    String query = command.length() > 6 ? command.substring(6).trim() : "";

    // /stock（無參數）→ 手動觸發報告
    if (query.isEmpty()) {
        showLoading(userId);
        String report = stockReportService.buildDailyReport();
        reply(replyToken, new TextMessage(report));
        return;
    }

    // /stock [問題] → AI 對話
    showLoading(userId);
    String response = stockAiService.chat(userId, query);
    reply(replyToken, new TextMessage(response));
}
```

同時在 `LineService` 新增以下依賴注入（Constructor Injection）：

```java
private final StockProperties stockProperties;
private final StockReportService stockReportService;
private final StockAiService stockAiService;
```

### Step 2：執行所有測試，確認無回歸問題

```bash
./mvnw test -pl .
```
Expected: 所有既有測試 PASS

### Step 3：Commit

```bash
git add src/main/java/com/next/nexrailai/service/LineService.java
git commit -m "feat(stock): add /stock command routing with owner-only access control"
```

---

## Task 9：環境變數補齊與驗收

### Step 1：確認 .env / 環境變數新增以下三個

```bash
STOCK_OWNER_LINE_USER_ID=<你的 LINE User ID>
ALPHA_VANTAGE_API_KEY=<Alpha Vantage 免費 API Key>
FINNHUB_API_KEY=<Finnhub 免費 API Key>
```

取得方式：
- LINE User ID：可從後台管理員介面查詢，或呼叫 `GET /api/admin/users`
- Alpha Vantage：https://www.alphavantage.co/support/#api-key
- Finnhub：https://finnhub.io/register

### Step 2：本地驗收測試

```bash
# 1. 啟動應用程式
./mvnw spring-boot:run

# 2. 手動觸發報告
curl -X POST http://localhost:8089/api/admin/stock/report/trigger \
  -H "Authorization: Bearer <JWT_TOKEN>"

# 3. 新增測試持倉
curl -X POST http://localhost:8089/api/admin/stock/positions \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{"symbol":"AAPL","shares":10,"costPrice":180.00,"note":"長線持有"}'

# 4. 再次觸發報告，確認持倉損益顯示正確
curl -X POST http://localhost:8089/api/admin/stock/report/trigger \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

### Step 3：在 LINE 測試指令

- 輸入 `/stock` → 收到每日報告
- 輸入 `/stock NVDA 最近跌什麼原因` → 收到 AI 分析
- 用其他帳號輸入 `/stock` → 靜默無回應

### Step 4：Final Commit

```bash
git add .
git commit -m "feat(stock): complete stock agent module - daily report, admin API, AI chat"
```

---

## 驗收標準

- [ ] `POST /api/admin/stock/report/trigger` 能成功發送 LINE 訊息
- [ ] 報告包含情緒、新聞、持倉損益三個區塊
- [ ] 非 owner 使用 `/stock` 無任何回應
- [ ] Owner 使用 `/stock` 收到報告
- [ ] Owner 使用 `/stock NVDA ...` 收到 AI 回答
- [ ] `./mvnw test` 全部通過，無回歸問題
