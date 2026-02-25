import { reactive, ref, type Ref } from 'vue';
import { requestJson } from './useApi';
import { useTextFeedback } from './useFeedbackState';
import type { AdminGroup, HelpdeskCategory } from '../types';

type UseAdminManagementOptions = {
  isAdmin: Ref<boolean>;
  authHeaders: () => HeadersInit;
  loadMyGroups: () => Promise<void>;
  loadHelpdeskCategories: () => Promise<void>;
};

export function useAdminManagement(options: UseAdminManagementOptions) {
  const adminHelpdeskCategories = ref<HelpdeskCategory[]>([]);
  const { feedback: categoryFeedback, clearFeedback: clearCategoryFeedback } = useTextFeedback();
  const createCategoryName = ref('');
  const adminGroups = ref<AdminGroup[]>([]);
  const loadingGroups = ref(false);
  const { feedback: groupsFeedback, clearFeedback: clearGroupsFeedback } = useTextFeedback();
  const createGroupName = ref('');
  const groupAssignForm = reactive<{ groupId: number | null; memberId: number | null }>({ groupId: null, memberId: null });

  async function loadAdminGroups(): Promise<void> {
    if (!options.isAdmin.value) return;
    loadingGroups.value = true;
    groupsFeedback.value = '';
    try {
      adminGroups.value = await requestJson<AdminGroup[]>('/api/admin/groups', { headers: options.authHeaders() }, '讀取群組失敗');
      if (adminGroups.value.length && !groupAssignForm.groupId) {
        groupAssignForm.groupId = adminGroups.value[0].id;
      }
    } catch (e) {
      groupsFeedback.value = e instanceof Error ? e.message : '讀取群組失敗';
    } finally {
      loadingGroups.value = false;
    }
  }

  async function loadAdminHelpdeskCategories(): Promise<void> {
    if (!options.isAdmin.value) return;
    categoryFeedback.value = '';
    try {
      adminHelpdeskCategories.value = await requestJson<HelpdeskCategory[]>(
        '/api/admin/helpdesk-categories',
        { headers: options.authHeaders() },
        '讀取分類失敗'
      );
    } catch (e) {
      categoryFeedback.value = e instanceof Error ? e.message : '讀取分類失敗';
    }
  }

  async function createHelpdeskCategory(): Promise<void> {
    if (!options.isAdmin.value) return;
    const name = createCategoryName.value.trim();
    if (!name) {
      categoryFeedback.value = '請輸入分類名稱。';
      return;
    }
    categoryFeedback.value = '';
    try {
      await requestJson<HelpdeskCategory>(
        '/api/admin/helpdesk-categories',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
          body: JSON.stringify({ name })
        },
        '建立分類失敗'
      );
      createCategoryName.value = '';
      await loadAdminHelpdeskCategories();
      await options.loadHelpdeskCategories();
    } catch (e) {
      categoryFeedback.value = e instanceof Error ? e.message : '建立分類失敗';
    }
  }

  async function updateHelpdeskCategory(categoryId: number, name: string): Promise<void> {
    if (!options.isAdmin.value) return;
    const normalized = name.trim();
    if (!normalized) {
      categoryFeedback.value = '請輸入分類名稱。';
      return;
    }
    categoryFeedback.value = '';
    try {
      await requestJson<HelpdeskCategory>(
        `/api/admin/helpdesk-categories/${categoryId}`,
        {
          method: 'PATCH',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
          body: JSON.stringify({ name: normalized })
        },
        '修改分類失敗'
      );
      await loadAdminHelpdeskCategories();
      await options.loadHelpdeskCategories();
    } catch (e) {
      categoryFeedback.value = e instanceof Error ? e.message : '修改分類失敗';
    }
  }

  async function deleteHelpdeskCategory(categoryId: number): Promise<void> {
    if (!options.isAdmin.value) return;
    categoryFeedback.value = '';
    try {
      await requestJson<void>(
        `/api/admin/helpdesk-categories/${categoryId}`,
        { method: 'DELETE', headers: options.authHeaders() },
        '刪除分類失敗'
      );
      await loadAdminHelpdeskCategories();
      await options.loadHelpdeskCategories();
    } catch (e) {
      categoryFeedback.value = e instanceof Error ? e.message : '刪除分類失敗';
    }
  }

  async function createAdminGroup(): Promise<void> {
    if (!options.isAdmin.value) return;
    const name = createGroupName.value.trim();
    if (!name) {
      groupsFeedback.value = '請輸入群組名稱。';
      return;
    }
    groupsFeedback.value = '';
    try {
      await requestJson<AdminGroup>(
        '/api/admin/groups',
        {
          method: 'POST',
          headers: { 'Content-Type': 'application/json', ...options.authHeaders() },
          body: JSON.stringify({ name })
        },
        '建立群組失敗'
      );
      createGroupName.value = '';
      await loadAdminGroups();
      await options.loadMyGroups();
    } catch (e) {
      groupsFeedback.value = e instanceof Error ? e.message : '建立群組失敗';
    }
  }

  async function addMemberToGroup(): Promise<void> {
    if (!options.isAdmin.value || !groupAssignForm.groupId || !groupAssignForm.memberId) return;
    groupsFeedback.value = '';
    try {
      await requestJson<AdminGroup>(
        `/api/admin/groups/${groupAssignForm.groupId}/members/${groupAssignForm.memberId}`,
        { method: 'PATCH', headers: options.authHeaders() },
        '加入群組失敗'
      );
      await loadAdminGroups();
      await options.loadMyGroups();
    } catch (e) {
      groupsFeedback.value = e instanceof Error ? e.message : '加入群組失敗';
    }
  }

  async function removeMemberFromGroup(groupId: number, memberId: number): Promise<void> {
    if (!options.isAdmin.value) return;
    groupsFeedback.value = '';
    try {
      await requestJson<AdminGroup>(
        `/api/admin/groups/${groupId}/members/${memberId}`,
        { method: 'DELETE', headers: options.authHeaders() },
        '移出群組失敗'
      );
      await loadAdminGroups();
      await options.loadMyGroups();
    } catch (e) {
      groupsFeedback.value = e instanceof Error ? e.message : '移出群組失敗';
    }
  }

  async function setGroupSupervisor(groupId: number, memberId: number): Promise<void> {
    if (!options.isAdmin.value) return;
    groupsFeedback.value = '';
    try {
      await requestJson<AdminGroup>(
        `/api/admin/groups/${groupId}/supervisor/${memberId}`,
        { method: 'PATCH', headers: options.authHeaders() },
        '設定主管失敗'
      );
      await loadAdminGroups();
      await options.loadMyGroups();
    } catch (e) {
      groupsFeedback.value = e instanceof Error ? e.message : '設定主管失敗';
    }
  }

  function clearAdminManagementState(): void {
    adminHelpdeskCategories.value = [];
    clearCategoryFeedback();
    createCategoryName.value = '';
    adminGroups.value = [];
    loadingGroups.value = false;
    clearGroupsFeedback();
    createGroupName.value = '';
    groupAssignForm.groupId = null;
    groupAssignForm.memberId = null;
  }

  return {
    adminHelpdeskCategories,
    categoryFeedback,
    createCategoryName,
    adminGroups,
    loadingGroups,
    groupsFeedback,
    createGroupName,
    groupAssignForm,
    loadAdminGroups,
    loadAdminHelpdeskCategories,
    createHelpdeskCategory,
    updateHelpdeskCategory,
    deleteHelpdeskCategory,
    createAdminGroup,
    addMemberToGroup,
    removeMemberFromGroup,
    setGroupSupervisor,
    clearAdminManagementState
  };
}
