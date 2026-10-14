import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { App } from '../App';
import { SessionWatcher } from '../auth/auth';
import { ToastProvider } from '../components/Toast';
import type { User } from '../api/types';

export interface MockReply {
  status?: number;
  body?: unknown;
}
export type Handler =
  MockReply | ((req: { url: URL; body: unknown; headers: Headers }) => MockReply);
export interface Call {
  method: string;
  path: string;
  query: URLSearchParams;
  body: unknown;
  headers: Headers;
}

export const ananya: User = {
  id: 1,
  email: 'ananya@example.in',
  displayName: 'Ananya Rao',
  bio: null,
  locality: 'Koramangala 5th Block',
  onboarded: true,
  platformRole: 'USER',
  circles: [
    { circleId: 10, name: 'Koramangala 5th Block Neighbours', role: 'MEMBER' },
    { circleId: 11, name: 'Bandra West Commons', role: 'MODERATOR' },
  ],
};

export const apiError = (
  status: number,
  code: string,
  message: string,
  fieldErrors: { field: string; message: string }[] = [],
) => ({
  status,
  body: {
    status,
    code,
    message,
    fieldErrors,
    timestamp: '2026-10-14T10:00:00+05:30',
    path: '/api/v1/x',
  },
});

/** Stubs global fetch. Routes are keyed "METHOD /path" (no /api/v1 prefix). */
export function mockApi(
  routes: Record<string, Handler>,
  opts: { me?: User | null } = { me: ananya },
) {
  const calls: Call[] = [];
  const all: Record<string, Handler> = {
    'GET /auth/me': opts.me ? { body: opts.me } : apiError(401, 'UNAUTHENTICATED', 'Not signed in'),
    'GET /meta': {
      body: {
        currency: 'INR',
        locale: 'en-IN',
        timezone: 'Asia/Kolkata',
        categories: [
          { code: 'TOOLS', label: 'Tools' },
          { code: 'KITCHEN', label: 'Kitchen' },
        ],
        listingKinds: ['ITEM', 'SKILL', 'TIME'],
      },
    },
    'GET /notifications/unread-count': { body: { count: 0 } },
    ...routes,
  };
  const fn = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = new URL(String(input), 'http://localhost');
    const method = (init?.method ?? 'GET').toUpperCase();
    const path = url.pathname.replace(/^\/api\/v1/, '');
    const headers = new Headers(init?.headers);
    const body = init?.body ? JSON.parse(String(init.body)) : undefined;
    calls.push({ method, path, query: url.searchParams, body, headers });
    const h = all[`${method} ${path}`];
    const reply: MockReply = !h
      ? {
          status: 404,
          body: {
            status: 404,
            code: 'NOT_FOUND',
            message: `No mock for ${method} ${path}`,
            fieldErrors: [],
          },
        }
      : typeof h === 'function'
        ? h({ url, body, headers })
        : h;
    const status = reply.status ?? 200;
    return new Response(
      status === 204 || reply.body === undefined ? null : JSON.stringify(reply.body),
      {
        status,
        headers: { 'Content-Type': 'application/json' },
      },
    );
  });
  vi.stubGlobal('fetch', fn);
  return {
    calls,
    fn,
    called: (method: string, path: string) =>
      calls.filter((c) => c.method === method && c.path === path),
  };
}

export function renderApp(route = '/') {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: 0 } } });
  return render(
    <QueryClientProvider client={client}>
      <MemoryRouter initialEntries={[route]}>
        <ToastProvider>
          <SessionWatcher>
            <App />
          </SessionWatcher>
        </ToastProvider>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

export const page = <T,>(
  content: T[],
  extra: Partial<{ page: number; totalPages: number; totalElements: number }> = {},
) => ({
  content,
  page: extra.page ?? 0,
  size: 12,
  totalElements: extra.totalElements ?? content.length,
  totalPages: extra.totalPages ?? (content.length ? 1 : 0),
});
