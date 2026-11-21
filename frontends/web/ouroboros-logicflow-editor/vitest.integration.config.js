import { fileURLToPath } from 'node:url'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  esbuild: {
    loader: 'jsx',
    include: /src\/.*\.js$/,
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
      'ouroboros-sdk': fileURLToPath(
        new URL('../ouroboros-sdk/src/main.ts', import.meta.url)
      ),
      'monaco-editor': fileURLToPath(
        new URL('./tests/integration/logicflow/harness/platform/stubs/monaco-editor.mjs', import.meta.url)
      ),
    },
  },
  server: {
    fs: {
      allow: [fileURLToPath(new URL('..', import.meta.url))],
    },
  },
  test: {
    environment: 'jsdom',
    include: ['tests/integration/**/*.integration.test.js'],
    fileParallelism: false,
    maxConcurrency: 1,
    testTimeout: 30000,
  },
})
