import { ref, type ComputedRef } from 'vue';
import { requestJson } from './useApi';
import type { Member, Role } from '../types';

type UseMembersOptions = {
  isAdmin: ComputedRef<boolean>;
  authHeaders: () => HeadersInit;
};

export function useMembers(options: UseMembersOptions) {
  const members = ref<Member[]>([]);
  const loadingMembers = ref(false);
  const membersFeedback = ref('');

  async function loadMembers(): Promise<void> {
    if (!options.isAdmin.value) return;
    loadingMembers.value = true;
    membersFeedback.value = '';
    try {
      members.value = await requestJson<Member[]>('/api/admin/members', { headers: options.authHeaders() }, '讀取成員失敗');
    } catch (e) {
      membersFeedback.value = e instanceof Error ? e.message : '讀取成員失敗';
    } finally {
      loadingMembers.value = false;
    }
  }

  async function updateMemberRole(member: Member, role: Role): Promise<void> {
    if (member.role === 'ADMIN') return;
    try {
      const updated = await requestJson<Member>(
        `/api/admin/members/${member.id}/role`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
          body: JSON.stringify({ role })
        },
        '更新角色失敗'
      );
      members.value = members.value.map((m) => (m.id === updated.id ? updated : m));
    } catch (e) {
      membersFeedback.value = e instanceof Error ? e.message : '更新角色失敗';
    }
  }

  async function deleteMember(member: Member): Promise<void> {
    if (member.role === 'ADMIN') return;
    if (!confirm(`確定刪除 ${member.name} (${member.employeeId})？`)) return;
    try {
      await requestJson(
        `/api/admin/members/${member.id}`,
        { method: 'DELETE', headers: options.authHeaders() },
        '刪除失敗'
      );
      members.value = members.value.filter((m) => m.id !== member.id);
    } catch (e) {
      membersFeedback.value = e instanceof Error ? e.message : '刪除失敗';
    }
  }

  function clearMemberState(): void {
    members.value = [];
    membersFeedback.value = '';
  }

  return {
    members,
    loadingMembers,
    membersFeedback,
    loadMembers,
    updateMemberRole,
    deleteMember,
    clearMemberState
  };
}
