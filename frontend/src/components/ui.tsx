import { useEffect, useId, type ReactNode } from 'react';
import { isApiError } from '../api/client';

export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = `${title} · Commonweave`;
  }, [title]);
}

export function PageHead(props: { eyebrow?: string; title: string; children?: ReactNode }) {
  return (
    <div className="page-head">
      <div>
        {props.eyebrow && <p className="eyebrow">{props.eyebrow}</p>}
        <h1>{props.title}</h1>
      </div>
      {props.children && <div className="row">{props.children}</div>}
    </div>
  );
}

export function Loading({ rows = 3, label = 'Loading' }: { rows?: number; label?: string }) {
  return (
    <div aria-busy="true" role="status">
      <span className="sr-only">{label}…</span>
      {Array.from({ length: rows }, (_, i) => (
        <span key={i} className="skeleton skeleton--block" aria-hidden="true" />
      ))}
    </div>
  );
}

export function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message;
  return 'Could not reach the server. Check your connection and try again.';
}

export function ErrorBox({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  return (
    <div className="alert" role="alert">
      <strong>That did not work.</strong> {errorMessage(error)}{' '}
      {onRetry && (
        <button type="button" className="link-btn" onClick={onRetry}>
          Try again
        </button>
      )}
    </div>
  );
}

export function Empty(props: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <h3>{props.title}</h3>
      {props.children && <p>{props.children}</p>}
      {props.action}
    </div>
  );
}

export interface FieldControlProps {
  id: string;
  'aria-invalid': boolean | undefined;
  'aria-describedby': string | undefined;
}

export function Field(props: {
  label: string;
  error?: string;
  hint?: string;
  className?: string;
  children: (p: FieldControlProps) => ReactNode;
}) {
  const id = useId();
  const errId = `${id}-err`;
  const hintId = `${id}-hint`;
  const described = [props.hint ? hintId : '', props.error ? errId : ''].filter(Boolean).join(' ');
  return (
    <div className={`field ${props.className ?? ''}`}>
      <label htmlFor={id}>{props.label}</label>
      {props.children({
        id,
        'aria-invalid': props.error ? true : undefined,
        'aria-describedby': described || undefined,
      })}
      {props.hint && (
        <span className="hint" id={hintId}>
          {props.hint}
        </span>
      )}
      {props.error && (
        <span className="error-text" id={errId}>
          {props.error}
        </span>
      )}
    </div>
  );
}

export function Pagination(props: {
  page: number;
  totalPages: number;
  onPage: (p: number) => void;
}) {
  if (props.totalPages <= 1) return null;
  return (
    <nav className="pagination" aria-label="Pagination">
      <button
        type="button"
        className="btn btn--ghost btn--small"
        disabled={props.page <= 0}
        onClick={() => props.onPage(props.page - 1)}
      >
        Previous
      </button>
      <span aria-live="polite">
        Page {props.page + 1} of {props.totalPages}
      </span>
      <button
        type="button"
        className="btn btn--ghost btn--small"
        disabled={props.page + 1 >= props.totalPages}
        onClick={() => props.onPage(props.page + 1)}
      >
        Next
      </button>
    </nav>
  );
}

export function FormError({ error }: { error: unknown }) {
  if (!error) return null;
  return (
    <div className="alert" role="alert">
      {errorMessage(error)}
    </div>
  );
}
