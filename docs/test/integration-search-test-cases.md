# Integration Test Cases: Thsr Search Flow

## 1. Search Flow Test
Target: `com.next.nexrailai.service.LineService` (Integration Level)

[x] [正常情況] User sends search query -> System replies with Timetable
**測試資料**
User: "U100"
Message: "查明天早上台北到高雄"
Mock AI: Sets Context (Origin: 台北, Dest: 高雄, Date: Tomorrow)
Mock TDX: Returns 2 trains
**預期結果**
1. `LineMessageService.reply` 被呼叫
2. 參數為 `FlexMessage` (Timetable Carousel)
3. `RateLimitService` 扣除 1 點

[ ] [正常情況] User sends booking query -> System replies with Booking Link
**測試資料**
User: "U100"
Message: "訂 101 車次"
Mock AI: Sets Context (BookingLink: "http://...")
**預期結果**
1. `LineMessageService.reply` 被呼叫
2. 參數為 `FlexMessage` (Booking Confirmation)

[x] [異常處理] Rate Limit Exceeded
**測試資料**
User: "U_BROKE"
Redis Quota: 0
**預期結果**
1. `LineMessageService.reply` 被呼叫
2. 參數為 TextMessage ("額度已用完")
3. AI Service **未被呼叫**
