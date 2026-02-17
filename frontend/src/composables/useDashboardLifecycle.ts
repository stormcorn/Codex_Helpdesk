import type { Ref } from 'vue';
import type { DashboardTab } from '../types';

type UseDashboardLifecycleOptions = {
  restoreSession: () => Promise<void>;
  isAdmin: Ref<boolean>;
  isItOrAdmin: Ref<boolean>;
  dashboardTab: Ref<DashboardTab>;
  loadMyGroups: () => Promise<void>;
  loadHelpdeskCategories: () => Promise<void>;
  loadTickets: () => Promise<void>;
  loadNotifications: () => Promise<void>;
  startNotificationPolling: () => void;
  stopNotificationPolling: () => void;
  loadMembers: () => Promise<void>;
  loadAdminGroups: () => Promise<void>;
  loadAdminHelpdeskCategories: () => Promise<void>;
  loadAuditLogs: () => Promise<void>;
  clearNotificationState: () => void;
  clearTicketState: () => void;
  clearMemberState: () => void;
  clearBaseDataState: () => void;
  clearAdminManagementState: () => void;
  clearAuditLogState: () => void;
};

export function useDashboardLifecycle(options: UseDashboardLifecycleOptions) {
  async function runOnMounted(): Promise<void> {
    await options.restoreSession();
  }

  function runOnBeforeUnmount(): void {
    options.stopNotificationPolling();
    options.clearTicketState();
  }

  async function runOpenMembersTab(): Promise<void> {
    options.dashboardTab.value = 'members';
    await options.loadMembers();
    await options.loadAdminGroups();
    await options.loadAdminHelpdeskCategories();
    await options.loadAuditLogs();
  }

  async function runAfterLoginLoad(): Promise<void> {
    await options.loadMyGroups();
    await options.loadHelpdeskCategories();
    await options.loadTickets();
    await options.loadNotifications();
    options.startNotificationPolling();
    if (options.isAdmin.value) {
      options.dashboardTab.value = 'members';
      await options.loadMembers();
      await options.loadAdminGroups();
      await options.loadAdminHelpdeskCategories();
      await options.loadAuditLogs();
    } else if (options.isItOrAdmin.value) {
      options.dashboardTab.value = 'itdesk';
    } else {
      options.dashboardTab.value = 'helpdesk';
    }
  }

  function runClearSessionState(): void {
    options.stopNotificationPolling();
    options.clearNotificationState();
    options.clearTicketState();
    options.clearMemberState();
    options.clearBaseDataState();
    options.clearAdminManagementState();
    options.clearAuditLogState();
    options.dashboardTab.value = 'helpdesk';
  }

  return {
    runOnMounted,
    runOnBeforeUnmount,
    runOpenMembersTab,
    runAfterLoginLoad,
    runClearSessionState
  };
}
