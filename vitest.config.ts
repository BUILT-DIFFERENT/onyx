// vitest.config.ts
import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    include: [
      'apps/web/src/**/*.test.{ts,tsx}',
      'packages/*/src/**/*.test.{ts,tsx}',
      'tests/contracts/src/**/*.test.ts',
      'tests/mocks/**/*.test.ts',
    ],
    exclude: ['**/node_modules/**', 'convex/**'],
    setupFiles: ['./tests/setup.ts'],
  },
});
