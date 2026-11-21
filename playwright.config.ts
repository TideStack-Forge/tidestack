import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './testing/e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { outputFolder: 'tmp/testing-evidence/playwright-report' }],
    ['json', { outputFile: 'tmp/testing-evidence/test-results.json' }],
    ['list']
  ],
  use: {
    baseURL: process.env.OB_TEST_FRONTEND_BASE_URL || 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
  webServer: undefined,
});
