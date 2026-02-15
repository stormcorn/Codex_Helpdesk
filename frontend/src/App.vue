<script setup lang="ts">
import { nextTick, onBeforeUnmount, onMounted } from 'vue';
import AuthPanel from './components/AuthPanel.vue';
import AppHeader from './components/AppHeader.vue';
import NotificationPanel from './components/NotificationPanel.vue';
import TicketFormPanel from './components/TicketFormPanel.vue';
import MembersPanel from './components/MembersPanel.vue';
import TicketListPanel from './components/TicketListPanel.vue';
import { useNotifications } from './composables/useNotifications';
import { useMembers } from './composables/useMembers';
import { useAuth } from './composables/useAuth';
import { useTickets } from './composables/useTickets';
import { useDashboardOrchestrator } from './composables/useDashboardOrchestrator';

const TOKEN_KEY = 'helpdesk_auth_token';

const {
  authMode,
  registerStep,
  authLoading,
  authError,
  loginForm,
  registerForm,
  token,
  currentMember,
  isAuthenticated,
  isAdmin,
  isItOrAdmin,
  authHeaders,
  nextRegisterStep,
  prevRegisterStep,
  login,
  register,
  restoreSession,
  logout
} = useAuth({ tokenKey: TOKEN_KEY });

const {
  ticketForm,
  selectedFiles,
  submittingTicket,
  ticketFeedback,
  ticketFeedbackType,
  tickets,
  loadingTickets,
  ticketStats,
  replyInputs,
  itActionLoading,
  itFeedback,
  openTicketIds,
  lightboxOpen,
  lightboxSrc,
  lightboxTitle,
  formatSize,
  effectiveStatus,
  displayStatus,
  attachmentDownloadUrl,
  isImageAttachment,
  openImageLightbox,
  closeLightbox,
  onFilesChanged,
  openTicket,
  loadTickets,
  submitTicket,
  getNextStatus,
  quickAdvanceTicketStatus,
  sendReply,
  toggleTicket,
  onReplyInput,
  canDeleteTicket,
  softDeleteTicket,
  applyMemberProfile,
  clearTicketState
} = useTickets({
  token,
  authHeaders,
  currentMember
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
  onNavigateToTicket: navigateToTicketFromNotification
});

const {
  dashboardTab,
  runPostLoginSetup,
  clearSessionState,
  openMembersTab,
  setDashboardTab
} = useDashboardOrchestrator({
  isAdmin,
  isItOrAdmin,
  loadTickets,
  loadNotifications,
  startNotificationPolling,
  stopNotificationPolling,
  loadMembers,
  clearTicketState,
  clearMemberState,
  clearNotificationState
});

async function navigateToTicketFromNotification(ticketId: number): Promise<void> {
  setDashboardTab(isItOrAdmin.value ? 'itdesk' : 'helpdesk');
  await loadTickets();
  openTicket(ticketId);
  await nextTick();
  const target = document.getElementById(`ticket-${ticketId}`);
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function handleLogin(): Promise<void> {
  const member = await login();
  if (!member) return;
  applyMemberProfile(member);
  await runPostLoginSetup();
}

async function handleRegister(): Promise<void> {
  const member = await register();
  if (!member) return;
  applyMemberProfile(member);
  await runPostLoginSetup();
}

async function handleLogout(): Promise<void> {
  await logout();
  clearSessionState();
}

onMounted(async () => {
  const member = await restoreSession();
  if (!member) return;
  applyMemberProfile(member);
  await runPostLoginSetup();
});

onBeforeUnmount(() => {
  stopNotificationPolling();
});
</script>

<template>
  <main class="page">
    <template v-if="!isAuthenticated">
      <AuthPanel
        :auth-mode="authMode"
        :register-step="registerStep"
        :auth-loading="authLoading"
        :auth-error="authError"
        :login-form="loginForm"
        :register-form="registerForm"
        :set-auth-mode="(mode) => (authMode = mode)"
        :on-login="handleLogin"
        :on-register="handleRegister"
        :on-next-register-step="nextRegisterStep"
        :on-prev-register-step="prevRegisterStep"
      />
    </template>

    <template v-else>
      <AppHeader
        :current-member="currentMember"
        :unread-count="unreadCount"
        :notifications-open="notificationsOpen"
        :on-toggle-notifications="() => (notificationsOpen = !notificationsOpen)"
        :on-logout="handleLogout"
      />

      <NotificationPanel
        :open="notificationsOpen"
        :loading="notificationLoading"
        :notifications="notifications"
        :feedback="notificationFeedback"
        :on-mark-all-read="markAllNotificationsRead"
        :on-open-notification="openNotification"
      />

      <section class="tabs">
        <button :class="{ active: dashboardTab === 'helpdesk' }" @click="dashboardTab = 'helpdesk'">提交工單</button>
        <button v-if="isItOrAdmin" :class="{ active: dashboardTab === 'itdesk' }" @click="dashboardTab = 'itdesk'">IT 工單處理</button>
        <button v-if="isAdmin" :class="{ active: dashboardTab === 'members' }" @click="openMembersTab">成員管理</button>
      </section>

      <TicketFormPanel
        :open="dashboardTab === 'helpdesk'"
        :ticket-form="ticketForm"
        :selected-files="selectedFiles"
        :submitting-ticket="submittingTicket"
        :ticket-feedback="ticketFeedback"
        :ticket-feedback-type="ticketFeedbackType"
        :on-files-changed="onFilesChanged"
        :on-submit="submitTicket"
        :format-size="formatSize"
      />

      <TicketListPanel
        :open="dashboardTab === 'itdesk' || dashboardTab === 'helpdesk'"
        :loading-tickets="loadingTickets"
        :tickets="tickets"
        :ticket-stats="ticketStats"
        :is-it-or-admin="isItOrAdmin"
        :open-ticket-ids="openTicketIds"
        :it-action-loading="itActionLoading"
        :reply-inputs="replyInputs"
        :it-feedback="itFeedback"
        :effective-status="effectiveStatus"
        :display-status="displayStatus"
        :get-next-status="getNextStatus"
        :can-delete-ticket="canDeleteTicket"
        :on-toggle-ticket="toggleTicket"
        :on-soft-delete-ticket="softDeleteTicket"
        :on-quick-advance-ticket-status="quickAdvanceTicketStatus"
        :is-image-attachment="isImageAttachment"
        :on-open-image-lightbox="openImageLightbox"
        :attachment-download-url="attachmentDownloadUrl"
        :on-send-reply="sendReply"
        :on-reply-input="onReplyInput"
      />

      <MembersPanel
        :open="dashboardTab === 'members'"
        :loading-members="loadingMembers"
        :members-feedback="membersFeedback"
        :members="members"
        :on-update-member-role="updateMemberRole"
        :on-delete-member="deleteMember"
      />

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
