# Security SOP: Secrets Rotation and Incident Response

## 1. Scope
- 適用於：`token`, `api key`, `password`, `smtp/sendgrid key`, `db password`.
- 目標：避免敏感資料外洩，並在外洩時可快速止血。

## 2. Storage Rules
- 禁止將任何 secrets 寫入 Git 版本庫。
- 使用 `.env`（本機）或 Secret Manager（上線環境）。
- 只提交 `.env.example`，絕不提交 `.env`。
- 前端不可持有後端寄信 key（例如 SendGrid API key）。

## 3. Minimum Access Principle
- SendGrid API key 只開 `Mail Send` 權限。
- 依環境分 key（dev/staging/prod 不共用）。
- 服務帳號權限只給必要資源。

## 4. Rotation Policy
- 例行輪替：每 90 天。
- 立即輪替：
  - 人員異動
  - key 疑似外洩
  - 偵測到異常寄信/登入/存取

## 5. Rotation Steps (Standard)
1. 在 provider 建立新 key（先不刪舊 key）。
2. 更新 secret storage（`.env` 或 secret manager）。
3. 重啟服務並驗證功能（寄信、登入、DB 連線）。
4. 監控 15~30 分鐘確認無異常。
5. 刪除舊 key。
6. 記錄輪替時間、責任人、變更單號。

## 6. Incident Response (Leak Suspected)
1. 立即撤銷疑似外洩 key。
2. 產生新 key 並更新部署。
3. 強制失效受影響 token/session（若適用）。
4. 回溯查詢：
   - `audit_logs`
   - `notification_jobs`
   - `email_delivery_logs`
   - access logs（traceId）
5. 匯出事件報告（時間線、影響範圍、補救措施）。

## 7. Logging Rules
- 不可 log：
  - Authorization header
  - token/api key/password 原文
  - 完整敏感 payload
- 可 log：
  - traceId
  - jobId / providerMessageId（非敏感）
  - 錯誤碼與摘要（不含密文）

## 8. Repository Guardrails
- 啟用 secrets scan（建議 gitleaks）：
  - pre-commit（本機）
  - CI（PR gate）
- 發現疑似 secrets：
  - 立即 rotate
  - 清理歷史（必要時使用 BFG/git filter-repo）

## 9. Deployment Checklist
- [ ] `.env` 存在且權限限制（600）
- [ ] `APP_EMAIL_PROVIDER` 正確（prod 建議 `sendgrid`）
- [ ] `APP_EMAIL_SENDGRID_API_KEY` 已設
- [ ] 管理員預設密碼已修改
- [ ] 監控告警已開（失敗寄信率、異常登入）

## 10. Ownership
- 系統 owner：Platform / Backend lead
- 執行人：當值 SRE/Backend engineer
- 稽核窗口：Security/Compliance
