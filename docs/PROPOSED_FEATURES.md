# Helpdesk 系統功能增強建議 (Proposed Features)

根據現有功能架構分析，以下列出建議新增的功能模組，旨在提升團隊協作效率、增強用戶體驗以及提供數據驅動的管理決策。

## 1. 工單指派系統 (Ticket Assignment)

### 描述
目前工單僅能在群組層級可見，缺乏明確的負責人機制。建議新增將工單指派給特定 IT 人員的功能。

### 效益
- 明確責任歸屬，避免工單無人處理或多人重複處理。
- 讓 IT 人員能專注於「我的工單」。

### 技術實作建議
- **Database**: `helpdesk_tickets` 新增 `assigned_to_member_id` (Nullable, FK to users).
- **API**:
  - `PATCH /api/helpdesk/tickets/{ticketId}/assign`: Body `{ "memberId": 123 }`.
  - `GET /api/helpdesk/tickets?assignedTo=me`: 篩選功能增強。
- **Frontend**: 工單詳情頁新增「指派給」下拉選單（僅列出該群組 IT 成員）。

## 2. 服務層級協議管理 (SLA Management)

### 描述
針對不同優先級的工單設定回應與解決時限（例如：急件 4 小時內回應，一般件 24 小時內解決）。

### 效益
- 確保重要問題得到及時處理。
- 量化服務品質，作為績效評估依據。

### 技術實作建議
- **Database**:
  - 新增 `sla_policies` 表（定義優先級與時限）。
  - `helpdesk_tickets` 新增 `due_date` 或 `sla_deadline` 欄位。
- **Backend**:
  - 建立工單時根據 Policy 自動計算 Deadline。
  - 排程 Job 定期掃描即將過期或已過期的工單，發送通知。
- **Frontend**: 工單列表顯示剩餘時間（例如：`2h 30m`），逾期則亮紅燈。

## 3. 內部備註 (Internal Notes)

### 描述
允許 IT 人員在工單內新增僅供內部查看的留言，一般 User 無法看見。

### 效益
- IT 團隊可在工單內討論解決方案而不干擾用戶。
- 記錄除錯過程或敏感資訊。

### 技術實作建議
- **Database**: `helpdesk_ticket_messages` 新增 `is_internal` (Boolean, Default false).
- **API**:
  - `POST /api/helpdesk/tickets/{ticketId}/messages`: Body 增加 `isInternal` 欄位。
  - `GET /api/helpdesk/tickets/{ticketId}/messages`: 若請求者為 USER，過濾掉 `isInternal=true` 的訊息。
- **Frontend**: 回覆框新增「內部備註」切換開關，內部訊息以不同背景色區分。

## 4. 知識庫 / 常見問題 (Knowledge Base / FAQ)

### 描述
建立文章庫，供用戶自行查詢常見問題解答，減少重複提單。

### 效益
- 降低 IT 客服負擔。
- 用戶可即時獲得解答，提升滿意度。

### 技術實作建議
- **Module**: 新增 `knowledgebase` 模組。
- **Database**: 新增 `kb_articles` (id, title, content, tags, is_published, view_count).
- **API**:
  - CRUD for Admin/IT.
  - Public Search API for Users.
- **Frontend**: 提單頁面可根據主旨關鍵字自動推薦相關文章。

## 5. 預設回覆模版 (Canned Responses)

### 描述
IT 人員可儲存常用的回覆內容（如：重設密碼指引、請提供更多資訊等），並在回覆時快速插入。

### 效益
- 大幅縮短回覆時間。
- 確保回覆語氣與內容的一致性。

### 技術實作建議
- **Database**: 新增 `canned_responses` (id, title, content, created_by).
- **Frontend**: 回覆框旁新增「插入模版」按鈕，點擊後彈出選單選擇。

## 6. 滿意度調查 (Customer Satisfaction Survey - CSAT)

### 描述
當工單狀態變更為 `CLOSED` 時，自動發送滿意度調查連結。

### 效益
- 收集用戶反饋，持續改進服務流程。

### 技術實作建議
- **Database**: `helpdesk_tickets` 新增 `csat_rating` (1-5) 與 `csat_comment`.
- **Workflow**: 結案時發送 Email 或通知，包含評分連結。
- **Frontend**: 簡易的 5 星評分介面。

## 7. 儀表板與數據分析 (Dashboard & Analytics)

### 描述
提供視覺化的數據報表，展示工單流量、處理效率等關鍵指標。

### 效益
- 管理層可快速掌握運作狀況。
- 識別瓶頸（如：某類問題暴增、某位人員負擔過重）。

### 技術實作建議
- **Backend**: 提供聚合查詢 API (Aggregation Query).
  - 平均回應時間 / 平均解決時間。
  - 每日/每週工單量趨勢圖。
  - 依分類/群組統計圓餅圖。
- **Frontend**: 使用 Chart.js 或 ECharts 繪製圖表。

## 8. 即時更新 (Real-time Updates)

### 描述
使用 WebSocket 技術，當有新工單或新訊息時，前端介面無需重新整理即可即時顯示。

### 效益
- 提升操作流暢度與即時性。
- 避免多人同時操作同一張工單的衝突（即時鎖定或提示）。

### 技術實作建議
- **Technology**: Spring WebSocket (STOMP).
- **Frontend**: 訂閱 `/topic/tickets` 或 `/user/queue/notifications`.

## 9. 標籤系統 (Tagging System)

### 描述
除了固定的「分類 (Category)」外，允許對工單貼上多個靈活的標籤（如：`VPN`, `Printer`, `Win11`）。

### 效益
- 更靈活的分類方式，便於後續搜尋與統計。

### 技術實作建議
- **Database**: `tags` 表與 `ticket_tags` 關聯表 (Many-to-Many).
- **Frontend**: 支援標籤輸入與自動補全。

## 10. Email 整合 (Email Integration)

### 描述
允許用戶直接發送 Email 到指定信箱（如 `support@company.com`）自動建立工單，且後續回覆也會同步。

### 效益
- 降低用戶使用門檻，無需登入系統即可提單。

### 技術實作建議
- **Backend**: 整合 IMAP/POP3 接收郵件，解析內容並建立 Ticket。
- **Security**: 需驗證發信者 Email 是否為系統註冊用戶。
