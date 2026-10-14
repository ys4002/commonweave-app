import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { apiError, ananya, mockApi, renderApp } from './utils';

const emptyDash = {
  upcomingHandoffs: [],
  needsAction: [],
  suggestions: [],
  circleActivity: [],
  unreadNotifications: 2,
};

describe('login', () => {
  it('redirects anonymous visitors to /login', async () => {
    mockApi({}, { me: null });
    renderApp('/explore');
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('validates fields client-side without calling the API', async () => {
    const api = mockApi({}, { me: null });
    const user = userEvent.setup();
    renderApp('/login');
    await user.click(await screen.findByRole('button', { name: 'Sign in' }));
    expect(await screen.findByText('Enter your email or username.')).toBeInTheDocument();
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
    expect(screen.getByLabelText('Email or username')).toHaveAttribute('aria-invalid', 'true');
    expect(api.called('POST', '/auth/login')).toHaveLength(0);
  });

  it('signs in, sends the CSRF header and lands on the dashboard', async () => {
    document.cookie = 'XSRF-TOKEN=tok123';
    let signedIn = false;
    const api = mockApi(
      {
        'GET /auth/me': () =>
          signedIn ? { body: ananya } : apiError(401, 'UNAUTHENTICATED', 'Not signed in'),
        'POST /auth/login': () => {
          signedIn = true;
          return { body: ananya };
        },
        'GET /dashboard': { body: emptyDash },
      },
      { me: null },
    );
    const user = userEvent.setup();
    renderApp('/login');
    await user.type(await screen.findByLabelText('Email or username'), 'Ananya@Example.in');
    await user.type(screen.getByLabelText('Password'), 'Welcome@123');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('heading', { name: /Namaste, Ananya/ })).toBeInTheDocument();
    const call = api.called('POST', '/auth/login')[0];
    expect(call.headers.get('X-XSRF-TOKEN')).toBe('tok123');
    expect(call.body).toEqual({ email: 'ananya@example.in', password: 'Welcome@123' });
    expect(await screen.findByText(/2 unread notifications/)).toBeInTheDocument();
  });

  it('shows the server message on bad credentials', async () => {
    mockApi(
      { 'POST /auth/login': apiError(401, 'UNAUTHENTICATED', 'Email or password is incorrect') },
      { me: null },
    );
    const user = userEvent.setup();
    renderApp('/login');
    await user.type(await screen.findByLabelText('Email or username'), 'a@b.in');
    await user.type(screen.getByLabelText('Password'), 'wrongpass');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect');
  });
});

describe('signup', () => {
  it('requires an 8 character password', async () => {
    const api = mockApi({}, { me: null });
    const user = userEvent.setup();
    renderApp('/signup');
    await user.type(await screen.findByLabelText('Display name'), 'Asha');
    await user.type(screen.getByLabelText('Email'), 'asha@example.in');
    await user.type(screen.getByLabelText('Password'), 'short');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('Use at least 8 characters.')).toBeInTheDocument();
    expect(api.called('POST', '/auth/signup')).toHaveLength(0);
  });

  it('maps server fieldErrors onto the form fields', async () => {
    mockApi(
      {
        'POST /auth/signup': apiError(400, 'VALIDATION_FAILED', 'Validation failed', [
          { field: 'email', message: 'must be a well-formed email address' },
          { field: 'displayName', message: 'size must be between 2 and 60' },
        ]),
      },
      { me: null },
    );
    const user = userEvent.setup();
    renderApp('/signup');
    await user.type(await screen.findByLabelText('Display name'), 'A');
    await user.type(screen.getByLabelText('Email'), 'asha@example.in');
    await user.type(screen.getByLabelText('Password'), 'longenough1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    expect(await screen.findByText('must be a well-formed email address')).toBeInTheDocument();
    expect(screen.getByText('size must be between 2 and 60')).toBeInTheDocument();
    expect(screen.getByLabelText('Display name')).toHaveAttribute('aria-invalid', 'true');
  });

  it('maps EMAIL_TAKEN to the email field', async () => {
    mockApi(
      { 'POST /auth/signup': apiError(409, 'EMAIL_TAKEN', 'Email already registered') },
      { me: null },
    );
    const user = userEvent.setup();
    renderApp('/signup');
    await user.type(await screen.findByLabelText('Display name'), 'Asha');
    await user.type(screen.getByLabelText('Email'), 'asha@example.in');
    await user.type(screen.getByLabelText('Password'), 'longenough1');
    await user.click(screen.getByRole('button', { name: 'Create account' }));
    await waitFor(() =>
      expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true'),
    );
    expect(screen.getByText(/already has an account/)).toBeInTheDocument();
  });
});

describe('onboarding guard', () => {
  it('redirects to /onboarding while user.onboarded is false', async () => {
    mockApi(
      {
        'GET /circles': {
          body: { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 },
        },
      },
      { me: { ...ananya, onboarded: false, circles: [] } },
    );
    renderApp('/explore');
    expect(await screen.findByRole('heading', { name: 'Set up your profile' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Finish setup' })).toBeDisabled();
  });
});
