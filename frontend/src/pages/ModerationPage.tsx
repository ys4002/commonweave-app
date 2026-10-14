import { useEffect, useState, type FormEvent } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  useCircle,
  useMembers,
  useModerationLog,
  useReports,
  useResolveReport,
  useUpdateCircle,
  useUpdateMember,
} from '../api/hooks';
import { isApiError } from '../api/client';
import type { MemberRole, MemberStatus, Report } from '../api/types';
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
import { formatDateTime } from '../lib/format';

export function ModerationPage() {
  const id = Number(useParams().id);
  const circle = useCircle(id);
  useDocumentTitle('Moderation');
  if (circle.isLoading)
    return (
      <div className="page">
        <Loading label="Loading circle" />
      </div>
    );
  if (circle.isError)
    return (
      <div className="page">
        <ErrorBox error={circle.error} onRetry={() => circle.refetch()} />
      </div>
    );
  const c = circle.data!;
  if (c.myRole !== 'MODERATOR')
    return (
      <div className="page">
        <Empty
          title="Moderators only"
          action={
            <Link to={`/circles/${id}`} className="btn btn--ghost">
              Back to the circle
            </Link>
          }
        >
          Only moderators of {c.name} can see this page.
        </Empty>
      </div>
    );
  return (
    <div className="page">
      <PageHead eyebrow={c.name} title="Moderation">
        <Link to={`/circles/${id}`} className="btn btn--ghost">
          Back to circle
        </Link>
      </PageHead>
      <ReportsSection circleId={id} />
      <MembersSection circleId={id} />
      <GuidelinesSection circleId={id} guidelines={c.guidelines} description={c.description} />
      <LogSection circleId={id} />
    </div>
  );
}

function ReportsSection({ circleId }: { circleId: number }) {
  const q = useReports(circleId, true);
  return (
    <section className="section" aria-labelledby="rep-h">
      <h2 id="rep-h">Open reports</h2>
      {q.isLoading && <Loading rows={2} label="Loading reports" />}
      {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data?.length === 0 && (
        <Empty title="No open reports">Your circle is calm right now.</Empty>
      )}
      <ul className="ledger">
        {q.data?.map((r) => (
          <ReportRow key={r.id} circleId={circleId} r={r} />
        ))}
      </ul>
    </section>
  );
}

function ReportRow({ circleId, r }: { circleId: number; r: Report }) {
  const toast = useToast();
  const resolve = useResolveReport(circleId);
  const [open, setOpen] = useState(false);
  const [note, setNote] = useState('');
  const [remove, setRemove] = useState(false);

  const act = (resolution: 'RESOLVED' | 'DISMISSED') =>
    resolve.mutate(
      {
        reportId: r.id,
        resolution,
        note: note.trim(),
        removeListing: remove && r.targetType === 'LISTING' ? true : undefined,
      },
      {
        onSuccess: () =>
          toast.success(resolution === 'RESOLVED' ? 'Report resolved.' : 'Report dismissed.'),
        onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not update the report.'),
      },
    );

  return (
    <li>
      <div className="row row--between">
        <div>
          <span className="tag">{r.targetType === 'LISTING' ? 'Listing' : 'Member'}</span>{' '}
          <strong>{r.targetLabel}</strong>
          <p className="muted small">
            Reported by {r.reporter.displayName} · {formatDateTime(r.createdAt)}
          </p>
          <p>“{r.reason}”</p>
        </div>
        <button
          type="button"
          className="btn btn--ghost btn--small"
          aria-expanded={open}
          onClick={() => setOpen((o) => !o)}
        >
          {open ? 'Close' : 'Review'}
          <span className="sr-only"> report on {r.targetLabel}</span>
        </button>
      </div>
      {open && (
        <div
          className="card card--flat"
          role="group"
          aria-label={`Review report on ${r.targetLabel}`}
        >
          <Field label="Note for the log" hint="Optional.">
            {(p) => <textarea {...p} value={note} onChange={(e) => setNote(e.target.value)} />}
          </Field>
          {r.targetType === 'LISTING' && (
            <label className="field field--inline">
              <input
                type="checkbox"
                checked={remove}
                onChange={(e) => setRemove(e.target.checked)}
              />
              <span>Also remove the listing from the circle</span>
            </label>
          )}
          <div className="row">
            <button
              type="button"
              className="btn"
              disabled={resolve.isPending}
              onClick={() => act('RESOLVED')}
            >
              Resolve report
            </button>
            <button
              type="button"
              className="btn btn--ghost"
              disabled={resolve.isPending}
              onClick={() => act('DISMISSED')}
            >
              Dismiss report
            </button>
          </div>
        </div>
      )}
    </li>
  );
}

function MembersSection({ circleId }: { circleId: number }) {
  const q = useMembers(circleId);
  const toast = useToast();
  const upd = useUpdateMember(circleId);
  const change = (
    userId: number,
    name: string,
    patch: { role?: MemberRole; status?: MemberStatus },
  ) =>
    upd.mutate(
      { userId, ...patch },
      {
        onSuccess: () => toast.success(`Updated ${name}.`),
        onError: (e) => toast.error(isApiError(e) ? e.message : 'Could not update member.'),
      },
    );
  return (
    <section className="section" aria-labelledby="mm-h">
      <h2 id="mm-h">Members</h2>
      {q.isLoading && <Loading rows={2} label="Loading members" />}
      {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data && (
        <div className="table-wrap">
          <table className="data">
            <caption className="sr-only">Circle members and their roles</caption>
            <thead>
              <tr>
                <th scope="col">Member</th>
                <th scope="col">Role</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {q.data.map((m) => (
                <tr key={m.userId}>
                  <th scope="row">{m.displayName}</th>
                  <td>
                    <select
                      aria-label={`Role for ${m.displayName}`}
                      value={m.role}
                      disabled={upd.isPending}
                      onChange={(e) =>
                        change(m.userId, m.displayName, { role: e.target.value as MemberRole })
                      }
                    >
                      <option value="MEMBER">Member</option>
                      <option value="MODERATOR">Moderator</option>
                    </select>
                  </td>
                  <td>
                    <select
                      aria-label={`Status for ${m.displayName}`}
                      value={m.status}
                      disabled={upd.isPending}
                      onChange={(e) =>
                        change(m.userId, m.displayName, { status: e.target.value as MemberStatus })
                      }
                    >
                      <option value="ACTIVE">Active</option>
                      <option value="SUSPENDED">Suspended</option>
                      <option value="REMOVED">Removed</option>
                    </select>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}

function GuidelinesSection(props: { circleId: number; guidelines: string; description: string }) {
  const toast = useToast();
  const upd = useUpdateCircle(props.circleId);
  const [guidelines, setGuidelines] = useState(props.guidelines);
  const [description, setDescription] = useState(props.description);
  useEffect(() => setGuidelines(props.guidelines), [props.guidelines]);
  useEffect(() => setDescription(props.description), [props.description]);
  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    upd.mutate(
      { guidelines, description },
      { onSuccess: () => toast.success('Circle details saved.') },
    );
  };
  return (
    <section className="section" aria-labelledby="gl-h">
      <h2 id="gl-h">Circle details</h2>
      <form onSubmit={submit} className="stack">
        <FormError error={upd.error} />
        <Field label="Description">
          {(p) => (
            <textarea {...p} value={description} onChange={(e) => setDescription(e.target.value)} />
          )}
        </Field>
        <Field label="Community guidelines">
          {(p) => (
            <textarea {...p} value={guidelines} onChange={(e) => setGuidelines(e.target.value)} />
          )}
        </Field>
        <button type="submit" className="btn" disabled={upd.isPending}>
          Save guidelines
        </button>
      </form>
    </section>
  );
}

function LogSection({ circleId }: { circleId: number }) {
  const q = useModerationLog(circleId, true);
  return (
    <section className="section" aria-labelledby="log-h">
      <h2 id="log-h">Moderation log</h2>
      {q.isLoading && <Loading rows={1} label="Loading log" />}
      {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
      {q.data?.length === 0 && (
        <Empty title="Nothing logged yet">Moderator actions are recorded here.</Empty>
      )}
      <ul className="ledger">
        {q.data?.map((a, i) => (
          <li key={i}>
            <span className="tag tag--plain">{a.actionType.replaceAll('_', ' ')}</span>{' '}
            {a.targetType.toLowerCase()} #{a.targetId}
            {a.note && <> · “{a.note}”</>}
            <span className="muted small">
              {' '}
              · {a.moderator.displayName} · {formatDateTime(a.createdAt)}
            </span>
          </li>
        ))}
      </ul>
    </section>
  );
}
