# AI 代理開發指南 (AGENTS.md)

本文件旨在指導 AI 代理（Agent）在此儲存庫中進行開發、除錯與維護。所有 AI 代理在執行任務時必須嚴格遵守以下準則。

**⚠️ 重要：請務必使用繁體中文（Traditional Chinese）與使用者進行溝通。**

## 1. 專案環境與常用指令

本專案基於 **Java 21** 與 **Spring Boot 3.5.9**，使用 **Maven** 進行建置。

### 建置與執行
- **編譯與打包 (Skip Tests)**:
  ```bash
  ./mvnw clean package -DskipTests
  ```
- **完整建置 (含測試)**:
  ```bash
  ./mvnw clean install
  ```
- **啟動應用程式**:
  ```bash
  ./mvnw spring-boot:run
  ```

### 測試指令
- **執行所有測試**:
  ```bash
  ./mvnw test
  ```
- **執行單一測試類別**:
  ```bash
  ./mvnw -Dtest=NexRailAiApplicationTests test
  ```
- **執行單一測試方法**:
  ```bash
  ./mvnw -Dtest=NexRailAiApplicationTests#contextLoads test
  ```

## 2. 程式碼風格與規範 (Code Style)

請模仿現有的程式碼風格，保持一致性。

### 核心原則
- **Java 版本**: 使用 Java 21 特性（如 `var`、Records 等，視情況而定）。
- **Lombok**: 廣泛使用 Lombok 註解以減少樣板程式碼。
  - Entity/DTO 使用 `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Builder`。
  - Service/Controller 使用 `@RequiredArgsConstructor` 進行建構子注入。
  - 日誌使用 `@Slf4j`。
- **依賴注入 (DI)**: **優先使用建構子注入 (Constructor Injection)**。
  - 宣告欄位為 `private final`。
  - 使用 `@RequiredArgsConstructor` 自動生成建構子。
  - *避免*使用 `@Autowired` 在欄位上 (Field Injection)。

### 命名慣例
- **類別 (Classes)**: PascalCase (e.g., `AiService`, `ThsrTicketService`)。
- **方法與變數 (Methods & Variables)**: camelCase (e.g., `getRemainingQuota`, `chatId`)。
- **常數 (Constants)**: UPPER_SNAKE_CASE (e.g., `MAX_RETRY_COUNT`)。
- **DTO**: 通常以 `Request`, `Response`, 或 `DTO` 結尾 (e.g., `SearchRequest`, `ThsrSummaryDTO`)。

### 錯誤處理 (Error Handling)
- 使用 `try-catch` 區塊包覆外部服務呼叫（如 AI API, TDX API）。
- 發生異常時，務必使用 Slf4j 記錄錯誤堆疊：
  ```java
  log.error(">>>> [Context Info] Error message: {}", e.getMessage(), e);
  ```
- 業務邏輯錯誤請拋出 `ApBusinessException`（若專案中有定義）。
- 對使用者回傳友善的錯誤訊息，避免直接暴露系統內部錯誤。

### AI 服務整合 (Spring AI)
- 使用 `ChatClient` 的 Fluent API 建構請求。
- Prompt 參數應透過 `.param()` 注入，避免字串直接串接以防注入攻擊。
- 系統提示詞 (System Prompt) 位於 `application-prompts.yml`，請勿硬編碼在 Java 程式碼中。

## 3. 專案結構導覽

- `src/main/java/com/next/nexrailai/controller`: REST API 入口與 Webhook 處理。
- `src/main/java/com/next/nexrailai/service`: 核心業務邏輯。
- `src/main/java/com/next/nexrailai/jpa/entity`: 資料庫實體。
- `src/main/java/com/next/nexrailai/jpa/repository`: Spring Data JPA 存取層。
- `src/main/java/com/next/nexrailai/component`: 工具類別與 Function Tools 定義。
- `src/main/resources`: 設定檔 (`application.yml`) 與 Prompt 設定。

## 4. Git 提交規範

- 提交訊息請清晰描述變更內容（Why & What）。
- 範例：`fix: 修正 AI 回應格式錯誤`, `feat: 新增高鐵時刻表查詢功能`。

---
*Created by AI Agent for AI Agents. Follow these rules to ensure code quality and consistency.*
