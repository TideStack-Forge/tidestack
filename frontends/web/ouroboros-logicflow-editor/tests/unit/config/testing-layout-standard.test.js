const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')
const packageRoot = path.resolve(__dirname, '..', '..', '..')

test('logicflow editor source tests live under tests/unit', () => {
  const packageJson = JSON.parse(fs.readFileSync(path.join(packageRoot, 'package.json'), 'utf8'))

  assert.ok(packageJson.scripts.test, 'package.json should expose test')
  assert.ok(packageJson.scripts['test:unit'], 'package.json should expose test:unit')
  assert.match(
    packageJson.scripts['test:unit'],
    /vitest run/,
    'test:unit should be backed by Vitest'
  )
  assert.ok(!packageJson.scripts['test:node'], 'legacy test:node alias should be removed')
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/README.md')),
    'tests/README.md should document the package testing standard'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'tests/unit/src/script-intelligence-source.test.js')),
    'script intelligence source test should live under tests/unit/src'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'vitest.config.js')),
    'logicflow editor should define a vitest config'
  )
  assert.ok(
    fs.existsSync(path.join(packageRoot, 'webpack.config.js')),
    'logicflow editor should define a webpack config'
  )
  assert.ok(
    !fs.existsSync(path.join(packageRoot, 'vue.config.js')),
    'logicflow editor should not keep a vue-cli config'
  )
  assert.ok(
    !fs.existsSync(path.join(packageRoot, 'build/index.js')),
    'logicflow editor should not keep a legacy build wrapper once package.json build is webpack-owned'
  )
  const forbiddenDeps = ['vue', 'vuex']
  forbiddenDeps.forEach((dep) => {
    assert.ok(
      !packageJson.dependencies?.[dep],
      `dependencies should not include retired Vue dependency: ${dep}`
    )
  })
  const forbiddenDevDeps = [
    '@vue/cli-plugin-babel',
    '@vue/cli-service',
    'eslint-plugin-vue',
    '@vitejs/plugin-vue',
  ]
  forbiddenDevDeps.forEach((dep) => {
    assert.ok(
      !packageJson.devDependencies?.[dep],
      `devDependencies should not include retired Vue tooling: ${dep}`
    )
  })
  assert.deepEqual(
    fs.readdirSync(path.join(packageRoot, 'src/logicFlowEditor')).filter((entry) => entry.endsWith('.vue')),
    [],
    'logicFlowEditor source should not keep active .vue files'
  )
  assert.ok(
    fs.existsSync(
      path.join(packageRoot, 'tests/integration/logicflow/harness/createLogicflowEditorHarness.mjs')
    ),
    'logicflow editor integration harness should live under tests/integration'
  )
  assert.ok(
    !fs.existsSync(
      path.join(packageRoot, 'src/logicFlowEditor/script-intelligence-source.test.js')
    ),
    'source-local test file should be removed from src/logicFlowEditor'
  )
  assert.ok(
    !fs.existsSync(path.join(packageRoot, 'src/logicFlowEditor/testing')),
    'src/logicFlowEditor/testing should be removed after integration-test standardization'
  )
})
