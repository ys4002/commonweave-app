import { defineConfig } from '@playwright/test';

// Starts (or reuses) the real backend and frontend; the backend needs the gitignored root .env.
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  retries: 0,
  workers: 1,
  reporter: [['list']],
  use: { baseURL: 'http://localhost:5173', timezoneId: 'Asia/Kolkata', locale: 'en-IN', trace: 'retain-on-failure' },
  webServer: [
    { command: 'bash ../scripts/dev-backend.sh', url: 'http://localhost:8080/actuator/health', reuseExistingServer: true, timeout: 180_000 },
    { command: 'bash ../scripts/dev-frontend.sh', url: 'http://localhost:5173', reuseExistingServer: true, timeout: 120_000 },
  ],
});
