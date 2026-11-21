import test from 'node:test'
import assert from 'node:assert/strict'

import { createEditorSelectionBridge } from '../src/logicFlowEditor/orchestration/editorSelectionBridge.mjs'

function resolvePanelConfig(configFactory) {
  if (typeof configFactory !== 'function') {
    return {
      formItems: configFactory,
      data: undefined,
      saveAction: undefined,
    }
  }
  return configFactory()
}

test('createEditorSelectionBridge cleans flow selection and forwards flow panel config', () => {
  const calls = []
  const graph = {
    cleanSelection() {
      calls.push(['cleanSelection'])
    },
  }
  const bridge = createEditorSelectionBridge({
    showPropertiesPanel(configFactory) {
      const { formItems, data, saveAction } = resolvePanelConfig(configFactory)
      calls.push(['showPropertiesPanel', formItems, data, saveAction])
    },
    getCurrentGraph() {
      return null
    },
    getValidationEntries() {
      return [{ message: '流程错误' }]
    },
    getFlowDefaultProperties: () => ['flow-defaults'],
    getNodeDefaultProperties: () => ['node-defaults'],
    getEdgeDefaultProperties: () => ['edge-defaults'],
    transpileAll: () => [],
    wrapData: (data) => data,
    buildFlowPanelConfigFn({ graph: graphArg, getValidationEntries }) {
      calls.push(['buildFlowPanelConfig', graphArg, getValidationEntries()])
      return {
        formItems: ['flow-form'],
        data: { kind: 'flow' },
        saveAction: 'flow-save',
      }
    },
    buildNodePanelConfigFn() {
      throw new Error('unexpected node config build')
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
  })

  bridge.showLogicFlowProperties(graph)

  assert.deepEqual(calls, [
    ['cleanSelection'],
    ['buildFlowPanelConfig', graph, [{ message: '流程错误' }]],
    ['showPropertiesPanel', ['flow-form'], { kind: 'flow' }, 'flow-save'],
  ])
})

test('createEditorSelectionBridge skips node panel rendering when multiple cells are selected', async () => {
  const calls = []
  const node = { id: 'node-1' }
  const graph = {
    getSelectedCells() {
      return [{ id: 'a' }, { id: 'b' }]
    },
    select(nodeArg, options) {
      calls.push(['select', nodeArg, options])
    },
  }

  const bridge = createEditorSelectionBridge({
    showPropertiesPanel() {
      calls.push(['showPropertiesPanel'])
    },
    getCurrentGraph() {
      return { id: 'current-graph' }
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties: () => [],
    getEdgeDefaultProperties: () => [],
    transpileAll: () => [],
    wrapData: (data) => data,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn() {
      calls.push(['buildNodePanelConfig'])
      return {}
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
  })

  await bridge.showNodeProperties(node, graph)

  assert.deepEqual(calls, [])
})

test('createEditorSelectionBridge highlights node and prefers current graph for node panel config', async () => {
  const calls = []
  const node = { id: 'node-1' }
  const getNodeDefaultProperties = () => ['node-defaults']
  const transpileAll = () => ['transpiled']
  const wrapData = (data) => data
  const graph = {
    getSelectedCells() {
      return []
    },
    select(nodeArg, options) {
      calls.push(['select', nodeArg, options])
    },
  }
  const currentGraph = { id: 'current-graph' }
  const validationEntries = [{ message: '节点错误', nodeName: '节点A' }]

  const bridge = createEditorSelectionBridge({
    showPropertiesPanel(configFactory) {
      const { formItems, data, saveAction } = resolvePanelConfig(configFactory)
      calls.push(['showPropertiesPanel', formItems, data, saveAction])
    },
    getCurrentGraph() {
      return currentGraph
    },
    getValidationEntries() {
      return validationEntries
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties,
    getEdgeDefaultProperties: () => [],
    transpileAll,
    wrapData,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn(config) {
      calls.push(['buildNodePanelConfig', config])
      return {
        formItems: ['node-form'],
        data: { kind: 'node' },
        saveAction: 'node-save',
      }
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
  })

  await bridge.showNodeProperties(node, graph)

  assert.deepEqual(calls[0], ['select', node, { highlight: true }])
  assert.equal(calls.filter(([name]) => name === 'buildNodePanelConfig').length, 2)
  const nodeConfigCalls = calls.filter(([name]) => name === 'buildNodePanelConfig')
  assert.equal(nodeConfigCalls[0][1].getValidationEntries, nodeConfigCalls[1][1].getValidationEntries)
  assert.deepEqual(nodeConfigCalls[0][1].getValidationEntries(), validationEntries)
  assert.deepEqual(calls.at(-1), ['showPropertiesPanel', ['node-form'], { kind: 'node' }, 'node-save'])

})

test('createEditorSelectionBridge does not reselect an already-selected node', async () => {
  const calls = []
  const node = { id: 'node-1' }
  const graph = {
    getSelectedCells() {
      return [node]
    },
    select(nodeArg, options) {
      calls.push(['select', nodeArg, options])
    },
  }

  const bridge = createEditorSelectionBridge({
    showPropertiesPanel(configFactory) {
      const { formItems, data, saveAction } = resolvePanelConfig(configFactory)
      calls.push(['showPropertiesPanel', formItems, data, saveAction])
    },
    getCurrentGraph() {
      return graph
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties: () => [],
    getEdgeDefaultProperties: () => [],
    transpileAll: () => [],
    wrapData: (data) => data,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn() {
      calls.push(['buildNodePanelConfig'])
      return {
        formItems: ['node-form'],
        data: { kind: 'node' },
        saveAction: 'node-save',
      }
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
  })

  await bridge.showNodeProperties(node, graph)

  assert.equal(calls.filter(([name]) => name === 'buildNodePanelConfig').length, 2)
  assert.deepEqual(calls.at(-1), ['showPropertiesPanel', ['node-form'], { kind: 'node' }, 'node-save'])

})

test('createEditorSelectionBridge awaits async node property-panel rendering', async () => {
  const calls = []
  let releaseShow = null
  const node = { id: 'node-1' }
  const graph = {
    getSelectedCells() {
      return []
    },
    select(nodeArg, options) {
      calls.push(['select', nodeArg, options])
    },
  }

  const bridge = createEditorSelectionBridge({
    showPropertiesPanel(configFactory) {
      const { formItems, data, saveAction } = resolvePanelConfig(configFactory)
      calls.push(['showPropertiesPanel:start', formItems, data, saveAction])
      return new Promise((resolve) => {
        releaseShow = () => {
          calls.push(['showPropertiesPanel:end'])
          resolve('panel-ready')
        }
      })
    },
    getCurrentGraph() {
      return graph
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties: () => [],
    getEdgeDefaultProperties: () => [],
    transpileAll: () => [],
    wrapData: (data) => data,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn() {
      calls.push(['buildNodePanelConfig'])
      return {
        formItems: ['node-form'],
        data: { kind: 'node' },
        saveAction: 'node-save',
      }
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
  })

  const renderPromise = bridge.showNodeProperties(node, graph)
  calls.push(['after-showNodeProperties'])

  assert.deepEqual(calls[0], ['select', node, { highlight: true }])
  assert.equal(calls.filter(([name]) => name === 'buildNodePanelConfig').length, 2)
  assert.deepEqual(calls.at(-2), ['showPropertiesPanel:start', ['node-form'], { kind: 'node' }, 'node-save'])
  assert.deepEqual(calls.at(-1), ['after-showNodeProperties'])

  releaseShow()
  assert.equal(await renderPromise, 'panel-ready')
  assert.deepEqual(calls[0], ['select', node, { highlight: true }])
  assert.equal(calls.filter(([name]) => name === 'buildNodePanelConfig').length, 2)
  assert.deepEqual(calls.at(-2), ['after-showNodeProperties'])
  assert.deepEqual(calls.at(-1), ['showPropertiesPanel:end'])
})

test('createEditorSelectionBridge highlights edge and forwards edge panel config', async () => {
  const calls = []
  const edge = { id: 'edge-1' }
  const getEdgeDefaultProperties = () => ['edge-defaults']
  const wrapData = (data) => data
  const graph = {
    getSelectedCells() {
      return []
    },
    select(edgeArg, options) {
      calls.push(['select', edgeArg, options])
    },
  }

  const bridge = createEditorSelectionBridge({
    showPropertiesPanel(configFactory) {
      const { formItems, data, saveAction } = resolvePanelConfig(configFactory)
      calls.push(['showPropertiesPanel', formItems, data, saveAction])
    },
    getCurrentGraph() {
      return null
    },
    getValidationEntries() {
      return [{ message: '边错误' }]
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties: () => [],
    getEdgeDefaultProperties,
    transpileAll: () => [],
    wrapData,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn() {
      throw new Error('unexpected node config build')
    },
    buildEdgePanelConfigFn(config) {
      calls.push(['buildEdgePanelConfig', config])
      return {
        formItems: ['edge-form'],
        data: { kind: 'edge' },
        saveAction: 'edge-save',
      }
    },
  })

  await bridge.showEdgeProperties(edge, graph)

  assert.deepEqual(calls[0], ['select', edge, { highlight: true }])
  assert.equal(calls[1][0], 'buildEdgePanelConfig')
  assert.equal(calls[1][1].edge, edge)
  assert.equal(calls[1][1].graph, graph)
  assert.equal(calls[1][1].getEdgeDefaultProperties, getEdgeDefaultProperties)
  assert.equal(calls[1][1].wrapData, wrapData)
  assert.equal(typeof calls[1][1].getValidationEntries, 'function')
  assert.deepEqual(calls[1][1].getValidationEntries(), [{ message: '边错误' }])
  assert.deepEqual(calls[2], ['showPropertiesPanel', ['edge-form'], { kind: 'edge' }, 'edge-save'])
})

test('createEditorSelectionBridge delegates validation node navigation to the navigation helper', async () => {
  const calls = []
  const currentGraph = { id: 'graph-1' }
  const bridge = createEditorSelectionBridge({
    showPropertiesPanel() {},
    getCurrentGraph() {
      return currentGraph
    },
    getFlowDefaultProperties: () => [],
    getNodeDefaultProperties: () => [],
    getEdgeDefaultProperties: () => [],
    transpileAll: () => [],
    wrapData: (data) => data,
    buildFlowPanelConfigFn() {
      throw new Error('unexpected flow config build')
    },
    buildNodePanelConfigFn() {
      calls.push(['buildNodePanelConfig'])
      return {}
    },
    buildEdgePanelConfigFn() {
      throw new Error('unexpected edge config build')
    },
    navigateToValidationNodeFn(args) {
      calls.push(['navigateToValidationNodeFn', args])
      return Promise.resolve({ ok: true })
    },
  })

  const result = await bridge.navigateToValidationNode('target-node')

  assert.deepEqual(result, { ok: true })
  assert.equal(calls.length, 1)
  assert.equal(calls[0][0], 'navigateToValidationNodeFn')
  assert.equal(calls[0][1].nodeName, 'target-node')
  assert.equal(calls[0][1].graph, currentGraph)
  assert.equal(typeof calls[0][1].showNodeProperties, 'function')
})
