# Schedule Task Integration Test Cases

## 測試目標
驗證排程服務 (`ScheduleService`) 能正確掃描資料庫中的待辦任務，並透過 `TicketMonitorExecutor` 執行查票邏輯，最後正確更新任務狀態與發送 LINE 通知。

## 前置條件
- 使用 H2 資料庫模擬任務持久化
- Mock `ThsrTicketService` 模擬 TDX 回傳結果
- Mock `LineMessageService` 驗證推播
- Mock `SystemConfigService` 設定重試間隔

## 測試案例清單

### 1. 正常流程 - 監控到有票 (Happy Path)
- [x] **TC01**: 當 `TICKET_MONITOR` 任務到期且 TDX 回傳「有位」時
  - **Arrange**: 
    - 資料庫插入一筆 `PENDING` 且 `triggerTime` <= Now 的監控任務
    - Mock `ThsrTicketService` 回傳含有效座位的 `ThsrSummaryDTO`
  - **Act**: 觸發 `scheduleService.processScheduledTasks()`
  - **Assert**:
    - 驗證 `lineMessageService.pushMessage` 被呼叫 (Flex Message)
    - 驗證該任務狀態變更為 `COMPLETED`

### 2. 正常流程 - 監控無票 (Retry)
- [x] **TC02**: 當 `TICKET_MONITOR` 任務到期但 TDX 回傳「客滿」時
  - **Arrange**:
    - 資料庫插入一筆 `PENDING` 監控任務
    - Mock `ThsrTicketService` 回傳狀態皆為 "客滿" 的結果
    - Mock `SystemConfigService` 回傳重試間隔 (例如 60秒)
  - **Act**: 觸發 `scheduleService.processScheduledTasks()`
  - **Assert**:
    - `lineMessageService.pushMessage` **不應**被呼叫
    - 驗證任務狀態仍為 `PENDING` (或未變成終態)
    - 驗證任務的 `triggerTime` 被展延 (Now + 60s)

### 3. 邊界案例 - 任務過期 (Expired)
- [x] **TC03**: 當監控任務的目標發車時間已過
  - **Arrange**:
    - 資料庫插入一筆 `PENDING` 任務，其 Payload 中的 `date/time` 早於當前時間
  - **Act**: 觸發 `scheduleService.processScheduledTasks()`
  - **Assert**:
    - 驗證 `lineMessageService.pushMessage` 被呼叫 (發送過期通知文字)
    - 驗證該任務狀態變更為 `EXPIRED`

### 4. 異常流程 - API 錯誤 (Error Handling)
- [x] **TC04**: 當 TDX API 拋出異常 (HttpClientErrorException)
  - **Arrange**:
    - Mock `ThsrTicketService` 拋出 500 或 400 錯誤
  - **Act**: 觸發 `scheduleService.processScheduledTasks()`
  - **Assert**:
    - 驗證任務 `triggerTime` 被展延 (預設錯誤重試時間，如 5分鐘)
    - 驗證 Log 紀錄錯誤 (可選)
    - 任務狀態不應變成 `FAILED` (除非是致命錯誤，根據程式邏輯目前是重試)
