<script setup lang="ts">
import type { Attachment, Ticket, TicketStats } from '../types';

const props = defineProps<{
  open: boolean;
  loadingTickets: boolean;
  tickets: Ticket[];
  ticketStats: TicketStats;
  isItOrAdmin: boolean;
  openTicketIds: Record<number, boolean>;
  itActionLoading: Record<number, boolean>;
  replyInputs: Record<number, string>;
  itFeedback: string;
  effectiveStatus: (ticket: Ticket) => Ticket['status'];
  displayStatus: (ticket: Ticket) => string;
  getNextStatus: (status: Ticket['status']) => Ticket['status'];
  canDeleteTicket: (ticket: Ticket) => boolean;
  onToggleTicket: (ticketId: number) => void;
  onSoftDeleteTicket: (ticket: Ticket) => void;
  onQuickAdvanceTicketStatus: (ticket: Ticket) => void;
  isImageAttachment: (attachment: Attachment) => boolean;
  onOpenImageLightbox: (ticketId: number, attachment: Attachment) => void;
  attachmentDownloadUrl: (ticketId: number, attachmentId: number) => string;
  onSendReply: (ticket: Ticket) => void;
  onReplyInput: (ticketId: number, value: string) => void;
}>();

function handleReplyInput(ticketId: number, event: Event): void {
  const target = event.target as HTMLInputElement | null;
  props.onReplyInput(ticketId, target?.value ?? '');
}
</script>

<template>
  <section v-if="props.open" class="panel">
    <div class="ticket-list-top">
      <h2>工單列表</h2>
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
    <p v-if="props.loadingTickets">讀取中...</p>
    <ul v-else class="ticket-list">
      <li v-for="ticket in props.tickets" :id="`ticket-${ticket.id}`" :key="ticket.id">
        <div class="ticket-head">
          <button class="ticket-toggle" type="button" @click="props.onToggleTicket(ticket.id)">
            <strong :class="{ deleted: ticket.deleted }">#{{ ticket.id }} {{ ticket.subject }}</strong>
            <small>{{ props.openTicketIds[ticket.id] ? '收合' : '展開' }}</small>
          </button>
          <button
            v-if="props.canDeleteTicket(ticket)"
            class="danger ticket-delete-btn"
            type="button"
            :disabled="props.itActionLoading[ticket.id] || ticket.deleted"
            @click="props.onSoftDeleteTicket(ticket)"
          >
            {{ ticket.deleted ? '已刪除' : '刪除' }}
          </button>
          <small v-if="props.isItOrAdmin" class="status-hint">點擊狀態標籤可快速切換</small>
          <button
            v-if="props.isItOrAdmin"
            :class="['status-tag', 'status-button', `status-${props.effectiveStatus(ticket).toLowerCase()}`]"
            :disabled="props.itActionLoading[ticket.id] || ticket.deleted"
            :title="`點擊切換狀態（下一步：${props.getNextStatus(ticket.status)}）`"
            type="button"
            @click="props.onQuickAdvanceTicketStatus(ticket)"
          >
            {{ props.displayStatus(ticket) }}
          </button>
          <span v-else :class="['status-tag', `status-${props.effectiveStatus(ticket).toLowerCase()}`]">{{ props.displayStatus(ticket) }}</span>
        </div>
        <div v-if="props.openTicketIds[ticket.id]" class="ticket-content">
          <p :class="{ deleted: ticket.deleted }">{{ ticket.description }}</p>
          <small>{{ ticket.name }} ({{ ticket.email }}) · {{ new Date(ticket.createdAt).toLocaleString() }}</small>
          <small v-if="ticket.deletedAt"> · 已刪除於 {{ new Date(ticket.deletedAt).toLocaleString() }}</small>

          <ul v-if="ticket.attachments.length" class="simple-list">
            <li v-for="att in ticket.attachments" :key="att.id">
              <template v-if="props.isImageAttachment(att)">
                <button class="link-button" type="button" @click="props.onOpenImageLightbox(ticket.id, att)">預覽 {{ att.originalFilename }}</button>
              </template>
              <template v-else>
                <a :href="props.attachmentDownloadUrl(ticket.id, att.id)" target="_blank" rel="noopener">下載 {{ att.originalFilename }}</a>
              </template>
            </li>
          </ul>

          <div class="message-box">
            <h4>工單訊息</h4>
            <ul class="simple-list">
              <li v-for="msg in ticket.messages" :key="msg.id">
                <strong>[{{ msg.authorRole }}] {{ msg.authorName }}</strong>：{{ msg.content }}
                <small> · {{ new Date(msg.createdAt).toLocaleString() }}</small>
              </li>
            </ul>
          </div>

          <div v-if="props.isItOrAdmin" class="it-actions">
            <div class="row">
              <input
                :value="props.replyInputs[ticket.id] ?? ''"
                placeholder="輸入回覆訊息"
                @input="handleReplyInput(ticket.id, $event)"
              />
              <button :disabled="props.itActionLoading[ticket.id] || ticket.deleted" @click="props.onSendReply(ticket)">送出回覆</button>
            </div>
          </div>
        </div>
      </li>
    </ul>
    <p v-if="props.itFeedback" class="feedback error">{{ props.itFeedback }}</p>
  </section>
</template>
