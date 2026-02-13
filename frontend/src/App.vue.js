import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
const TOKEN_KEY = 'helpdesk_auth_token';
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const authMode = ref('login');
const registerStep = ref(1);
const authLoading = ref(false);
const authError = ref('');
const loginForm = reactive({ employeeId: '', password: '' });
const registerForm = reactive({ employeeId: '', name: '', email: '', password: '' });
const token = ref('');
const currentMember = ref(null);
const dashboardTab = ref('helpdesk');
const ticketForm = reactive({
    name: '',
    email: '',
    subject: '',
    description: '',
    priority: 'GENERAL',
    groupId: null
});
const selectedFiles = ref([]);
const submittingTicket = ref(false);
const ticketFeedback = ref('');
const ticketFeedbackType = ref('');
const tickets = ref([]);
const loadingTickets = ref(false);
const ticketKeyword = ref('');
const onlyMyTickets = ref(false);
const createdTimeSort = ref('newest');
const statusFilter = ref('ALL');
const members = ref([]);
const loadingMembers = ref(false);
const membersFeedback = ref('');
const myGroups = ref([]);
const adminGroups = ref([]);
const loadingGroups = ref(false);
const groupsFeedback = ref('');
const createGroupName = ref('');
const groupAssignForm = reactive({ groupId: null, memberId: null });
const replyInputs = reactive({});
const statusDrafts = reactive({});
const itActionLoading = reactive({});
const itFeedback = ref('');
const openTicketIds = reactive({});
const newTicketHighlights = reactive({});
const jumpTicketHighlights = reactive({});
const ticketHighlightTimers = reactive({});
const lightboxOpen = ref(false);
const lightboxSrc = ref('');
const lightboxTitle = ref('');
const notifications = ref([]);
const unreadCount = ref(0);
const notificationsOpen = ref(false);
const notificationLoading = ref(false);
const notificationFeedback = ref('');
let notificationTimer = null;
const isAuthenticated = computed(() => Boolean(token.value));
const isAdmin = computed(() => currentMember.value?.role === 'ADMIN');
const isItOrAdmin = computed(() => currentMember.value?.role === 'IT' || currentMember.value?.role === 'ADMIN');
const EDITABLE_STATUSES = ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'];
function normalizeStatus(value) {
    const normalized = String(value ?? '').trim().toUpperCase();
    if (normalized === 'DELETED')
        return 'DELETED';
    if (normalized === 'OPEN')
        return 'OPEN';
    if (normalized === 'PROCEEDING')
        return 'PROCEEDING';
    if (normalized === 'PENDING')
        return 'PENDING';
    if (normalized === 'CLOSED')
        return 'CLOSED';
    return 'OPEN';
}
function normalizePriority(value) {
    return String(value ?? '').trim().toUpperCase() === 'URGENT' ? 'URGENT' : 'GENERAL';
}
function effectiveStatus(ticket) {
    if (ticket.deleted || ticket.deletedAt)
        return 'DELETED';
    return normalizeStatus(ticket.status);
}
function isTicketDeleted(ticket) {
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
        if (!keyword)
            return true;
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
function authHeaders() {
    return { Authorization: `Bearer ${token.value}` };
}
function formatSize(bytes) {
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
}
function displayStatus(ticket) {
    return effectiveStatus(ticket);
}
function normalizeTicket(ticket) {
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
function isCurrentMemberSupervisorOfGroup(groupId) {
    if (!groupId)
        return false;
    return myGroups.value.some((g) => g.id === groupId && g.supervisor);
}
function formatStatusTransition(history) {
    const toStatus = normalizeStatus(history.toStatus);
    if (!history.fromStatus) {
        return `初始化為 ${toStatus}`;
    }
    return `${normalizeStatus(history.fromStatus)} → ${toStatus}`;
}
function isToday(isoDateTime) {
    const target = new Date(isoDateTime);
    const now = new Date();
    return (target.getFullYear() === now.getFullYear() &&
        target.getMonth() === now.getMonth() &&
        target.getDate() === now.getDate());
}
function parseErrorMessage(fallback, body) {
    if (typeof body === 'object' && body !== null && 'message' in body) {
        const v = body.message;
        if (typeof v === 'string' && v)
            return v;
    }
    return fallback;
}
async function requestJson(url, init, fallback) {
    const response = await fetch(url, init);
    if (!response.ok) {
        let parsed = null;
        try {
            parsed = await response.json();
        }
        catch {
            // ignore
        }
        throw new Error(parseErrorMessage(fallback, parsed));
    }
    return (await response.json());
}
function attachmentViewUrl(ticketId, attachmentId) {
    return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/view?token=${encodeURIComponent(token.value)}`;
}
function attachmentDownloadUrl(ticketId, attachmentId) {
    return `/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/download?token=${encodeURIComponent(token.value)}`;
}
function isImageAttachment(attachment) {
    return attachment.contentType.startsWith('image/');
}
function openImageLightbox(ticketId, attachment) {
    lightboxSrc.value = attachmentViewUrl(ticketId, attachment.id);
    lightboxTitle.value = attachment.originalFilename;
    lightboxOpen.value = true;
}
function closeLightbox() {
    lightboxOpen.value = false;
    lightboxSrc.value = '';
    lightboxTitle.value = '';
}
function nextRegisterStep() {
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
function prevRegisterStep() {
    authError.value = '';
    registerStep.value = Math.max(1, registerStep.value - 1);
}
function applyAuth(newToken, member) {
    token.value = newToken;
    currentMember.value = member;
    localStorage.setItem(TOKEN_KEY, newToken);
    ticketForm.name = member.name;
    ticketForm.email = member.email;
}
async function login() {
    authError.value = '';
    authLoading.value = true;
    try {
        const result = await requestJson('/api/auth/login', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(loginForm) }, '登入失敗');
        applyAuth(result.token, result.member);
        await afterLoginLoad();
    }
    catch (e) {
        authError.value = e instanceof Error ? e.message : '登入失敗';
    }
    finally {
        authLoading.value = false;
    }
}
async function register() {
    authError.value = '';
    authLoading.value = true;
    try {
        const result = await requestJson('/api/auth/register', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(registerForm) }, '註冊失敗');
        applyAuth(result.token, result.member);
        await afterLoginLoad();
    }
    catch (e) {
        authError.value = e instanceof Error ? e.message : '註冊失敗';
    }
    finally {
        authLoading.value = false;
    }
}
async function restoreSession() {
    const saved = localStorage.getItem(TOKEN_KEY);
    if (!saved)
        return;
    token.value = saved;
    try {
        const me = await requestJson('/api/auth/me', { headers: authHeaders() }, '登入已失效');
        currentMember.value = me;
        ticketForm.name = me.name;
        ticketForm.email = me.email;
        await afterLoginLoad();
    }
    catch {
        clearSession();
    }
}
async function afterLoginLoad() {
    await loadMyGroups();
    await loadTickets();
    await loadNotifications();
    startNotificationPolling();
    if (isAdmin.value) {
        dashboardTab.value = 'members';
        await loadMembers();
        await loadAdminGroups();
    }
    else if (isItOrAdmin.value) {
        dashboardTab.value = 'itdesk';
    }
    else {
        dashboardTab.value = 'helpdesk';
    }
}
async function logout() {
    try {
        if (token.value) {
            await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders() });
        }
    }
    finally {
        clearSession();
    }
}
function clearSession() {
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
async function loadMyGroups() {
    if (!token.value)
        return;
    try {
        myGroups.value = await requestJson('/api/groups/mine', { headers: authHeaders() }, '讀取群組失敗');
        if (myGroups.value.length && !ticketForm.groupId) {
            ticketForm.groupId = myGroups.value[0].id;
        }
    }
    catch {
        myGroups.value = [];
    }
}
function clearTicketHighlights() {
    Object.values(ticketHighlightTimers).forEach((timerId) => window.clearTimeout(timerId));
    Object.keys(ticketHighlightTimers).forEach((k) => delete ticketHighlightTimers[k]);
    Object.keys(newTicketHighlights).forEach((k) => delete newTicketHighlights[Number(k)]);
    Object.keys(jumpTicketHighlights).forEach((k) => delete jumpTicketHighlights[Number(k)]);
}
function highlightTicket(ticketId, kind, durationMs) {
    const key = `${kind}-${ticketId}`;
    const previousTimer = ticketHighlightTimers[key];
    if (previousTimer) {
        window.clearTimeout(previousTimer);
    }
    if (kind === 'new') {
        newTicketHighlights[ticketId] = true;
    }
    else {
        jumpTicketHighlights[ticketId] = true;
    }
    ticketHighlightTimers[key] = window.setTimeout(() => {
        if (kind === 'new') {
            delete newTicketHighlights[ticketId];
        }
        else {
            delete jumpTicketHighlights[ticketId];
        }
        delete ticketHighlightTimers[key];
    }, durationMs);
}
function onFilesChanged(event) {
    const input = event.target;
    selectedFiles.value = Array.from(input.files ?? []);
}
async function loadTickets() {
    loadingTickets.value = true;
    ticketFeedback.value = '';
    try {
        const previousIds = new Set(tickets.value.map((t) => t.id));
        const data = await requestJson('/api/helpdesk/tickets', { headers: authHeaders() }, '讀取工單失敗');
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
    }
    catch (e) {
        ticketFeedback.value = e instanceof Error ? e.message : '讀取工單失敗';
        ticketFeedbackType.value = 'error';
    }
    finally {
        loadingTickets.value = false;
    }
}
async function submitTicket() {
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
            let parsed = null;
            try {
                parsed = await response.json();
            }
            catch { /* ignore */ }
            throw new Error(parseErrorMessage('送出失敗', parsed));
        }
        const created = normalizeTicket((await response.json()));
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
    }
    catch (e) {
        ticketFeedback.value = e instanceof Error ? e.message : '送出失敗';
        ticketFeedbackType.value = 'error';
    }
    finally {
        submittingTicket.value = false;
    }
}
async function updateTicketStatus(ticket) {
    if (isTicketDeleted(ticket))
        return;
    const status = normalizeStatus(statusDrafts[ticket.id]);
    if (status === 'DELETED' || !EDITABLE_STATUSES.includes(status))
        return;
    itActionLoading[ticket.id] = true;
    itFeedback.value = '';
    try {
        const updated = await requestJson(`/api/helpdesk/tickets/${ticket.id}/status`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', ...authHeaders() },
            body: JSON.stringify({ status })
        }, '更新狀態失敗');
        replaceTicket(updated);
    }
    catch (e) {
        itFeedback.value = e instanceof Error ? e.message : '更新狀態失敗';
    }
    finally {
        itActionLoading[ticket.id] = false;
    }
}
async function sendReply(ticket) {
    if (isTicketDeleted(ticket))
        return;
    const content = (replyInputs[ticket.id] ?? '').trim();
    if (!content)
        return;
    itActionLoading[ticket.id] = true;
    itFeedback.value = '';
    try {
        const updated = await requestJson(`/api/helpdesk/tickets/${ticket.id}/messages`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json', ...authHeaders() },
            body: JSON.stringify({ content })
        }, '回覆失敗');
        replyInputs[ticket.id] = '';
        replaceTicket(updated);
    }
    catch (e) {
        itFeedback.value = e instanceof Error ? e.message : '回覆失敗';
    }
    finally {
        itActionLoading[ticket.id] = false;
    }
}
function replaceTicket(updated) {
    const normalized = normalizeTicket(updated);
    tickets.value = tickets.value.map((t) => (t.id === normalized.id ? normalized : t));
    statusDrafts[normalized.id] = effectiveStatus(normalized);
    if (openTicketIds[normalized.id] === undefined) {
        openTicketIds[normalized.id] = false;
    }
}
async function loadNotifications(silent = false) {
    if (!token.value)
        return;
    if (!silent)
        notificationLoading.value = true;
    notificationFeedback.value = '';
    try {
        const data = await requestJson('/api/notifications', { headers: authHeaders() }, '讀取通知失敗');
        notifications.value = data.notifications;
        unreadCount.value = data.unreadCount;
    }
    catch (e) {
        if (!silent) {
            notificationFeedback.value = e instanceof Error ? e.message : '讀取通知失敗';
        }
    }
    finally {
        if (!silent)
            notificationLoading.value = false;
    }
}
async function markNotificationRead(notificationId) {
    try {
        await fetch(`/api/notifications/${notificationId}/read`, { method: 'PATCH', headers: authHeaders() });
        const target = notifications.value.find((n) => n.id === notificationId);
        if (target && !target.read) {
            target.read = true;
            unreadCount.value = Math.max(0, unreadCount.value - 1);
        }
    }
    catch {
        // ignore optimistic update failure
    }
}
async function markAllNotificationsRead() {
    notificationFeedback.value = '';
    try {
        await requestJson('/api/notifications/read-all', { method: 'PATCH', headers: authHeaders() }, '全部已讀失敗');
        notifications.value = notifications.value.map((n) => ({ ...n, read: true }));
        unreadCount.value = 0;
    }
    catch (e) {
        notificationFeedback.value = e instanceof Error ? e.message : '全部已讀失敗';
    }
}
function startNotificationPolling() {
    stopNotificationPolling();
    notificationTimer = window.setInterval(() => {
        void loadNotifications(true);
    }, 15000);
}
function stopNotificationPolling() {
    if (notificationTimer !== null) {
        window.clearInterval(notificationTimer);
        notificationTimer = null;
    }
}
async function openNotification(item) {
    if (!item.read) {
        await markNotificationRead(item.id);
    }
    if (item.ticketId) {
        dashboardTab.value = isItOrAdmin.value ? 'itdesk' : 'helpdesk';
        await loadTickets();
        openTicketIds[item.ticketId] = true;
        window.setTimeout(() => {
            const target = document.getElementById(`ticket-${item.ticketId}`);
            highlightTicket(item.ticketId, 'jump', 1600);
            target?.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }, 80);
    }
    notificationsOpen.value = false;
}
function toggleTicket(ticketId) {
    openTicketIds[ticketId] = !openTicketIds[ticketId];
}
function canDeleteTicket(ticket) {
    const member = currentMember.value;
    if (!member || isTicketDeleted(ticket))
        return false;
    if (member.role === 'ADMIN' || member.role === 'IT')
        return true;
    return ticket.createdByMemberId === member.id;
}
async function softDeleteTicket(ticket) {
    if (!canDeleteTicket(ticket))
        return;
    if (!confirm(`確認將工單 #${ticket.id} 標記為刪除？`))
        return;
    itFeedback.value = '';
    itActionLoading[ticket.id] = true;
    try {
        const updated = await requestJson(`/api/helpdesk/tickets/${ticket.id}/delete`, { method: 'PATCH', headers: authHeaders() }, '刪除工單失敗');
        replaceTicket(updated);
    }
    catch (e) {
        itFeedback.value = e instanceof Error ? e.message : '刪除工單失敗';
    }
    finally {
        itActionLoading[ticket.id] = false;
    }
}
function canSupervisorApprove(ticket) {
    return Boolean(ticket.priority === 'URGENT' &&
        isCurrentMemberSupervisorOfGroup(ticket.groupId) &&
        !ticket.supervisorApproved &&
        !isTicketDeleted(ticket));
}
async function supervisorApproveTicket(ticket) {
    if (!canSupervisorApprove(ticket))
        return;
    itFeedback.value = '';
    itActionLoading[ticket.id] = true;
    try {
        const updated = await requestJson(`/api/helpdesk/tickets/${ticket.id}/supervisor-approve`, { method: 'PATCH', headers: authHeaders() }, '主管確認失敗');
        replaceTicket(updated);
    }
    catch (e) {
        itFeedback.value = e instanceof Error ? e.message : '主管確認失敗';
    }
    finally {
        itActionLoading[ticket.id] = false;
    }
}
async function loadMembers() {
    if (!isAdmin.value)
        return;
    loadingMembers.value = true;
    membersFeedback.value = '';
    try {
        members.value = await requestJson('/api/admin/members', { headers: authHeaders() }, '讀取成員失敗');
    }
    catch (e) {
        membersFeedback.value = e instanceof Error ? e.message : '讀取成員失敗';
    }
    finally {
        loadingMembers.value = false;
    }
}
async function loadAdminGroups() {
    if (!isAdmin.value)
        return;
    loadingGroups.value = true;
    groupsFeedback.value = '';
    try {
        adminGroups.value = await requestJson('/api/admin/groups', { headers: authHeaders() }, '讀取群組失敗');
        if (adminGroups.value.length && !groupAssignForm.groupId) {
            groupAssignForm.groupId = adminGroups.value[0].id;
        }
    }
    catch (e) {
        groupsFeedback.value = e instanceof Error ? e.message : '讀取群組失敗';
    }
    finally {
        loadingGroups.value = false;
    }
}
async function createAdminGroup() {
    if (!isAdmin.value)
        return;
    const name = createGroupName.value.trim();
    if (!name) {
        groupsFeedback.value = '請輸入群組名稱。';
        return;
    }
    groupsFeedback.value = '';
    try {
        await requestJson('/api/admin/groups', { method: 'POST', headers: { 'Content-Type': 'application/json', ...authHeaders() }, body: JSON.stringify({ name }) }, '建立群組失敗');
        createGroupName.value = '';
        await loadAdminGroups();
        await loadMyGroups();
    }
    catch (e) {
        groupsFeedback.value = e instanceof Error ? e.message : '建立群組失敗';
    }
}
async function addMemberToGroup() {
    if (!isAdmin.value || !groupAssignForm.groupId || !groupAssignForm.memberId)
        return;
    groupsFeedback.value = '';
    try {
        await requestJson(`/api/admin/groups/${groupAssignForm.groupId}/members/${groupAssignForm.memberId}`, { method: 'PATCH', headers: authHeaders() }, '加入群組失敗');
        await loadAdminGroups();
        await loadMyGroups();
    }
    catch (e) {
        groupsFeedback.value = e instanceof Error ? e.message : '加入群組失敗';
    }
}
async function removeMemberFromGroup(groupId, memberId) {
    if (!isAdmin.value)
        return;
    groupsFeedback.value = '';
    try {
        await requestJson(`/api/admin/groups/${groupId}/members/${memberId}`, { method: 'DELETE', headers: authHeaders() }, '移出群組失敗');
        await loadAdminGroups();
        await loadMyGroups();
    }
    catch (e) {
        groupsFeedback.value = e instanceof Error ? e.message : '移出群組失敗';
    }
}
async function setGroupSupervisor(groupId, memberId) {
    if (!isAdmin.value)
        return;
    groupsFeedback.value = '';
    try {
        await requestJson(`/api/admin/groups/${groupId}/supervisor/${memberId}`, { method: 'PATCH', headers: authHeaders() }, '設定主管失敗');
        await loadAdminGroups();
        await loadMyGroups();
    }
    catch (e) {
        groupsFeedback.value = e instanceof Error ? e.message : '設定主管失敗';
    }
}
async function updateMemberRole(member, role) {
    if (member.role === 'ADMIN')
        return;
    try {
        const updated = await requestJson(`/api/admin/members/${member.id}/role`, {
            method: 'PATCH',
            headers: { 'Content-Type': 'application/json', ...authHeaders() },
            body: JSON.stringify({ role })
        }, '更新角色失敗');
        members.value = members.value.map((m) => (m.id === updated.id ? updated : m));
    }
    catch (e) {
        membersFeedback.value = e instanceof Error ? e.message : '更新角色失敗';
    }
}
async function deleteMember(member) {
    if (member.role === 'ADMIN')
        return;
    if (!confirm(`確定刪除 ${member.name} (${member.employeeId})？`))
        return;
    try {
        const response = await fetch(`/api/admin/members/${member.id}`, { method: 'DELETE', headers: authHeaders() });
        if (!response.ok) {
            let parsed = null;
            try {
                parsed = await response.json();
            }
            catch { /* ignore */ }
            throw new Error(parseErrorMessage('刪除失敗', parsed));
        }
        members.value = members.value.filter((m) => m.id !== member.id);
    }
    catch (e) {
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
debugger; /* PartiallyEnd: #3632/scriptSetup.vue */
const __VLS_ctx = {};
let __VLS_components;
let __VLS_directives;
__VLS_asFunctionalElement(__VLS_intrinsicElements.main, __VLS_intrinsicElements.main)({
    ...{ class: "page" },
});
if (!__VLS_ctx.isAuthenticated) {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "auth-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
        ...{ class: "subtitle" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "switch-row" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(!__VLS_ctx.isAuthenticated))
                    return;
                __VLS_ctx.authMode = 'login';
            } },
        ...{ class: ({ active: __VLS_ctx.authMode === 'login' }) },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!(!__VLS_ctx.isAuthenticated))
                    return;
                __VLS_ctx.authMode = 'register';
            } },
        ...{ class: ({ active: __VLS_ctx.authMode === 'register' }) },
    });
    if (__VLS_ctx.authMode === 'login') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.form, __VLS_intrinsicElements.form)({
            ...{ onSubmit: (__VLS_ctx.login) },
            ...{ class: "form-grid" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            required: true,
        });
        (__VLS_ctx.loginForm.employeeId);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            type: "password",
            required: true,
        });
        (__VLS_ctx.loginForm.password);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            disabled: (__VLS_ctx.authLoading),
            type: "submit",
        });
        (__VLS_ctx.authLoading ? '登入中...' : '登入');
    }
    else {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "stepper" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: ({ on: __VLS_ctx.registerStep >= 1 }) },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: ({ on: __VLS_ctx.registerStep >= 2 }) },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: ({ on: __VLS_ctx.registerStep >= 3 }) },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.form, __VLS_intrinsicElements.form)({
            ...{ onSubmit: (...[$event]) => {
                    if (!(!__VLS_ctx.isAuthenticated))
                        return;
                    if (!!(__VLS_ctx.authMode === 'login'))
                        return;
                    __VLS_ctx.registerStep === 3 ? __VLS_ctx.register() : __VLS_ctx.nextRegisterStep();
                } },
            ...{ class: "form-grid" },
        });
        if (__VLS_ctx.registerStep === 1) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                required: true,
            });
            (__VLS_ctx.registerForm.employeeId);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                required: true,
            });
            (__VLS_ctx.registerForm.name);
        }
        else if (__VLS_ctx.registerStep === 2) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                type: "email",
                required: true,
            });
            (__VLS_ctx.registerForm.email);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                type: "password",
                minlength: "8",
                required: true,
            });
            (__VLS_ctx.registerForm.password);
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                ...{ class: "confirm-box" },
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
            (__VLS_ctx.registerForm.employeeId);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
            (__VLS_ctx.registerForm.name);
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
            (__VLS_ctx.registerForm.email);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        if (__VLS_ctx.registerStep > 1) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                ...{ onClick: (__VLS_ctx.prevRegisterStep) },
                type: "button",
            });
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            disabled: (__VLS_ctx.authLoading),
            type: "submit",
        });
        (__VLS_ctx.registerStep === 3 ? '完成註冊' : '下一步');
    }
    if (__VLS_ctx.authError) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
            ...{ class: "feedback error" },
        });
        (__VLS_ctx.authError);
    }
}
else {
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "header-panel" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({});
    __VLS_asFunctionalElement(__VLS_intrinsicElements.h1, __VLS_intrinsicElements.h1)({});
    (__VLS_ctx.currentMember?.name);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
    (__VLS_ctx.currentMember?.employeeId);
    (__VLS_ctx.currentMember?.role);
    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
        ...{ class: "header-actions" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.isAuthenticated))
                    return;
                __VLS_ctx.notificationsOpen = !__VLS_ctx.notificationsOpen;
            } },
        ...{ class: "notify-toggle" },
        type: "button",
    });
    if (__VLS_ctx.unreadCount > 0) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "notify-badge" },
        });
        (__VLS_ctx.unreadCount);
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (__VLS_ctx.logout) },
    });
    if (__VLS_ctx.notificationsOpen) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
            ...{ class: "panel notify-panel" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "notify-head" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.markAllNotificationsRead) },
            type: "button",
        });
        if (__VLS_ctx.notificationLoading) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else if (!__VLS_ctx.notifications.length) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                ...{ class: "simple-list notify-list" },
            });
            for (const [item] of __VLS_getVForSourceType((__VLS_ctx.notifications))) {
                __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                    key: (item.id),
                    ...{ class: ({ unread: !item.read }) },
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                    ...{ onClick: (...[$event]) => {
                            if (!!(!__VLS_ctx.isAuthenticated))
                                return;
                            if (!(__VLS_ctx.notificationsOpen))
                                return;
                            if (!!(__VLS_ctx.notificationLoading))
                                return;
                            if (!!(!__VLS_ctx.notifications.length))
                                return;
                            __VLS_ctx.openNotification(item);
                        } },
                    ...{ class: "notify-item" },
                    type: "button",
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
                (item.message);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                (new Date(item.createdAt).toLocaleString());
            }
        }
        if (__VLS_ctx.notificationFeedback) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                ...{ class: "feedback error" },
            });
            (__VLS_ctx.notificationFeedback);
        }
    }
    __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
        ...{ class: "tabs" },
    });
    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
        ...{ onClick: (...[$event]) => {
                if (!!(!__VLS_ctx.isAuthenticated))
                    return;
                __VLS_ctx.dashboardTab = 'helpdesk';
            } },
        ...{ class: ({ active: __VLS_ctx.dashboardTab === 'helpdesk' }) },
    });
    if (__VLS_ctx.isItOrAdmin) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!!(!__VLS_ctx.isAuthenticated))
                        return;
                    if (!(__VLS_ctx.isItOrAdmin))
                        return;
                    __VLS_ctx.dashboardTab = 'itdesk';
                } },
            ...{ class: ({ active: __VLS_ctx.dashboardTab === 'itdesk' }) },
        });
    }
    if (__VLS_ctx.isAdmin) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (...[$event]) => {
                    if (!!(!__VLS_ctx.isAuthenticated))
                        return;
                    if (!(__VLS_ctx.isAdmin))
                        return;
                    __VLS_ctx.dashboardTab = 'members';
                    __VLS_ctx.loadMembers();
                    __VLS_ctx.loadAdminGroups();
                } },
            ...{ class: ({ active: __VLS_ctx.dashboardTab === 'members' }) },
        });
    }
    if (__VLS_ctx.dashboardTab === 'helpdesk') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
            ...{ class: "panel" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.form, __VLS_intrinsicElements.form)({
            ...{ onSubmit: (__VLS_ctx.submitTicket) },
            ...{ class: "form-grid" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            required: true,
        });
        (__VLS_ctx.ticketForm.name);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            type: "email",
            required: true,
        });
        (__VLS_ctx.ticketForm.email);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.ticketForm.groupId),
            required: true,
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: (null),
            disabled: true,
        });
        for (const [g] of __VLS_getVForSourceType((__VLS_ctx.myGroups))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                key: (g.id),
                value: (g.id),
            });
            (g.name);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            required: true,
        });
        (__VLS_ctx.ticketForm.subject);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.ticketForm.priority),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "GENERAL",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "URGENT",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.textarea)({
            value: (__VLS_ctx.ticketForm.description),
            rows: "5",
            required: true,
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            ...{ onChange: (__VLS_ctx.onFilesChanged) },
            type: "file",
            multiple: true,
        });
        if (__VLS_ctx.selectedFiles.length) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                ...{ class: "simple-list" },
            });
            for (const [f] of __VLS_getVForSourceType((__VLS_ctx.selectedFiles))) {
                __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                    key: (f.name + f.lastModified),
                });
                (f.name);
                (__VLS_ctx.formatSize(f.size));
            }
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            disabled: (__VLS_ctx.submittingTicket),
            type: "submit",
        });
        (__VLS_ctx.submittingTicket ? '送出中...' : '送出工單');
        if (__VLS_ctx.ticketFeedback) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                ...{ class: "feedback" },
                ...{ class: (__VLS_ctx.ticketFeedbackType) },
            });
            (__VLS_ctx.ticketFeedback);
        }
    }
    if (__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
            ...{ class: "panel" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "ticket-list-top" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "ticket-stats" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.total);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.todayNew);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip status-open" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.open);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip status-proceeding" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.proceeding);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip status-pending" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.pending);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip status-closed" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.closed);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
            ...{ class: "stat-chip status-deleted" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
        (__VLS_ctx.ticketStats.deleted);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "ticket-filters" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            placeholder: "工單編號 / 主旨 / 內容 / 建立人 / Email",
        });
        (__VLS_ctx.ticketKeyword);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            type: "checkbox",
        });
        (__VLS_ctx.onlyMyTickets);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.createdTimeSort),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "newest",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "oldest",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.label, __VLS_intrinsicElements.label)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.statusFilter),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "ALL",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "OPEN",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "PROCEEDING",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "PENDING",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "CLOSED",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: "DELETED",
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
        (__VLS_ctx.filteredTickets.length);
        (__VLS_ctx.tickets.length);
        if (__VLS_ctx.loadingTickets) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else if (!__VLS_ctx.filteredTickets.length) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                ...{ class: "ticket-list" },
            });
            for (const [ticket] of __VLS_getVForSourceType((__VLS_ctx.filteredTickets))) {
                __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                    id: (`ticket-${ticket.id}`),
                    key: (ticket.id),
                    ...{ class: ([
                            'ticket-card',
                            `ticket-card-${__VLS_ctx.effectiveStatus(ticket).toLowerCase()}`,
                            {
                                expanded: __VLS_ctx.openTicketIds[ticket.id],
                                'new-ticket-highlight': __VLS_ctx.newTicketHighlights[ticket.id],
                                'jump-ticket-highlight': __VLS_ctx.jumpTicketHighlights[ticket.id]
                            }
                        ]) },
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                    ...{ class: "ticket-head" },
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                    ...{ onClick: (...[$event]) => {
                            if (!!(!__VLS_ctx.isAuthenticated))
                                return;
                            if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                return;
                            if (!!(__VLS_ctx.loadingTickets))
                                return;
                            if (!!(!__VLS_ctx.filteredTickets.length))
                                return;
                            __VLS_ctx.toggleTicket(ticket.id);
                        } },
                    ...{ class: "ticket-toggle" },
                    type: "button",
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
                    ...{ class: ({ 'deleted-title': __VLS_ctx.isTicketDeleted(ticket) }) },
                });
                (ticket.id);
                (ticket.subject);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                (__VLS_ctx.openTicketIds[ticket.id] ? '收合' : '展開');
                __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({
                    ...{ class: (['ticket-meta', { 'deleted-meta': __VLS_ctx.isTicketDeleted(ticket) }]) },
                });
                (ticket.name);
                (new Date(ticket.createdAt).toLocaleString());
                if (ticket.groupName) {
                    (ticket.groupName);
                }
                __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                    ...{ class: (['priority-tag', `priority-${ticket.priority.toLowerCase()}`]) },
                });
                (ticket.priority === 'URGENT' ? '急件' : '一般');
                if (ticket.priority === 'URGENT') {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                        ...{ class: (['approval-tag', ticket.supervisorApproved ? 'approved' : 'pending']) },
                    });
                    (ticket.supervisorApproved ? '主管已確認' : '需主管確認');
                }
                if (__VLS_ctx.isTicketDeleted(ticket)) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                        ...{ class: "deleted-badge" },
                        'aria-label': "已刪除工單",
                    });
                }
                if (__VLS_ctx.canSupervisorApprove(ticket)) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                    return;
                                if (!!(__VLS_ctx.loadingTickets))
                                    return;
                                if (!!(!__VLS_ctx.filteredTickets.length))
                                    return;
                                if (!(__VLS_ctx.canSupervisorApprove(ticket)))
                                    return;
                                __VLS_ctx.supervisorApproveTicket(ticket);
                            } },
                        ...{ class: "supervisor-approve-btn" },
                        type: "button",
                        disabled: (__VLS_ctx.itActionLoading[ticket.id]),
                    });
                }
                if (__VLS_ctx.canDeleteTicket(ticket)) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                    return;
                                if (!!(__VLS_ctx.loadingTickets))
                                    return;
                                if (!!(!__VLS_ctx.filteredTickets.length))
                                    return;
                                if (!(__VLS_ctx.canDeleteTicket(ticket)))
                                    return;
                                __VLS_ctx.softDeleteTicket(ticket);
                            } },
                        ...{ class: "danger ticket-delete-btn" },
                        type: "button",
                        disabled: (__VLS_ctx.itActionLoading[ticket.id] || __VLS_ctx.isTicketDeleted(ticket)),
                    });
                    (__VLS_ctx.isTicketDeleted(ticket) ? '已刪除' : '刪除');
                }
                if (__VLS_ctx.isItOrAdmin) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({
                        ...{ class: "status-hint" },
                    });
                }
                if (__VLS_ctx.isItOrAdmin && !__VLS_ctx.isTicketDeleted(ticket)) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
                        ...{ onChange: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                    return;
                                if (!!(__VLS_ctx.loadingTickets))
                                    return;
                                if (!!(!__VLS_ctx.filteredTickets.length))
                                    return;
                                if (!(__VLS_ctx.isItOrAdmin && !__VLS_ctx.isTicketDeleted(ticket)))
                                    return;
                                __VLS_ctx.updateTicketStatus(ticket);
                            } },
                        ...{ class: (['status-select', `status-${__VLS_ctx.effectiveStatus(ticket).toLowerCase()}`]) },
                        disabled: (__VLS_ctx.itActionLoading[ticket.id]),
                        value: (__VLS_ctx.statusDrafts[ticket.id]),
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                        value: "OPEN",
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                        value: "PROCEEDING",
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                        value: "PENDING",
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                        value: "CLOSED",
                    });
                }
                else {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                        ...{ class: (['status-tag', `status-${__VLS_ctx.effectiveStatus(ticket).toLowerCase()}`]) },
                    });
                    (__VLS_ctx.displayStatus(ticket));
                }
                const __VLS_0 = {}.Transition;
                /** @type {[typeof __VLS_components.Transition, typeof __VLS_components.Transition, ]} */ ;
                // @ts-ignore
                const __VLS_1 = __VLS_asFunctionalComponent(__VLS_0, new __VLS_0({
                    name: "ticket-expand",
                }));
                const __VLS_2 = __VLS_1({
                    name: "ticket-expand",
                }, ...__VLS_functionalComponentArgsRest(__VLS_1));
                __VLS_3.slots.default;
                if (__VLS_ctx.openTicketIds[ticket.id]) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                        ...{ class: "ticket-content" },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                        ...{ class: ({ 'deleted-content': __VLS_ctx.isTicketDeleted(ticket) }) },
                    });
                    (ticket.description);
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                    (ticket.email);
                    if (ticket.deletedAt) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                        (new Date(ticket.deletedAt).toLocaleString());
                    }
                    if (ticket.attachments.length) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                            ...{ class: "simple-list" },
                        });
                        for (const [att] of __VLS_getVForSourceType((ticket.attachments))) {
                            __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                                key: (att.id),
                            });
                            if (__VLS_ctx.isImageAttachment(att)) {
                                __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                                    ...{ onClick: (...[$event]) => {
                                            if (!!(!__VLS_ctx.isAuthenticated))
                                                return;
                                            if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                                return;
                                            if (!!(__VLS_ctx.loadingTickets))
                                                return;
                                            if (!!(!__VLS_ctx.filteredTickets.length))
                                                return;
                                            if (!(__VLS_ctx.openTicketIds[ticket.id]))
                                                return;
                                            if (!(ticket.attachments.length))
                                                return;
                                            if (!(__VLS_ctx.isImageAttachment(att)))
                                                return;
                                            __VLS_ctx.openImageLightbox(ticket.id, att);
                                        } },
                                    ...{ class: "link-button" },
                                    type: "button",
                                });
                                (att.originalFilename);
                            }
                            else {
                                __VLS_asFunctionalElement(__VLS_intrinsicElements.a, __VLS_intrinsicElements.a)({
                                    href: (__VLS_ctx.attachmentDownloadUrl(ticket.id, att.id)),
                                    target: "_blank",
                                    rel: "noopener",
                                });
                                (att.originalFilename);
                            }
                        }
                    }
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                        ...{ class: "message-box" },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.h4, __VLS_intrinsicElements.h4)({});
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                        ...{ class: "simple-list" },
                    });
                    for (const [msg] of __VLS_getVForSourceType((ticket.messages))) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                            key: (msg.id),
                        });
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
                        (msg.authorRole);
                        (msg.authorName);
                        (msg.content);
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                        (new Date(msg.createdAt).toLocaleString());
                    }
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                        ...{ class: "status-history-box" },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.h4, __VLS_intrinsicElements.h4)({});
                    if (ticket.statusHistories.length) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                            ...{ class: "simple-list status-history-list" },
                        });
                        for (const [history] of __VLS_getVForSourceType((ticket.statusHistories))) {
                            __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                                key: (history.id),
                            });
                            __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({
                                ...{ class: (['status-tag', `status-${__VLS_ctx.normalizeStatus(history.toStatus).toLowerCase()}`]) },
                            });
                            (__VLS_ctx.normalizeStatus(history.toStatus));
                            __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                            (__VLS_ctx.formatStatusTransition(history));
                            (history.changedByRole);
                            (history.changedByName);
                            (history.changedByEmployeeId);
                            (new Date(history.createdAt).toLocaleString());
                        }
                    }
                    else {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                    }
                    if (__VLS_ctx.isItOrAdmin) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                            ...{ class: "it-actions" },
                        });
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                            ...{ class: "row" },
                        });
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
                            placeholder: "輸入回覆訊息",
                        });
                        (__VLS_ctx.replyInputs[ticket.id]);
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                            ...{ onClick: (...[$event]) => {
                                    if (!!(!__VLS_ctx.isAuthenticated))
                                        return;
                                    if (!(__VLS_ctx.dashboardTab === 'itdesk' || __VLS_ctx.dashboardTab === 'helpdesk'))
                                        return;
                                    if (!!(__VLS_ctx.loadingTickets))
                                        return;
                                    if (!!(!__VLS_ctx.filteredTickets.length))
                                        return;
                                    if (!(__VLS_ctx.openTicketIds[ticket.id]))
                                        return;
                                    if (!(__VLS_ctx.isItOrAdmin))
                                        return;
                                    __VLS_ctx.sendReply(ticket);
                                } },
                            disabled: (__VLS_ctx.itActionLoading[ticket.id] || __VLS_ctx.isTicketDeleted(ticket)),
                        });
                    }
                }
                var __VLS_3;
            }
        }
        if (__VLS_ctx.itFeedback) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                ...{ class: "feedback error" },
            });
            (__VLS_ctx.itFeedback);
        }
    }
    if (__VLS_ctx.dashboardTab === 'members') {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.section, __VLS_intrinsicElements.section)({
            ...{ class: "panel" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.h2, __VLS_intrinsicElements.h2)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
            ...{ class: "subtitle" },
        });
        if (__VLS_ctx.membersFeedback) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                ...{ class: "feedback error" },
            });
            (__VLS_ctx.membersFeedback);
        }
        if (__VLS_ctx.loadingMembers) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.table, __VLS_intrinsicElements.table)({
                ...{ class: "member-table" },
            });
            __VLS_asFunctionalElement(__VLS_intrinsicElements.thead, __VLS_intrinsicElements.thead)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.th, __VLS_intrinsicElements.th)({});
            __VLS_asFunctionalElement(__VLS_intrinsicElements.tbody, __VLS_intrinsicElements.tbody)({});
            for (const [m] of __VLS_getVForSourceType((__VLS_ctx.members))) {
                __VLS_asFunctionalElement(__VLS_intrinsicElements.tr, __VLS_intrinsicElements.tr)({
                    key: (m.id),
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
                (m.employeeId);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
                (m.name);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
                (m.email);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
                (m.role);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.td, __VLS_intrinsicElements.td)({});
                if (m.role !== 'ADMIN') {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                        ...{ class: "row" },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'members'))
                                    return;
                                if (!!(__VLS_ctx.loadingMembers))
                                    return;
                                if (!(m.role !== 'ADMIN'))
                                    return;
                                __VLS_ctx.updateMemberRole(m, 'USER');
                            } },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'members'))
                                    return;
                                if (!!(__VLS_ctx.loadingMembers))
                                    return;
                                if (!(m.role !== 'ADMIN'))
                                    return;
                                __VLS_ctx.updateMemberRole(m, 'IT');
                            } },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'members'))
                                    return;
                                if (!!(__VLS_ctx.loadingMembers))
                                    return;
                                if (!(m.role !== 'ADMIN'))
                                    return;
                                __VLS_ctx.deleteMember(m);
                            } },
                        ...{ class: "danger" },
                    });
                }
                else {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
                }
            }
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "group-admin" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.h3, __VLS_intrinsicElements.h3)({});
        __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
            ...{ class: "subtitle" },
        });
        if (__VLS_ctx.groupsFeedback) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({
                ...{ class: "feedback error" },
            });
            (__VLS_ctx.groupsFeedback);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.input)({
            placeholder: "新群組名稱",
        });
        (__VLS_ctx.createGroupName);
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.createAdminGroup) },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "row" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.groupAssignForm.groupId),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: (null),
            disabled: true,
        });
        for (const [g] of __VLS_getVForSourceType((__VLS_ctx.adminGroups))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                key: (g.id),
                value: (g.id),
            });
            (g.name);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.select, __VLS_intrinsicElements.select)({
            value: (__VLS_ctx.groupAssignForm.memberId),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
            value: (null),
            disabled: true,
        });
        for (const [m] of __VLS_getVForSourceType((__VLS_ctx.members))) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.option, __VLS_intrinsicElements.option)({
                key: (m.id),
                value: (m.id),
            });
            (m.employeeId);
            (m.name);
        }
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.addMemberToGroup) },
        });
        if (__VLS_ctx.loadingGroups) {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        }
        else {
            __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                ...{ class: "simple-list group-list" },
            });
            for (const [g] of __VLS_getVForSourceType((__VLS_ctx.adminGroups))) {
                __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                    key: (g.id),
                    ...{ class: "group-card" },
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                    ...{ class: "group-head" },
                });
                __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({});
                (g.name);
                __VLS_asFunctionalElement(__VLS_intrinsicElements.small, __VLS_intrinsicElements.small)({});
                (new Date(g.createdAt).toLocaleString());
                __VLS_asFunctionalElement(__VLS_intrinsicElements.ul, __VLS_intrinsicElements.ul)({
                    ...{ class: "simple-list" },
                });
                for (const [gm] of __VLS_getVForSourceType((g.members))) {
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.li, __VLS_intrinsicElements.li)({
                        key: (`${g.id}-${gm.memberId}`),
                        ...{ class: "group-member-row" },
                    });
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.span, __VLS_intrinsicElements.span)({});
                    (gm.employeeId);
                    (gm.name);
                    (gm.role);
                    if (gm.supervisor) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.strong, __VLS_intrinsicElements.strong)({
                            ...{ class: "group-supervisor-chip" },
                        });
                    }
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
                        ...{ class: "row" },
                    });
                    if (!gm.supervisor) {
                        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                            ...{ onClick: (...[$event]) => {
                                    if (!!(!__VLS_ctx.isAuthenticated))
                                        return;
                                    if (!(__VLS_ctx.dashboardTab === 'members'))
                                        return;
                                    if (!!(__VLS_ctx.loadingGroups))
                                        return;
                                    if (!(!gm.supervisor))
                                        return;
                                    __VLS_ctx.setGroupSupervisor(g.id, gm.memberId);
                                } },
                        });
                    }
                    __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
                        ...{ onClick: (...[$event]) => {
                                if (!!(!__VLS_ctx.isAuthenticated))
                                    return;
                                if (!(__VLS_ctx.dashboardTab === 'members'))
                                    return;
                                if (!!(__VLS_ctx.loadingGroups))
                                    return;
                                __VLS_ctx.removeMemberFromGroup(g.id, gm.memberId);
                            } },
                        ...{ class: "danger" },
                    });
                }
            }
        }
    }
    if (__VLS_ctx.lightboxOpen) {
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ onClick: (__VLS_ctx.closeLightbox) },
            ...{ class: "lightbox" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.div, __VLS_intrinsicElements.div)({
            ...{ class: "lightbox-body" },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.button, __VLS_intrinsicElements.button)({
            ...{ onClick: (__VLS_ctx.closeLightbox) },
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.img)({
            src: (__VLS_ctx.lightboxSrc),
            alt: (__VLS_ctx.lightboxTitle),
        });
        __VLS_asFunctionalElement(__VLS_intrinsicElements.p, __VLS_intrinsicElements.p)({});
        (__VLS_ctx.lightboxTitle);
    }
}
/** @type {__VLS_StyleScopedClasses['page']} */ ;
/** @type {__VLS_StyleScopedClasses['auth-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['subtitle']} */ ;
/** @type {__VLS_StyleScopedClasses['switch-row']} */ ;
/** @type {__VLS_StyleScopedClasses['form-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['stepper']} */ ;
/** @type {__VLS_StyleScopedClasses['form-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['confirm-box']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['error']} */ ;
/** @type {__VLS_StyleScopedClasses['header-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['header-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-toggle']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-badge']} */ ;
/** @type {__VLS_StyleScopedClasses['panel']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-panel']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-head']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-list']} */ ;
/** @type {__VLS_StyleScopedClasses['notify-item']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['error']} */ ;
/** @type {__VLS_StyleScopedClasses['tabs']} */ ;
/** @type {__VLS_StyleScopedClasses['panel']} */ ;
/** @type {__VLS_StyleScopedClasses['form-grid']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['panel']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-list-top']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-stats']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['status-open']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['status-proceeding']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['status-pending']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['status-closed']} */ ;
/** @type {__VLS_StyleScopedClasses['stat-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['status-deleted']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-filters']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-list']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-head']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-toggle']} */ ;
/** @type {__VLS_StyleScopedClasses['deleted-badge']} */ ;
/** @type {__VLS_StyleScopedClasses['supervisor-approve-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['danger']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-delete-btn']} */ ;
/** @type {__VLS_StyleScopedClasses['status-hint']} */ ;
/** @type {__VLS_StyleScopedClasses['ticket-content']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['link-button']} */ ;
/** @type {__VLS_StyleScopedClasses['message-box']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['status-history-box']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['status-history-list']} */ ;
/** @type {__VLS_StyleScopedClasses['it-actions']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['error']} */ ;
/** @type {__VLS_StyleScopedClasses['panel']} */ ;
/** @type {__VLS_StyleScopedClasses['subtitle']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['error']} */ ;
/** @type {__VLS_StyleScopedClasses['member-table']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['danger']} */ ;
/** @type {__VLS_StyleScopedClasses['group-admin']} */ ;
/** @type {__VLS_StyleScopedClasses['subtitle']} */ ;
/** @type {__VLS_StyleScopedClasses['feedback']} */ ;
/** @type {__VLS_StyleScopedClasses['error']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['group-list']} */ ;
/** @type {__VLS_StyleScopedClasses['group-card']} */ ;
/** @type {__VLS_StyleScopedClasses['group-head']} */ ;
/** @type {__VLS_StyleScopedClasses['simple-list']} */ ;
/** @type {__VLS_StyleScopedClasses['group-member-row']} */ ;
/** @type {__VLS_StyleScopedClasses['group-supervisor-chip']} */ ;
/** @type {__VLS_StyleScopedClasses['row']} */ ;
/** @type {__VLS_StyleScopedClasses['danger']} */ ;
/** @type {__VLS_StyleScopedClasses['lightbox']} */ ;
/** @type {__VLS_StyleScopedClasses['lightbox-body']} */ ;
var __VLS_dollars;
const __VLS_self = (await import('vue')).defineComponent({
    setup() {
        return {
            authMode: authMode,
            registerStep: registerStep,
            authLoading: authLoading,
            authError: authError,
            loginForm: loginForm,
            registerForm: registerForm,
            currentMember: currentMember,
            dashboardTab: dashboardTab,
            ticketForm: ticketForm,
            selectedFiles: selectedFiles,
            submittingTicket: submittingTicket,
            ticketFeedback: ticketFeedback,
            ticketFeedbackType: ticketFeedbackType,
            tickets: tickets,
            loadingTickets: loadingTickets,
            ticketKeyword: ticketKeyword,
            onlyMyTickets: onlyMyTickets,
            createdTimeSort: createdTimeSort,
            statusFilter: statusFilter,
            members: members,
            loadingMembers: loadingMembers,
            membersFeedback: membersFeedback,
            myGroups: myGroups,
            adminGroups: adminGroups,
            loadingGroups: loadingGroups,
            groupsFeedback: groupsFeedback,
            createGroupName: createGroupName,
            groupAssignForm: groupAssignForm,
            replyInputs: replyInputs,
            statusDrafts: statusDrafts,
            itActionLoading: itActionLoading,
            itFeedback: itFeedback,
            openTicketIds: openTicketIds,
            newTicketHighlights: newTicketHighlights,
            jumpTicketHighlights: jumpTicketHighlights,
            lightboxOpen: lightboxOpen,
            lightboxSrc: lightboxSrc,
            lightboxTitle: lightboxTitle,
            notifications: notifications,
            unreadCount: unreadCount,
            notificationsOpen: notificationsOpen,
            notificationLoading: notificationLoading,
            notificationFeedback: notificationFeedback,
            isAuthenticated: isAuthenticated,
            isAdmin: isAdmin,
            isItOrAdmin: isItOrAdmin,
            normalizeStatus: normalizeStatus,
            effectiveStatus: effectiveStatus,
            isTicketDeleted: isTicketDeleted,
            ticketStats: ticketStats,
            filteredTickets: filteredTickets,
            formatSize: formatSize,
            displayStatus: displayStatus,
            formatStatusTransition: formatStatusTransition,
            attachmentDownloadUrl: attachmentDownloadUrl,
            isImageAttachment: isImageAttachment,
            openImageLightbox: openImageLightbox,
            closeLightbox: closeLightbox,
            nextRegisterStep: nextRegisterStep,
            prevRegisterStep: prevRegisterStep,
            login: login,
            register: register,
            logout: logout,
            onFilesChanged: onFilesChanged,
            submitTicket: submitTicket,
            updateTicketStatus: updateTicketStatus,
            sendReply: sendReply,
            markAllNotificationsRead: markAllNotificationsRead,
            openNotification: openNotification,
            toggleTicket: toggleTicket,
            canDeleteTicket: canDeleteTicket,
            softDeleteTicket: softDeleteTicket,
            canSupervisorApprove: canSupervisorApprove,
            supervisorApproveTicket: supervisorApproveTicket,
            loadMembers: loadMembers,
            loadAdminGroups: loadAdminGroups,
            createAdminGroup: createAdminGroup,
            addMemberToGroup: addMemberToGroup,
            removeMemberFromGroup: removeMemberFromGroup,
            setGroupSupervisor: setGroupSupervisor,
            updateMemberRole: updateMemberRole,
            deleteMember: deleteMember,
        };
    },
});
export default (await import('vue')).defineComponent({
    setup() {
        return {};
    },
});
; /* PartiallyEnd: #4569/main.vue */
