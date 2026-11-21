const { defineConfig } = require('vitest/config')

module.exports = defineConfig({
  resolve: {
    alias: {
      'ouroboros-sdk': require('node:path').resolve(
        __dirname,
        '../ouroboros-sdk/src/main.ts'
      ),
    },
  },
  test: {
    globals: true,
    environment: 'node',
    include: ['tests/unit/**/*.test.js'],
  },
})
