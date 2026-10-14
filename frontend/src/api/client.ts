import type { ApiErrorBody, FieldError } from './types';

export class ApiError extends Error {
  status: number;
  code: string;
  fieldErrors: FieldError[];
  constructor(body: ApiErrorBody) {
    super(body.message);
    this.name = 'ApiError';
    this.status = body.status;
    this.code = body.code;
    this.fieldErrors = body.fieldErrors ?? [];
  }
  /** Map server fieldErrors to `{ field: message }` (first message wins). */
  fieldMap(): Record<string, string> {
    const out: Record<string, string> = {};
    for (const fe of this.fieldErrors) if (!(fe.field in out)) out[fe.field] = fe.message;
    return out;
  }
}

export function isApiError(e: unknown): e is ApiError {
  return e instanceof ApiError;
}

export const BASE = '/api/v1';

/** Public backend origin set at Vite build time; blank keeps local/Docker same-origin proxying. */
function apiBaseUrl(): string {
  const base = import.meta.env.VITE_API_BASE_URL as string | undefined;
  return (base ?? '').trim().replace(/\/+$/, '');
}

function apiCredentials(): RequestCredentials {
  return apiBaseUrl() ? 'include' : 'same-origin';
}

function rememberCsrfToken(response: Response): string | null {
  csrfToken = response.headers.get('X-XSRF-TOKEN') ?? csrfToken;
  return csrfToken ?? readCookie('XSRF-TOKEN');
}

let csrfToken: string | null = null;

export function readCookie(name: string): string | null {
  const m = document.cookie.split('; ').find((c) => c.startsWith(`${name}=`));
  return m ? decodeURIComponent(m.slice(name.length + 1)) : null;
}

type Params = Record<string, string | number | boolean | undefined | null>;

export function qs(params?: Params): string {
  if (!params) return '';
  const sp = new URLSearchParams();
  for (const [k, v] of Object.entries(params)) {
    if (v !== undefined && v !== null && v !== '') sp.set(k, String(v));
  }
  const s = sp.toString();
  return s ? `?${s}` : '';
}

let unauthorizedHandler: (() => void) | null = null;
export function onUnauthorized(fn: (() => void) | null) {
  unauthorizedHandler = fn;
}

async function toError(res: Response): Promise<ApiError> {
  let body: ApiErrorBody | null = null;
  try {
    body = (await res.json()) as ApiErrorBody;
  } catch {
    body = null;
  }
  return new ApiError(
    body && typeof body.code === 'string'
      ? body
      : {
          status: res.status,
          code: 'INTERNAL',
          message: 'Something went wrong. Please try again.',
        },
  );
}

async function ensureCsrf(): Promise<string | null> {
  let token = csrfToken ?? readCookie('XSRF-TOKEN');
  if (!token) {
    // Any GET sets the cookie, even a 401.
    const response = await fetch(`${apiBaseUrl()}${BASE}/auth/me`, {
      credentials: apiCredentials(),
    }).catch(() => undefined);
    if (response) token = rememberCsrfToken(response);
  }
  return token;
}

export async function request<T>(
  method: string,
  path: string,
  opts: { params?: Params; body?: unknown; silent401?: boolean } = {},
): Promise<T> {
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (opts.body !== undefined) headers['Content-Type'] = 'application/json';
  if (method !== 'GET') {
    const token = await ensureCsrf();
    if (token) headers['X-XSRF-TOKEN'] = token;
  }
  const res = await fetch(`${apiBaseUrl()}${BASE}${path}${qs(opts.params)}`, {
    method,
    headers,
    credentials: apiCredentials(),
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (method === 'POST' && path === '/auth/logout' && res.ok) csrfToken = null;
  else rememberCsrfToken(res);
  if (!res.ok) {
    const err = await toError(res);
    if (
      (res.status === 401 || err.code === 'ACCOUNT_DISABLED') &&
      !opts.silent401 &&
      unauthorizedHandler
    )
      unauthorizedHandler();
    throw err;
  }
  if (res.status === 204) return undefined as T;
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const api = {
  get: <T>(path: string, params?: Params, silent401 = false) =>
    request<T>('GET', path, { params, silent401 }),
  post: <T>(path: string, body?: unknown) => request<T>('POST', path, { body }),
  put: <T>(path: string, body?: unknown) => request<T>('PUT', path, { body }),
  patch: <T>(path: string, body?: unknown) => request<T>('PATCH', path, { body }),
  del: <T>(path: string) => request<T>('DELETE', path),
};
