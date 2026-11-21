const path = require('path')
const { spawnSync } = require('child_process')

const rawArgv = process.argv.slice(2)
const args = rawArgv.filter((arg) => arg !== '--preview' && arg !== '--report')

const result = spawnSync(
  process.execPath,
  [
    require.resolve('webpack/bin/webpack.js'),
    '--config',
    path.resolve(__dirname, '../webpack.config.js'),
    '--mode',
    'production',
    ...args,
  ],
  {
    stdio: 'inherit',
    env: process.env,
  }
)

process.exit(result.status === null ? 1 : result.status)
