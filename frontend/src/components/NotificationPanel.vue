<script setup lang="ts">
import type { NotificationItem } from '../types';

const props = defineProps<{
  open: boolean;
  loading: boolean;
  notifications: NotificationItem[];
  feedback: string;
  onMarkAllRead: () => void;
  onOpenNotification: (item: NotificationItem) => void;
}>();
</script>

<template>
  <section v-if="props.open" class="panel notify-panel">
    <div class="notify-head">
      <h3>通知中心</h3>
      <button type="button" @click="props.onMarkAllRead">全部已讀</button>
    </div>
    <p v-if="props.loading">讀取通知中...</p>
    <p v-else-if="!props.notifications.length">目前沒有通知</p>
    <ul v-else class="simple-list notify-list">
      <li v-for="item in props.notifications" :key="item.id" :class="{ unread: !item.read }">
        <button class="notify-item" type="button" @click="props.onOpenNotification(item)">
          <strong>{{ item.message }}</strong>
          <small>{{ new Date(item.createdAt).toLocaleString() }}</small>
        </button>
      </li>
    </ul>
    <p v-if="props.feedback" class="feedback error">{{ props.feedback }}</p>
  </section>
</template>
