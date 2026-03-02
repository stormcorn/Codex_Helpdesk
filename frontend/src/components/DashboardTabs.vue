<script setup lang="ts">
import type { DashboardContext, DashboardTab } from '../types';

const props = defineProps<{
  context: DashboardContext;
}>();

const emit = defineEmits<{
  setTab: [tab: DashboardTab];
  openMembers: [];
}>();
</script>

<template>
  <section class="tabs">
    <button :class="{ active: props.context.dashboardTab === 'helpdesk' }" @click="emit('setTab', 'helpdesk')">提交工單</button>
    <button
      v-if="props.context.isItOrAdmin"
      :class="{ active: props.context.dashboardTab === 'itdesk' }"
      @click="emit('setTab', 'itdesk')"
    >
      IT 工單處理
    </button>
    <button :class="{ active: props.context.dashboardTab === 'archive' }" @click="emit('setTab', 'archive')">封存</button>
    <button :class="{ active: props.context.dashboardTab === 'account' }" @click="emit('setTab', 'account')">帳號管理</button>
    <button
      v-if="props.context.isAdmin"
      :class="{ active: props.context.dashboardTab === 'members' }"
      @click="emit('openMembers')"
    >
      成員管理
    </button>
  </section>
</template>
