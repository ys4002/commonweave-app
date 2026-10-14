import { useSearchParams } from 'react-router-dom';
import { useImpact } from '../api/hooks';
import { useAuth } from '../auth/auth';
import { useCategoryLabel } from '../components/domain';
import { Empty, ErrorBox, Field, Loading, PageHead, useDocumentTitle } from '../components/ui';
import { formatInr, formatNumber } from '../lib/format';

const DAYS = [30, 90, 180, 365];

function Estimate() {
  return <span className="tag tag--warn estimate-flag">Estimate</span>;
}

export function ImpactPage() {
  useDocumentTitle('Impact');
  const { user } = useAuth();
  const catLabel = useCategoryLabel();
  const [sp, setSp] = useSearchParams();
  const circleId = sp.get('circleId') ? Number(sp.get('circleId')) : undefined;
  const days = Number(sp.get('days')) || 90;
  const q = useImpact({ circleId }, days);
  const set = (k: string, v: string) => {
    const next = new URLSearchParams(sp);
    if (v) next.set(k, v);
    else next.delete(k);
    setSp(next);
  };
  const scopeName = circleId
    ? (user?.circles.find((c) => c.circleId === circleId)?.name ?? `Circle ${circleId}`)
    : 'You';

  return (
    <div className="page">
      <PageHead eyebrow="Impact" title={circleId ? `${scopeName}: impact` : 'Your impact'} />
      <form className="filters" aria-label="Impact scope" onSubmit={(e) => e.preventDefault()}>
        <div className="form-grid">
          <Field label="Show impact for">
            {(p) => (
              <select
                {...p}
                value={circleId ?? ''}
                onChange={(e) => set('circleId', e.target.value)}
              >
                <option value="">Me</option>
                {user?.circles.map((c) => (
                  <option key={c.circleId} value={c.circleId}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="Period">
            {(p) => (
              <select {...p} value={days} onChange={(e) => set('days', e.target.value)}>
                {DAYS.map((d) => (
                  <option key={d} value={d}>
                    Last {d} days
                  </option>
                ))}
              </select>
            )}
          </Field>
        </div>
      </form>
      <div aria-live="polite">
        {q.isLoading && <Loading rows={3} label="Loading impact" />}
        {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      </div>
      {q.data && (
        <>
          <div className="alert alert--info" role="note">
            <strong>These are estimates.</strong>{' '}
            {q.data.disclaimer ||
              'Rough planning figures based on stated assumptions, not measurements.'}
          </div>
          <section aria-labelledby="num-h">
            <h2 id="num-h" className="sr-only">
              Numbers for the last {q.data.periodDays} days
            </h2>
            <div className="stats">
              <div className="stat">
                <div className="stat__num">{formatNumber(q.data.completedExchanges)}</div>
                <div className="stat__label">Completed exchanges</div>
              </div>
              <div className="stat">
                <div className="stat__num">{formatNumber(q.data.activeMembers)}</div>
                <div className="stat__label">Active members</div>
              </div>
              <div className="stat">
                <div className="stat__num">{formatInr(q.data.rupeesSavedEstimate)}</div>
                <div className="stat__label">
                  Rupees saved
                  <Estimate />
                </div>
              </div>
              <div className="stat">
                <div className="stat__num">{formatNumber(q.data.co2KgAvoidedEstimate, 1)} kg</div>
                <div className="stat__label">
                  CO₂e avoided
                  <Estimate />
                </div>
              </div>
              <div className="stat">
                <div className="stat__num">{formatNumber(q.data.hoursShared, 1)}</div>
                <div className="stat__label">
                  Hours shared
                  <Estimate />
                </div>
              </div>
            </div>
          </section>

          <section className="section" aria-labelledby="cat-h">
            <h2 id="cat-h">By category</h2>
            {q.data.byCategory.length === 0 ? (
              <Empty title="No completed exchanges in this period">
                Once a handoff is completed or returned, it counts here.
              </Empty>
            ) : (
              <div className="table-wrap">
                <table className="data">
                  <thead>
                    <tr>
                      <th scope="col">Category</th>
                      <th scope="col">Exchanges</th>
                      <th scope="col">Rupees saved (estimate)</th>
                    </tr>
                  </thead>
                  <tbody>
                    {q.data.byCategory.map((r) => (
                      <tr key={r.category}>
                        <th scope="row">{catLabel(r.category)}</th>
                        <td>{r.count}</td>
                        <td>{formatInr(r.rupeesSaved)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </section>

          <section className="section" aria-labelledby="meth-h">
            <h2 id="meth-h">How we estimate</h2>
            <ul className="ledger">
              {q.data.methodology.map((m) => (
                <li key={m.label}>
                  <strong>{m.label}</strong>
                  <p className="small">
                    <code>{m.formula}</code>
                  </p>
                  <p className="muted small">{m.assumption}</p>
                </li>
              ))}
            </ul>
          </section>
        </>
      )}
    </div>
  );
}
