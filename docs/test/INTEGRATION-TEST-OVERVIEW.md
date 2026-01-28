# 整合測試總覽 (Integration Test Overview)

## 測試環境
- **資料庫**: H2 (In-memory) - 模擬 PostgreSQL
- **Mocking**: `@MockBean` (模擬內部 Bean) 搭配 Mockito
- **測試框架**: Spring Boot Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)

## 測試進度表

| 場景 | 狀態 | 說明 |
| --- | --- | --- |
| **完整搜尋流程** | ✅ 完成 | 使用者發送訊息 -> AI (Mock) -> TDX (Simulated) -> Flex Message |
| **排程任務執行** | ✅ 完成 | 資料庫 Task -> ScheduleService -> LINE 推播 |
| **管理員 API** | ✅ 完成 | JWT 驗證 -> 廣播/設定修改 -> DB 狀態驗證 |

## 測試規範
1. **獨立性**: 每個測試類別啟動獨立的 Context 或使用 `@DirtiesContext`。
2. **外部依賴**: 必須 Mock 所有外部依賴 (如 TDX, LINE 服務)，避免真實 API 呼叫。
3. **資料庫**: 使用 H2 記憶體資料庫，每次測試前清空。

---

## 已完成的測試
- `ThsrSearchIntegrationTest`: 驗證搜尋流程與流量限制。
- `ScheduleTaskIntegrationTest`: 驗證排程任務掃描、執行、票務監控、過期與錯誤重試機制。
- `AdminApiIntegrationTest`: 驗證管理員登入、JWT 驗證、廣播與系統設定 API。
