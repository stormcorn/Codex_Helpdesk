# Helpdesk 開發與維運規範（Engineering Standards）

本文件定義專案日常開發、版本控管、部署、安全與測試的最低標準。

## 1. 分支與版本管理規範
## 1.1 主分支策略
- `main` 為可部署分支（deployable branch）
- 上線部署以 `main` 為唯一來源（remote `/opt/fullstack`）
- 遠端部署機不得在工作目錄做未提交修改

## 1.2 Commit 規範（建議）
- 使用語意前綴：
  - `feat:`
  - `fix:`
  - `refactor:`
  - `docs:`
  - `chore:`
  - `security:`
- 一個 commit 盡量只做一類變更（功能 / 文件 / 部署腳本）

## 1.3 歷史重寫（force push）
- 非必要不使用 `git push --force`
- 若需重寫歷史（例如清除機敏資訊）：
  - 優先 `--force-with-lease`
  - 完成後同步更新部署機 repo（`fetch + reset --hard origin/main`）
  - 通知所有協作者重新同步

## 2. 開發規範（Frontend / Backend）
## 2.1 前端規範
- 技術棧：Vue 3 + TypeScript
- 所有 API 回應型別需在 `frontend/src/types.ts` 定義或擴充
- 新功能優先放入 composables，避免 `App.vue` 持續膨脹
- 手機版需驗證：
  - 不水平溢出
  - 長字串可換行
  - 表格可捲動或改卡片化

## 2.2 後端規範
- 技術棧：Spring Boot + JPA
- 控制器只做：
  - 驗證/授權入口
  - request/response mapping
- 業務邏輯集中於 service 層
- DB schema 變更以 Flyway migration 管理（避免 runtime schema patch）

## 2.3 API 相容性規範
- 既有 API 若需升級，優先保留相容路徑/格式
- 範例：
  - 回覆 API 保留 JSON 格式，新增 multipart 支援附件

## 3. 測試規範
## 3.1 本機開發前置驗證（建議）
- Backend：
  - `mvn test`
- Frontend：
  - `npm run build`

## 3.2 CI 規範（現行）
- GitHub Actions `CI`
  - backend：`mvn test`
  - frontend：`npm ci && npm run build`
- `CI` 綠燈後才可部署

## 3.3 驗收測試（手動）
- 核心流程至少確認：
  - 登入 / 註冊
  - 建單 / 回覆 / 狀態變更
  - 附件下載 / 預覽
  - 通知跳轉
  - 即時更新（雙視窗）

## 4. 部署規範（正式流程）
## 4.1 正式部署流程
- 採 `CI + SSH 手動部署`
- Remote 主機以 `/opt/fullstack` 為部署來源
- 使用腳本：`/opt/fullstack/scripts/deploy_helpdesk.sh`

## 4.2 禁止事項
- 不以 Portainer image-only stack 作為主要版本來源
- 不在 remote `/opt/fullstack` 手動改程式碼後直接 build（會失去版本可追溯性）

## 4.3 Remote 部署標準指令
```bash
cd /opt/fullstack
./scripts/deploy_helpdesk.sh
```

## 4.4 Remote Repo 同步策略
- 使用：
  - `git fetch origin main`
  - `git reset --hard origin/main`
- 原因：
  - 可處理 force-push / 歷史重寫後的分岔問題

## 5. 安全規範（摘要）
## 5.1 Secrets 管理
- `.env` 不可提交 Git
- 所有密碼/API key 必須由環境變數提供
- 預設弱密碼不得存在於正式環境設定

## 5.2 憑證與 HTTPS
- 內網正式環境建議：
  - 內網 DNS 主機名
  - Nginx TLS termination
  - 公司 CA 簽發憑證
- 不使用 `http://IP:port` 作為正式使用入口

## 5.3 日誌與敏感資訊
- 不記錄 token/password/API key 原文
- 可記錄 `traceId` 以利串接 access log / audit log

## 5.4 機敏資訊處理流程
- 若誤提交機敏資訊到 Git：
  1. 立即 rotate
  2. 清理最新版內容
  3. 視需要進行歷史重寫
  4. 使用 `--force-with-lease` 推送

## 6. 文件維護規範
- 功能改動：更新 `docs/SYSTEM_FUNCTION_SPEC.md`
- UI/UX 調整：更新 `docs/UI_UX_DESIGN_GUIDELINES.md`
- 流程/部署改動：更新 `docs/DEPLOY_RUNBOOK.md` 與 `README.md`
- 文件入口：同步維護 `docs/DOCUMENTATION_INDEX.md`

## 7. 變更管理清單（每次上版）
- [ ] 程式碼已 commit / push 到 `main`
- [ ] GitHub Actions `CI` 綠燈
- [ ] remote `/opt/fullstack` 執行部署腳本
- [ ] API ready (`/api/hello` = 200)
- [ ] 關鍵功能驗收
- [ ] 文件已更新（若本次變更涉及功能/流程/UI）
