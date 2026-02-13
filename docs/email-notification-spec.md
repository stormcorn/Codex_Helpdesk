# Helpdesk Email Notification Spec (MVP v1)

## 1. Scope
- 目標：建立交易型通知信（必寄）能力，與現有站內通知並行。
- 首批事件（MVP）：
  - `USER_REGISTERED`
  - `TICKET_CREATED`
  - `TICKET_REPLIED`
  - `TICKET_CLOSED`
  - `TICKET_URGENT_SUPERVISOR_REQUIRED`

## 2. Architecture (Industry Standard)
- 採用「事件落地 + 背景發送」而非同步寄信。
- API/Service 層只負責寫入 `notification_jobs`。
- 背景 worker 定時撈 pending job，呼叫郵件供應商。
- 失敗重試（exponential backoff），超過上限進 dead letter。

### 2.1 Flow
1. 業務事件發生（註冊、建單、回覆、結案...）。
2. 寫入 `notification_jobs`（含 payload/templateKey/recipient）。
3. Worker 抓取 `PENDING` job 並 lock。
4. 套模板產生 html/text，呼叫 provider API。
5. 成功：`SENT`；失敗：`RETRYING` 或 `FAILED`。
6. 所有結果寫 `email_delivery_logs` 供稽核。

## 3. Database Design

## 3.1 `notification_jobs`
- 用途：郵件發送佇列（application-level queue）。

```sql
create table if not exists notification_jobs (
  id bigserial primary key,
  event_type varchar(80) not null,
  recipient_member_id bigint,
  recipient_email varchar(320) not null,
  template_key varchar(120) not null,
  locale varchar(20) not null default 'zh-TW',
  payload_json text not null,
  dedupe_key varchar(200),
  status varchar(20) not null default 'PENDING', -- PENDING|PROCESSING|RETRYING|SENT|FAILED|CANCELLED
  attempts int not null default 0,
  max_attempts int not null default 5,
  next_retry_at timestamp,
  last_error text,
  provider_message_id varchar(200),
  trace_id varchar(80),
  created_at timestamp not null default now(),
  updated_at timestamp not null default now(),
  sent_at timestamp
);
create index if not exists idx_notification_jobs_status_next_retry on notification_jobs(status, next_retry_at);
create unique index if not exists uq_notification_jobs_dedupe_key on notification_jobs(dedupe_key) where dedupe_key is not null;
```

## 3.2 `email_delivery_logs`
- 用途：不可變發送紀錄（稽核/追查）。

```sql
create table if not exists email_delivery_logs (
  id bigserial primary key,
  job_id bigint not null references notification_jobs(id),
  event_type varchar(80) not null,
  recipient_email varchar(320) not null,
  template_key varchar(120) not null,
  provider varchar(40) not null, -- SES/SendGrid/...
  provider_message_id varchar(200),
  success boolean not null,
  error_code varchar(80),
  error_message text,
  trace_id varchar(80),
  created_at timestamp not null default now()
);
create index if not exists idx_email_delivery_logs_job_id on email_delivery_logs(job_id);
create index if not exists idx_email_delivery_logs_trace_id on email_delivery_logs(trace_id);
```

## 4. Domain Events -> Email Rules

## 4.1 `USER_REGISTERED`
- 收件者：註冊者本人。
- 觸發時機：註冊成功後。
- 目的：帳號啟用確認、建立信任。

## 4.2 `TICKET_CREATED`
- 收件者：提單者本人（確認成功）。
- 額外：若 `priority=URGENT`，另發 `TICKET_URGENT_SUPERVISOR_REQUIRED` 給群組主管。

## 4.3 `TICKET_REPLIED`
- 收件者：提單者本人（排除回覆者本人）。
- 內容：回覆摘要 + 連結。

## 4.4 `TICKET_CLOSED`
- 收件者：提單者本人。
- 內容：結案時間、結果摘要、是否可 reopen（若未實作則先不放）。

## 4.5 `TICKET_URGENT_SUPERVISOR_REQUIRED`
- 收件者：工單所屬群組主管。
- 內容：急件提醒、一鍵進入工單頁面。

## 5. Template Contract

## 5.1 Key Naming
- `user_registered_v1`
- `ticket_created_v1`
- `ticket_replied_v1`
- `ticket_closed_v1`
- `ticket_urgent_supervisor_required_v1`

## 5.2 Payload Minimum Fields
- 共通欄位：
  - `recipientName`
  - `appUrl`
  - `traceId`
- 工單相關：
  - `ticketId`
  - `subject`
  - `status`
  - `priority`
  - `groupName`
  - `actorName`
  - `actionTime`
  - `ticketUrl`

## 5.3 Subject Draft (zh-TW)
- 註冊：`[Helpdesk] 註冊成功通知`
- 建單：`[Helpdesk] 工單 #{{ticketId}} 已建立`
- 回覆：`[Helpdesk] 工單 #{{ticketId}} 有新回覆`
- 結案：`[Helpdesk] 工單 #{{ticketId}} 已完成`
- 急件主管：`[Helpdesk] 急件工單 #{{ticketId}} 待主管確認`

## 6. Retry / Failure Policy
- `max_attempts=5`
- backoff 建議：`1m -> 5m -> 15m -> 1h -> 6h`
- 規則：
  - 臨時錯誤（timeout/5xx）=> RETRYING
  - 永久錯誤（invalid email/blocked）=> FAILED
- 保留 `last_error` 與 provider code。

## 7. Security / Compliance
- 交易型信件不提供退訂（可提供偏好設定但預設開）。
- 避免寫敏感資訊在 payload（不含密碼/token）。
- 建議在信中只放 ticket 連結，不放完整內文敏感細節。
- 使用 SPF/DKIM/DMARC，避免落垃圾信。

## 8. Observability
- 每次 job 生成時寫 `trace_id`（沿用現有 request trace）。
- `notification_jobs` 與 `email_delivery_logs` 皆可依 `trace_id` 查詢。
- 建議加 metrics：
  - `email_jobs_created_total`
  - `email_jobs_sent_total`
  - `email_jobs_failed_total`
  - `email_send_latency_ms`

## 9. API / Admin (Phase 2)
- `GET /api/admin/email-jobs?status=...&eventType=...`
- `POST /api/admin/email-jobs/{id}/retry`
- `GET /api/admin/email-delivery-logs?...`

MVP 可先不開 API，只靠 DB + log 驗證。

## 10. Implementation Plan
1. 建 DB schema patch（兩張表 + index）。
2. 新增 `EmailNotificationService`（enqueue）。
3. 新增 `EmailJobWorker`（`@Scheduled` 輪詢）。
4. 接 provider adapter（先做 `ConsoleEmailProvider`，再接 SES/SendGrid）。
5. 在既有事件點掛 enqueue：
   - 註冊成功
   - 建單成功
   - 回覆成功
   - 結案（status=CLOSED）
   - 急件待主管確認
6. 加 delivery log + retry。
7. 文件補齊與驗收腳本。

## 11. Acceptance Criteria (MVP)
- 註冊、建單、回覆、結案、急件主管提醒都會產生 job。
- Worker 可成功送信並更新 `SENT`。
- 失敗可重試，超過上限轉 `FAILED`。
- 能用 traceId 從 audit/access log 對到 email job 與 delivery log。
- 至少一封信可在真實 mailbox 收到（staging）。
