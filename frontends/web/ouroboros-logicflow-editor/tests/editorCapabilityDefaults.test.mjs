import test from 'node:test'
import assert from 'node:assert/strict'

import { defaultEditorCapabilities } from '../src/node-define/editorCapabilityDefaults.mjs'
import { buildNodeOptionsByCapability } from '../src/node-define/editorCapabilities.js'

test('defaultEditorCapabilities leaves node roles unset unless backend metadata provides them', () => {
  assert.equal(defaultEditorCapabilities.hasExclusiveOutputs, false)
  assert.equal(defaultEditorCapabilities.canBeEntry, null)
  assert.equal(defaultEditorCapabilities.canBeExit, null)
  assert.equal(defaultEditorCapabilities.canBeErrorEntry, null)
  assert.equal(defaultEditorCapabilities.isSuccessfulTerminal, false)
})

test('buildNodeOptionsByCapability keeps nodes in dropdown options even when role capabilities are explicitly false', () => {
  const nodes = [
    { name: '开始', type: 'start' },
    { name: '成功返回', type: 'return' },
    { name: '错误入口', type: 'error-hook' },
  ]
  const nodeDefines = [
    {
      type: 'start',
      editorCapabilities: {
        canBeEntry: true,
        canBeExit: false,
        canBeErrorEntry: false,
      },
    },
    {
      type: 'return',
      editorCapabilities: {
        canBeEntry: false,
        canBeExit: false,
        canBeErrorEntry: false,
      },
    },
    {
      type: 'error-hook',
      editorCapabilities: {
        canBeEntry: false,
        canBeExit: false,
        canBeErrorEntry: false,
      },
    },
  ]

  const expectedOptions = [
    { label: '开始', value: '开始' },
    { label: '成功返回', value: '成功返回' },
    { label: '错误入口', value: '错误入口' },
  ]

  assert.deepEqual(buildNodeOptionsByCapability(nodes, nodeDefines, 'canBeEntry'), expectedOptions)
  assert.deepEqual(buildNodeOptionsByCapability(nodes, nodeDefines, 'canBeExit'), expectedOptions)
  assert.deepEqual(
    buildNodeOptionsByCapability(nodes, nodeDefines, 'canBeErrorEntry'),
    expectedOptions
  )
})
