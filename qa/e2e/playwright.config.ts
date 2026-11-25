import { defineConfig, devices } from '@playwright/test';
import { defineBddConfig, cucumberReporter } from 'playwright-bdd';
import { env } from './support/env.ts';

// Los .feature se traducen a pruebas de Playwright en .features-gen/ (bddgen, lo corre `npm test`).
const testDir = defineBddConfig({
  features: 'features/*.feature',
  steps: ['steps/*.ts'],
  language: 'es',
});

export default defineConfig({
  testDir,
  globalSetup: './support/global-setup.ts',
  outputDir: 'reports/test-results',
  // Un solo worker: todas las pruebas comparten un backend con una agenda de un recurso, así que
  // en paralelo competirían por las mismas franjas. Los navegadores corren uno tras otro.
  workers: 1,
  fullyParallel: false,
  retries: 0,
  timeout: 90_000,
  expect: { timeout: 10_000 },
  reporter: [
    ['list'],
    ['html', { outputFolder: 'reports/playwright-html', open: 'never' }],
    ['json', { outputFile: 'reports/playwright-results.json' }],
    cucumberReporter('html', { outputFile: 'reports/cucumber/cucumber-report.html' }),
    cucumberReporter('json', { outputFile: 'reports/cucumber/cucumber-report.json' }),
  ],
  use: {
    baseURL: env.baseUrl,
    headless: true,
    locale: 'es-CO',
    timezoneId: 'America/Bogota',
    viewport: { width: 1280, height: 800 },
    screenshot: 'on',
    video: 'on',
    trace: 'on',
    actionTimeout: 15_000,
    navigationTimeout: 30_000,
  },
  projects: [
    { name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } },
    { name: 'firefox', use: { ...devices['Desktop Firefox'], viewport: { width: 1280, height: 800 } } },
    { name: 'msedge', use: { ...devices['Desktop Edge'], channel: 'msedge', viewport: { width: 1280, height: 800 } } },
  ],
});
