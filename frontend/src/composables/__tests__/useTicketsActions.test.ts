import { reactive, ref } from 'vue';
import { describe, expect, it, vi, beforeEach } from 'vitest';
import type { Ticket } from '../../types';
import { useTicketsActions } from '../useTicketsActions';

const { requestJsonMock, parseErrorMessageMock } = vi.hoisted(() => ({
  requestJsonMock: vi.fn(),
  parseErrorMessageMock: vi.fn(() => '送出失敗')
}));

vi.mock('../useApi', () => ({
  requestJson: requestJsonMock,
  parseErrorMessage: parseErrorMessageMock
}));

function createOptions() {
  const ticketForm = reactive({
    name: '',
    email: '',
    subject: '',
    description: '',
    priority: 'GENERAL' as const,
    groupId: null as number | null,
    categoryId: null as number | null
  });
  const selectedFiles = ref<File[]>([]);
  const submittingTicket = ref(false);
  const ticketFeedback = ref('');
  const ticketFeedbackType = ref<'' | 'success' | 'error'>('');
  const tickets = ref<Ticket[]>([]);
  const loadingTickets = ref(false);
  const replyInputs = reactive<Record<number, string>>({});
  const statusDrafts = reactive<Record<number, Ticket['status']>>({});
  const itActionLoading = reactive<Record<number, boolean>>({});
  const itFeedback = ref('');
  const openTicketIds = reactive<Record<number, boolean>>({});
  const myGroups = ref<{ id: number; name: string; supervisor: boolean }[]>([]);
  const helpdeskCategories = ref<{ id: number; name: string; createdAt: string }[]>([]);

  return {
    authHeaders: () => ({ Authorization: 'Bearer test' }),
    ticketForm,
    selectedFiles,
    submittingTicket,
    ticketFeedback,
    ticketFeedbackType,
    tickets,
    loadingTickets,
    replyInputs,
    statusDrafts,
    itActionLoading,
    itFeedback,
    openTicketIds,
    myGroups,
    helpdeskCategories,
    maxFileBytes: 5 * 1024 * 1024,
    editableStatuses: ['OPEN', 'PROCEEDING', 'PENDING', 'CLOSED'] as const,
    normalizeStatus: (v: unknown) => String(v) as Ticket['status'],
    normalizeTicket: (t: Ticket) => t,
    effectiveStatus: (t: Ticket) => t.status,
    isTicketDeleted: (t: Ticket) => Boolean(t.deleted || t.deletedAt),
    canDeleteTicket: () => true,
    canSupervisorApprove: () => true,
    highlightTicket: vi.fn(),
    replaceTicket: vi.fn()
  };
}

describe('useTicketsActions', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('validates required fields before submit', async () => {
    const options = createOptions();
    const { submitTicket } = useTicketsActions(options);

    await submitTicket();

    expect(options.ticketFeedback.value).toContain('請完整填寫');
    expect(options.ticketFeedbackType.value).toBe('error');
    expect(options.submittingTicket.value).toBe(false);
  });

  it('skips status update when ticket is deleted', async () => {
    const options = createOptions();
    const { updateTicketStatus } = useTicketsActions(options);
    const deletedTicket = {
      id: 10,
      name: 'A',
      email: 'a@test.com',
      subject: 's',
      description: 'd',
      status: 'DELETED',
      priority: 'GENERAL',
      supervisorApproved: true,
      supervisorApprovedByMemberId: null,
      supervisorApprovedAt: null,
      groupId: null,
      groupName: null,
      categoryId: null,
      categoryName: null,
      createdByMemberId: null,
      deleted: true,
      deletedAt: '2026-01-01T00:00:00Z',
      createdAt: '2026-01-01T00:00:00Z',
      attachments: [],
      messages: [],
      statusHistories: []
    } as Ticket;

    options.isTicketDeleted = () => true;
    await updateTicketStatus(deletedTicket);

    expect(requestJsonMock).not.toHaveBeenCalled();
  });
});
