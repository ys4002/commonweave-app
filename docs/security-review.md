# COMMONWEAVE security review

Date: 2026-10-14. Scope: read-only code review of `backend/src/main` and `frontend/src`, checked against `docs/api-contract.md` (including Addendum A). No servers were started and no database probes were run. Every finding below was confirmed by reading the code.

Summary: 0 critical, 0 high, 3 medium, 5 low.

---

## M1. Former, suspended or removed members can still list a circle's active listings through matches (medium)

- **Where:** `matching/MatchingService.java:43-47` (`forListing`) together with `listing/ListingAccess.java:27-29`.
- **Cause:** `requireVisible` returns true for a listing's owner even when the owner is no longer an active member of that circle. `forListing` then runs `candidates(source)` against that circle (`MatchingService.java:74-78`). It returns up to 5 ACTIVE listings from other members, with owner names, titles, tags and pickup preferences. The source listing's status is never checked, so a CLOSED listing still works. When a member leaves, `CircleService.leave` closes their listings, but those listings are still "theirs".
- **Exploit:** A member is suspended or removed by a moderator, or leaves on their own. They call `GET /api/v1/listings/{theirOldListingId}/matches` and keep reading the circle's current offers and requests. They can change keywords and dates by editing an ACTIVE listing, if they still have one (see M2), and get different results each time. This goes against the rule that only active members can see what a circle contains.
- **Fix:** In `forListing`, after `requireVisible`, require `circles.isActiveMember(source.getCircleId(), userId)` and `source.getStatus() == ACTIVE`. Otherwise return 404, or an empty list.

## M2. Suspended or removed members keep live, editable listings in the circle (medium)

- **Where:** `listing/ListingService.java:111-120` (`ownedListing` uses only `canSee`, and the owner always passes), `ListingService.java:93-99` (`update`), and `circle/CircleService.java:161-184` (`updateMember` changes status but leaves the member's listings alone).
- **Cause:** When a moderator sets a member to SUSPENDED or REMOVED, that member's ACTIVE listings stay ACTIVE and visible to everyone in the circle. The suspended owner can still `PUT /listings/{id}`, because no membership check is made for owners. Other members can still propose on those listings (`HandoffService.propose` checks only the proposer's membership), but the owner cannot confirm (`requireCanConfirm`). Those proposals will never be confirmed.
- **Exploit:** A member is suspended for posting abusive content. They edit the title and description of their still-ACTIVE listing to repost the content, and every circle member sees it in search and on the dashboard. Moderators must find and remove each listing by hand.
- **Fix:** (a) In `ownedListing`, require `circles.isActiveMember(l.getCircleId(), userId)` for update and edit, and keep `close` allowed. (b) In `updateMember`, when the new status is not ACTIVE, close the member's ACTIVE listings and call `handoffs.cancelOpenForListing(..., false, ...)`, as `leave()` already does.

## M3. No brute-force protection on login (medium)

- **Where:** `auth/AuthService.java:59-66`, `auth/SecurityConfig.java:37-39`.
- **Cause:** `/api/v1/auth/login` is public and has no rate limit, no lockout, no backoff and no CAPTCHA hook. The minimum password length is 8 characters and there is no strength check. Platform admin accounts (Addendum A) use the same endpoint.
- **Exploit:** An attacker gets a CSRF token from `GET /auth/me`, then runs password guesses against `POST /auth/login` for a known admin email (or `admin` on a dev deployment that is reachable). Success gives full `/admin/**` access, including password resets for any user.
- **Fix:** Add a limiter keyed on both account and client IP, for example a Bucket4j or Resilience4j filter on `/auth/login` and `/auth/signup`, with roughly 5 failures per 15 minutes per account plus an IP bucket. Return 429. Log failed attempts without the password.

---

## L1. Session and XSRF cookies are not marked Secure (low)

- **Where:** `resources/application.yml:31-36` sets no `server.servlet.session.cookie.secure`. `SecurityConfig.java:44-45` sets no Secure flag on the XSRF cookie.
- **Exploit:** If the app is served over TLS but a user ever opens an `http://` URL on the same host, for example through a downgrade on hostile Wi-Fi, the browser sends `JSESSIONID` in cleartext and the session can be hijacked.
- **Fix:** Add `server.servlet.session.cookie.secure: ${APP_COOKIE_SECURE:true}`, set it to false only in profile `dev`, and add `c.secure(...)` in the XSRF cookie customizer. When TLS ends at a proxy, also set `server.forward-headers-strategy: native` and HSTS.

## L2. An admin password reset leaves the target's existing sessions alive (low)

- **Where:** `admin/AdminService.java:169-175`. `AccountStatusFilter.java:48-62` re-checks only `enabled` and the admin flag.
- **Exploit:** An account is compromised and the admin resets its password. The attacker's existing session stays valid for up to the 8-hour timeout, so the reset does not cut them off.
- **Fix:** Add a `credentials_changed_at` (or `session_version`) column. Store it in `AppUserDetails` at login. In `AccountStatusFilter`, invalidate the session when the stored value no longer matches the database (the same path as a disabled account). Alternatively, use Spring Session's `FindByIndexNameSessionRepository` to delete the user's sessions.

## L3. Race on the last-admin check (low)

- **Where:** `admin/AdminService.java:146` (`countByPlatformRoleAndEnabled(...) <= 1` with no lock).
- **Exploit:** Two admins demote or disable each other at the same moment. Under REPEATABLE READ or READ COMMITTED, both transactions count 2 and both commit, leaving zero enabled admins. Recovery then needs direct database access.
- **Fix:** Lock the admin rows before counting, for example with `@Lock(PESSIMISTIC_WRITE) select u from User u where u.platformRole = ADMIN and u.enabled = true`, then count the result. Alternatively, run this method at SERIALIZABLE isolation.

## L4. The platform admin can join circles and take part in handoffs, which Addendum A forbids (low)

- **Where:** `circle/CircleService.java:100-113` (`join` does not check the platform role). After joining, `HandoffService.propose` and `ListingService.create` allow the admin like any member.
- **Exploit:** The audit trail is weakened. An admin account (or a stolen admin session) can join a circle, propose or confirm handoffs, and see member-only data. None of it is recorded in `admin_actions`, and Addendum A says this should not be possible ("The admin is not a circle member and cannot take part in handoffs").
- **Fix:** In `CircleService.join` and `create`, reject callers whose `User.isAdmin()` is true with a 403.

## L5. Signup and login reveal which emails have accounts (low, partly accepted)

- **Where:** `auth/AuthService.java:43-45` returns `EMAIL_TAKEN`. `AuthService.java:91-95` returns `ACCOUNT_DISABLED`, but only after a correct password.
- **Exploit:** An attacker can list registered emails through `POST /auth/signup`, then target those accounts with M3. Login itself does not reveal whether an account exists, because `DaoAuthenticationProvider` handles unknown users and wrong passwords in the same time.
- **Fix:** Covered mostly by adding the M3 rate limit to `/auth/signup` as well. Full protection would need an email-verification flow that returns the same response for every signup.

---

## Checked and found fine

- **Sessions and CSRF:** Logins call `changeSessionId()` to prevent session fixation, and logout invalidates the session. CSRF uses a cookie token checked against a header, applies to every POST (login included), and the cookie is SameSite=Lax. CORS allows only origins listed in the environment, with credentials. `JSESSIONID` is HttpOnly, and errors never return messages or stack traces.
- **Disabled accounts and role changes:** `AccountStatusFilter` runs before `AuthorizationFilter` on every request. It kills the session of a disabled account and applies a changed admin flag immediately. `/api/v1/admin/**` requires `hasRole("ADMIN")`. Self-demotion and self-disable return 409.
- **Authorization:** Each handoff is visible only to its two participants (anyone else gets 404). CONFIRM requires the listing owner, who must be an active member. Reports, report resolution, moderation log and moderator removals all call `requireModerator`, and report resolution is scoped to its circle. Notifications are filtered by `findByIdAndUserId`. Impact and activity require an active member, and dashboard and suggestions use only `activeCircleIds`. Listing search always applies the visibility predicate.
- **Reservation concurrency:** The handoff row is locked before the listing row (`PESSIMISTIC_WRITE`), the code re-checks `ensureAvailable` at READ_COMMITTED, the `uq_handoffs_active_listing` index on a generated column backs this up, and a `DataIntegrityViolationException` maps to `LISTING_UNAVAILABLE`.
- **Mass assignment:** Request DTOs are records with no status, owner or circle fields to overwrite. Listing update ignores `circleId`, and status changes only happen through the state machine.
- **SQL injection:** All queries use JPA Criteria or bound parameters (the one native query, `INSERT IGNORE`, is parameterized). LIKE input escapes `%`, `_` and `\`. Sort is either a fixed whitelist (`newest`/`soonest`) or hard-coded, and page size is capped at 50.
- **Passwords:** Hashed with BCrypt and limited to 8-72 characters. Admin DTOs never include `passwordHash`.
- **Admin seeding:** `admin`/`admin` and `DemoDataSeeder` exist only in profile `dev`. No tracked file activates `dev`, and other profiles need `APP_ADMIN_EMAIL` plus a password of at least 8 characters.
- **Secrets:** No `.env` files or secrets are in the tree or the git history (`.gitignore` excludes `.env*`). Logs never contain passwords, tokens or query strings.
- **Frontend:** No `dangerouslySetInnerHTML`, `innerHTML` or `eval`, and no `href` built from user data. Nothing is stored in localStorage. The XSRF token is read from the cookie and sent as `X-XSRF-TOKEN` with same-origin credentials.
