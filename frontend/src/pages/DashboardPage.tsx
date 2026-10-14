import { Link } from 'react-router-dom';
import { useDashboard } from '../api/hooks';
import { useAuth } from '../auth/auth';
import { HandoffCard, MatchList } from '../components/domain';
import { Empty, ErrorBox, Loading, PageHead, useDocumentTitle } from '../components/ui';
import { timeAgo } from '../lib/format';

export function DashboardPage() {
  useDocumentTitle('Home');
  const { user } = useAuth();
  const q = useDashboard();

  return (
    <div className="page">
      <PageHead
        eyebrow="Field journal"
        title={`Namaste, ${user?.displayName.split(' ')[0] ?? 'neighbour'}`}
      >
        <Link to="/listings/new" className="btn btn--signal">
          New listing
        </Link>
      </PageHead>
      {q.isLoading && <Loading rows={4} label="Loading your dashboard" />}
      {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <>
          <p className="lede">
            <Link to="/notifications">
              {q.data.unreadNotifications === 0
                ? 'No unread notifications.'
                : `${q.data.unreadNotifications} unread notification${q.data.unreadNotifications === 1 ? '' : 's'}.`}
            </Link>
          </p>
          <section className="section" aria-labelledby="needs-h">
            <h2 id="needs-h">Needs your action</h2>
            {q.data.needsAction.length === 0 ? (
              <Empty title="Nothing waiting on you">
                New proposals and overdue returns will show up here.
              </Empty>
            ) : (
              <div className="grid">
                {q.data.needsAction.map((h) => (
                  <HandoffCard key={h.id} handoff={h} />
                ))}
              </div>
            )}
          </section>
          <section className="section" aria-labelledby="up-h">
            <h2 id="up-h">Upcoming handoffs</h2>
            {q.data.upcomingHandoffs.length === 0 ? (
              <Empty
                title="No handoffs scheduled"
                action={
                  <Link to="/explore" className="btn btn--ghost">
                    Browse what neighbours share
                  </Link>
                }
              >
                Propose one from a listing and it will appear here once confirmed.
              </Empty>
            ) : (
              <div className="grid">
                {q.data.upcomingHandoffs.map((h) => (
                  <HandoffCard key={h.id} handoff={h} />
                ))}
              </div>
            )}
          </section>
          <div className="cols-2">
            <section className="section" aria-labelledby="sug-h">
              <h2 id="sug-h">Suggested matches</h2>
              {q.data.suggestions.length === 0 ? (
                <Empty title="No suggestions yet">
                  Post an offer or a request and we will look for good fits in your circle.
                </Empty>
              ) : (
                <ul className="ledger">
                  {q.data.suggestions.map((s) => (
                    <li key={`${s.myListing.id}-${s.match.listing.id}`}>
                      <p className="small muted">
                        For your <Link to={`/listings/${s.myListing.id}`}>{s.myListing.title}</Link>
                      </p>
                      <MatchList matches={[s.match]} />
                    </li>
                  ))}
                </ul>
              )}
            </section>
            <section className="section" aria-labelledby="act-h">
              <h2 id="act-h">Circle activity</h2>
              {q.data.circleActivity.length === 0 ? (
                <Empty title="Quiet for now">Activity from your circles will appear here.</Empty>
              ) : (
                <ul className="ledger">
                  {q.data.circleActivity.map((a, i) => (
                    <li key={i}>
                      {a.text}
                      <span className="muted small">
                        {' '}
                        · {a.circleName} · {timeAgo(a.at)}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </section>
          </div>
        </>
      )}
    </div>
  );
}
