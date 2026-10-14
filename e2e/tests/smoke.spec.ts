import { test, expect } from '@playwright/test';

const baseURL = 'http://localhost:5173';

const signUp = async (page, email, password, displayName) => {
  await page.goto(`${baseURL}/signup`);
  await page.getByLabel('Display name').fill(displayName);
  await page.getByLabel('Email').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Create account' }).click();
};

const signIn = async (page, email, password) => {
  await page.goto(`${baseURL}/login`);  
  await page.getByLabel('Email or username').fill(email);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Sign in' }).click();
};

const signOut = async (page) => {
  await page.getByRole('button', { name: 'Sign out' }).click();
};

test('smoke test', async ({ page }) => {
  const timestamp = Date.now();
  const email = `e2e-${timestamp}@example.in`;
  const password = 'E2eSmoke#2026';
  const displayName = 'E2E Tester';
  const listingTitle = `E2E Folding Table ${timestamp}`;

  await test.step('sign up new user and complete onboarding', async () => {
    await signUp(page, email, password, displayName);
    await expect(page.getByRole('heading', { name: 'Set up your profile' })).toBeVisible();
    await page.getByLabel('Display name').fill(displayName);
    await page.getByLabel('Your locality').fill('5th Block, Koramangala');
    await page.getByLabel('Filter by city').type('Bengaluru');
    await page.getByRole('button', { name: 'Join Koramangala 5th Block Neighbours' }).click();
    await page.getByRole('button', { name: 'Finish setup' }).click();
  });

  await test.step('create new OFFER listing', async () => {
    await page.getByRole('link', { name: 'New listing' }).click();
    await page.getByLabel('Title').fill(listingTitle);
    await page.getByLabel('Description').fill('Brand new folding table');
    await page.getByLabel('Available from').fill(new Date().toISOString().split('T')[0]);
    await page.getByLabel('Available until').fill(new Date(Date.now() + 30 * 86400000).toISOString().split('T')[0]);
    await page.getByLabel('Pickup preference').fill('Society gate');
    await page.getByLabel('Estimated value (₹)').fill('1500');
    await page.getByRole('button', { name: 'Post listing' }).click();
    await expect(page.getByRole('heading', { name: listingTitle })).toBeVisible();
  });

  await test.step('sign in as demo user and propose handoff', async () => {
    await signOut(page);
    await signIn(page, 'ananya@example.in', 'Welcome@123');
    await page.getByRole('link', { name: 'Explore' }).click();
    await page.getByLabel('Search').fill(listingTitle);
    await page.getByRole('button', { name: 'Search' }).click();
    await page.getByRole('link', { name: listingTitle }).first().click();
    await page.getByRole('button', { name: 'Propose handoff' }).click();
    await page.getByLabel('Message to the owner').fill('Can I borrow this tomorrow?');
    await page.getByRole('button', { name: 'Send proposal' }).click();
    await expect(page.getByText('Proposal sent')).toBeVisible();
  });

  await test.step('sign back in as new user and complete handoff', async () => {
    await signOut(page);
    await signIn(page, email, password);
    await page.getByRole('link', { name: 'Handoffs' }).click();
    await page.getByRole('link', { name: listingTitle }).click();
    const scheduledAt = new Date(Date.now() + 2 * 86400000).toISOString().slice(0, 16);
    await page.getByRole('button', { name: 'Confirm handoff' }).click();
    await page.getByLabel('Handoff date and time (IST)').fill(scheduledAt);
    await page.getByRole('button', { name: 'Schedule it' }).click();
    await expect(page.getByText('Handoff confirmed and scheduled')).toBeVisible();
    await page.getByRole('button', { name: 'Mark as handed off' }).click();
    await expect(page.getByText('Marked as handed off')).toBeVisible();
    await page.getByRole('button', { name: 'Mark as returned' }).click();  
    await expect(page.getByText('Marked as returned. Thank you!')).toBeVisible();
    await expect(page.getByText('Returned', { exact: true }).first()).toBeVisible();
  });
});
