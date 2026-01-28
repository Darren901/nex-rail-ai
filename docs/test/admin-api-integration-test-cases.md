# Admin API Integration Test Cases

## 測試目標
驗證管理後台 API 的安全性與功能，包括 JWT 登入、權限控管、系統設定修改與訊息廣播功能。

## 前置條件
- 使用 H2 資料庫
- Mock `AuthenticationManager` 模擬登入驗證
- Mock `LineMessageService` 驗證廣播
- 準備預設管理員帳號 (在 `DataInitializer` 已建立，或測試時建立)

## 測試案例清單

### 1. 認證流程 (Authentication)
- [x] **TC01**: 管理員登入成功
  - **Arrange**: 準備有效的使用者名稱與密碼
  - **Act**: POST `/api/auth/login`
  - **Assert**: 回傳 HTTP 200 及 JWT Token

- [x] **TC02**: 管理員登入失敗
  - **Arrange**: 準備錯誤的密碼
  - **Act**: POST `/api/auth/login`
  - **Assert**: 回傳 HTTP 401 或 403

### 2. 廣播功能 (Broadcast)
- [x] **TC03**: 未授權存取廣播 API
  - **Arrange**: 不帶 Token
  - **Act**: POST `/api/admin/broadcast`
  - **Assert**: 回傳 HTTP 403 Forbidden

- [x] **TC04**: 成功發送廣播
  - **Arrange**: 
    - 帶有有效 Admin Token
    - 資料庫中有狀態為 `ENABLE` 的使用者
  - **Act**: POST `/api/admin/broadcast` (帶 Payload)
  - **Assert**: 
    - 回傳 HTTP 200
    - 驗證 `lineMessageService.multicast` 被呼叫且包含目標使用者 ID

### 3. 系統設定 (System Config)
- [x] **TC05**: 修改系統設定
  - **Arrange**: 帶有有效 Admin Token
  - **Act**: POST `/api/admin/configs/{key}` 更新設定值
  - **Assert**: 
    - 回傳 HTTP 200
    - 驗證資料庫或 Service 中該 Key 的值已更新
