import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useLogin, useSignup } from '../api/hooks';
import { isApiError } from '../api/client';
import type { User } from '../api/types';
import { Logo } from '../components/AppShell';
import { Field, FormError, useDocumentTitle } from '../components/ui';
import { useToast } from '../components/Toast';

function landingOr(u: User, from: string): string {
  return u.platformRole === 'ADMIN' ? '/admin' : from;
}
const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
type Errors = Record<string, string>;

function AuthLayout(props: { title: string; blurb: string; children: ReactNode }) {
  return (
    <div className="auth">
      <div className="auth__aside">
        <span className="brand">
          <Logo />
          Commonweave
        </span>
        <h1>{props.title}</h1>
        <p>{props.blurb}</p>
        <p className="small">
          Share a drill with the society next door, trade a Sunday of tutoring for a cycle repair.
          No ratings, no leaderboards. Just neighbours.
        </p>
      </div>
      <main id="main" className="auth__main" tabIndex={-1}>
        {props.children}
      </main>
    </div>
  );
}

function applyServerError(e: unknown, setErrors: (e: Errors) => void) {
  if (isApiError(e) && e.fieldErrors.length > 0) {
    setErrors(e.fieldMap());
  } else if (isApiError(e) && e.code === 'EMAIL_TAKEN') {
    setErrors({ email: 'That email already has an account. Try signing in instead.' });
  }
}

export function LoginPage() {
  useDocumentTitle('Sign in');
  const nav = useNavigate();
  const loc = useLocation();
  const toast = useToast();
  const login = useLogin();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});
  const from = (loc.state as { from?: string } | null)?.from ?? '/';

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const errs: Errors = {};
    if (!email.trim()) errs.email = 'Enter your email or username.';
    if (!password) errs.password = 'Enter your password.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    login.mutate(
      { email: email.trim().toLowerCase(), password },
      {
        onSuccess: (u) => {
          toast.success(`Welcome back, ${u.displayName}.`);
          nav(u.platformRole === 'ADMIN' || u.onboarded ? landingOr(u, from) : '/onboarding', {
            replace: true,
          });
        },
        onError: (e) => applyServerError(e, setErrors),
      },
    );
  };

  return (
    <AuthLayout
      title="Welcome back to your circle."
      blurb="Sign in to see today's handoffs and what your neighbours are sharing."
    >
      <h2>Sign in</h2>
      <form onSubmit={submit} noValidate>
        {Object.keys(errors).length > 0 && (
          <div className="alert" role="alert">
            Please fix the highlighted fields.
          </div>
        )}
        {login.isError && !Object.keys(errors).length && <FormError error={login.error} />}
        <Field label="Email or username" error={errors.email}>
          {(p) => (
            <input
              {...p}
              className="input"
              type="text"
              autoComplete="username"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="asha@example.in"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>
        <Field label="Password" error={errors.password}>
          {(p) => (
            <input
              {...p}
              className="input"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </Field>
        <button type="submit" className="btn btn--signal" disabled={login.isPending}>
          {login.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="section">
        New here? <Link to="/signup">Create an account</Link>
      </p>
    </AuthLayout>
  );
}

export function SignupPage() {
  useDocumentTitle('Create account');
  const nav = useNavigate();
  const toast = useToast();
  const signup = useSignup();
  const [displayName, setDisplayName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<Errors>({});

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const errs: Errors = {};
    if (!displayName.trim()) errs.displayName = 'Tell your neighbours what to call you.';
    if (!EMAIL_RE.test(email.trim())) errs.email = 'Enter a valid email, like asha@example.in.';
    if (password.length < 8) errs.password = 'Use at least 8 characters.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    signup.mutate(
      { displayName: displayName.trim(), email: email.trim().toLowerCase(), password },
      {
        onSuccess: () => {
          toast.success('Account created. One more step: set up your profile.');
          nav('/onboarding', { replace: true });
        },
        onError: (e) => applyServerError(e, setErrors),
      },
    );
  };

  return (
    <AuthLayout
      title="Join the weave."
      blurb="Make an account, pick your neighbourhood circle, and start lending and borrowing."
    >
      <h2>Create your account</h2>
      <form onSubmit={submit} noValidate>
        {Object.keys(errors).length > 0 && (
          <div className="alert" role="alert">
            Please fix the highlighted fields.
          </div>
        )}
        {signup.isError && !Object.keys(errors).length && <FormError error={signup.error} />}
        <Field
          label="Display name"
          error={errors.displayName}
          hint="Shown to people in your circles."
        >
          {(p) => (
            <input
              {...p}
              className="input"
              autoComplete="name"
              placeholder="Ananya Rao"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          )}
        </Field>
        <Field label="Email" error={errors.email}>
          {(p) => (
            <input
              {...p}
              className="input"
              type="email"
              autoComplete="email"
              placeholder="ananya@example.in"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
            />
          )}
        </Field>
        <Field label="Password" error={errors.password} hint="At least 8 characters.">
          {(p) => (
            <input
              {...p}
              className="input"
              type="password"
              autoComplete="new-password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
            />
          )}
        </Field>
        <button type="submit" className="btn btn--signal" disabled={signup.isPending}>
          {signup.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="section">
        Already have an account? <Link to="/login">Sign in</Link>
      </p>
    </AuthLayout>
  );
}
