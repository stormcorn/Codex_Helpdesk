<script setup lang="ts">
import { computed, ref } from 'vue';
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

const showLoginPassword = ref(false);
const rememberLogin = ref(false);

function submitRegisterForm(): void {
  if (props.registerStep === 3) {
    emit('register');
    return;
  }
  emit('nextRegisterStep');
}
</script>

<template>
  <section v-if="authModeModel === 'login'" class="auth-panel auth-login-shell">
    <div class="auth-login-hero">
      <div class="auth-hero-orb auth-hero-orb-a"></div>
      <div class="auth-hero-orb auth-hero-orb-b"></div>
      <div class="auth-hero-content">
        <div class="auth-hero-brand-mark">🎧</div>
        <h1>工單管理系統</h1>
        <p>Helpdesk Management System</p>
        <ul class="auth-hero-feature-list">
          <li>
            <div class="auth-hero-feature-icon">🎫</div>
            <div>
              <strong>智慧工單管理</strong>
              <small>高效追蹤與處理企業內部服務請求</small>
            </div>
          </li>
          <li>
            <div class="auth-hero-feature-icon">👥</div>
            <div>
              <strong>多角色協作</strong>
              <small>支援使用者、IT 與管理員權限管理</small>
            </div>
          </li>
          <li>
            <div class="auth-hero-feature-icon">📈</div>
            <div>
              <strong>即時監控分析</strong>
              <small>完整稽核紀錄與通知追蹤</small>
            </div>
          </li>
        </ul>
      </div>
    </div>

    <div class="auth-login-pane">
      <div class="auth-login-mobile-brand">
        <div class="auth-login-mobile-mark">🎧</div>
        <h2>企業工單系統</h2>
      </div>

      <div class="auth-login-header">
        <h2>歡迎回來</h2>
        <p class="subtitle">請登入您的帳號以繼續使用系統</p>
      </div>

      <div class="auth-role-info-card">
        <div class="auth-role-info-icon">i</div>
        <div class="auth-role-info-text">
          <h4>系統角色說明</h4>
          <ul>
            <li><strong>USER</strong><span>一般使用者 - 建立與查看個人工單</span></li>
            <li><strong>MANAGER</strong><span>群組主管 - 確認急件工單優先處理</span></li>
            <li><strong>SUPPORT</strong><span>支援人員 - 處理與回應工單</span></li>
            <li><strong>ADMIN</strong><span>管理員 - 完整系統管理權限</span></li>
          </ul>
        </div>
      </div>

      <form class="auth-login-form" @submit.prevent="emit('login')">
        <label>
          工號
          <span class="required-mark">*</span>
          <div class="auth-input-wrap">
            <span class="auth-input-icon">🪪</span>
            <input v-model="props.loginForm.employeeId" placeholder="請輸入工號" required />
          </div>
        </label>

        <label>
          密碼
          <span class="required-mark">*</span>
          <div class="auth-input-wrap">
            <span class="auth-input-icon">🔒</span>
            <input
              v-model="props.loginForm.password"
              :type="showLoginPassword ? 'text' : 'password'"
              placeholder="請輸入密碼"
              required
            />
            <button type="button" class="auth-input-trailing" @click="showLoginPassword = !showLoginPassword">
              {{ showLoginPassword ? '隱藏' : '顯示' }}
            </button>
          </div>
        </label>

        <div class="auth-login-options">
          <label class="auth-check-inline">
            <input v-model="rememberLogin" type="checkbox" />
            <span>記住我</span>
          </label>
          <button type="button" class="auth-link-btn" disabled title="尚未實作">忘記密碼？</button>
        </div>

        <div class="auth-login-lang">語系：繁體中文</div>

        <button :disabled="props.authLoading" type="submit" class="auth-login-submit">
          {{ props.authLoading ? '登入中...' : '登入系統' }}
        </button>
      </form>

      <div class="auth-login-register">
        <p>還沒有帳號？</p>
        <button type="button" class="auth-link-btn auth-register-jump" @click="authModeModel = 'register'">前往註冊</button>
      </div>

      <div class="auth-login-footer">
        <span>使用條款</span>
        <span>隱私政策</span>
        <span>技術支援</span>
      </div>
    </div>

    <p v-if="props.authError" class="feedback error auth-login-error">{{ props.authError }}</p>
  </section>

  <section v-else class="auth-panel">
    <h1>Helpdesk Member Portal</h1>
    <p class="subtitle">登入或註冊後即可提交工單。管理員可指派 IT 角色處理工單。</p>

    <div class="switch-row">
      <button @click="authModeModel = 'login'">登入</button>
      <button :class="{ active: true }" @click="authModeModel = 'register'">註冊</button>
    </div>

    <div>
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
  </section>
</template>
