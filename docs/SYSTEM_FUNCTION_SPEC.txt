# Helpdesk 系統功能文件（System Function Spec）

版本定位：目前主線 `main` 功能說明（持續維護）

## 1. 系統目標
- 提供企業內部工單建立、處理、追蹤與通知能力
- 支援部門群組、群組主管與急件主管確認流程
- 支援附件、訊息串、操作稽核、站內通知與即時更新

## 2. 角色與權限
### 2.1 `USER`
- 註冊、登入、登出
- 建立工單（需選擇所屬部門群組與工單分類）
- 查看工單列表與工單明細
- 回覆工單訊息（可上傳附件，單檔 < 5MB）
- 刪除自己建立的工單（軟刪除）
- 若為群組主管，可對該群組急件進行主管確認

### 2.2 `IT`
- 擁有 `USER` 全部能力
- 變更工單狀態（`OPEN / PROCEEDING / PENDING / CLOSED`）
- 刪除工單（軟刪除）

### 2.3 `ADMIN`
- 擁有 `IT` 全部能力
- 成員管理（角色調整、刪除非管理員）
- 部門群組管理（建立群組、加入/移出成員、設定群組主管）
- 工單分類管理（建立 / 修改 / 刪除；已被使用分類不可刪除）
- 稽核紀錄查詢 / 匯出 / 清理

## 3. 功能模組
## 3.1 身分驗證與註冊
- 功能：
  - 登入 / 登出
  - 註冊流程（多步驟）
  - 註冊時選擇「所屬部門群組」
- 註冊 UI 重點：
  - 第 1 步：部門群組、工號、姓名
  - 顯示提醒：`若無可選群組，請先聯繫管理員建立。`

## 3.2 工單建立
- 欄位：
  - 姓名、Email、群組、分類、主旨、問題描述、優先層級、附件
- 驗證：
  - 必填欄位不可空
  - 群組/分類必選
  - 附件可多檔，單檔大小 < 5MB
- 急件限制：
  - 工單建立者必須屬於該群組
  - 該群組需已設定主管

## 3.3 工單列表與工單明細
- 進行中工單 / 封存工單分頁呈現
- 列表能力：
  - 關鍵字搜尋
  - 我的工單
  - 建立時間排序
  - 狀態篩選
- 工單卡片資訊：
  - 工單編號、主旨、狀態、優先層級、主管確認狀態
  - 提單者顯示格式：`工號 姓名`
  - 群組 / 分類 / 建立時間
- 展開內容：
  - 問題描述、Email
  - 附件清單（圖片預覽 / 一般附件下載）
  - 訊息串
  - 狀態歷程

## 3.4 工單回覆（全身份可用）
- 所有登入者皆可在「進行中工單」回覆訊息
- 回覆內容：
  - 文字訊息
  - 附件（多檔，單檔 < 5MB）
- 後端支援格式：
  - `application/json`（舊版相容）
  - `multipart/form-data`（新回覆附件）

## 3.5 工單狀態管理
- IT/ADMIN 可操作
- 狀態：
  - `OPEN`
  - `PROCEEDING`
  - `PENDING`
  - `CLOSED`
  - `DELETED`（軟刪除）
- 每次狀態變更寫入狀態歷程

## 3.6 急件主管確認
- 僅該工單所屬群組主管可操作
- 用途：`URGENT` 工單確認處理授權/優先性

## 3.7 通知中心
- 顯示通知 badge（未讀數）
- 通知可點擊導向工單
- 導向後：
  - 自動切換頁籤
  - 展開工單
  - 滾動定位
  - 卡片高亮提示

## 3.8 即時更新（WebSocket / STOMP）
- 前端訂閱 `/topic/tickets`（透過 `/ws`）
- 後端事件推播來源：
  - 建立工單
  - 回覆工單
  - 狀態變更
  - 刪除工單
  - 主管確認急件
- 前端收到事件後：
  - 自動刷新工單列表
  - 自動刷新通知列表
  - 高亮對應工單

## 3.9 部門群組管理（Admin）
- 建立群組
- 指派/移出群組成員
- 設定單一群組主管

## 3.10 工單分類管理（Admin）
- 建立分類
- 修改分類名稱
- 刪除分類（若已被工單使用則拒絕）

## 3.11 稽核紀錄（Audit Log）
- 查詢（action/entity/time/actor）
- 匯出 CSV
- 手動清理與保留期限設定

## 4. 主要 API（功能導向）
## 4.1 Auth / Member
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/logout`
- `GET /api/auth/me`
- `GET /api/groups/public`（註冊用公開群組清單）

## 4.2 Helpdesk Tickets
- `GET /api/helpdesk/tickets`
- `POST /api/helpdesk/tickets`（multipart）
- `POST /api/helpdesk/tickets/{ticketId}/messages`
  - JSON 或 multipart（含 `files[]`）
- `PATCH /api/helpdesk/tickets/{ticketId}/status`
- `PATCH /api/helpdesk/tickets/{ticketId}/delete`
- `PATCH /api/helpdesk/tickets/{ticketId}/supervisor-approve`
- `GET /api/helpdesk/tickets/{ticketId}/attachments/{attachmentId}/view`
- `GET /api/helpdesk/tickets/{ticketId}/attachments/{attachmentId}/download`

## 4.3 Notifications
- `GET /api/notifications`
- `PATCH /api/notifications/{id}/read`
- `PATCH /api/notifications/read-all`

## 4.4 Realtime
- WebSocket endpoint：`/ws`
- Topic：`/topic/tickets`

## 5. 核心限制與商業規則（摘要）
- 附件單檔大小 < 5MB（建單與回覆皆適用）
- 急件工單需群組主管存在
- 工單刪除為軟刪除（保留資料）
- 已刪除工單不可再變更狀態
- 分類刪除需檢查是否被工單引用

## 6. 驗收清單（功能）
- [ ] USER 可建立工單並上傳附件
- [ ] USER 可回覆工單並上傳附件
- [ ] IT/ADMIN 可變更工單狀態
- [ ] 群組主管可確認急件
- [ ] ADMIN 可管理群組與分類
- [ ] 通知可跳轉工單並高亮
- [ ] 兩個瀏覽器視窗可驗證即時更新
