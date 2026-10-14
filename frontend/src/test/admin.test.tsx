import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ananya, apiError, mockApi, page, renderApp } from './utils';
import type { AdminUser, User } from '../api/types';

const admin: User = {
  ...ananya,
  id: 99,
  email: 'admin',
  displayName: 'Platform Admin',
  platformRole: 'ADMIN',
  circles: [],
};

const overview = {
  users: { total: 12, disabled: 1, admins: 1 },
  circles: 3,
  listings: { ACTIVE: 20, RESERVED: 2, CLOSED: 5, REMOVED: 1 },
  handoffs: { PROPOSED: 1, SCHEDULED: 2, HANDED_OFF: 0, RETURNED: 1, COMPLETED: 4, CANCELLED: 1 },
  openReports: 2,
};

const rohan: AdminUser = {
  id: 2,
  email: 'rohan@example.in',
  displayName: 'Rohan Mehta',
  locality: 'Koramangala',
  platformRole: 'USER',
  enabled: true,
  onboarded: true,
  createdAt: '2026-09-01T10:00:00+05:30',
  lastLoginAt: null,
  circles: [],
  listingCount: 3,
  completedExchanges: 1,
};

describe('admin area', () => {
  it('lets an admin sign in with a plain username and lands on /admin', async () => {
    let signedIn = false;
    const api = mockApi(
      {
        'GET /auth/me': () =>
          signedIn ? { body: admin } : apiError(401, 'UNAUTHENTICATED', 'Not signed in'),
        'POST /auth/login': () => {
          signedIn = true;
          return { body: admin };
        },
        'GET /admin/overview': { body: overview },
      },
      { me: null },
    );
    const user = userEvent.setup();
    renderApp('/login');
    const field = await screen.findByLabelText('Email or username');
    expect(field).toHaveAttribute('type', 'text');
    await user.type(field, 'admin');
    await user.type(screen.getByLabelText('Password'), 'admin');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { name: 'Overview' })).toBeInTheDocument();
    expect(api.called('POST', '/auth/login')[0].body).toEqual({
      email: 'admin',
      password: 'admin',
    });
    expect(screen.getByText('12')).toBeInTheDocument();
  });

  it('shows the Admin nav item only to admins, and skips onboarding for them', async () => {
    mockApi({ 'GET /admin/overview': { body: overview } }, { me: { ...admin, onboarded: false } });
    renderApp('/explore');
    await screen.findByRole('link', { name: 'Admin' });
    expect(screen.queryByRole('heading', { name: 'Set up your profile' })).not.toBeInTheDocument();
  });

  it('hides the Admin nav item from regular users and shows 403 on /admin/*', async () => {
    const api = mockApi({});
    renderApp('/admin/users');
    expect(await screen.findByText(/403/)).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    expect(api.called('GET', '/admin/users')).toHaveLength(0);
  });

  it('asks for confirmation before disabling a user', async () => {
    const api = mockApi(
      {
        'GET /admin/users': { body: page([rohan]) },
        'PATCH /admin/users/2': { body: { ...rohan, enabled: false } },
      },
      { me: admin },
    );
    const user = userEvent.setup();
    renderApp('/admin/users');
    await user.click(await screen.findByRole('button', { name: 'Disable Rohan Mehta' }));
    const dialog = screen.getByRole('dialog', { name: 'Disable Rohan Mehta?' });
    expect(api.called('PATCH', '/admin/users/2')).toHaveLength(0);
    await user.click(within(dialog).getByRole('button', { name: 'Keep enabled' }));
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(api.called('PATCH', '/admin/users/2')).toHaveLength(0);

    await user.click(screen.getByRole('button', { name: 'Disable Rohan Mehta' }));
    await user.click(screen.getByRole('button', { name: 'Yes, disable account' }));
    await screen.findByText('Rohan Mehta is disabled.');
    expect(api.called('PATCH', '/admin/users/2')[0].body).toEqual({ enabled: false });
  });

  it('closes dialogs with Escape and validates the reset password dialog', async () => {
    const api = mockApi(
      {
        'GET /admin/users': { body: page([rohan]) },
        'POST /admin/users/2/reset-password': { status: 204 },
      },
      { me: admin },
    );
    const user = userEvent.setup();
    renderApp('/admin/users');
    await user.click(await screen.findByRole('button', { name: 'Reset password for Rohan Mehta' }));
    await user.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Reset password for Rohan Mehta' }));
    await user.type(screen.getByLabelText('New password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Set new password' }));
    expect(await screen.findByText('Use at least 8 characters.')).toBeInTheDocument();
    expect(api.called('POST', '/admin/users/2/reset-password')).toHaveLength(0);
    await user.clear(screen.getByLabelText('New password'));
    await user.type(screen.getByLabelText('New password'), 'Fresh@1234');
    await user.click(screen.getByRole('button', { name: 'Set new password' }));
    await screen.findByText('Password reset for Rohan Mehta.');
    expect(api.called('POST', '/admin/users/2/reset-password')[0].body).toEqual({
      newPassword: 'Fresh@1234',
    });
  });

  it('shows the server message when an admin tries to demote the last admin', async () => {
    mockApi(
      {
        'GET /admin/users': { body: page([{ ...rohan, platformRole: 'ADMIN' as const }]) },
        'PATCH /admin/users/2': apiError(409, 'CONFLICT', 'Cannot demote the last admin'),
      },
      { me: admin },
    );
    const user = userEvent.setup();
    renderApp('/admin/users');
    await user.selectOptions(await screen.findByLabelText('Platform role for Rohan Mehta'), 'USER');
    expect(await screen.findByText('Cannot demote the last admin')).toBeInTheDocument();
  });

  it('confirms before removing a listing and sends the note', async () => {
    const api = mockApi(
      {
        'GET /admin/listings': {
          body: page([
            {
              id: 7,
              circleId: 10,
              circleName: 'Koramangala',
              type: 'OFFER',
              kind: 'ITEM',
              title: 'Suspicious drill',
              category: 'TOOLS',
              tags: [],
              availableFrom: null,
              availableTo: null,
              pickupPreference: null,
              estimatedValueInr: 0,
              depositInr: 0,
              status: 'ACTIVE',
              owner: { id: 2, displayName: 'Rohan Mehta' },
              createdAt: '2026-10-10T10:00:00+05:30',
            },
          ]),
        },
        'POST /admin/listings/7/remove': { status: 204 },
      },
      { me: admin },
    );
    const user = userEvent.setup();
    renderApp('/admin/listings');
    await user.click(await screen.findByRole('button', { name: 'Remove Suspicious drill' }));
    await user.click(screen.getByRole('button', { name: 'Yes, remove listing' }));
    expect(await screen.findByText('Add a short note for the audit log.')).toBeInTheDocument();
    await user.type(screen.getByLabelText('Note for the audit log'), 'Spam');
    await user.click(screen.getByRole('button', { name: 'Yes, remove listing' }));
    await screen.findByText('Removed “Suspicious drill”.');
    expect(api.called('POST', '/admin/listings/7/remove')[0].body).toEqual({ note: 'Spam' });
  });

  it('renders the audit log', async () => {
    mockApi(
      {
        'GET /admin/audit-log': {
          body: page([
            {
              action: 'DISABLE_USER',
              targetType: 'USER',
              targetId: 2,
              note: 'Spam account',
              admin: { id: 99, displayName: 'Platform Admin' },
              createdAt: '2026-10-13T10:00:00+05:30',
            },
          ]),
        },
      },
      { me: admin },
    );
    renderApp('/admin/audit');
    expect(await screen.findByText('DISABLE USER')).toBeInTheDocument();
    expect(screen.getByText('Spam account')).toBeInTheDocument();
  });
});
