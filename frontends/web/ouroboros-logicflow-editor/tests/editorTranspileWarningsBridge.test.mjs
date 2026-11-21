import test from 'node:test'
import assert from 'node:assert/strict'

import { createEditorPanelBridge } from '../src/logicFlowEditor/orchestration/editorBridgeHelpers.mjs'

function createBridge(toastCalls) {
  return createEditorPanelBridge({
    translate: (text, params = {}) => text.replace(/\{\{(\w+)\}\}/g, (_, key) => params[key]),
    propertiesPanelRef: {
      value: {
        show() {},
        toggle() {},
      },
    },
    toastFn(message, type) {
      toastCalls.push([message, type])
    },
  })
}

test('createEditorPanelBridge summarizes transpile warnings as a warning toast', () => {
  const toastCalls = []
  const bridge = createBridge(toastCalls)

  bridge.showTranspileWarnings({
    warnings: [
      {
        message: '跳过无效连线',
        payload: { reason: 'missing-source-node' },
      },
      {
        message: '跳过无效连线',
        payload: { reason: 'missing-source-port' },
      },
    ],
  })

  assert.deepEqual(toastCalls, [
    ['发现并跳过 2 条无效连线：源节点不存在 1 条，源输出端口不存在 1 条', 'warning'],
  ])
})

test('createEditorPanelBridge suppresses duplicate transpile warning batches', () => {
  const toastCalls = []
  const bridge = createBridge(toastCalls)
  const event = {
    warnings: [
      {
        message: '跳过无效连线',
        payload: { reason: 'missing-source-node' },
      },
    ],
  }

  bridge.showTranspileWarnings(event)
  bridge.showTranspileWarnings(event)

  assert.deepEqual(toastCalls, [['发现并跳过 1 条无效连线：源节点不存在 1 条', 'warning']])
})
