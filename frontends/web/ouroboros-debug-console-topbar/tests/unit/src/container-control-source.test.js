const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const packageRoot = path.resolve(__dirname, '..', '..', '..')

test('container control buttons defer rendering until profile is available and rerender on profile changes', () => {
  const source = fs.readFileSync(path.join(packageRoot, 'src/components/ContainerControlButtons.jsx'), 'utf8')
  assert.match(source, /componentDidUpdate\(prevProps\)/)
  assert.match(source, /prevProps\.profile !== this\.props\.profile/)
  assert.match(source, /if \(!this\.props\.profile\)/)
  assert.ok(source.includes('this.renderHandle?.destroy?.()'))
})
