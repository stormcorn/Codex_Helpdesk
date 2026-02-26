<script setup lang="ts">
import { computed } from 'vue';
import type {
  Attachment,
  NumberBooleanMap,
  NumberStringMap,
  NumberTicketStatusMap,
  SortOrder,
  Ticket,
  TicketArchiveStatusFilter,
  TicketStatusHistory
} from '../types';
import TicketCard from './TicketCard.vue';

const props = defineProps<{
  ticketKeyword: string;
  onlyMyTickets: boolean;
  createdTimeSort: SortOrder;
  archiveStatusFilter: TicketArchiveStatusFilter;
  filteredArchivedTickets: Ticket[];
  tickets: Ticket[];
  loadingTickets: boolean;
  openTicketIds: NumberBooleanMap;
  newTicketHighlights: NumberBooleanMap;
  jumpTicketHighlights: NumberBooleanMap;
  effectiveStatus: (ticket: Ticket) => Ticket['status'];
  isTicketDeleted: (ticket: Ticket) => boolean;
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
  'update:archiveStatusFilter': [value: TicketArchiveStatusFilter];
  toggleTicket: [ticketId: number];
  openImageLightbox: [ticketId: number, attachment: Attachment];
  downloadAttachment: [ticketId: number, attachment: Attachment];
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

const archiveStatusFilterModel = computed({
  get: () => props.archiveStatusFilter,
  set: (value: TicketArchiveStatusFilter) => emit('update:archiveStatusFilter', value)
});

const emptyBoolRecord: NumberBooleanMap = {};
const emptyStatusRecord: NumberTicketStatusMap = {};
const emptyStringRecord: NumberStringMap = {};
const emptyReplyFiles: Record<number, File[]> = {};
const noSupervisorApprove = () => false;
const noDeletePermission = () => false;
const noopReplyFilesChanged = () => {};
</script>

<template>
  <section class="panel">
    <div class="ticket-list-top">
      <h2>封存工單</h2>
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
        <select v-model="archiveStatusFilterModel">
          <option value="ALL">全部</option>
          <option value="CLOSED">CLOSED</option>
          <option value="DELETED">DELETED</option>
        </select>
      </label>
      <small>顯示 {{ props.filteredArchivedTickets.length }} / {{ props.tickets.length }} 筆</small>
    </div>
    <p v-if="props.loadingTickets">讀取中...</p>
    <p v-else-if="!props.filteredArchivedTickets.length">目前沒有封存工單</p>
    <ul v-else class="ticket-list">
      <TicketCard
        v-for="ticket in props.filteredArchivedTickets"
        :key="ticket.id"
        mode="archive"
        :ticket="ticket"
        :is-it-or-admin="false"
        :open="props.openTicketIds[ticket.id]"
        :new-highlight="props.newTicketHighlights[ticket.id]"
        :jump-highlight="props.jumpTicketHighlights[ticket.id]"
        :it-action-loading="emptyBoolRecord"
        :status-drafts="emptyStatusRecord"
        :reply-inputs="emptyStringRecord"
        :reply-files="emptyReplyFiles"
        :on-reply-files-changed="noopReplyFilesChanged"
        :effective-status="props.effectiveStatus"
        :is-ticket-deleted="props.isTicketDeleted"
        :can-supervisor-approve="noSupervisorApprove"
        :can-delete-ticket="noDeletePermission"
        :display-status="props.displayStatus"
        :format-size="props.formatSize"
        :is-image-attachment="props.isImageAttachment"
        :normalize-status="props.normalizeStatus"
        :format-status-transition="props.formatStatusTransition"
        @toggle-ticket="emit('toggleTicket', $event)"
        @open-image-lightbox="(...args) => emit('openImageLightbox', args[0], args[1])"
        @download-attachment="(...args) => emit('downloadAttachment', args[0], args[1])"
      />
    </ul>
  </section>
</template>
