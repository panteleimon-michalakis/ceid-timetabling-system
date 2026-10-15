import { test, expect } from '@playwright/test';

// Helper: login as admin before timetable tests
async function loginAsAdmin(page: import('@playwright/test').Page) {
  await page.goto('/login');
  await page.getByRole('textbox', { name: /username|χρήστης/i }).fill('admin');
  await page.locator('input[type="password"]').fill('admin123');
  await page.getByRole('button', { name: /login|σύνδεση/i }).click();
  await page.waitForURL(url => !url.pathname.includes('login'), { timeout: 5000 });
}

test.describe('Timetable - navigation', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('main page loads after login', async ({ page }) => {
    await expect(page).not.toHaveURL(/login/);
    // Page has loaded some content
    await expect(page.locator('body')).not.toBeEmpty();
  });

  test('timetable selector is visible', async ({ page }) => {
    // The app should show timetable selection or a timetable grid
    await expect(
      page.locator('text=/πρόγραμμα|timetable/i').first()
    ).toBeVisible({ timeout: 5000 });
  });
});

test.describe('Timetable - admin CRUD', () => {
  test.beforeEach(async ({ page }) => {
    await loginAsAdmin(page);
  });

  test('courses page is accessible', async ({ page }) => {
    await page.goto('/courses');
    await expect(page.locator('body')).not.toBeEmpty();
  });

  test('rooms page is accessible', async ({ page }) => {
    await page.goto('/rooms');
    await expect(page.locator('body')).not.toBeEmpty();
  });

  test('teachers page is accessible', async ({ page }) => {
    await page.goto('/teachers');
    await expect(page.locator('body')).not.toBeEmpty();
  });
});
