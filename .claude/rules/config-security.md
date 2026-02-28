# Configuration & Security (配置管理與安全實踐)

## Configuration Management（配置管理）

### Profiles

- `application.yml` - 共同配置
- `application-dev.yml` - 開發環境
- `application-test.yml` - 測試環境
- `application-prod.yml` - 生產環境

### Externalized Configuration

- 敏感資訊使用環境變數或 secrets management
- 避免將密碼、API keys 提交到版本控制
- 考慮使用 Spring Cloud Config（如適用）

### Properties Binding

- 使用 `@ConfigurationProperties` 管理配置類
- 啟用 validation (`@Validated`)
- 提供合理的預設值

## Security Best Practices（安全最佳實踐）

### Input Validation

- 驗證所有外部輸入
- 使用 Bean Validation
- 防止 SQL Injection（使用 Prepared Statements）
- 防止 XSS（適當的輸出編碼）

### Authentication & Authorization

- 如使用 Spring Security，配置正確的 security chain
- 實施最小權限原則
- 保護敏感 endpoints

### Data Protection

- 敏感資料加密存儲
- 使用 HTTPS
- 實施適當的 CORS 政策
