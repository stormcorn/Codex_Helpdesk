# Helpdesk 文件總索引（重整版）

本文件作為專案文件入口，將文件分為「系統功能」、「規範」、「UI/UX」、「部署與維運」、「歷史紀錄」五類。

## 1. 系統功能文件
- `docs/SYSTEM_FUNCTION_SPEC.md`
  - 系統功能全貌、角色權限、功能模組、API 對應、事件流程（含即時更新）

## 2. 規範文件
- `docs/ENGINEERING_STANDARDS.md`
  - 開發/版本/部署/安全/測試/變更管理規範（專案執行標準）
- `docs/security-key-rotation-sop.md`
  - Secrets 輪替與資安事件應變 SOP
- `docs/email-notification-spec.md`
  - Email 通知子系統規格（MVP/Phase 規劃）

## 3. UI/UX 設計文件
- `docs/UI_UX_DESIGN_GUIDELINES.md`
  - 介面結構、互動流程、狀態/權限呈現、手機版設計原則與檢查清單

## 4. 部署與維運文件
- `docs/DEPLOY_RUNBOOK.md`
  - 正式部署流程（CI + SSH 手動部署）
- `docs/PORTAINER_WEBHOOK_CD.md`
  - Portainer Webhook CD 說明（目前非正式流程）

## 5. 架構 / 歷史紀錄
- `docs/CURRENT_ARCHITECTURE.md`
  - 系統架構快照（歷史快照，可能與現況略有差異）
- `docs/REFRACTOR_REPORT_2026-02-17.md`
  - 前後端重構報告（歷史紀錄）

## 6. 閱讀順序（建議）
1. `docs/SYSTEM_FUNCTION_SPEC.md`
2. `docs/UI_UX_DESIGN_GUIDELINES.md`
3. `docs/ENGINEERING_STANDARDS.md`
4. `docs/DEPLOY_RUNBOOK.md`

## 7. 維護原則
- 新功能上線：同步更新 `SYSTEM_FUNCTION_SPEC` 與必要 UI/UX 章節
- 新部署流程變更：同步更新 `DEPLOY_RUNBOOK` 與 `README`
- 新安全要求：同步更新 `ENGINEERING_STANDARDS` 與 `security-key-rotation-sop`
