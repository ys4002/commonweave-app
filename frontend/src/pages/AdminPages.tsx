import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, NavLink, useParams, useSearchParams } from 'react-router-dom';
import {
  useAdminAudit,
  useAdminCircles,
  useAdminListings,
  useAdminOverview,
  useAdminRemoveListing,
  useAdminReports,
  useAdminResetPassword,
  useAdminUpdateUser,
  useAdminUser,
  useAdminUsers,
} from '../api/hooks';
import { isApiError } from '../api/client';
import type { AdminUser, ListingStatus, PlatformRole } from '../api/types';
import { Dialog } from '../components/Dialog';
import { ListingStatusTag, useCategoryLabel } from '../components/domain';
import { useToast } from '../components/Toast';
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
import { LISTING_STATUS_LABEL, STATUS_LABEL, formatDateTime } from '../lib/format';

export function ForbiddenPage() {
  useDocumentTitle('Access denied');
  return (
    <div className="page page--narrow">
      <Empty
        title="403: You do not have access to this page"
        action={
          <Link to="/" className="btn btn--ghost">
            Back to home
          </Link>
        }
      >
        The admin area is only for platform administrators.
      </Empty>
    </div>
  );
}

const SECTIONS: { to: string; label: string; end?: boolean }[] = [
  { to: '/admin', label: 'Overview', end: true },
  { to: '/admin/users', label: 'Users' },
  { to: '/admin/circles', label: 'Circles' },
  { to: '/admin/listings', label: 'Listings' },
  { to: '/admin/reports', label: 'Reports' },
  { to: '/admin/audit', label: 'Audit log' },
];

function AdminFrame(props: { title: string; children: ReactNode }) {
  useDocumentTitle(`Admin: ${props.title}`);
  return (
    <div className="page">
      <PageHead eyebrow="Platform admin" title={props.title} />
      <nav aria-label="Admin sections">
        <ul className="subnav">
          {SECTIONS.map((s) => (
            <li key={s.to}>
              <NavLink to={s.to} end={s.end}>
                {s.label}
              </NavLink>
            </li>
          ))}
        </ul>
      </nav>
      {props.children}
    </div>
  );
}

/** Reads/writes one page of URL-synced list state. */
function useListParams(keys: string[]) {
  const [sp, setSp] = useSearchParams();
  const set = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (resetPage) next.delete('page');
    setSp(next);
  };
  const values = Object.fromEntries(keys.map((k) => [k, sp.get(k) ?? '']));
  return { values, set, page: Number(sp.get('page')) || 0 };
}

function PagedState(props: {
  q: { isLoading: boolean; isError: boolean; error: unknown; refetch: () => unknown };
  label: string;
}) {
  return (
    <>
      {props.q.isLoading && <Loading rows={3} label={`Loading ${props.label}`} />}
      {props.q.isError && <ErrorBox error={props.q.error} onRetry={() => props.q.refetch()} />}
    </>
  );
}

// ---------- overview ----------
export function AdminOverviewPage() {
  const q = useAdminOverview();
  const d = q.data;
  const breakdown = (rec: Record<string, number>, labels: Record<string, string>) => (
    <ul className="stat__breakdown">
      {Object.entries(rec).map(([k, v]) => (
        <li key={k}>
          {labels[k] ?? k}: {v}
        </li>
      ))}
    </ul>
  );
  return (
    <AdminFrame title="Overview">
      <PagedState q={q} label="overview" />
      {d && (
        <div className="stats">
          <div className="stat">
            <div className="stat__num">{d.users.total}</div>
            <div className="stat__label">Users</div>
            <ul className="stat__breakdown">
              <li>Disabled: {d.users.disabled}</li>
              <li>Admins: {d.users.admins}</li>
            </ul>
          </div>
          <div className="stat">
            <div className="stat__num">{d.circles}</div>
            <div className="stat__label">Circles</div>
          </div>
          <div className="stat">
            <div className="stat__num">{Object.values(d.listings).reduce((a, b) => a + b, 0)}</div>
            <div className="stat__label">Listings</div>
            {breakdown(d.listings, LISTING_STATUS_LABEL)}
          </div>
          <div className="stat">
            <div className="stat__num">{Object.values(d.handoffs).reduce((a, b) => a + b, 0)}</div>
            <div className="stat__label">Handoffs</div>
            {breakdown(d.handoffs, STATUS_LABEL)}
          </div>
          <div className="stat">
            <div className="stat__num">{d.openReports}</div>
            <div className="stat__label">
              <Link to="/admin/reports">Open reports</Link>
            </div>
          </div>
        </div>
      )}
    </AdminFrame>
  );
}

// ---------- users ----------
export function AdminUsersPage() {
  const { values, set, page } = useListParams(['q', 'enabled']);
  const [text, setText] = useState(values.q);
  const q = useAdminUsers({
    q: values.q || undefined,
    enabled: values.enabled ? values.enabled === 'true' : undefined,
    page,
    size: 20,
  });
  const [disabling, setDisabling] = useState<AdminUser | null>(null);
  const [resetting, setResetting] = useState<AdminUser | null>(null);
  const toast = useToast();
  const update = useAdminUpdateUser();
  const fail = (e: unknown) =>
    toast.error(isApiError(e) ? e.message : 'Could not update the user.');

  const patch = (
    u: AdminUser,
    body: { enabled?: boolean; platformRole?: PlatformRole },
    ok: string,
  ) =>
    update.mutate({ id: u.id, patch: body }, { onSuccess: () => toast.success(ok), onError: fail });

  return (
    <AdminFrame title="Users">
      <form
        className="filters"
        role="search"
        aria-label="Find users"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          set({ q: text.trim() });
        }}
      >
        <div className="form-grid">
          <Field label="Search users">
            {(p) => (
              <input
                {...p}
                className="input"
                type="search"
                placeholder="Name or email"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            )}
          </Field>
          <Field label="Account status">
            {(p) => (
              <select
                {...p}
                value={values.enabled}
                onChange={(e) => set({ enabled: e.target.value })}
              >
                <option value="">All accounts</option>
                <option value="true">Enabled</option>
                <option value="false">Disabled</option>
              </select>
            )}
          </Field>
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      <div aria-live="polite">
        <PagedState q={q} label="users" />
        {q.data?.content.length === 0 && (
          <Empty title="No users match">Try a different search.</Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="table-wrap">
            <table className="data">
              <caption className="sr-only">Platform users</caption>
              <thead>
                <tr>
                  <th scope="col">User</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Last login</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((u) => (
                  <tr key={u.id}>
                    <th scope="row">
                      <Link to={`/admin/users/${u.id}`}>{u.displayName}</Link>
                      <br />
                      <span className="muted small">{u.email}</span>
                    </th>
                    <td>
                      <select
                        aria-label={`Platform role for ${u.displayName}`}
                        value={u.platformRole}
                        disabled={update.isPending}
                        onChange={(e) =>
                          patch(
                            u,
                            { platformRole: e.target.value as PlatformRole },
                            `Updated role for ${u.displayName}.`,
                          )
                        }
                      >
                        <option value="USER">User</option>
                        <option value="ADMIN">Admin</option>
                      </select>
                    </td>
                    <td>
                      <span className={`tag ${u.enabled ? 'tag--ok' : 'tag--bad'}`}>
                        {u.enabled ? 'Enabled' : 'Disabled'}
                      </span>
                    </td>
                    <td className="small">
                      {u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'Never'}
                    </td>
                    <td>
                      <div className="row">
                        {u.enabled ? (
                          <button
                            type="button"
                            className="btn btn--danger btn--small"
                            onClick={() => setDisabling(u)}
                          >
                            Disable<span className="sr-only"> {u.displayName}</span>
                          </button>
                        ) : (
                          <button
                            type="button"
                            className="btn btn--ghost btn--small"
                            disabled={update.isPending}
                            onClick={() =>
                              patch(u, { enabled: true }, `${u.displayName} is enabled again.`)
                            }
                          >
                            Enable<span className="sr-only"> {u.displayName}</span>
                          </button>
                        )}
                        <button
                          type="button"
                          className="btn btn--ghost btn--small"
                          onClick={() => setResetting(u)}
                        >
                          Reset password<span className="sr-only"> for {u.displayName}</span>
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set({ page: p ? String(p) : '' }, false)}
          />
        )}
      </div>
      {disabling && (
        <Dialog title={`Disable ${disabling.displayName}?`} onClose={() => setDisabling(null)}>
          <p>
            They will be signed out and unable to log in until you enable the account again. Their
            listings and handoffs stay as they are.
          </p>
          <div className="row">
            <button
              type="button"
              className="btn btn--danger"
              disabled={update.isPending}
              onClick={() => {
                const u = disabling;
                update.mutate(
                  { id: u.id, patch: { enabled: false } },
                  {
                    onSuccess: () => toast.success(`${u.displayName} is disabled.`),
                    onError: fail,
                    onSettled: () => setDisabling(null),
                  },
                );
              }}
            >
              Yes, disable account
            </button>
            <button type="button" className="btn btn--ghost" onClick={() => setDisabling(null)}>
              Keep enabled
            </button>
          </div>
        </Dialog>
      )}
      {resetting && <ResetPasswordDialog user={resetting} onClose={() => setResetting(null)} />}
    </AdminFrame>
  );
}

function ResetPasswordDialog({ user, onClose }: { user: AdminUser; onClose: () => void }) {
  const toast = useToast();
  const reset = useAdminResetPassword();
  const [pw, setPw] = useState('');
  const [error, setError] = useState('');
  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    if (pw.length < 8) {
      setError('Use at least 8 characters.');
      return;
    }
    setError('');
    reset.mutate(
      { id: user.id, newPassword: pw },
      {
        onSuccess: () => {
          toast.success(`Password reset for ${user.displayName}.`);
          onClose();
        },
        onError: (e) => isApiError(e) && setError(e.fieldMap().newPassword ?? e.message),
      },
    );
  };
  return (
    <Dialog title={`Reset password for ${user.displayName}`} onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <Field
          label="New password"
          error={error}
          hint="At least 8 characters. Share it with them securely."
        >
          {(p) => (
            <input
              {...p}
              className="input"
              type="password"
              autoComplete="new-password"
              value={pw}
              onChange={(e) => setPw(e.target.value)}
            />
          )}
        </Field>
        <div className="row">
          <button type="submit" className="btn btn--signal" disabled={reset.isPending}>
            {reset.isPending ? 'Resetting…' : 'Set new password'}
          </button>
          <button type="button" className="btn btn--ghost" onClick={onClose}>
            Cancel
          </button>
        </div>
      </form>
    </Dialog>
  );
}

export function AdminUserDetailPage() {
  const id = Number(useParams().id);
  const q = useAdminUser(id);
  const catLabel = useCategoryLabel();
  const u = q.data;
  return (
    <AdminFrame title={u ? u.displayName : 'User'}>
      <p>
        <Link to="/admin/users">Back to users</Link>
      </p>
      <PagedState q={q} label="user" />
      {u && (
        <>
          <section className="card card--stamp" aria-labelledby="au-h">
            <h2 id="au-h">Profile</h2>
            <dl className="facts">
              <dt>Email</dt>
              <dd>{u.email}</dd>
              <dt>Platform role</dt>
              <dd>{u.platformRole === 'ADMIN' ? 'Admin' : 'User'}</dd>
              <dt>Account</dt>
              <dd>{u.enabled ? 'Enabled' : 'Disabled'}</dd>
              <dt>Locality</dt>
              <dd>{u.locality || 'Not set'}</dd>
              <dt>About</dt>
              <dd>{u.bio || 'Not set'}</dd>
              <dt>Joined</dt>
              <dd>{formatDateTime(u.createdAt)}</dd>
              <dt>Last login</dt>
              <dd>{u.lastLoginAt ? formatDateTime(u.lastLoginAt) : 'Never'}</dd>
              <dt>Activity</dt>
              <dd>
                {u.listingCount} listings · {u.completedExchanges} completed exchanges
              </dd>
              <dt>Circles</dt>
              <dd>
                {u.circles.length === 0
                  ? 'None'
                  : u.circles
                      .map((c) => `${c.name} (${c.role.toLowerCase()}, ${c.status.toLowerCase()})`)
                      .join('; ')}
              </dd>
            </dl>
          </section>
          <section className="section" aria-labelledby="rl-h">
            <h2 id="rl-h">Recent listings</h2>
            {u.recentListings.length === 0 ? (
              <Empty title="No listings yet">This user has not posted anything.</Empty>
            ) : (
              <ul className="ledger">
                {u.recentListings.map((l) => (
                  <li key={l.id}>
                    <Link to={`/listings/${l.id}`}>{l.title}</Link>{' '}
                    <ListingStatusTag status={l.status} />{' '}
                    <span className="muted small">
                      · {catLabel(l.category)} · {l.circleName}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
          <section className="section" aria-labelledby="rh-h">
            <h2 id="rh-h">Recent handoffs</h2>
            {u.recentHandoffs.length === 0 ? (
              <Empty title="No handoffs yet">Nothing to show.</Empty>
            ) : (
              <ul className="ledger">
                {u.recentHandoffs.map((h) => (
                  <li key={h.id}>
                    {h.listing.title}{' '}
                    <span className="tag tag--plain">{STATUS_LABEL[h.status]}</span>{' '}
                    <span className="muted small">
                      · with {h.counterpart.displayName} · {formatDateTime(h.updatedAt)}
                    </span>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </>
      )}
    </AdminFrame>
  );
}

// ---------- circles ----------
export function AdminCirclesPage() {
  const { set, page } = useListParams([]);
  const q = useAdminCircles({ page, size: 20 });
  return (
    <AdminFrame title="Circles">
      <div aria-live="polite">
        <PagedState q={q} label="circles" />
        {q.data?.content.length === 0 && (
          <Empty title="No circles yet">Circles appear once people create them.</Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="table-wrap">
            <table className="data">
              <caption className="sr-only">All circles</caption>
              <thead>
                <tr>
                  <th scope="col">Circle</th>
                  <th scope="col">Place</th>
                  <th scope="col">Members</th>
                  <th scope="col">Active listings</th>
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((c) => (
                  <tr key={c.id}>
                    <th scope="row">{c.name}</th>
                    <td>
                      {c.area}, {c.city}
                    </td>
                    <td>{c.memberCount}</td>
                    <td>{c.activeListingCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set({ page: p ? String(p) : '' }, false)}
          />
        )}
      </div>
    </AdminFrame>
  );
}

// ---------- listings ----------
export function AdminListingsPage() {
  const { values, set, page } = useListParams(['q', 'status']);
  const [text, setText] = useState(values.q);
  const q = useAdminListings({
    q: values.q || undefined,
    status: values.status || undefined,
    page,
    size: 20,
  });
  const [removing, setRemoving] = useState<{ id: number; title: string } | null>(null);
  const toast = useToast();
  const remove = useAdminRemoveListing();
  const [note, setNote] = useState('');
  const [noteErr, setNoteErr] = useState('');

  const confirmRemove = (ev: FormEvent) => {
    ev.preventDefault();
    if (!removing) return;
    if (!note.trim()) {
      setNoteErr('Add a short note for the audit log.');
      return;
    }
    remove.mutate(
      { id: removing.id, note: note.trim() },
      {
        onSuccess: () => {
          toast.success(`Removed “${removing.title}”.`);
          setRemoving(null);
          setNote('');
        },
        onError: (e) => {
          toast.error(isApiError(e) ? e.message : 'Could not remove the listing.');
          setRemoving(null);
        },
      },
    );
  };

  return (
    <AdminFrame title="Listings">
      <form
        className="filters"
        role="search"
        aria-label="Find listings"
        onSubmit={(e: FormEvent) => {
          e.preventDefault();
          set({ q: text.trim() });
        }}
      >
        <div className="form-grid">
          <Field label="Search listings">
            {(p) => (
              <input
                {...p}
                className="input"
                type="search"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            )}
          </Field>
          <Field label="Listing status">
            {(p) => (
              <select
                {...p}
                value={values.status}
                onChange={(e) => set({ status: e.target.value })}
              >
                <option value="">Any status</option>
                {(Object.keys(LISTING_STATUS_LABEL) as ListingStatus[]).map((s) => (
                  <option key={s} value={s}>
                    {LISTING_STATUS_LABEL[s]}
                  </option>
                ))}
              </select>
            )}
          </Field>
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      <div aria-live="polite">
        <PagedState q={q} label="listings" />
        {q.data?.content.length === 0 && (
          <Empty title="No listings match">Try a different filter.</Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="table-wrap">
            <table className="data">
              <caption className="sr-only">All listings</caption>
              <thead>
                <tr>
                  <th scope="col">Listing</th>
                  <th scope="col">Owner</th>
                  <th scope="col">Circle</th>
                  <th scope="col">Status</th>
                  <th scope="col">Actions</th>
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((l) => (
                  <tr key={l.id}>
                    <th scope="row">
                      <Link to={`/listings/${l.id}`}>{l.title}</Link>
                    </th>
                    <td>{l.owner.displayName}</td>
                    <td>{l.circleName}</td>
                    <td>
                      <ListingStatusTag status={l.status} />
                    </td>
                    <td>
                      {l.status !== 'REMOVED' && (
                        <button
                          type="button"
                          className="btn btn--danger btn--small"
                          onClick={() => {
                            setNote('');
                            setNoteErr('');
                            setRemoving({ id: l.id, title: l.title });
                          }}
                        >
                          Remove<span className="sr-only"> {l.title}</span>
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set({ page: p ? String(p) : '' }, false)}
          />
        )}
      </div>
      {removing && (
        <Dialog title={`Remove “${removing.title}”?`} onClose={() => setRemoving(null)}>
          <form onSubmit={confirmRemove} noValidate>
            <p>
              The listing is hidden from everyone but its owner and moderators. Open proposals are
              cancelled.
            </p>
            <FormError error={remove.isError ? remove.error : null} />
            <Field label="Note for the audit log" error={noteErr}>
              {(p) => <textarea {...p} value={note} onChange={(e) => setNote(e.target.value)} />}
            </Field>
            <div className="row">
              <button type="submit" className="btn btn--danger" disabled={remove.isPending}>
                Yes, remove listing
              </button>
              <button type="button" className="btn btn--ghost" onClick={() => setRemoving(null)}>
                Keep listing
              </button>
            </div>
          </form>
        </Dialog>
      )}
    </AdminFrame>
  );
}

// ---------- reports ----------
export function AdminReportsPage() {
  const { values, set, page } = useListParams(['status']);
  const status = values.status || 'OPEN';
  const q = useAdminReports({ status, page, size: 20 });
  return (
    <AdminFrame title="Reports">
      <form className="filters" aria-label="Filter reports" onSubmit={(e) => e.preventDefault()}>
        <Field label="Report status">
          {(p) => (
            <select {...p} value={status} onChange={(e) => set({ status: e.target.value })}>
              <option value="OPEN">Open</option>
              <option value="RESOLVED">Resolved</option>
              <option value="DISMISSED">Dismissed</option>
            </select>
          )}
        </Field>
      </form>
      <div aria-live="polite">
        <PagedState q={q} label="reports" />
        {q.data?.content.length === 0 && (
          <Empty title="No reports here">Nothing to review for this status.</Empty>
        )}
        <ul className="ledger">
          {q.data?.content.map((r) => (
            <li key={r.id}>
              <span className="tag">{r.targetType === 'LISTING' ? 'Listing' : 'Member'}</span>{' '}
              <strong>{r.targetLabel}</strong>
              <p>“{r.reason}”</p>
              <p className="muted small">
                Reported by {r.reporter.displayName} · {formatDateTime(r.createdAt)} ·{' '}
                {r.status.toLowerCase()}
              </p>
            </li>
          ))}
        </ul>
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set({ page: p ? String(p) : '' }, false)}
          />
        )}
      </div>
    </AdminFrame>
  );
}

// ---------- audit ----------
export function AdminAuditPage() {
  const { set, page } = useListParams([]);
  const q = useAdminAudit({ page, size: 25 });
  return (
    <AdminFrame title="Audit log">
      <div aria-live="polite">
        <PagedState q={q} label="audit log" />
        {q.data?.content.length === 0 && (
          <Empty title="Nothing logged yet">Admin actions are recorded here.</Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="table-wrap">
            <table className="data">
              <caption className="sr-only">Admin audit log</caption>
              <thead>
                <tr>
                  <th scope="col">When</th>
                  <th scope="col">Admin</th>
                  <th scope="col">Action</th>
                  <th scope="col">Target</th>
                  <th scope="col">Note</th>
                </tr>
              </thead>
              <tbody>
                {q.data.content.map((a, i) => (
                  <tr key={i}>
                    <td className="small">{formatDateTime(a.createdAt)}</td>
                    <td>{a.admin.displayName}</td>
                    <td>
                      <span className="tag tag--plain">{a.action.replaceAll('_', ' ')}</span>
                    </td>
                    <td>
                      {a.targetType.toLowerCase()} {a.targetId != null ? `#${a.targetId}` : ''}
                    </td>
                    <td>{a.note}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => set({ page: p ? String(p) : '' }, false)}
          />
        )}
      </div>
    </AdminFrame>
  );
}
