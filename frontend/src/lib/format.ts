import type {
  Category,
  HandoffStatus,
  ListingKind,
  ListingStatus,
  TransitionAction,
} from '../api/types';

const TZ = 'Asia/Kolkata';
const inr = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 0 });

export function formatInr(n: number | null | undefined): string {
  return `₹${inr.format(n ?? 0)}`;
}
export function formatNumber(n: number | null | undefined, digits = 0): string {
  return new Intl.NumberFormat('en-IN', { maximumFractionDigits: digits }).format(n ?? 0);
}

/** "Sat, 18 Oct, 6:30 pm" in Asia/Kolkata */
export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const parts = new Intl.DateTimeFormat('en-IN', {
    timeZone: TZ,
    weekday: 'short',
    day: 'numeric',
    month: 'short',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
  }).formatToParts(d);
  const get = (t: string) => parts.find((p) => p.type === t)?.value ?? '';
  return `${get('weekday')}, ${get('day')} ${get('month')}, ${get('hour')}:${get('minute')} ${get('dayPeriod').toLowerCase()}`;
}

/** "18 Oct 2026" from YYYY-MM-DD or ISO instant */
export function formatDate(s: string | null | undefined): string {
  if (!s) return '';
  const d = /^\d{4}-\d{2}-\d{2}$/.test(s) ? new Date(`${s}T00:00:00+05:30`) : new Date(s);
  if (Number.isNaN(d.getTime())) return '';
  return new Intl.DateTimeFormat('en-IN', {
    timeZone: TZ,
    day: 'numeric',
    month: 'short',
    year: 'numeric',
  }).format(d);
}

export function formatRange(from: string | null, to: string | null): string {
  if (!from && !to) return 'Flexible';
  if (from && to) return `${formatDate(from)} to ${formatDate(to)}`;
  return from ? `From ${formatDate(from)}` : `Until ${formatDate(to)}`;
}

/** Convert a datetime-local value (interpreted as IST) to ISO with +05:30 offset. */
export function istLocalToIso(local: string): string {
  return local.length === 16 ? `${local}:00+05:30` : `${local}+05:30`;
}

export function timeAgo(iso: string, now = Date.now()): string {
  const diff = Math.max(0, now - new Date(iso).getTime());
  const m = Math.floor(diff / 60000);
  if (m < 1) return 'just now';
  if (m < 60) return `${m} min ago`;
  const h = Math.floor(m / 60);
  if (h < 24) return `${h} h ago`;
  const d = Math.floor(h / 24);
  return d < 7 ? `${d} d ago` : formatDate(iso);
}

export const FALLBACK_CATEGORY_LABELS: Record<Category, string> = {
  TOOLS: 'Tools',
  KITCHEN: 'Kitchen',
  GARDEN: 'Garden',
  ELECTRONICS: 'Electronics',
  FURNITURE: 'Furniture',
  BOOKS: 'Books',
  KIDS: "Kids' things",
  SPORTS_CYCLES: 'Sports and cycles',
  REPAIR_SKILLS: 'Repair skills',
  TUTORING: 'Tutoring',
  EVENTS: 'Events',
  OTHER: 'Other',
};
export const CATEGORY_CODES = Object.keys(FALLBACK_CATEGORY_LABELS) as Category[];

export const KIND_LABEL: Record<ListingKind, string> = {
  ITEM: 'Item',
  SKILL: 'Skill',
  TIME: 'Time',
};
export const STATUS_LABEL: Record<HandoffStatus, string> = {
  PROPOSED: 'Proposed',
  SCHEDULED: 'Scheduled',
  HANDED_OFF: 'Handed off',
  RETURNED: 'Returned',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};
export const LISTING_STATUS_LABEL: Record<ListingStatus, string> = {
  ACTIVE: 'Available',
  RESERVED: 'Reserved',
  CLOSED: 'Closed',
  REMOVED: 'Removed',
};
export const ACTION_LABEL: Record<TransitionAction, string> = {
  CONFIRM: 'Confirm handoff',
  MARK_HANDED_OFF: 'Mark as handed off',
  MARK_RETURNED: 'Mark as returned',
  COMPLETE: 'Mark as completed',
  CANCEL: 'Cancel handoff',
};
export const FACTOR_LABEL: Record<string, string> = {
  category: 'Category',
  keywords: 'Keywords',
  availability: 'Availability',
  pickup: 'Pickup',
};
export function factorLabel(f: string): string {
  return FACTOR_LABEL[f.toLowerCase()] ?? f.charAt(0).toUpperCase() + f.slice(1).toLowerCase();
}
