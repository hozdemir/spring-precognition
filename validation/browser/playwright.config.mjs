import { defineConfig } from '@playwright/test';

const platform = process.env.PRECOGNITION_REPORT?.includes('/boot3/') ? 'boot3' : 'boot4';
const results = `../../target/browser-results/${platform}`;

export default defineConfig({
  testDir: '.',
  testMatch: 'forms.spec.mjs',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  outputDir: results,
  reporter: [['list'], ['json', { outputFile: `${results}/report.json` }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    channel: process.env.PRECOGNITION_BROWSER ?? 'chrome',
    trace: 'retain-on-failure',
  },
  webServer: {
    command: 'node server.mjs',
    url: 'http://127.0.0.1:4173/react.html',
    reuseExistingServer: false,
    timeout: 45000,
  },
});
