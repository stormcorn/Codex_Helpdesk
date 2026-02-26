<script setup lang="ts">
import { computed } from 'vue';
import type { AuthMode, LoginForm, PublicGroupOption, RegisterForm } from '../types';

const props = defineProps<{
  authMode: AuthMode;
  registerStep: number;
  authLoading: boolean;
  authError: string;
  loginForm: LoginForm;
  registerForm: RegisterForm;
  registerGroupOptions: PublicGroupOption[];
}>();

const emit = defineEmits<{
  'update:authMode': [mode: AuthMode];
  login: [];
  nextRegisterStep: [];
  prevRegisterStep: [];
  register: [];
}>();

const authModeModel = computed({
  get: () => props.authMode,
  set: (mode: AuthMode) => emit('update:authMode', mode)
});

function submitRegisterForm(): void {
  if (props.registerStep === 3) {
    emit('register');
    return;
  }
  emit('nextRegisterStep');
}
</script>

<template>
  <section class="auth-panel">
    <h1>Helpdesk Member Portal</h1>
    <p class="subtitle">登入或註冊後即可提交工單。管理員可指派 IT 角色處理工單。</p>

    <div class="switch-row">
      <button :class="{ active: authModeModel === 'login' }" @click="authModeModel = 'login'">登入</button>
      <button :class="{ active: authModeModel === 'register' }" @click="authModeModel = 'register'">註冊</button>
    </div>

    <form v-if="authModeModel === 'login'" class="form-grid" @submit.prevent="emit('login')">
      <label>帳號（員工工號）<input v-model="props.loginForm.employeeId" required /></label>
      <label>密碼<input v-model="props.loginForm.password" type="password" required /></label>
      <button :disabled="props.authLoading" type="submit">{{ props.authLoading ? '登入中...' : '登入' }}</button>
    </form>

    <div v-else>
      <div class="stepper">
        <span :class="{ on: props.registerStep >= 1 }">1</span>
        <span :class="{ on: props.registerStep >= 2 }">2</span>
        <span :class="{ on: props.registerStep >= 3 }">3</span>
      </div>
      <form class="form-grid" @submit.prevent="submitRegisterForm">
        <template v-if="props.registerStep === 1">
          <div class="row">
            <label>
              所屬部門群組
              <select v-model="props.registerForm.groupId" required>
                <option :value="null" disabled>請選擇部門群組</option>
                <option v-for="g in props.registerGroupOptions" :key="g.id" :value="g.id">{{ g.name }}</option>
              </select>
            </label>
            <small class="subtitle">若無可選群組，請先聯繫管理員建立。</small>
          </div>
          <label>帳號（員工工號）<input v-model="props.registerForm.employeeId" required /></label>
          <label>姓名<input v-model="props.registerForm.name" required /></label>
        </template>
        <template v-else-if="props.registerStep === 2">
          <label>Email<input v-model="props.registerForm.email" type="email" required /></label>
          <label>密碼（至少 8 碼）<input v-model="props.registerForm.password" type="password" minlength="8" required /></label>
        </template>
        <template v-else>
          <div class="confirm-box">
            <p>工號：{{ props.registerForm.employeeId }}</p>
            <p>姓名：{{ props.registerForm.name }}</p>
            <p>Email：{{ props.registerForm.email }}</p>
            <p>部門群組：{{ props.registerGroupOptions.find((g) => g.id === props.registerForm.groupId)?.name ?? '-' }}</p>
          </div>
        </template>
        <div class="row">
          <button v-if="props.registerStep > 1" type="button" @click="emit('prevRegisterStep')">上一步</button>
          <button :disabled="props.authLoading" type="submit">{{ props.registerStep === 3 ? '完成註冊' : '下一步' }}</button>
        </div>
      </form>
    </div>

    <p v-if="props.authError" class="feedback error">{{ props.authError }}</p>
  </section>
</template>
