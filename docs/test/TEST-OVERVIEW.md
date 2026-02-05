# 測試總覽 (Test Overview)

## 專案資訊
- **語言**: Java 21
- **框架**: Spring Boot 3.5.9
- **測試框架**: JUnit 5, Mockito, Testcontainers

## 測試環境

### 單元測試 (Unit Tests)
- **測試框架**: JUnit 5 + Mockito
- **Mocking 策略**: Mock 所有外部依賴（Repository, API Client, Redis）
- **隔離性**: 每個測試獨立執行，不依賴外部服務

### 整合測試 (Integration Tests)
- **資料庫**: H2 (In-memory) - 模擬 PostgreSQL
- **Mocking**: `@MockBean` 模擬內部 Bean（如 LINE API, TDX API）
- **測試框架**: Spring Boot Test (`@SpringBootTest`, `@AutoConfigureMockMvc`)
- **Testcontainers**: 用於啟動真實 Redis 環境（分散式鎖測試）

## 測試進度表

### 核心服務 (Core Services)

| 模組 | 狀態 | 測試覆蓋率 (估計) | 備註 |
| --- | --- | --- | --- |
| `ScheduleService` | ✅ 完成 | 100% | Strategy delegation verified |
| `ThsrTicketService` | ✅ 完成 | 90% | Logic verified |
| `LineMessageService` | ✅ 完成 | 100% | Verified via LineServiceTest |
| `LineService` | ✅ 完成 | 100% | Logic verified |
| `Constant` (Enums) | ✅ 完成 | 100% | EnumUtil verified |
| `TicketMonitorExecutor`| ✅ 完成 | 100% | Logic verified |
| `ReminderExecutor` | ✅ 完成 | 100% | Logic verified |
| `RateLimitService` | ✅ 完成 | 100% | Unit tests (9) + Integration tests (7) |
| `SystemConfigService` | ✅ 完成 | 100% | Cache logic verified |
| `TdxService` | ✅ 完成 | 80% | API calling verified (Mock) |
| `AiService` | ✅ 完成 | 80% | Chat flow verified (Mock) |

### 分散式鎖 (Distributed Lock)

| 模組 | 狀態 | 測試類型 | 測試覆蓋率 | 備註 |
| --- | --- | --- | --- | --- |
| `DistributedLockAspect` | ✅ 完成 | 單元測試 | 100% | 10 tests - Mockito |
| `DistributedLockAspect` | ✅ 完成 | 整合測試 | 100% | 8 tests - Testcontainers + Real Redis |
| `RedissonConfig` | ⚠️ 部分完成 | 單元測試 | 50% | Basic bean creation test |
| `RedissonConfig` | ✅ 完成 | 整合測試 | 100% | Verified via DistributedLockIntegrationTest |

### 整合測試場景 (Integration Test Scenarios)

| 場景 | 狀態 | 測試檔案 | 說明 |
| --- | --- | --- | --- |
| **完整搜尋流程** | ✅ 完成 | `ThsrSearchIntegrationTest` | 使用者發送訊息 → AI (Mock) → TDX (Simulated) → Flex Message |
| **排程任務執行** | ✅ 完成 | `ScheduleTaskIntegrationTest` | 資料庫 Task → ScheduleService → LINE 推播 |
| **管理員 API** | ✅ 完成 | `AdminApiIntegrationTest` | JWT 驗證 → 廣播/設定修改 → DB 狀態驗證 |
| **分散式鎖** | ✅ 完成 | `DistributedLockIntegrationTest` | 真實 Redis 環境 → 併發控制 → Watchdog 驗證 |
| **流量限制 (Token Bucket)** | ✅ 完成 | `RateLimitServiceIntegrationTest` | 真實 Redis 環境 → Redisson RRateLimiter → 令牌桶演算法 |

### Controllers (Pending)

| 模組 | 狀態 | 備註 |
| --- | --- | --- |
| `AdminBroadcastController` | ⚪ 未開始 | |
| `AiController` | ⚪ 未開始 | |
| ... | ... | |

## 測試規範

### 單元測試規範
1. **測試範圍**: 針對 Service 層邏輯，Mock 所有外部依賴 (Repository, API Client)。
2. **命名規範**: `should[ExpectedBehavior]_When[Condition]`。
3. **工具**: 使用 `Mockito` 進行 Mocking，`Assertions` 進行驗證。

### 整合測試規範
1. **獨立性**: 每個測試類別啟動獨立的 Context 或使用 `@DirtiesContext`。
2. **外部依賴**: 必須 Mock 所有外部依賴 (如 TDX, LINE 服務)，避免真實 API 呼叫。
3. **資料庫**: 使用 H2 記憶體資料庫，每次測試前清空。
4. **Testcontainers**: 用於需要真實環境的測試（如 Redis、資料庫整合測試）。

---

## 已完成的測試

### 單元測試 (Unit Tests)
- `EnumUtilTest`
- `ScheduleTaskExecutorFactoryTest`
- `ReminderExecutorTest`
- `TicketMonitorExecutorTest`
- `ScheduleServiceTest`
- `ThsrTicketServiceTest`
- `LineServiceTest`
- `RateLimitServiceTest`
- `SystemConfigServiceTest`
- `TdxServiceTest`
- `AiServiceTest`
- `DistributedLockAspectTest` (10 tests)
- `RedissonConfigTest` (Basic)

### 整合測試 (Integration Tests)
- `ThsrSearchIntegrationTest` - 驗證搜尋流程與流量限制
- `ScheduleTaskIntegrationTest` - 驗證排程任務掃描、執行、票務監控、過期與錯誤重試機制
- `AdminApiIntegrationTest` - 驗證管理員登入、JWT 驗證、廣播與系統設定 API
- `DistributedLockIntegrationTest` (8 tests - Testcontainers)
- `RateLimitServiceIntegrationTest` (7 tests - Testcontainers)

---

## 整合測試詳細資訊

### 1. `ThsrSearchIntegrationTest`
**測試環境**: Spring Boot Test + H2 Database + MockBean
**測試場景**: 完整搜尋流程
**涵蓋範圍**:
- 使用者訊息接收 → AI 解析 (Mock) → TDX API 查詢 (Simulated)
- Flex Message 組裝與回應
- 流量限制驗證

### 2. `ScheduleTaskIntegrationTest`
**測試環境**: Spring Boot Test + H2 Database + MockBean
**測試場景**: 排程任務執行
**涵蓋範圍**:
- 資料庫 Task 掃描
- ScheduleService 執行
- 票務監控與提醒
- 過期與錯誤重試機制
- LINE 推播驗證

### 3. `AdminApiIntegrationTest`
**測試環境**: Spring Boot Test + H2 Database + MockMvc
**測試場景**: 管理員 API
**涵蓋範圍**:
- JWT 驗證與登入
- 廣播訊息發送
- 系統設定修改
- 資料庫狀態驗證

### 4. `DistributedLockIntegrationTest`
**測試環境**: Testcontainers (Redis 7-alpine)
**測試數量**: 8 個測試案例
**測試類型**: 真實 Redis 環境下的整合測試

#### 涵蓋範圍
1. ✅ 成功取得鎖並執行業務邏輯
2. ✅ 無法取得鎖時跳過執行（模擬其他實例持有鎖）
3. ✅ 多執行緒併發控制 (5 個執行緒，只有 1 個成功)
4. ✅ Watchdog 自動續約（長時間執行任務 4 秒）
5. ✅ 異常處理（業務邏輯拋出異常時鎖仍被釋放）
6. ✅ 鎖釋放後其他執行緒可以取得
7. ✅ 鎖的 key 包含特殊字元
8. ✅ 可重入鎖（同一執行緒多次取得同一把鎖）

#### 技術亮點
- **真實環境**: 使用 Testcontainers 啟動真實 Redis 容器
- **併發測試**: 使用 `CountDownLatch` 和 `ExecutorService` 模擬多實例環境
- **Watchdog 驗證**: 實際驗證 Redisson Watchdog 自動續約機制
- **時序控制**: 精確控制執行緒執行順序，確保測試穩定性

### 5. `RateLimitServiceIntegrationTest`
**測試環境**: Testcontainers (Redis 7-alpine)
**測試數量**: 7 個測試案例
**測試類型**: 真實 Redis 環境下的整合測試

#### 涵蓋範圍
1. ✅ 基本令牌桶功能 - 消耗與補充
2. ✅ 令牌桶初始化 - 新使用者完整額度
3. ✅ 併發場景 - 多執行緒同時消耗令牌 (20 執行緒搶 10 個額度)
4. ✅ 動態額度管理 - setQuota 與 addQuota
5. ✅ 每月額度（通知額度）- 獨立運作驗證
6. ✅ 純令牌桶模式 - Key 固定不變（無日期後綴）
7. ✅ 令牌桶容量限制 - 不會無限累積

#### 技術亮點
- **真實環境**: 使用 Testcontainers 啟動真實 Redis 容器
- **Redisson RRateLimiter**: 驗證令牌桶演算法的原子性
- **併發測試**: 驗證 20 執行緒併發時不會超發額度
- **純令牌桶模式**: 驗證 Key 不包含日期後綴，支援跨日累積
- **動態額度調整**: 驗證 `delete() + trySetRate()` 模式正確性

---

## 測試統計

### 總覽
- **單元測試**: 13 個測試類別
- **整合測試**: 5 個測試類別
- **總測試案例數**: 113+ 個
- **測試通過率**: 100% ✅

### 測試覆蓋率 (估計)
- **Service 層**: ~95%
- **Repository 層**: 透過整合測試覆蓋
- **Controller 層**: 部分完成（整合測試）
- **分散式鎖**: 100%