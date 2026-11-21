const fs = require('node:fs')
const path = require('node:path')
const crypto = require('node:crypto')

function editorBuild(packageRoot, { production = true, buildId, publicPath } = {}) {
  const hash = crypto.createHash('sha256')
  function include(directory, prefix) {
    if (!fs.existsSync(directory)) return
    for (const entry of fs.readdirSync(directory, { withFileTypes: true }).sort((a, b) => a.name.localeCompare(b.name))) {
      const file = path.join(directory, entry.name)
      const key = `${prefix}/${entry.name}`
      if (entry.isDirectory()) include(file, key)
      else { hash.update(key); hash.update(fs.readFileSync(file)) }
    }
  }
  include(path.join(packageRoot, 'src'), 'editor')
  include(path.join(packageRoot, '../ouroboros-sdk/src'), 'sdk')
  for (const name of ['package-lock.json', 'webpack.config.js', 'babel.config.js']) {
    const file = path.join(packageRoot, name)
    if (fs.existsSync(file)) { hash.update(name); hash.update(fs.readFileSync(file)) }
  }
  const version = buildId || hash.digest('hex').slice(0, 16)
  if (!/^[a-zA-Z0-9_-]+$/.test(version)) throw new Error('Invalid editor build ID')
  const base = publicPath || (production ? `/micro-logicflow-editor/${version}/` : '/micro-logicflow-editor/')
  if (base !== 'auto' && !base.endsWith('/')) throw new Error('Editor public path must be a directory ending in /')
  return { buildId: version, publicPath: base, entry: base }
}

module.exports = { editorBuild }
