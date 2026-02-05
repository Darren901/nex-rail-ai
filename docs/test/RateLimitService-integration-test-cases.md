# RateLimitService 整合測試案例

## 測試目標
驗證 RateLimitService 使用 Redisson RRateLimiter (令牌桶演算法) 在真實 Redis 環境下的行為。

## 測試環境
- **Redis**: Testcontainers (redis:7-alpine)
- **測試框架**: JUnit 5 + Spring Boot Test
- **併發測試**: ExecutorService + CountDownLatch

---

## 測試案例清單

### 1. 基本令牌桶功能

[x] [正常情況] 應該正確消耗令牌並在額度用完後拒絕請求
**測試資料**
- userId = "U_TEST_001"
- dailyQuota = 10 (從配置讀取)

**測試步驟**
1. 連續呼叫 10 次 `tryConsume(userId)`
2. 驗證每次都返回 `true`
3. 呼叫第 11 次 `tryConsume(userId)`
4. 驗證返回 `false`
5. 檢查 `getRemainingQuota(userId)` = 0

**預期結果**
- 前 10 次請求成功
- 第 11 次請求失敗
- 剩餘額度為 0

---

[x] [正常情況] 新使用者應該有完整的初始額度
**測試資料**
- userId = "U_NEW_USER"
- dailyQuota = 10

**測試步驟**
1. 呼叫 `getRemainingQuota(userId)` (尚未初始化)
2. 驗證返回值 = dailyQuota
3. 呼叫 `tryConsume(userId)`
4. 驗證 `getRemainingQuota(userId)` = dailyQuota - 1

**預期結果**
- 初始額度 = 10
- 消耗 1 次後剩餘 = 9

---

### 2. 併發場景測試

[x] [並發與數據完整性] 多執行緒併發請求時應該正確計數，不會超發額度
**測試資料**
- userId = "U_CONCURRENT_TEST"
- dailyQuota = 10
- threadCount = 20 (20 個執行緒搶 10 個額度)

**測試步驟**
1. 使用 `ExecutorService` 建立 20 個執行緒
2. 每個執行緒同時呼叫 `tryConsume(userId)`
3. 使用 `CountDownLatch` 確保同時執行
4. 統計成功次數與失敗次數

**預期結果**
- 成功次數 = 10 (dailyQuota)
- 失敗次數 = 10 (threadCount - dailyQuota)
- 剩餘額度 = 0
- **無 Race Condition**：不會超發額度

---

### 3. 動態額度管理

[x] [正常情況] 應該能夠動態設定與增加額度
**測試資料**
- userId = "U_ADMIN_TEST"
- initialQuota = 5
- additionalQuota = 10

**測試步驟**
1. 呼叫 `setQuota(userId, 5)`
2. 驗證 `getRemainingQuota(userId)` = 5
3. 消耗 2 次
4. 驗證剩餘 = 3
5. 呼叫 `addQuota(userId, 10)`
6. 驗證剩餘額度 >= 10

**預期結果**
- setQuota 成功設定初始額度
- addQuota 成功增加額度
- 額度計算正確

---

### 4. 每月額度（通知額度）

[x] [正常情況] 每月通知額度應該獨立運作
**測試資料**
- userId = "U_MONTHLY_TEST"
- monthlyQuota = 100
- consumeCount = 5

**測試步驟**
1. 檢查 `getRemainingMonthlyQuota(userId)` = 100
2. 呼叫 5 次 `tryConsumeMonthlyNotification(userId)`
3. 驗證剩餘 = 95
4. 檢查 `getRemainingQuota(userId)` (每日額度)
5. 驗證每日額度不受影響

**預期結果**
- 每月額度正確扣除
- 每日額度與每月額度獨立運作

---

### 5. 純令牌桶模式驗證

[x] [特殊情況] 應該使用固定 Key，不包含日期（純令牌桶模式）
**測試資料**
- userId = "U_KEY_TEST"

**測試步驟**
1. 消耗 3 個額度
2. 使用 `redissonClient.getKeys().countExists("rate_limit:quota:" + userId + "*")` 檢查 Key 數量
3. 驗證只有 1 個 Key
4. 檢查 Key 名稱是否為 `rate_limit:quota:U_KEY_TEST` (無日期後綴)

**預期結果**
- Redis 中只有 1 個固定 Key
- Key 不包含日期後綴（如 :20260205）
- 驗證純令牌桶模式（可跨日累積）

---

### 6. 令牌桶容量限制

[x] [邊界值] 令牌數量應該受桶容量限制，不會無限累積
**測試資料**
- userId = "U_CAPACITY_TEST"
- setQuota = 15 (超過標準額度 10)

**測試步驟**
1. 呼叫 `setQuota(userId, 15)`
2. 等待 100ms (模擬令牌補充)
3. 檢查 `getRemainingQuota(userId)`
4. 驗證剩餘額度 <= 15

**預期結果**
- setQuota 成功設定為 15
- 剩餘額度不超過設定值
- Redisson RRateLimiter 的容量限制正確運作

---

### 7. 外部依賴測試

[ ] [正常情況] Redis 容器應該正常運作
**測試資料**
- Testcontainers Redis 容器

**測試步驟**
1. 檢查 `redis.isRunning()` = true
2. 驗證 RedissonClient 可正常連線

**預期結果**
- Redis 容器啟動成功
- RedissonClient 連線正常

---

## 測試統計

- **總測試案例**: 7 個
- **正常情況**: 5 個
- **邊界值**: 1 個
- **並發與數據完整性**: 1 個
- **特殊情況**: 1 個

---

## 測試執行

執行指令：
```bash
./mvnw test -Dtest='RateLimitServiceIntegrationTest'
```

### ✅ 測試結果（2026-02-05）

- **狀態**: 全部通過 ✅
- **測試數量**: 7
- **失敗數**: 0
- **錯誤數**: 0
- **跳過數**: 0
- **執行時間**: ~6 秒（含 Docker 容器啟動）
- **建置狀態**: BUILD SUCCESS

### 修正歷程

**第一次執行失敗 (2 個測試失敗)**:
1. `shouldAllowDynamicQuotaAdjustment` - 失敗原因：`addQuota()` 使用 `trySetRate()` 對已存在的 RateLimiter 無效
2. `shouldUsePersistentKeyWithoutDate` - 失敗原因：`countExists()` 萬用字元匹配錯誤

**第一次修正措施**:
- 修改 `RateLimitService.addQuota()` (line 115-126): 在 `trySetRate()` 前先呼叫 `delete()` 刪除舊限流器
- 修改 `RateLimitService.addMonthlyQuota()` (line 146-157): 同樣修正
- 修改測試案例 `shouldUsePersistentKeyWithoutDate()` (line 218-240): 改用直接檢查 Key 存在性 + Pattern 搜尋驗證無日期後綴

**第二次執行結果**: ✅ 全部通過

---

**第二次優化 (2026-02-05)**:

**問題發現**:
- `addQuota()` 和 `addMonthlyQuota()` 使用 `availablePermits()` (剩餘令牌數) 作為基礎計算新額度
- 這會導致已消耗的令牌無法計入新額度（例如：設定 5 個，用掉 2 個剩 3 個，增加 10 個應該變成 15 個，但舊邏輯會變成 13 個）

**優化措施**:
- 改用 `rateLimiter.getConfig().getRate()` 取得已設定的容量（配置的速率），而非剩餘令牌數
- 更新邏輯：`newTotal = currentConfiguredRate + amount`
- 若 RateLimiter 不存在，則使用系統預設值作為 fallback
- 更新日誌訊息：顯示舊速率與新速率，更清楚追蹤變更

**測試驗證**:
- 單元測試: 9/9 通過 ✅ (新增 3 個測試案例驗證 `getConfig().getRate()` 邏輯)
- 整合測試: 7/7 通過 ✅

**第三次執行結果**: ✅ 全部通過
