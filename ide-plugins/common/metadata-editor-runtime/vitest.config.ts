import { resolve } from 'node:path'
import { defineConfig } from 'vitest/config'

const repoRoot = resolve(__dirname, '../../..')
const sharedAmisSrc = resolve(repoRoot, 'frontends/web/ouroboros-amis-runtime/src')

export default defineConfig({
  resolve: {
    alias: {
      'ouroboros-amis-runtime': sharedAmisSrc,
    },
  },
  test: {
    environment: 'node',
    include: ['tests/unit/**/*.test.ts', 'tests/unit/**/*.test.mjs'],
  },
})
