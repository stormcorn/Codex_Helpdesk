# Fullstack Starter (Vue + TypeScript + Spring Boot + PostgreSQL)

## 專案結構

- `frontend`: Vue 3 + TypeScript + Vite（容器內使用 Nginx 提供靜態頁面）
- `backend`: Java 17 + Spring Boot + Spring Data JPA
- `postgres`: PostgreSQL 16

## 一鍵啟動（Docker Compose）

在 `fullstack` 目錄執行：

```bash
docker compose up --build
```

啟動後：

- 前端：`http://localhost:5173`
- 後端 API：`http://localhost:8080/api/hello`
- PostgreSQL：`localhost:5432`（db: `fullstack`, user: `app`, password: `app`）

停止服務：

```bash
docker compose down
```

若要連資料一起清除：

```bash
docker compose down -v
```

## 後端資料設定

`backend/src/main/resources/application.properties` 支援環境變數：

- `SPRING_DATASOURCE_URL`
- `SPRING_DATASOURCE_USERNAME`
- `SPRING_DATASOURCE_PASSWORD`

預設值：

- `jdbc:postgresql://localhost:5432/fullstack`
- `app`
- `app`

## 開發模式（不走 Docker）

### 1. 啟動 PostgreSQL（本機或容器）

資料庫資訊需符合上面預設值，或自行設定環境變數。

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

## 目前 API

- `GET /api/hello`
  - 回傳資料表 `greetings` 的第一筆 `message`
  - 若資料表沒有資料，啟動時會自動初始化為 `Hello from PostgreSQL + Spring Data JPA`
