import test from 'node:test'
import assert from 'node:assert/strict'

import { createEditorInteractionActionsBridge } from '../src/logicFlowEditor/orchestration/editorInteractionActionsBridge.mjs'

test('createEditorInteractionActionsBridge delegates node deletion through injected confirm action', async () => {
  const calls = []
  const node = { id: 'node-1' }
  const graph = { id: 'graph-1' }
  const confirmFn = async () => true
  const bridge = createEditorInteractionActionsBridge({
    confirmFn,
    openDialogFn: async () => [],
    createDefaultPortData: () => ({}),
    confirmAndDeleteNodeFn: async (config) => {
      calls.push(['confirmAndDeleteNode', config])
      return 'node-deleted'
    },
    confirmAndDeleteEdgeFn: async () => {
      throw new Error('unexpected edge delete')
    },
    addInputPortToNodeFn: () => {
      throw new Error('unexpected add port')
    },
    deleteInputPortsWithDialogFn: async () => {
      throw new Error('unexpected delete ports')
    },
  })

  const result = await bridge.deleteNode(node, graph)

  assert.equal(result, 'node-deleted')
  assert.deepEqual(calls, [
    ['confirmAndDeleteNode', { node, graph, confirmFn }],
  ])
})

test('createEditorInteractionActionsBridge delegates edge deletion through injected confirm action', async () => {
  const calls = []
  const edge = { id: 'edge-1' }
  const graph = { id: 'graph-1' }
  const confirmFn = async () => true
  const bridge = createEditorInteractionActionsBridge({
    confirmFn,
    openDialogFn: async () => [],
    createDefaultPortData: () => ({}),
    confirmAndDeleteNodeFn: async () => {
      throw new Error('unexpected node delete')
    },
    confirmAndDeleteEdgeFn: async (config) => {
      calls.push(['confirmAndDeleteEdge', config])
      return 'edge-deleted'
    },
    addInputPortToNodeFn: () => {
      throw new Error('unexpected add port')
    },
    deleteInputPortsWithDialogFn: async () => {
      throw new Error('unexpected delete ports')
    },
  })

  const result = await bridge.deleteEdge(edge, graph)

  assert.equal(result, 'edge-deleted')
  assert.deepEqual(calls, [
    ['confirmAndDeleteEdge', { edge, graph, confirmFn }],
  ])
})

test('createEditorInteractionActionsBridge delegates input-port addition with injected port factory', () => {
  const calls = []
  const node = { id: 'node-1' }
  const graph = { id: 'graph-1' }
  const createDefaultPortData = () => ({ name: '新增端口' })
  const bridge = createEditorInteractionActionsBridge({
    confirmFn: async () => true,
    openDialogFn: async () => [],
    createDefaultPortData,
    confirmAndDeleteNodeFn: async () => {
      throw new Error('unexpected node delete')
    },
    confirmAndDeleteEdgeFn: async () => {
      throw new Error('unexpected edge delete')
    },
    addInputPortToNodeFn: (config) => {
      calls.push(['addInputPortToNode', config])
      return 'port-added'
    },
    deleteInputPortsWithDialogFn: async () => {
      throw new Error('unexpected delete ports')
    },
  })

  const result = bridge.addInPort(node, graph)

  assert.equal(result, 'port-added')
  assert.deepEqual(calls, [
    ['addInputPortToNode', { node, graph, createDefaultPortData }],
  ])
})

test('createEditorInteractionActionsBridge delegates input-port deletion dialog with injected dialog opener', async () => {
  const calls = []
  const node = { id: 'node-1' }
  const graph = { id: 'graph-1' }
  const openDialogFn = async () => [{ deletePorts: [0] }]
  const translate = (text) => text
  const bridge = createEditorInteractionActionsBridge({
    confirmFn: async () => true,
    openDialogFn,
    translate,
    createDefaultPortData: () => ({}),
    confirmAndDeleteNodeFn: async () => {
      throw new Error('unexpected node delete')
    },
    confirmAndDeleteEdgeFn: async () => {
      throw new Error('unexpected edge delete')
    },
    addInputPortToNodeFn: () => {
      throw new Error('unexpected add port')
    },
    deleteInputPortsWithDialogFn: async (config) => {
      calls.push(['deleteInputPortsWithDialog', config])
      return 'ports-deleted'
    },
  })

  const result = await bridge.showDeleteInputPortDialog(node, graph)

  assert.equal(result, 'ports-deleted')
  assert.deepEqual(calls, [
    ['deleteInputPortsWithDialog', { node, graph, openDialogFn, translate }],
  ])
})
