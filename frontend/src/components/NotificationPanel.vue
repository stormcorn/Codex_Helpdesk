<script setup lang="ts">
import type { DashboardContext, NotificationItem } from '../types';
import { formatDateTime } from '../utils/datetime';

const props = defineProps<{
  context: DashboardContext;
  notificationLoading: boolean;
  notifications: NotificationItem[];
  notificationFeedback: string;
}>();

const emit = defineEmits<{
  markAllRead: [];
  openNotification: [item: NotificationItem];
}>();
</script>

<template>
  <section v-if="props.context.notificationsOpen" class="panel notify-panel">
    <div class="notify-head">
      <h3>通知中心</h3>
      <button type="button" @click="emit('markAllRead')">全部已讀</button>
    </div>
    <p v-if="props.notificationLoading">讀取通知中...</p>
    <p v-else-if="!props.notifications.length">目前沒有通知</p>
    <ul v-else class="simple-list notify-list">
      <li v-for="item in props.notifications" :key="item.id" :class="{ unread: !item.read }">
        <button class="notify-item" type="button" @click="emit('openNotification', item)">
          <strong>{{ item.message }}</strong>
          <small>{{ formatDateTime(item.createdAt) }}</small>
        </button>
      </li>
    </ul>
    <p v-if="props.notificationFeedback" class="feedback error">{{ props.notificationFeedback }}</p>
  </section>
</template>
