import { test, expect } from '@playwright/test';

test.describe('Authentication', () => {
  test('redirects to login when unauthenticated', async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/login/);
  });

  test('login page renders correctly', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByRole('textbox', { name: /username|χρήστης/i })).toBeVisible();
    await expect(page.getByRole('textbox', { name: /password|κωδικός/i }).or(page.locator('input[type="password"]'))).toBeVisible();
    await expect(page.getByRole('button', { name: /login|σύνδεση/i })).toBeVisible();
  });

  test('login with invalid credentials shows error', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('textbox', { name: /username|χρήστης/i }).fill('wronguser');
    await page.locator('input[type="password"]').fill('wrongpass');
    await page.getByRole('button', { name: /login|σύνδεση/i }).click();
    await expect(page.locator('text=/error|σφάλμα|invalid|λάθος/i')).toBeVisible({ timeout: 5000 });
  });

  test('login with valid admin credentials succeeds', async ({ page }) => {
    await page.goto('/login');
    await page.getByRole('textbox', { name: /username|χρήστης/i }).fill('admin');
    await page.locator('input[type="password"]').fill('admin123');
    await page.getByRole('button', { name: /login|σύνδεση/i }).click();
    // After login, should NOT be on /login anymore
    await expect(page).not.toHaveURL(/login/, { timeout: 5000 });
  });
});
