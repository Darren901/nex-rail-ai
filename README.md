# NexRailAI - 智能高鐵助理

NexRailAI 是一個結合生成式 AI (Google Gemini) 與 LINE Messaging API 的智慧高鐵旅程助理。不僅提供自然語言對話，更深度整合 LINE Flex Message 介面，將高鐵時刻與訂票資訊轉化為精美的視覺化卡片。

本專案展示了如何使用 Spring Boot 3 與 Spring AI 框架，透過 Function Calling 技術將 LLM 與外部 API (TDX 運輸資料) 及背景排程任務進行深度整合，實現從查詢、監控到提醒的一站式服務。

## 主要功能

*   **自然語言互動**
    支援像真人一樣的流暢對話，無需死記指令 (例如: "幫我查明天早上九點去高雄的車，我要靠窗")。
*   **Flex Message 介面**
    告別純文字回覆！系統自動將查詢結果轉換為美觀的 LINE Flex Message 卡片，清楚呈現車次、發車/抵達時間、行駛時間與剩餘座位狀態。
*   **智慧刷票監控**
    針對熱門時段或已售罄的班次，可指令 AI 進行「監控任務」。系統將在背景持續掃描座位釋出狀況，一旦有票立即推播通知。
*   **行程與訂票提醒**
    設定未來的行程後，系統可建立排程任務，在開放訂票日（如乘車日前 29 天）或指定時間發送提醒，助您搶得先機。
*   **常用班次與偏好記憶**
    AI 具備長期記憶能力，能記住您的常用路線（如「每週五晚上回台南」）或偏好（如「只搭直達車」），下次查詢更快速。
*   **完整票務資訊**
    依據起訖站自動計算標準車廂、商務車廂及自由座票價，並提供直達高鐵官方訂票系統的 Deep Link，點擊卡片即可開始訂票。
*   **流量控制**
    內建 Rate Limiter，針對使用者進行每日/每月的使用額度控管，防止 API 濫用。
*   **系統管理與廣播**
    提供管理員專屬 API，支援動態調整系統參數與發送全站推播訊息，並具備 JWT 安全認證機制。

## 技術棧

*   **核心語言**: Java 21
*   **應用框架**: Spring Boot 3.5.9
*   **AI 整合**: Spring AI (Google Gemini `gemini-2.5-flash`)
*   **資料庫**: PostgreSQL (資料儲存), Redis (快取、對話 Session 與排程鎖)
*   **通訊介面**: LINE Bot SDK (整合 Flex Message)
*   **外部服務**: TDX Transport API (交通部運輸資料流通服務)
*   **建置工具**: Maven

## 快速開始

### 前置需求

*   Java 21 SDK
*   Docker & Docker Compose (用於啟動 PostgreSQL 與 Redis)
*   LINE Official Account (取得 Channel Token & Secret)
*   Google Cloud Project (取得 Gemini API Key)
*   TDX 帳號 (取得 Client ID & Secret)

### 安裝與執行

1.  **Clone 專案**
    ```bash
    git clone https://github.com/your-username/NexRailAI.git
    cd NexRailAI
    ```

2.  **啟動基礎設施 (DB & Redis)**
    ```bash
    docker-compose up -d
    ```

3.  **設定環境變數**
    請確保以下環境變數已設定 (可透過 IDE 或系統環境變數)：
    
    | 變數名稱 | 描述 |
    |Str |Str |
    | `GEMINI_API_KEY` | Google Gemini API 金鑰 |
    | `LINE_BOT_TOKEN` | LINE Channel Access Token |
    | `LINE_BOT_SECRET` | LINE Channel Secret |
    | `TDX_CLIENT_ID` | TDX API Client ID |
    | `TDX_CLIENT_SECRET` | TDX API Client Secret |
    | `JWT_SECRET` | JWT 簽章密鑰 |
    | `JWT_EXPIRATION_MILLISECONDS` | JWT 過期時間 (ms) |

4.  **編譯與執行**
    ```bash
    ./mvnw spring-boot:run
    ```

### 測試

本專案包含完整的測試套件，涵蓋核心功能、單元測試與整合流程：

*   **搜尋整合測試 (ThsrSearchIntegrationTest)**: 驗證使用者對話、AI 意圖解析、TDX 資料查詢至 Flex Message 回覆的完整流程。
*   **排程任務測試 (ScheduleTaskIntegrationTest)**: 驗證自動查票監控、任務狀態流轉 (Pending -> Completed/Expired) 及錯誤重試機制。
*   **管理員 API 測試 (AdminApiIntegrationTest)**: 驗證後台登入、JWT Token 簽發與驗證、系統廣播及參數設定功能。
*   **核心服務單元測試**: 涵蓋 `TdxService`、`LineService`、`ThsrTicketService`、`RateLimitService` 等核心邏輯驗證。

執行所有測試：
```bash
./mvnw test
```

## 專案結構

*   `src/main/java/com/next/nexrailai/controller`: REST API 與 LINE Webhook 入口
*   `src/main/java/com/next/nexrailai/service`: 核心業務邏輯 (AI 對話、TDX 整合、排程服務)
*   `src/main/java/com/next/nexrailai/component`: AI Function Tools 定義
*   `src/main/java/com/next/nexrailai/utils`: Flex Message 建構工具 (`FlexMessageUtil`)
*   `src/main/java/com/next/nexrailai/scheduled`: 背景排程任務 (刷票與提醒)
*   `src/main/java/com/next/nexrailai/jpa`: 資料庫 Entity 與 Repository
*   `src/test/java/com/next/nexrailai/integration`: 整合測試程式碼
