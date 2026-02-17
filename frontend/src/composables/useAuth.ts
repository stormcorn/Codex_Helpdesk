import { computed, reactive, ref } from 'vue';
import { requestJson } from './useApi';
import type { AuthMode, LoginForm, Member, RegisterForm } from '../types';

type UseAuthOptions = {
  tokenKey: string;
  onAfterLogin: () => Promise<void>;
  onApplyMemberProfile: (member: Member) => void;
  onClearState: () => void;
};

export function useAuth(options: UseAuthOptions) {
  const authMode = ref<AuthMode>('login');
  const registerStep = ref(1);
  const authLoading = ref(false);
  const authError = ref('');

  const loginForm = reactive<LoginForm>({ employeeId: '', password: '' });
  const registerForm = reactive<RegisterForm>({ employeeId: '', name: '', email: '', password: '' });

  const token = ref('');
  const currentMember = ref<Member | null>(null);

  const isAuthenticated = computed(() => Boolean(token.value));
  const isAdmin = computed(() => currentMember.value?.role === 'ADMIN');
  const isItOrAdmin = computed(() => currentMember.value?.role === 'IT' || currentMember.value?.role === 'ADMIN');

  function authHeaders(): HeadersInit {
    return { Authorization: `Bearer ${token.value}` };
  }

  function nextRegisterStep(): void {
    authError.value = '';
    if (registerStep.value === 1 && (!registerForm.employeeId || !registerForm.name)) {
      authError.value = '請先填寫員工工號與姓名。';
      return;
    }
    if (registerStep.value === 2) {
      if (!registerForm.email || !registerForm.password) {
        authError.value = '請填寫 Email 與密碼。';
        return;
      }
      if (registerForm.password.length < 8) {
        authError.value = '密碼至少 8 碼。';
        return;
      }
    }
    registerStep.value = Math.min(3, registerStep.value + 1);
  }

  function prevRegisterStep(): void {
    authError.value = '';
    registerStep.value = Math.max(1, registerStep.value - 1);
  }

  function applyAuth(newToken: string, member: Member): void {
    token.value = newToken;
    currentMember.value = member;
    sessionStorage.setItem(options.tokenKey, newToken);
    options.onApplyMemberProfile(member);
  }

  async function login(): Promise<void> {
    authError.value = '';
    authLoading.value = true;
    try {
      const result = await requestJson<{ token: string; member: Member }>(
        '/api/auth/login',
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(loginForm) },
        '登入失敗'
      );
      applyAuth(result.token, result.member);
      await options.onAfterLogin();
    } catch (e) {
      authError.value = e instanceof Error ? e.message : '登入失敗';
    } finally {
      authLoading.value = false;
    }
  }

  async function register(): Promise<void> {
    authError.value = '';
    authLoading.value = true;
    try {
      const result = await requestJson<{ token: string; member: Member }>(
        '/api/auth/register',
        { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(registerForm) },
        '註冊失敗'
      );
      applyAuth(result.token, result.member);
      await options.onAfterLogin();
    } catch (e) {
      authError.value = e instanceof Error ? e.message : '註冊失敗';
    } finally {
      authLoading.value = false;
    }
  }

  function clearSession(): void {
    token.value = '';
    currentMember.value = null;
    sessionStorage.removeItem(options.tokenKey);
    options.onClearState();
  }

  async function restoreSession(): Promise<void> {
    const saved = sessionStorage.getItem(options.tokenKey);
    if (!saved) return;
    token.value = saved;
    try {
      const me = await requestJson<Member>('/api/auth/me', { headers: authHeaders() }, '登入已失效');
      currentMember.value = me;
      options.onApplyMemberProfile(me);
      await options.onAfterLogin();
    } catch {
      clearSession();
    }
  }

  async function logout(): Promise<void> {
    try {
      if (token.value) {
        await fetch('/api/auth/logout', { method: 'POST', headers: authHeaders() });
      }
    } finally {
      clearSession();
    }
  }

  return {
    authMode,
    registerStep,
    authLoading,
    authError,
    loginForm,
    registerForm,
    token,
    currentMember,
    isAuthenticated,
    isAdmin,
    isItOrAdmin,
    authHeaders,
    nextRegisterStep,
    prevRegisterStep,
    login,
    register,
    restoreSession,
    logout,
    clearSession
  };
}
