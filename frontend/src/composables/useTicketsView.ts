import { computed, reactive, ref, type Ref } from 'vue';
import { parseErrorMessage } from './useApi';
import { useStatusFeedback, useTextFeedback } from './useFeedbackState';
import type {
  Attachment,
  HelpdeskCategory,
  Member,
  MyGroup,
  SortOrder,
  Ticket,
  TicketActiveStatusFilter,
  TicketArchiveStatusFilter,
  TicketForm,
  TicketPriority,
  TicketStatusHistory
} from '../types';

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const EDITABLE_STATUSES = ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'] as const;

type UseTicketsViewOptions = {
  authHeaders: () => HeadersInit;
  currentMember: Ref<Member | null>;
  myGroups: Ref<MyGroup[]>;
  helpdeskCategories: Ref<HelpdeskCategory[]>;
};

export function useTicketsView(options: UseTicketsViewOptions) {
  const ticketForm = reactive<TicketForm>({
    name: '',
    email: '',
    subject: '',
    description: '',
    priority: 'GENERAL',
    groupId: null,
    categoryId: null
  });
  const selectedFiles = ref<File[]>([]);
  const submittingTicket = ref(false);
  const { feedback: ticketFeedback, feedbackType: ticketFeedbackType } = useStatusFeedback();
  const tickets = ref<Ticket[]>([]);
  const loadingTickets = ref(false);
  const ticketKeyword = ref('');
  const onlyMyTickets = ref(false);
  const createdTimeSort = ref<SortOrder>('newest');
  const statusFilter = ref<TicketActiveStatusFilter>('ALL');
  const archiveStatusFilter = ref<TicketArchiveStatusFilter>('ALL');

  const replyInputs = reactive<Record<number, string>>({});
  const replyFiles = reactive<Record<number, File[]>>({});
  const statusDrafts = reactive<Record<number, Ticket['status']>>({});
  const itActionLoading = reactive<Record<number, boolean>>({});
  const { feedback: itFeedback, clearFeedback: clearItFeedback } = useTextFeedback();
  const openTicketIds = reactive<Record<number, boolean>>({});
  const newTicketHighlights = reactive<Record<number, boolean>>({});
  const jumpTicketHighlights = reactive<Record<number, boolean>>({});
  const ticketHighlightTimers = reactive<Record<string, number>>({});

  const lightboxOpen = ref(false);
  const lightboxSrc = ref('');
  const lightboxTitle = ref('');
  let lightboxObjectUrl: string | null = null;

  function normalizeStatus(value: unknown): Ticket['status'] {
    const normalized = String(value ?? '').trim().toUpperCase();
    if (normalized === 'DELETED') return 'DELETED';
    if (normalized === 'OPEN') return 'OPEN';
    if (normalized === 'PROCEEDING') return 'PROCEEDING';
    if (normalized === 'PENDING') return 'PENDING';
    if (normalized === 'CLOSED') return 'CLOSED';
    return 'OPEN';
  }

  function normalizePriority(value: unknown): TicketPriority {
    return String(value ?? '').trim().toUpperCase() === 'URGENT' ? 'URGENT' : 'GENERAL';
  }

  function effectiveStatus(ticket: Ticket): Ticket['status'] {
    if (ticket.deleted || ticket.deletedAt) return 'DELETED';
    return normalizeStatus(ticket.status);
  }

  function isTicketDeleted(ticket: Ticket): boolean {
    return effectiveStatus(ticket) === 'DELETED';
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

  const baseFilteredTickets = computed(() => {
    const keyword = ticketKeyword.value.trim().toLowerCase();
    const memberId = options.currentMember.value?.id ?? null;

    return [...tickets.value]
      .filter((ticket) => {
        if (onlyMyTickets.value && (!memberId || ticket.createdByMemberId !== memberId)) {
          return false;
        }
        if (!keyword) return true;

        const haystack = [
          String(ticket.id),
          ticket.subject,
          ticket.description,
          ticket.name,
          ticket.email,
          ticket.groupName ?? '',
          ticket.categoryName ?? '',
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

  const filteredActiveTickets = computed(() =>
    baseFilteredTickets.value.filter((ticket) => {
      const status = effectiveStatus(ticket);
      if (status === 'CLOSED' || status === 'DELETED') return false;
      if (statusFilter.value === 'ALL') return true;
      return status === statusFilter.value;
    })
  );

  const filteredArchivedTickets = computed(() =>
    baseFilteredTickets.value.filter((ticket) => {
      const status = effectiveStatus(ticket);
      if (status !== 'CLOSED' && status !== 'DELETED') return false;
      if (archiveStatusFilter.value === 'ALL') return true;
      return status === archiveStatusFilter.value;
    })
  );

  function formatSize(bytes: number): string {
    return `${(bytes / 1024 / 1024).toFixed(2)} MB`;
  }

  function displayStatus(ticket: Ticket): string {
    return effectiveStatus(ticket);
  }

  function normalizeTicket(ticket: Ticket): Ticket {
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
      categoryId: ticket.categoryId ?? null,
      categoryName: ticket.categoryName ?? null,
      statusHistories: Array.isArray(ticket.statusHistories) ? ticket.statusHistories : []
    };
  }

  function isCurrentMemberSupervisorOfGroup(groupId: number | null): boolean {
    if (!groupId) return false;
    return options.myGroups.value.some((g) => g.id === groupId && g.supervisor);
  }

  function formatStatusTransition(history: TicketStatusHistory): string {
    const toStatus = normalizeStatus(history.toStatus);
    if (history.fromStatus && normalizeStatus(history.fromStatus) === toStatus) {
      return `主管已確認（${toStatus}）`;
    }
    if (!history.fromStatus) {
      return `初始化為 ${toStatus}`;
    }
    return `${normalizeStatus(history.fromStatus)} → ${toStatus}`;
  }

  function isImageAttachment(attachment: Attachment): boolean {
    return attachment.contentType.startsWith('image/');
  }

  function extractFilenameFromDisposition(disposition: string | null): string | null {
    if (!disposition) return null;
    const utf8Match = disposition.match(/filename\*=UTF-8''([^;]+)/i);
    if (utf8Match?.[1]) {
      try {
        return decodeURIComponent(utf8Match[1]);
      } catch {
        // ignore decode issue
      }
    }
    const plainMatch = disposition.match(/filename="([^"]+)"/i);
    return plainMatch?.[1] ?? null;
  }

  async function fetchAttachmentBlob(ticketId: number, attachmentId: number, action: 'view' | 'download'): Promise<{
    blob: Blob;
    filename: string;
  }> {
    const response = await fetch(`/api/helpdesk/tickets/${ticketId}/attachments/${attachmentId}/${action}`, {
      headers: options.authHeaders()
    });
    if (!response.ok) {
      let parsed: unknown = null;
      try {
        parsed = await response.json();
      } catch {
        // ignore
      }
      throw new Error(parseErrorMessage('讀取附件失敗', parsed));
    }
    const blob = await response.blob();
    const filename = extractFilenameFromDisposition(response.headers.get('Content-Disposition')) ?? `attachment-${attachmentId}`;
    return { blob, filename };
  }

  async function openImageLightbox(ticketId: number, attachment: Attachment): Promise<void> {
    try {
      const { blob } = await fetchAttachmentBlob(ticketId, attachment.id, 'view');
      if (lightboxObjectUrl) {
        URL.revokeObjectURL(lightboxObjectUrl);
      }
      lightboxObjectUrl = URL.createObjectURL(blob);
      lightboxSrc.value = lightboxObjectUrl;
      lightboxTitle.value = attachment.originalFilename;
      lightboxOpen.value = true;
    } catch (e) {
      itFeedback.value = e instanceof Error ? e.message : '讀取附件失敗';
    }
  }

  async function downloadAttachment(ticketId: number, attachment: Attachment): Promise<void> {
    try {
      const { blob, filename } = await fetchAttachmentBlob(ticketId, attachment.id, 'download');
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = filename || attachment.originalFilename;
      anchor.click();
      URL.revokeObjectURL(objectUrl);
    } catch (e) {
      itFeedback.value = e instanceof Error ? e.message : '下載附件失敗';
    }
  }

  function closeLightbox(): void {
    if (lightboxObjectUrl) {
      URL.revokeObjectURL(lightboxObjectUrl);
      lightboxObjectUrl = null;
    }
    lightboxOpen.value = false;
    lightboxSrc.value = '';
    lightboxTitle.value = '';
  }

  function clearTicketHighlights(): void {
    Object.values(ticketHighlightTimers).forEach((timerId) => window.clearTimeout(timerId));
    Object.keys(ticketHighlightTimers).forEach((k) => delete ticketHighlightTimers[k]);
    Object.keys(newTicketHighlights).forEach((k) => delete newTicketHighlights[Number(k)]);
    Object.keys(jumpTicketHighlights).forEach((k) => delete jumpTicketHighlights[Number(k)]);
  }

  function highlightTicket(ticketId: number, kind: 'new' | 'jump', durationMs: number): void {
    const key = `${kind}-${ticketId}`;
    const previousTimer = ticketHighlightTimers[key];
    if (previousTimer) {
      window.clearTimeout(previousTimer);
    }

    if (kind === 'new') {
      newTicketHighlights[ticketId] = true;
    } else {
      jumpTicketHighlights[ticketId] = true;
    }

    ticketHighlightTimers[key] = window.setTimeout(() => {
      if (kind === 'new') {
        delete newTicketHighlights[ticketId];
      } else {
        delete jumpTicketHighlights[ticketId];
      }
      delete ticketHighlightTimers[key];
    }, durationMs);
  }

  function onFilesChanged(event: Event): void {
    const input = event.target as HTMLInputElement;
    selectedFiles.value = Array.from(input.files ?? []);
  }

  function onReplyFilesChanged(ticketId: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    replyFiles[ticketId] = Array.from(input.files ?? []);
  }

  function replaceTicket(updated: Ticket): void {
    const normalized = normalizeTicket(updated);
    tickets.value = tickets.value.map((t) => (t.id === normalized.id ? normalized : t));
    statusDrafts[normalized.id] = effectiveStatus(normalized);
    if (openTicketIds[normalized.id] === undefined) {
      openTicketIds[normalized.id] = false;
    }
  }

  function toggleTicket(ticketId: number): void {
    openTicketIds[ticketId] = !openTicketIds[ticketId];
  }

  function canDeleteTicket(ticket: Ticket): boolean {
    const member = options.currentMember.value;
    if (!member || isTicketDeleted(ticket)) return false;
    if (member.role === 'ADMIN' || member.role === 'IT') return true;
    return ticket.createdByMemberId === member.id;
  }

  function canSupervisorApprove(ticket: Ticket): boolean {
    return Boolean(
      ticket.priority === 'URGENT' &&
      isCurrentMemberSupervisorOfGroup(ticket.groupId) &&
      !ticket.supervisorApproved &&
      !isTicketDeleted(ticket)
    );
  }

  function applyMemberProfile(member: Member): void {
    ticketForm.name = member.name;
    ticketForm.email = member.email;
    ticketForm.groupId = null;
    ticketForm.categoryId = null;
  }

  function clearTicketState(): void {
    clearTicketHighlights();
    closeLightbox();
    tickets.value = [];
    clearItFeedback();
    ticketForm.name = '';
    ticketForm.email = '';
    ticketForm.groupId = null;
    ticketForm.categoryId = null;
    ticketForm.priority = 'GENERAL';
    selectedFiles.value = [];
    Object.keys(replyInputs).forEach((key) => delete replyInputs[Number(key)]);
    Object.keys(replyFiles).forEach((key) => delete replyFiles[Number(key)]);
    Object.keys(statusDrafts).forEach((key) => delete statusDrafts[Number(key)]);
    Object.keys(itActionLoading).forEach((key) => delete itActionLoading[Number(key)]);
    Object.keys(openTicketIds).forEach((key) => delete openTicketIds[Number(key)]);
  }

  return {
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
    normalizePriority,
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
    clearTicketHighlights,
    highlightTicket,
    onFilesChanged,
    onReplyFilesChanged,
    replaceTicket,
    toggleTicket,
    canDeleteTicket,
    canSupervisorApprove,
    applyMemberProfile,
    clearTicketState
  };
}
