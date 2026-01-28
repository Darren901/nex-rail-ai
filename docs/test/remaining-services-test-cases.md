# Remaining Services Test Cases

## 1. RateLimitService Test Cases
Target: `com.next.nexrailai.service.RateLimitService`

[ ] [正常情況] tryConsume - 額度足夠
**測試資料**
UserId: "U123"
Mock `redisTemplate.opsForValue().decrement(key)` 回傳 9
**預期結果**
返回 `true`

[ ] [正常情況] tryConsume - 額度剛好用完
**測試資料**
UserId: "U123"
Mock `decrement` 回傳 0
**預期結果**
返回 `true`

[ ] [異常處理] tryConsume - 額度不足
**測試資料**
UserId: "U123"
Mock `decrement` 回傳 -1
**預期結果**
1. 返回 `false`
2. 呼叫 `increment(key)` 把額度加回來 (Rollback)

[ ] [正常情況] getRemainingQuota - Redis 有值
**測試資料**
Mock `get(key)` 回傳 "5"
**預期結果**
返回 5

[ ] [邊界值] getRemainingQuota - Redis 無值 (回傳預設)
**測試資料**
Mock `get(key)` 回傳 null
Mock `systemConfigService.getInt("daily_message_limit")` 回傳 10
**預期結果**
返回 10

---

## 2. SystemConfigService Test Cases
Target: `com.next.nexrailai.service.SystemConfigService`

[ ] [正常情況] get - 快取命中 (Cache Hit)
**測試資料**
Key: "daily_message_limit"
Mock Redis `get` 回傳 "20"
**預期結果**
1. 返回 "20"
2. 不查詢 DB

[ ] [正常情況] get - 快取未命中 (Cache Miss)
**測試資料**
Key: "daily_message_limit"
Mock Redis `get` 回傳 null
Mock DB `findById` 回傳 Config("daily_message_limit", "15")
**預期結果**
1. 返回 "15"
2. 查詢 DB
3. 寫入 Redis 快取

[ ] [正常情況] updateConfig - 更新設定
**測試資料**
Key: "test_key", Value: "new_val"
**預期結果**
1. DB `save` 被呼叫
2. Redis `set` 被呼叫 (更新快取)

---

## 3. TdxService Test Cases
Target: `com.next.nexrailai.service.TdxService`

[ ] [正常情況] getAccessToken - 快取命中
**測試資料**
Mock Redis `get(TOKEN_KEY)` 回傳 "cached_token"
**預期結果**
1. 返回 "cached_token"
2. 不呼叫 TDX API

[ ] [正常情況] getThsrTimetable - 快取命中
**測試資料**
Mock Redis `get(timetable_key)` 回傳 JSON String
**預期結果**
1. 返回 JSON 解析後的 List
2. 不呼叫 TDX API

[ ] [正常情況] getMaasDeepLink - 成功
**測試資料**
Mock RestClient 回傳 `MaasDeeplinkResponseDTO(success, data("http://deeplink"))`
**預期結果**
返回 "http://deeplink"

---

## 4. AiService Test Cases
Target: `com.next.nexrailai.service.AiService`

[ ] [正常情況] chat - 正常對話
**測試資料**
UserId: "U123", Message: "Hi"
Mock `ChatClient.prompt()...call().content()` 回傳 "AI Response"
**預期結果**
返回 "AI Response"

[ ] [異常處理] chat - 發生例外
**測試資料**
Mock `ChatClient` 拋出 RuntimeException
**預期結果**
返回 "哎呀 我的大腦抽筋了..." (Fallback Message)
