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

type TicketStatusHistory = {
  id: number;
  fromStatus: string | null;
  toStatus: string;
  changedByMemberId: number | null;
  changedByEmployeeId: string;
  changedByName: string;
  changedByRole: string;
  createdAt: string;
};

type TicketPriority = 'GENERAL' | 'URGENT';

type MyGroup = {
  id: number;
  name: string;
  supervisor: boolean;
};

type AdminGroupMember = {
  memberId: number;
  employeeId: string;
  name: string;
  role: Role;
  supervisor: boolean;
};

type AdminGroup = {
  id: number;
  name: string;
  createdAt: string;
  members: AdminGroupMember[];
};

type Ticket = {
  id: number;
  name: string;
  email: string;
  subject: string;
  description: string;
  status: 'OPEN' | 'PROCEEDING' | 'PENDING' | 'CLOSED' | 'DELETED';
  priority: TicketPriority;
  supervisorApproved: boolean;
  supervisorApprovedByMemberId: number | null;
  supervisorApprovedAt: string | null;
  groupId: number | null;
  groupName: string | null;
  createdByMemberId: number | null;
  deleted: boolean;
  deletedAt: string | null;
  createdAt: string;
  attachments: Attachment[];
  messages: TicketMessage[];
  statusHistories: TicketStatusHistory[];
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

const ticketForm = reactive<{
  name: string;
  email: string;
  subject: string;
  description: string;
  priority: TicketPriority;
  groupId: number | null;
}>({
  name: '',
  email: '',
  subject: '',
  description: '',
  priority: 'GENERAL',
  groupId: null
});
const selectedFiles = ref<File[]>([]);
const submittingTicket = ref(false);
const ticketFeedback = ref('');
const ticketFeedbackType = ref<'success' | 'error' | ''>('');
const tickets = ref<Ticket[]>([]);
const loadingTickets = ref(false);
const ticketKeyword = ref('');
const onlyMyTickets = ref(false);
const createdTimeSort = ref<'newest' | 'oldest'>('newest');
const statusFilter = ref<'ALL' | Ticket['status']>('ALL');

const members = ref<Member[]>([]);
const loadingMembers = ref(false);
const membersFeedback = ref('');
const myGroups = ref<MyGroup[]>([]);
const adminGroups = ref<AdminGroup[]>([]);
const loadingGroups = ref(false);
const groupsFeedback = ref('');
const createGroupName = ref('');
const groupAssignForm = reactive<{ groupId: number | null; memberId: number | null }>({ groupId: null, memberId: null });

const replyInputs = reactive<Record<number, string>>({});
const statusDrafts = reactive<Record<number, Ticket['status']>>({});
const itActionLoading = reactive<Record<number, boolean>>({});
const itFeedback = ref('');
const openTicketIds = reactive<Record<number, boolean>>({});
const newTicketHighlights = reactive<Record<number, boolean>>({});
const jumpTicketHighlights = reactive<Record<number, boolean>>({});
const ticketHighlightTimers = reactive<Record<string, number>>({});

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

const EDITABLE_STATUSES = ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'] as const;

function normalizeStatus(value: unknown): Ticket['status'] {
  const normalized = String(value ?? '').trim().toUpperCase();
  if (normalized === 'DELETED') return 'DELETED';
  if (normalized === 'OPEN') return 'OPEN';
  if (normalized === 'PROCEEDING') return 'PROCEEDING';
  if (normalized === 'PENDING') return 'PENDING';
  if (normalized === 'CLOSED') return 'CLOSED';
  return 'OPEN';
}

function normalizePriority(value: unknown): TicketPriority {
  return String(value ?? '').trim().toUpperCase() === 'URGENT' ? 'URGENT' : 'GENERAL';
}

function effectiveStatus(ticket: Ticket): Ticket['status'] {
  if (ticket.deleted || ticket.deletedAt) return 'DELETED';
  return normalizeStatus(ticket.status);
}

function isTicketDeleted(ticket: Ticket): boolean {
  return effectiveStatus(ticket) === 'DELETED';
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

const filteredTickets = computed(() => {
  const keyword = ticketKeyword.value.trim().toLowerCase();
  const memberId = currentMember.value?.id ?? null;

  return [...tickets.value]
    .filter((ticket) => {
      if (statusFilter.value !== 'ALL' && effectiveStatus(ticket) !== statusFilter.value) {
        return false;
      }
      if (onlyMyTickets.value && (!memberId || ticket.createdByMemberId !== memberId)) {
        return false;
      }
      if (!keyword) return true;

      const haystack = [
        String(ticket.id),
        ticket.subject,
        ticket.description,
        ticket.name,
        ticket.email,
        ticket.groupName ?? '',
        ticket.priority,
        effectiveStatus(ticket)
      ]
        .join(' ')
        .toLowerCase();
      return haystack.includes(keyword);
    })
    .sort((a, b) => {
      const diff = new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime();
      return createdTimeSort.value === 'newest' ? diff : -diff;
    });
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

function normalizeTicket(ticket: Ticket): Ticket {
  const priority = normalizePriority(ticket.priority);
  return {
    ...ticket,
    status: normalizeStatus(ticket.status),
    priority,
    supervisorApproved: priority === 'URGENT' ? Boolean(ticket.supervisorApproved) : true,
    supervisorApprovedByMemberId: ticket.supervisorApprovedByMemberId ?? null,
    supervisorApprovedAt: ticket.supervisorApprovedAt ?? null,
    groupId: ticket.groupId ?? null,
    groupName: ticket.groupName ?? null,
    statusHistories: Array.isArray(ticket.statusHistories) ? ticket.statusHistories : []
  };
}

function isCurrentMemberSupervisorOfGroup(groupId: number | null): boolean {
  if (!groupId) return false;
  return myGroups.value.some((g) => g.id === groupId && g.supervisor);
}

function formatStatusTransition(history: TicketStatusHistory): string {
  const toStatus = normalizeStatus(history.toStatus);
  if (!history.fromStatus) {
    return `初始化為 ${toStatus}`;
  }
  return `${normalizeStatus(history.fromStatus)} → ${toStatus}`;
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
  await loadMyGroups();
  await loadTickets();
  await loadNotifications();
  startNotificationPolling();
  if (isAdmin.value) {
    dashboardTab.value = 'members';
    await loadMembers();
    await loadAdminGroups();
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
  clearTicketHighlights();
  token.value = '';
  currentMember.value = null;
  localStorage.removeItem(TOKEN_KEY);
  tickets.value = [];
  members.value = [];
  myGroups.value = [];
  adminGroups.value = [];
  createGroupName.value = '';
  groupAssignForm.groupId = null;
  groupAssignForm.memberId = null;
  notifications.value = [];
  unreadCount.value = 0;
  notificationsOpen.value = false;
  dashboardTab.value = 'helpdesk';
}

async function loadMyGroups(): Promise<void> {
  if (!token.value) return;
  try {
    myGroups.value = await requestJson<MyGroup[]>('/api/groups/mine', { headers: authHeaders() }, '讀取群組失敗');
    if (myGroups.value.length && !ticketForm.groupId) {
      ticketForm.groupId = myGroups.value[0].id;
    }
  } catch {
    myGroups.value = [];
  }
}

function clearTicketHighlights(): void {
  Object.values(ticketHighlightTimers).forEach((timerId) => window.clearTimeout(timerId));
  Object.keys(ticketHighlightTimers).forEach((k) => delete ticketHighlightTimers[k]);
  Object.keys(newTicketHighlights).forEach((k) => delete newTicketHighlights[Number(k)]);
  Object.keys(jumpTicketHighlights).forEach((k) => delete jumpTicketHighlights[Number(k)]);
}

function highlightTicket(ticketId: number, kind: 'new' | 'jump', durationMs: number): void {
  const key = `${kind}-${ticketId}`;
  const previousTimer = ticketHighlightTimers[key];
  if (previousTimer) {
    window.clearTimeout(previousTimer);
  }

  if (kind === 'new') {
    newTicketHighlights[ticketId] = true;
  } else {
    jumpTicketHighlights[ticketId] = true;
  }

  ticketHighlightTimers[key] = window.setTimeout(() => {
    if (kind === 'new') {
      delete newTicketHighlights[ticketId];
    } else {
      delete jumpTicketHighlights[ticketId];
    }
    delete ticketHighlightTimers[key];
  }, durationMs);
}

function onFilesChanged(event: Event): void {
  const input = event.target as HTMLInputElement;
  selectedFiles.value = Array.from(input.files ?? []);
}

async function loadTickets(): Promise<void> {
  loadingTickets.value = true;
  ticketFeedback.value = '';
  try {
    const previousIds = new Set(tickets.value.map((t) => t.id));
    const data = await requestJson<Ticket[]>('/api/helpdesk/tickets', { headers: authHeaders() }, '讀取工單失敗');
    tickets.value = data.map(normalizeTicket);
    tickets.value.forEach((t) => {
      if (previousIds.size > 0 && !previousIds.has(t.id)) {
        highlightTicket(t.id, 'new', 3000);
      }
      statusDrafts[t.id] = effectiveStatus(t);
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
  if (!ticketForm.groupId) {
    ticketFeedback.value = '請選擇工單所屬群組。';
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
    formData.append('groupId', String(ticketForm.groupId));
    formData.append('priority', ticketForm.priority);
    selectedFiles.value.forEach((f) => formData.append('files', f));

    const response = await fetch('/api/helpdesk/tickets', { method: 'POST', headers: authHeaders(), body: formData });
    if (!response.ok) {
      let parsed: unknown = null;
      try { parsed = await response.json(); } catch { /* ignore */ }
      throw new Error(parseErrorMessage('送出失敗', parsed));
    }

    const created = normalizeTicket((await response.json()) as Ticket);
    tickets.value = [created, ...tickets.value].slice(0, 20);
    highlightTicket(created.id, 'new', 3000);
    statusDrafts[created.id] = effectiveStatus(created);
    replyInputs[created.id] = '';
    openTicketIds[created.id] = false;
    ticketForm.subject = '';
    ticketForm.description = '';
    selectedFiles.value = [];
    ticketFeedback.value = `工單送出成功 #${created.id}`;
    ticketFeedbackType.value = 'success';
    ticketForm.priority = 'GENERAL';
    if (!ticketForm.groupId && myGroups.value.length) {
      ticketForm.groupId = myGroups.value[0].id;
    }
  } catch (e) {
    ticketFeedback.value = e instanceof Error ? e.message : '送出失敗';
    ticketFeedbackType.value = 'error';
  } finally {
    submittingTicket.value = false;
  }
}

async function updateTicketStatus(ticket: Ticket): Promise<void> {
  if (isTicketDeleted(ticket)) return;
  const status = normalizeStatus(statusDrafts[ticket.id]);
  if (status === 'DELETED' || !EDITABLE_STATUSES.includes(status)) return;
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

async function sendReply(ticket: Ticket): Promise<void> {
  if (isTicketDeleted(ticket)) return;
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
  const normalized = normalizeTicket(updated);
  tickets.value = tickets.value.map((t) => (t.id === normalized.id ? normalized : t));
  statusDrafts[normalized.id] = effectiveStatus(normalized);
  if (openTicketIds[normalized.id] === undefined) {
    openTicketIds[normalized.id] = false;
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
      highlightTicket(item.ticketId!, 'jump', 1600);
      target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }, 80);
  }
  notificationsOpen.value = false;
}

function toggleTicket(ticketId: number): void {
  openTicketIds[ticketId] = !openTicketIds[ticketId];
}

function canDeleteTicket(ticket: Ticket): boolean {
  const member = currentMember.value;
  if (!member || isTicketDeleted(ticket)) return false;
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

function canSupervisorApprove(ticket: Ticket): boolean {
  return Boolean(
    ticket.priority === 'URGENT' &&
    isCurrentMemberSupervisorOfGroup(ticket.groupId) &&
    !ticket.supervisorApproved &&
    !isTicketDeleted(ticket)
  );
}

async function supervisorApproveTicket(ticket: Ticket): Promise<void> {
  if (!canSupervisorApprove(ticket)) return;
  itFeedback.value = '';
  itActionLoading[ticket.id] = true;
  try {
    const updated = await requestJson<Ticket>(
      `/api/helpdesk/tickets/${ticket.id}/supervisor-approve`,
      { method: 'PATCH', headers: authHeaders() },
      '主管確認失敗'
    );
    replaceTicket(updated);
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '主管確認失敗';
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

async function loadAdminGroups(): Promise<void> {
  if (!isAdmin.value) return;
  loadingGroups.value = true;
  groupsFeedback.value = '';
  try {
    adminGroups.value = await requestJson<AdminGroup[]>('/api/admin/groups', { headers: authHeaders() }, '讀取群組失敗');
    if (adminGroups.value.length && !groupAssignForm.groupId) {
      groupAssignForm.groupId = adminGroups.value[0].id;
    }
  } catch (e) {
    groupsFeedback.value = e instanceof Error ? e.message : '讀取群組失敗';
  } finally {
    loadingGroups.value = false;
  }
}

async function createAdminGroup(): Promise<void> {
  if (!isAdmin.value) return;
  const name = createGroupName.value.trim();
  if (!name) {
    groupsFeedback.value = '請輸入群組名稱。';
    return;
  }
  groupsFeedback.value = '';
  try {
    await requestJson<AdminGroup>(
      '/api/admin/groups',
      { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ name }) },
      '建立群組失敗'
    );
    createGroupName.value = '';
    await loadAdminGroups();
    await loadMyGroups();
  } catch (e) {
    groupsFeedback.value = e instanceof Error ? e.message : '建立群組失敗';
  }
}

async function addMemberToGroup(): Promise<void> {
  if (!isAdmin.value || !groupAssignForm.groupId || !groupAssignForm.memberId) return;
  groupsFeedback.value = '';
  try {
    await requestJson<AdminGroup>(
      `/api/admin/groups/${groupAssignForm.groupId}/members/${groupAssignForm.memberId}`,
      { method: 'PATCH', headers: authHeaders() },
      '加入群組失敗'
    );
    await loadAdminGroups();
    await loadMyGroups();
  } catch (e) {
    groupsFeedback.value = e instanceof Error ? e.message : '加入群組失敗';
  }
}

async function removeMemberFromGroup(groupId: number, memberId: number): Promise<void> {
  if (!isAdmin.value) return;
  groupsFeedback.value = '';
  try {
    await requestJson<AdminGroup>(
      `/api/admin/groups/${groupId}/members/${memberId}`,
      { method: 'DELETE', headers: authHeaders() },
      '移出群組失敗'
    );
    await loadAdminGroups();
    await loadMyGroups();
  } catch (e) {
    groupsFeedback.value = e instanceof Error ? e.message : '移出群組失敗';
  }
}

async function setGroupSupervisor(groupId: number, memberId: number): Promise<void> {
  if (!isAdmin.value) return;
  groupsFeedback.value = '';
  try {
    await requestJson<AdminGroup>(
      `/api/admin/groups/${groupId}/supervisor/${memberId}`,
      { method: 'PATCH', headers: authHeaders() },
      '設定主管失敗'
    );
    await loadAdminGroups();
    await loadMyGroups();
  } catch (e) {
    groupsFeedback.value = e instanceof Error ? e.message : '設定主管失敗';
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
  clearTicketHighlights();
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
        <button v-if="isAdmin" :class="{ active: dashboardTab === 'members' }" @click="dashboardTab = 'members'; loadMembers(); loadAdminGroups()">成員管理</button>
      </section>

      <section v-if="dashboardTab === 'helpdesk'" class="panel">
        <h2>新增工單</h2>
        <form class="form-grid" @submit.prevent="submitTicket">
          <label>姓名<input v-model="ticketForm.name" required /></label>
          <label>Email<input v-model="ticketForm.email" type="email" required /></label>
          <label>所屬群組
            <select v-model="ticketForm.groupId" required>
              <option :value="null" disabled>請選擇群組</option>
              <option v-for="g in myGroups" :key="g.id" :value="g.id">{{ g.name }}</option>
            </select>
          </label>
          <label>主旨<input v-model="ticketForm.subject" required /></label>
          <label>優先層級
            <select v-model="ticketForm.priority">
              <option value="GENERAL">一般</option>
              <option value="URGENT">急件（需主管確認）</option>
            </select>
          </label>
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
        <div class="ticket-filters">
          <label>
            關鍵字搜尋
            <input v-model="ticketKeyword" placeholder="工單編號 / 主旨 / 內容 / 建立人 / Email" />
          </label>
          <label>
            我的工單
            <input v-model="onlyMyTickets" type="checkbox" />
          </label>
          <label>
            依建立時間排序
            <select v-model="createdTimeSort">
              <option value="newest">新到舊</option>
              <option value="oldest">舊到新</option>
            </select>
          </label>
          <label>
            依狀態篩選
            <select v-model="statusFilter">
              <option value="ALL">全部</option>
              <option value="OPEN">OPEN</option>
              <option value="PROCEEDING">PROCEEDING</option>
              <option value="PENDING">PENDING</option>
              <option value="CLOSED">CLOSED</option>
              <option value="DELETED">DELETED</option>
            </select>
          </label>
          <small>顯示 {{ filteredTickets.length }} / {{ tickets.length }} 筆</small>
        </div>
        <p v-if="loadingTickets">讀取中...</p>
        <p v-else-if="!filteredTickets.length">沒有符合條件的工單</p>
        <ul v-else class="ticket-list">
          <li
            v-for="ticket in filteredTickets"
            :id="`ticket-${ticket.id}`"
            :key="ticket.id"
            :class="[
              'ticket-card',
              `ticket-card-${effectiveStatus(ticket).toLowerCase()}`,
              {
                expanded: openTicketIds[ticket.id],
                'new-ticket-highlight': newTicketHighlights[ticket.id],
                'jump-ticket-highlight': jumpTicketHighlights[ticket.id]
              }
            ]"
          >
            <div class="ticket-head">
              <button class="ticket-toggle" type="button" @click="toggleTicket(ticket.id)">
                <strong :class="{ 'deleted-title': isTicketDeleted(ticket) }">#{{ ticket.id }} {{ ticket.subject }}</strong>
                <small>{{ openTicketIds[ticket.id] ? '收合' : '展開' }}</small>
              </button>
              <small :class="['ticket-meta', { 'deleted-meta': isTicketDeleted(ticket) }]">
                {{ ticket.name }} · {{ new Date(ticket.createdAt).toLocaleString() }}
                <template v-if="ticket.groupName"> · 群組 {{ ticket.groupName }}</template>
              </small>
              <span :class="['priority-tag', `priority-${ticket.priority.toLowerCase()}`]">
                {{ ticket.priority === 'URGENT' ? '急件' : '一般' }}
              </span>
              <span
                v-if="ticket.priority === 'URGENT'"
                :class="['approval-tag', ticket.supervisorApproved ? 'approved' : 'pending']"
              >
                {{ ticket.supervisorApproved ? '主管已確認' : '需主管確認' }}
              </span>
              <span v-if="isTicketDeleted(ticket)" class="deleted-badge" aria-label="已刪除工單">🗑 已刪除</span>
              <button
                v-if="canSupervisorApprove(ticket)"
                class="supervisor-approve-btn"
                type="button"
                :disabled="itActionLoading[ticket.id]"
                @click="supervisorApproveTicket(ticket)"
              >
                主管確認
              </button>
              <button
                v-if="canDeleteTicket(ticket)"
                class="danger ticket-delete-btn"
                type="button"
                :disabled="itActionLoading[ticket.id] || isTicketDeleted(ticket)"
                @click="softDeleteTicket(ticket)"
              >
                {{ isTicketDeleted(ticket) ? '已刪除' : '刪除' }}
              </button>
              <small v-if="isItOrAdmin" class="status-hint">狀態</small>
              <select
                v-if="isItOrAdmin && !isTicketDeleted(ticket)"
                :class="['status-select', `status-${effectiveStatus(ticket).toLowerCase()}`]"
                :disabled="itActionLoading[ticket.id]"
                v-model="statusDrafts[ticket.id]"
                @change="updateTicketStatus(ticket)"
              >
                <option value="OPEN">OPEN</option>
                <option value="PROCEEDING">PROCEEDING</option>
                <option value="PENDING">PENDING</option>
                <option value="CLOSED">CLOSED</option>
              </select>
              <span v-else :class="['status-tag', `status-${effectiveStatus(ticket).toLowerCase()}`]">{{ displayStatus(ticket) }}</span>
            </div>
            <Transition name="ticket-expand">
              <div v-if="openTicketIds[ticket.id]" class="ticket-content">
                <p :class="{ 'deleted-content': isTicketDeleted(ticket) }">{{ ticket.description }}</p>
                <small>{{ ticket.email }}</small>
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

                <div class="status-history-box">
                  <h4>狀態歷程</h4>
                  <ul v-if="ticket.statusHistories.length" class="simple-list status-history-list">
                    <li v-for="history in ticket.statusHistories" :key="history.id">
                      <span :class="['status-tag', `status-${normalizeStatus(history.toStatus).toLowerCase()}`]">
                        {{ normalizeStatus(history.toStatus) }}
                      </span>
                      <small>
                        {{ formatStatusTransition(history) }} ·
                        {{ history.changedByRole }} {{ history.changedByName }} ({{ history.changedByEmployeeId }}) ·
                        {{ new Date(history.createdAt).toLocaleString() }}
                      </small>
                    </li>
                  </ul>
                  <small v-else>尚無狀態變更紀錄</small>
                </div>

                <div v-if="isItOrAdmin" class="it-actions">
                  <div class="row">
                    <input v-model="replyInputs[ticket.id]" placeholder="輸入回覆訊息" />
                    <button :disabled="itActionLoading[ticket.id] || isTicketDeleted(ticket)" @click="sendReply(ticket)">送出回覆</button>
                  </div>
                </div>
              </div>
            </Transition>
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

        <div class="group-admin">
          <h3>部門群組管理</h3>
          <p class="subtitle">可建立群組、指派成員加入群組，並將群組成員指定為主管。</p>
          <p v-if="groupsFeedback" class="feedback error">{{ groupsFeedback }}</p>

          <div class="row">
            <input v-model="createGroupName" placeholder="新群組名稱" />
            <button @click="createAdminGroup">建立群組</button>
          </div>

          <div class="row">
            <select v-model="groupAssignForm.groupId">
              <option :value="null" disabled>選擇群組</option>
              <option v-for="g in adminGroups" :key="g.id" :value="g.id">{{ g.name }}</option>
            </select>
            <select v-model="groupAssignForm.memberId">
              <option :value="null" disabled>選擇成員</option>
              <option v-for="m in members" :key="m.id" :value="m.id">{{ m.employeeId }} {{ m.name }}</option>
            </select>
            <button @click="addMemberToGroup">加入群組</button>
          </div>

          <p v-if="loadingGroups">讀取群組中...</p>
          <ul v-else class="simple-list group-list">
            <li v-for="g in adminGroups" :key="g.id" class="group-card">
              <div class="group-head">
                <strong>{{ g.name }}</strong>
                <small>建立於 {{ new Date(g.createdAt).toLocaleString() }}</small>
              </div>
              <ul class="simple-list">
                <li v-for="gm in g.members" :key="`${g.id}-${gm.memberId}`" class="group-member-row">
                  <span>
                    {{ gm.employeeId }} {{ gm.name }} ({{ gm.role }})
                    <strong v-if="gm.supervisor" class="group-supervisor-chip">主管</strong>
                  </span>
                  <div class="row">
                    <button v-if="!gm.supervisor" @click="setGroupSupervisor(g.id, gm.memberId)">設為主管</button>
                    <button class="danger" @click="removeMemberFromGroup(g.id, gm.memberId)">移出群組</button>
                  </div>
                </li>
              </ul>
            </li>
          </ul>
        </div>
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
