import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildEditorWrapData,
  collectNodeDefineSvgs,
} from '../src/logicFlowEditor/state/editorStateHelpers.mjs'

test('collectNodeDefineSvgs merges svg maps from all node defines', () => {
  assert.deepEqual(
    collectNodeDefineSvgs([
      { type: 'start', svgs: { start: '<svg />' } },
      { type: 'action', svgs: { action: '<svg />' } },
      { type: 'noop' },
    ]),
    {
      start: '<svg />',
      action: '<svg />',
    }
  )
})

test('buildEditorWrapData combines derived editor data, rootData, superProps and domain data', () => {
  const flowModel = { name: '主流程' }
  const result = buildEditorWrapData({
    createObjectFn(superData, data) {
      return {
        ...superData,
        ...data,
      }
    },
    entryNodes: [{ label: '入口', value: 'start' }],
    exitNodes: [{ label: '出口', value: 'done' }],
    errorEntryNodes: [{ label: '异常入口', value: 'error' }],
    nodeTypes: [{ label: '动作', value: 'action' }],
    isReadOnly: true,
    flowModel,
    rootData: { appName: 'demo-app' },
    data: { name: '节点A' },
    superProps: { __validate_unique_name: () => true },
  })

  assert.equal(result.name, '节点A')
  assert.equal(result.appName, 'demo-app')
  assert.equal(result.__IS_READONLY, true)
  assert.equal(result.__FLOW_MODEL, flowModel)
  assert.deepEqual(result.__ENTRY_NODES, [{ label: '入口', value: 'start' }])
  assert.equal(typeof result.__validate_unique_name, 'function')
})
