<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';

type Role = 'ADMIN' | 'IT' | 'USER';

type Member = {
  id: number;
  employeeId: string;
  name: string;
  email: string;
  role: Role;
  createdAt: string;
};

type Attachment = {
  id: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
};

type TicketMessage = {
  id: number;
  content: string;
  authorEmployeeId: string;
  authorName: string;
  authorRole: Role;
  createdAt: string;
};

type Ticket = {
  id: number;
  name: string;
  email: string;
  subject: string;
  description: string;
  status: 'OPEN' | 'PROCEEDING' | 'PENDING' | 'CLOSED' | 'DELETED';
  createdByMemberId: number | null;
  deleted: boolean;
  deletedAt: string | null;
  createdAt: string;
  attachments: Attachment[];
  messages: TicketMessage[];
};

type NotificationItem = {
  id: number;
  type: 'TICKET_CREATED' | 'TICKET_REPLY' | 'TICKET_STATUS';
  message: string;
  ticketId: number | null;
  read: boolean;
  createdAt: string;
};

type NotificationListResponse = {
  notifications: NotificationItem[];
  unreadCount: number;
};

const STATUS_FLOW: Ticket['status'][] = ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'];

const TOKEN_KEY = 'helpdesk_auth_token';
const MAX_FILE_BYTES = 5 * 1024 * 1024;

const authMode = ref<'login' | 'register'>('login');
const registerStep = ref(1);
const authLoading = ref(false);
const authError = ref('');

const loginForm = reactive({ employeeId: '', password: '' });
const registerForm = reactive({ employeeId: '', name: '', email: '', password: '' });

const token = ref('');
const currentMember = ref<Member | null>(null);
const dashboardTab = ref<'helpdesk' | 'itdesk' | 'members'>('helpdesk');

const ticketForm = reactive({ name: '', email: '', subject: '', description: '' });
const selectedFiles = ref<File[]>([]);
const submittingTicket = ref(false);
const ticketFeedback = ref('');
const ticketFeedbackType = ref<'success' | 'error' | ''>('');
const tickets = ref<Ticket[]>([]);
const loadingTickets = ref(false);

const members = ref<Member[]>([]);
const loadingMembers = ref(false);
const membersFeedback = ref('');

const replyInputs = reactive<Record<number, string>>({});
const statusDrafts = reactive<Record<number, Ticket['status']>>({});
const itActionLoading = reactive<Record<number, boolean>>({});
const itFeedback = ref('');
const openTicketIds = reactive<Record<number, boolean>>({});

const lightboxOpen = ref(false);
const lightboxSrc = ref('');
const lightboxTitle = ref('');
const notifications = ref<NotificationItem[]>([]);
const unreadCount = ref(0);
const notificationsOpen = ref(false);
const notificationLoading = ref(false);
const notificationFeedback = ref('');
let notificationTimer: number | null = null;

const isAuthenticated = computed(() => Boolean(token.value));
const isAdmin = computed(() => currentMember.value?.role === 'ADMIN');
const isItOrAdmin = computed(() => currentMember.value?.role === 'IT' || currentMember.value?.role === 'ADMIN');

function effectiveStatus(ticket: Ticket): Ticket['status'] {
  return ticket.deleted ? 'DELETED' : ticket.status;
}

const ticketStats = computed(() => {
  const total = tickets.value.length;
  const open = tickets.value.filter((t) => effectiveStatus(t) === 'OPEN').length;
  const proceeding = tickets.value.filter((t) => effectiveStatus(t) === 'PROCEEDING').length;
  const pending = tickets.value.filter((t) => effectiveStatus(t) === 'PENDING').length;
  const closed = tickets.value.filter((t) => effectiveStatus(t) === 'CLOSED').length;
  const deleted = tickets.value.filter((t) => effectiveStatus(t) === 'DELETED').length;
  const todayNew = tickets.value.filter((t) => isToday(t.createdAt)).length;
  return { total, open, proceeding, pending, closed, deleted, todayNew };
});

function authHeaders(): HeadersInit {
  return { Authorization: `Bearer ${token.value}` };
}

function formatSize(bytes: number): string {
  return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}

function displayStatus(ticket: Ticket): string {
  return effectiveStatus(ticket);
}

function isToday(isoDateTime: string): boolean {
  const target = new Date(isoDateTime);
  const now = new Date();
  return (
    target.getFullYear() === now.getFullYear() &&
    target.getMonth() === now.getMonth() &&
    target.getDate() === now.getDate()
  );
}

function parseErrorMessage(fallback: string, body: unknown): string {
  if (typeof body === 'object' && body !== null && 'message' in body) {
    const v = (body as { message?: unknown }).message;
    if (typeof v === 'string' && v) return v;
  }
  return fallback;
}

async function requestJson<T>(url: string, init: RequestInit, fallback: string): Promise<T> {
  const response = await fetch(url, init);
  if (!response.ok) {
    let parsed: unknown = null;
    try {
      parsed = await response.json();
    } catch {
      // ignore
    }
    throw new Error(parseErrorMessage(fallback, parsed));
  }
  return (await response.json()) as T;
}

function attachmentViewUrl(ticketId: number, attachmentId: number): string {
  return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/view?token=${encodeURIComponent(token.value)}`;
}

function attachmentDownloadUrl(ticketId: number, attachmentId: number): string {
  return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/download?token=${encodeURIComponent(token.value)}`;
}

function isImageAttachment(attachment: Attachment): boolean {
  return attachment.contentType.startsWith('image/');
}

function openImageLightbox(ticketId: number, attachment: Attachment): void {
  lightboxSrc.value = attachmentViewUrl(ticketId, attachment.id);
  lightboxTitle.value = attachment.originalFilename;
  lightboxOpen.value = true;
}

function closeLightbox(): void {
  lightboxOpen.value = false;
  lightboxSrc.value = '';
  lightboxTitle.value = '';
}

function nextRegisterStep(): void {
  authError.value = '';
  if (registerStep.value === 1 && (!registerForm.employeeId || !registerForm.name)) {
    authError.value = '請先填寫員工工號與姓名。';
    return;
  }
  if (registerStep.value === 2) {
    if (!registerForm.email || !registerForm.password) {
      authError.value = '請填寫 Email 與密碼。';
      return;
    }
    if (registerForm.password.length < 8) {
      authError.value = '密碼至少 8 碼。';
      return;
    }
  }
  registerStep.value = Math.min(3, registerStep.value + 1);
}

function prevRegisterStep(): void {
  authError.value = '';
  registerStep.value = Math.max(1, registerStep.value - 1);
}

function applyAuth(newToken: string, member: Member): void {
  token.value = newToken;
  currentMember.value = member;
  localStorage.setItem(TOKEN_KEY, newToken);
  ticketForm.name = member.name;
  ticketForm.email = member.email;
}

async function login(): Promise<void> {
  authError.value = '';
  authLoading.value = true;
  try {
    const result = await requestJson<{ token: string; member: Member }>(
      '/api/auth/login',
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(loginForm) },
      '登入失敗'
    );
    applyAuth(result.token, result.member);
    await afterLoginLoad();
  } catch (e) {
    authError.value = e instanceof Error ? e.message : '登入失敗';
  } finally {
    authLoading.value = false;
  }
}

async function register(): Promise<void> {
  authError.value = '';
  authLoading.value = true;
  try {
    const result = await requestJson<{ token: string; member: Member }>(
      '/api/auth/register',
      { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(registerForm) },
      '註冊失敗'
    );
    applyAuth(result.token, result.member);
    await afterLoginLoad();
  } catch (e) {
    authError.value = e instanceof Error ? e.message : '註冊失敗';
  } finally {
    authLoading.value = false;
  }
}

async function restoreSession(): Promise<void> {
  const saved = localStorage.getItem(TOKEN_KEY);
  if (!saved) return;
  token.value = saved;
  try {
    const me = await requestJson<Member>('/api/auth/me', { headers: authHeaders() }, '登入已失效');
    currentMember.value = me;
    ticketForm.name = me.name;
    ticketForm.email = me.email;
    await afterLoginLoad();
  } catch {
    clearSession();
  }
}

async function afterLoginLoad(): Promise<void> {
  await loadTickets();
  await loadNotifications();
  startNotificationPolling();
  if (isAdmin.value) {
    dashboardTab.value = 'members';
    await loadMembers();
  } else if (isItOrAdmin.value) {
    dashboardTab.value = 'itdesk';
  } else {
    dashboardTab.value = 'helpdesk';
  }
}

async function logout(): Promise<void> {
  try {
    if (token.value) {
      await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders() });
    }
  } finally {
    clearSession();
  }
}

function clearSession(): void {
  stopNotificationPolling();
  token.value = '';
  currentMember.value = null;
  localStorage.removeItem(TOKEN_KEY);
  tickets.value = [];
  members.value = [];
  notifications.value = [];
  unreadCount.value = 0;
  notificationsOpen.value = false;
  dashboardTab.value = 'helpdesk';
}

function onFilesChanged(event: Event): void {
  const input = event.target as HTMLInputElement;
  selectedFiles.value = Array.from(input.files ?? []);
}

async function loadTickets(): Promise<void> {
  loadingTickets.value = true;
  ticketFeedback.value = '';
  try {
    const data = await requestJson<Ticket[]>('/api/helpdesk/tickets', { headers: authHeaders() }, '讀取工單失敗');
    tickets.value = data;
    data.forEach((t) => {
      statusDrafts[t.id] = t.status;
      replyInputs[t.id] = replyInputs[t.id] ?? '';
      if (openTicketIds[t.id] === undefined) {
        openTicketIds[t.id] = false;
      }
    });
  } catch (e) {
    ticketFeedback.value = e instanceof Error ? e.message : '讀取工單失敗';
    ticketFeedbackType.value = 'error';
  } finally {
    loadingTickets.value = false;
  }
}

async function submitTicket(): Promise<void> {
  ticketFeedback.value = '';
  ticketFeedbackType.value = '';

  if (!ticketForm.name || !ticketForm.email || !ticketForm.subject || !ticketForm.description) {
    ticketFeedback.value = '請完整填寫所有欄位。';
    ticketFeedbackType.value = 'error';
    return;
  }

  const oversized = selectedFiles.value.find((f) => f.size >= MAX_FILE_BYTES);
  if (oversized) {
    ticketFeedback.value = `檔案 ${oversized.name} 超過 5MB 限制。`;
    ticketFeedbackType.value = 'error';
    return;
  }

  submittingTicket.value = true;
  try {
    const formData = new FormData();
    formData.append('name', ticketForm.name);
    formData.append('email', ticketForm.email);
    formData.append('subject', ticketForm.subject);
    formData.append('description', ticketForm.description);
    selectedFiles.value.forEach((f) => formData.append('files', f));

    const response = await fetch('/api/helpdesk/tickets', { method: 'POST', headers: authHeaders(), body: formData });
    if (!response.ok) {
      let parsed: unknown = null;
      try { parsed = await response.json(); } catch { /* ignore */ }
      throw new Error(parseErrorMessage('送出失敗', parsed));
    }

    const created = (await response.json()) as Ticket;
    tickets.value = [created, ...tickets.value].slice(0, 20);
    statusDrafts[created.id] = created.status;
    replyInputs[created.id] = '';
    openTicketIds[created.id] = false;
    ticketForm.subject = '';
    ticketForm.description = '';
    selectedFiles.value = [];
    ticketFeedback.value = `工單送出成功 #${created.id}`;
    ticketFeedbackType.value = 'success';
  } catch (e) {
    ticketFeedback.value = e instanceof Error ? e.message : '送出失敗';
    ticketFeedbackType.value = 'error';
  } finally {
    submittingTicket.value = false;
  }
}

async function updateTicketStatus(ticket: Ticket): Promise<void> {
  const status = statusDrafts[ticket.id];
  if (!status) return;
  itActionLoading[ticket.id] = true;
  itFeedback.value = '';
  try {
    const updated = await requestJson<Ticket>(
      `/api/helpdesk/tickets/${ticket.id}/status`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({ status })
      },
      '更新狀態失敗'
    );
    replaceTicket(updated);
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '更新狀態失敗';
  } finally {
    itActionLoading[ticket.id] = false;
  }
}

function getNextStatus(status: Ticket['status']): Ticket['status'] {
  const idx = STATUS_FLOW.indexOf(status);
  if (idx < 0) return 'OPEN';
  return STATUS_FLOW[(idx + 1) % STATUS_FLOW.length];
}

async function quickAdvanceTicketStatus(ticket: Ticket): Promise<void> {
  if (ticket.deleted) return;
  statusDrafts[ticket.id] = getNextStatus(ticket.status);
  await updateTicketStatus(ticket);
}

async function sendReply(ticket: Ticket): Promise<void> {
  if (ticket.deleted) return;
  const content = (replyInputs[ticket.id] ?? '').trim();
  if (!content) return;
  itActionLoading[ticket.id] = true;
  itFeedback.value = '';
  try {
    const updated = await requestJson<Ticket>(
      `/api/helpdesk/tickets/${ticket.id}/messages`,
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({ content })
      },
      '回覆失敗'
    );
    replyInputs[ticket.id] = '';
    replaceTicket(updated);
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '回覆失敗';
  } finally {
    itActionLoading[ticket.id] = false;
  }
}

function replaceTicket(updated: Ticket): void {
  tickets.value = tickets.value.map((t) => (t.id === updated.id ? updated : t));
  statusDrafts[updated.id] = updated.status;
  if (openTicketIds[updated.id] === undefined) {
    openTicketIds[updated.id] = false;
  }
}

async function loadNotifications(silent = false): Promise<void> {
  if (!token.value) return;
  if (!silent) notificationLoading.value = true;
  notificationFeedback.value = '';
  try {
    const data = await requestJson<NotificationListResponse>(
      '/api/notifications',
      { headers: authHeaders() },
      '讀取通知失敗'
    );
    notifications.value = data.notifications;
    unreadCount.value = data.unreadCount;
  } catch (e) {
    if (!silent) {
      notificationFeedback.value = e instanceof Error ? e.message : '讀取通知失敗';
    }
  } finally {
    if (!silent) notificationLoading.value = false;
  }
}

async function markNotificationRead(notificationId: number): Promise<void> {
  try {
    await fetch(`/api/notifications/${notificationId}/read`, { method: 'PATCH', headers: authHeaders() });
    const target = notifications.value.find((n) => n.id === notificationId);
    if (target && !target.read) {
      target.read = true;
      unreadCount.value = Math.max(0, unreadCount.value - 1);
    }
  } catch {
    // ignore optimistic update failure
  }
}

async function markAllNotificationsRead(): Promise<void> {
  notificationFeedback.value = '';
  try {
    await requestJson('/api/notifications/read-all', { method: 'PATCH', headers: authHeaders() }, '全部已讀失敗');
    notifications.value = notifications.value.map((n) => ({ ...n, read: true }));
    unreadCount.value = 0;
  } catch (e) {
    notificationFeedback.value = e instanceof Error ? e.message : '全部已讀失敗';
  }
}

function startNotificationPolling(): void {
  stopNotificationPolling();
  notificationTimer = window.setInterval(() => {
    void loadNotifications(true);
  }, 15000);
}

function stopNotificationPolling(): void {
  if (notificationTimer !== null) {
    window.clearInterval(notificationTimer);
    notificationTimer = null;
  }
}

async function openNotification(item: NotificationItem): Promise<void> {
  if (!item.read) {
    await markNotificationRead(item.id);
  }
  if (item.ticketId) {
    dashboardTab.value = isItOrAdmin.value ? 'itdesk' : 'helpdesk';
    await loadTickets();
    openTicketIds[item.ticketId] = true;
    window.setTimeout(() => {
      const target = document.getElementById(`ticket-${item.ticketId}`);
      target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 80);
  }
  notificationsOpen.value = false;
}

function toggleTicket(ticketId: number): void {
  openTicketIds[ticketId] = !openTicketIds[ticketId];
}

function canDeleteTicket(ticket: Ticket): boolean {
  const member = currentMember.value;
  if (!member || ticket.deleted) return false;
  if (member.role === 'ADMIN' || member.role === 'IT') return true;
  return ticket.createdByMemberId === member.id;
}

async function softDeleteTicket(ticket: Ticket): Promise<void> {
  if (!canDeleteTicket(ticket)) return;
  if (!confirm(`確認將工單 #${ticket.id} 標記為刪除？`)) return;
  itFeedback.value = '';
  itActionLoading[ticket.id] = true;
  try {
    const updated = await requestJson<Ticket>(
      `/api/helpdesk/tickets/${ticket.id}/delete`,
      { method: 'PATCH', headers: authHeaders() },
      '刪除工單失敗'
    );
    replaceTicket(updated);
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '刪除工單失敗';
  } finally {
    itActionLoading[ticket.id] = false;
  }
}

async function loadMembers(): Promise<void> {
  if (!isAdmin.value) return;
  loadingMembers.value = true;
  membersFeedback.value = '';
  try {
    members.value = await requestJson<Member[]>('/api/admin/members', { headers: authHeaders() }, '讀取成員失敗');
  } catch (e) {
    membersFeedback.value = e instanceof Error ? e.message : '讀取成員失敗';
  } finally {
    loadingMembers.value = false;
  }
}

async function updateMemberRole(member: Member, role: Role): Promise<void> {
  if (member.role === 'ADMIN') return;
  try {
    const updated = await requestJson<Member>(
      `/api/admin/members/${member.id}/role`,
      {
        method: 'PATCH',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({ role })
      },
      '更新角色失敗'
    );
    members.value = members.value.map((m) => (m.id === updated.id ? updated : m));
  } catch (e) {
    membersFeedback.value = e instanceof Error ? e.message : '更新角色失敗';
  }
}

async function deleteMember(member: Member): Promise<void> {
  if (member.role === 'ADMIN') return;
  if (!confirm(`確定刪除 ${member.name} (${member.employeeId})？`)) return;
  try {
    const response = await fetch(`/api/admin/members/${member.id}`, { method: 'DELETE', headers: authHeaders() });
    if (!response.ok) {
      let parsed: unknown = null;
      try { parsed = await response.json(); } catch { /* ignore */ }
      throw new Error(parseErrorMessage('刪除失敗', parsed));
    }
    members.value = members.value.filter((m) => m.id !== member.id);
  } catch (e) {
    membersFeedback.value = e instanceof Error ? e.message : '刪除失敗';
  }
}

onMounted(async () => {
  await restoreSession();
});

onBeforeUnmount(() => {
  stopNotificationPolling();
});
</script>

<template>
  <main class="page">
    <template v-if="!isAuthenticated">
      <section class="auth-panel">
        <h1>Helpdesk Member Portal</h1>
        <p class="subtitle">登入或註冊後即可提交工單。管理員可指派 IT 角色處理工單。</p>

        <div class="switch-row">
          <button :class="{ active: authMode === 'login' }" @click="authMode = 'login'">登入</button>
          <button :class="{ active: authMode === 'register' }" @click="authMode = 'register'">註冊</button>
        </div>

        <form v-if="authMode === 'login'" class="form-grid" @submit.prevent="login">
          <label>帳號（員工工號）<input v-model="loginForm.employeeId" required /></label>
          <label>密碼<input v-model="loginForm.password" type="password" required /></label>
          <button :disabled="authLoading" type="submit">{{ authLoading ? '登入中...' : '登入' }}</button>
        </form>

        <div v-else>
          <div class="stepper">
            <span :class="{ on: registerStep >= 1 }">1</span>
            <span :class="{ on: registerStep >= 2 }">2</span>
            <span :class="{ on: registerStep >= 3 }">3</span>
          </div>
          <form class="form-grid" @submit.prevent="registerStep === 3 ? register() : nextRegisterStep()">
            <template v-if="registerStep === 1">
              <label>帳號（員工工號）<input v-model="registerForm.employeeId" required /></label>
              <label>姓名<input v-model="registerForm.name" required /></label>
            </template>
            <template v-else-if="registerStep === 2">
              <label>Email<input v-model="registerForm.email" type="email" required /></label>
              <label>密碼（至少 8 碼）<input v-model="registerForm.password" type="password" minlength="8" required /></label>
            </template>
            <template v-else>
              <div class="confirm-box">
                <p>工號：{{ registerForm.employeeId }}</p>
                <p>姓名：{{ registerForm.name }}</p>
                <p>Email：{{ registerForm.email }}</p>
              </div>
            </template>
            <div class="row">
              <button v-if="registerStep > 1" type="button" @click="prevRegisterStep">上一步</button>
              <button :disabled="authLoading" type="submit">{{ registerStep === 3 ? '完成註冊' : '下一步' }}</button>
            </div>
          </form>
        </div>

        <p v-if="authError" class="feedback error">{{ authError }}</p>
      </section>
    </template>

    <template v-else>
      <section class="header-panel">
        <div>
          <h1>{{ currentMember?.name }}</h1>
          <p>{{ currentMember?.employeeId }} · {{ currentMember?.role }}</p>
        </div>
        <div class="header-actions">
          <button class="notify-toggle" type="button" @click="notificationsOpen = !notificationsOpen">
            通知
            <span v-if="unreadCount > 0" class="notify-badge">{{ unreadCount }}</span>
          </button>
          <button @click="logout">登出</button>
        </div>
      </section>

      <section v-if="notificationsOpen" class="panel notify-panel">
        <div class="notify-head">
          <h3>通知中心</h3>
          <button type="button" @click="markAllNotificationsRead">全部已讀</button>
        </div>
        <p v-if="notificationLoading">讀取通知中...</p>
        <p v-else-if="!notifications.length">目前沒有通知</p>
        <ul v-else class="simple-list notify-list">
          <li v-for="item in notifications" :key="item.id" :class="{ unread: !item.read }">
            <button class="notify-item" type="button" @click="openNotification(item)">
              <strong>{{ item.message }}</strong>
              <small>{{ new Date(item.createdAt).toLocaleString() }}</small>
            </button>
          </li>
        </ul>
        <p v-if="notificationFeedback" class="feedback error">{{ notificationFeedback }}</p>
      </section>

      <section class="tabs">
        <button :class="{ active: dashboardTab === 'helpdesk' }" @click="dashboardTab = 'helpdesk'">提交工單</button>
        <button v-if="isItOrAdmin" :class="{ active: dashboardTab === 'itdesk' }" @click="dashboardTab = 'itdesk'">IT 工單處理</button>
        <button v-if="isAdmin" :class="{ active: dashboardTab === 'members' }" @click="dashboardTab = 'members'; loadMembers()">成員管理</button>
      </section>

      <section v-if="dashboardTab === 'helpdesk'" class="panel">
        <h2>新增工單</h2>
        <form class="form-grid" @submit.prevent="submitTicket">
          <label>姓名<input v-model="ticketForm.name" required /></label>
          <label>Email<input v-model="ticketForm.email" type="email" required /></label>
          <label>主旨<input v-model="ticketForm.subject" required /></label>
          <label>問題描述<textarea v-model="ticketForm.description" rows="5" required /></label>
          <label>附件（可多檔，每檔 < 5MB）<input type="file" multiple @change="onFilesChanged" /></label>
          <ul v-if="selectedFiles.length" class="simple-list">
            <li v-for="f in selectedFiles" :key="f.name + f.lastModified">{{ f.name }} ({{ formatSize(f.size) }})</li>
          </ul>
          <button :disabled="submittingTicket" type="submit">{{ submittingTicket ? '送出中...' : '送出工單' }}</button>
        </form>
        <p v-if="ticketFeedback" class="feedback" :class="ticketFeedbackType">{{ ticketFeedback }}</p>
      </section>

      <section v-if="dashboardTab === 'itdesk' || dashboardTab === 'helpdesk'" class="panel">
        <div class="ticket-list-top">
          <h2>工單列表</h2>
          <div class="ticket-stats">
            <span class="stat-chip">總數 <strong>{{ ticketStats.total }}</strong></span>
            <span class="stat-chip">本日新增 <strong>{{ ticketStats.todayNew }}</strong></span>
            <span class="stat-chip status-open">OPEN <strong>{{ ticketStats.open }}</strong></span>
            <span class="stat-chip status-proceeding">PROCEEDING <strong>{{ ticketStats.proceeding }}</strong></span>
            <span class="stat-chip status-pending">PENDING <strong>{{ ticketStats.pending }}</strong></span>
            <span class="stat-chip status-closed">CLOSED <strong>{{ ticketStats.closed }}</strong></span>
            <span class="stat-chip status-deleted">DELETED <strong>{{ ticketStats.deleted }}</strong></span>
          </div>
        </div>
        <p v-if="loadingTickets">讀取中...</p>
        <ul v-else class="ticket-list">
          <li v-for="ticket in tickets" :id="`ticket-${ticket.id}`" :key="ticket.id">
            <div class="ticket-head">
              <button class="ticket-toggle" type="button" @click="toggleTicket(ticket.id)">
                <strong :class="{ deleted: ticket.deleted }">#{{ ticket.id }} {{ ticket.subject }}</strong>
                <small>{{ openTicketIds[ticket.id] ? '收合' : '展開' }}</small>
              </button>
              <button
                v-if="canDeleteTicket(ticket)"
                class="danger ticket-delete-btn"
                type="button"
                :disabled="itActionLoading[ticket.id] || ticket.deleted"
                @click="softDeleteTicket(ticket)"
              >
                {{ ticket.deleted ? '已刪除' : '刪除' }}
              </button>
              <small v-if="isItOrAdmin" class="status-hint">點擊狀態標籤可快速切換</small>
              <button
                v-if="isItOrAdmin"
                :class="['status-tag', 'status-button', `status-${effectiveStatus(ticket).toLowerCase()}`]"
                :disabled="itActionLoading[ticket.id] || ticket.deleted"
                :title="`點擊切換狀態（下一步：${getNextStatus(ticket.status)}）`"
                type="button"
                @click="quickAdvanceTicketStatus(ticket)"
              >
                {{ displayStatus(ticket) }}
              </button>
              <span v-else :class="['status-tag', `status-${effectiveStatus(ticket).toLowerCase()}`]">{{ displayStatus(ticket) }}</span>
            </div>
            <div v-if="openTicketIds[ticket.id]" class="ticket-content">
              <p :class="{ deleted: ticket.deleted }">{{ ticket.description }}</p>
              <small>{{ ticket.name }} ({{ ticket.email }}) · {{ new Date(ticket.createdAt).toLocaleString() }}</small>
              <small v-if="ticket.deletedAt"> · 已刪除於 {{ new Date(ticket.deletedAt).toLocaleString() }}</small>

              <ul v-if="ticket.attachments.length" class="simple-list">
                <li v-for="att in ticket.attachments" :key="att.id">
                  <template v-if="isImageAttachment(att)">
                    <button class="link-button" type="button" @click="openImageLightbox(ticket.id, att)">預覽 {{ att.originalFilename }}</button>
                  </template>
                  <template v-else>
                    <a :href="attachmentDownloadUrl(ticket.id, att.id)" target="_blank" rel="noopener">下載 {{ att.originalFilename }}</a>
                  </template>
                </li>
              </ul>

              <div class="message-box">
                <h4>工單訊息</h4>
                <ul class="simple-list">
                  <li v-for="msg in ticket.messages" :key="msg.id">
                    <strong>[{{ msg.authorRole }}] {{ msg.authorName }}</strong>：{{ msg.content }}
                    <small> · {{ new Date(msg.createdAt).toLocaleString() }}</small>
                  </li>
                </ul>
              </div>

              <div v-if="isItOrAdmin" class="it-actions">
                <div class="row">
                  <input v-model="replyInputs[ticket.id]" placeholder="輸入回覆訊息" />
                  <button :disabled="itActionLoading[ticket.id] || ticket.deleted" @click="sendReply(ticket)">送出回覆</button>
                </div>
              </div>
            </div>
          </li>
        </ul>
        <p v-if="itFeedback" class="feedback error">{{ itFeedback }}</p>
      </section>

      <section v-if="dashboardTab === 'members'" class="panel">
        <h2>成員管理（Admin）</h2>
        <p class="subtitle">可指派 USER / IT。不可將任一帳號設為 ADMIN，也不可刪除 ADMIN。</p>
        <p v-if="membersFeedback" class="feedback error">{{ membersFeedback }}</p>
        <p v-if="loadingMembers">讀取成員中...</p>
        <table v-else class="member-table">
          <thead>
            <tr>
              <th>工號</th><th>姓名</th><th>Email</th><th>角色</th><th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="m in members" :key="m.id">
              <td>{{ m.employeeId }}</td>
              <td>{{ m.name }}</td>
              <td>{{ m.email }}</td>
              <td>{{ m.role }}</td>
              <td>
                <template v-if="m.role !== 'ADMIN'">
                  <div class="row">
                    <button @click="updateMemberRole(m, 'USER')">設為 USER</button>
                    <button @click="updateMemberRole(m, 'IT')">設為 IT</button>
                    <button class="danger" @click="deleteMember(m)">刪除</button>
                  </div>
                </template>
                <span v-else>管理員不可變更</span>
              </td>
            </tr>
          </tbody>
        </table>
      </section>

      <div v-if="lightboxOpen" class="lightbox" @click.self="closeLightbox">
        <div class="lightbox-body">
          <button @click="closeLightbox">關閉</button>
          <img :src="lightboxSrc" :alt="lightboxTitle" />
          <p>{{ lightboxTitle }}</p>
        </div>
      </div>
    </template>
  </main>
</template>
