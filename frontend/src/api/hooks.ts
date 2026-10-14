import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import { api } from './client';
import type {
  ActivityItem,
  AdminAuditEntry,
  AdminOverview,
  AdminUser,
  AdminUserDetail,
  AdminUserPatch,
  AppNotification,
  CircleDetail,
  CircleInput,
  CircleSummary,
  Dashboard,
  HandoffDetail,
  HandoffQuery,
  HandoffSummary,
  Impact,
  ListingDetail,
  ListingInput,
  ListingQuery,
  ListingSummary,
  Match,
  Member,
  MemberRole,
  MemberStatus,
  Meta,
  ModerationLogEntry,
  Page,
  ProfilePatch,
  Report,
  Suggestion,
  TransitionInput,
  User,
} from './types';

export const keys = {
  me: ['me'] as const,
  meta: ['meta'] as const,
};

// ---------- auth / profile ----------
export const fetchMe = () => api.get<User>('/auth/me', undefined, true);

export function useLogin() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (b: { email: string; password: string }) => api.post<User>('/auth/login', b),
    onSuccess: (u) => {
      qc.clear();
      qc.setQueryData(keys.me, u);
    },
  });
}
export function useSignup() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (b: { email: string; password: string; displayName: string }) =>
      api.post<User>('/auth/signup', b),
    onSuccess: (u) => {
      qc.clear();
      qc.setQueryData(keys.me, u);
    },
  });
}
export function useLogout() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post<void>('/auth/logout'),
    onSettled: () => {
      qc.clear();
      qc.setQueryData(keys.me, null);
    },
  });
}
export function useUpdateProfile() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (b: ProfilePatch) => api.patch<User>('/users/me', b),
    onSuccess: (u) => qc.setQueryData(keys.me, u),
  });
}
export const useMeta = () =>
  useQuery({ queryKey: keys.meta, queryFn: () => api.get<Meta>('/meta'), staleTime: Infinity });

// ---------- circles ----------
export const useCircles = (p: { q?: string; city?: string; page?: number; size?: number }) =>
  useQuery({
    queryKey: ['circles', p],
    queryFn: () => api.get<Page<CircleSummary>>('/circles', p),
    placeholderData: keepPreviousData,
  });
export const useCircle = (id: number) =>
  useQuery({ queryKey: ['circle', id], queryFn: () => api.get<CircleDetail>(`/circles/${id}`) });
export const useMembers = (id: number, enabled = true) =>
  useQuery({
    queryKey: ['circle', id, 'members'],
    queryFn: () => api.get<Member[]>(`/circles/${id}/members`),
    enabled,
  });
export const useCircleActivity = (id: number, enabled = true) =>
  useQuery({
    queryKey: ['circle', id, 'activity'],
    queryFn: () => api.get<ActivityItem[]>(`/circles/${id}/activity`, { size: 10 }),
    enabled,
  });

function useInvalidating<V, R>(fn: (v: V) => Promise<R>, extra?: (r: R) => void) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: fn,
    onSuccess: async (r) => {
      extra?.(r);
      await qc.invalidateQueries();
    },
  });
}
export const useJoinCircle = () =>
  useInvalidating((id: number) => api.post<CircleDetail>(`/circles/${id}/join`));
export const useLeaveCircle = () =>
  useInvalidating((id: number) => api.post<void>(`/circles/${id}/leave`));
export const useCreateCircle = () =>
  useInvalidating((b: CircleInput) => api.post<CircleDetail>('/circles', b));
export const useUpdateCircle = (id: number) =>
  useInvalidating((b: { description?: string; guidelines?: string }) =>
    api.patch<CircleDetail>(`/circles/${id}`, b),
  );
export const useUpdateMember = (circleId: number) =>
  useInvalidating((v: { userId: number; role?: MemberRole; status?: MemberStatus }) =>
    api.patch<Member>(`/circles/${circleId}/members/${v.userId}`, {
      role: v.role,
      status: v.status,
    }),
  );

// ---------- listings ----------
export const useListings = (q: ListingQuery) =>
  useQuery({
    queryKey: ['listings', q],
    queryFn: () => api.get<Page<ListingSummary>>('/listings', { ...q }),
    placeholderData: keepPreviousData,
  });
export const useListing = (id: number) =>
  useQuery({ queryKey: ['listing', id], queryFn: () => api.get<ListingDetail>(`/listings/${id}`) });
export const useMatches = (id: number) =>
  useQuery({
    queryKey: ['listing', id, 'matches'],
    queryFn: () => api.get<Match[]>(`/listings/${id}/matches`),
  });
export const useSuggested = () =>
  useQuery({ queryKey: ['suggested'], queryFn: () => api.get<Suggestion[]>('/matches/suggested') });
export const useCreateListing = () =>
  useInvalidating((b: ListingInput) => api.post<ListingDetail>('/listings', b));
export const useUpdateListing = (id: number) =>
  useInvalidating((b: ListingInput) => api.put<ListingDetail>(`/listings/${id}`, b));
export const useCloseListing = () =>
  useInvalidating((id: number) => api.del<void>(`/listings/${id}`));

// ---------- handoffs ----------
export const useHandoffs = (q: HandoffQuery) =>
  useQuery({
    queryKey: ['handoffs', q],
    queryFn: () => api.get<Page<HandoffSummary>>('/handoffs', { ...q }),
    placeholderData: keepPreviousData,
  });
export const useHandoff = (id: number) =>
  useQuery({ queryKey: ['handoff', id], queryFn: () => api.get<HandoffDetail>(`/handoffs/${id}`) });
export const useProposeHandoff = () =>
  useInvalidating((b: { listingId: number; message?: string }) =>
    api.post<HandoffDetail>('/handoffs', b),
  );
export const useTransition = (id: number) =>
  useInvalidating((b: TransitionInput) =>
    api.post<HandoffDetail>(`/handoffs/${id}/transitions`, b),
  );

// ---------- notifications ----------
export const useNotifications = (p: { unread?: boolean; page?: number; size?: number }) =>
  useQuery({
    queryKey: ['notifications', p],
    queryFn: () => api.get<Page<AppNotification>>('/notifications', p),
    placeholderData: keepPreviousData,
  });
export const useUnreadCount = () =>
  useQuery({
    queryKey: ['unread-count'],
    queryFn: () => api.get<{ count: number }>('/notifications/unread-count'),
    refetchInterval: 60_000,
  });
export const useMarkRead = () =>
  useInvalidating((id: number) => api.post<void>(`/notifications/${id}/read`));
export const useMarkAllRead = () =>
  useInvalidating(() => api.post<void>('/notifications/read-all'));

// ---------- moderation ----------
export const useReports = (circleId: number, enabled: boolean) =>
  useQuery({
    queryKey: ['circle', circleId, 'reports'],
    queryFn: () => api.get<Report[]>(`/circles/${circleId}/reports`, { status: 'OPEN' }),
    enabled,
  });
export const useModerationLog = (circleId: number, enabled: boolean) =>
  useQuery({
    queryKey: ['circle', circleId, 'log'],
    queryFn: () => api.get<ModerationLogEntry[]>(`/circles/${circleId}/moderation-log`),
    enabled,
  });
export const useResolveReport = (circleId: number) =>
  useInvalidating(
    (v: {
      reportId: number;
      resolution: 'RESOLVED' | 'DISMISSED';
      note: string;
      removeListing?: boolean;
    }) =>
      api.post<void>(`/circles/${circleId}/reports/${v.reportId}/resolve`, {
        resolution: v.resolution,
        note: v.note,
        removeListing: v.removeListing,
      }),
  );
export const useCreateReport = () =>
  useInvalidating(
    (b: { circleId: number; targetType: 'LISTING' | 'MEMBER'; targetId: number; reason: string }) =>
      api.post<void>('/reports', b),
  );

// ---------- dashboard & impact ----------
export const useDashboard = () =>
  useQuery({ queryKey: ['dashboard'], queryFn: () => api.get<Dashboard>('/dashboard') });
export const useImpact = (scope: { circleId?: number }, days: number) =>
  useQuery({
    queryKey: ['impact', scope, days],
    queryFn: () =>
      scope.circleId
        ? api.get<Impact>(`/circles/${scope.circleId}/impact`, { days })
        : api.get<Impact>('/impact/me', { days }),
  });

// ---------- admin ----------
type AdminPage = { q?: string; status?: string; enabled?: boolean; page?: number; size?: number };
export const useAdminOverview = () =>
  useQuery({
    queryKey: ['admin', 'overview'],
    queryFn: () => api.get<AdminOverview>('/admin/overview'),
  });
export const useAdminUsers = (p: AdminPage) =>
  useQuery({
    queryKey: ['admin', 'users', p],
    queryFn: () => api.get<Page<AdminUser>>('/admin/users', { ...p }),
    placeholderData: keepPreviousData,
  });
export const useAdminUser = (id: number) =>
  useQuery({
    queryKey: ['admin', 'user', id],
    queryFn: () => api.get<AdminUserDetail>(`/admin/users/${id}`),
  });
export const useAdminUpdateUser = () =>
  useInvalidating((v: { id: number; patch: AdminUserPatch }) =>
    api.patch<AdminUser>(`/admin/users/${v.id}`, v.patch),
  );
export const useAdminResetPassword = () =>
  useInvalidating((v: { id: number; newPassword: string }) =>
    api.post<void>(`/admin/users/${v.id}/reset-password`, { newPassword: v.newPassword }),
  );
export const useAdminCircles = (p: AdminPage) =>
  useQuery({
    queryKey: ['admin', 'circles', p],
    queryFn: () => api.get<Page<CircleSummary>>('/admin/circles', { ...p }),
    placeholderData: keepPreviousData,
  });
export const useAdminListings = (p: AdminPage) =>
  useQuery({
    queryKey: ['admin', 'listings', p],
    queryFn: () => api.get<Page<ListingSummary>>('/admin/listings', { ...p }),
    placeholderData: keepPreviousData,
  });
export const useAdminRemoveListing = () =>
  useInvalidating((v: { id: number; note: string }) =>
    api.post<void>(`/admin/listings/${v.id}/remove`, { note: v.note }),
  );
export const useAdminReports = (p: AdminPage) =>
  useQuery({
    queryKey: ['admin', 'reports', p],
    queryFn: () => api.get<Page<Report>>('/admin/reports', { ...p }),
    placeholderData: keepPreviousData,
  });
export const useAdminAudit = (p: AdminPage) =>
  useQuery({
    queryKey: ['admin', 'audit', p],
    queryFn: () => api.get<Page<AdminAuditEntry>>('/admin/audit-log', { ...p }),
    placeholderData: keepPreviousData,
  });
