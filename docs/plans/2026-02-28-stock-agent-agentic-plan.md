# Stock Agent 重構計畫（Agentic 架構）

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 將既有的 Pipeline 式 Stock Agent 改為 Agentic 架構——給 Gemini 一組 `@Tool` 工具，讓 Agent 自主查詢市場資料、個股新聞、持倉損益，自行撰寫分析報告，而非程式碼固定格式化字串。

**前提：以下 Task 已完成（勿重複實作）**
- ✅ StockProperties（config）
- ✅ StockPosition Entity + Repository
- ✅ StockPositionService（jpa/service）
- ✅ StockPositionRequest / StockPositionResponse DTOs
- ✅ StockAdminController（含 CRUD + /report/trigger）
- ✅ StockApiService（getStockPrice, getMarketNews, getFearAndGreedIndex）
- ✅ StockScheduler（已完成，但需修改呼叫目標）
- ✅ LineService（/stock 路由，已完成，但需移除 StockReportService 依賴）

**需刪除：**
- ❌ StockReportService.java（由 Agent 取代）
- ❌ StockReportServiceTest.java

**Tech Stack:** Java 21, Spring Boot 3.5.9, Spring AI 1.1.2（`@Tool`, `ChatClient`）, Gemini 2.5 Flash

---

## 新增/修改檔案總覽

```
【新增】
service/StockTools.java                         ← @Tool 方法集合
test/.../service/StockToolsTest.java

【修改】
service/StockApiService.java                    ← 新增 getCompanyNews(symbol)
test/.../service/StockApiServiceTest.java       ← 新增對應測試
service/StockAiService.java                     ← 改寫：整合 Tools，新增 generateDailyReport()
test/.../service/StockAiServiceTest.java        ← 改寫測試
scheduled/StockScheduler.java                   ← buildDailyReport → generateDailyReport
controller/admin/StockAdminController.java      ← /report/trigger 改呼叫 Agent
service/LineService.java                        ← 移除 StockReportService 依賴

【刪除】
service/StockReportService.java
test/.../service/StockReportServiceTest.java
```

---

## Task 1：StockApiService 新增 getCompanyNews(symbol)

**Files:**
- Modify: `src/main/java/com/next/nexrailai/service/StockApiService.java`
- Modify: `src/test/java/com/next/nexrailai/service/StockApiServiceTest.java`

### Step 1：在 StockApiServiceTest 新增測試（TDD）

在現有測試類別新增：

```java
@Test
void getCompanyNews_shouldReturnDefaultMessage_whenApiFails() {
    // StockApiService 建構時不需要網路，僅驗證防禦性回傳
    String result = stockApiService.getCompanyNews("INVALID_SYMBOL_XYZ");
    // 實際呼叫會失敗（無效 API key），應回傳預設訊息而非拋出例外
    assertThat(result).isNotNull();
    assertThat(result).isNotEmpty();
}
```

### Step 2：執行測試，確認通過（StockApiService 存在，測試不會因為 compile error 而失敗，但新方法需先加才能呼叫）

實際上：先在 `StockApiService` 新增方法 stub，再確認測試 compile 通過。

### Step 3：在 StockApiService 實作 getCompanyNews

在 `getMarketNews()` 之後新增：

```java
/**
 * 取得個股公司相關新聞（Finnhub company-news，最新 3 則）
 * @param symbol 股票代碼，例如 AAPL
 * @return 新聞摘要字串（多則合併），失敗時回傳預設訊息
 */
public String getCompanyNews(String symbol) {
    try {
        // Finnhub company-news 需要日期範圍，取最近 7 天
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate weekAgo = today.minusDays(7);

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
            .collect(java.util.stream.Collectors.joining("\n"));
    } catch (Exception e) {
        log.error(">>>> [Stock API] 取得 {} 個股新聞失敗: {}", symbol, e.getMessage());
        return symbol + " 近期無法取得新聞資料";
    }
}
```

### Step 4：執行測試，確認通過

```bash
./mvnw test -Dtest=StockApiServiceTest -pl .
```
Expected: PASS

### Step 5：Commit

```bash
git add src/main/java/com/next/nexrailai/service/StockApiService.java \
        src/test/java/com/next/nexrailai/service/StockApiServiceTest.java
git commit -m "feat(stock): add getCompanyNews(symbol) to StockApiService"
```

---

## Task 2：建立 StockTools（Agent 工具集）

**Files:**
- Create: `src/main/java/com/next/nexrailai/service/StockTools.java`
- Create: `src/test/java/com/next/nexrailai/service/StockToolsTest.java`

### Step 1：寫測試（TDD）

```java
package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.service.StockPositionService;
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
class StockToolsTest {

    @Mock
    private StockApiService stockApiService;

    @Mock
    private StockPositionService stockPositionService;

    @InjectMocks
    private StockTools stockTools;

    @Test
    void getPortfolioPositions_shouldReturnFormattedPositions_whenPositionsExist() {
        StockPosition aapl = StockPosition.builder()
            .symbol("AAPL")
            .shares(new BigDecimal("10"))
            .costPrice(new BigDecimal("180.00"))
            .note("長線持有")
            .build();
        when(stockPositionService.findAll()).thenReturn(List.of(aapl));

        String result = stockTools.getPortfolioPositions();

        assertThat(result).contains("AAPL");
        assertThat(result).contains("10");
        assertThat(result).contains("180.00");
    }

    @Test
    void getPortfolioPositions_shouldReturnEmptyMessage_whenNoPositions() {
        when(stockPositionService.findAll()).thenReturn(List.of());

        String result = stockTools.getPortfolioPositions();

        assertThat(result).contains("尚未設定持倉");
    }

    @Test
    void getStockPrice_shouldDelegateToApiService() {
        when(stockApiService.getStockPrice("AAPL")).thenReturn(185.5);

        double result = stockTools.getStockPrice("AAPL");

        assertThat(result).isEqualTo(185.5);
    }
}
```

### Step 2：執行測試，確認編譯失敗（StockTools 不存在）

```bash
./mvnw test -Dtest=StockToolsTest -pl .
```
Expected: FAIL（compile error）

### Step 3：建立 StockTools.java

```java
package com.next.nexrailai.service;

import com.next.nexrailai.jpa.entity.StockPosition;
import com.next.nexrailai.jpa.service.StockPositionService;
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
```

### Step 4：執行測試，確認通過

```bash
./mvnw test -Dtest=StockToolsTest -pl .
```
Expected: PASS（3 tests）

### Step 5：Commit

```bash
git add src/main/java/com/next/nexrailai/service/StockTools.java \
        src/test/java/com/next/nexrailai/service/StockToolsTest.java
git commit -m "feat(stock): add StockTools with @Tool annotated methods for Agent"
```

---

## Task 3：改寫 StockAiService（整合 Tools，新增 generateDailyReport）

**Files:**
- Rewrite: `src/main/java/com/next/nexrailai/service/StockAiService.java`
- Rewrite: `src/test/java/com/next/nexrailai/service/StockAiServiceTest.java`

### Step 1：改寫測試

```java
package com.next.nexrailai.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class StockAiServiceTest {

    @Mock
    private ChatClient chatClient;

    @Mock
    private StockTools stockTools;

    @InjectMocks
    private StockAiService stockAiService;

    @Test
    void stockAiService_shouldBeInstantiable() {
        // 驗證 StockTools 已注入（不再依賴 StockApiService 直接呼叫）
        assertThat(stockAiService).isNotNull();
    }
}
```

### Step 2：改寫 StockAiService.java

**注意**：移除對 `StockApiService` 的直接依賴，改注入 `StockTools`。`generateDailyReport()` 供 Scheduler、Admin、LINE `/stock` 三個入口共用。

```java
package com.next.nexrailai.service;

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

    private static final String CONVERSATION_PREFIX = "stock-";

    private static final String DAILY_REPORT_PROMPT = """
            你是一位美股投資分析助理，專門服務長線投資者。
            今日任務：產生每日美股投資摘要報告。

            請依以下步驟使用工具：
            1. 呼叫 getPortfolioPositions 取得持倉清單
            2. 對每支持倉呼叫 getStockPrice 取得最新股價
            3. 對每支持倉呼叫 getCompanyNews 取得個股近期新聞
            4. 呼叫 getMarketNews 取得大盤重要新聞
            5. 呼叫 getFearAndGreedIndex 取得市場情緒

            最後整合所有資料，用繁體中文撰寫分析報告，包含：
            - 市場整體情緒與解讀
            - 重要新聞摘要與潛在影響
            - 各持倉股價與損益概況（需計算損益百分比）
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

            根據使用者的問題，自主決定調用哪些工具後再回答。
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
git commit -m "refactor(stock): rewrite StockAiService with Tool Calling for agentic daily report"
```

---

## Task 4：刪除 StockReportService

**Files:**
- Delete: `src/main/java/com/next/nexrailai/service/StockReportService.java`
- Delete: `src/test/java/com/next/nexrailai/service/StockReportServiceTest.java`

### Step 1：刪除檔案

```bash
git rm src/main/java/com/next/nexrailai/service/StockReportService.java
git rm src/test/java/com/next/nexrailai/service/StockReportServiceTest.java
```

### Step 2：確認沒有其他地方引用 StockReportService

```bash
grep -r "StockReportService" src/
```
Expected: 找到引用在 `StockScheduler`、`StockAdminController`、`LineService` — 這些會在 Task 5 修改。

### Step 3：Commit

```bash
git commit -m "refactor(stock): remove StockReportService replaced by agentic StockAiService"
```

---

## Task 5：更新 StockScheduler、StockAdminController、LineService

**Files:**
- Modify: `src/main/java/com/next/nexrailai/scheduled/StockScheduler.java`
- Modify: `src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java`
- Modify: `src/main/java/com/next/nexrailai/service/LineService.java`

### Step 1：更新 StockScheduler

將 `StockReportService` 依賴改為 `StockAiService`：

```java
// 移除
private final StockReportService stockReportService;

// 改為（已存在則確認）
private final StockAiService stockAiService;

// sendDailyReport() 方法內：
// 舊
String report = stockReportService.buildDailyReport();
// 新
String report = stockAiService.generateDailyReport();
```

完整 `StockScheduler.java`：

```java
package com.next.nexrailai.scheduled;

import com.linecorp.bot.messaging.model.TextMessage;
import com.next.nexrailai.aspect.DistributedLock;
import com.next.nexrailai.config.StockProperties;
import com.next.nexrailai.service.LineMessageService;
import com.next.nexrailai.service.StockAiService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class StockScheduler {

    private final StockAiService stockAiService;
    private final LineMessageService lineMessageService;
    private final StockProperties stockProperties;

    @Scheduled(cron = "0 0 8 * * MON-FRI", zone = "Asia/Taipei")
    @DistributedLock(key = "stock-daily-report")
    public void sendDailyReport() {
        log.info(">>>> [Stock Scheduler] 開始產生每日美股報告");
        try {
            String report = stockAiService.generateDailyReport();
            lineMessageService.pushMessage(
                stockProperties.ownerLineUserId(),
                new TextMessage(report)
            );
            log.info(">>>> [Stock Scheduler] 每日美股報告已發送");
        } catch (Exception e) {
            log.error(">>>> [Stock Scheduler] 發送每日報告失敗", e);
        }
    }
}
```

### Step 2：更新 StockAdminController

在 `/report/trigger` 端點改呼叫 Agent，移除 `StockReportService` 依賴：

```java
// 移除注入
private final StockReportService stockReportService;

// trigger endpoint 改為
@PostMapping("/report/trigger")
public ResponseEntity<String> triggerReport() {
    log.info(">>>> [Stock Admin] 手動觸發每日報告");
    String report = stockAiService.generateDailyReport();
    lineMessageService.pushMessage(
        stockProperties.ownerLineUserId(),
        new TextMessage(report)
    );
    return ResponseEntity.ok("報告已發送");
}
```

確認 `StockAdminController` 已注入 `StockAiService`（透過 `@RequiredArgsConstructor`），移除 `StockReportService` 的 `final` 欄位宣告。

### Step 3：更新 LineService

找到 `handleStockCommand` 中：

```java
// 舊
String report = stockReportService.buildDailyReport();

// 新
String report = stockAiService.generateDailyReport();
```

移除 `StockReportService` 的 `final` 欄位及 Constructor 參數（如使用 `@RequiredArgsConstructor`，直接刪欄位宣告即可）。

### Step 4：執行全部測試，確認無回歸

```bash
./mvnw test -pl .
```
Expected: 所有 stock 相關測試 PASS，既有測試無回歸

### Step 5：Commit

```bash
git add src/main/java/com/next/nexrailai/scheduled/StockScheduler.java \
        src/main/java/com/next/nexrailai/controller/admin/StockAdminController.java \
        src/main/java/com/next/nexrailai/service/LineService.java
git commit -m "refactor(stock): update Scheduler, AdminController, LineService to use StockAiService.generateDailyReport()"
```

---

## Task 6：驗收

### Step 1：執行全部測試

```bash
./mvnw test -pl .
```
Expected: 所有測試 PASS，零錯誤

### Step 2：確認編譯乾淨

```bash
./mvnw clean compile -pl .
```
Expected: BUILD SUCCESS，無 warning（特別確認無 `StockReportService` 殘留引用）

### Step 3：手動觸發 Agent（本地測試）

```bash
# 手動觸發報告，觀察 Agent 是否自主呼叫多個工具
curl -X POST http://localhost:8089/api/admin/stock/report/trigger \
  -H "Authorization: Bearer <JWT_TOKEN>"
```

觀察 log 應出現：
```
>>>> [Stock AI] 開始產生每日美股分析報告
>>>> [Stock Tools] 查詢持倉清單
>>>> [Stock Tools] 查詢股價: AAPL
>>>> [Stock Tools] 查詢個股新聞: AAPL
...（依倉位數量重複）
>>>> [Stock Tools] 查詢大盤新聞
>>>> [Stock Tools] 查詢恐慌貪婪指數
>>>> [Stock AI] 每日美股分析報告產生完成
```

### Step 4：Final Commit（若有遺漏）

```bash
git add .
git commit -m "feat(stock): complete agentic stock agent with Tool Calling"
```

---

## 驗收標準

- [ ] `StockReportService` 已刪除，無任何引用殘留
- [ ] `StockTools` 包含 5 個 `@Tool` 方法且測試通過
- [ ] `StockAiService.generateDailyReport()` 使用 `.tools(stockTools)` 呼叫 Agent
- [ ] `StockAiService.chat()` 同樣使用 `.tools(stockTools)`，支援 ChatMemory
- [ ] Log 中可見 Agent 自主呼叫多個工具（非固定順序）
- [ ] `POST /api/admin/stock/report/trigger` 能成功觸發 Agent 並發送 LINE 訊息
- [ ] 非 owner 使用 `/stock` 無任何回應（靜默忽略）
- [ ] `./mvnw test` 全部通過，無回歸問題
