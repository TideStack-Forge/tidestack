const assert = require('node:assert/strict')
const fs = require('node:fs')
const path = require('node:path')

const packageRoot = path.resolve(__dirname, '../../..')
const source = fs.readFileSync(path.join(packageRoot, 'src/TopBar.jsx'), 'utf8')

test('TopBar derives route state from host props instead of polling global location', () => {
  assert.match(source, /currentPath: propCurrentPath/)
  assert.match(source, /const currentPath = propCurrentPath \|\| this\.getCurrentLocationKey\(\)/)
  assert.doesNotMatch(source, /setInterval/)
  assert.doesNotMatch(source, /pathCheckInterval/)
})

test('TopBar only treats app dev routes as development mode', () => {
  assert.ok(source.includes('pathname.match(/^\\/(dev|debug)\\/(.+?)\\/(.+)$/)'))
  assert.match(source, /const isDevelopment = Boolean\(developmentRouteMatch\) && !pathname\.startsWith\('\/dev\/apps'\)/)
})

test('TopBar excludes standalone debug tool routes from development mode', () => {
  assert.doesNotMatch(source, /pathname\.startsWith\('\/debug'\)/)
})

test('TopBar switches from dev controls to the plain app title without a refresh branch', () => {
  assert.ok(source.includes('!isDevelopment ? ('), 'non-dev branch should render the app title')
  assert.ok(source.includes('<div className="title" style={titleStyle}>'), 'title should stay in the non-dev branch')
  assert.ok(
    source.includes('<div className="helper-buttons" key="helper-buttons">'),
    'dev branch should keep the helper controls in the right rail'
  )
  assert.ok(
    source.includes('<CloseAppButton key="close-button" />'),
    'dev branch should still expose the close-app control'
  )
})
