import { ref } from 'vue';
import { describe, expect, it, vi } from 'vitest';
import { useDashboardLifecycle } from '../useDashboardLifecycle';

function createLifecycle() {
  const isAdmin = ref(false);
  const isItOrAdmin = ref(false);
  const dashboardTab = ref<'helpdesk' | 'itdesk' | 'archive' | 'members'>('helpdesk');

  const fns = {
    restoreSession: vi.fn(async () => {}),
    loadMyGroups: vi.fn(async () => {}),
    loadHelpdeskCategories: vi.fn(async () => {}),
    loadTickets: vi.fn(async () => {}),
    loadNotifications: vi.fn(async () => {}),
    startNotificationPolling: vi.fn(),
    stopNotificationPolling: vi.fn(),
    loadMembers: vi.fn(async () => {}),
    loadAdminGroups: vi.fn(async () => {}),
    loadAdminHelpdeskCategories: vi.fn(async () => {}),
    loadAuditLogs: vi.fn(async () => {}),
    clearNotificationState: vi.fn(),
    clearTicketState: vi.fn(),
    clearMemberState: vi.fn(),
    clearBaseDataState: vi.fn(),
    clearAdminManagementState: vi.fn(),
    clearAuditLogState: vi.fn()
  };

  const lifecycle = useDashboardLifecycle({
    restoreSession: fns.restoreSession,
    isAdmin,
    isItOrAdmin,
    dashboardTab,
    loadMyGroups: fns.loadMyGroups,
    loadHelpdeskCategories: fns.loadHelpdeskCategories,
    loadTickets: fns.loadTickets,
    loadNotifications: fns.loadNotifications,
    startNotificationPolling: fns.startNotificationPolling,
    stopNotificationPolling: fns.stopNotificationPolling,
    loadMembers: fns.loadMembers,
    loadAdminGroups: fns.loadAdminGroups,
    loadAdminHelpdeskCategories: fns.loadAdminHelpdeskCategories,
    loadAuditLogs: fns.loadAuditLogs,
    clearNotificationState: fns.clearNotificationState,
    clearTicketState: fns.clearTicketState,
    clearMemberState: fns.clearMemberState,
    clearBaseDataState: fns.clearBaseDataState,
    clearAdminManagementState: fns.clearAdminManagementState,
    clearAuditLogState: fns.clearAuditLogState
  });

  return { lifecycle, fns, isAdmin, isItOrAdmin, dashboardTab };
}

describe('useDashboardLifecycle', () => {
  it('loads admin data and switches to members tab for admins', async () => {
    const { lifecycle, fns, isAdmin, dashboardTab } = createLifecycle();
    isAdmin.value = true;

    await lifecycle.runAfterLoginLoad();

    expect(fns.loadMyGroups).toHaveBeenCalled();
    expect(fns.loadHelpdeskCategories).toHaveBeenCalled();
    expect(fns.loadTickets).toHaveBeenCalled();
    expect(fns.loadNotifications).toHaveBeenCalled();
    expect(fns.startNotificationPolling).toHaveBeenCalled();
    expect(fns.loadMembers).toHaveBeenCalled();
    expect(fns.loadAdminGroups).toHaveBeenCalled();
    expect(fns.loadAdminHelpdeskCategories).toHaveBeenCalled();
    expect(fns.loadAuditLogs).toHaveBeenCalled();
    expect(dashboardTab.value).toBe('members');
  });

  it('clears all dashboard-related states on clearSession', () => {
    const { lifecycle, fns, dashboardTab } = createLifecycle();
    dashboardTab.value = 'members';

    lifecycle.runClearSessionState();

    expect(fns.stopNotificationPolling).toHaveBeenCalled();
    expect(fns.clearNotificationState).toHaveBeenCalled();
    expect(fns.clearTicketState).toHaveBeenCalled();
    expect(fns.clearMemberState).toHaveBeenCalled();
    expect(fns.clearBaseDataState).toHaveBeenCalled();
    expect(fns.clearAdminManagementState).toHaveBeenCalled();
    expect(fns.clearAuditLogState).toHaveBeenCalled();
    expect(dashboardTab.value).toBe('helpdesk');
  });
});
