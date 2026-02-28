# Stock Agent 設計文件

**日期**：2026-02-27（架構更新：2026-02-28）
**開發模式**：褐地 Brownfield（新增模組至既有 NexRailAI）
**功能定位**：Owner 專屬美股追蹤 Agent，整合於現有 LINE Bot

---

## 需求摘要

- 長線投資者，不需要即時盯盤，只需每日一次市場摘要
- 透過 `/stock` 前綴在 LINE 與 AI 對話，或透過後台 API 管理持倉
- 只有 owner 可使用，其他 10+ 位使用者不可見此功能

---

## 整體架構（Agentic 版）

```
LINE Bot (/stock 指令)
    ↓
LineService                   ← 攔截 /stock 前綴，owner-only 判斷
    ↓
StockAiService                ← 持有 ChatClient + StockTools，決定呼叫哪些工具
    ↓ (Tool Calling / Function Calling)
StockTools                    ← @Tool 方法集合，供 Gemini 自主調用
    ├── getPortfolioPositions()   → StockPositionService → PostgreSQL
    ├── getStockPrice(symbol)     → StockApiService → Alpha Vantage
    ├── getCompanyNews(symbol)    → StockApiService → Finnhub (個股新聞)
    ├── getMarketNews()           → StockApiService → Finnhub (大盤新聞)
    └── getFearAndGreedIndex()    → StockApiService → CNN Fear & Greed
    ↓
Gemini 2.5 Flash              ← 匯總資料、自行分析撰寫報告
    ↓
StockScheduler                ← @Scheduled 每日 08:00，推播報告給 owner
```

### 與舊架構的關鍵差異

| 舊（Pipeline）| 新（Agentic）|
|---|---|
| `StockReportService` 固定格式化字串 | Gemini 自主決定分析深度與格式 |
| 程式碼控制要查哪些 API | Agent 根據倉位自行決定查哪些 API |
| 新聞只有大盤新聞 | 可查個股新聞（更精準） |
| 無法動態調整報告內容 | 可依市場情況調整報告重點 |

### 現有組件複用

| 現有組件 | 複用方式 |
|---------|---------|
| `LineMessageService` | 直接呼叫 `pushMessage(ownerId, message)` 發送報告 |
| `@DistributedLock` | Scheduler 套用，防多實例重複執行 |
| `RestClient` | 呼叫 Alpha Vantage / Finnhub REST API |
| `ChatClient` | StockAiService 注入，配不同 system prompt |
| Spring Security + JWT | 後台 API 直接沿用，不需新增認證 |
| `StockPositionService` | StockTools 注入，供 Agent 查詢持倉 |

---

## 資料模型

### `stock_positions` 資料表（不變）

```sql
CREATE TABLE stock_positions (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(10)    NOT NULL UNIQUE,  -- AAPL, NVDA
    shares      DECIMAL(12, 4) NOT NULL,          -- 股數（支援零股）
    cost_price  DECIMAL(12, 4) NOT NULL,          -- 平均成本（USD）
    note        VARCHAR(255),                      -- 備註（可選）
    created_at  TIMESTAMP      NOT NULL,
    updated_at  TIMESTAMP      NOT NULL
);
```

---

## 外部 API

| 需求 | API | 免費額度 | 備註 |
|------|-----|---------|------|
| 股價（收盤價） | Alpha Vantage | 25 req/day | 每日一報綽綽有餘 |
| 個股新聞 | Finnhub `/company-news` | 60 req/min | 依 symbol 查詢 |
| 大盤新聞 | Finnhub `/news` | 60 req/min | category=general |
| 市場恐慌貪婪指數 | CNN Fear & Greed | 無限制 | 非官方 endpoint，穩定多年 |

---

## Agent Tools 設計

```java
@Component
public class StockTools {

    @Tool(description = "查詢目前持倉清單，回傳各股票代碼、持股數、平均成本")
    public String getPortfolioPositions() { ... }

    @Tool(description = "查詢指定股票的最新收盤價（USD）")
    public double getStockPrice(
        @ToolParam(description = "股票代碼，例如 AAPL、NVDA") String symbol) { ... }

    @Tool(description = "查詢指定公司的最新新聞（最近 3 則）")
    public String getCompanyNews(
        @ToolParam(description = "股票代碼，例如 AAPL") String symbol) { ... }

    @Tool(description = "查詢整體市場最新新聞（最新 5 則）")
    public String getMarketNews() { ... }

    @Tool(description = "查詢 CNN 恐慌貪婪指數，反映整體市場情緒")
    public String getFearAndGreedIndex() { ... }
}
```

---

## AI Prompt 設計

### 每日報告 Prompt（generateDailyReport）

```
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
- 各持倉股價與損益概況
- 整體投資組合評估

保持客觀，不給出明確買賣建議。
```

### 即時對話 Prompt（chat）

```
你是一位美股投資分析助理，可使用以下工具查詢即時資訊：
- getPortfolioPositions：查我的持倉
- getStockPrice：查個股股價
- getCompanyNews：查個股新聞
- getMarketNews：查大盤新聞
- getFearAndGreedIndex：查市場情緒

根據使用者的問題，自主決定調用哪些工具後再回答。
保持客觀，不給出明確買賣建議，用繁體中文回答。
```

---

## 每日報告格式（AI 自由生成，示意）

**發送時間**：每日 08:00 台灣時間（美股收盤後 3~4 小時，數據完整穩定）

```
📊 美股日報 2026/02/28

😨 市場情緒：恐慌 (Fear 32)
整體市場仍處於恐慌情緒，但短期恐慌往往是長線佈局的機會。

📰 市場重點
• Fed 官員暗示 3 月不降息，科技股承壓
• 美債殖利率上升，成長股面臨估值壓力

💼 持倉分析

AAPL（蘋果）
成本 $182.5 → 現價 $180.1（-1.3%，-$24）
近期新聞：供應鏈傳出擴大印度產能，長期正向但短期受大盤拖累。

NVDA（輝達）
成本 $520.0 → 現價 $551.2（+6.0%，+$156）
近期新聞：財報超預期，資料中心需求持續強勁，短線動能佳。

💰 整體損益：+$132（+1.2%）
```

---

## 後台 REST API（不變）

掛載於現有 `/api/v1/admin/` 路徑下，沿用 JWT 認證：

```
GET    /api/v1/admin/stock/positions             → 查全部持倉
POST   /api/v1/admin/stock/positions             → 新增持倉
PUT    /api/v1/admin/stock/positions/{symbol}    → 更新（股數/成本）
DELETE /api/v1/admin/stock/positions/{symbol}    → 刪除持倉

POST   /api/v1/admin/stock/report/trigger        → 手動觸發報告（測試用）
```

---

## 權限控管（不變）

`application.yml` 新增：

```yaml
app:
  stock:
    owner-line-user-id: ${STOCK_OWNER_LINE_USER_ID}
```

`LineService` 收到 `/stock` 指令時，比對 `lineUserId`：
- 符合 → 執行功能
- 不符合 → 靜默忽略（不回應，不暴露功能存在）

---

## LINE 互動指令

| 指令 | 功能 |
|------|------|
| `/stock` | 手動觸發當日報告（Agent 自主查詢並分析） |
| `/stock NVDA 今天怎麼了` | 問 AI 特定股票分析（Agent 自主決定查哪些工具） |

持倉管理一律透過後台 API，不走 LINE 指令。

---

## 最終檔案清單

```
src/main/java/com/next/nexrailai/
├── config/
│   └── StockProperties.java              [已完成]
├── jpa/entity/
│   └── StockPosition.java                [已完成]
├── jpa/repository/
│   └── StockPositionRepository.java      [已完成]
├── jpa/service/
│   └── StockPositionService.java         [已完成]
├── dto/
│   ├── StockPositionRequest.java         [已完成]
│   └── StockPositionResponse.java        [已完成]
├── service/
│   ├── StockApiService.java              [已完成，新增 getCompanyNews]
│   ├── StockTools.java                   [新增 - Agent Tools]
│   └── StockAiService.java               [改寫 - 整合 Tools]
├── scheduled/
│   └── StockScheduler.java               [修改 - 改呼叫 Agent]
└── controller/admin/
    └── StockAdminController.java         [修改 - 改呼叫 Agent]

src/main/java/com/next/nexrailai/service/
└── LineService.java                      [修改 - 移除 StockReportService 依賴]

已刪除：
└── StockReportService.java               [刪除 - 由 Agent 取代]
```
