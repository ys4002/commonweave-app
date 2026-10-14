// Types mirroring docs/api-contract.md. Keep in sync with the contract.

export type Category =
  | 'TOOLS'
  | 'KITCHEN'
  | 'GARDEN'
  | 'ELECTRONICS'
  | 'FURNITURE'
  | 'BOOKS'
  | 'KIDS'
  | 'SPORTS_CYCLES'
  | 'REPAIR_SKILLS'
  | 'TUTORING'
  | 'EVENTS'
  | 'OTHER';
export type ListingType = 'OFFER' | 'REQUEST';
export type ListingKind = 'ITEM' | 'SKILL' | 'TIME';
export type ListingStatus = 'ACTIVE' | 'RESERVED' | 'CLOSED' | 'REMOVED';
export type MemberRole = 'MEMBER' | 'MODERATOR';
export type MemberStatus = 'ACTIVE' | 'SUSPENDED' | 'REMOVED';
export type HandoffStatus =
  'PROPOSED' | 'SCHEDULED' | 'HANDED_OFF' | 'RETURNED' | 'COMPLETED' | 'CANCELLED';
export type TransitionAction =
  'CONFIRM' | 'MARK_HANDED_OFF' | 'MARK_RETURNED' | 'COMPLETE' | 'CANCEL';

export interface FieldError {
  field: string;
  message: string;
}
export interface ApiErrorBody {
  status: number;
  code: string;
  message: string;
  fieldErrors?: FieldError[];
  timestamp?: string;
  path?: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface Meta {
  currency: string;
  locale: string;
  timezone: string;
  categories: { code: Category; label: string }[];
  listingKinds: ListingKind[] | { code: ListingKind; label: string }[];
}

export interface UserCircle {
  circleId: number;
  name: string;
  role: MemberRole;
}
export type PlatformRole = 'USER' | 'ADMIN';
export interface User {
  id: number;
  email: string;
  displayName: string;
  bio: string | null;
  locality: string | null;
  onboarded: boolean;
  platformRole: PlatformRole;
  circles: UserCircle[];
}
export interface ProfilePatch {
  displayName?: string;
  bio?: string;
  locality?: string;
  onboarded?: boolean;
}

export interface CircleSummary {
  id: number;
  slug: string;
  name: string;
  city: string;
  area: string;
  description: string;
  memberCount: number;
  activeListingCount: number;
  myRole: MemberRole | null;
}
export interface CircleDetail extends CircleSummary {
  guidelines: string;
  moderators: { userId: number; displayName: string }[];
  myStatus: MemberStatus | null;
}
export interface CircleInput {
  name: string;
  city: string;
  area: string;
  description: string;
  guidelines: string;
}
export interface Member {
  userId: number;
  displayName: string;
  role: MemberRole;
  status: MemberStatus;
  joinedAt: string;
  completedExchanges: number;
}
export interface ActivityItem {
  type: string;
  text: string;
  at: string;
}
export interface DashboardActivity extends ActivityItem {
  circleName: string;
}

export interface Person {
  id: number;
  displayName: string;
}
export interface ListingSummary {
  id: number;
  circleId: number;
  circleName: string;
  type: ListingType;
  kind: ListingKind;
  title: string;
  category: Category;
  tags: string[];
  availableFrom: string | null;
  availableTo: string | null;
  pickupPreference: string | null;
  estimatedValueInr: number | null;
  depositInr: number | null;
  status: ListingStatus;
  owner: Person;
  createdAt: string;
}
export interface ListingDetail extends ListingSummary {
  description: string | null;
  hoursEstimate: number | null;
  returnExpected: boolean;
  completedExchangesOfOwner: number;
  myHandoff: { id: number; status: HandoffStatus } | null;
  canEdit: boolean;
}
export interface ListingInput {
  circleId: number;
  type: ListingType;
  kind: ListingKind;
  title: string;
  description: string;
  category: Category;
  tags: string[];
  availableFrom: string;
  availableTo: string;
  pickupPreference: string;
  estimatedValueInr: number;
  depositInr: number;
  hoursEstimate: number;
  returnExpected: boolean;
}
export interface MatchReason {
  factor: string;
  points: number;
  max: number;
  explanation: string;
}
export interface Match {
  listing: ListingSummary;
  score: number;
  reasons: MatchReason[];
}
export interface Suggestion {
  myListing: ListingSummary;
  match: Match;
}
export interface ListingQuery {
  circleId?: number;
  type?: ListingType;
  category?: Category;
  kind?: ListingKind;
  q?: string;
  availableOn?: string;
  status?: ListingStatus;
  mine?: boolean;
  sort?: 'newest' | 'soonest';
  page?: number;
  size?: number;
}

export interface HandoffSummary {
  id: number;
  status: HandoffStatus;
  listing: { id: number; title: string; category: Category; type: ListingType };
  role: 'PROVIDER' | 'RECIPIENT';
  counterpart: Person;
  scheduledAt: string | null;
  returnBy: string | null;
  returnExpected: boolean;
  updatedAt: string;
}
export interface HandoffEvent {
  fromStatus: HandoffStatus | null;
  toStatus: HandoffStatus;
  actor: Person;
  note: string | null;
  at: string;
}
export interface HandoffDetail extends HandoffSummary {
  message: string | null;
  pickupNote: string | null;
  pickupPreference: string | null;
  events: HandoffEvent[];
  allowedActions: TransitionAction[];
}
export interface TransitionInput {
  action: TransitionAction;
  scheduledAt?: string;
  pickupNote?: string;
  returnBy?: string;
  note?: string;
}
export interface HandoffQuery {
  status?: HandoffStatus;
  active?: boolean;
  role?: 'PROVIDER' | 'RECIPIENT';
  page?: number;
  size?: number;
}

export interface AppNotification {
  id: number;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  readAt: string | null;
  createdAt: string;
}

export type ReportStatus = 'OPEN' | 'RESOLVED' | 'DISMISSED';
export interface Report {
  id: number;
  targetType: 'LISTING' | 'MEMBER';
  targetId: number;
  targetLabel: string;
  reason: string;
  reporter: Person;
  status: ReportStatus;
  createdAt: string;
}
export interface ModerationLogEntry {
  actionType: string;
  targetType: string;
  targetId: number;
  note: string | null;
  moderator: Person;
  createdAt: string;
}

export interface Dashboard {
  upcomingHandoffs: HandoffSummary[];
  needsAction: HandoffSummary[];
  suggestions: Suggestion[];
  circleActivity: DashboardActivity[];
  unreadNotifications: number;
}
export interface Impact {
  periodDays: number;
  completedExchanges: number;
  activeMembers: number;
  rupeesSavedEstimate: number;
  co2KgAvoidedEstimate: number;
  hoursShared: number;
  byCategory: { category: Category; count: number; rupeesSaved: number }[];
  methodology: { label: string; formula: string; assumption: string }[];
  disclaimer: string;
}

// ---------- admin (ADDENDUM A) ----------
export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  locality: string | null;
  platformRole: PlatformRole;
  enabled: boolean;
  onboarded: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  circles: { circleId: number; name: string; role: MemberRole; status: MemberStatus }[];
  listingCount: number;
  completedExchanges: number;
}
export interface AdminUserDetail extends AdminUser {
  bio: string | null;
  recentListings: ListingSummary[];
  recentHandoffs: HandoffSummary[];
}
export interface AdminOverview {
  users: { total: number; disabled: number; admins: number };
  circles: number;
  listings: Record<ListingStatus, number>;
  handoffs: Record<HandoffStatus, number>;
  openReports: number;
}
export interface AdminAuditEntry {
  action: string;
  targetType: string;
  targetId: number | null;
  note: string | null;
  admin: Person;
  createdAt: string;
}
export interface AdminUserPatch {
  enabled?: boolean;
  platformRole?: PlatformRole;
  displayName?: string;
}
