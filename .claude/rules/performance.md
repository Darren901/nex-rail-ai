# Performance & Documentation & Version Control

## Performance Considerations（效能考量）

### Database

- 使用連接池（HikariCP - Spring Boot default）
- 適當的批次處理

### Caching

- 識別適合快取的資料
- 設定合理的過期策略
- 監控快取命中率

### API Performance

- 實施分頁（避免返回大量資料）
- 使用適當的 HTTP caching headers
- 考慮 API rate limiting

## Code Documentation（程式碼文件）

- Public APIs 和複雜邏輯使用 Javadoc
- 註解解釋「為什麼」，而不是「做什麼」
- 保持註解與程式碼同步

## Version Control（版本控制）

### Git Workflow

- 使用有意義的 commit messages
- 遵循專案的 branching strategy
- Pull Request 前確保通過所有測試

### Commit Messages

- 格式：`[Type] Brief description`
- Types: `feat`, `fix`, `refactor`, `test`, `docs`, `chore`
- 範例：`feat: 新增使用者註冊端點`
