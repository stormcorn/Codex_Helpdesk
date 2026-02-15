<script setup lang="ts">
import type { AuthMode, LoginForm, RegisterForm } from '../types';

const props = defineProps<{
  authMode: AuthMode;
  registerStep: number;
  authLoading: boolean;
  authError: string;
  loginForm: LoginForm;
  registerForm: RegisterForm;
  setAuthMode: (mode: AuthMode) => void;
  onLogin: () => void;
  onRegister: () => void;
  onNextRegisterStep: () => void;
  onPrevRegisterStep: () => void;
}>();
</script>

<template>
  <section class="auth-panel">
    <h1>Helpdesk Member Portal</h1>
    <p class="subtitle">登入或註冊後即可提交工單。管理員可指派 IT 角色處理工單。</p>

    <div class="switch-row">
      <button :class="{ active: props.authMode === 'login' }" @click="props.setAuthMode('login')">登入</button>
      <button :class="{ active: props.authMode === 'register' }" @click="props.setAuthMode('register')">註冊</button>
    </div>

    <form v-if="props.authMode === 'login'" class="form-grid" @submit.prevent="props.onLogin">
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
      <form class="form-grid" @submit.prevent="props.registerStep === 3 ? props.onRegister() : props.onNextRegisterStep()">
        <template v-if="props.registerStep === 1">
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
          </div>
        </template>
        <div class="row">
          <button v-if="props.registerStep > 1" type="button" @click="props.onPrevRegisterStep">上一步</button>
          <button :disabled="props.authLoading" type="submit">{{ props.registerStep === 3 ? '完成註冊' : '下一步' }}</button>
        </div>
      </form>
    </div>

    <p v-if="props.authError" class="feedback error">{{ props.authError }}</p>
  </section>
</template>
