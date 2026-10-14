# COMMONWEAVE Architecture

## 1. Overview and Goals
COMMONWEAVE facilitates sharing and borrowing within local communities in India. It enables users to create listings for items or skills, propose handoffs, and track exchanges. The app emphasizes trust-building, deterministic matching, and impact estimation without leaderboards or ratings.

## 2. System Diagram
```
Browser → Vite/React (port 5173) → /api proxy → Spring Boot (port 8080) → MySQL
```

## 3. Backend Structure
- **Packages:** `auth`, `circle`, `handoff`, `listing`, `moderation`, `notification`, `report`, `user`, `meta`, `dashboard`, `impact`
- **Layering:** 
  - Controllers handle HTTP requests/responses.
  - Services enforce business logic and orchestrate transactions.
  - Repositories interact with the database via JPA.
- **Transactions:** Used for critical operations like handoff transitions, ensuring atomicity.

## 4. Data Model
- **Tables:** `users`, `circles`, `memberships`, `listings`, `handoffs`, `handoff_events`, `notifications`, `reports`, `moderation_actions`
- **Handoff State Machine:**
```
PROPOSED --CONFIRM--> SCHEDULED --MARK_HANDED_OFF--> HANDED_OFF --MARK_RETURNED--> RETURNED
                                                                 --COMPLETE--> COMPLETED
PROPOSED|SCHEDULED --CANCEL--> CANCELLED
```

## 5. Key Decisions
- Session cookie + CSRF: Simpler than JWT for session-based apps.  
- SELECT FOR UPDATE + generated-column unique index: Prevents double-booking.  
- Deterministic explainable matching: Transparency builds trust.  
- Impact estimates: Rough planning over precise measurements.  
- No ratings/leaderboards: Focus on trust-building, not competition.  
- Flyway: Reliable database migrations.  
- MySQL-real tests: Ensures production-like behavior.  
- India context: INR currency, IST timezone, Indian neighbourhoods.

## 6. Security Model
- **Authn:** Session cookie (`JSESSIONID`) with CSRF protection (`XSRF-TOKEN`).  
- **Authz:** Role-based access control (`MEMBER`, `MODERATOR`).  
- **404-not-403:** Don't leak existence of hidden resources.  
- **Password hashing:** BCrypt with minimum 8-character length.  
- **CORS:** Same-origin proxy for frontend.  
- **Logging:** No secrets (passwords, tokens) in logs or Git.

## 7. Frontend Structure and Accessibility Approach
- **Routes:** `/login`, `/signup`, `/onboarding`, `/dashboard`, `/explore`, `/listings`, `/handoffs`, `/circles`, `/impact`, `/notifications`, `/me`
- **Accessibility:** Semantic HTML, ARIA roles, keyboard navigation, and contrast ratios compliant with WCAG 2.1.

## 8. Testing Strategy
- **Backend:** Integration tests with MySQL, covering business logic, transactions, and security.  
- **Frontend:** Component tests with React Testing Library, focusing on UI and accessibility.  
- **E2E:** Playwright tests for user flows across frontend and backend.  
- **Security:** Penetration testing and manual security reviews.

## 9. Verification Status
| Check | Result |
|-------|--------|
| Backend tests (JUnit + MockMvc on real MySQL `commonweave_test`, incl. 8-thread reservation race) | 115 passed, 0 failed |
| Frontend (ESLint, `tsc`, Vitest + axe) | lint clean, 46 passed, 0 failed |
| Production builds | frontend `npm run build` OK; backend `mvn verify` OK |
| Playwright E2E (Chromium, real backend + frontend + MySQL) | 3 passed: signup to returned handoff, admin sign-in, member blocked from `/admin` |
| Security review (Opus, medium effort; see `security-review.md`) | 0 critical / 0 high; 3 medium + 4 low fixed with regression tests |
| Ruflo `aidefence_scan` | not run: `@claude-flow/aidefence` module missing in the Ruflo MCP environment |

### Known limitations
- Signup reveals whether an email is registered (review item L5, accepted for now).
- Login/signup throttling is in-memory and per instance; use a shared store if the backend is ever scaled out.
- The seeded `admin` / `admin` account exists only under the `dev` profile. Outside `dev` an admin is created only from `APP_ADMIN_EMAIL` + `APP_ADMIN_PASSWORD`.
- Impact figures are rough estimates (see the methodology returned by the API), not measurements.
- No admin page for the handoffs list yet (the endpoint exists).
