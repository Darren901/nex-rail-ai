# Refactored Components Test Cases

## 1. EnumUtil Test Cases
Target: `com.next.nexrailai.utils.EnumUtil`

[x] [正常情況] fromCode - 成功查找存在的 Enum
**測試資料**
Enum: `Constant.TicketType`
Code: 1
**預期結果**
返回 `Constant.TicketType.ONE_WAY`

[x] [邊界值] fromCode - 查找不存在的 Code
**測試資料**
Enum: `Constant.TicketType`
Code: 999
**預期結果**
返回 `null`

[x] [邊界值] fromCode - 輸入 null Code
**測試資料**
Enum: `Constant.TicketType`
Code: null
**預期結果**
返回 `null`

[x] [正常情況] fromName - 成功查找存在的 Enum
**測試資料**
Enum: `Constant.FareClass`
Name: "成人"
**預期結果**
返回 `Constant.FareClass.ADULT`

[x] [邊界值] fromName - 查找不存在的 Name
**測試資料**
Enum: `Constant.FareClass`
Name: "外星人"
**預期結果**
返回 `null`

---

## 2. ScheduleTaskExecutorFactory Test Cases
Target: `com.next.nexrailai.scheduled.strategy.ScheduleTaskExecutorFactory`

[x] [正常情況] getExecutor - 成功取得 ReminderExecutor
**測試資料**
Type: `TaskType.REMINDER`
**預期結果**
返回實作了 `ReminderExecutor` 的實例

[x] [正常情況] getExecutor - 成功取得 TicketMonitorExecutor
**測試資料**
Type: `TaskType.TICKET_MONITOR`
**預期結果**
返回實作了 `TicketMonitorExecutor` 的實例

[x] [異常處理] getExecutor - 找不到對應的 Executor
**測試資料**
Type: 假設一個未實作的 TaskType (或者 Mock Map 為空)
**預期結果**
拋出 `RuntimeException` 或 `ApBusinessException`

---

## 3. ReminderExecutor Test Cases
Target: `com.next.nexrailai.scheduled.strategy.ReminderExecutor`

[x] [正常情況] execute - 成功發送提醒
**測試資料**
Task: userId="U123", content="記得買票"
**預期結果**
1. 呼叫 `LineMessageService.pushMessage("U123", ...)`
2. Task status 更新為 `EXECUTED`

---

## 4. TicketMonitorExecutor Test Cases
Target: `com.next.nexrailai.scheduled.strategy.TicketMonitorExecutor`

[x] [正常情況] execute - 監控到有票 (Happy Path)
**測試資料**
Task: userId="U123", payload="{valid search request}"
Mock `ThsrTicketService.searchTickets` 回傳有空位的 `ThsrSummaryDTO`
**預期結果**
1. 呼叫 `LineMessageService.pushMessage` (Flex Message)
2. Task status 更新為 `COMPLETED`

[x] [正常情況] execute - 監控無票 (Retry)
**測試資料**
Task: userId="U123"
Mock `ThsrTicketService.searchTickets` 回傳全部客滿
**預期結果**
1. 不發送訊息
2. Task `triggerTime` 被延後 (加上 interval)
3. Task status 保持 `PENDING` (或不變)

[x] [邊界值] execute - 任務過期
**測試資料**
Task: triggerTime = 現在
Payload 中的發車時間 = 過去的時間
**預期結果**
1. 發送「監控結束」通知
2. Task status 更新為 `EXPIRED`

---

## 5. ScheduleService Test Cases
Target: `com.next.nexrailai.service.ScheduleService`

[x] [正常情況] processScheduledTasks - 分派任務
**測試資料**
Repository 回傳 2 個任務: [Reminder, Monitor]
**預期結果**
1. `Factory.getExecutor(REMINDER).execute()` 被呼叫
2. `Factory.getExecutor(TICKET_MONITOR).execute()` 被呼叫
3. `Repository.saveAll` 被呼叫

---

## 6. ThsrTicketService Test Cases
Target: `com.next.nexrailai.service.ThsrTicketService`

[x] [正常情況] searchTickets - 完整流程
**測試資料**
Request: From="台北", To="高雄", Date="2023-12-01"
Mock `TdxService` 回傳正常 Timetable, Seat, Fares
**預期結果**
1. 回傳 List<ThsrSummaryDTO>
2. 包含正確的票價資訊 (經過過濾)
3. 包含正確的座位狀態

[x] [正常情況] searchTickets - 票價過濾 (EnumUtil 驗證)
**測試資料**
Request: fareClass="成人" (implicitly tested in full flow via mock data)
Mock `TdxService` 回傳 "成人" 與 "孩童" 票價
**預期結果**
回傳結果中只包含 "成人" 的票價

[ ] [異常處理] searchTickets - 站名錯誤 (Pending specific test case, covered by logic but explicit test recommended)
**測試資料**
Request: From="火星"
**預期結果**
拋出 `ApBusinessException(FROM_STATION_NOT_FOUND)`

---

## 7. LineService Test Cases
Target: `com.next.nexrailai.service.LineService`

[x] [正常情況] handleUserMessage - 額度不足
**測試資料**
Mock `RateLimitService.tryConsume` 回傳 false
**預期結果**
呼叫 `lineMessageService.reply` 回傳「額度已用完」

[x] [正常情況] handleUserMessage - 純文字回覆
**測試資料**
Mock AI Service 回傳 "你好"
Context 為空
**預期結果**
回傳 TextMessage

[x] [正常情況] handleUserMessage - 回傳訂票連結 Flex
**測試資料**
Mock AI Service 設定 Context 包含 BookingLink
**預期結果**
回傳 Booking Confirmation Flex Message

[x] [正常情況] handleUserMessage - 回傳時刻表 Flex (decideMessage)
**測試資料**
Mock AI Service 設定 Context 包含 Trains
**預期結果**
回傳 Timetable Carousel Flex Message
