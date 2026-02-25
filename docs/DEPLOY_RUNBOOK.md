# Helpdesk 部署與回復手冊（目前實務流程）

本文件描述目前專案在公司內網（ESXi VM + Docker + Portainer）環境的實際部署方式。

## 1. 架構摘要
- 主機：Linux VM（VMware ESXi 管理）
- 容器：
  - `fullstack-frontend`（Nginx）
  - `fullstack-backend`（Spring Boot）
  - `fullstack-postgres`（PostgreSQL）
- Stack 名稱：`helpdesk`
- Portainer：管理容器與 Stack（目前可能因 Git 認證失效無法直接 Pull）

## 2. 上線前檢查
1. 本機確認程式碼已推到 GitHub `main`
2. `.env` 已設定正式密碼（不可用預設值）
3. `docker-compose.yml` 已包含 network aliases（`backend/postgres`）

建議本機先驗證：
```bash
cd /Users/stormcorn/Codex/fullstack
cd backend && mvn test
cd ../frontend && npm run build
```

## 3. 本機（開發/驗證）重建流程
```bash
cd /Users/stormcorn/Codex/fullstack
docker compose up -d --build backend frontend
docker compose ps
docker compose logs --since=5m backend | tail -n 80
docker compose logs --since=5m frontend | tail -n 50
curl -i http://localhost:5173/api/hello
```

## 4. 遠端主機（SSH）部署流程
若 Portainer 無法 `Pull and redeploy`，可直接在遠端主機用 Docker CLI 部署。

```bash
ssh <deploy-user>@<deploy-host>
cd /data/compose/6
docker compose -p helpdesk -f docker-compose.yml up -d --build --force-recreate
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
docker logs --tail 120 fullstack-backend
docker logs --tail 80 fullstack-frontend
curl -i http://localhost:5173/api/hello
```

## 5. Portainer Stack 更新（UI）
若 Portainer Git 認證正常：
1. `Stacks` → `helpdesk`
2. `Pull and redeploy`（或 `Update the stack`）
3. 確認 `Repository reference = main`

若 Portainer Git 認證失效：
- 改用 SSH CLI（見第 4 節）或在 Portainer 更新 Git 認證（PAT/SSH）

## 6. 部署後驗證清單
1. 容器狀態皆為 `running`
2. `fullstack-postgres` 顯示 `healthy`
3. `fullstack-backend` logs 有：
   - `Started DemoApplication`
   - Flyway migration/baseline 正常訊息
4. `fullstack-frontend` logs 無：
   - `host not found in upstream`
5. API 測試：
   - `GET /api/hello` 回 `200`
6. 前端 UI：
   - 可登入
   - 工單列表可載入
   - 通知 API 可載入

## 7. 常見故障與處理
### A. Frontend `Exited (1)`
常見原因：Nginx upstream DNS 解析失敗（`backend` / `fullstack-backend`）

處理：
1. 確認 `docker-compose.yml` 的 `backend` network aliases 包含：
   - `backend`
   - `fullstack-backend`
2. 重建 stack

### B. Backend 啟動失敗，找不到 `postgres`
常見原因：Docker DNS 沒有 `postgres` alias

處理：
1. 確認 `docker-compose.yml` 的 `postgres` network aliases 包含：
   - `postgres`
   - `fullstack-postgres`
2. 重建 stack

### C. Portainer 顯示 Git 認證失敗
錯誤常見訊息：
- `authentication required: Invalid username or token`

處理：
- 改用 GitHub PAT（不要用 GitHub 密碼）
- 或改用 SSH deploy key

## 8. 回復（Rollback）原則
1. 優先回到上一個 Git commit（已驗證版本）
2. 用相同流程重建 `backend/frontend`
3. 不使用破壞性命令（如 `docker system prune`）當作第一步

## 9. 維運建議（下一步）
1. 修復 Portainer Git 認證（PAT 或 SSH）
2. 啟用 Portainer Webhook（搭配 GitHub Actions）
3. 將部署步驟標準化為單一腳本
