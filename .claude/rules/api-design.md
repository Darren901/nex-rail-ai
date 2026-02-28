# API Design Guidelines (API 設計規範)

## URL 命名規則

- 使用**名詞複數**，禁止動詞：`/users`、`/orders`，禁止 `/getUser`、`/createOrder`
- 使用 **kebab-case**：`/order-items`，禁止 `/orderItems`、`/order_items`
- 巢狀資源表達從屬關係（最多兩層）：`/users/{userId}/orders`
- 版本號放在路徑前綴：`/api/v1/users`

```
# 正確
GET  /api/v1/users
GET  /api/v1/users/{id}
GET  /api/v1/users/{userId}/orders
POST /api/v1/orders

# 錯誤
GET  /api/v1/getUser
GET  /api/v1/user/{id}
POST /api/v1/createOrder
```

---

## HTTP Methods

| Method | 用途 | 冪等 |
|--------|------|------|
| `GET` | 查詢資源，不修改資料 | ✅ |
| `POST` | 建立新資源 | ❌ |
| `PUT` | 完整替換資源 | ✅ |
| `PATCH` | 部分更新資源 | ✅ |
| `DELETE` | 刪除資源 | ✅ |

---

## HTTP Status Codes

### 成功

| Code | 使用時機 |
|------|----------|
| `200 OK` | 查詢、更新成功 |
| `201 Created` | 建立資源成功，搭配 `Location` header |
| `204 No Content` | 刪除成功，無回傳內容 |

### 客戶端錯誤

| Code | 使用時機 |
|------|----------|
| `400 Bad Request` | 請求格式錯誤、參數驗證失敗 |
| `401 Unauthorized` | 未登入或 token 無效 |
| `403 Forbidden` | 已登入但權限不足 |
| `404 Not Found` | 資源不存在 |
| `409 Conflict` | 資料衝突（如重複建立） |
| `422 Unprocessable Entity` | 格式正確但業務邏輯驗證失敗 |

### 伺服器錯誤

| Code | 使用時機 |
|------|----------|
| `500 Internal Server Error` | 未預期的系統錯誤 |
| `503 Service Unavailable` | 外部依賴（DB、第三方服務）不可用 |

---

## 統一 Response 格式

### 成功回應

```json
{
  "success": true,
  "data": { }
}
```

### 成功回應（分頁）

```json
{
  "success": true,
  "data": {
    "items": [ ],
    "pagination": {
      "page": 1,
      "size": 20,
      "totalElements": 100,
      "totalPages": 5
    }
  }
}
```

### 錯誤回應

```json
{
  "success": false,
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "使用者不存在",
    "details": [ ]
  }
}
```

### 驗證錯誤回應（400）

```json
{
  "success": false,
  "error": {
    "code": "VALIDATION_FAILED",
    "message": "請求參數驗證失敗",
    "details": [
      { "field": "email", "message": "格式不正確" },
      { "field": "name", "message": "不可為空" }
    ]
  }
}
```

---

## 錯誤碼規範

- 格式：`大寫_蛇形命名`，以資源或模組為前綴
- 範例：`USER_NOT_FOUND`、`ORDER_ALREADY_CANCELLED`、`PAYMENT_GATEWAY_TIMEOUT`
- 禁止使用數字錯誤碼或模糊名稱（如 `ERROR_001`、`FAILED`）

---

## 分頁規範

- 使用 Query Parameters：`?page=1&size=20`
- `page` 從 **1** 開始（非 0）
- 預設值：`page=1`、`size=20`，最大 `size=100`
- 排序：`?sort=createdAt,desc`（支援多欄位）

```
GET /api/v1/orders?page=1&size=20&sort=createdAt,desc
```

---

## 查詢篩選規範

- 篩選條件使用 Query Parameters
- 日期格式統一使用 **ISO 8601**：`2024-01-01T00:00:00Z`

```
GET /api/v1/orders?status=PENDING&createdAfter=2024-01-01T00:00:00Z
```

---

## 其他規範

- Response 欄位使用 **camelCase**
- 時間欄位統一回傳 **UTC ISO 8601** 字串
- `POST` 建立成功後，回應 `201` 並在 `Location` header 附上新資源 URL
- 禁止在 GET 請求中修改任何資料
- 敏感欄位（密碼、token）禁止出現在任何 Response
