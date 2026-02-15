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

type HelpdeskCategory = {
  id: number;
  name: string;
  createdAt: string;
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
  categoryId: number | null;
  categoryName: string | null;
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

type AuditLogItem = {
  id: number;
  actorMemberId: number | null;
  actorEmployeeId: string;
  actorName: string;
  actorRole: string;
  action: string;
  entityType: string;
  entityId: number | null;
  beforeJson: string | null;
  afterJson: string | null;
  metadataJson: string | null;
  createdAt: string;
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
const dashboardTab = ref<'helpdesk' | 'itdesk' | 'archive' | 'members'>('helpdesk');

const ticketForm = reactive<{
  name: string;
  email: string;
  subject: string;
  description: string;
  priority: TicketPriority;
  groupId: number | null;
  categoryId: number | null;
}>({
  name: '',
  email: '',
  subject: '',
  description: '',
  priority: 'GENERAL',
  groupId: null,
  categoryId: null
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
const statusFilter = ref<'ALL' | 'OPEN' | 'PROCEEDING' | 'PENDING'>('ALL');
const archiveStatusFilter = ref<'ALL' | 'CLOSED' | 'DELETED'>('ALL');

const members = ref<Member[]>([]);
const loadingMembers = ref(false);
const membersFeedback = ref('');
const myGroups = ref<MyGroup[]>([]);
const helpdeskCategories = ref<HelpdeskCategory[]>([]);
const adminHelpdeskCategories = ref<HelpdeskCategory[]>([]);
const categoryFeedback = ref('');
const createCategoryName = ref('');
const adminGroups = ref<AdminGroup[]>([]);
const loadingGroups = ref(false);
const groupsFeedback = ref('');
const createGroupName = ref('');
const groupAssignForm = reactive<{ groupId: number | null; memberId: number | null }>({ groupId: null, memberId: null });
const auditLogs = ref<AuditLogItem[]>([]);
const loadingAuditLogs = ref(false);
const exportingAuditLogs = ref(false);
const purgingAuditLogs = ref(false);
const auditLogsFeedback = ref('');
const auditCleanupFeedback = ref('');
const auditFilters = reactive<{
  action: string;
  entityType: string;
  entityId: string;
  actorMemberId: string;
  from: string;
  to: string;
  limit: number;
}>({
  action: '',
  entityType: '',
  entityId: '',
  actorMemberId: '',
  from: '',
  to: '',
  limit: 100
});
const auditCleanupDays = ref(180);

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
let lightboxObjectUrl: string | null = null;
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

const baseFilteredTickets = computed(() => {
  const keyword = ticketKeyword.value.trim().toLowerCase();
  const memberId = currentMember.value?.id ?? null;

  return [...tickets.value]
    .filter((ticket) => {
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
        ticket.categoryName ?? '',
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

const filteredActiveTickets = computed(() =>
  baseFilteredTickets.value.filter((ticket) => {
    const status = effectiveStatus(ticket);
    if (status === 'CLOSED' || status === 'DELETED') return false;
    if (statusFilter.value === 'ALL') return true;
    return status === statusFilter.value;
  })
);

const filteredArchivedTickets = computed(() =>
  baseFilteredTickets.value.filter((ticket) => {
    const status = effectiveStatus(ticket);
    if (status !== 'CLOSED' && status !== 'DELETED') return false;
    if (archiveStatusFilter.value === 'ALL') return true;
    return status === archiveStatusFilter.value;
  })
);

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
    categoryId: ticket.categoryId ?? null,
    categoryName: ticket.categoryName ?? null,
    statusHistories: Array.isArray(ticket.statusHistories) ? ticket.statusHistories : []
  };
}

function isCurrentMemberSupervisorOfGroup(groupId: number | null): boolean {
  if (!groupId) return false;
  return myGroups.value.some((g) => g.id === groupId && g.supervisor);
}

function formatStatusTransition(history: TicketStatusHistory): string {
  const toStatus = normalizeStatus(history.toStatus);
  if (history.fromStatus && normalizeStatus(history.fromStatus) === toStatus) {
    return `主管已確認（${toStatus}）`;
  }
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

function isImageAttachment(attachment: Attachment): boolean {
  return attachment.contentType.startsWith('image/');
}

function extractFilenameFromDisposition(disposition: string | null): string | null {
  if (!disposition) return null;
  const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1]);
    } catch {
      // ignore decode issue
    }
  }
  const plainMatch = disposition.match(/filename="([^"]+)"/i);
  return plainMatch?.[1] ?? null;
}

async function fetchAttachmentBlob(ticketId: number, attachmentId: number, action: 'view' | 'download'): Promise<{
  blob: Blob;
  filename: string;
}> {
  const response = await fetch(`/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/${action}`, {
    headers: authHeaders()
  });
  if (!response.ok) {
    let parsed: unknown = null;
    try {
      parsed = await response.json();
    } catch {
      // ignore
    }
    throw new Error(parseErrorMessage('讀取附件失敗', parsed));
  }
  const blob = await response.blob();
  const filename = extractFilenameFromDisposition(response.headers.get('Content-Disposition')) ?? `attachment-${attachmentId}`;
  return { blob, filename };
}

async function openImageLightbox(ticketId: number, attachment: Attachment): Promise<void> {
  try {
    const { blob } = await fetchAttachmentBlob(ticketId, attachment.id, 'view');
    if (lightboxObjectUrl) {
      URL.revokeObjectURL(lightboxObjectUrl);
    }
    lightboxObjectUrl = URL.createObjectURL(blob);
    lightboxSrc.value = lightboxObjectUrl;
    lightboxTitle.value = attachment.originalFilename;
    lightboxOpen.value = true;
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '讀取附件失敗';
  }
}

async function downloadAttachment(ticketId: number, attachment: Attachment): Promise<void> {
  try {
    const { blob, filename } = await fetchAttachmentBlob(ticketId, attachment.id, 'download');
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename || attachment.originalFilename;
    anchor.click();
    URL.revokeObjectURL(objectUrl);
  } catch (e) {
    itFeedback.value = e instanceof Error ? e.message : '下載附件失敗';
  }
}

function closeLightbox(): void {
  if (lightboxObjectUrl) {
    URL.revokeObjectURL(lightboxObjectUrl);
    lightboxObjectUrl = null;
  }
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
  sessionStorage.setItem(TOKEN_KEY, newToken);
  ticketForm.name = member.name;
  ticketForm.email = member.email;
  ticketForm.groupId = null;
  ticketForm.categoryId = null;
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
  const saved = sessionStorage.getItem(TOKEN_KEY);
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
  await loadHelpdeskCategories();
  await loadTickets();
  await loadNotifications();
  startNotificationPolling();
  if (isAdmin.value) {
    dashboardTab.value = 'members';
    await loadMembers();
    await loadAdminGroups();
    await loadAdminHelpdeskCategories();
    await loadAuditLogs();
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
  closeLightbox();
  token.value = '';
  currentMember.value = null;
  sessionStorage.removeItem(TOKEN_KEY);
  tickets.value = [];
  members.value = [];
  myGroups.value = [];
  helpdeskCategories.value = [];
  adminHelpdeskCategories.value = [];
  createCategoryName.value = '';
  categoryFeedback.value = '';
  adminGroups.value = [];
  createGroupName.value = '';
  groupAssignForm.groupId = null;
  groupAssignForm.memberId = null;
  auditLogs.value = [];
  auditLogsFeedback.value = '';
  notifications.value = [];
  unreadCount.value = 0;
  notificationsOpen.value = false;
  dashboardTab.value = 'helpdesk';
  ticketForm.name = '';
  ticketForm.email = '';
  ticketForm.groupId = null;
  ticketForm.categoryId = null;
  ticketForm.priority = 'GENERAL';
}

async function loadMyGroups(): Promise<void> {
  if (!token.value) return;
  try {
    myGroups.value = await requestJson<MyGroup[]>('/api/groups/mine', { headers: authHeaders() }, '讀取群組失敗');
    if (!myGroups.value.length) {
      ticketForm.groupId = null;
      return;
    }
    const currentGroupStillValid = myGroups.value.some((g) => g.id === ticketForm.groupId);
    if (!currentGroupStillValid) {
      ticketForm.groupId = myGroups.value[0].id;
    }
  } catch {
    myGroups.value = [];
    ticketForm.groupId = null;
  }
}

async function loadHelpdeskCategories(): Promise<void> {
  if (!token.value) return;
  try {
    helpdeskCategories.value = await requestJson<HelpdeskCategory[]>(
      '/api/helpdesk/categories',
      { headers: authHeaders() },
      '讀取分類失敗'
    );
    if (!helpdeskCategories.value.length) {
      ticketForm.categoryId = null;
      return;
    }
    const currentCategoryStillValid = helpdeskCategories.value.some((c) => c.id === ticketForm.categoryId);
    if (!currentCategoryStillValid) {
      ticketForm.categoryId = helpdeskCategories.value[0].id;
    }
  } catch {
    helpdeskCategories.value = [];
    ticketForm.categoryId = null;
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
  if (!ticketForm.categoryId) {
    ticketFeedback.value = '請選擇工單分類。';
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
    formData.append('categoryId', String(ticketForm.categoryId));
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
    if (!ticketForm.categoryId && helpdeskCategories.value.length) {
      ticketForm.categoryId = helpdeskCategories.value[0].id;
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
    await loadTickets();
    const targetTicket = tickets.value.find((t) => t.id === item.ticketId);
    const archived = targetTicket ? ['CLOSED', 'DELETED'].includes(effectiveStatus(targetTicket)) : false;
    dashboardTab.value = archived ? 'archive' : isItOrAdmin.value ? 'itdesk' : 'helpdesk';
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

async function loadAdminHelpdeskCategories(): Promise<void> {
  if (!isAdmin.value) return;
  categoryFeedback.value = '';
  try {
    adminHelpdeskCategories.value = await requestJson<HelpdeskCategory[]>(
      '/api/admin/helpdesk-categories',
      { headers: authHeaders() },
      '讀取分類失敗'
    );
  } catch (e) {
    categoryFeedback.value = e instanceof Error ? e.message : '讀取分類失敗';
  }
}

async function createHelpdeskCategory(): Promise<void> {
  if (!isAdmin.value) return;
  const name = createCategoryName.value.trim();
  if (!name) {
    categoryFeedback.value = '請輸入分類名稱。';
    return;
  }
  categoryFeedback.value = '';
  try {
    await requestJson<HelpdeskCategory>(
      '/api/admin/helpdesk-categories',
      {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', ...authHeaders() },
        body: JSON.stringify({ name })
      },
      '建立分類失敗'
    );
    createCategoryName.value = '';
    await loadAdminHelpdeskCategories();
    await loadHelpdeskCategories();
  } catch (e) {
    categoryFeedback.value = e instanceof Error ? e.message : '建立分類失敗';
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

function formatJsonPreview(raw: string | null): string {
  if (!raw) return '-';
  try {
    const parsed = JSON.parse(raw);
    return JSON.stringify(parsed, null, 2);
  } catch {
    return raw;
  }
}

function trimmedText(value: string, max = 140): string {
  const text = value.trim();
  if (!text) return '';
  return text.length <= max ? text : `${text.slice(0, max)}...`;
}

async function loadAuditLogs(): Promise<void> {
  if (!isAdmin.value) return;
  loadingAuditLogs.value = true;
  auditLogsFeedback.value = '';
  try {
    const params = new URLSearchParams();
    const action = trimmedText(auditFilters.action, 80).toUpperCase();
    const entityType = trimmedText(auditFilters.entityType, 80).toUpperCase();
    const entityId = trimmedText(auditFilters.entityId, 40);
    const actorMemberId = trimmedText(auditFilters.actorMemberId, 40);
    const from = trimmedText(auditFilters.from, 40);
    const to = trimmedText(auditFilters.to, 40);
    const safeLimit = Math.min(Math.max(Number(auditFilters.limit) || 100, 1), 500);

    if (action) params.set('action', action);
    if (entityType) params.set('entityType', entityType);
    if (entityId) params.set('entityId', entityId);
    if (actorMemberId) params.set('actorMemberId', actorMemberId);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    params.set('limit', String(safeLimit));

    const query = params.toString();
    const url = query ? `/api/admin/audit-logs?${query}` : '/api/admin/audit-logs';
    auditLogs.value = await requestJson<AuditLogItem[]>(url, { headers: authHeaders() }, '讀取操作紀錄失敗');
  } catch (e) {
    auditLogsFeedback.value = e instanceof Error ? e.message : '讀取操作紀錄失敗';
  } finally {
    loadingAuditLogs.value = false;
  }
}

async function exportAuditLogsCsv(): Promise<void> {
  if (!isAdmin.value) return;
  exportingAuditLogs.value = true;
  auditLogsFeedback.value = '';
  try {
    const params = new URLSearchParams();
    const action = trimmedText(auditFilters.action, 80).toUpperCase();
    const entityType = trimmedText(auditFilters.entityType, 80).toUpperCase();
    const entityId = trimmedText(auditFilters.entityId, 40);
    const actorMemberId = trimmedText(auditFilters.actorMemberId, 40);
    const from = trimmedText(auditFilters.from, 40);
    const to = trimmedText(auditFilters.to, 40);
    const safeLimit = Math.min(Math.max(Number(auditFilters.limit) || 500, 1), 500);

    if (action) params.set('action', action);
    if (entityType) params.set('entityType', entityType);
    if (entityId) params.set('entityId', entityId);
    if (actorMemberId) params.set('actorMemberId', actorMemberId);
    if (from) params.set('from', from);
    if (to) params.set('to', to);
    params.set('limit', String(safeLimit));

    const query = params.toString();
    const url = query ? `/api/admin/audit-logs/export.csv?${query}` : '/api/admin/audit-logs/export.csv';
    const response = await fetch(url, { headers: authHeaders() });
    if (!response.ok) {
      let parsed: unknown = null;
      try {
        parsed = await response.json();
      } catch {
        // ignore
      }
      throw new Error(parseErrorMessage('匯出 CSV 失敗', parsed));
    }

    const blob = await response.blob();
    const disposition = response.headers.get('Content-Disposition') ?? '';
    const matched = disposition.match(/filename="([^"]+)"/i);
    const filename = matched?.[1] ?? 'audit-logs.csv';
    const objectUrl = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = objectUrl;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(objectUrl);
  } catch (e) {
    auditLogsFeedback.value = e instanceof Error ? e.message : '匯出 CSV 失敗';
  } finally {
    exportingAuditLogs.value = false;
  }
}

async function purgeAuditLogs(): Promise<void> {
  if (!isAdmin.value) return;
  purgingAuditLogs.value = true;
  auditCleanupFeedback.value = '';
  try {
    const safeDays = Math.min(Math.max(Number(auditCleanupDays.value) || 180, 1), 3650);
    const response = await requestJson<{
      configuredRetentionDays: number;
      requestedDays: number | null;
      cutoff: string;
      candidateCount: number;
      deletedCount: number;
    }>(
      `/api/admin/audit-logs/cleanup?days=${safeDays}`,
      { method: 'POST', headers: authHeaders() },
      '清理操作紀錄失敗'
    );
    auditCleanupFeedback.value = `清理完成：候選 ${response.candidateCount} 筆，刪除 ${response.deletedCount} 筆（cutoff: ${new Date(response.cutoff).toLocaleString()}）`;
    await loadAuditLogs();
  } catch (e) {
    auditCleanupFeedback.value = e instanceof Error ? e.message : '清理操作紀錄失敗';
  } finally {
    purgingAuditLogs.value = false;
  }
}

onMounted(async () => {
  await restoreSession();
});

onBeforeUnmount(() => {
  closeLightbox();
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
        <button :class="{ active: dashboardTab === 'archive' }" @click="dashboardTab = 'archive'">封存</button>
        <button
          v-if="isAdmin"
          :class="{ active: dashboardTab === 'members' }"
          @click="dashboardTab = 'members'; loadMembers(); loadAdminGroups(); loadAdminHelpdeskCategories(); loadAuditLogs()"
        >
          成員管理
        </button>
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
          <label>工單分類
            <select v-model="ticketForm.categoryId" required>
              <option :value="null" disabled>請選擇分類</option>
              <option v-for="c in helpdeskCategories" :key="c.id" :value="c.id">{{ c.name }}</option>
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
          <h2>進行中工單</h2>
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
            </select>
          </label>
          <small>顯示 {{ filteredActiveTickets.length }} / {{ tickets.length }} 筆</small>
        </div>
        <p v-if="loadingTickets">讀取中...</p>
        <p v-else-if="!filteredActiveTickets.length">沒有符合條件的工單</p>
        <ul v-else class="ticket-list">
          <li
            v-for="ticket in filteredActiveTickets"
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
                <template v-if="ticket.categoryName"> · 分類 {{ ticket.categoryName }}</template>
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
                      <button class="link-button" type="button" @click="downloadAttachment(ticket.id, att)">下載 {{ att.originalFilename }}</button>
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

      <section v-if="dashboardTab === 'archive'" class="panel">
        <div class="ticket-list-top">
          <h2>封存工單</h2>
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
            <select v-model="archiveStatusFilter">
              <option value="ALL">全部</option>
              <option value="CLOSED">CLOSED</option>
              <option value="DELETED">DELETED</option>
            </select>
          </label>
          <small>顯示 {{ filteredArchivedTickets.length }} / {{ tickets.length }} 筆</small>
        </div>
        <p v-if="loadingTickets">讀取中...</p>
        <p v-else-if="!filteredArchivedTickets.length">目前沒有封存工單</p>
        <ul v-else class="ticket-list">
          <li
            v-for="ticket in filteredArchivedTickets"
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
                <template v-if="ticket.categoryName"> · 分類 {{ ticket.categoryName }}</template>
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
              <span :class="['status-tag', `status-${effectiveStatus(ticket).toLowerCase()}`]">{{ displayStatus(ticket) }}</span>
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
                      <button class="link-button" type="button" @click="downloadAttachment(ticket.id, att)">下載 {{ att.originalFilename }}</button>
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
              </div>
            </Transition>
          </li>
        </ul>
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

        <div class="group-admin">
          <h3>工單分類管理</h3>
          <p class="subtitle">ADMIN 可新增分類，使用者提交工單時需選擇分類。</p>
          <p v-if="categoryFeedback" class="feedback error">{{ categoryFeedback }}</p>

          <div class="row">
            <input v-model="createCategoryName" placeholder="新分類名稱" />
            <button @click="createHelpdeskCategory">建立分類</button>
          </div>

          <ul class="simple-list">
            <li v-for="c in adminHelpdeskCategories" :key="c.id">
              <strong>{{ c.name }}</strong>
              <small> · 建立於 {{ new Date(c.createdAt).toLocaleString() }}</small>
            </li>
          </ul>
        </div>

        <div class="audit-admin">
          <div class="audit-head">
            <h3>操作紀錄（Audit Log）</h3>
            <div class="row">
              <button type="button" @click="loadAuditLogs">重新整理</button>
              <button type="button" :disabled="exportingAuditLogs" @click="exportAuditLogsCsv">
                {{ exportingAuditLogs ? '匯出中...' : '匯出 CSV' }}
              </button>
            </div>
          </div>
          <p class="subtitle">僅限 ADMIN 查詢。可依動作、實體、時間與操作者過濾。</p>
          <p v-if="auditLogsFeedback" class="feedback error">{{ auditLogsFeedback }}</p>
          <p v-if="auditCleanupFeedback" class="feedback">{{ auditCleanupFeedback }}</p>

          <div class="row">
            <label>清理幾天前的紀錄
              <input v-model.number="auditCleanupDays" type="number" min="1" max="3650" />
            </label>
            <button type="button" class="danger" :disabled="purgingAuditLogs" @click="purgeAuditLogs">
              {{ purgingAuditLogs ? '清理中...' : '執行清理' }}
            </button>
          </div>

          <div class="audit-filters">
            <label>動作（action）
              <input v-model="auditFilters.action" placeholder="例如 TICKET_STATUS_UPDATED" />
            </label>
            <label>實體類型（entityType）
              <input v-model="auditFilters.entityType" placeholder="例如 TICKET / GROUP" />
            </label>
            <label>實體 ID（entityId）
              <input v-model="auditFilters.entityId" placeholder="例如 123" />
            </label>
            <label>操作者 ID（actorMemberId）
              <input v-model="auditFilters.actorMemberId" placeholder="例如 5" />
            </label>
            <label>起始時間（from）
              <input v-model="auditFilters.from" placeholder="2026-02-13T00:00:00Z" />
            </label>
            <label>結束時間（to）
              <input v-model="auditFilters.to" placeholder="2026-02-13T23:59:59Z" />
            </label>
            <label>筆數上限（1-500）
              <input v-model.number="auditFilters.limit" type="number" min="1" max="500" />
            </label>
            <button type="button" @click="loadAuditLogs">查詢</button>
          </div>

          <p v-if="loadingAuditLogs">讀取操作紀錄中...</p>
          <p v-else-if="!auditLogs.length">目前沒有符合條件的操作紀錄</p>
          <ul v-else class="simple-list audit-log-list">
            <li v-for="log in auditLogs" :key="log.id" class="audit-log-card">
              <div class="audit-log-title">
                <strong>#{{ log.id }} {{ log.action }}</strong>
                <small>{{ new Date(log.createdAt).toLocaleString() }}</small>
              </div>
              <small>
                {{ log.actorRole }} {{ log.actorName }} ({{ log.actorEmployeeId }})
                · entity: {{ log.entityType }} #{{ log.entityId ?? '-' }}
              </small>
              <details>
                <summary>前後資料與 metadata</summary>
                <div class="audit-log-json-grid">
                  <div>
                    <h4>Before</h4>
                    <pre>{{ formatJsonPreview(log.beforeJson) }}</pre>
                  </div>
                  <div>
                    <h4>After</h4>
                    <pre>{{ formatJsonPreview(log.afterJson) }}</pre>
                  </div>
                  <div>
                    <h4>Metadata</h4>
                    <pre>{{ formatJsonPreview(log.metadataJson) }}</pre>
                  </div>
                </div>
              </details>
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
