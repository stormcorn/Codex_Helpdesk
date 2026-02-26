<script setup lang="ts">
import { computed } from 'vue';
import type {
  Attachment,
  NumberBooleanMap,
  NumberStringMap,
  NumberTicketStatusMap,
  SortOrder,
  Ticket,
  TicketActiveStatusFilter,
  TicketStats,
  TicketStatusHistory
} from '../types';
import TicketCard from './TicketCard.vue';

const props = defineProps<{
  ticketStats: TicketStats;
  ticketKeyword: string;
  onlyMyTickets: boolean;
  createdTimeSort: SortOrder;
  statusFilter: TicketActiveStatusFilter;
  filteredActiveTickets: Ticket[];
  tickets: Ticket[];
  loadingTickets: boolean;
  openTicketIds: NumberBooleanMap;
  newTicketHighlights: NumberBooleanMap;
  jumpTicketHighlights: NumberBooleanMap;
  isItOrAdmin: boolean;
  itActionLoading: NumberBooleanMap;
  statusDrafts: NumberTicketStatusMap;
  replyInputs: NumberStringMap;
  replyFiles: Record<number, File[]>;
  itFeedback: string;
  onReplyFilesChanged: (ticketId: number, event: Event) => void;
  effectiveStatus: (ticket: Ticket) => Ticket['status'];
  isTicketDeleted: (ticket: Ticket) => boolean;
  canSupervisorApprove: (ticket: Ticket) => boolean;
  canDeleteTicket: (ticket: Ticket) => boolean;
  displayStatus: (ticket: Ticket) => string;
  formatSize: (bytes: number) => string;
  isImageAttachment: (attachment: Attachment) => boolean;
  normalizeStatus: (value: unknown) => Ticket['status'];
  formatStatusTransition: (history: TicketStatusHistory) => string;
}>();

const emit = defineEmits<{
  'update:ticketKeyword': [value: string];
  'update:onlyMyTickets': [value: boolean];
  'update:createdTimeSort': [value: SortOrder];
  'update:statusFilter': [value: TicketActiveStatusFilter];
  toggleTicket: [ticketId: number];
  supervisorApprove: [ticket: Ticket];
  softDelete: [ticket: Ticket];
  updateTicketStatus: [ticket: Ticket];
  openImageLightbox: [ticketId: number, attachment: Attachment];
  downloadAttachment: [ticketId: number, attachment: Attachment];
  sendReply: [ticket: Ticket];
}>();

const ticketKeywordModel = computed({
  get: () => props.ticketKeyword,
  set: (value: string) => emit('update:ticketKeyword', value)
});

const onlyMyTicketsModel = computed({
  get: () => props.onlyMyTickets,
  set: (value: boolean) => emit('update:onlyMyTickets', value)
});

const createdTimeSortModel = computed({
  get: () => props.createdTimeSort,
  set: (value: SortOrder) => emit('update:createdTimeSort', value)
});

const statusFilterModel = computed({
  get: () => props.statusFilter,
  set: (value: TicketActiveStatusFilter) => emit('update:statusFilter', value)
});
</script>

<template>
  <section class="panel">
    <div class="ticket-list-top">
      <h2>進行中工單</h2>
      <div class="ticket-stats">
        <span class="stat-chip">總數 <strong>{{ props.ticketStats.total }}</strong></span>
        <span class="stat-chip">本日新增 <strong>{{ props.ticketStats.todayNew }}</strong></span>
        <span class="stat-chip status-open">OPEN <strong>{{ props.ticketStats.open }}</strong></span>
        <span class="stat-chip status-proceeding">PROCEEDING <strong>{{ props.ticketStats.proceeding }}</strong></span>
        <span class="stat-chip status-pending">PENDING <strong>{{ props.ticketStats.pending }}</strong></span>
        <span class="stat-chip status-closed">CLOSED <strong>{{ props.ticketStats.closed }}</strong></span>
        <span class="stat-chip status-deleted">DELETED <strong>{{ props.ticketStats.deleted }}</strong></span>
      </div>
    </div>
    <div class="ticket-filters">
      <label>
        關鍵字搜尋
        <input v-model="ticketKeywordModel" placeholder="工單編號 / 主旨 / 內容 / 建立人 / Email" />
      </label>
      <label>
        我的工單
        <input v-model="onlyMyTicketsModel" type="checkbox" />
      </label>
      <label>
        依建立時間排序
        <select v-model="createdTimeSortModel">
          <option value="newest">新到舊</option>
          <option value="oldest">舊到新</option>
        </select>
      </label>
      <label>
        依狀態篩選
        <select v-model="statusFilterModel">
          <option value="ALL">全部</option>
          <option value="OPEN">OPEN</option>
          <option value="PROCEEDING">PROCEEDING</option>
          <option value="PENDING">PENDING</option>
        </select>
      </label>
      <small>顯示 {{ props.filteredActiveTickets.length }} / {{ props.tickets.length }} 筆</small>
    </div>
    <p v-if="props.loadingTickets">讀取中...</p>
    <p v-else-if="!props.filteredActiveTickets.length">沒有符合條件的工單</p>
    <ul v-else class="ticket-list">
      <TicketCard
        v-for="ticket in props.filteredActiveTickets"
        :key="ticket.id"
        mode="active"
        :ticket="ticket"
        :is-it-or-admin="props.isItOrAdmin"
        :open="props.openTicketIds[ticket.id]"
        :new-highlight="props.newTicketHighlights[ticket.id]"
        :jump-highlight="props.jumpTicketHighlights[ticket.id]"
        :it-action-loading="props.itActionLoading"
        :status-drafts="props.statusDrafts"
        :reply-inputs="props.replyInputs"
        :reply-files="props.replyFiles"
        :on-reply-files-changed="props.onReplyFilesChanged"
        :effective-status="props.effectiveStatus"
        :is-ticket-deleted="props.isTicketDeleted"
        :can-supervisor-approve="props.canSupervisorApprove"
        :can-delete-ticket="props.canDeleteTicket"
        :display-status="props.displayStatus"
        :format-size="props.formatSize"
        :is-image-attachment="props.isImageAttachment"
        :normalize-status="props.normalizeStatus"
        :format-status-transition="props.formatStatusTransition"
        @toggle-ticket="emit('toggleTicket', $event)"
        @supervisor-approve="emit('supervisorApprove', $event)"
        @soft-delete="emit('softDelete', $event)"
        @update-ticket-status="emit('updateTicketStatus', $event)"
        @open-image-lightbox="(...args) => emit('openImageLightbox', args[0], args[1])"
        @download-attachment="(...args) => emit('downloadAttachment', args[0], args[1])"
        @send-reply="emit('sendReply', $event)"
      />
    </ul>
    <p v-if="props.itFeedback" class="feedback error">{{ props.itFeedback }}</p>
  </section>
</template>
