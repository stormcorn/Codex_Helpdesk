import { ref, type Ref } from 'vue';
import { requestJson } from './useApi';
import type { HelpdeskCategory, MyGroup } from '../types';

type UseBaseDataOptions = {
  token: Ref<string>;
  authHeaders: () => HeadersInit;
};

export function useBaseData(options: UseBaseDataOptions) {
  const myGroups = ref<MyGroup[]>([]);
  const helpdeskCategories = ref<HelpdeskCategory[]>([]);

  async function loadMyGroups(): Promise<void> {
    if (!options.token.value) return;
    try {
      myGroups.value = await requestJson<MyGroup[]>('/api/groups/mine', { headers: options.authHeaders() }, '讀取群組失敗');
    } catch {
      myGroups.value = [];
    }
  }

  async function loadHelpdeskCategories(): Promise<void> {
    if (!options.token.value) return;
    try {
      helpdeskCategories.value = await requestJson<HelpdeskCategory[]>(
        '/api/helpdesk/categories',
        { headers: options.authHeaders() },
        '讀取分類失敗'
      );
    } catch {
      helpdeskCategories.value = [];
    }
  }

  function clearBaseDataState(): void {
    myGroups.value = [];
    helpdeskCategories.value = [];
  }

  return {
    myGroups,
    helpdeskCategories,
    loadMyGroups,
    loadHelpdeskCategories,
    clearBaseDataState
  };
}
