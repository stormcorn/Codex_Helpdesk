import { ref, type ComputedRef } from 'vue';
import type { DashboardTab } from '../types';

type UseDashboardOrchestratorOptions = {
  isAdmin: ComputedRef<boolean>;
  isItOrAdmin: ComputedRef<boolean>;
  loadTickets: () => Promise<void>;
  loadNotifications: () => Promise<void>;
  startNotificationPolling: () => void;
  stopNotificationPolling: () => void;
  loadMembers: () => Promise<void>;
  clearTicketState: () => void;
  clearMemberState: () => void;
  clearNotificationState: () => void;
};

export function useDashboardOrchestrator(options: UseDashboardOrchestratorOptions) {
  const dashboardTab = ref<DashboardTab>('helpdesk');

  async function runPostLoginSetup(): Promise<void> {
    await options.loadTickets();
    await options.loadNotifications();
    options.startNotificationPolling();
    if (options.isAdmin.value) {
      dashboardTab.value = 'members';
      await options.loadMembers();
      return;
    }
    dashboardTab.value = options.isItOrAdmin.value ? 'itdesk' : 'helpdesk';
  }

  function clearSessionState(): void {
    options.stopNotificationPolling();
    options.clearTicketState();
    options.clearMemberState();
    options.clearNotificationState();
    dashboardTab.value = 'helpdesk';
  }

  async function openMembersTab(): Promise<void> {
    dashboardTab.value = 'members';
    await options.loadMembers();
  }

  function setDashboardTab(tab: DashboardTab): void {
    dashboardTab.value = tab;
  }

  return {
    dashboardTab,
    runPostLoginSetup,
    clearSessionState,
    openMembersTab,
    setDashboardTab
  };
}
