<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useAuth } from './composables/useAuth';
import { useNotifications } from './composables/useNotifications';
import { useAuditLogs } from './composables/useAuditLogs';
import { useMembers } from './composables/useMembers';
import { useTicketsView } from './composables/useTicketsView';
import { useTicketsActions } from './composables/useTicketsActions';
import { useAdminManagement } from './composables/useAdminManagement';
import { useBaseData } from './composables/useBaseData';
import { useDashboardLifecycle } from './composables/useDashboardLifecycle';
import { useRealtimeTickets } from './composables/useRealtimeTickets';
import HelpdeskForm from './components/HelpdeskForm.vue';
import ActiveTicketsPanel from './components/ActiveTicketsPanel.vue';
import ArchivePanel from './components/ArchivePanel.vue';
import MembersAdminPanel from './components/MembersAdminPanel.vue';
import NotificationPanel from './components/NotificationPanel.vue';
import DashboardTabs from './components/DashboardTabs.vue';
import HeaderPanel from './components/HeaderPanel.vue';
import AuthPanel from './components/AuthPanel.vue';
import LightboxModal from './components/LightboxModal.vue';
import { useTicketDefaults } from './composables/useTicketDefaults';
import type {
  DashboardContext,
  DashboardTab,
  Member
} from './types';

const TOKEN_KEY = 'helpdesk_auth_token';

const dashboardTab = ref<DashboardTab>('helpdesk');
let applyMemberProfileHandler: (member: Member) => void = () => {};
let afterLoginHandler: () => Promise<void> = async () => {};
let clearSessionHandler: () => void = () => {};

const {
  authMode,
  registerStep,
  authLoading,
  authError,
  loginForm,
  registerForm,
  registerGroupOptions,
  token,
  currentMember,
  isAuthenticated,
  isAdmin,
  isItOrAdmin,
  authHeaders,
  nextRegisterStep,
  prevRegisterStep,
  loadRegisterGroupOptions,
  login,
  register,
  restoreSession,
  logout
} = useAuth({
  tokenKey: TOKEN_KEY,
  onAfterLogin: () => afterLoginHandler(),
  onApplyMemberProfile: (member) => applyMemberProfileHandler(member),
  onClearState: () => clearSessionHandler()
});

const {
  myGroups,
  helpdeskCategories,
  loadMyGroups: loadBaseMyGroups,
  loadHelpdeskCategories: loadBaseHelpdeskCategories,
  clearBaseDataState
} = useBaseData({
  token,
  authHeaders
});

const {
  MAX_FILE_BYTES,
  EDITABLE_STATUSES,
  ticketForm,
  selectedFiles,
  submittingTicket,
  ticketFeedback,
  ticketFeedbackType,
  tickets,
  loadingTickets,
  ticketKeyword,
  onlyMyTickets,
  createdTimeSort,
  statusFilter,
  archiveStatusFilter,
  replyInputs,
  replyFiles,
  statusDrafts,
  itActionLoading,
  itFeedback,
  openTicketIds,
  newTicketHighlights,
  jumpTicketHighlights,
  lightboxOpen,
  lightboxSrc,
  lightboxTitle,
  ticketStats,
  filteredActiveTickets,
  filteredArchivedTickets,
  normalizeStatus,
  effectiveStatus,
  isTicketDeleted,
  formatSize,
  displayStatus,
  normalizeTicket,
  formatStatusTransition,
  isImageAttachment,
  openImageLightbox,
  downloadAttachment,
  closeLightbox,
  highlightTicket,
  onFilesChanged,
  onReplyFilesChanged,
  replaceTicket,
  toggleTicket,
  canDeleteTicket,
  canSupervisorApprove,
  applyMemberProfile: applyTicketMemberProfile,
  clearTicketState
} = useTicketsView({
  authHeaders,
  currentMember,
  myGroups,
  helpdeskCategories
});

const { loadMyGroups, loadHelpdeskCategories } = useTicketDefaults({
  myGroups,
  helpdeskCategories,
  ticketForm,
  loadBaseMyGroups,
  loadBaseHelpdeskCategories
});

applyMemberProfileHandler = applyTicketMemberProfile;

const {
  loadTickets,
  submitTicket,
  updateTicketStatus,
  sendReply,
  softDeleteTicket,
  supervisorApproveTicket
} = useTicketsActions({
  authHeaders,
  ticketForm,
  selectedFiles,
  submittingTicket,
  ticketFeedback,
  ticketFeedbackType,
  tickets,
  loadingTickets,
  replyInputs,
  replyFiles,
  statusDrafts,
  itActionLoading,
  itFeedback,
  openTicketIds,
  myGroups,
  helpdeskCategories,
  maxFileBytes: MAX_FILE_BYTES,
  editableStatuses: EDITABLE_STATUSES,
  normalizeStatus,
  normalizeTicket,
  effectiveStatus,
  isTicketDeleted,
  canDeleteTicket,
  canSupervisorApprove,
  highlightTicket,
  replaceTicket
});

const {
  adminHelpdeskCategories,
  categoryFeedback,
  createCategoryName,
  adminGroups,
  loadingGroups,
  groupsFeedback,
  createGroupName,
  groupAssignForm,
  loadAdminGroups,
  loadAdminHelpdeskCategories,
  createHelpdeskCategory,
  updateHelpdeskCategory,
  deleteHelpdeskCategory,
  createAdminGroup,
  addMemberToGroup,
  removeMemberFromGroup,
  setGroupSupervisor,
  clearAdminManagementState
} = useAdminManagement({
  isAdmin,
  authHeaders,
  loadMyGroups,
  loadHelpdeskCategories
});

const {
  members,
  loadingMembers,
  membersFeedback,
  loadMembers,
  updateMemberRole,
  deleteMember,
  clearMemberState
} = useMembers({
  isAdmin,
  authHeaders
});

const {
  notifications,
  unreadCount,
  notificationsOpen,
  notificationLoading,
  notificationFeedback,
  loadNotifications,
  markAllNotificationsRead,
  startNotificationPolling,
  stopNotificationPolling,
  openNotification,
  clearNotificationState
} = useNotifications({
  token,
  authHeaders,
  isItOrAdmin,
  loadTickets,
  tickets,
  effectiveStatus,
  setDashboardTab: (tab) => (dashboardTab.value = tab),
  openTicketIds,
  highlightTicket
});

const {
  auditLogs,
  loadingAuditLogs,
  exportingAuditLogs,
  purgingAuditLogs,
  auditLogsFeedback,
  auditCleanupFeedback,
  auditFilters,
  auditCleanupDays,
  formatJsonPreview,
  loadAuditLogs,
  exportAuditLogsCsv,
  purgeAuditLogs,
  clearAuditLogState
} = useAuditLogs({
  isAdmin,
  authHeaders
});

const { runOnMounted, runOnBeforeUnmount, runOpenMembersTab, runAfterLoginLoad, runClearSessionState } = useDashboardLifecycle({
  restoreSession,
  isAdmin,
  isItOrAdmin,
  dashboardTab,
  loadMyGroups,
  loadHelpdeskCategories,
  loadTickets,
  loadNotifications,
  startNotificationPolling,
  stopNotificationPolling,
  loadMembers,
  loadAdminGroups,
  loadAdminHelpdeskCategories,
  loadAuditLogs,
  clearNotificationState,
  clearTicketState,
  clearMemberState,
  clearBaseDataState,
  clearAdminManagementState,
  clearAuditLogState
});

afterLoginHandler = runAfterLoginLoad;
clearSessionHandler = runClearSessionState;

const activeTicketsPanelProps = computed(() => ({
  ticketStats: ticketStats.value,
  filteredActiveTickets: filteredActiveTickets.value,
  tickets: tickets.value,
  loadingTickets: loadingTickets.value,
  openTicketIds,
  newTicketHighlights,
  jumpTicketHighlights,
  isItOrAdmin: isItOrAdmin.value,
  itActionLoading,
  statusDrafts,
  replyInputs,
  replyFiles,
  itFeedback: itFeedback.value,
  onReplyFilesChanged,
  effectiveStatus,
  isTicketDeleted,
  canSupervisorApprove,
  canDeleteTicket,
  displayStatus,
  formatSize,
  isImageAttachment,
  normalizeStatus,
  formatStatusTransition
}));

const archivePanelProps = computed(() => ({
  filteredArchivedTickets: filteredArchivedTickets.value,
  tickets: tickets.value,
  loadingTickets: loadingTickets.value,
  openTicketIds,
  newTicketHighlights,
  jumpTicketHighlights,
  effectiveStatus,
  isTicketDeleted,
  displayStatus,
  formatSize,
  isImageAttachment,
  normalizeStatus,
  formatStatusTransition
}));

const membersPanelProps = computed(() => ({
  membersFeedback: membersFeedback.value,
  loadingMembers: loadingMembers.value,
  members: members.value,
  groupsFeedback: groupsFeedback.value,
  groupAssignForm,
  loadingGroups: loadingGroups.value,
  adminGroups: adminGroups.value,
  categoryFeedback: categoryFeedback.value,
  adminHelpdeskCategories: adminHelpdeskCategories.value,
  exportingAuditLogs: exportingAuditLogs.value,
  auditLogsFeedback: auditLogsFeedback.value,
  auditCleanupFeedback: auditCleanupFeedback.value,
  purgingAuditLogs: purgingAuditLogs.value,
  auditFilters,
  loadingAuditLogs: loadingAuditLogs.value,
  auditLogs: auditLogs.value,
  formatJsonPreview
}));

const dashboardContext = computed<DashboardContext>(() => ({
  dashboardTab: dashboardTab.value,
  isItOrAdmin: isItOrAdmin.value,
  isAdmin: isAdmin.value,
  notificationsOpen: notificationsOpen.value,
  unreadCount: unreadCount.value
}));

const currentMemberId = computed(() => currentMember.value?.id ?? null);

const { connectRealtimeTickets, disconnectRealtimeTickets } = useRealtimeTickets({
  isAuthenticated,
  currentMemberId,
  loadTickets,
  loadNotifications,
  highlightTicket
});

watch(
  isAuthenticated,
  (authed) => {
    if (authed) {
      connectRealtimeTickets();
      return;
    }
    disconnectRealtimeTickets();
  },
  { immediate: true }
);

onMounted(async () => {
  await loadRegisterGroupOptions();
  await runOnMounted();
});

onBeforeUnmount(() => {
  disconnectRealtimeTickets();
  runOnBeforeUnmount();
});
</script>

<template>
  <main class="page">
    <template v-if="!isAuthenticated">
      <AuthPanel
        v-model:auth-mode="authMode"
        :register-step="registerStep"
        :auth-loading="authLoading"
        :auth-error="authError"
        :login-form="loginForm"
        :register-form="registerForm"
        :register-group-options="registerGroupOptions"
        @login="login"
        @next-register-step="nextRegisterStep"
        @prev-register-step="prevRegisterStep"
        @register="register"
      />
    </template>

    <template v-else>
      <HeaderPanel
        :current-member="currentMember"
        :context="dashboardContext"
        @toggle-notifications="notificationsOpen = !notificationsOpen"
        @logout="logout"
      />

      <NotificationPanel
        :context="dashboardContext"
        :notification-loading="notificationLoading"
        :notifications="notifications"
        :notification-feedback="notificationFeedback"
        @mark-all-read="markAllNotificationsRead"
        @open-notification="openNotification"
      />

      <DashboardTabs
        :context="dashboardContext"
        @set-tab="dashboardTab = $event"
        @open-members="runOpenMembersTab"
      />

      <HelpdeskForm
        v-if="dashboardTab === 'helpdesk'"
        :ticket-form="ticketForm"
        :my-groups="myGroups"
        :helpdesk-categories="helpdeskCategories"
        :selected-files="selectedFiles"
        :submitting-ticket="submittingTicket"
        :ticket-feedback="ticketFeedback"
        :ticket-feedback-type="ticketFeedbackType"
        :format-size="formatSize"
        @submit="submitTicket"
        @files-changed="onFilesChanged"
      />

      <ActiveTicketsPanel
        v-if="dashboardTab === 'itdesk' || dashboardTab === 'helpdesk'"
        v-model:ticket-keyword="ticketKeyword"
        v-model:only-my-tickets="onlyMyTickets"
        v-model:created-time-sort="createdTimeSort"
        v-model:status-filter="statusFilter"
        v-bind="activeTicketsPanelProps"
        @toggle-ticket="toggleTicket"
        @supervisor-approve="supervisorApproveTicket"
        @soft-delete="softDeleteTicket"
        @update-ticket-status="updateTicketStatus"
        @open-image-lightbox="openImageLightbox"
        @download-attachment="downloadAttachment"
        @send-reply="sendReply"
      />

      <ArchivePanel
        v-if="dashboardTab === 'archive'"
        v-model:ticket-keyword="ticketKeyword"
        v-model:only-my-tickets="onlyMyTickets"
        v-model:created-time-sort="createdTimeSort"
        v-model:archive-status-filter="archiveStatusFilter"
        v-bind="archivePanelProps"
        @toggle-ticket="toggleTicket"
        @open-image-lightbox="openImageLightbox"
        @download-attachment="downloadAttachment"
      />

      <MembersAdminPanel
        v-if="dashboardTab === 'members'"
        v-model:create-group-name="createGroupName"
        v-model:create-category-name="createCategoryName"
        v-model:audit-cleanup-days="auditCleanupDays"
        v-bind="membersPanelProps"
        @update-member-role="updateMemberRole"
        @delete-member="deleteMember"
        @create-admin-group="createAdminGroup"
        @add-member-to-group="addMemberToGroup"
        @set-group-supervisor="setGroupSupervisor"
        @remove-member-from-group="removeMemberFromGroup"
        @create-helpdesk-category="createHelpdeskCategory"
        @update-helpdesk-category="updateHelpdeskCategory"
        @delete-helpdesk-category="deleteHelpdeskCategory"
        @load-audit-logs="loadAuditLogs"
        @export-audit-logs-csv="exportAuditLogsCsv"
        @purge-audit-logs="purgeAuditLogs"
      />

      <LightboxModal
        :open="lightboxOpen"
        :src="lightboxSrc"
        :title="lightboxTitle"
        @close="closeLightbox"
      />
    </template>
  </main>
</template>
