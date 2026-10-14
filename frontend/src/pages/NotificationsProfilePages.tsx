import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useMarkAllRead, useMarkRead, useNotifications, useUpdateProfile } from '../api/hooks';
import { isApiError } from '../api/client';
import { useAuth } from '../auth/auth';
import {
  Empty,
  ErrorBox,
  Field,
  FormError,
  Loading,
  PageHead,
  Pagination,
  useDocumentTitle,
} from '../components/ui';
import { useToast } from '../components/Toast';
import { formatDateTime } from '../lib/format';

export function NotificationsPage() {
  useDocumentTitle('Notifications');
  const toast = useToast();
  const [sp, setSp] = useSearchParams();
  const unreadOnly = sp.get('unread') === 'true';
  const page = Number(sp.get('page')) || 0;
  const q = useNotifications({ unread: unreadOnly || undefined, page, size: 15 });
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(sp);
    if (v) next.set(k, v);
    else next.delete(k);
    if (k !== 'page') next.delete('page');
    setSp(next);
  };

  return (
    <div className="page page--narrow">
      <PageHead eyebrow="Notifications" title="What's new">
        <button
          type="button"
          className="btn btn--ghost"
          disabled={markAll.isPending}
          onClick={() =>
            markAll.mutate(undefined, { onSuccess: () => toast.success('All caught up.') })
          }
        >
          Mark all as read
        </button>
      </PageHead>
      <label className="field field--inline">
        <input
          type="checkbox"
          checked={unreadOnly}
          onChange={(e) => set('unread', e.target.checked ? 'true' : '')}
        />
        <span>Show unread only</span>
      </label>
      <div aria-live="polite">
        {q.isLoading && <Loading rows={3} label="Loading notifications" />}
        {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data?.content.length === 0 && (
          <Empty title={unreadOnly ? 'No unread notifications' : 'No notifications yet'}>
            Handoff updates and reminders will show up here.
          </Empty>
        )}
        <ul className="ledger">
          {q.data?.content.map((n) => (
            <li
              key={n.id}
              className={n.readAt ? '' : 'notif-unread'}
              style={{ paddingLeft: n.readAt ? 0 : 'var(--s-3)' }}
            >
              <div className="row row--between">
                <div>
                  <strong>{n.title}</strong>
                  {!n.readAt && <span className="sr-only"> (unread)</span>}
                  {n.body && <p>{n.body}</p>}
                  <p className="muted small">
                    {formatDateTime(n.createdAt)}
                    {n.link && n.link.startsWith('/') && (
                      <>
                        {' · '}
                        <Link to={n.link}>Open</Link>
                      </>
                    )}
                  </p>
                </div>
                {!n.readAt && (
                  <button
                    type="button"
                    className="btn btn--ghost btn--small"
                    disabled={markRead.isPending}
                    aria-label={`Mark "${n.title}" as read`}
                    onClick={() => markRead.mutate(n.id)}
                  >
                    Mark as read
                  </button>
                )}
              </div>
            </li>
          ))}
        </ul>
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set('page', p ? String(p) : '')}
          />
        )}
      </div>
    </div>
  );
}

export function ProfilePage() {
  useDocumentTitle('Profile');
  const { user } = useAuth();
  const toast = useToast();
  const update = useUpdateProfile();
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [bio, setBio] = useState(user?.bio ?? '');
  const [locality, setLocality] = useState(user?.locality ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});
  useEffect(() => {
    if (user) {
      setDisplayName(user.displayName);
      setBio(user.bio ?? '');
      setLocality(user.locality ?? '');
    }
  }, [user]);

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    if (!displayName.trim()) {
      setErrors({ displayName: 'Display name cannot be empty.' });
      return;
    }
    setErrors({});
    update.mutate(
      { displayName: displayName.trim(), bio: bio.trim(), locality: locality.trim() },
      {
        onSuccess: () => toast.success('Profile saved.'),
        onError: (e) => isApiError(e) && setErrors(e.fieldMap()),
      },
    );
  };

  return (
    <div className="page page--narrow">
      <PageHead eyebrow="Profile" title="Your profile" />
      <p className="muted">Signed in as {user?.email}</p>
      <form onSubmit={submit} noValidate>
        <FormError error={update.isError && !Object.keys(errors).length ? update.error : null} />
        <Field label="Display name" error={errors.displayName}>
          {(p) => (
            <input
              {...p}
              className="input"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          )}
        </Field>
        <Field label="Locality" error={errors.locality}>
          {(p) => (
            <input
              {...p}
              className="input"
              placeholder="e.g. Bandra West, near Linking Road"
              value={locality}
              onChange={(e) => setLocality(e.target.value)}
            />
          )}
        </Field>
        <Field label="About you" error={errors.bio}>
          {(p) => <textarea {...p} value={bio} onChange={(e) => setBio(e.target.value)} />}
        </Field>
        <button type="submit" className="btn btn--signal" disabled={update.isPending}>
          {update.isPending ? 'Saving…' : 'Save profile'}
        </button>
      </form>
      <section className="section" aria-labelledby="my-circles-h">
        <h2 id="my-circles-h">Your circles</h2>
        {user?.circles.length === 0 ? (
          <Empty
            title="You have not joined a circle"
            action={
              <Link className="btn btn--ghost" to="/circles">
                Find a circle
              </Link>
            }
          >
            Circles are how you find neighbours to share with.
          </Empty>
        ) : (
          <ul className="ledger">
            {user?.circles.map((c) => (
              <li key={c.circleId}>
                <Link to={`/circles/${c.circleId}`}>{c.name}</Link>{' '}
                <span className="tag tag--plain">
                  {c.role === 'MODERATOR' ? 'Moderator' : 'Member'}
                </span>
              </li>
            ))}
          </ul>
        )}
        <p>
          <Link to="/explore?mine=true">See my listings</Link>
        </p>
      </section>
    </div>
  );
}

export function NotFoundPage() {
  useDocumentTitle('Not found');
  return (
    <div className="page page--narrow">
      <Empty
        title="We could not find that page"
        action={
          <Link to="/" className="btn btn--ghost">
            Back to home
          </Link>
        }
      >
        It may have moved, or you may not have access to it.
      </Empty>
    </div>
  );
}
