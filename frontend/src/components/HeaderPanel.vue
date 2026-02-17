<script setup lang="ts">
import type { DashboardContext, Member } from '../types';

const props = defineProps<{
  currentMember: Member | null;
  context: DashboardContext;
}>();

const emit = defineEmits<{
  toggleNotifications: [];
  logout: [];
}>();
</script>

<template>
  <section class="header-panel">
    <div>
      <h1>{{ props.currentMember?.name }}</h1>
      <p>{{ props.currentMember?.employeeId }} · {{ props.currentMember?.role }}</p>
    </div>
    <div class="header-actions">
      <button class="notify-toggle" type="button" @click="emit('toggleNotifications')">
        通知
        <span v-if="props.context.unreadCount > 0" class="notify-badge">{{ props.context.unreadCount }}</span>
      </button>
      <button @click="emit('logout')">登出</button>
    </div>
  </section>
</template>
