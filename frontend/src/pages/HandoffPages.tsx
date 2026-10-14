import { useState, type FormEvent } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useHandoff, useHandoffs, useTransition } from '../api/hooks';
import { isApiError } from '../api/client';
import type { HandoffDetail, HandoffStatus, TransitionAction, TransitionInput } from '../api/types';
import { HandoffCard, HandoffStatusTag } from '../components/domain';
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
import { ACTION_LABEL, STATUS_LABEL, formatDateTime, istLocalToIso } from '../lib/format';

export function HandoffsPage() {
  useDocumentTitle('Handoffs');
  const [sp, setSp] = useSearchParams();
  const status = (sp.get('status') as HandoffStatus) || undefined;
  const role = (sp.get('role') as 'PROVIDER' | 'RECIPIENT') || undefined;
  const active = sp.get('active') === 'true';
  const page = Number(sp.get('page')) || 0;
  const q = useHandoffs({ status, role, active: active || undefined, page, size: 12 });

  const set = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (resetPage) next.delete('page');
    setSp(next);
  };

  return (
    <div className="page">
      <PageHead eyebrow="Handoffs" title="Your handoffs" />
      <form className="filters" aria-label="Filter handoffs" onSubmit={(e) => e.preventDefault()}>
        <div className="form-grid">
          <Field label="Status">
            {(p) => (
              <select {...p} value={status ?? ''} onChange={(e) => set({ status: e.target.value })}>
                <option value="">Any status</option>
                {(Object.keys(STATUS_LABEL) as HandoffStatus[]).map((s) => (
                  <option key={s} value={s}>
                    {STATUS_LABEL[s]}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Your role">
            {(p) => (
              <select {...p} value={role ?? ''} onChange={(e) => set({ role: e.target.value })}>
                <option value="">Giving or receiving</option>
                <option value="PROVIDER">Giving</option>
                <option value="RECIPIENT">Receiving</option>
              </select>
            )}
          </Field>
          <label className="field field--inline">
            <input
              type="checkbox"
              checked={active}
              onChange={(e) => set({ active: e.target.checked ? 'true' : '' })}
            />
            <span>Only active handoffs</span>
          </label>
        </div>
      </form>
      <div aria-live="polite">
        {q.isLoading && <Loading rows={3} label="Loading handoffs" />}
        {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data && q.data.content.length === 0 && (
          <Empty
            title="No handoffs here"
            action={
              <Link to="/explore" className="btn btn--ghost">
                Find something to borrow
              </Link>
            }
          >
            Propose one from any listing to get started.
          </Empty>
        )}
        {q.data && q.data.content.length > 0 && (
          <div className="grid">
            {q.data.content.map((h) => (
              <HandoffCard key={h.id} handoff={h} />
            ))}
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
    </div>
  );
}

function Stepper({ h }: { h: HandoffDetail }) {
  const steps: HandoffStatus[] = [
    'PROPOSED',
    'SCHEDULED',
    'HANDED_OFF',
    h.returnExpected ? 'RETURNED' : 'COMPLETED',
  ];
  const reached = new Set(h.events.map((e) => e.toStatus));
  return (
    <ol className="stepper" aria-label="Handoff progress">
      {steps.map((s) => (
        <li
          key={s}
          data-state={
            s === h.status ? 'current' : reached.has(s) || s === 'PROPOSED' ? 'done' : 'todo'
          }
          aria-current={s === h.status ? 'step' : undefined}
        >
          {STATUS_LABEL[s]}
          {s === h.status && <span className="sr-only"> (current)</span>}
        </li>
      ))}
    </ol>
  );
}

export function HandoffDetailPage() {
  const id = Number(useParams().id);
  const q = useHandoff(id);
  useDocumentTitle(q.data ? `Handoff: ${q.data.listing.title}` : 'Handoff');
  if (q.isLoading)
    return (
      <div className="page">
        <Loading rows={3} label="Loading handoff" />
      </div>
    );
  if (q.isError)
    return (
      <div className="page">
        <ErrorBox error={q.error} onRetry={() => q.refetch()} />
        <Link to="/handoffs">Back to handoffs</Link>
      </div>
    );
  const h = q.data!;
  return (
    <div className="page">
      <PageHead eyebrow="Handoff" title={h.listing.title}>
        <HandoffStatusTag status={h.status} />
      </PageHead>
      {h.status !== 'CANCELLED' && <Stepper h={h} />}
      <div className="cols-2">
        <div>
          <section className="card card--stamp" aria-labelledby="hd-h">
            <h2 id="hd-h">Details</h2>
            <dl className="facts">
              <dt>Listing</dt>
              <dd>
                <Link to={`/listings/${h.listing.id}`}>{h.listing.title}</Link>
              </dd>
              <dt>Your role</dt>
              <dd>{h.role === 'PROVIDER' ? 'Giving' : 'Receiving'}</dd>
              <dt>With</dt>
              <dd>{h.counterpart.displayName}</dd>
              {h.message && (
                <>
                  <dt>Message</dt>
                  <dd>{h.message}</dd>
                </>
              )}
              <dt>Scheduled</dt>
              <dd>{h.scheduledAt ? formatDateTime(h.scheduledAt) : 'Not scheduled yet'}</dd>
              <dt>Pickup</dt>
              <dd>{h.pickupNote || h.pickupPreference || 'To be agreed'}</dd>
              {h.returnExpected && (
                <>
                  <dt>Return by</dt>
                  <dd>{h.returnBy ? formatDateTime(h.returnBy) : 'Not set'}</dd>
                </>
              )}
            </dl>
          </section>
          <section className="section" aria-labelledby="tl-h">
            <h2 id="tl-h">Timeline</h2>
            <ol className="thread">
              {h.events.map((e, i) => (
                <li key={i}>
                  <strong>{STATUS_LABEL[e.toStatus]}</strong> by {e.actor.displayName}
                  <time>{formatDateTime(e.at)}</time>
                  {e.note && <p className="muted">“{e.note}”</p>}
                </li>
              ))}
            </ol>
          </section>
        </div>
        <aside aria-label="Handoff actions">
          <ActionPanel h={h} />
        </aside>
      </div>
    </div>
  );
}

const nowIstLocal = () => new Date(Date.now() + 5.5 * 3600e3).toISOString().slice(0, 16);

function ActionPanel({ h }: { h: HandoffDetail }) {
  const toast = useToast();
  const tr = useTransition(h.id);
  const [panel, setPanel] = useState<TransitionAction | null>(null);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [scheduledAt, setScheduledAt] = useState('');
  const [pickupNote, setPickupNote] = useState('');
  const [returnBy, setReturnBy] = useState('');
  const [note, setNote] = useState('');

  const run = (input: TransitionInput, success: string) =>
    tr.mutate(input, {
      onSuccess: () => {
        toast.success(success);
        setPanel(null);
        setErrors({});
      },
      onError: (e) => {
        if (isApiError(e) && e.fieldErrors.length) setErrors(e.fieldMap());
        else toast.error(isApiError(e) ? e.message : 'Could not update the handoff.');
      },
    });

  const confirm = (ev: FormEvent) => {
    ev.preventDefault();
    const errs: Record<string, string> = {};
    if (!scheduledAt) errs.scheduledAt = 'Pick a date and time for the handoff.';
    else if (new Date(istLocalToIso(scheduledAt)).getTime() <= Date.now())
      errs.scheduledAt = 'Pick a time in the future.';
    if (returnBy && scheduledAt && returnBy <= scheduledAt)
      errs.returnBy = 'Return time must be after the handoff.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    run(
      {
        action: 'CONFIRM',
        scheduledAt: istLocalToIso(scheduledAt),
        pickupNote: pickupNote.trim() || undefined,
        returnBy: returnBy ? istLocalToIso(returnBy) : undefined,
      },
      'Handoff confirmed and scheduled.',
    );
  };

  const simple: Partial<Record<TransitionAction, string>> = {
    MARK_HANDED_OFF: 'Marked as handed off.',
    MARK_RETURNED: 'Marked as returned. Thank you!',
    COMPLETE: 'Handoff completed. Thank you!',
  };

  if (h.allowedActions.length === 0)
    return (
      <div className="card">
        <h2>No actions</h2>
        <p className="muted">
          {h.status === 'COMPLETED' || h.status === 'RETURNED'
            ? 'This exchange is complete.'
            : h.status === 'CANCELLED'
              ? 'This handoff was cancelled.'
              : 'Waiting on the other person.'}
        </p>
      </div>
    );

  return (
    <div className="card">
      <h2>Next steps</h2>
      <div className="stack">
        <div className="row">
          {h.allowedActions.map((a) => (
            <button
              key={a}
              type="button"
              className={`btn ${a === 'CANCEL' ? 'btn--danger' : a === 'CONFIRM' ? 'btn--signal' : ''}`}
              aria-expanded={a === 'CONFIRM' || a === 'CANCEL' ? panel === a : undefined}
              disabled={tr.isPending}
              onClick={() => {
                setErrors({});
                if (a === 'CONFIRM' || a === 'CANCEL') setPanel(panel === a ? null : a);
                else run({ action: a }, simple[a] ?? 'Done.');
              }}
            >
              {ACTION_LABEL[a]}
            </button>
          ))}
        </div>

        {panel === 'CONFIRM' && h.allowedActions.includes('CONFIRM') && (
          <form onSubmit={confirm} noValidate aria-label="Schedule handoff">
            <FormError error={tr.isError && !Object.keys(errors).length ? tr.error : null} />
            <Field label="Handoff date and time (IST)" error={errors.scheduledAt}>
              {(p) => (
                <input
                  {...p}
                  className="input"
                  type="datetime-local"
                  min={nowIstLocal()}
                  value={scheduledAt}
                  onChange={(e) => setScheduledAt(e.target.value)}
                />
              )}
            </Field>
            <Field
              label="Pickup note"
              error={errors.pickupNote}
              hint="Optional. Where exactly should you meet?"
            >
              {(p) => (
                <input
                  {...p}
                  className="input"
                  placeholder="Society gate, near the lift lobby"
                  value={pickupNote}
                  onChange={(e) => setPickupNote(e.target.value)}
                />
              )}
            </Field>
            {h.returnExpected && (
              <Field label="Return by (IST)" error={errors.returnBy} hint="Optional.">
                {(p) => (
                  <input
                    {...p}
                    className="input"
                    type="datetime-local"
                    value={returnBy}
                    onChange={(e) => setReturnBy(e.target.value)}
                  />
                )}
              </Field>
            )}
            <button type="submit" className="btn btn--signal" disabled={tr.isPending}>
              {tr.isPending ? 'Scheduling…' : 'Schedule it'}
            </button>
          </form>
        )}

        {panel === 'CANCEL' && h.allowedActions.includes('CANCEL') && (
          <form
            aria-label="Cancel handoff"
            onSubmit={(ev) => {
              ev.preventDefault();
              run({ action: 'CANCEL', note: note.trim() || undefined }, 'Handoff cancelled.');
            }}
          >
            <Field label="Reason" hint="Optional. The other person will see this.">
              {(p) => <textarea {...p} value={note} onChange={(e) => setNote(e.target.value)} />}
            </Field>
            <div className="row">
              <button type="submit" className="btn btn--danger" disabled={tr.isPending}>
                Yes, cancel it
              </button>
              <button type="button" className="btn btn--ghost" onClick={() => setPanel(null)}>
                Keep handoff
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
