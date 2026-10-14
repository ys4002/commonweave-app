import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';

interface ToastItem {
  id: number;
  message: string;
  kind: 'success' | 'error';
}
interface ToastApi {
  success: (m: string) => void;
  error: (m: string) => void;
}

const ToastCtx = createContext<ToastApi>({ success: () => undefined, error: () => undefined });
export const useToast = () => useContext(ToastCtx);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);
  const nextId = useRef(1);

  const dismiss = useCallback((id: number) => setItems((l) => l.filter((t) => t.id !== id)), []);
  const push = useCallback(
    (kind: ToastItem['kind'], message: string) => {
      const id = nextId.current++;
      setItems((l) => [...l.slice(-3), { id, message, kind }]);
      window.setTimeout(() => dismiss(id), 6000);
    },
    [dismiss],
  );
  const api = useMemo<ToastApi>(
    () => ({ success: (m) => push('success', m), error: (m) => push('error', m) }),
    [push],
  );

  return (
    <ToastCtx.Provider value={api}>
      {children}
      <div className="toasts" role="status" aria-live="polite" aria-atomic="false">
        {items.map((t) => (
          <div key={t.id} className={`toast toast--${t.kind}`}>
            <span>{t.message}</span>
            <button type="button" onClick={() => dismiss(t.id)} aria-label="Dismiss message">
              Dismiss
            </button>
          </div>
        ))}
      </div>
    </ToastCtx.Provider>
  );
}
