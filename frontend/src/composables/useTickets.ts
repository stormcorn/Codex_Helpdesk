import { computed, reactive, ref, type Ref } from 'vue';
import { parseErrorMessage, requestJson } from './useApi';
import { useTicketUiState } from './useTicketUiState';
import type { Member, Ticket } from '../types';

const STATUS_FLOW: Ticket['status'][] = ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'];
const MAX_FILE_BYTES = 5 * 1024 * 1024;

type UseTicketsOptions = {
  token: Ref<string>;
  authHeaders: () => HeadersInit;
  currentMember: Ref<Member | null>;
};

export function useTickets(options: UseTicketsOptions) {
  const ui = useTicketUiState({ token: options.token });

  const submittingTicket = ref(false);
  const tickets = ref<Ticket[]>([]);
  const loadingTickets = ref(false);
  const statusDrafts = reactive<Record<number, Ticket['status']>>({});
  const itActionLoading = reactive<Record<number, boolean>>({});
  const itFeedback = ref('');

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

  function effectiveStatus(ticket: Ticket): Ticket['status'] {
    return ticket.deleted ? 'DELETED' : ticket.status;
  }

  function displayStatus(ticket: Ticket): string {
    return effectiveStatus(ticket);
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

  async function loadTickets(): Promise<void> {
    if (!options.token.value) return;
    loadingTickets.value = true;
    ui.ticketFeedback.value = '';
    try {
      const data = await requestJson<Ticket[]>(
        '/api/helpdesk/tickets',
        { headers: options.authHeaders() },
        '讀取工單失敗'
      );
      tickets.value = data;
      data.forEach((ticket) => {
        statusDrafts[ticket.id] = ticket.status;
        ui.replyInputs[ticket.id] = ui.replyInputs[ticket.id] ?? '';
        if (ui.openTicketIds[ticket.id] === undefined) {
          ui.openTicketIds[ticket.id] = false;
        }
      });
    } catch (e) {
      ui.ticketFeedback.value = e instanceof Error ? e.message : '讀取工單失敗';
      ui.ticketFeedbackType.value = 'error';
    } finally {
      loadingTickets.value = false;
    }
  }

  async function submitTicket(): Promise<void> {
    ui.ticketFeedback.value = '';
    ui.ticketFeedbackType.value = '';

    if (!ui.ticketForm.name || !ui.ticketForm.email || !ui.ticketForm.subject || !ui.ticketForm.description) {
      ui.ticketFeedback.value = '請完整填寫所有欄位。';
      ui.ticketFeedbackType.value = 'error';
      return;
    }

    const oversized = ui.selectedFiles.value.find((file) => file.size >= MAX_FILE_BYTES);
    if (oversized) {
      ui.ticketFeedback.value = `檔案 ${oversized.name} 超過 5MB 限制。`;
      ui.ticketFeedbackType.value = 'error';
      return;
    }

    submittingTicket.value = true;
    try {
      const formData = new FormData();
      formData.append('name', ui.ticketForm.name);
      formData.append('email', ui.ticketForm.email);
      formData.append('subject', ui.ticketForm.subject);
      formData.append('description', ui.ticketForm.description);
      ui.selectedFiles.value.forEach((file) => formData.append('files', file));

      const response = await fetch('/api/helpdesk/tickets', {
        method: 'POST',
        headers: options.authHeaders(),
        body: formData
      });
      if (!response.ok) {
        let parsed: unknown = null;
        try {
          parsed = await response.json();
        } catch {
          // Ignore error response body parse failure.
        }
        throw new Error(parseErrorMessage('送出失敗', parsed));
      }

      const created = (await response.json()) as Ticket;
      tickets.value = [created, ...tickets.value].slice(0, 20);
      statusDrafts[created.id] = created.status;
      ui.replyInputs[created.id] = '';
      ui.openTicketIds[created.id] = false;
      ui.ticketForm.subject = '';
      ui.ticketForm.description = '';
      ui.selectedFiles.value = [];
      ui.ticketFeedback.value = `工單送出成功 #${created.id}`;
      ui.ticketFeedbackType.value = 'success';
    } catch (e) {
      ui.ticketFeedback.value = e instanceof Error ? e.message : '送出失敗';
      ui.ticketFeedbackType.value = 'error';
    } finally {
      submittingTicket.value = false;
    }
  }

  async function updateTicketStatus(ticket: Ticket): Promise<void> {
    const status = statusDrafts[ticket.id];
    if (!status) return;
    itActionLoading[ticket.id] = true;
    itFeedback.value = '';
    try {
      const updated = await requestJson<Ticket>(
        `/api/helpdesk/tickets/${ticket.id}/status`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
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

  function getNextStatus(status: Ticket['status']): Ticket['status'] {
    const idx = STATUS_FLOW.indexOf(status);
    if (idx < 0) return 'OPEN';
    return STATUS_FLOW[(idx + 1) % STATUS_FLOW.length];
  }

  async function quickAdvanceTicketStatus(ticket: Ticket): Promise<void> {
    if (ticket.deleted) return;
    statusDrafts[ticket.id] = getNextStatus(ticket.status);
    await updateTicketStatus(ticket);
  }

  async function sendReply(ticket: Ticket): Promise<void> {
    if (ticket.deleted) return;
    const content = (ui.replyInputs[ticket.id] ?? '').trim();
    if (!content) return;
    itActionLoading[ticket.id] = true;
    itFeedback.value = '';
    try {
      const updated = await requestJson<Ticket>(
        `/api/helpdesk/tickets/${ticket.id}/messages`,
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
          body: JSON.stringify({ content })
        },
        '回覆失敗'
      );
      ui.replyInputs[ticket.id] = '';
      replaceTicket(updated);
    } catch (e) {
      itFeedback.value = e instanceof Error ? e.message : '回覆失敗';
    } finally {
      itActionLoading[ticket.id] = false;
    }
  }

  function replaceTicket(updated: Ticket): void {
    tickets.value = tickets.value.map((ticket) => (ticket.id === updated.id ? updated : ticket));
    statusDrafts[updated.id] = updated.status;
    if (ui.openTicketIds[updated.id] === undefined) {
      ui.openTicketIds[updated.id] = false;
    }
  }

  function canDeleteTicket(ticket: Ticket): boolean {
    const member = options.currentMember.value;
    if (!member || ticket.deleted) return false;
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
        { method: 'PATCH', headers: options.authHeaders() },
        '刪除工單失敗'
      );
      replaceTicket(updated);
    } catch (e) {
      itFeedback.value = e instanceof Error ? e.message : '刪除工單失敗';
    } finally {
      itActionLoading[ticket.id] = false;
    }
  }

  function applyMemberProfile(member: Member): void {
    ui.ticketForm.name = member.name;
    ui.ticketForm.email = member.email;
  }

  function clearTicketState(): void {
    tickets.value = [];
    submittingTicket.value = false;
    itFeedback.value = '';
    Object.keys(statusDrafts).forEach((key) => delete statusDrafts[Number(key)]);
    Object.keys(itActionLoading).forEach((key) => delete itActionLoading[Number(key)]);
    ui.clearTicketUiState();
  }

  return {
    ...ui,
    submittingTicket,
    tickets,
    loadingTickets,
    ticketStats,
    itActionLoading,
    itFeedback,
    effectiveStatus,
    displayStatus,
    loadTickets,
    submitTicket,
    getNextStatus,
    quickAdvanceTicketStatus,
    sendReply,
    canDeleteTicket,
    softDeleteTicket,
    applyMemberProfile,
    clearTicketState
  };
}
