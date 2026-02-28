# Architecture Patterns (架構模式)

## Layered Architecture

```
Controller Layer (API/Presentation)
    ↓
Service Layer (Business Logic)
    ↓
Repository Layer (Data Access)
    ↓
Database
```

## Common Patterns

### Repository Pattern

- 使用 Spring Data JPA repositories
- 自訂查詢使用 `@Query` 或 Query Methods
- 複雜查詢考慮使用 Specifications 或 QueryDSL

### DTO Pattern

- Controller 接收/返回 DTOs，不直接使用 Entities
- 使用 ModelMapper 或 MapStruct 進行轉換
- 分離 Request DTOs 和 Response DTOs

### Service Pattern

- 每個 Entity 通常對應一個 Service
- Service 負責業務邏輯，不處理 HTTP 相關邏輯
- 保持 Service 方法的單一職責

### Transaction Management

- 在 Service layer 使用 `@Transactional`
- 只讀操作使用 `@Transactional(readOnly = true)`
- 注意事務邊界和傳播行為

## Design Pattern Thinking (設計模式思維)

在設計或重構功能時，代理必須先評估是否可用設計模式提升可維護性、可測試性與擴充性，並避免過度設計。

### Template Method Pattern（模板方法模式）

- 適用情境：
  - 流程主幹固定，但部分步驟因業務而異
  - 需要統一流程順序與共用前後處理（如驗證、記錄、通知）
- 實作原則：
  - 將不可變流程放在抽象類別中
  - 可變步驟以 abstract 方法讓子類別實作
  - 避免子類別破壞主流程順序
- Spring Boot 範例場景：
  - 匯入流程（解析 → 驗證 → 儲存 → 通知）
  - 不同通路的訂單處理流程

### Strategy Pattern（策略模式）

- 適用情境：
  - 同一問題有多種可替換演算法或規則
  - 需要在執行期依條件切換行為
- 實作原則：
  - 以介面定義策略，各策略類別單一職責
  - 使用工廠或映射關係選擇策略，避免大量 `if-else` / `switch`
  - 新增策略時不修改既有核心流程（符合 OCP）
- Spring Boot 範例場景：
  - 不同付款方式（信用卡、轉帳、第三方支付）
  - 不同定價/折扣規則

### Observer Pattern（觀察者模式）

- 適用情境：
  - 一個事件需觸發多個後續動作，且希望鬆耦合
  - 主要流程不應被次要副作用阻塞
- 實作原則：
  - 使用事件發布/訂閱機制解耦（如 `ApplicationEventPublisher`）
  - 視需求搭配 `@Async` 處理非同步觀察者
  - 明確區分核心交易流程與附加流程（通知、審計、快取刷新）
- Spring Boot 範例場景：
  - 使用者註冊成功後觸發歡迎信、點數初始化、行為記錄
  - 訂單成立後觸發庫存更新與通知流程

### Pattern Selection Checklist（模式選型檢查清單）

- 是否有重複邏輯或分支爆炸問題？
- 是否預期短期內新增同類型規則/流程？
- 是否能明確降低耦合並提升測試性？
- 若僅有單一實作且短期不變，優先保持簡單設計，避免過度抽象。

## Async Processing

- 使用 `@Async` 註解
- 配置 Thread Pool (`@EnableAsync`)
- 考慮使用 CompletableFuture 或 Spring's ListenableFuture

## Caching

- 使用 Spring Cache Abstraction (`@Cacheable`, `@CacheEvict`)
- 定義合理的 TTL
- 注意快取一致性問題

## Event-Driven

- Spring Events (`ApplicationEventPublisher`)
- Message Queue (Kafka, RabbitMQ)
- 非同步解耦
