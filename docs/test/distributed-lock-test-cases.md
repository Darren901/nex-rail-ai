# 分散式鎖測試案例 (Distributed Lock Test Cases)

## 模組資訊
- **主要類別**: `DistributedLockAspect`, `RedissonConfig`
- **測試目標**: 驗證 Redisson 分散式鎖的正確性、Watchdog 機制、併發控制

---

## DistributedLockAspect 測試案例

### 正常情況 (Happy Path)

[ ] [正常情況] 成功取得鎖並執行業務邏輯
**測試資料**
- 模擬方法帶有 `@DistributedLock(key = "test-task")`
- RLock.tryLock() 返回 true
- joinPoint.proceed() 正常執行並返回 "success"

**預期結果**
- 取得鎖成功
- 業務邏輯被執行
- 返回值為 "success"
- 鎖被正確釋放
- 記錄 "取得鎖成功" 和 "釋放鎖成功" 日誌

---

[ ] [正常情況] 無法取得鎖時跳過執行
**測試資料**
- 模擬方法帶有 `@DistributedLock(key = "test-task")`
- RLock.tryLock() 返回 false (鎖已被其他實例持有)

**預期結果**
- 取得鎖失敗
- 業務邏輯**不被執行**
- 返回值為 null
- 記錄 "無法取得鎖，跳過執行" 警告日誌

---

[ ] [正常情況] Watchdog 自動續約機制 (leaseTime = -1)
**測試資料**
- 模擬方法帶有 `@DistributedLock(key = "long-task")`
- RLock.tryLock(-1, TimeUnit.MILLISECONDS) 被正確呼叫

**預期結果**
- tryLock 被呼叫時 leaseTime 參數為 -1
- 驗證 Watchdog 自動續約被啟用

---

### 邊界值 (Edge Cases)

[ ] [邊界值] 業務邏輯執行時間超過 Watchdog 超時時間
**測試資料**
- 模擬方法帶有 `@DistributedLock(key = "slow-task")`
- joinPoint.proceed() 模擬執行 35 秒 (超過 30 秒 Watchdog 超時)
- RLock.tryLock() 返回 true

**預期結果**
- 鎖在執行期間不會過期 (Watchdog 自動續約)
- 業務邏輯完整執行
- 鎖被正確釋放

---

[ ] [邊界值] 鎖的 key 包含特殊字元
**測試資料**
- `@DistributedLock(key = "task:with:colons")`
- `@DistributedLock(key = "task-with-dashes")`
- `@DistributedLock(key = "task_with_underscores")`

**預期結果**
- 鎖的完整 key 為 "lock:scheduled:task:with:colons"
- 特殊字元不影響鎖的取得與釋放

---

### 異常處理 (Error Handling)

[ ] [異常處理] 業務邏輯執行時拋出異常
**測試資料**
- 模擬方法帶有 `@DistributedLock(key = "error-task")`
- RLock.tryLock() 返回 true
- joinPoint.proceed() 拋出 RuntimeException("業務邏輯錯誤")

**預期結果**
- 異常被重新拋出
- 鎖仍然被正確釋放 (finally 區塊執行)
- 記錄 "執行失敗" 錯誤日誌

---

[ ] [異常處理] RedissonClient.getLock() 拋出異常
**測試資料**
- 模擬 RedissonClient.getLock() 拋出 RedisException("Redis 連線失敗")

**預期結果**
- 異常被拋出
- 方法執行失敗
- 記錄錯誤日誌

---

[ ] [異常處理] tryLock() 拋出 InterruptedException
**測試資料**
- 模擬 RLock.tryLock() 拋出 InterruptedException

**預期結果**
- 異常被重新拋出
- 記錄 "執行失敗" 錯誤日誌
- 執行緒中斷狀態被保留

---

[ ] [異常處理] unlock() 拋出 IllegalMonitorStateException
**測試資料**
- 模擬 RLock.unlock() 拋出 IllegalMonitorStateException
  (鎖已經被其他執行緒釋放)

**預期結果**
- 異常不應導致主流程失敗
- 記錄警告或錯誤日誌

---

### 並發與數據完整性 (Concurrency & Integrity)

[ ] [並發] 多執行緒同時嘗試取得同一把鎖
**測試資料**
- 啟動 5 個執行緒同時執行帶有 `@DistributedLock(key = "concurrent-task")` 的方法
- 每個執行緒嘗試取得鎖並執行業務邏輯

**預期結果**
- 只有 1 個執行緒成功取得鎖並執行
- 其他 4 個執行緒返回 null (跳過執行)
- 業務邏輯只被執行 1 次
- 鎖被正確釋放

---

[ ] [並發] 可重入鎖 (同一執行緒多次取得鎖)
**測試資料**
- 同一執行緒內巢狀呼叫帶有相同 `@DistributedLock(key = "reentrant-task")` 的方法

**預期結果**
- 第一次取得鎖成功
- 第二次取得鎖也成功 (Redisson RLock 支援可重入)
- 鎖的計數器正確遞增和遞減
- 最終鎖被完全釋放

---

[ ] [並發] 鎖釋放後其他執行緒可以取得
**測試資料**
- 執行緒 A 取得鎖 → 執行業務邏輯 → 釋放鎖
- 執行緒 B 在 A 釋放後嘗試取得同一把鎖

**預期結果**
- 執行緒 A 成功取得並釋放鎖
- 執行緒 B 在 A 釋放後成功取得鎖
- 兩個執行緒都正常執行業務邏輯

---

### 特殊情況 (Special Cases)

[ ] [特殊情況] isHeldByCurrentThread() 返回 false 時不釋放鎖
**測試資料**
- acquired = true
- lock.isHeldByCurrentThread() 返回 false (鎖已被其他執行緒持有或已過期)

**預期結果**
- unlock() 不被呼叫
- 避免釋放不屬於當前執行緒的鎖

---

[ ] [特殊情況] 業務邏輯返回 null
**測試資料**
- joinPoint.proceed() 返回 null

**預期結果**
- 方法正常執行並返回 null
- 鎖被正確釋放

---

---

## RedissonConfig 測試案例

### 正常情況 (Happy Path)

[ ] [正常情況] 正確建立 RedissonClient Bean
**測試資料**
- redis.host = "localhost"
- redis.port = 6379
- redis.password = ""

**預期結果**
- RedissonClient Bean 被成功建立
- 連線位址為 "redis://localhost:6379"
- 密碼為 null (空字串視為無密碼)
- LockWatchdogTimeout 設定為 30000ms

---

[ ] [正常情況] 使用自訂 Redis 連線資訊
**測試資料**
- redis.host = "redis.example.com"
- redis.port = 6380
- redis.password = "mypassword"

**預期結果**
- 連線位址為 "redis://redis.example.com:6380"
- 密碼為 "mypassword"
- 連線池大小為 64
- 最小閒置連線為 10

---

### 邊界值 (Edge Cases)

[ ] [邊界值] Redis 密碼為空字串
**測試資料**
- redis.password = ""

**預期結果**
- 密碼設定為 null
- 不影響 Redis 連線

---

[ ] [邊界值] Redis 密碼包含特殊字元
**測試資料**
- redis.password = "p@ssw0rd!#$%"

**預期結果**
- 密碼被正確設定
- 連線成功

---

### 異常處理 (Error Handling)

[ ] [異常處理] Redis 連線失敗
**測試資料**
- redis.host = "invalid-host"
- redis.port = 9999

**預期結果**
- RedissonClient 建立時不拋出異常 (延遲連線)
- 實際使用時拋出連線錯誤

---

### 配置驗證 (Configuration Validation)

[ ] [配置驗證] Watchdog 超時時間設定正確
**測試資料**
- 檢查 config.getLockWatchdogTimeout()

**預期結果**
- 返回 30000 (30 秒)

---

[ ] [配置驗證] 連線池參數設定正確
**測試資料**
- 檢查 config.useSingleServer() 的設定

**預期結果**
- ConnectionPoolSize = 64
- ConnectionMinimumIdleSize = 10
- Timeout = 3000ms
- RetryAttempts = 3
- RetryInterval = 1500ms

---

---

## 測試總結

**測試覆蓋率目標**: 90%+

**重點測試項目**:
1. ✅ 鎖的取得與釋放邏輯
2. ✅ Watchdog 自動續約機制
3. ✅ 異常情況下的鎖釋放
4. ✅ 多執行緒併發控制
5. ✅ Redisson 配置正確性

**風險點**:
- Watchdog 機制需要整合測試驗證 (較難用單元測試模擬)
- 多執行緒測試可能有時序問題 (需要仔細設計)

---

## 整合測試 (Integration Tests)

使用 **Testcontainers** 啟動真實 Redis 環境進行整合測試。

### 測試檔案
`src/test/java/com/next/nexrailai/integration/DistributedLockIntegrationTest.java`

### 測試結果摘要
- **執行日期**: 2026-01-30
- **測試數量**: 8 個
- **通過**: 8 個 ✅
- **失敗**: 0 個
- **測試覆蓋範圍**:
  - 真實 Redis 環境下的鎖行為
  - 多執行緒併發控制
  - Watchdog 自動續約機制
  - 異常處理與鎖釋放
  - 可重入鎖

### 已完成的整合測試案例

[x] [整合測試] 成功取得鎖並執行業務邏輯
**測試方法**: `shouldAcquireLockAndExecuteBusinessLogic()`
**驗證項目**:
- 鎖成功取得
- 業務邏輯執行完成
- 鎖被正確釋放

---

[x] [整合測試] 無法取得鎖時跳過執行（模擬其他實例持有鎖）
**測試方法**: `shouldSkipExecutionWhenLockIsHeld()`
**驗證項目**:
- 使用另一個執行緒持有鎖
- 主執行緒無法取得鎖
- 方法返回 null，業務邏輯未執行

---

[x] [整合測試] 多執行緒併發控制 - 只有一個執行緒成功取得鎖
**測試方法**: `shouldOnlyAllowOneThreadToAcquireLock()`
**驗證項目**:
- 5 個執行緒同時嘗試取得同一把鎖
- 只有 1 個執行緒成功取得並執行
- 其他 4 個執行緒跳過執行

---

[x] [整合測試] Watchdog 自動續約 - 長時間執行任務鎖不會過期
**測試方法**: `shouldNotExpireLockDuringLongRunningTask()`
**驗證項目**:
- 執行 4 秒長任務（超過一般鎖檢查間隔）
- 在執行期間檢查鎖狀態（每 500ms 檢查一次）
- 鎖在整個執行期間保持持有狀態
- 任務完成後鎖被正確釋放

---

[x] [整合測試] 異常處理 - 業務邏輯拋出異常時鎖仍然被釋放
**測試方法**: `shouldReleaseLockWhenExceptionThrown()`
**驗證項目**:
- 業務邏輯拋出 RuntimeException
- 異常被正確傳播
- 鎖在 finally 區塊被正確釋放

---

[x] [整合測試] 鎖釋放後其他執行緒可以取得
**測試方法**: `shouldAllowOtherThreadToAcquireLockAfterRelease()`
**驗證項目**:
- 執行緒 A 取得鎖並執行任務
- 執行緒 A 釋放鎖後，執行緒 B 成功取得鎖
- 兩個執行緒都成功執行業務邏輯

---

[x] [整合測試] 鎖的 key 包含特殊字元
**測試方法**: `shouldHandleLockKeyWithSpecialCharacters()`
**驗證項目**:
- `task:with:colons` - 冒號
- `task-with-dashes` - 破折號
- `task_with_underscores` - 底線
- 所有 key 都能正常使用

---

[x] [整合測試] 可重入鎖 - 同一執行緒多次取得同一把鎖
**測試方法**: `shouldSupportReentrantLock()`
**驗證項目**:
- 外層方法取得鎖
- 內層方法（相同鎖 key）再次取得鎖
- 鎖的計數器正確遞增和遞減
- 最終鎖被完全釋放

---

### 整合測試技術細節

#### Testcontainers 配置
```java
@Container
static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .withReuse(true);
```

#### RedissonClient 測試配置
- Watchdog 超時: 30 秒
- 連線池大小: 10
- 最小閒置連線: 2
- 超時時間: 3 秒

#### 測試策略
1. **隔離性**: 每個測試使用不同的 lock key，避免互相影響
2. **併發測試**: 使用 `CountDownLatch` 同步執行緒啟動
3. **真實環境**: Testcontainers 提供真實 Redis 環境，比 Mock 更可靠
4. **時序控制**: 使用 `Thread.sleep()` 和 `Future.get()` 控制測試時序

---

