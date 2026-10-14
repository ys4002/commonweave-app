# COMMONWEAVE

A neighbourhood **micro-commons** for India: share tools, skills and time with the people in your society or mohalla, and keep a calm, private record of every exchange. Not a marketplace — no public star ratings, no leaderboards. Trust comes from a **handoff loop**: *proposed → scheduled → handed off → returned / completed*, with reminders and a privacy-respecting count of successful exchanges.

Money is shown in **₹ (INR)**, times in **IST**, and the demo data is set in Bengaluru, Mumbai and Delhi neighbourhoods.

## Stack
| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security (session cookie + CSRF), Spring Data JPA, Flyway |
| Database | MySQL 8 (local server, no Docker needed) |
| Frontend | React 18, TypeScript, Vite, React Router, TanStack Query |
| Tests | JUnit/MockMvc on **real MySQL**, Vitest + Testing Library, Playwright (Chromium) |

## Prerequisites
JDK 21 (Homebrew: `/opt/homebrew/opt/openjdk@21`, the scripts set `JAVA_HOME` for you), Maven 3.9+, Node 20+, MySQL running on `localhost:3306`.

## Run it
```bash
cp .env.example .env        # then set DB_PASSWORD (the file is gitignored)
bash scripts/dev-backend.sh   # http://localhost:8080  (creates DB `commonweave`, runs Flyway, loads demo data)
bash scripts/dev-frontend.sh  # http://localhost:5173
```
Demo login (demo data only): `ananya@example.in` / `Welcome@123` · moderator: `rohan@example.in` / `Welcome@123`.

## Test it
```bash
bash scripts/test-backend.sh      # unit + API + MySQL integration + concurrency (DB: commonweave_test)
cd frontend && npm run lint && npm test -- --run && npm run build
cd e2e && npm install && npx playwright install chromium && npx playwright test   # starts (or reuses) backend + frontend
```

## Documentation
- [`docs/api-contract.md`](docs/api-contract.md) — endpoints, JSON shapes, state machine, matching and impact formulas, data model
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, key decisions, security notes, verification status
