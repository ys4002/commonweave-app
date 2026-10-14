# COMMONWEAVE

A neighbourhood **micro-commons** for India: share tools, skills and time with the people in your society or mohalla, and keep a calm, private record of every exchange. Not a marketplace — no public star ratings, no leaderboards. Trust comes from a **handoff loop**: *proposed → scheduled → handed off → returned / completed*, with reminders and a privacy-respecting count of successful exchanges.

Money is shown in **₹ (INR)**, times in **IST**, and the demo data is set in Bengaluru, Mumbai and Delhi neighbourhoods.

## Stack
| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3, Spring Security (session cookie + CSRF), Spring Data JPA, Flyway |
| Database | MySQL 8 for local development; PostgreSQL 16 for Docker and production |
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

## Run with Docker Compose
Requires Docker Engine or Docker Desktop with Compose v2. The container setup runs PostgreSQL, the Spring Boot backend, and the frontend. Copy the example environment files to their working paths, and use the same PostgreSQL password in the database and backend files:

```bash
cp database/.env.example database/.env
cp backend/.env.example backend/.env
cp frontend/.env.example frontend/.env
docker compose up --build
```

Open the app at `http://localhost:5173`; the API is available at `http://localhost:8080`. Compose persists PostgreSQL data in the `postgres-data` volume. The backend uses the PostgreSQL Flyway scripts in this setup. For production, set `SPRING_PROFILES_ACTIVE=prod`, point `DB_URL` at the managed PostgreSQL database, and set `DB_USERNAME`, `DB_PASSWORD`, `APP_CORS_ORIGINS`, and `APP_COOKIE_SECURE=true` in the backend environment. Set `API_UPSTREAM` in the frontend environment to the backend service address. The `prod` profile selects the PostgreSQL migrations automatically.

Environment files are kept beside each service: `backend/.env`, `frontend/.env`, and `database/.env`. Keep secrets in those ignored files or in your deployment platform’s secret manager. The root `.env` remains for the non-containerized local scripts, which use MySQL by default.

## Test it
```bash
bash scripts/test-backend.sh      # unit + API + MySQL integration + concurrency (DB: commonweave_test)
cd frontend && npm run lint && npm test -- --run && npm run build
cd e2e && npm install && npx playwright install chromium && npx playwright test   # starts (or reuses) backend + frontend
```

## Documentation
- [`docs/api-contract.md`](docs/api-contract.md) — endpoints, JSON shapes, state machine, matching and impact formulas, data model
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, key decisions, security notes, verification status
