# COMMONWEAVE — API contract & data model (source of truth)

Backend: Java 21, Spring Boot 3.x, Maven, Flyway, JPA, MySQL 8. Port 8080. Base path `/api/v1`.
Frontend: React 18 + TypeScript + Vite, port 5173, dev proxy `/api` → `http://localhost:8080` (same-origin, so cookies just work).
India context: currency **INR** (`₹`, whole rupees, `Intl.NumberFormat('en-IN')`), timezone **Asia/Kolkata**, demo circles in Indian neighbourhoods.

## Conventions
- JSON, camelCase. Instants = ISO-8601 with offset. Dates (`availableFrom/To`) = `YYYY-MM-DD`. Money = integer rupees (`estimatedValueInr`, `depositInr`).
- **Auth:** session cookie (`JSESSIONID`, HttpOnly; SameSite=Lax locally and SameSite=None in production for cross-site Vercel-to-Render requests). **CSRF:** `XSRF-TOKEN` cookie (readable by JS) + header `X-XSRF-TOKEN` on every non-GET. Use `CookieCsrfTokenRepository.withHttpOnlyFalse()` with `CsrfTokenRequestAttributeHandler` (non-XOR) plus a filter that force-loads the token so any GET (e.g. `GET /auth/me`, even a 401) sets the cookie.
- Public: `POST /auth/signup`, `POST /auth/login`, `GET /auth/me` (401 if anon), `GET /meta`, `/actuator/health`. Everything else needs a session. 401 = not signed in, 403 = not allowed, 404 = not found **or not visible to you** (don't leak existence).
- **Errors** (all): `{ "status":409, "code":"LISTING_UNAVAILABLE", "message":"…", "fieldErrors":[{"field":"title","message":"must not be blank"}], "timestamp":"…", "path":"/api/v1/…" }`. Codes: `VALIDATION_FAILED`(400) `UNAUTHENTICATED`(401) `FORBIDDEN`(403) `NOT_FOUND`(404) `CONFLICT`/`LISTING_UNAVAILABLE`/`INVALID_TRANSITION`/`EMAIL_TAKEN`(409) `INTERNAL`(500, no stack/secret leakage).
- **Pages:** query `page` (0-based, default 0), `size` (default 12, max 50) → `{ "content":[…], "page":0, "size":12, "totalElements":0, "totalPages":0 }`.
- Passwords ≥ 8 chars, BCrypt. Never log passwords/cookies/tokens. Email lower-cased, unique.

## Enums
- `Category`: `TOOLS, KITCHEN, GARDEN, ELECTRONICS, FURNITURE, BOOKS, KIDS, SPORTS_CYCLES, REPAIR_SKILLS, TUTORING, EVENTS, OTHER` (labels via `/meta`).
- `ListingType`: `OFFER | REQUEST`. `ListingKind`: `ITEM | SKILL | TIME`.
- `ListingStatus`: `ACTIVE | RESERVED | CLOSED | REMOVED` (REMOVED = by moderator; hidden from everyone but owner/moderators).
- `MemberRole`: `MEMBER | MODERATOR`. `MemberStatus`: `ACTIVE | SUSPENDED | REMOVED`.
- `HandoffStatus`: `PROPOSED, SCHEDULED, HANDED_OFF, RETURNED, COMPLETED, CANCELLED`.
- `TransitionAction`: `CONFIRM, MARK_HANDED_OFF, MARK_RETURNED, COMPLETE, CANCEL`.

## Database tables (Flyway `V1__…` onward; utf8mb4, InnoDB)
`users(id, email UQ, password_hash, display_name, bio, locality, onboarded, created_at)`
`circles(id, slug UQ, name, city, area, description, guidelines, created_at)`
`memberships(id, circle_id, user_id, role, status, joined_at, UQ(circle_id,user_id))`
`listings(id, circle_id, owner_id, type, kind, title, description, category, tags(csv), available_from, available_to, pickup_preference, estimated_value_inr, deposit_inr, hours_estimate, return_expected, status, version, created_at, updated_at)`
`handoffs(id, listing_id, circle_id, provider_id, recipient_id, proposer_id, status, message, scheduled_at, pickup_note, return_by, return_expected, created_at, updated_at, version, active_listing_id GENERATED ALWAYS AS (CASE WHEN status IN ('SCHEDULED','HANDED_OFF') THEN listing_id END) STORED, UNIQUE(active_listing_id))` ← DB-level guarantee: one active reservation per listing.
`handoff_events(id, handoff_id, actor_id, from_status, to_status, note, created_at)`
`notifications(id, user_id, type, title, body, link, dedupe_key, read_at, created_at, UQ(user_id,dedupe_key))`
`reports(id, circle_id, reporter_id, target_type LISTING|MEMBER, target_id, reason, status OPEN|RESOLVED|DISMISSED, resolution_note, resolved_by, created_at, resolved_at)`
`moderation_actions(id, circle_id, moderator_id, action_type, target_type, target_id, note, created_at)`

## Roles in a handoff
OFFER listing → owner is **provider**, proposer is **recipient**. REQUEST listing → owner is **recipient**, proposer is **provider** (volunteering). The **listing owner** confirms; either party may later mark progress. Moderators do not take part in handoffs.

## State machine (server enforces; invalid → 409 `INVALID_TRANSITION`)
```
PROPOSED --CONFIRM(owner; scheduledAt required, in future; pickupNote, returnBy optional)--> SCHEDULED
PROPOSED|SCHEDULED --CANCEL(either party)--> CANCELLED
SCHEDULED --MARK_HANDED_OFF(either)--> HANDED_OFF
HANDED_OFF --MARK_RETURNED(either; only if returnExpected)--> RETURNED
HANDED_OFF --COMPLETE(either; only if !returnExpected)--> COMPLETED
```
- Proposals do not block the listing (many PROPOSED allowed, but only one proposal per proposer per listing at a time). **CONFIRM** runs in one transaction: `SELECT … FOR UPDATE` on the listing row, fail with 409 `LISTING_UNAVAILABLE` if listing not ACTIVE or an active handoff exists; set listing `RESERVED`; auto-cancel nothing else. The unique generated column is the last line of defence (map the integrity violation to the same 409).
- On CANCEL of a SCHEDULED handoff → listing `ACTIVE`. On RETURNED → listing `ACTIVE`. On COMPLETED → listing `CLOSED`. (REQUEST listings: COMPLETED → CLOSED.)
- A suspended/removed member cannot propose or confirm. You cannot propose on your own listing, on a non-ACTIVE listing, or outside a circle you actively belong to.
- Every transition writes a `handoff_events` row and a notification to the other party. A scheduled job (hourly, `@Scheduled`) creates deduped reminder notifications: handoff within next 24 h, return due within 24 h, return overdue.
- **Trust record:** a member's `completedExchanges` = count of their COMPLETED/RETURNED handoffs. Visible only to co-members of a shared circle. **No ratings, no leaderboards, no ranking by it.**

## Matching (deterministic, explainable; no external API)
Candidates = opposite `type`, same circle, ACTIVE, different owner. Score 0–100 = category 35 (same) + keywords 25 (Jaccard of lower-cased title+tags tokens, stop-words removed, ×25) + availability 25 (overlap days ÷ shorter window days, ×25) + pickup 15 (Jaccard of pickup-preference tokens ×15; 8 if either blank). Drop score < 30. Sort desc, tie-break by newer listing id. Each match returns `reasons: [{factor, points, max, explanation}]` summing to `score`.

## Impact (estimate — always labelled)
Per COMPLETED/RETURNED handoff, constants in config and echoed in `methodology`:
- **₹ saved** — ITEM loan (returnExpected): 20 % of `estimatedValueInr` (assumes an item is shared ~5 times instead of each borrower buying). ITEM gift: 60 % (second-hand value vs new). SKILL/TIME: `hoursEstimate × ₹250` (assumed local service rate).
- **CO₂e avoided (kg)** — category factor × same share (loan 0.2 / gift 0.6; skill/time 0.1): TOOLS 6, ELECTRONICS 15, FURNITURE 8, KITCHEN 3, GARDEN 2, BOOKS 1, KIDS 4, SPORTS_CYCLES 7, others 1.
- **Hours shared** = sum of `hoursEstimate` for SKILL/TIME.
These are rough planning estimates, not measurements.

## Endpoints (all under `/api/v1`)
**Meta/Auth/Profile**
- `GET /meta` → `{currency:"INR", locale:"en-IN", timezone:"Asia/Kolkata", categories:[{code,label}], listingKinds:[…]}`
- `POST /auth/signup {email,password,displayName}` → 201 `User` (+session). `POST /auth/login {email,password}` → `User`. `POST /auth/logout` → 204. `GET /auth/me` → `User`/401.
- `PATCH /users/me {displayName?,bio?,locality?,onboarded?}` → `User`.
- `User` = `{id,email,displayName,bio,locality,onboarded,circles:[{circleId,name,role}]}`.

**Circles**
- `GET /circles?q=&city=&page=&size=` → Page<`CircleSummary`> `{id,slug,name,city,area,description,memberCount,activeListingCount,myRole|null}`
- `GET /circles/{id}` → `CircleDetail` = summary + `{guidelines, moderators:[{userId,displayName}], myStatus|null}`
- `POST /circles {name,city,area,description,guidelines}` → 201 (creator = MODERATOR)
- `POST /circles/{id}/join` → `CircleDetail` (idempotent; REMOVED/SUSPENDED members get 403). `POST /circles/{id}/leave` → 204 (last moderator cannot leave → 409).
- `PATCH /circles/{id} {description?,guidelines?}` (mod)
- `GET /circles/{id}/members` (members: ACTIVE only; mods: all) → `[{userId,displayName,role,status,joinedAt,completedExchanges}]`
- `PATCH /circles/{id}/members/{userId} {role?,status?}` (mod; can't demote/remove last mod)
- `GET /circles/{id}/activity?size=10` (members) → `[{type,text,at}]` (e.g. "Ananya offered a Bosch drill")

**Listings**
- `ListingSummary` = `{id,circleId,circleName,type,kind,title,category,tags:[…],availableFrom,availableTo,pickupPreference,estimatedValueInr,depositInr,status,owner:{id,displayName},createdAt}`; `ListingDetail` = summary + `{description,hoursEstimate,returnExpected,completedExchangesOfOwner,myHandoff:{id,status}|null,canEdit}`.
- `GET /listings?circleId=&type=&category=&kind=&q=&availableOn=YYYY-MM-DD&status=&mine=true&sort=newest|soonest&page&size` → Page<ListingSummary>. Default: ACTIVE+RESERVED in circles I actively belong to. `q` matches title/description/tags (LIKE).
- `GET /listings/{id}` → `ListingDetail`. `POST /listings` → 201; body `{circleId,type,kind,title(3-120),description(≤2000),category,tags[≤8],availableFrom,availableTo(≥from),pickupPreference(≤200),estimatedValueInr(0-500000),depositInr(0-100000),hoursEstimate(0-100),returnExpected}`. `PUT /listings/{id}` (owner; not while RESERVED → 409). `DELETE /listings/{id}` (owner → CLOSED; not while RESERVED).
- `GET /listings/{id}/matches` → `[{listing:ListingSummary,score,reasons:[…]}]` (top 5, visible to anyone who can see the listing)
- `GET /matches/suggested` → `[{myListing:ListingSummary,match:{listing,score,reasons}}]` top 6 across my ACTIVE listings.

**Handoffs**
- `HandoffSummary` = `{id,status,listing:{id,title,category,type},role:"PROVIDER"|"RECIPIENT",counterpart:{id,displayName},scheduledAt,returnBy,returnExpected,updatedAt}`; `HandoffDetail` = summary + `{message,pickupNote,pickupPreference,events:[{fromStatus,toStatus,actor:{id,displayName},note,at}],allowedActions:[TransitionAction…]}`.
- `POST /handoffs {listingId,message?}` → 201 `HandoffDetail` (PROPOSED).
- `GET /handoffs?status=&active=true&role=PROVIDER|RECIPIENT&page&size` → Page<HandoffSummary> (only mine). `GET /handoffs/{id}` (participants only, else 404).
- `POST /handoffs/{id}/transitions {action,scheduledAt?,pickupNote?,returnBy?,note?}` → `HandoffDetail`.

**Notifications:** `GET /notifications?unread=true&page&size` → Page<`{id,type,title,body,link,readAt,createdAt}`>; `GET /notifications/unread-count` → `{count}`; `POST /notifications/{id}/read` → 204; `POST /notifications/read-all` → 204.

**Moderation (circle moderators only unless stated)**
- `POST /reports {circleId,targetType,targetId,reason(5-500)}` (any active member) → 201.
- `GET /circles/{id}/reports?status=OPEN` → `[{id,targetType,targetId,targetLabel,reason,reporter:{id,displayName},status,createdAt}]`
- `POST /circles/{id}/reports/{reportId}/resolve {resolution:"RESOLVED"|"DISMISSED",note,removeListing?}` → 204
- `POST /circles/{id}/listings/{listingId}/remove {note}` → 204 (status REMOVED; cancels PROPOSED handoffs; refuses if HANDED_OFF).
- `GET /circles/{id}/moderation-log` → `[{actionType,targetType,targetId,note,moderator:{id,displayName},createdAt}]`

**Dashboard & Impact**
- `GET /dashboard` → `{upcomingHandoffs:[HandoffSummary], needsAction:[HandoffSummary], suggestions:[{myListing,match}], circleActivity:[{circleName,type,text,at}], unreadNotifications:int}`. `needsAction` = PROPOSED where I'm owner, or overdue returns.
- `GET /circles/{id}/impact?days=90` (members) and `GET /impact/me?days=90` → `{periodDays,completedExchanges,activeMembers,rupeesSavedEstimate,co2KgAvoidedEstimate,hoursShared,byCategory:[{category,count,rupeesSaved}],methodology:[{label,formula,assumption}],disclaimer:"Estimates only…"}`

## Demo data (profile `dev`, only when `users` is empty; Java seeder using `PasswordEncoder`; dates relative to today)
~12 members with Indian names; circles: "Koramangala 5th Block Neighbours" (Bengaluru), "Bandra West Commons" (Mumbai), "Hauz Khas Village Circle" (Delhi); ~30 listings (drill, folding table, pressure cooker, cycle repair, Hindi/Kannada tutoring, Diwali-decor swap, balcony-garden advice, society-event chairs/tents) priced in ₹; handoffs in every state; completed ones for impact; a few notifications; one open report. Demo password for all seed users: `Welcome@123` (documented as demo-only). Primary demo login: `ananya@example.in`; moderator: `rohan@example.in` (moderator of Koramangala).

## Frontend routes
`/login /signup /onboarding /` (dashboard) `/explore /listings/new /listings/:id /listings/:id/edit /handoffs /handoffs/:id /circles /circles/:id /circles/:id/moderate /impact /notifications /me`. Onboarding = profile + pick/join a circle; redirect there while `user.onboarded=false`.

## File ownership
`backend/` → backend agent. `frontend/` → frontend agent. `e2e/` + `backend/src/test/**/security/` → QA agent (after merge). `docs/`, root files, `scripts/` → coordinator. Nobody edits another's folder.

## Scripts (coordinator provides)
`scripts/dev-backend.sh` (sources `.env`, sets `JAVA_HOME=/opt/homebrew/opt/openjdk@21`, `mvn spring-boot:run`), `scripts/test-backend.sh` (same env, `mvn verify` against `DB_TEST_URL`), `scripts/dev-frontend.sh`. Backend reads `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (no password default in code); tests read `DB_TEST_URL`.

---
## ADDENDUM A — Platform admin (added after the first build started; applies to backend + frontend)
- **Schema:** `users` gains `platform_role ENUM('USER','ADMIN') NOT NULL DEFAULT 'USER'`, `enabled BOOLEAN NOT NULL DEFAULT TRUE`, `last_login_at DATETIME NULL`. New table `admin_actions(id, admin_id, action, target_type, target_id, note, created_at)`. (Add to the migrations; if V1 is already written, add the next migration.)
- **Login:** `POST /auth/login {email,password}` — the `email` field also accepts a plain **username**, so the admin signs in with `admin`. Frontend label: "Email or username", `type="text"`. Signup still requires a real email. Disabled users get 403 `ACCOUNT_DISABLED` on login and on any existing session. `User` DTO gains `platformRole: "USER"|"ADMIN"`.
- **Admin account (profile `dev` only):** seeder creates user email/username `admin`, display name "Platform Admin", `platform_role=ADMIN`, `onboarded=true`, password `admin` (seed bypasses the 8-char rule; **never created outside the `dev` profile**; other profiles create an admin only if `APP_ADMIN_EMAIL` + `APP_ADMIN_PASSWORD` env vars are set). The admin is not a circle member and cannot take part in handoffs.
- **Authorization:** everything under `/api/v1/admin/**` requires `ROLE_ADMIN` → otherwise 403. Admin responses never include `password_hash`. Every admin write inserts an `admin_actions` row.
- **Endpoints**
  - `GET /admin/overview` → `{users:{total,disabled,admins},circles,listings:{ACTIVE,RESERVED,CLOSED,REMOVED},handoffs:{PROPOSED,SCHEDULED,HANDED_OFF,RETURNED,COMPLETED,CANCELLED},openReports}`
  - `GET /admin/users?q=&enabled=&page&size` → Page<`AdminUser`> = `{id,email,displayName,locality,platformRole,enabled,onboarded,createdAt,lastLoginAt,circles:[{circleId,name,role,status}],listingCount,completedExchanges}`
  - `GET /admin/users/{id}` → `AdminUser` + `{bio,recentListings:[ListingSummary],recentHandoffs:[HandoffSummary]}` (read-only view of their activity)
  - `PATCH /admin/users/{id} {enabled?,platformRole?,displayName?}` → `AdminUser` (cannot disable or demote yourself, nor the last admin → 409)
  - `POST /admin/users/{id}/reset-password {newPassword(≥8)}` → 204
  - `GET /admin/circles`, `GET /admin/listings?status=&q=`, `GET /admin/handoffs?status=`, `GET /admin/reports?status=` → Page lists (same item shapes as the member endpoints, plus ids of owner/circle)
  - `POST /admin/listings/{id}/remove {note}` → 204 (same effect as moderator removal)
  - `GET /admin/audit-log?page&size` → Page<`{action,targetType,targetId,note,admin:{id,displayName},createdAt}`>
- **Frontend:** nav shows "Admin" only when `platformRole==="ADMIN"`; admin lands on `/admin` after login and skips onboarding. Routes: `/admin` (overview cards), `/admin/users` (search, enable/disable toggle, role change, reset password dialog), `/admin/users/:id` (profile + activity), `/admin/circles`, `/admin/listings` (remove), `/admin/reports`, `/admin/audit`. Non-admins visiting `/admin/*` see a 403 page. Confirm dialogs before disabling/removing.
