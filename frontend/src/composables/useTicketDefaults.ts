import type { Ref } from 'vue';
import type { HelpdeskCategory, MyGroup, TicketForm } from '../types';

type UseTicketDefaultsOptions = {
  myGroups: Ref<MyGroup[]>;
  helpdeskCategories: Ref<HelpdeskCategory[]>;
  ticketForm: TicketForm;
  loadBaseMyGroups: () => Promise<void>;
  loadBaseHelpdeskCategories: () => Promise<void>;
};

export function useTicketDefaults(options: UseTicketDefaultsOptions) {
  async function loadMyGroups(): Promise<void> {
    await options.loadBaseMyGroups();
    if (!options.myGroups.value.length) {
      options.ticketForm.groupId = null;
      return;
    }
    const currentGroupStillValid = options.myGroups.value.some((g) => g.id === options.ticketForm.groupId);
    if (!currentGroupStillValid) {
      options.ticketForm.groupId = options.myGroups.value[0].id;
    }
  }

  async function loadHelpdeskCategories(): Promise<void> {
    await options.loadBaseHelpdeskCategories();
    if (!options.helpdeskCategories.value.length) {
      options.ticketForm.categoryId = null;
      return;
    }
    const currentCategoryStillValid = options.helpdeskCategories.value.some((c) => c.id === options.ticketForm.categoryId);
    if (!currentCategoryStillValid) {
      options.ticketForm.categoryId = options.helpdeskCategories.value[0].id;
    }
  }

  return {
    loadMyGroups,
    loadHelpdeskCategories
  };
}
