# Database Guidelines (資料庫指南)

## Schema Design

- 使用合理的正規化
- 定義適當的索引
- 設定外鍵約束（根據需求）

## Migrations

- 使用 Flyway 或 Liquibase
- 版本化所有 schema 變更
- 只增量修改，不直接修改已發布的 migration

## Query Optimization

- 避免 N+1 查詢問題
- 使用 `@EntityGraph` 或 JOIN FETCH
- 監控慢查詢並優化
