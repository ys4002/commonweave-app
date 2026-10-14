import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  useCloseListing,
  useCreateReport,
  useListing,
  useMatches,
  useProposeHandoff,
} from '../api/hooks';
import { isApiError } from '../api/client';
import type { ListingDetail } from '../api/types';
import {
  HandoffStatusTag,
  ListingStatusTag,
  MatchList,
  TypeTag,
  useCategoryLabel,
} from '../components/domain';
import {
  Empty,
  ErrorBox,
  Field,
  FormError,
  Loading,
  PageHead,
  useDocumentTitle,
} from '../components/ui';
import { useToast } from '../components/Toast';
import { KIND_LABEL, formatInr, formatRange } from '../lib/format';

export function ListingDetailPage() {
  const id = Number(useParams().id);
  const q = useListing(id);
  useDocumentTitle(q.data?.title ?? 'Listing');
  if (q.isLoading)
    return (
      <div className="page">
        <Loading rows={3} label="Loading listing" />
      </div>
    );
  if (q.isError)
    return (
      <div className="page">
        <ErrorBox error={q.error} onRetry={() => q.refetch()} />
        <Link to="/explore">Back to Explore</Link>
      </div>
    );
  return <Detail l={q.data!} />;
}

function Detail({ l }: { l: ListingDetail }) {
  const catLabel = useCategoryLabel();
  const matches = useMatches(l.id);
  const item = l.kind === 'ITEM';
  return (
    <div className="page">
      <PageHead eyebrow={`${KIND_LABEL[l.kind]} · ${l.circleName}`} title={l.title}>
        {l.canEdit && l.status !== 'REMOVED' && (
          <Link to={`/listings/${l.id}/edit`} className="btn btn--ghost">
            Edit listing
          </Link>
        )}
      </PageHead>
      <div className="cols-2">
        <div>
          <div className="tags">
            <TypeTag type={l.type} />
            <span className="tag">{KIND_LABEL[l.kind]}</span>
            <span className="tag tag--plain">{catLabel(l.category)}</span>
            <ListingStatusTag status={l.status} />
          </div>
          {l.description && <p className="lede">{l.description}</p>}
          {l.tags.length > 0 && (
            <ul className="tags" aria-label="Tags" style={{ listStyle: 'none', padding: 0 }}>
              {l.tags.map((t) => (
                <li key={t} className="chip">
                  {t}
                </li>
              ))}
            </ul>
          )}
          <section className="card card--stamp" aria-labelledby="facts-h">
            <h2 id="facts-h">Details</h2>
            <dl className="facts">
              <dt>Availability</dt>
              <dd>{formatRange(l.availableFrom, l.availableTo)}</dd>
              <dt>Pickup</dt>
              <dd>{l.pickupPreference || 'Flexible, agree in the handoff'}</dd>
              {item ? (
                <>
                  <dt>Estimated value</dt>
                  <dd className="price">{formatInr(l.estimatedValueInr)}</dd>
                  <dt>Deposit</dt>
                  <dd>{l.depositInr ? formatInr(l.depositInr) : 'None'}</dd>
                  <dt>Return</dt>
                  <dd>
                    {l.returnExpected ? 'To be returned after use' : 'A gift, no return needed'}
                  </dd>
                </>
              ) : (
                <>
                  <dt>Time involved</dt>
                  <dd>{l.hoursEstimate ? `About ${l.hoursEstimate} hours` : 'Not specified'}</dd>
                </>
              )}
              <dt>{l.type === 'OFFER' ? 'Offered by' : 'Requested by'}</dt>
              <dd>
                {l.owner.displayName} ·{' '}
                <span>
                  {l.completedExchangesOfOwner} completed exchange
                  {l.completedExchangesOfOwner === 1 ? '' : 's'}
                </span>
              </dd>
            </dl>
          </section>

          <section className="section" aria-labelledby="match-h">
            <h2 id="match-h">Good fits in this circle</h2>
            {matches.isLoading && <Loading rows={2} label="Finding matches" />}
            {matches.isError && (
              <ErrorBox error={matches.error} onRetry={() => matches.refetch()} />
            )}
            {matches.data && matches.data.length === 0 && (
              <Empty title="No matches yet">
                When a neighbour posts something that fits, it will be listed here with the reasons.
              </Empty>
            )}
            {matches.data && matches.data.length > 0 && <MatchList matches={matches.data} />}
          </section>
        </div>
        <aside className="stack" aria-label="Actions">
          <Actions l={l} />
        </aside>
      </div>
    </div>
  );
}

function Actions({ l }: { l: ListingDetail }) {
  if (l.canEdit) return <OwnerActions l={l} />;
  return (
    <>
      <div className="card">
        {l.myHandoff ? (
          <>
            <h2>Your handoff</h2>
            <p>
              <HandoffStatusTag status={l.myHandoff.status} />
            </p>
            <Link className="btn" to={`/handoffs/${l.myHandoff.id}`}>
              View handoff
            </Link>
          </>
        ) : l.status === 'ACTIVE' ? (
          <Propose l={l} />
        ) : (
          <>
            <h2>Not available right now</h2>
            <p className="muted">
              This listing is {l.status.toLowerCase()}, so handoffs are paused.
            </p>
          </>
        )}
      </div>
      <ReportBox l={l} />
    </>
  );
}

function OwnerActions({ l }: { l: ListingDetail }) {
  const close = useCloseListing();
  const toast = useToast();
  const [confirming, setConfirming] = useState(false);
  const canClose = l.status === 'ACTIVE';
  return (
    <div className="card">
      <h2>Your listing</h2>
      <p className="muted">
        {l.status === 'RESERVED'
          ? 'A handoff is scheduled, so editing and closing are paused until it finishes.'
          : 'Neighbours can propose handoffs. You confirm the ones that suit you.'}
      </p>
      <div className="stack">
        <Link className="btn btn--ghost" to="/handoffs">
          Review proposals
        </Link>
        {canClose && !confirming && (
          <button type="button" className="btn btn--danger" onClick={() => setConfirming(true)}>
            Close listing
          </button>
        )}
        {canClose && confirming && (
          <div role="group" aria-label="Confirm closing">
            <p>Close this listing? Neighbours will no longer see it.</p>
            <div className="row">
              <button
                type="button"
                className="btn btn--danger"
                disabled={close.isPending}
                onClick={() =>
                  close.mutate(l.id, {
                    onSuccess: () => toast.success('Listing closed.'),
                    onError: (e) =>
                      toast.error(isApiError(e) ? e.message : 'Could not close the listing.'),
                  })
                }
              >
                Yes, close it
              </button>
              <button type="button" className="btn btn--ghost" onClick={() => setConfirming(false)}>
                Keep open
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

function Propose({ l }: { l: ListingDetail }) {
  const nav = useNavigate();
  const toast = useToast();
  const propose = useProposeHandoff();
  const [open, setOpen] = useState(false);
  const [message, setMessage] = useState('');
  const verb = l.type === 'OFFER' ? 'ask to borrow or receive this' : 'offer to help with this';

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    propose.mutate(
      { listingId: l.id, message: message.trim() || undefined },
      {
        onSuccess: (h) => {
          toast.success('Proposal sent. You will be notified when it is confirmed.');
          nav(`/handoffs/${h.id}`);
        },
      },
    );
  };

  if (!open)
    return (
      <>
        <h2>Interested?</h2>
        <p className="muted">
          You can {verb}. {l.owner.displayName} will confirm a time.
        </p>
        <button type="button" className="btn btn--signal" onClick={() => setOpen(true)}>
          Propose handoff
        </button>
      </>
    );
  return (
    <form onSubmit={submit}>
      <h2>Propose a handoff</h2>
      <FormError error={propose.error} />
      <Field
        label="Message to the owner"
        hint="Optional. Suggest a time or say what you need it for."
      >
        {(p) => (
          <textarea
            {...p}
            placeholder="Could I pick it up this Saturday near the society gate?"
            value={message}
            onChange={(e) => setMessage(e.target.value)}
          />
        )}
      </Field>
      <div className="row">
        <button type="submit" className="btn btn--signal" disabled={propose.isPending}>
          {propose.isPending ? 'Sending…' : 'Send proposal'}
        </button>
        <button type="button" className="btn btn--ghost" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </form>
  );
}

function ReportBox({ l }: { l: ListingDetail }) {
  const toast = useToast();
  const report = useCreateReport();
  const [open, setOpen] = useState(false);
  const [reason, setReason] = useState('');
  const [err, setErr] = useState('');

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const r = reason.trim();
    if (r.length < 5 || r.length > 500) {
      setErr('Please explain in 5 to 500 characters.');
      return;
    }
    setErr('');
    report.mutate(
      { circleId: l.circleId, targetType: 'LISTING', targetId: l.id, reason: r },
      {
        onSuccess: () => {
          toast.success('Thanks. Your circle moderators will take a look.');
          setOpen(false);
          setReason('');
        },
        onError: (e) => isApiError(e) && setErr(e.fieldMap().reason ?? e.message),
      },
    );
  };

  if (!open)
    return (
      <button type="button" className="btn btn--ghost btn--small" onClick={() => setOpen(true)}>
        Report listing
      </button>
    );
  return (
    <form className="card card--flat" onSubmit={submit} noValidate>
      <h2>Report this listing</h2>
      <Field label="What is wrong?" error={err} hint="Only your circle's moderators see this.">
        {(p) => <textarea {...p} value={reason} onChange={(e) => setReason(e.target.value)} />}
      </Field>
      <div className="row">
        <button type="submit" className="btn btn--danger" disabled={report.isPending}>
          Submit report
        </button>
        <button type="button" className="btn btn--ghost" onClick={() => setOpen(false)}>
          Cancel
        </button>
      </div>
    </form>
  );
}
