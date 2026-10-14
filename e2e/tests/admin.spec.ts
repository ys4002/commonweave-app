import { test, expect } from '@playwright/test';

test('admin signs in with username, sees users,', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email or username').fill('admin');
  await page.getByLabel('Password').fill('admin');
  await page.getByRole('button', { name: 'Sign in' }).click();

  await expect(page).toHaveURL(/\/admin$/);
  await expect(page.getByRole('heading', { name: 'Overview' })).toBeVisible();

  await page.getByRole('link', { name: 'Users', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Users', exact: true })).toBeVisible();
  await expect(page.getByText('ananya@example.in')).toBeVisible();

  await page.getByRole('link', { name: 'Audit log' }).click();
  await expect(page.getByRole('heading', { name: /audit/i })).toBeVisible();
});

test('a regular member cannot open the admin area', async ({ page }) => {
  await page.goto('/login');
  await page.getByLabel('Email or username').fill('ananya@example.in');
  await page.getByLabel('Password').fill('Welcome@123');
  await page.getByRole('button', { name: 'Sign in' }).click();
  await expect(page).not.toHaveURL(/\/login/);
  await page.goto('/admin/users');
  await expect(page.getByText(/403/)).toBeVisible();
});
