# AI Agent Charter (AI 代理憲章)

## ⚠️ 關鍵指令

**必須使用繁體中文 (Traditional Chinese) 與使用者溝通。**
所有解釋、對話、問題確認都必須使用繁體中文。程式碼註解可使用英文或繁體中文。

---

## 專案背景

**Project**: NexRailAI
**Description**: 結合 LLM 語意理解與分散式架構的高鐵訂票 LINE Bot，使用 Google Gemini Function Calling 將自然語言轉換為高鐵查票 API 請求，支援背景搶票監控、智慧記憶、個人化提醒與後台管理系統。
**開發模式**: 褐地 Brownfield

**Tech Stack**:

- Java 21
- Spring Boot 3.5.9
- Database: PostgreSQL
- Cache: Redis（含 Redisson 分散式鎖 + Watchdog 自動續約）
- Additional: Spring AI 1.1.2（Google Gemini 2.5 Flash）、LINE Bot SDK 9.13.0、Spring Security + JWT、Thymeleaf（後台管理）、Testcontainers（整合測試）

---

## 常用命令

```bash
./mvnw clean compile   # 編譯驗證
./mvnw test            # 執行測試
./mvnw spring-boot:run # 啟動應用程式
docker compose up -d   # 啟動 PostgreSQL + Redis
```

---

## 專案結構

```
src/main/java/com/next/nexrailai/
├── controller/        # REST API 入口（含 admin/ 後台子路由）
├── handler/           # LINE Bot Event Handler
├── service/           # 業務邏輯（AI 對話、推播等）
├── jpa/
│   ├── entity/        # JPA Entities
│   ├── repository/    # Spring Data JPA Repositories
│   └── service/       # 資料存取層 Service
├── scheduled/         # 排程任務（含 strategy/ 搶票策略）
├── dto/               # Request / Response DTOs（含 admin/, ai/ 子目錄）
├── config/            # Spring 配置類
├── security/          # Spring Security + JWT
├── aspect/            # AOP 切面（如配額管理）
├── common/            # 共用常數、例外類別
├── component/         # Spring Components（工具型 Bean）
├── context/           # 上下文相關（如用戶 Session）
└── utils/             # 工具類
```

---

## 禁止事項

- 禁止 field injection（`@Autowired` on fields）
- 禁止 wildcard import（`import java.util.*`）
- 禁止將密碼、API keys、token 寫入程式碼或 log
- 禁止直接修改已發布的 Flyway migration 檔案（如使用 Flyway）
- 禁止在 Controller 直接操作 Entity，必須經過 DTO（如使用 JPA）

---

## 對話規範

### Understand（每次對話開始時）

先閱讀以下檔案再行動：

- `README.md` — 了解專案概觀（如存在）
- `pom.xml` / `build.gradle` — 確認 Tech Stack 與依賴版本
- `application.yml` — 確認環境配置

褐地開發時額外進行影響分析，再進入 `brainstorming`。

### Communicate（每次完成後）

必須用**繁體中文**輸出：做了什麼（What）、為什麼這樣設計（Why）、需確認的問題（若有）、已知限制或後續建議（若有）。

---

## 規則文件

詳細規範請參考 `.claude/rules/`：

- `code-style.md` — 程式碼風格、命名規範、Logging
- `architecture.md` — 分層架構、設計模式（Template/Strategy/Observer）
- `api-design.md` — URL 命名、HTTP Methods/Status Codes、統一 Response 格式
- `config-security.md` — 配置管理、安全最佳實踐
- `database.md` — 資料庫設計、Migration、查詢優化
- `performance.md` — 效能考量、文件規範、版本控制

---

## Superpowers 技能庫

使用 `using-superpowers` 探索可用技能。依規模選擇技能鏈：

| 規模 | 判斷標準 | 技能鏈 |
|---|---|---|
| **小型** | 單一方法修改、簡單 bugfix | `systematic-debugging` → `verification-before-completion` |
| **中型** | 新增單一功能、修改一個模組 | `brainstorming` → `writing-plans` → TDD → `verification-before-completion` |
| **大型** | 新系統、跨模組重構、重大功能 | 綠地或褐地完整流程（見下方） |

---

## 綠地開發規則（Greenfield）

從零開始的新功能或新專案，使用完整技能鏈：

```
brainstorming
  → writing-plans
    → using-git-worktrees
      → test-driven-development + executing-plans
        → verification-before-completion
          → requesting-code-review
            → finishing-a-development-branch
```

**重點**：設計自由度高，`brainstorming` 階段可大膽探索架構選型，不受既有程式碼約束。

---

## 褐地開發規則（Brownfield）

修改既有系統時，`brainstorming` 前必須先做影響分析：

```
[影響分析] 列出受影響的檔案、模組、下游依賴
  → brainstorming（設計須考慮既有架構約束）
    → writing-plans（計畫須含向下相容策略）
      → using-git-worktrees（隔離尤其重要）
        → test-driven-development（既有程式碼若不可測，先重構再補測試）
          → executing-plans / systematic-debugging
            → verification-before-completion（必須包含回歸測試）
              → requesting-code-review + receiving-code-review
                → finishing-a-development-branch
```

**與綠地的關鍵差異**：
- `brainstorming` 前必須先 Explore 現有程式碼，列出影響範圍
- `writing-plans` 必須包含向下相容策略，說明不破壞既有行為的方法
- `test-driven-development` 遇到不可測的 legacy code 時，先重構解耦，再補測試，再實作
- `systematic-debugging` 使用頻率高於綠地，優先使用
- `receiving-code-review` 特別重要：審查意見若與既有模式衝突，須先確認再實作
