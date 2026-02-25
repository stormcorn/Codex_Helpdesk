# Portainer Webhook 自動部署（半自動 CD）

本文件說明如何在目前架構下，將 GitHub push 後的部署從手動操作升級為「半自動」。

## 1. 目標
- 保留 Portainer 管理 Stack 的方式
- Push 到 GitHub 後由 GitHub Actions 呼叫 Portainer Webhook
- Portainer 自動重新部署 `helpdesk` Stack

## 2. 前提條件
1. Portainer 的 `helpdesk` Stack 已可正常更新
2. Portainer 可從 GitHub 拉取 repo（PAT 或 SSH 認證已修好）
3. Stack 已設定 `Repository reference = main`

注意：
- 若 Portainer 本身無法拉 Git，Webhook 也會失敗（因為底層仍需 Portainer 更新 stack）

## 3. 在 Portainer 建立 Webhook
1. `Stacks` → `helpdesk`
2. 找 `Webhook` 區塊（依版本位置可能不同）
3. 啟用 webhook 並複製 URL

範例（示意）：
```text
https://portainer.company.local/api/webhooks/<long-random-id>
```

## 4. 在 GitHub 設定 Secrets
Repository → `Settings` → `Secrets and variables` → `Actions`

新增：
- `PORTAINER_WEBHOOK_URL`
  - 值：Portainer 提供的完整 Webhook URL

若 Portainer 使用內網自簽憑證且 GitHub Actions 無法驗證憑證：
- 建議先用公司正式憑證
- 不建議在 workflow 關閉 TLS 驗證

## 5. GitHub Actions 部署 Workflow（範例）
新增檔案：`.github/workflows/deploy-portainer.yml`

```yaml
name: Deploy via Portainer Webhook

on:
  workflow_run:
    workflows: ["CI"]
    types: [completed]

jobs:
  deploy:
    if: ${{ github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.head_branch == 'main' }}
    runs-on: ubuntu-latest
    steps:
      - name: Trigger Portainer webhook
        run: |
          curl -fsS -X POST "${{ secrets.PORTAINER_WEBHOOK_URL }}"
```

## 6. 推薦部署策略
### 策略 A（目前最實用）
- `CI` 成功後呼叫 Portainer webhook
- Portainer 重新部署 stack
- 你再做簡短人工驗證（UI + `api/hello`）

### 策略 B（更穩）
- `CI` 成功後先建立 image 並推 registry（GHCR）
- Portainer 只負責拉 image + 重建
- 可降低遠端 build 時間與不確定性

## 7. 失敗排查
### A. Webhook 有打到但 stack 沒更新
- 檢查 Portainer stack logs / events
- 確認 Git 認證是否失效（PAT 過期）

### B. 更新後 frontend 掛掉
- 檢查 `frontend` logs 是否有 `host not found in upstream`
- 確認 `docker-compose.yml` aliases 設定仍存在

### C. 更新後 backend 掛掉
- 檢查 `backend` logs 是否有 DB 連線錯誤
- 確認 `postgres` aliases 與 `.env` 密碼設定

## 8. 安全注意事項
1. Webhook URL 視同密鑰，僅存於 GitHub Secrets
2. 不要把 Webhook URL 寫進 repo 或文件截圖
3. 只允許公司網路可連 Portainer 管理介面
4. 優先使用 HTTPS 與有效憑證
