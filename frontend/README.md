# Commonweave frontend

React 18 + TypeScript + Vite. Plain CSS with design tokens (`src/styles/tokens.css`). Built to `docs/api-contract.md`.

```bash
npm install
npm run dev          # http://localhost:5173, proxies /api -> http://localhost:8080
npm run lint
npm run test -- --run
npm run build
```

- `src/api`: typed client (`client.ts`: same-origin cookies, XSRF header, error parsing), contract types (`types.ts`), TanStack Query hooks (`hooks.ts`).
- `src/pages`: one file per area (auth, onboarding, dashboard, explore, listings, handoffs, circles, moderation, impact, notifications/profile, admin).
- `src/test`: Vitest + Testing Library with a mocked `fetch` (`utils.tsx`), plus axe checks.
- Selectors for QA: use roles and labels (for example `getByRole('button', { name: 'Confirm handoff' })`, `getByLabel('Email or username')`). No `data-testid`.
- Demo logins (dev profile): `ananya@example.in` / `Welcome@123`, moderator `rohan@example.in`, admin `admin` / `admin`.
