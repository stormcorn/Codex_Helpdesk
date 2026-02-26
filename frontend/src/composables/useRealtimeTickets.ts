import { Client } from '@stomp/stompjs';
import type { Ref } from 'vue';

type TicketRealtimeEvent = {
  type: string;
  ticketId: number;
  actorMemberId?: number | null;
  at: string;
};

type UseRealtimeTicketsOptions = {
  isAuthenticated: Ref<boolean>;
  currentMemberId: Ref<number | null>;
  loadTickets: () => Promise<void>;
  loadNotifications: () => Promise<void>;
  highlightTicket: (ticketId: number, kind: 'new' | 'jump', durationMs: number) => void;
};

export function useRealtimeTickets(options: UseRealtimeTicketsOptions) {
  let client: Client | null = null;
  let refreshTimer: number | null = null;
  let pendingTicketId: number | null = null;

  function wsUrl(): string {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    return `${protocol}//${window.location.host}/ws`;
  }

  function scheduleRefresh(ticketId: number): void {
    pendingTicketId = ticketId;
    if (refreshTimer) {
      window.clearTimeout(refreshTimer);
    }
    refreshTimer = window.setTimeout(async () => {
      const highlightId = pendingTicketId;
      pendingTicketId = null;
      refreshTimer = null;
      try {
        await Promise.all([options.loadTickets(), options.loadNotifications()]);
        if (highlightId) {
          options.highlightTicket(highlightId, 'jump', 3000);
        }
      } catch {
        // ignore realtime refresh failures; polling/manual reload still exists
      }
    }, 600);
  }

  function connect(): void {
    if (!options.isAuthenticated.value || client?.active) return;
    client = new Client({
      brokerURL: wsUrl(),
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000
    });

    client.onConnect = () => {
      client?.subscribe('/topic/tickets', (message) => {
        let payload: TicketRealtimeEvent | null = null;
        try {
          payload = JSON.parse(message.body) as TicketRealtimeEvent;
        } catch {
          return;
        }
        if (!payload?.ticketId) return;
        if (payload.actorMemberId && payload.actorMemberId === options.currentMemberId.value) {
          return;
        }
        scheduleRefresh(payload.ticketId);
      });
    };

    client.onStompError = () => {
      // no-op; reconnect is enabled
    };

    client.activate();
  }

  function disconnect(): void {
    if (refreshTimer) {
      window.clearTimeout(refreshTimer);
      refreshTimer = null;
    }
    pendingTicketId = null;
    if (client) {
      client.deactivate();
      client = null;
    }
  }

  return {
    connectRealtimeTickets: connect,
    disconnectRealtimeTickets: disconnect
  };
}
