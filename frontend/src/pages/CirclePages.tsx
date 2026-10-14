import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import {
  useCircle,
  useCircleActivity,
  useCircles,
  useCreateCircle,
  useJoinCircle,
  useLeaveCircle,
  useMembers,
} from '../api/hooks';
import { isApiError } from '../api/client';
import type { CircleSummary } from '../api/types';
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
import { formatDate, timeAgo } from '../lib/format';

function CircleCard({ c }: { c: CircleSummary }) {
  return (
    <article className="card card--link card--stamp">
      <div className="tags">
        {c.myRole && (
          <span className="tag tag--ok">
            {c.myRole === 'MODERATOR' ? 'You moderate' : 'Member'}
          </span>
        )}
      </div>
      <h3>
        <Link to={`/circles/${c.id}`}>{c.name}</Link>
      </h3>
      <p className="muted small">
        {c.area}, {c.city}
      </p>
      <p>{c.description}</p>
      <p className="small muted">
        {c.memberCount} members · {c.activeListingCount} active listings
      </p>
    </article>
  );
}

export function CirclesPage() {
  useDocumentTitle('Circles');
  const [sp, setSp] = useSearchParams();
  const page = Number(sp.get('page')) || 0;
  const [text, setText] = useState(sp.get('q') ?? '');
  const [city, setCity] = useState(sp.get('city') ?? '');
  const q = useCircles({
    q: sp.get('q') || undefined,
    city: sp.get('city') || undefined,
    page,
    size: 12,
  });
  const [creating, setCreating] = useState(false);

  const search = (ev: FormEvent) => {
    ev.preventDefault();
    const next = new URLSearchParams();
    if (text.trim()) next.set('q', text.trim());
    if (city.trim()) next.set('city', city.trim());
    setSp(next);
  };

  return (
    <div className="page">
      <PageHead eyebrow="Circles" title="Neighbourhood circles">
        <button
          type="button"
          className="btn btn--signal"
          onClick={() => setCreating((c) => !c)}
          aria-expanded={creating}
        >
          Start a circle
        </button>
      </PageHead>
      {creating && <CreateCircle onDone={() => setCreating(false)} />}
      <form className="filters" role="search" aria-label="Find circles" onSubmit={search}>
        <div className="form-grid">
          <Field label="Search circles">
            {(p) => (
              <input
                {...p}
                className="input"
                type="search"
                placeholder="Koramangala, Bandra, Hauz Khas"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            )}
          </Field>
          <Field label="City">
            {(p) => (
              <input
                {...p}
                className="input"
                placeholder="Bengaluru"
                value={city}
                onChange={(e) => setCity(e.target.value)}
              />
            )}
          </Field>
        </div>
        <button type="submit" className="btn">
          Search
        </button>
      </form>
      <div aria-live="polite">
        {q.isLoading && <Loading rows={3} label="Loading circles" />}
        {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data && q.data.content.length === 0 && (
          <Empty title="No circles found">
            Try another search, or start the first circle for your neighbourhood.
          </Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="grid">
            {q.data.content.map((c) => (
              <CircleCard key={c.id} c={c} />
            ))}
          </div>
        )}
        {q.data && (
          <Pagination
            page={q.data.page}
            totalPages={q.data.totalPages}
            onPage={(p) => {
              const next = new URLSearchParams(sp);
              if (p) next.set('page', String(p));
              else next.delete('page');
              setSp(next);
            }}
          />
        )}
      </div>
    </div>
  );
}

function CreateCircle({ onDone }: { onDone: () => void }) {
  const nav = useNavigate();
  const toast = useToast();
  const create = useCreateCircle();
  const [f, setF] = useState({ name: '', city: '', area: '', description: '', guidelines: '' });
  const [errors, setErrors] = useState<Record<string, string>>({});
  const upd = (k: keyof typeof f, v: string) => setF((s) => ({ ...s, [k]: v }));

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const errs: Record<string, string> = {};
    if (f.name.trim().length < 3) errs.name = 'Give the circle a name of at least 3 characters.';
    if (!f.city.trim()) errs.city = 'Enter the city.';
    if (!f.area.trim()) errs.area = 'Enter the neighbourhood or area.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    create.mutate(
      {
        name: f.name.trim(),
        city: f.city.trim(),
        area: f.area.trim(),
        description: f.description.trim(),
        guidelines: f.guidelines.trim(),
      },
      {
        onSuccess: (c) => {
          toast.success('Circle created. You are its first moderator.');
          onDone();
          nav(`/circles/${c.id}`);
        },
        onError: (e) => isApiError(e) && setErrors(e.fieldMap()),
      },
    );
  };

  return (
    <form className="card section" onSubmit={submit} noValidate aria-label="Start a circle">
      <h2>Start a circle</h2>
      <FormError error={create.isError && !Object.keys(errors).length ? create.error : null} />
      <div className="form-grid">
        <Field label="Circle name" error={errors.name}>
          {(p) => (
            <input
              {...p}
              className="input"
              placeholder="Indiranagar Lane 12 Neighbours"
              value={f.name}
              onChange={(e) => upd('name', e.target.value)}
            />
          )}
        </Field>
        <Field label="City" error={errors.city}>
          {(p) => (
            <input
              {...p}
              className="input"
              placeholder="Bengaluru"
              value={f.city}
              onChange={(e) => upd('city', e.target.value)}
            />
          )}
        </Field>
        <Field label="Area" error={errors.area}>
          {(p) => (
            <input
              {...p}
              className="input"
              placeholder="Indiranagar"
              value={f.area}
              onChange={(e) => upd('area', e.target.value)}
            />
          )}
        </Field>
        <Field label="Description" error={errors.description} className="span-2">
          {(p) => (
            <textarea
              {...p}
              value={f.description}
              onChange={(e) => upd('description', e.target.value)}
            />
          )}
        </Field>
        <Field label="Community guidelines" error={errors.guidelines} className="span-2">
          {(p) => (
            <textarea
              {...p}
              placeholder="Be kind. Return things clean. Meet in the society lobby."
              value={f.guidelines}
              onChange={(e) => upd('guidelines', e.target.value)}
            />
          )}
        </Field>
      </div>
      <div className="row">
        <button type="submit" className="btn btn--signal" disabled={create.isPending}>
          Create circle
        </button>
        <button type="button" className="btn btn--ghost" onClick={onDone}>
          Cancel
        </button>
      </div>
    </form>
  );
}

export function CircleDetailPage() {
  const id = Number(useParams().id);
  const q = useCircle(id);
  const toast = useToast();
  const join = useJoinCircle();
  const leave = useLeaveCircle();
  const [confirmLeave, setConfirmLeave] = useState(false);
  const isMember = q.data?.myStatus === 'ACTIVE';
  const members = useMembers(id, isMember);
  const activity = useCircleActivity(id, isMember);
  useDocumentTitle(q.data?.name ?? 'Circle');

  if (q.isLoading)
    return (
      <div className="page">
        <Loading rows={3} label="Loading circle" />
      </div>
    );
  if (q.isError)
    return (
      <div className="page">
        <ErrorBox error={q.error} onRetry={() => q.refetch()} />
        <Link to="/circles">Back to circles</Link>
      </div>
    );
  const c = q.data!;
  const err = (e: unknown) => toast.error(isApiError(e) ? e.message : 'Something went wrong.');

  return (
    <div className="page">
      <PageHead eyebrow={`${c.area}, ${c.city}`} title={c.name}>
        {isMember ? (
          <>
            <Link className="btn btn--ghost" to={`/explore?circleId=${c.id}`}>
              Browse listings
            </Link>
            <Link className="btn btn--ghost" to={`/impact?circleId=${c.id}`}>
              Circle impact
            </Link>
            {c.myRole === 'MODERATOR' && (
              <Link className="btn btn--ghost" to={`/circles/${c.id}/moderate`}>
                Moderate
              </Link>
            )}
          </>
        ) : null}
      </PageHead>
      <div className="cols-2">
        <div>
          <p className="lede">{c.description}</p>
          <section className="card card--stamp" aria-labelledby="guide-h">
            <h2 id="guide-h">Community guidelines</h2>
            <p style={{ whiteSpace: 'pre-line' }}>
              {c.guidelines || 'This circle has no written guidelines yet.'}
            </p>
            <p className="muted small">
              Moderators: {c.moderators.map((m) => m.displayName).join(', ') || 'none listed'}
            </p>
          </section>

          {isMember && (
            <>
              <section className="section" aria-labelledby="mem-h">
                <h2 id="mem-h">Members</h2>
                {members.isLoading && <Loading rows={2} label="Loading members" />}
                {members.isError && (
                  <ErrorBox error={members.error} onRetry={() => members.refetch()} />
                )}
                {members.data && (
                  <ul className="ledger">
                    {members.data.map((m) => (
                      <li key={m.userId}>
                        <strong>{m.displayName}</strong>{' '}
                        {m.role === 'MODERATOR' && <span className="tag">Moderator</span>}
                        <span className="muted small">
                          {' '}
                          · joined {formatDate(m.joinedAt)} · {m.completedExchanges} completed
                          exchange
                          {m.completedExchanges === 1 ? '' : 's'}
                        </span>
                      </li>
                    ))}
                  </ul>
                )}
              </section>
              <section className="section" aria-labelledby="act-h">
                <h2 id="act-h">Recent activity</h2>
                {activity.isLoading && <Loading rows={1} label="Loading activity" />}
                {activity.isError && (
                  <ErrorBox error={activity.error} onRetry={() => activity.refetch()} />
                )}
                {activity.data?.length === 0 && (
                  <Empty title="Quiet so far">Be the first to post something.</Empty>
                )}
                <ul className="ledger">
                  {activity.data?.map((a, i) => (
                    <li key={i}>
                      {a.text} <span className="muted small">· {timeAgo(a.at)}</span>
                    </li>
                  ))}
                </ul>
              </section>
            </>
          )}
        </div>
        <aside className="card" aria-label="Membership">
          <h2>Membership</h2>
          <p className="muted small">
            {c.memberCount} members · {c.activeListingCount} active listings
          </p>
          {c.myStatus === 'SUSPENDED' || c.myStatus === 'REMOVED' ? (
            <p>Your membership here is {c.myStatus.toLowerCase()}. Contact a moderator.</p>
          ) : isMember ? (
            confirmLeave ? (
              <div role="group" aria-label="Confirm leaving">
                <p>Leave {c.name}?</p>
                <div className="row">
                  <button
                    type="button"
                    className="btn btn--danger"
                    disabled={leave.isPending}
                    onClick={() =>
                      leave.mutate(c.id, {
                        onSuccess: () => {
                          toast.success(`You left ${c.name}.`);
                          setConfirmLeave(false);
                        },
                        onError: (e) => {
                          err(e);
                          setConfirmLeave(false);
                        },
                      })
                    }
                  >
                    Yes, leave
                  </button>
                  <button
                    type="button"
                    className="btn btn--ghost"
                    onClick={() => setConfirmLeave(false)}
                  >
                    Stay
                  </button>
                </div>
              </div>
            ) : (
              <button
                type="button"
                className="btn btn--ghost"
                onClick={() => setConfirmLeave(true)}
              >
                Leave circle
              </button>
            )
          ) : (
            <button
              type="button"
              className="btn btn--signal"
              disabled={join.isPending}
              onClick={() =>
                join.mutate(c.id, {
                  onSuccess: () => toast.success(`Welcome to ${c.name}.`),
                  onError: err,
                })
              }
            >
              Join circle
            </button>
          )}
        </aside>
      </div>
    </div>
  );
}
