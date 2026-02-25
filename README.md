# Helpdesk 系統（Vue + TypeScript + Spring Boot + PostgreSQL）

## 專案簡介
本專案為企業內部 Helpdesk 工單系統，支援帳號管理、工單協作、通知中心與群組主管流程。

核心能力：
- 會員註冊 / 登入（`ADMIN` / `IT` / `USER`）
- 工單建立、附件上傳、回覆、狀態管理、軟刪除
- 工單優先層級（`GENERAL` / `URGENT`）
- 部門群組管理（Admin 建立群組、指派成員、設定群組主管）
- 急件主管確認（由該群組主管確認，不是 ADMIN 角色本身）
- 工單狀態歷程
- 作業稽核紀錄（Audit Log）
- 通知中心（跳轉、自動展開、高亮）
- 工單篩選/搜尋/排序

技術棧：
- 前端：Vue 3 + TypeScript + Vite + Nginx
- 後端：Java 17 + Spring Boot + Spring Data JPA
- 資料庫：PostgreSQL 16
- 部署：Docker Compose

## 角色與權限
- `USER`
  - 提交工單、查看工單
  - 刪除自己建立的工單（軟刪除）
- `IT`
  - 查看工單、回覆工單、變更工單狀態、刪除工單
- `ADMIN`
  - 擁有 IT 能力
  - 成員管理（指派 `USER` / `IT`、刪除非管理員）
  - 群組管理（建立群組、加入/移出成員、指派群組主管）

注意：
- 急件「主管確認」權限是「該工單所屬群組的主管」，不是 `ADMIN` 全域權限。

## 主要功能
### 1. 工單管理
- 建立工單欄位：姓名、Email、所屬群組、主旨、優先層級、問題描述、附件
- 狀態：`OPEN` / `PROCEEDING` / `PENDING` / `CLOSED` / `DELETED`
- IT/ADMIN 可更新狀態（已刪除工單不可變更）
- 訊息串回覆（IT/ADMIN）
- 軟刪除（資料保留）

### 2. 優先層級與主管確認
- `GENERAL`：一般件
- `URGENT`：急件，需主管確認
- 急件建立前需滿足：
  - 提單者必須屬於某個群組
  - 該群組已設定主管
- 主管確認 API 會驗證「操作人是否為該工單群組主管」

### 3. 群組主管制（Admin）
- 建立部門群組
- 指派成員加入群組
- 將已加入群組的成員指定為該群組主管（單一主管）

### 4. 通知中心
- Header 通知 badge
- 通知可點擊跳轉對應工單
- 跳轉後自動展開工單，並 `scroll into center`
- 跳轉卡片短暫發光高亮
- 新工單自動高亮約 3 秒

### 5. 工單列表體驗
- 關鍵字搜尋
- 我的工單
- 依建立時間排序（新到舊 / 舊到新）
- 依狀態篩選
- 展開收合轉場（`max-height + opacity`）
- 已刪除工單標題與內容視覺區別（含刪除線）

### 6. 工單歷程與附件
- 狀態歷程（from/to、操作者、時間）
- 圖片附件可預覽（燈箱）
- 非圖片附件可下載

### 7. 稽核與追蹤（Audit + Trace）
- Admin 可在前端成員管理頁查詢操作紀錄（依 action/entity/time/操作者篩選）
- 支援 CSV 匯出（可帶篩選條件）
- 支援保留期限與清理（排程 + 手動）
- 每筆業務 audit metadata 會自動帶入 `traceId`
- Access log 與 Audit log 可用同一個 `traceId` 串起來追查

## API 重點（摘要）
### Auth / 成員
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/admin/members`（Admin）
- `PATCH /api/admin/members/{memberId}/role`（Admin）
- `DELETE /api/admin/members/{memberId}`（Admin）

### 群組（新）
- `GET /api/groups/mine`
- `GET /api/admin/groups`（Admin）
- `POST /api/admin/groups`（Admin）
- `PATCH /api/admin/groups/{groupId}/members/{memberId}`（Admin）
- `DELETE /api/admin/groups/{groupId}/members/{memberId}`（Admin）
- `PATCH /api/admin/groups/{groupId}/supervisor/{memberId}`（Admin）

### 工單
- `GET /api/helpdesk/tickets`
- `POST /api/helpdesk/tickets`
  - form-data 主要欄位：`name`, `email`, `subject`, `description`, `groupId`, `priority`, `files[]`
- `PATCH /api/helpdesk/tickets/{ticketId}/status`
- `PATCH /api/helpdesk/tickets/{ticketId}/delete`
- `PATCH /api/helpdesk/tickets/{ticketId}/supervisor-approve`
- `POST /api/helpdesk/tickets/{ticketId}/messages`
- `GET /api/helpdesk/tickets/{ticketId}/attachments/{attachmentId}/view`
- `GET /api/helpdesk/tickets/{ticketId}/attachments/{attachmentId}/download`

### 通知
- `GET /api/notifications`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read-all`

### 稽核（Admin）
- `GET /api/admin/audit-logs`
  - query: `action`, `entityType`, `entityId`, `actorMemberId`, `from`, `to`, `limit`
- `GET /api/admin/audit-logs/export.csv`
  - 同上篩選條件，回傳 CSV 附件
- `POST /api/admin/audit-logs/cleanup`
  - query: `days`（可選，未帶則用系統保留天數）

## 專案結構
- `frontend`：前端應用（Vue + TS）
- `backend`：後端 API（Spring Boot）
- `docker-compose.yml`：一鍵啟動前後端與資料庫

## 文件
- `docs/CURRENT_ARCHITECTURE.md`：目前檔案與系統架構快照
- `docs/REFRACTOR_REPORT_2026-02-17.md`：前後端重構報告（本次重構成果與驗證紀錄）
- `docs/DEPLOY_RUNBOOK.md`：目前實務部署/回復手冊（ESXi + Portainer + Docker）
- `docs/PORTAINER_WEBHOOK_CD.md`：Portainer Webhook 半自動部署（CD）設定說明

## 快速啟動（Docker Compose）
在 `fullstack` 目錄：

```bash
docker compose up -d --build
```

啟動後：
- 前端：`http://localhost:5173`
- 後端：`http://localhost:8080`
- PostgreSQL：`localhost:5432`（db/user 依 `.env` 設定）

停止服務：
```bash
docker compose down
```

移除資料卷：
```bash
docker compose down -v
```

## 開發模式（不走 Docker）
### 1. 啟動資料庫
預設連線（可由環境變數覆蓋）：
- URL：`jdbc:postgresql://localhost:5432/fullstack`
- Username：`app`
- Password：由 `SPRING_DATASOURCE_PASSWORD` 設定（必填）

### 2. 啟動後端
```bash
cd backend
mvn spring-boot:run
```

### 3. 啟動前端
```bash
cd frontend
npm install
npm run dev
```

## 管理員初始化
由環境變數初始化：
- Employee ID：`APP_ADMIN_EMPLOYEE_ID`
- Name：`APP_ADMIN_NAME`
- Email：`APP_ADMIN_EMAIL`
- Password：`APP_ADMIN_PASSWORD`（必填，請使用強密碼）

建議上線前修改：
- `APP_ADMIN_EMPLOYEE_ID`
- `APP_ADMIN_NAME`
- `APP_ADMIN_EMAIL`
- `APP_ADMIN_PASSWORD`

## 重要環境變數
- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`
- `HELPDESK_UPLOAD_DIR`
- `APP_ADMIN_*`
- `APP_AUDIT_RETENTION_DAYS`（預設 `180`）
- `APP_AUDIT_CLEANUP_CRON`（預設 `0 30 3 * * *`，每日 03:30 清理）

## Log 與稽核維運
### 1. Access log 追蹤
- 後端每次請求會回傳 `X-Trace-Id`。
- 可由 client 主動帶 `X-Trace-Id`，若未帶則後端自動產生。
- 日誌會輸出 method/path/status/duration/ip 與 `traceId`。

### 2. Audit log 查詢建議
- 常用篩選：
  - `action=TICKET_STATUS_CHANGE`
  - `entityType=TICKET`
  - `from/to` 使用 ISO-8601，例如 `2026-02-13T00:00:00Z`
- `limit` 建議 100~500，避免一次查太大。

### 3. Audit 清理策略
- 正式環境建議保留 90~365 天（依公司稽核規範調整）。
- 例：保留 180 天
  - `APP_AUDIT_RETENTION_DAYS=180`
- 例：每天凌晨 2:00 執行
  - `APP_AUDIT_CLEANUP_CRON=0 0 2 * * *`

## 操作手冊
### USER
1. 登入後先在「提交工單」選擇所屬群組。
2. 選擇優先層級（一般/急件），填寫主旨與描述後送出。
3. 在工單列表查看進度、訊息、狀態歷程與通知。
4. 可刪除自己建立的工單（軟刪除）。

### IT
1. 進入「IT 工單處理」。
2. 使用篩選、搜尋、排序快速定位工單。
3. 可回覆工單、更新狀態、刪除工單。

### 群組主管
1. 需先被 Admin 指派為某群組主管。
2. 可對該群組急件執行「主管確認」。

### ADMIN
1. 可管理成員角色與帳號。
2. 在成員管理頁可：
   - 建立群組
   - 指派成員加入群組
   - 將群組成員設為主管
3. 在「操作紀錄（Audit Log）」可：
   - 依條件查詢紀錄
   - 匯出 CSV
   - 執行清理（輸入保留天數）
4. 可監控通知與工單整體流量。
