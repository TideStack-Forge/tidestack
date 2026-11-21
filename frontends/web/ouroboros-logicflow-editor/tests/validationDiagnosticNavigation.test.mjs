import test from 'node:test'
import assert from 'node:assert/strict'

import { navigateToValidationNode } from '../src/logicFlowEditor/orchestration/validationDiagnosticNavigation.mjs'

test('navigateToValidationNode finds a node by data.name, reuses graph selection flow, and recenters when available', async () => {
  const calls = []
  const node = {
    id: 'node-1',
    data: {
      name: 'target-node',
    },
  }
  const graph = {
    getNodes() {
      calls.push(['getNodes'])
      return [node]
    },
    cleanSelection() {
      calls.push(['cleanSelection'])
    },
    select(nodeArg, options) {
      calls.push(['select', nodeArg, options])
    },
    refreshLogicFlowValidationHighlights() {
      calls.push(['refreshLogicFlowValidationHighlights'])
    },
    centerCell(nodeArg) {
      calls.push(['centerCell', nodeArg])
    },
  }

  const result = await navigateToValidationNode({
    nodeName: 'target-node',
    graph,
    showNodeProperties(nodeArg, graphArg) {
      calls.push(['showNodeProperties', nodeArg, graphArg])
      return Promise.resolve()
    },
  })

  assert.equal(result.ok, true)
  assert.equal(result.node, node)
  assert.equal(result.centered, true)
  assert.deepEqual(calls, [
    ['getNodes'],
    ['cleanSelection'],
    ['select', node, { highlight: true }],
    ['refreshLogicFlowValidationHighlights'],
    ['centerCell', node],
  ])
})

test('navigateToValidationNode supports node names exposed through getData()', async () => {
  const node = {
    id: 'node-2',
    getData() {
      return { name: 'get-data-node' }
    },
  }

  const result = await navigateToValidationNode({
    nodeName: 'get-data-node',
    graph: {
      getNodes() {
        return [node]
      },
    },
    showNodeProperties(nodeArg, graphArg) {
      assert.equal(nodeArg, node)
      assert.ok(graphArg)
      return Promise.resolve()
    },
  })

  assert.equal(result.ok, true)
  assert.equal(result.node, node)
  assert.equal(result.centered, false)
})

test('navigateToValidationNode falls back to showing properties when graph selection is unavailable', async () => {
  const calls = []
  const node = {
    id: 'node-3',
    data: {
      name: 'fallback-node',
    },
  }
  const graph = {
    getNodes() {
      calls.push(['getNodes'])
      return [node]
    },
    cleanSelection() {
      calls.push(['cleanSelection'])
    },
  }

  const result = await navigateToValidationNode({
    nodeName: 'fallback-node',
    graph,
    showNodeProperties(nodeArg, graphArg) {
      calls.push(['showNodeProperties', nodeArg, graphArg])
      return Promise.resolve()
    },
  })

  assert.equal(result.ok, true)
  assert.equal(result.node, node)
  assert.deepEqual(calls, [
    ['getNodes'],
    ['cleanSelection'],
    ['showNodeProperties', node, graph],
  ])
})

test('navigateToValidationNode returns a warning-friendly result when the node cannot be found', async () => {
  let showNodePropertiesCalled = false

  const result = await navigateToValidationNode({
    nodeName: 'missing-node',
    graph: {
      getNodes() {
        return [{ data: { name: 'other-node' } }]
      },
      cleanSelection() {
        throw new Error('should not clean selection when node is missing')
      },
    },
    showNodeProperties() {
      showNodePropertiesCalled = true
      return Promise.resolve()
    },
  })

  assert.equal(result.ok, false)
  assert.equal(result.reason, 'node_not_found')
  assert.equal(result.message, '无法定位节点：节点可能已不存在（missing-node）')
  assert.equal(showNodePropertiesCalled, false)
})
