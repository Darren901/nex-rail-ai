# NexRailAI

### 🚄 NexRail AI 開發待辦清單 (Todo List)

### 第一階段：核心查詢（已完成 ✅）

- [x]  **TDX 認證模組**：RestClient 實作 OAuth2 拿 Token。
- [x]  **基礎資料庫**：PostgreSQL 存車站資訊、經緯度。
- [x]  **AI 核心配置**：Spring AI + Gemini + JDBC 永久對話記憶。
- [x]  **時刻表工具**：FunctionToolCallback 封裝「查票時刻表」邏輯。

### 第二階段：即時資訊與用戶體驗（現在進行中 🚀）

- [x]  **即時座位模組**：接 `/AvailableSeatStatusList`，過濾出該車次在特定車站的剩餘座位狀態。
- [x]  **票價計算機**：接 `/Fare` API，支援「全票/大學生/早鳥」票價計算。
- [ ]  **System Prompt 2.0**：Markdown 表格輸出優化。
- [ ]  **DeepLink 導購連結**：封裝一個產出高鐵訂票 URL 的工具類。

### 第三階段：介面整合（下一站 🚉）

- [ ]  **LINE Messaging API 串接**：實作 Webhook Controller。
- [ ]  **Flex Message 視覺化**：將車次結果轉換成漂亮的 LINE 卡片（Flex Message）。
- [ ]  **異常通報**：監控 `/Alert` API，當有地震或延誤時自動在對話中插播。

### 第四階段：進階功能（終點站 🏆）

- [ ]  **MaaS 轉乘規劃**：結合捷運/台鐵 API 進行跨運具路徑計算。
- [ ]  **地點搜尋整合**：Google Maps API 整合（「我在內湖，幫我找最近的高鐵站」）。