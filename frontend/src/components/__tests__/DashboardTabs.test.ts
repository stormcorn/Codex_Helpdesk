import { mount } from '@vue/test-utils';
import { describe, expect, it } from 'vitest';
import DashboardTabs from '../DashboardTabs.vue';

describe('DashboardTabs', () => {
  it('emits openMembers when admin members tab is clicked', async () => {
    const wrapper = mount(DashboardTabs, {
      props: {
        context: {
          dashboardTab: 'helpdesk',
          isItOrAdmin: true,
          isAdmin: true,
          notificationsOpen: false,
          unreadCount: 0
        }
      }
    });

    const membersButton = wrapper.findAll('button').find((btn) => btn.text() === '成員管理');
    expect(membersButton).toBeDefined();
    await membersButton!.trigger('click');

    expect(wrapper.emitted('openMembers')).toBeTruthy();
  });

  it('emits dashboard tab update when switching to archive', async () => {
    const wrapper = mount(DashboardTabs, {
      props: {
        context: {
          dashboardTab: 'helpdesk',
          isItOrAdmin: false,
          isAdmin: false,
          notificationsOpen: false,
          unreadCount: 0
        }
      }
    });

    const archiveButton = wrapper.findAll('button').find((btn) => btn.text() === '封存');
    await archiveButton!.trigger('click');

    expect(wrapper.emitted('setTab')?.[0]).toEqual(['archive']);
  });
});
