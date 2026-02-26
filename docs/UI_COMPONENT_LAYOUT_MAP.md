# Helpdesk UI 元件佈局地圖（供 AI Design Agent 參考）

用途：
- 提供 AI 設計 Agent / UI 改版作業時的「畫面結構與元件位置」參考
- 降低只看功能文件時無法理解實際版面關係的問題

範圍：
- 目前前端主線畫面（`App.vue` + `frontend/src/components/*`）
- 著重：畫面區塊順序、元件層級、顯示條件、常見互動

---

## 1. 版面總覽（App Shell）

## 1.1 根容器
- 元件：`App.vue`
- 根節點：`<main class="page">`
- 版面模式：
  - 未登入模式：顯示 `AuthPanel`
  - 已登入模式：顯示 Header / 通知 / Tabs / 各功能面板

## 1.2 頁面主軸（已登入）
由上到下順序固定：
1. `HeaderPanel`
2. `NotificationPanel`（條件顯示）
3. `DashboardTabs`
4. 功能面板（依 tab）
5. `LightboxModal`（浮層，條件顯示）

---

## 2. 畫面層級與位置（Screen Layout Map）

## 2.1 未登入畫面（Auth）
### 元件
- `AuthPanel.vue`

### 位置/層級（由上到下）
1. 標題（系統名稱）
2. 副標文字（登入/註冊說明）
3. 切換按鈕列（登入 / 註冊）
4. 表單區
   - 登入模式：單一表單
   - 註冊模式：stepper + 多步驟表單
5. 錯誤訊息區（底部）

### 註冊流程內部佈局
#### Step 1（身分資訊）
- 位置順序：
  1. 所屬部門群組（下拉）
  2. 右側提示文字（同一列）：`若無可選群組，請先聯繫管理員建立。`
  3. 帳號（員工工號）
  4. 姓名

#### Step 2（帳密資訊）
- Email
- Password

#### Step 3（確認）
- 摘要確認區（`confirm-box`）

#### 底部按鈕列（每步皆有）
- 上一步 / 下一步 / 註冊（依 step 顯示）

---

## 2.2 已登入主頁（Dashboard Shell）
### 頂部區（固定順序）
#### A. `HeaderPanel.vue`
- 左側：
  - 系統標題
  - 當前登入者資訊（姓名 / 工號 / 角色）
- 右側（`header-actions`）：
  - 通知按鈕（含 badge）
  - 登出按鈕

#### B. `NotificationPanel.vue`（展開式）
- 顯示條件：`notificationsOpen = true`
- 位置：Header 下方
- 內部：
  - 標題 + 全部標為已讀
  - 通知列表（每項為可點擊 button）
  - 錯誤訊息（底部）

#### C. `DashboardTabs.vue`
- 位置：通知面板下方（或 Header 下方）
- 按鈕順序（依權限顯示）：
  - `提交工單`（helpdesk）
  - `IT 工單`（itdesk，IT/ADMIN）
  - `封存`（archive）
  - `成員管理`（members，ADMIN）

---

## 2.3 `helpdesk` 分頁（一般使用者主要工作區）
### 顯示元件順序
1. `HelpdeskForm.vue`
2. `ActiveTicketsPanel.vue`

### A. `HelpdeskForm.vue`（工單建立表單）
由上到下：
1. 面板標題
2. 表單（`form-grid`）
   - 姓名
   - Email
   - 所屬群組
   - 工單分類
   - 主旨
   - 優先層級
   - 問題描述
   - 附件上傳（多檔，<5MB）
3. 已選檔案列表（條件顯示）
4. 送出按鈕
5. 成功/錯誤 feedback（底部）

### B. `ActiveTicketsPanel.vue`（進行中工單）
由上到下：
1. `ticket-list-top`
   - 左：標題（進行中工單）
   - 右：統計 chip 區（總數 / 本日新增 / 各狀態）
2. `ticket-filters`
   - 關鍵字搜尋
   - 我的工單 checkbox
   - 建立時間排序
   - 狀態篩選
   - 顯示筆數
3. 讀取中 / 空狀態訊息
4. 工單列表（`<ul class="ticket-list">`）
5. IT 操作錯誤訊息（panel 底部）

---

## 2.4 `itdesk` 分頁（IT/ADMIN 工單處理）
### 顯示元件
- `ActiveTicketsPanel.vue`（與 helpdesk 共用）

### 與 `helpdesk` 的差異
- 不顯示 `HelpdeskForm`
- 工單卡片中可見狀態下拉與更多操作按鈕（依權限）

---

## 2.5 `archive` 分頁（封存工單）
### 元件
- `ArchivePanel.vue`

### 位置結構（由上到下）
1. 標題（封存工單）
2. 篩選區（關鍵字 / 我的工單 / 排序 / 狀態篩選）
3. 空狀態/讀取狀態
4. 工單列表（使用 `TicketCard`，但為 archive mode）

### 差異（相對 ActiveTicketsPanel）
- 不顯示回覆區
- 不顯示狀態編輯操作

---

## 2.6 `members` 分頁（Admin 後台）
### 元件
- `MembersAdminPanel.vue`

### 區塊順序（由上到下）
1. 成員管理（members table）
2. 群組管理（group-admin 區塊）
   - 建立群組
   - 群組成員指派
   - 群組列表與主管設定
3. 工單分類管理（group-admin 區塊）
   - 建立分類
   - 分類列表（修改 / 刪除）
4. 稽核管理（audit-admin 區塊）
   - 匯出 / 清理按鈕
   - 篩選欄位
   - 稽核紀錄列表

### 版面特性
- 單頁長內容、多區塊堆疊
- `row` 與 `member-table` 在手機版最容易超寬（需特別注意）

---

## 3. 可重用核心元件位置與內部佈局

## 3.1 `TicketCard.vue`（核心卡片元件）
### 用途
- 被 `ActiveTicketsPanel` 與 `ArchivePanel` 共用

### 卡片結構（由上到下）
1. `ticket-head`（卡片頭）
   - 展開/收合按鈕（左側主體）
   - meta（工號、姓名、時間、群組、分類）
   - 優先層級 tag
   - 急件主管確認 tag（條件）
   - 已刪除 badge（條件）
   - 操作按鈕（主管確認 / 刪除 / 狀態選單，依模式與權限）
   - 狀態 tag（archive 或非 IT/ADMIN）
2. 展開內容 `ticket-content`（條件：open）
   - 問題描述
   - Email / 刪除時間
   - `TicketAttachments`
   - `message-box`（工單訊息）
   - `TicketStatusHistoryPanel`
   - 回覆區 `it-actions`（active mode 才顯示；目前所有登入者可用）
     - 回覆文字輸入 + 送出按鈕
     - 回覆附件上傳欄位
     - 已選回覆附件列表

### AI 設計 Agent 注意
- `TicketCard` 是最重要的資訊密度載體，改版時需保留：
  - 標題 / 狀態 / 操作 的可掃讀性
  - 展開後內容分區順序
  - 狀態色彩語義一致性

## 3.2 `TicketAttachments.vue`
- 位置：`TicketCard` 展開內容中，描述下方、訊息串上方
- 顯示：
  - 圖片：預覽按鈕
  - 非圖片：下載按鈕

## 3.3 `TicketStatusHistory.vue`
- 位置：訊息串下方
- 功能：顯示狀態歷程與操作者資訊

## 3.4 `LightboxModal.vue`
- 位置：全頁最上層浮層（portal-like overlay）
- 觸發：圖片附件預覽
- 關閉方式：背景點擊 / 關閉按鈕（若有）

---

## 4. 顯示條件矩陣（Visibility Matrix）

## 4.1 面板顯示條件
- `AuthPanel`：未登入
- `HeaderPanel / DashboardTabs`：已登入
- `NotificationPanel`：已登入且通知開啟
- `HelpdeskForm`：`dashboardTab === 'helpdesk'`
- `ActiveTicketsPanel`：`dashboardTab === 'helpdesk' || 'itdesk'`
- `ArchivePanel`：`dashboardTab === 'archive'`
- `MembersAdminPanel`：`dashboardTab === 'members'`

## 4.2 工單卡片操作顯示條件（摘要）
- 狀態下拉：active mode 且 `isItOrAdmin`
- 刪除按鈕：`canDeleteTicket(ticket)`
- 主管確認按鈕：`canSupervisorApprove(ticket)`
- 回覆區：active mode（目前全登入角色）

---

## 5. 響應式（RWD）布局參考（AI 改版必看）

## 5.1 手機版布局原則（目前）
- Header 改為縱向堆疊
- `ticket-list-top` 改縱向
- `ticket-filters` 改單欄
- `row` 內元素改滿寬
- 工單標題與 meta 允許換行
- 表格區允許橫向捲動

## 5.2 容易爆版的高風險區塊
1. `ticket-filters`（多欄位）
2. `ticket-head`（tag + button + meta 同列）
3. `MembersAdminPanel` 的 `row` 按鈕群
4. `member-table`
5. 長檔名附件列表

---

## 6. 提供 AI 設計 Agent 的改版約束（建議）
AI Agent 在提出 UI 重構方案時，應明確標註：
- 保留的核心資訊（不可消失）
- 重新排列的區塊順序（before/after）
- 桌機/手機布局差異
- 權限差異對 UI 的影響（USER / IT / ADMIN）
- 是否影響既有交互（通知跳轉、展開工單、狀態修改、回覆附件）

---

## 7. 維護方式（如何更新這份文件）
以下任一情況發生時，需同步更新本文件：
- 新增/移除 dashboard tab
- 工單卡片區塊順序改變
- 新增大型面板（如報表、SLA 看板）
- 權限導致的顯示條件變更
- 手機版布局策略變更

建議更新格式：
- 先更新「畫面層級」
- 再更新「核心元件」
- 最後更新「顯示條件矩陣」
