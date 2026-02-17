import type { Ref } from 'vue';
import { parseErrorMessage, requestJson } from './useApi';
import type { HelpdeskCategory, MyGroup, Ticket, TicketForm } from '../types';

type UseTicketsActionsOptions = {
  authHeaders: () => HeadersInit;
  ticketForm: TicketForm;
  selectedFiles: Ref<File[]>;
  submittingTicket: Ref<boolean>;
  ticketFeedback: Ref<string>;
  ticketFeedbackType: Ref<'' | 'success' | 'error'>;
  tickets: Ref<Ticket[]>;
  loadingTickets: Ref<boolean>;
  replyInputs: Record<number, string>;
  statusDrafts: Record<number, Ticket['status']>;
  itActionLoading: Record<number, boolean>;
  itFeedback: Ref<string>;
  openTicketIds: Record<number, boolean>;
  myGroups: Ref<MyGroup[]>;
  helpdeskCategories: Ref<HelpdeskCategory[]>;
  maxFileBytes: number;
  editableStatuses: readonly Ticket['status'][];
  normalizeStatus: (value: unknown) => Ticket['status'];
  normalizeTicket: (ticket: Ticket) => Ticket;
  effectiveStatus: (ticket: Ticket) => Ticket['status'];
  isTicketDeleted: (ticket: Ticket) => boolean;
  canDeleteTicket: (ticket: Ticket) => boolean;
  canSupervisorApprove: (ticket: Ticket) => boolean;
  highlightTicket: (ticketId: number, kind: 'new' | 'jump', durationMs: number) => void;
  replaceTicket: (ticket: Ticket) => void;
};

export function useTicketsActions(options: UseTicketsActionsOptions) {
  async function loadTickets(): Promise<void> {
    options.loadingTickets.value = true;
    options.ticketFeedback.value = '';
    try {
      const previousIds = new Set(options.tickets.value.map((t) => t.id));
      const data = await requestJson<Ticket[]>('/api/helpdesk/tickets', { headers: options.authHeaders() }, '讀取工單失敗');
      options.tickets.value = data.map(options.normalizeTicket);
      options.tickets.value.forEach((t) => {
        if (previousIds.size > 0 && !previousIds.has(t.id)) {
          options.highlightTicket(t.id, 'new', 3000);
        }
        options.statusDrafts[t.id] = options.effectiveStatus(t);
        options.replyInputs[t.id] = options.replyInputs[t.id] ?? '';
        if (options.openTicketIds[t.id] === undefined) {
          options.openTicketIds[t.id] = false;
        }
      });
    } catch (e) {
      options.ticketFeedback.value = e instanceof Error ? e.message : '讀取工單失敗';
      options.ticketFeedbackType.value = 'error';
    } finally {
      options.loadingTickets.value = false;
    }
  }

  async function submitTicket(): Promise<void> {
    options.ticketFeedback.value = '';
    options.ticketFeedbackType.value = '';

    if (!options.ticketForm.name || !options.ticketForm.email || !options.ticketForm.subject || !options.ticketForm.description) {
      options.ticketFeedback.value = '請完整填寫所有欄位。';
      options.ticketFeedbackType.value = 'error';
      return;
    }
    if (!options.ticketForm.groupId) {
      options.ticketFeedback.value = '請選擇工單所屬群組。';
      options.ticketFeedbackType.value = 'error';
      return;
    }
    if (!options.ticketForm.categoryId) {
      options.ticketFeedback.value = '請選擇工單分類。';
      options.ticketFeedbackType.value = 'error';
      return;
    }

    const oversized = options.selectedFiles.value.find((f) => f.size >= options.maxFileBytes);
    if (oversized) {
      options.ticketFeedback.value = `檔案 ${oversized.name} 超過 5MB 限制。`;
      options.ticketFeedbackType.value = 'error';
      return;
    }

    options.submittingTicket.value = true;
    try {
      const formData = new FormData();
      formData.append('name', options.ticketForm.name);
      formData.append('email', options.ticketForm.email);
      formData.append('subject', options.ticketForm.subject);
      formData.append('description', options.ticketForm.description);
      formData.append('groupId', String(options.ticketForm.groupId));
      formData.append('categoryId', String(options.ticketForm.categoryId));
      formData.append('priority', options.ticketForm.priority);
      options.selectedFiles.value.forEach((f) => formData.append('files', f));

      const response = await fetch('/api/helpdesk/tickets', { method: 'POST', headers: options.authHeaders(), body: formData });
      if (!response.ok) {
        let parsed: unknown = null;
        try {
          parsed = await response.json();
        } catch {
          // ignore
        }
        throw new Error(parseErrorMessage('送出失敗', parsed));
      }

      const created = options.normalizeTicket((await response.json()) as Ticket);
      options.tickets.value = [created, ...options.tickets.value].slice(0, 20);
      options.highlightTicket(created.id, 'new', 3000);
      options.statusDrafts[created.id] = options.effectiveStatus(created);
      options.replyInputs[created.id] = '';
      options.openTicketIds[created.id] = false;
      options.ticketForm.subject = '';
      options.ticketForm.description = '';
      options.selectedFiles.value = [];
      options.ticketFeedback.value = `工單送出成功 #${created.id}`;
      options.ticketFeedbackType.value = 'success';
      options.ticketForm.priority = 'GENERAL';
      if (!options.ticketForm.groupId && options.myGroups.value.length) {
        options.ticketForm.groupId = options.myGroups.value[0].id;
      }
      if (!options.ticketForm.categoryId && options.helpdeskCategories.value.length) {
        options.ticketForm.categoryId = options.helpdeskCategories.value[0].id;
      }
    } catch (e) {
      options.ticketFeedback.value = e instanceof Error ? e.message : '送出失敗';
      options.ticketFeedbackType.value = 'error';
    } finally {
      options.submittingTicket.value = false;
    }
  }

  async function updateTicketStatus(ticket: Ticket): Promise<void> {
    if (options.isTicketDeleted(ticket)) return;
    const status = options.normalizeStatus(options.statusDrafts[ticket.id]);
    if (status === 'DELETED' || !options.editableStatuses.includes(status)) return;
    options.itActionLoading[ticket.id] = true;
    options.itFeedback.value = '';
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
      options.replaceTicket(updated);
    } catch (e) {
      options.itFeedback.value = e instanceof Error ? e.message : '更新狀態失敗';
    } finally {
      options.itActionLoading[ticket.id] = false;
    }
  }

  async function sendReply(ticket: Ticket): Promise<void> {
    if (options.isTicketDeleted(ticket)) return;
    const content = (options.replyInputs[ticket.id] ?? '').trim();
    if (!content) return;
    options.itActionLoading[ticket.id] = true;
    options.itFeedback.value = '';
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
      options.replyInputs[ticket.id] = '';
      options.replaceTicket(updated);
    } catch (e) {
      options.itFeedback.value = e instanceof Error ? e.message : '回覆失敗';
    } finally {
      options.itActionLoading[ticket.id] = false;
    }
  }

  async function softDeleteTicket(ticket: Ticket): Promise<void> {
    if (!options.canDeleteTicket(ticket)) return;
    if (!confirm(`確認將工單 #${ticket.id} 標記為刪除？`)) return;
    options.itFeedback.value = '';
    options.itActionLoading[ticket.id] = true;
    try {
      const updated = await requestJson<Ticket>(
        `/api/helpdesk/tickets/${ticket.id}/delete`,
        { method: 'PATCH', headers: options.authHeaders() },
        '刪除工單失敗'
      );
      options.replaceTicket(updated);
    } catch (e) {
      options.itFeedback.value = e instanceof Error ? e.message : '刪除工單失敗';
    } finally {
      options.itActionLoading[ticket.id] = false;
    }
  }

  async function supervisorApproveTicket(ticket: Ticket): Promise<void> {
    if (!options.canSupervisorApprove(ticket)) return;
    options.itFeedback.value = '';
    options.itActionLoading[ticket.id] = true;
    try {
      const updated = await requestJson<Ticket>(
        `/api/helpdesk/tickets/${ticket.id}/supervisor-approve`,
        { method: 'PATCH', headers: options.authHeaders() },
        '主管確認失敗'
      );
      options.replaceTicket(updated);
    } catch (e) {
      options.itFeedback.value = e instanceof Error ? e.message : '主管確認失敗';
    } finally {
      options.itActionLoading[ticket.id] = false;
    }
  }

  return {
    loadTickets,
    submitTicket,
    updateTicketStatus,
    sendReply,
    softDeleteTicket,
    supervisorApproveTicket
  };
}
