import { Link } from 'react-router-dom';
import { useMeta } from '../api/hooks';
import type {
  Category,
  HandoffStatus,
  HandoffSummary,
  ListingStatus,
  ListingSummary,
  ListingType,
  Match,
} from '../api/types';
import {
  FALLBACK_CATEGORY_LABELS,
  KIND_LABEL,
  LISTING_STATUS_LABEL,
  STATUS_LABEL,
  factorLabel,
  formatDateTime,
  formatInr,
  formatRange,
} from '../lib/format';

export function useCategoryLabel(): (c: Category) => string {
  const meta = useMeta();
  const map = new Map((meta.data?.categories ?? []).map((c) => [c.code, c.label]));
  return (c) => map.get(c) ?? FALLBACK_CATEGORY_LABELS[c] ?? c;
}

export function TypeTag({ type }: { type: ListingType }) {
  return (
    <span className={`tag ${type === 'OFFER' ? 'tag--offer' : 'tag--request'}`}>
      {type === 'OFFER' ? 'Offer' : 'Request'}
    </span>
  );
}

export function ListingStatusTag({ status }: { status: ListingStatus }) {
  const cls = status === 'ACTIVE' ? 'tag--ok' : status === 'REMOVED' ? 'tag--bad' : 'tag--warn';
  return <span className={`tag ${cls}`}>{LISTING_STATUS_LABEL[status]}</span>;
}

export function HandoffStatusTag({ status }: { status: HandoffStatus }) {
  const cls =
    status === 'COMPLETED' || status === 'RETURNED'
      ? 'tag--ok'
      : status === 'CANCELLED'
        ? 'tag--bad'
        : status === 'PROPOSED'
          ? 'tag--plain'
          : 'tag--warn';
  return <span className={`tag ${cls}`}>{STATUS_LABEL[status]}</span>;
}

export function ListingCard({ listing: l }: { listing: ListingSummary }) {
  const catLabel = useCategoryLabel();
  return (
    <article className="card card--link card--stamp">
      <div className="tags">
        <TypeTag type={l.type} />
        <span className="tag">{KIND_LABEL[l.kind]}</span>
        <span className="tag tag--plain">{catLabel(l.category)}</span>
        {l.status !== 'ACTIVE' && <ListingStatusTag status={l.status} />}
      </div>
      <h3>
        <Link to={`/listings/${l.id}`}>{l.title}</Link>
      </h3>
      <p className="muted small">
        {l.owner.displayName} · {l.circleName}
      </p>
      <dl className="facts small">
        <dt>Available</dt>
        <dd>{formatRange(l.availableFrom, l.availableTo)}</dd>
        {l.pickupPreference && (
          <>
            <dt>Pickup</dt>
            <dd>{l.pickupPreference}</dd>
          </>
        )}
        {l.kind === 'ITEM' && (
          <>
            <dt>Worth</dt>
            <dd>
              <span className="price">{formatInr(l.estimatedValueInr)}</span>
              {l.depositInr ? ` · ${formatInr(l.depositInr)} deposit` : ''}
            </dd>
          </>
        )}
      </dl>
    </article>
  );
}

export function HandoffCard({ handoff: h }: { handoff: HandoffSummary }) {
  return (
    <article className="card card--link">
      <div className="tags">
        <HandoffStatusTag status={h.status} />
        <span className="tag tag--plain">{h.role === 'PROVIDER' ? 'You give' : 'You receive'}</span>
      </div>
      <h3>
        <Link to={`/handoffs/${h.id}`}>{h.listing.title}</Link>
      </h3>
      <p className="muted small">With {h.counterpart.displayName}</p>
      {h.scheduledAt && <p className="small">When: {formatDateTime(h.scheduledAt)}</p>}
      {h.returnExpected && h.returnBy && (
        <p className="small">Return by: {formatDateTime(h.returnBy)}</p>
      )}
    </article>
  );
}

export function MatchList({ matches, heading }: { matches: Match[]; heading?: string }) {
  return (
    <ul className="ledger" aria-label={heading}>
      {matches.map((m) => (
        <li key={m.listing.id}>
          <div className="row row--between">
            <Link to={`/listings/${m.listing.id}`}>
              <strong>{m.listing.title}</strong>
            </Link>
            <span className="score" aria-label={`Match score ${m.score} out of 100`}>
              {m.score}/100
            </span>
          </div>
          <p className="muted small">
            {m.listing.owner.displayName} · {m.listing.type === 'OFFER' ? 'Offer' : 'Request'}
          </p>
          <ul className="reasons" aria-label={`Why ${m.listing.title} matches`}>
            {m.reasons.map((r) => (
              <li key={r.factor}>
                <span>
                  <strong>{factorLabel(r.factor)}</strong>
                  <br />
                  {r.points}/{r.max} pts
                  <span className="meter" aria-hidden="true">
                    <span style={{ width: `${r.max ? (r.points / r.max) * 100 : 0}%` }} />
                  </span>
                </span>
                <span>{r.explanation}</span>
              </li>
            ))}
          </ul>
        </li>
      ))}
    </ul>
  );
}
