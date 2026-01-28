# 測試總覽 (Test Overview)

## 專案資訊
- **語言**: Java 21
- **框架**: Spring Boot 3.5.9
- **測試框架**: JUnit 5, Mockito

## 測試進度表

### 核心服務 (Refactored Components - Priority)

| 模組 | 狀態 | 測試覆蓋率 (估計) | 備註 |
| --- | --- | --- | --- |
| `ScheduleService` | ✅ 完成 | 100% (Core) | Strategy delegation verified |
| `ThsrTicketService` | ✅ 完成 | 90% | Logic verified, missing integration with real API |
| `LineMessageService` | ✅ 完成 | 100% (Core) | Verified via `LineServiceTest` & Executors |
| `LineService` | ✅ 完成 | 100% (Core) | `decideMessage`, `handleUserMessage` verified |
| `Constant` (Enums) | ✅ 完成 | 100% | `EnumUtil` verified |
| `TicketMonitorExecutor`| ✅ 完成 | 100% | Monitor logic, retry, expire logic verified |
| `ReminderExecutor` | ✅ 完成 | 100% | Reminder logic verified |
| `ScheduleTaskExecutorFactory`| ✅ 完成 | 100% | Factory logic verified |

### 其他服務 (Pending)

| 模組 | 狀態 | 備註 |
| --- | --- | --- |
| `AiService` | ⚪ 未開始 | |
| `TdxService` | ⚪ 未開始 | |
| `RateLimitService` | ⚪ 未開始 | |
| `SystemConfigService` | ⚪ 未開始 | |

### Controllers (Pending)

| 模組 | 狀態 | 備註 |
| --- | --- | --- |
| `AdminBroadcastController` | ⚪ 未開始 | |
| `AiController` | ⚪ 未開始 | |
| ... | ... | |

## 測試規範
1. **單元測試 (Unit Tests)**: 針對 Service 層邏輯，Mock 所有外部依賴 (Repository, API Client)。
2. **命名規範**: `should[ExpectedBehavior]_When[Condition]`。
3. **工具**: 使用 `Mockito` 進行 Mocking，`Assertions` 進行驗證。

---

## 已完成的測試
- `EnumUtilTest`
- `ScheduleTaskExecutorFactoryTest`
- `ReminderExecutorTest`
- `TicketMonitorExecutorTest`
- `ScheduleServiceTest`
- `ThsrTicketServiceTest`
- `LineServiceTest`
