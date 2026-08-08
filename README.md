# NexRailAI

[![Deployed on Zeabur](https://img.shields.io/badge/Deployed_on-Zeabur-6306E9?style=flat-square&logo=zeabur&logoColor=white)](https://line.me/R/ti/p/@539axasf)

NexRailAI 是一個直接住在 **LINE** 裡的高鐵訂票助理。不用下載新 App、不用記指令，用平常說話的方式，就能查票、盯票與設提醒。

## 專案概述

### 解決的問題

用現有方式訂高鐵票，常會遇到這些狀況：

- **指令僵化**：一般的聊天機器人只認得關鍵字，講「這週五晚上七點後」它就聽不懂了。
- **流程繁瑣**：開 App、選日期、選車站、選時段，點了好幾層才看到班次。
- **錯過搶票**：連假開賣時間一過就沒位子，等想起來時已經一票難求。
- **重複輸入**：每次幫家人朋友查票，都要從頭再設定一次起訖站與時間。

### NexRailAI 的做法

1. **用說的就好**：一句話講完需求，模糊的時間與地點都聽得懂，直接給你符合條件的車次。
2. **沒票也不用一直刷**：把班次交給它盯，有人退票的瞬間就通知你，不必開著網頁等。
3. **記得你的行程**：常搭的班次可以取名字存起來，下次一句「查回家的班次」就叫得出來。
4. **不會忘記時間**：搶票、出發、轉乘，想被提醒什麼都可以直接說，時間到就推播給你。

## 核心功能 (Core Features)

NexRailAI 把複雜的訂票流程，收進一段對話裡：

*   **自然語言查票 (AI-Powered Search)**

    拋棄僵化的指令。你可以直接說：幫我查本週五晚上七點後回台南的票。日期、時間、起訖站與「越早越好」這類條件都會自動判讀，直接列出符合的車次。

*   **智慧搶票監控 (Smart Monitoring)**

    熱門時段買不到票？只需告訴它：幫我監控這班車。系統會在背景持續替你留意座位狀態，一旦有人退票，立即發送 LINE 通知。

*   **智慧記憶與常用班次 (Smart Memory)**

    常搭的行程可以記下來。你可以說：這是我女兒的班次，幫我記下來。下次只要說：幫我查女兒的班次，起訖站與時間偏好就會自動帶入。

*   **個人化提醒 (Custom Reminders)**

    隨身的 LINE 備忘錄。你可以設定任意時間的提醒事項，例如：明天早上 10 點提醒我搶連假車票。時間到了，通知就會出現在 LINE 裡。

*   **圖文選單與快速存取 (Rich Menu)**

    常用功能都在圖文選單上，一鍵就能查看目前正在監控的班次、待辦提醒與已儲存的行程，隨時掌握背景任務的狀態。

*   **視覺化票務卡片 (Rich UI)**

    所有查詢結果皆以 LINE Flex Message 呈現。發車與抵達時間、行駛時長、座位狀態一目了然，點擊卡片即可直達官方購票頁面。

*   **使用額度 (Usage Quota)**

    每日的 AI 對話次數與每月的提醒設定次數都有額度上限，確保服務對所有使用者都穩定可用。額度會持續回補並可跨日累積至上限，偶爾用得多一點也不會突然被卡住。

## 實機畫面 (Screenshots)

### 手機端體驗 (Mobile Experience)

| 自然語言訂票 | 待辦提醒與常用行程 | 監控通知與卡片 |
|:---:|:---:|:---:|
| <img src="docs/images/chat_demo.jpg" width="250"> | <img src="docs/images/reminder_demo.jpg" width="250"> | <img src="docs/images/monitor_notify.jpg" width="250"> |
| 講一句話，模糊的時間地點也聽得懂 | 設定個人化提醒，記住常搭的班次 | 座位一釋出，立刻收到 LINE 通知 |

### 後台管理系統 (Admin Dashboard)

<img src="docs/images/admin_dashboard.png" width="800">

> 視覺化監控系統狀態、任務管理、使用者管理與全站參數熱更新。

## 立即體驗 (Live Demo)

歡迎掃描下方 QR Code 加入 LINE 官方帳號，體驗 NexRailAI 的功能：

![NexRailAI QR Code](docs/images/qrcode.png)

> 若無法掃描，請搜尋 LINE ID: @539axasf

## 運作方式 (How It Works)

### 1. AI 只負責聽懂，不負責回答票務

NexRailAI 不會讓 AI 自己「想」出班次。AI 的角色是把你那句話翻譯成明確的查詢條件，實際的車次、時刻與座位狀態一律來自官方票務資料。

這代表：**你看到的每一筆資訊都是真的**，不會有 AI 編出來的班次或時間。

### 2. 監控與提醒在背景自己跑

你設定完就可以關掉 LINE。監控任務會持續在雲端執行，不受手機開關或網路狀態影響；提醒也一樣，時間到了就會準時送達。

### 3. 記憶是屬於你的

儲存的常用行程與偏好只會用在你自己的對話中，並且隨時可以查看、修改或刪除。

## 系統架構 (Architecture)

```text
+----------------+      +------------------+      +-------------------+
|   LINE User    | ---> |   NexRailAI Bot  | ---> |   Google Gemini   |
| (Flex Message) | <--- |   (Spring Boot)  | <--- |    語意理解        |
+----------------+      +--------+---------+      +-------------------+
                                 |
                                 | 查詢 / 監控 / 提醒
                                 v
        +----------------+-----------------+
        |                |                 |
   +----+-----+    +-----+------+    +-----+------+
   | TDX API  |    | PostgreSQL |    |   Redis    |
   | 官方票務  |    | 行程 / 任務 |    | 快取 / 排程 |
   +----------+    +------------+    +------------+
```

## 使用技術 (Built With)

*   **Core**: Java 21, Spring Boot 3.5.9
*   **AI**: Spring AI, Google Gemini 2.5 Flash
*   **Infrastructure**: PostgreSQL, Redis, Docker
*   **Messaging**: LINE Bot SDK (Flex Message)
*   **Deployment**: Zeabur (PaaS)

## 快速開始

### 前置需求

*   Java 21 SDK
*   Docker & Docker Compose
*   Google Cloud Project (Gemini API Key)
*   TDX 帳號 (Client ID & Secret)

### 執行步驟

1.  **啟動基礎設施**
    ```bash
    docker-compose up -d
    ```

2.  **設定環境變數**
    請參考 `application.yml` 設定 `GEMINI_API_KEY`, `LINE_BOT_TOKEN`, `TDX_CLIENT_ID` 等參數。

3.  **執行應用**
    ```bash
    ./mvnw spring-boot:run
    ```

4.  **執行測試**
    ```bash
    ./mvnw test
    ```
