import { useEffect, type ReactNode } from 'react';
import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { fetchMe, keys } from '../api/hooks';
import { isApiError, onUnauthorized } from '../api/client';
import type { User } from '../api/types';
import { Loading } from '../components/ui';

export function landingPath(u: User): string {
  if (u.platformRole === 'ADMIN') return '/admin';
  return u.onboarded ? '/' : '/onboarding';
}

/** Current user; `null` when anonymous. Also bootstraps the XSRF cookie. */
export function useAuth(): { user: User | null | undefined; isLoading: boolean } {
  const q = useQuery({
    queryKey: keys.me,
    queryFn: async () => {
      try {
        return await fetchMe();
      } catch (e) {
        if (isApiError(e) && e.status === 401) return null;
        throw e;
      }
    },
    staleTime: 5 * 60_000,
    retry: false,
  });
  return { user: q.data, isLoading: q.isLoading };
}

/** If any API call answers 401, drop the cached user so guards redirect to /login. */
export function SessionWatcher({ children }: { children: ReactNode }) {
  const qc = useQueryClient();
  useEffect(() => {
    onUnauthorized(() => qc.setQueryData(keys.me, null));
    return () => onUnauthorized(null);
  }, [qc]);
  return <>{children}</>;
}

export function RequireAuth() {
  const { user, isLoading } = useAuth();
  const loc = useLocation();
  if (isLoading) return <Loading rows={2} label="Checking your session" />;
  if (!user) return <Navigate to="/login" replace state={{ from: loc.pathname + loc.search }} />;
  if (!user.onboarded && user.platformRole !== 'ADMIN' && loc.pathname !== '/onboarding')
    return <Navigate to="/onboarding" replace />;
  return <Outlet />;
}

export function PublicOnly() {
  const { user, isLoading } = useAuth();
  if (isLoading) return <Loading rows={2} label="Checking your session" />;
  if (user) return <Navigate to={landingPath(user)} replace />;
  return <Outlet />;
}
