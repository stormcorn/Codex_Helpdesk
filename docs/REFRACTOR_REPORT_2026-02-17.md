# Frontend/Backend 重構報告

日期：2026-02-17
分支：`main`（本地未提交變更）

## 1. 重構目標
- 降低單一檔案複雜度與耦合度。
- 將業務邏輯與 UI / Controller 解耦。
- 建立可持續演進的資料庫 schema 管理與測試基線。

## 2. 前端重構摘要

### 2.1 結構重點
- `App.vue` 轉為組裝層，將狀態與行為移到 composables / components。
- 工單邏輯拆分為：
  - 檢視狀態：`frontend/src/composables/useTicketsView.ts`
  - 行為操作：`frontend/src/composables/useTicketsActions.ts`
  - 預設資料：`frontend/src/composables/useTicketDefaults.ts`
- 管理功能拆分：
  - 管理頁流程：`frontend/src/composables/useAdminManagement.ts`
  - 成員管理：`frontend/src/composables/useMembers.ts`
  - 稽核紀錄：`frontend/src/composables/useAuditLogs.ts`
- 儀表板生命週期與流程拆分：
  - `frontend/src/composables/useDashboardLifecycle.ts`
  - `frontend/src/composables/useDashboardOrchestrator.ts`
- UI 組件化（例如 `HelpdeskForm.vue`, `ActiveTicketsPanel.vue`, `ArchivePanel.vue`, `MembersAdminPanel.vue`）。

### 2.2 目前狀態判定
- 前端主流程已完成模組化重構（非單一巨大 `App.vue` 直寫模式）。
- 仍可持續優化的點：
  - `useTicketsView.ts` 仍偏大（約 410 行），可再切分 selector/filter 與 UI state。
  - `App.vue` 仍約 460 行，可再抽一層 page-level container。

## 3. 後端重構摘要

### 3.1 Service 分層（完成）
- Auth：
  - 協調層：`backend/src/main/java/com/example/demo/auth/AuthService.java`
  - Token：`backend/src/main/java/com/example/demo/auth/AuthTokenService.java`
  - 成員管理：`backend/src/main/java/com/example/demo/auth/AuthMemberAdminService.java`
- Email：
  - 協調層：`backend/src/main/java/com/example/demo/email/EmailNotificationService.java`
  - Dispatch：`backend/src/main/java/com/example/demo/email/EmailDispatchService.java`
  - Payload：`backend/src/main/java/com/example/demo/email/EmailPayloadFactory.java`
  - Template：`backend/src/main/java/com/example/demo/email/EmailTemplateService.java`
- Helpdesk：
  - 協調層：`backend/src/main/java/com/example/demo/helpdesk/HelpdeskTicketService.java`
  - 附件：`backend/src/main/java/com/example/demo/helpdesk/HelpdeskAttachmentService.java`
  - 狀態歷程：`backend/src/main/java/com/example/demo/helpdesk/HelpdeskTicketHistoryService.java`
  - 快照序列化：`backend/src/main/java/com/example/demo/helpdesk/HelpdeskTicketSnapshotService.java`

### 3.2 Schema 管理（完成）
- 導入 Flyway：
  - `backend/pom.xml` 新增 `flyway-core`
  - `backend/src/main/resources/application.properties` 啟用 Flyway
- 建立 migration 基線：
  - `backend/src/main/resources/db/migration/V1__initial_schema_baseline.sql`
- 移除啟動期 schema patch：
  - `AuthSchemaPatch`, `HelpdeskSchemaPatch`, `AuditSchemaPatch`, `EmailSchemaPatch`

### 3.3 測試補強（完成）
- 新增單元測試：
  - `backend/src/test/java/com/example/demo/auth/AuthTokenServiceTest.java`
  - `backend/src/test/java/com/example/demo/email/EmailTemplateServiceTest.java`
  - `backend/src/test/java/com/example/demo/helpdesk/HelpdeskTicketHistoryServiceTest.java`
- 測試設定更新：
  - `backend/src/test/resources/application.properties`
  - 測試環境關閉 Flyway（H2 與 PostgreSQL 專用語法差異）

## 4. 驗證紀錄

### 4.1 前端
- 指令：`npm run test -- --run`
- 結果：4 files / 8 tests 全通過。
- 指令：`npm run build`
- 結果：建置成功。

### 4.2 後端
- 指令：`mvn clean test`
- 結果：`BUILD SUCCESS`，6 tests 全通過。

## 5. 風險與後續建議
- Flyway migration 目前包含 PostgreSQL 專用語法（`DO $$ ... $$`）。
  - 已在測試環境關閉 Flyway 避免 H2 失敗。
  - 建議下一步：將 migration 分離為 PostgreSQL 專用與 H2 相容策略（或改 Testcontainers PostgreSQL）。
- 前端仍有中大型 composable（`useTicketsView.ts`）可再拆分，建議作為下一階段重構。

## 6. 結論
- 前端：核心流程已完成模組化重構並可正常測試/建置。
- 後端：服務分層、schema 管理轉型（Flyway）、測試補強已完成並通過測試。
- 可進入下一階段（效能優化、整合測試與部署流程收斂）。
