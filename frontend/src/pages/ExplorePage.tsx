import { useEffect, useState, type FormEvent } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useListings, useMeta } from '../api/hooks';
import type { Category, ListingKind, ListingQuery, ListingType } from '../api/types';
import { useAuth } from '../auth/auth';
import { ListingCard } from '../components/domain';
import {
  Empty,
  ErrorBox,
  Field,
  Loading,
  PageHead,
  Pagination,
  useDocumentTitle,
} from '../components/ui';
import { CATEGORY_CODES, FALLBACK_CATEGORY_LABELS } from '../lib/format';

export function ExplorePage() {
  useDocumentTitle('Explore');
  const { user } = useAuth();
  const meta = useMeta();
  const [sp, setSp] = useSearchParams();
  const get = (k: string) => sp.get(k) ?? '';
  const [text, setText] = useState(get('q'));
  useEffect(() => setText(sp.get('q') ?? ''), [sp]);

  const page = Number(get('page')) || 0;
  const query: ListingQuery = {
    circleId: get('circleId') ? Number(get('circleId')) : undefined,
    type: (get('type') as ListingType) || undefined,
    category: (get('category') as Category) || undefined,
    kind: (get('kind') as ListingKind) || undefined,
    q: get('q') || undefined,
    mine: get('mine') === 'true' || undefined,
    availableOn: get('availableOn') || undefined,
    sort: (get('sort') as 'newest' | 'soonest') || 'newest',
    page,
    size: 12,
  };
  const q = useListings(query);

  const set = (patch: Record<string, string>, resetPage = true) => {
    const next = new URLSearchParams(sp);
    for (const [k, v] of Object.entries(patch)) {
      if (v) next.set(k, v);
      else next.delete(k);
    }
    if (resetPage) next.delete('page');
    setSp(next);
  };
  const onSearch = (ev: FormEvent) => {
    ev.preventDefault();
    set({ q: text.trim() });
  };
  const filtered = ['q', 'circleId', 'type', 'category', 'kind', 'availableOn', 'mine'].some((k) =>
    sp.has(k),
  );
  const categories =
    meta.data?.categories ??
    CATEGORY_CODES.map((c) => ({ code: c, label: FALLBACK_CATEGORY_LABELS[c] }));

  const select = (name: string, label: string, options: [string, string][]) => (
    <Field label={label}>
      {(p) => (
        <select {...p} value={get(name)} onChange={(e) => set({ [name]: e.target.value })}>
          <option value="">Any</option>
          {options.map(([v, l]) => (
            <option key={v} value={v}>
              {l}
            </option>
          ))}
        </select>
      )}
    </Field>
  );

  return (
    <div className="page">
      <PageHead eyebrow="Explore" title="What your neighbours share">
        <Link to="/listings/new" className="btn btn--signal">
          New listing
        </Link>
      </PageHead>

      <form className="filters" role="search" aria-label="Filter listings" onSubmit={onSearch}>
        <div className="form-grid">
          <Field label="Search" className="span-2">
            {(p) => (
              <input
                {...p}
                className="input"
                type="search"
                placeholder="Drill, pressure cooker, Hindi tutoring…"
                value={text}
                onChange={(e) => setText(e.target.value)}
              />
            )}
          </Field>
          {select(
            'circleId',
            'Circle',
            (user?.circles ?? []).map((c) => [String(c.circleId), c.name]),
          )}
          {select('type', 'Offer or request', [
            ['OFFER', 'Offers'],
            ['REQUEST', 'Requests'],
          ])}
          {select(
            'category',
            'Category',
            categories.map((c) => [c.code, c.label]),
          )}
          {select('kind', 'Kind', [
            ['ITEM', 'Item'],
            ['SKILL', 'Skill'],
            ['TIME', 'Time'],
          ])}
          <Field label="Available on">
            {(p) => (
              <input
                {...p}
                className="input"
                type="date"
                value={get('availableOn')}
                onChange={(e) => set({ availableOn: e.target.value })}
              />
            )}
          </Field>
          <label className="field field--inline">
            <input
              type="checkbox"
              checked={get('mine') === 'true'}
              onChange={(e) => set({ mine: e.target.checked ? 'true' : '' })}
            />
            <span>Only my listings</span>
          </label>
          <Field label="Sort by">
            {(p) => (
              <select
                {...p}
                value={get('sort') || 'newest'}
                onChange={(e) => set({ sort: e.target.value })}
              >
                <option value="newest">Newest first</option>
                <option value="soonest">Available soonest</option>
              </select>
            )}
          </Field>
        </div>
        <div className="row">
          <button type="submit" className="btn">
            Search
          </button>
          {filtered && (
            <button
              type="button"
              className="btn btn--ghost"
              onClick={() => setSp(new URLSearchParams())}
            >
              Clear filters
            </button>
          )}
        </div>
      </form>

      <section aria-labelledby="results-h" aria-live="polite">
        <h2 id="results-h" className="sr-only">
          Results
        </h2>
        {q.isLoading && <Loading rows={4} label="Loading listings" />}
        {q.isError && <ErrorBox error={q.error} onRetry={() => q.refetch()} />}
        {q.data && (
          <>
            <p className="muted small">
              {q.data.totalElements} listing{q.data.totalElements === 1 ? '' : 's'} found
            </p>
            {q.data.content.length === 0 ? (
              <Empty
                title="Nothing matches yet"
                action={
                  <Link to="/listings/new" className="btn btn--ghost">
                    Post the first one
                  </Link>
                }
              >
                Try widening your filters, or ask your circle for what you need.
              </Empty>
            ) : (
              <div className="grid">
                {q.data.content.map((l) => (
                  <ListingCard key={l.id} listing={l} />
                ))}
              </div>
            )}
            <Pagination
              page={q.data.page}
              totalPages={q.data.totalPages}
              onPage={(p) => set({ page: p ? String(p) : '' }, false)}
            />
          </>
        )}
      </section>
    </div>
  );
}
