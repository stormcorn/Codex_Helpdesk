<script setup lang="ts">
import type { Attachment, Ticket, TicketStatusHistory } from '../types';
import TicketAttachments from './TicketAttachments.vue';
import TicketStatusHistoryPanel from './TicketStatusHistory.vue';

const props = defineProps<{
  ticket: Ticket;
  mode: 'active' | 'archive';
  isItOrAdmin: boolean;
  open: boolean;
  newHighlight: boolean;
  jumpHighlight: boolean;
  itActionLoading: Record<number, boolean>;
  statusDrafts: Record<number, Ticket['status']>;
  replyInputs: Record<number, string>;
  effectiveStatus: (ticket: Ticket) => Ticket['status'];
  isTicketDeleted: (ticket: Ticket) => boolean;
  canSupervisorApprove: (ticket: Ticket) => boolean;
  canDeleteTicket: (ticket: Ticket) => boolean;
  displayStatus: (ticket: Ticket) => string;
  isImageAttachment: (attachment: Attachment) => boolean;
  normalizeStatus: (value: unknown) => Ticket['status'];
  formatStatusTransition: (history: TicketStatusHistory) => string;
}>();

const emit = defineEmits<{
  toggleTicket: [ticketId: number];
  supervisorApprove: [ticket: Ticket];
  softDelete: [ticket: Ticket];
  updateTicketStatus: [ticket: Ticket];
  openImageLightbox: [ticketId: number, attachment: Attachment];
  downloadAttachment: [ticketId: number, attachment: Attachment];
  sendReply: [ticket: Ticket];
}>();
</script>

<template>
  <li
    :id="`ticket-${props.ticket.id}`"
    :class="[
      'ticket-card',
      `ticket-card-${props.effectiveStatus(props.ticket).toLowerCase()}`,
      {
        expanded: props.open,
        'new-ticket-highlight': props.newHighlight,
        'jump-ticket-highlight': props.jumpHighlight
      }
    ]"
  >
    <div class="ticket-head">
      <button class="ticket-toggle" type="button" @click="emit('toggleTicket', props.ticket.id)">
        <strong :class="{ 'deleted-title': props.isTicketDeleted(props.ticket) }">#{{ props.ticket.id }} {{ props.ticket.subject }}</strong>
        <small>{{ props.open ? '收合' : '展開' }}</small>
      </button>
      <small :class="['ticket-meta', { 'deleted-meta': props.isTicketDeleted(props.ticket) }]">
        {{ props.ticket.name }} · {{ new Date(props.ticket.createdAt).toLocaleString() }}
        <template v-if="props.ticket.groupName"> · 群組 {{ props.ticket.groupName }}</template>
        <template v-if="props.ticket.categoryName"> · 分類 {{ props.ticket.categoryName }}</template>
      </small>
      <span :class="['priority-tag', `priority-${props.ticket.priority.toLowerCase()}`]">
        {{ props.ticket.priority === 'URGENT' ? '急件' : '一般' }}
      </span>
      <span
        v-if="props.ticket.priority === 'URGENT'"
        :class="['approval-tag', props.ticket.supervisorApproved ? 'approved' : 'pending']"
      >
        {{ props.ticket.supervisorApproved ? '主管已確認' : '需主管確認' }}
      </span>
      <span v-if="props.isTicketDeleted(props.ticket)" class="deleted-badge" aria-label="已刪除工單">🗑 已刪除</span>

      <template v-if="props.mode === 'active'">
        <button
          v-if="props.canSupervisorApprove(props.ticket)"
          class="supervisor-approve-btn"
          type="button"
          :disabled="props.itActionLoading[props.ticket.id]"
          @click="emit('supervisorApprove', props.ticket)"
        >
          主管確認
        </button>
        <button
          v-if="props.canDeleteTicket(props.ticket)"
          class="danger ticket-delete-btn"
          type="button"
          :disabled="props.itActionLoading[props.ticket.id] || props.isTicketDeleted(props.ticket)"
          @click="emit('softDelete', props.ticket)"
        >
          {{ props.isTicketDeleted(props.ticket) ? '已刪除' : '刪除' }}
        </button>
        <small v-if="props.isItOrAdmin" class="status-hint">狀態</small>
        <select
          v-if="props.isItOrAdmin && !props.isTicketDeleted(props.ticket)"
          v-model="props.statusDrafts[props.ticket.id]"
          :class="['status-select', `status-${props.effectiveStatus(props.ticket).toLowerCase()}`]"
          :disabled="props.itActionLoading[props.ticket.id]"
          @change="emit('updateTicketStatus', props.ticket)"
        >
          <option value="OPEN">OPEN</option>
          <option value="PROCEEDING">PROCEEDING</option>
          <option value="PENDING">PENDING</option>
          <option value="CLOSED">CLOSED</option>
        </select>
      </template>

      <span v-if="props.mode === 'archive' || !props.isItOrAdmin || props.isTicketDeleted(props.ticket)" :class="['status-tag', `status-${props.effectiveStatus(props.ticket).toLowerCase()}`]">{{ props.displayStatus(props.ticket) }}</span>
    </div>

    <Transition name="ticket-expand">
      <div v-if="props.open" class="ticket-content">
        <p :class="{ 'deleted-content': props.isTicketDeleted(props.ticket) }">{{ props.ticket.description }}</p>
        <small>{{ props.ticket.email }}</small>
        <small v-if="props.ticket.deletedAt"> · 已刪除於 {{ new Date(props.ticket.deletedAt).toLocaleString() }}</small>

        <TicketAttachments
          :ticket-id="props.ticket.id"
          :attachments="props.ticket.attachments"
          :is-image-attachment="props.isImageAttachment"
          @open-image-lightbox="(...args) => emit('openImageLightbox', args[0], args[1])"
          @download-attachment="(...args) => emit('downloadAttachment', args[0], args[1])"
        />

        <div class="message-box">
          <h4>工單訊息</h4>
          <ul class="simple-list">
            <li v-for="msg in props.ticket.messages" :key="msg.id">
              <strong>[{{ msg.authorRole }}] {{ msg.authorName }}</strong>：{{ msg.content }}
              <small> · {{ new Date(msg.createdAt).toLocaleString() }}</small>
            </li>
          </ul>
        </div>

        <TicketStatusHistoryPanel
          :status-histories="props.ticket.statusHistories"
          :normalize-status="props.normalizeStatus"
          :format-status-transition="props.formatStatusTransition"
        />

        <div v-if="props.mode === 'active' && props.isItOrAdmin" class="it-actions">
          <div class="row">
            <input v-model="props.replyInputs[props.ticket.id]" placeholder="輸入回覆訊息" />
            <button :disabled="props.itActionLoading[props.ticket.id] || props.isTicketDeleted(props.ticket)" @click="emit('sendReply', props.ticket)">送出回覆</button>
          </div>
        </div>
      </div>
    </Transition>
  </li>
</template>
