<script setup lang="ts">
import type { TicketStatusHistory } from '../types';
import { formatDateTime } from '../utils/datetime';

const props = defineProps<{
  statusHistories: TicketStatusHistory[];
  normalizeStatus: (value: unknown) => string;
  formatStatusTransition: (history: TicketStatusHistory) => string;
}>();
</script>

<template>
  <div class="status-history-box">
    <h4>狀態歷程</h4>
    <ul v-if="props.statusHistories.length" class="simple-list status-history-list">
      <li v-for="history in props.statusHistories" :key="history.id">
        <span :class="['status-tag', `status-${props.normalizeStatus(history.toStatus).toLowerCase()}`]">
          {{ props.normalizeStatus(history.toStatus) }}
        </span>
        <small>
          {{ props.formatStatusTransition(history) }} ·
          {{ history.changedByRole }} {{ history.changedByName }} ({{ history.changedByEmployeeId }}) ·
          {{ formatDateTime(history.createdAt) }}
        </small>
      </li>
    </ul>
    <small v-else>尚無狀態變更紀錄</small>
  </div>
</template>
