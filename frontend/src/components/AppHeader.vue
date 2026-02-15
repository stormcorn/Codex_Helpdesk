<script setup lang="ts">
import type { Member } from '../types';

const props = defineProps<{
  currentMember: Member | null;
  unreadCount: number;
  notificationsOpen: boolean;
  onToggleNotifications: () => void;
  onLogout: () => void;
}>();
</script>

<template>
  <section class="header-panel">
    <div>
      <h1>{{ props.currentMember?.name }}</h1>
      <p>{{ props.currentMember?.employeeId }} · {{ props.currentMember?.role }}</p>
    </div>
    <div class="header-actions">
      <button class="notify-toggle" type="button" @click="props.onToggleNotifications">
        通知
        <span v-if="props.unreadCount > 0" class="notify-badge">{{ props.unreadCount }}</span>
      </button>
      <button @click="props.onLogout">登出</button>
    </div>
  </section>
</template>
