import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useLogout, useUnreadCount } from '../api/hooks';
import { useAuth } from '../auth/auth';
import { useToast } from './Toast';

export function Logo() {
  return (
    <svg viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <rect width="32" height="32" rx="7" fill="#e8a317" />
      <path
        d="M6 20c4-10 10-10 10 0s6 10 10 0"
        fill="none"
        stroke="#12261f"
        strokeWidth="2.6"
        strokeLinecap="round"
        strokeDasharray="1 0"
      />
      <path
        d="M6 13c4-6 10-6 10 0s6 6 10 0"
        fill="none"
        stroke="#12261f"
        strokeWidth="1.6"
        strokeDasharray="2 3"
      />
    </svg>
  );
}

const NAV: { to: string; label: string; end?: boolean }[] = [
  { to: '/', label: 'Home', end: true },
  { to: '/explore', label: 'Explore' },
  { to: '/handoffs', label: 'Handoffs' },
  { to: '/circles', label: 'Circles' },
  { to: '/impact', label: 'Impact' },
];

export function AppShell() {
  const [open, setOpen] = useState(false);
  const loc = useLocation();
  const nav = useNavigate();
  const toast = useToast();
  const logout = useLogout();
  const unread = useUnreadCount();
  const count = unread.data?.count ?? 0;
  const isAdmin = useAuth().user?.platformRole === 'ADMIN';

  useEffect(() => {
    setOpen(false);
    window.scrollTo?.(0, 0);
    document.getElementById('main')?.focus({ preventScroll: true });
  }, [loc.pathname]);

  const signOut = () =>
    logout.mutate(undefined, {
      onSettled: () => {
        toast.success('You are signed out. See you at the next handoff.');
        nav('/login', { replace: true });
      },
    });

  return (
    <>
      <a className="skip-link" href="#main">
        Skip to main content
      </a>
      <header className="topbar">
        <div className="topbar__inner">
          <Link to="/" className="brand">
            <Logo />
            Commonweave
          </Link>
          <button
            type="button"
            className="menu-btn"
            aria-expanded={open}
            aria-controls="primary-nav"
            onClick={() => setOpen((o) => !o)}
          >
            {open ? 'Close menu' : 'Menu'}
          </button>
          <nav id="primary-nav" className="mainnav" aria-label="Primary" data-open={open}>
            <ul>
              {NAV.map((n) => (
                <li key={n.to}>
                  <NavLink to={n.to} end={n.end}>
                    {n.label}
                  </NavLink>
                </li>
              ))}
              {isAdmin && (
                <li>
                  <NavLink to="/admin">Admin</NavLink>
                </li>
              )}
              <li>
                <NavLink to="/notifications">
                  Notifications
                  {count > 0 && (
                    <span className="badge-count" aria-label={`${count} unread`}>
                      {count}
                    </span>
                  )}
                </NavLink>
              </li>
            </ul>
            <ul>
              <li>
                <NavLink to="/me">Profile</NavLink>
              </li>
              <li>
                <button type="button" className="linklike" onClick={signOut}>
                  Sign out
                </button>
              </li>
            </ul>
          </nav>
        </div>
      </header>
      <main id="main" tabIndex={-1}>
        <Outlet />
      </main>
    </>
  );
}
