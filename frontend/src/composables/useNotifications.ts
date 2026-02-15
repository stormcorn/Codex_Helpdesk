import { ref, type Ref } from 'vue';
import { requestJson } from './useApi';
import type { NotificationItem } from '../types';

type NotificationListResponse = {
  notifications: NotificationItem[];
  unreadCount: number;
};

type UseNotificationsOptions = {
  token: Ref<string>;
  authHeaders: () => HeadersInit;
  onNavigateToTicket: (ticketId: number) => Promise<void> | void;
};

export function useNotifications(options: UseNotificationsOptions) {
  const notifications = ref<NotificationItem[]>([]);
  const unreadCount = ref(0);
  const notificationsOpen = ref(false);
  const notificationLoading = ref(false);
  const notificationFeedback = ref('');
  let notificationTimer: number | null = null;

  async function loadNotifications(silent = false): Promise<void> {
    if (!options.token.value) return;
    if (!silent) notificationLoading.value = true;
    notificationFeedback.value = '';
    try {
      const data = await requestJson<NotificationListResponse>(
        '/api/notifications',
        { headers: options.authHeaders() },
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
      await fetch(`/api/notifications/${notificationId}/read`, { method: 'PATCH', headers: options.authHeaders() });
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
      await requestJson('/api/notifications/read-all', { method: 'PATCH', headers: options.authHeaders() }, '全部已讀失敗');
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
      await options.onNavigateToTicket(item.ticketId);
    }
    notificationsOpen.value = false;
  }

  function clearNotificationState(): void {
    notifications.value = [];
    unreadCount.value = 0;
    notificationsOpen.value = false;
    notificationFeedback.value = '';
  }

  return {
    notifications,
    unreadCount,
    notificationsOpen,
    notificationLoading,
    notificationFeedback,
    loadNotifications,
    markNotificationRead,
    markAllNotificationsRead,
    startNotificationPolling,
    stopNotificationPolling,
    openNotification,
    clearNotificationState
  };
}
