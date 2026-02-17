import { expect, test } from '@playwright/test';

async function mockCommonApi(page: import('@playwright/test').Page) {
  await page.route('**/api/**', async (route) => {
    const url = route.request().url();
    const method = route.request().method();

    if (url.endsWith('/api/auth/login') && method === 'POST') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          token: 'fake-token',
          member: {
            id: 1,
            employeeId: 'USER001',
            name: 'Test User',
            email: 'user@test.local',
            role: 'USER',
            createdAt: '2026-01-01T00:00:00Z'
          }
        })
      });
    }

    if (url.endsWith('/api/groups/mine') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: 'IT', supervisor: false }])
      });
    }

    if (url.endsWith('/api/helpdesk/categories') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([{ id: 1, name: '一般問題', createdAt: '2026-01-01T00:00:00Z' }])
      });
    }

    if (url.endsWith('/api/helpdesk/tickets') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify([])
      });
    }

    if (url.endsWith('/api/notifications') && method === 'GET') {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ notifications: [], unreadCount: 0 })
      });
    }

    if (url.endsWith('/api/notifications/read-all') && method === 'PATCH') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }

    if (url.endsWith('/api/auth/logout') && method === 'POST') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
    }

    return route.fulfill({ status: 200, contentType: 'application/json', body: '{}' });
  });
}

test('unauth page renders and can switch to register tab', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Helpdesk Member Portal' })).toBeVisible();
  await page.getByRole('button', { name: '註冊' }).click();
  await expect(page.getByLabel('姓名')).toBeVisible();
});

test('mock login enters dashboard and can switch to archive tab', async ({ page }) => {
  await mockCommonApi(page);
  await page.goto('/');

  await page.getByLabel('帳號（員工工號）').fill('USER001');
  await page.getByLabel('密碼').fill('Password123');
  await page.locator('form').getByRole('button', { name: '登入' }).click();

  await expect(page.getByRole('button', { name: '封存' })).toBeVisible();
  await page.getByRole('button', { name: '封存' }).click();
  await expect(page.getByRole('heading', { name: '封存工單' })).toBeVisible();
});
