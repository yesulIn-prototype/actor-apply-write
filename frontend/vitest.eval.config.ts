import { defineConfig } from 'vitest/config'

// Accuracy evaluation against eval/gold. Needs the backend running, so it stays out of `npm test`.
export default defineConfig({
  test: {
    environment: 'node',
    include: ['eval/**/*.eval.ts'],
  },
})
