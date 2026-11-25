import { defineConfig, devices } from '@playwright/test'

// UI end-to-end suite (docs/qa/e2e.md). It expects the local stack to be running already
// (`scripts/e2e/stack.sh up`): backend on :8080, Supabase Auth on :54321, Mailpit on :54324.
const apiTarget = process.env.E2E_API_URL ?? 'http://localhost:8080'
const baseURL = 'http://localhost:3000'
const ci = Boolean(process.env.CI)

export default defineConfig({
  testDir: 'e2e',
  testMatch: '**/*.spec.ts',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: true,
  forbidOnly: ci,
  retries: ci ? 1 : 0,
  workers: ci ? 2 : undefined,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL,
    locale: 'es-CO',
    actionTimeout: 10_000,
    navigationTimeout: 30_000,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: 'npm run dev',
    url: baseURL,
    reuseExistingServer: !ci,
    timeout: 120_000,
    // vite.config.ts proxies /api to this target (the backend sends no CORS headers)
    env: { VITE_API_TARGET: apiTarget },
  },
})
