import { useEffect, useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { useCircles, useJoinCircle, useUpdateProfile } from '../api/hooks';
import { isApiError } from '../api/client';
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

export function OnboardingPage() {
  useDocumentTitle('Set up your profile');
  const { user } = useAuth();
  const nav = useNavigate();
  const toast = useToast();
  const update = useUpdateProfile();
  const join = useJoinCircle();
  const [city, setCity] = useState('');
  const circles = useCircles({ city: city || undefined, size: 20 });
  const [displayName, setDisplayName] = useState(user?.displayName ?? '');
  const [locality, setLocality] = useState(user?.locality ?? '');
  const [bio, setBio] = useState(user?.bio ?? '');
  const [errors, setErrors] = useState<Record<string, string>>({});

  useEffect(() => {
    if (user) {
      setDisplayName((v) => v || user.displayName);
      setLocality((v) => v || user.locality || '');
      setBio((v) => v || user.bio || '');
    }
  }, [user]);

  const joined = new Set(user?.circles.map((c) => c.circleId));
  const canFinish = joined.size > 0;

  const finish = (ev: FormEvent) => {
    ev.preventDefault();
    if (!displayName.trim()) {
      setErrors({ displayName: 'Tell your neighbours what to call you.' });
      return;
    }
    setErrors({});
    update.mutate(
      {
        displayName: displayName.trim(),
        locality: locality.trim(),
        bio: bio.trim(),
        onboarded: true,
      },
      {
        onSuccess: () => {
          toast.success('You are all set. Welcome to your circle.');
          nav('/', { replace: true });
        },
        onError: (e) => isApiError(e) && setErrors(e.fieldMap()),
      },
    );
  };

  return (
    <div className="page page--narrow">
      <PageHead eyebrow="Step 1 of 1" title="Set up your profile" />
      <form onSubmit={finish} noValidate className="stack">
        <FormError error={update.isError && !Object.keys(errors).length ? update.error : null} />
        <Field label="Display name" error={errors.displayName}>
          {(p) => (
            <input
              {...p}
              className="input"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
            />
          )}
        </Field>
        <Field label="Your locality" error={errors.locality} hint="Only your circles see this.">
          {(p) => (
            <input
              {...p}
              className="input"
              placeholder="e.g. 5th Block, Koramangala"
              value={locality}
              onChange={(e) => setLocality(e.target.value)}
            />
          )}
        </Field>
        <Field label="About you" error={errors.bio} hint="Optional. What can you lend or teach?">
          {(p) => (
            <textarea
              {...p}
              placeholder="Weekend cyclist, happy to lend a pump. Can help with Kannada homework."
              value={bio}
              onChange={(e) => setBio(e.target.value)}
            />
          )}
        </Field>

        <section className="section" aria-labelledby="circle-h">
          <h2 id="circle-h">Join a circle</h2>
          <p className="muted">
            A circle is your neighbourhood group. Join at least one to see listings.
          </p>
          <Field label="Filter by city">
            {(p) => (
              <input
                {...p}
                className="input"
                placeholder="Bengaluru, Mumbai, Delhi"
                value={city}
                onChange={(e) => setCity(e.target.value)}
              />
            )}
          </Field>
          <div aria-live="polite">
            {circles.isLoading && <Loading rows={2} label="Loading circles" />}
            {circles.isError && (
              <ErrorBox error={circles.error} onRetry={() => circles.refetch()} />
            )}
            {circles.data?.content.length === 0 && (
              <Empty title="No circles found">
                Try another city, or start one from the Circles page.
              </Empty>
            )}
            <ul className="ledger">
              {circles.data?.content.map((c) => (
                <li key={c.id}>
                  <div className="row row--between">
                    <div>
                      <strong>{c.name}</strong>
                      <p className="muted small">
                        {c.area}, {c.city} · {c.memberCount} members
                      </p>
                    </div>
                    {joined.has(c.id) ? (
                      <span className="tag tag--ok">Joined</span>
                    ) : (
                      <button
                        type="button"
                        className="btn btn--ghost btn--small"
                        disabled={join.isPending}
                        aria-label={`Join ${c.name}`}
                        onClick={() =>
                          join.mutate(c.id, {
                            onSuccess: () => toast.success(`You joined ${c.name}.`),
                            onError: (e) =>
                              toast.error(isApiError(e) ? e.message : 'Could not join.'),
                          })
                        }
                      >
                        Join
                      </button>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          </div>
        </section>

        <div className="row">
          <button
            type="submit"
            className="btn btn--signal"
            disabled={!canFinish || update.isPending}
          >
            {update.isPending ? 'Saving…' : 'Finish setup'}
          </button>
          {!canFinish && <span className="muted small">Join a circle to continue.</span>}
        </div>
      </form>
    </div>
  );
}
