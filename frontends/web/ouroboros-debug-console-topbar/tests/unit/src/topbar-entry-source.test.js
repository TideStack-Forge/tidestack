import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { test } from 'vitest'

const packageRoot = path.resolve(__dirname, '..', '..', '..')

test('topbar entry keeps runtime wiring under src while tests stay under tests/unit', () => {
  const entrySource = fs.readFileSync(path.join(packageRoot, 'src/main.js'), 'utf8')

  assert.match(entrySource, /initLayoutComponent/, 'main entry should bootstrap via ouroboros-sdk')
  assert.match(entrySource, /TopBar/, 'main entry should wire the TopBar component')
  assert.match(entrySource, /createRoot/, 'main entry should mount through react-dom')
  assert.match(entrySource, /nextContainer\.id = 'custom-topbar-container'/, 'main entry should create a host mount container when qiankun has not injected one yet')
  assert.match(entrySource, /if \(!mountContainer\) \{\s+return root/m, 'main entry should guard missing mount nodes during updates')
  assert.doesNotMatch(entrySource, /DEV_DIAGNOSTIC_PORTS|logTopbarDiagnostic|console\.info\(\s*`\[TopBar\]\[diag\]/, 'main entry should not keep dev-only diagnostics in delivery code')
  assert.doesNotMatch(entrySource, /root\?\.unmount\(\)\s*\n\s*root = createRoot/, 'main entry should not recreate the React root on every update')
})

test('close app button restores the non-dev topbar state before closing tabs', () => {
  const source = fs.readFileSync(path.join(packageRoot, 'src/components/CloseAppButton.jsx'), 'utf8')

  assert.match(source, /eventName: 'updateAppName'/)
  assert.match(source, /eventName: 'updateMenu'/)
  assert.match(source, /replayStage: 'SYS_INIT_USER'/)
  assert.match(source, /replayScope: 'SESSION'/)
  assert.match(source, /actionType: 'closeOtherTabs'/)
  assert.match(source, /actionType: 'closeCurrentTab'/)
})
