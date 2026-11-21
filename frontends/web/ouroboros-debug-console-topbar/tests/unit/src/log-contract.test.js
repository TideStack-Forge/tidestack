import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import { test } from 'vitest'

const packageRoot = path.resolve(__dirname, '..', '..', '..')

test('topbar app log consumer uses shared init/live subscribe contract', () => {
  const source = fs.readFileSync(path.join(packageRoot, 'src/components/Log.jsx'), 'utf8')

  assert.match(source, /const stompTopic = `\/user\/queue\/app\/\$\{appName\}\/logs`/)
  assert.match(source, /Stomp\.subscribe\(stompTopic, this\.stompListener, \{/) 
  assert.match(source, /initialBody: \{\}/)
  assert.match(source, /\/user\/queue\/app\/\$\{appName\}\/logs/)
  assert.match(source, /actionType:\s*'dialog'/)
  assert.match(source, /dialog:\s*\{/)
  assert.match(source, /title:\s*'实时日志'/)
  assert.doesNotMatch(source, /\/app\/app\/\$\{appName\}\/logs\/subscribe/)
  assert.doesNotMatch(source, /onClick:\s*\(\)\s*=>/)
})
