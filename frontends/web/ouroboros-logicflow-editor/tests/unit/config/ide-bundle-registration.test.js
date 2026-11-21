const assert = require('node:assert/strict')
const fs = require('node:fs')
const os = require('node:os')
const path = require('node:path')
const webpack = require('webpack')
const test = globalThis.test || require('node:test')

const createWebpackConfig = require('../../../webpack.config.js')

function runWebpack(config) {
  return new Promise((resolve, reject) => {
    const compiler = webpack(config)
    compiler.run((runError, stats) => {
      compiler.close((closeError) => {
        if (runError || closeError) {
          reject(runError || closeError)
          return
        }
        if (stats?.hasErrors()) {
          reject(new Error(stats.toString({ all: false, errors: true, errorDetails: true })))
          return
        }
        resolve(stats)
      })
    })
  })
}

test('IDE-loaded logicflow app bundle contains the AMIS renderer registration', async () => {
  const outputPath = fs.mkdtempSync(path.join(os.tmpdir(), 'logicflow-ide-bundle-'))

  try {
    const config = createWebpackConfig({}, { mode: 'production' })
    config.output = {
      ...config.output,
      path: outputPath,
    }
    config.cache = false

    const stats = await runWebpack(config)

    const entry = stats.toJson({ all: false, entrypoints: true }).entrypoints.app.assets
      .find(asset => asset.name.endsWith('.js')).name
    const appBundle = fs.readFileSync(path.join(outputPath, entry), 'utf8')
    assert.match(appBundle, /logic-flow-editor/)
  } finally {
    fs.rmSync(outputPath, { recursive: true, force: true })
  }
}, 60000)
