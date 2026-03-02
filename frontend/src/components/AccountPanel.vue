<script setup lang="ts">
import { computed } from 'vue';
import type { Member } from '../types';

const props = defineProps<{
  currentMember: Member | null;
  loading: boolean;
  feedback: string;
  newPassword: string;
  confirmPassword: string;
}>();

const emit = defineEmits<{
  'update:newPassword': [value: string];
  'update:confirmPassword': [value: string];
  changePassword: [];
}>();

const newPasswordModel = computed({
  get: () => props.newPassword,
  set: (value: string) => emit('update:newPassword', value)
});

const confirmPasswordModel = computed({
  get: () => props.confirmPassword,
  set: (value: string) => emit('update:confirmPassword', value)
});
</script>

<template>
  <section class="panel">
    <h2>帳號管理</h2>
    <p class="subtitle">目前登入：{{ props.currentMember?.employeeId }} · {{ props.currentMember?.name }} ({{ props.currentMember?.role }})</p>

    <form class="form-grid" @submit.prevent="emit('changePassword')">
      <label>
        新密碼（至少 8 碼）
        <input v-model="newPasswordModel" type="password" minlength="8" autocomplete="new-password" required />
      </label>
      <label>
        確認新密碼
        <input v-model="confirmPasswordModel" type="password" minlength="8" autocomplete="new-password" required />
      </label>
      <div class="row">
        <button :disabled="props.loading" type="submit">{{ props.loading ? '更新中...' : '更新密碼' }}</button>
      </div>
    </form>

    <p v-if="props.feedback" class="feedback">{{ props.feedback }}</p>
  </section>
</template>
