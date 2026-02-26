import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import AuthPanel from '../AuthPanel.vue';

describe('AuthPanel', () => {
  it('emits authMode update when switching to register', async () => {
    const wrapper = mount(AuthPanel, {
      props: {
        authMode: 'login',
        registerStep: 1,
        authLoading: false,
        authError: '',
        loginForm: { employeeId: '', password: '' },
        registerForm: { employeeId: '', name: '', email: '', password: '', groupId: null },
        registerGroupOptions: []
      }
    });

    const registerButton = wrapper.findAll('button').find((btn) => btn.text() === '註冊');
    await registerButton!.trigger('click');

    expect(wrapper.emitted('update:authMode')?.[0]).toEqual(['register']);
  });

  it('emits register when submit on step 3', async () => {
    const wrapper = mount(AuthPanel, {
      props: {
        authMode: 'register',
        registerStep: 3,
        authLoading: false,
        authError: '',
        loginForm: { employeeId: '', password: '' },
        registerForm: { employeeId: 'A001', name: 'Amy', email: 'amy@test.com', password: 'Password123', groupId: 1 },
        registerGroupOptions: [{ id: 1, name: 'IT' }]
      }
    });

    await wrapper.find('form').trigger('submit.prevent');
    expect(wrapper.emitted('register')).toBeTruthy();
  });
});
