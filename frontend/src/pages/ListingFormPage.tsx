import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { useCreateListing, useListing, useMeta, useUpdateListing } from '../api/hooks';
import { isApiError } from '../api/client';
import type { Category, ListingDetail, ListingInput, ListingKind, ListingType } from '../api/types';
import { useAuth } from '../auth/auth';
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
import { CATEGORY_CODES, FALLBACK_CATEGORY_LABELS } from '../lib/format';

interface FormState {
  circleId: string;
  type: ListingType;
  kind: ListingKind;
  title: string;
  description: string;
  category: Category;
  tags: string;
  availableFrom: string;
  availableTo: string;
  pickupPreference: string;
  estimatedValueInr: string;
  depositInr: string;
  hoursEstimate: string;
  returnExpected: boolean;
}
type Errors = Record<string, string>;

const todayIst = (offsetDays = 0) =>
  new Date(Date.now() + offsetDays * 86400000).toLocaleDateString('en-CA', {
    timeZone: 'Asia/Kolkata',
  });

export function parseTags(s: string): string[] {
  return s
    .split(',')
    .map((t) => t.trim())
    .filter(Boolean);
}

export function validateListing(f: FormState): Errors {
  const e: Errors = {};
  const title = f.title.trim();
  if (!f.circleId) e.circleId = 'Choose a circle.';
  if (title.length < 3 || title.length > 120) e.title = 'Title must be 3 to 120 characters.';
  if (f.description.length > 2000) e.description = 'Description can be at most 2000 characters.';
  if (parseTags(f.tags).length > 8) e.tags = 'Use at most 8 tags.';
  if (!f.availableFrom) e.availableFrom = 'Pick the first day you are available.';
  if (!f.availableTo) e.availableTo = 'Pick the last day you are available.';
  else if (f.availableFrom && f.availableTo < f.availableFrom)
    e.availableTo = 'The end date cannot be before the start date.';
  if (f.pickupPreference.length > 200) e.pickupPreference = 'Keep this under 200 characters.';
  const range = (v: string, max: number) => v !== '' && !(Number(v) >= 0 && Number(v) <= max);
  if (f.kind === 'ITEM' && range(f.estimatedValueInr, 500000))
    e.estimatedValueInr = 'Enter a value between ₹0 and ₹5,00,000.';
  if (f.kind === 'ITEM' && range(f.depositInr, 100000))
    e.depositInr = 'Enter a deposit between ₹0 and ₹1,00,000.';
  if (f.kind !== 'ITEM' && range(f.hoursEstimate, 100))
    e.hoursEstimate = 'Enter hours between 0 and 100.';
  return e;
}

function toInput(f: FormState): ListingInput {
  const item = f.kind === 'ITEM';
  return {
    circleId: Number(f.circleId),
    type: f.type,
    kind: f.kind,
    title: f.title.trim(),
    description: f.description.trim(),
    category: f.category,
    tags: parseTags(f.tags),
    availableFrom: f.availableFrom,
    availableTo: f.availableTo,
    pickupPreference: f.pickupPreference.trim(),
    estimatedValueInr: item ? Number(f.estimatedValueInr || 0) : 0,
    depositInr: item ? Number(f.depositInr || 0) : 0,
    hoursEstimate: item ? 0 : Number(f.hoursEstimate || 0),
    returnExpected: item ? f.returnExpected : false,
  };
}

function fromListing(l: ListingDetail): FormState {
  return {
    circleId: String(l.circleId),
    type: l.type,
    kind: l.kind,
    title: l.title,
    description: l.description ?? '',
    category: l.category,
    tags: l.tags.join(', '),
    availableFrom: l.availableFrom ?? '',
    availableTo: l.availableTo ?? '',
    pickupPreference: l.pickupPreference ?? '',
    estimatedValueInr: String(l.estimatedValueInr ?? 0),
    depositInr: String(l.depositInr ?? 0),
    hoursEstimate: String(l.hoursEstimate ?? 0),
    returnExpected: l.returnExpected,
  };
}

export function ListingFormPage() {
  const params = useParams();
  const editId = params.id ? Number(params.id) : null;
  return editId ? <EditListing id={editId} /> : <ListingForm />;
}

function EditListing({ id }: { id: number }) {
  const q = useListing(id);
  if (q.isLoading)
    return (
      <div className="page">
        <Loading label="Loading listing" />
      </div>
    );
  if (q.isError)
    return (
      <div className="page">
        <ErrorBox error={q.error} onRetry={() => q.refetch()} />
      </div>
    );
  if (!q.data?.canEdit)
    return (
      <div className="page">
        <Empty title="You cannot edit this listing">
          Only the person who posted it can change it.
        </Empty>
      </div>
    );
  return <ListingForm existing={q.data} />;
}

function ListingForm({ existing }: { existing?: ListingDetail }) {
  const edit = Boolean(existing);
  useDocumentTitle(edit ? 'Edit listing' : 'New listing');
  const { user } = useAuth();
  const meta = useMeta();
  const nav = useNavigate();
  const toast = useToast();
  const create = useCreateListing();
  const update = useUpdateListing(existing?.id ?? 0);
  const mutation = edit ? update : create;
  const [errors, setErrors] = useState<Errors>({});
  const [f, setF] = useState<FormState>(
    existing
      ? fromListing(existing)
      : {
          circleId: '',
          type: 'OFFER',
          kind: 'ITEM',
          title: '',
          description: '',
          category: 'TOOLS',
          tags: '',
          availableFrom: todayIst(),
          availableTo: todayIst(30),
          pickupPreference: '',
          estimatedValueInr: '',
          depositInr: '',
          hoursEstimate: '',
          returnExpected: true,
        },
  );
  useEffect(() => {
    if (!edit && !f.circleId && user?.circles[0])
      setF((s) => ({ ...s, circleId: String(user.circles[0].circleId) }));
  }, [edit, f.circleId, user]);

  const set = <K extends keyof FormState>(k: K, v: FormState[K]) => setF((s) => ({ ...s, [k]: v }));
  const fieldError = (name: string) =>
    errors[name] ?? Object.entries(errors).find(([k]) => k.startsWith(`${name}[`))?.[1];
  const categories =
    meta.data?.categories ??
    CATEGORY_CODES.map((c) => ({ code: c, label: FALLBACK_CATEGORY_LABELS[c] }));
  const item = f.kind === 'ITEM';

  const submit = (ev: FormEvent) => {
    ev.preventDefault();
    const errs = validateListing(f);
    setErrors(errs);
    if (Object.keys(errs).length) return;
    const input = toInput(f);
    const done = (l: ListingDetail) => {
      toast.success(edit ? 'Listing updated.' : 'Listing posted to your circle.');
      nav(`/listings/${l.id}`);
    };
    const opts = {
      onSuccess: done,
      onError: (e: unknown) => isApiError(e) && setErrors(e.fieldMap()),
    };
    if (edit) update.mutate(input, opts);
    else create.mutate(input, opts);
  };

  const text = (
    name: keyof FormState,
    label: string,
    extra: { hint?: string; placeholder?: string; type?: string } = {},
  ) => (
    <Field label={label} error={fieldError(name)} hint={extra.hint}>
      {(p) => (
        <input
          {...p}
          className="input"
          type={extra.type ?? 'text'}
          placeholder={extra.placeholder}
          value={String(f[name])}
          onChange={(e) => set(name, e.target.value as never)}
        />
      )}
    </Field>
  );

  return (
    <div className="page page--narrow">
      <PageHead
        eyebrow={edit ? 'Edit' : 'New listing'}
        title={edit ? 'Edit your listing' : 'Share something'}
      />
      <form onSubmit={submit} noValidate>
        {Object.keys(errors).length > 0 && (
          <div className="alert" role="alert">
            Please fix the highlighted fields.
          </div>
        )}
        {mutation.isError && !Object.keys(errors).length && <FormError error={mutation.error} />}
        <div className="form-grid">
          <Field label="Circle" error={fieldError('circleId')}>
            {(p) => (
              <select
                {...p}
                value={f.circleId}
                disabled={edit}
                onChange={(e) => set('circleId', e.target.value)}
              >
                <option value="">Choose a circle</option>
                {user?.circles.map((c) => (
                  <option key={c.circleId} value={c.circleId}>
                    {c.name}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <Field label="I want to" error={fieldError('type')}>
            {(p) => (
              <select
                {...p}
                value={f.type}
                onChange={(e) => set('type', e.target.value as ListingType)}
              >
                <option value="OFFER">Offer something</option>
                <option value="REQUEST">Request something</option>
              </select>
            )}
          </Field>
          <Field label="Kind" error={fieldError('kind')}>
            {(p) => (
              <select
                {...p}
                value={f.kind}
                onChange={(e) => set('kind', e.target.value as ListingKind)}
              >
                <option value="ITEM">Item (a thing)</option>
                <option value="SKILL">Skill (know-how)</option>
                <option value="TIME">Time (a helping hand)</option>
              </select>
            )}
          </Field>
          <Field label="Category" error={fieldError('category')}>
            {(p) => (
              <select
                {...p}
                value={f.category}
                onChange={(e) => set('category', e.target.value as Category)}
              >
                {categories.map((c) => (
                  <option key={c.code} value={c.code}>
                    {c.label}
                  </option>
                ))}
              </select>
            )}
          </Field>
          <div className="span-2">
            {text('title', 'Title', {
              placeholder: 'Bosch drill with bits, or Hindi tutoring for Class 6',
            })}
          </div>
          <Field
            label="Description"
            error={fieldError('description')}
            hint="Condition, what is included, house rules."
            className="span-2"
          >
            {(p) => (
              <textarea
                {...p}
                placeholder="Works well, comes with 12 bits. Please return it clean before Diwali week."
                value={f.description}
                onChange={(e) => set('description', e.target.value)}
              />
            )}
          </Field>
          <div className="span-2">
            {text('tags', 'Tags', {
              hint: 'Comma separated, up to 8. For example: drill, diy, weekend',
              placeholder: 'drill, diy, weekend',
            })}
          </div>
          {text('availableFrom', 'Available from', { type: 'date' })}
          {text('availableTo', 'Available until', { type: 'date' })}
          <div className="span-2">
            {text('pickupPreference', 'Pickup preference', {
              placeholder: 'Society gate or lift lobby, after 6 pm',
            })}
          </div>
          {item ? (
            <>
              {text('estimatedValueInr', 'Estimated value (₹)', {
                type: 'number',
                placeholder: '1499',
              })}
              {text('depositInr', 'Deposit (₹)', { type: 'number', placeholder: '0' })}
              <label className="field field--inline span-2">
                <input
                  type="checkbox"
                  checked={f.returnExpected}
                  onChange={(e) => set('returnExpected', e.target.checked)}
                />
                <span>This item should be returned after use</span>
              </label>
            </>
          ) : (
            text('hoursEstimate', 'Hours involved', {
              type: 'number',
              hint: 'A rough estimate, 0 to 100.',
              placeholder: '2',
            })
          )}
        </div>
        <div className="row">
          <button type="submit" className="btn btn--signal" disabled={mutation.isPending}>
            {mutation.isPending ? 'Saving…' : edit ? 'Save changes' : 'Post listing'}
          </button>
          <button type="button" className="btn btn--ghost" onClick={() => nav(-1)}>
            Cancel
          </button>
        </div>
      </form>
    </div>
  );
}
