import test from 'node:test'
import assert from 'node:assert/strict'

import {
  addInputPortToNode,
  confirmAndDeleteEdge,
  confirmAndDeleteNode,
  deleteInputPortsWithDialog,
} from '../src/logicFlowEditor/interactions/logicFlowInteractionController.mjs'

function createNode({
  data = {},
  nodeDefine = {},
  setDataSpy = () => {},
} = {}) {
  return {
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return data
    },
    setData(value, options) {
      data = value
      setDataSpy(value, options)
    },
  }
}

function createGraph({
  selectedCells = [],
  removeNodeSpy = () => {},
  removeEdgeSpy = () => {},
  selectSpy = () => {},
  triggerSpy = () => {},
} = {}) {
  return {
    getSelectedCells() {
      return selectedCells
    },
    removeNode(node) {
      removeNodeSpy(node)
    },
    removeEdge(edge) {
      removeEdgeSpy(edge)
    },
    select(node, options) {
      selectSpy(node, options)
    },
    trigger(eventName, payload) {
      triggerSpy(eventName, payload)
    },
  }
}

test('confirmAndDeleteNode only removes node after confirmation', async () => {
  const removed = []
  const node = createNode()
  const graph = createGraph({
    removeNodeSpy(nodeArg) {
      removed.push(nodeArg)
    },
  })

  await confirmAndDeleteNode({
    node,
    graph,
    confirmFn: async () => false,
  })
  await confirmAndDeleteNode({
    node,
    graph,
    confirmFn: async () => true,
  })

  assert.deepEqual(removed, [node])
})

test('confirmAndDeleteEdge only removes edge after confirmation', async () => {
  const removed = []
  const edge = { id: 'edge-1' }
  const graph = createGraph({
    removeEdgeSpy(edgeArg) {
      removed.push(edgeArg)
    },
  })

  await confirmAndDeleteEdge({
    edge,
    graph,
    confirmFn: async () => false,
  })
  await confirmAndDeleteEdge({
    edge,
    graph,
    confirmFn: async () => true,
  })

  assert.deepEqual(removed, [edge])
})

test('addInputPortToNode appends a default port and refreshes node selection', () => {
  const setDataCalls = []
  const selectCalls = []
  const triggerCalls = []
  const node = createNode({
    data: {
      name: '处理节点',
      inputPorts: [{ name: '原端口' }],
      isAsync: true,
    },
    nodeDefine: {
      portRules: {
        input: { ruleType: 'limit', min: 1, max: 2 },
      },
    },
    setDataSpy(value, options) {
      setDataCalls.push({ value, options })
    },
  })
  const graph = createGraph({
    selectSpy(nodeArg, options) {
      selectCalls.push({ node: nodeArg, options })
    },
    triggerSpy(eventName, payload) {
      triggerCalls.push({ eventName, payload })
    },
  })

  addInputPortToNode({
    node,
    graph,
    createDefaultPortData: () => ({ name: '新增端口' }),
  })

  assert.equal(setDataCalls.length, 1)
  assert.deepEqual(setDataCalls[0], {
    value: {
      name: '处理节点',
      inputPorts: [{ name: '原端口' }, { name: '新增端口' }],
      isAsync: true,
    },
    options: { overwrite: true },
  })
  assert.deepEqual(selectCalls, [{ node, options: { highlight: true } }])
  assert.deepEqual(triggerCalls, [{ eventName: 'node:click', payload: { node } }])
})

test('deleteInputPortsWithDialog validates and applies selected port deletions', async () => {
  const setDataCalls = []
  const selectCalls = []
  const triggerCalls = []
  const inputPorts = [
    { name: '端口A' },
    { name: '端口B' },
    { name: '端口C' },
  ]
  const node = createNode({
    data: {
      name: '节点A',
      inputPorts,
      isEnabled: true,
    },
    nodeDefine: {
      portRules: {
        input: { ruleType: 'limit', min: 1, max: 4 },
      },
    },
    setDataSpy(value, options) {
      setDataCalls.push({ value, options })
    },
  })
  const graph = createGraph({
    selectSpy(nodeArg, options) {
      selectCalls.push({ node: nodeArg, options })
    },
    triggerSpy(eventName, payload) {
      triggerCalls.push({ eventName, payload })
    },
  })

  let dialogConfig = null
  await deleteInputPortsWithDialog({
    node,
    graph,
    translate: (text, params) => text.replace(/\{\{(\w+)\}\}/g, (_, key) => params[key]),
    openDialogFn: async (config) => {
      dialogConfig = config
      return [{ deletePorts: [0, 2] }]
    },
  })

  assert.equal(dialogConfig.title, '删除端口')
  const checkboxes = dialogConfig.body.body[0]
  assert.deepEqual(
    checkboxes.options.map((item) => item.label),
    ['端口0（端口A）', '端口1（端口B）', '端口2（端口C）']
  )
  assert.equal(checkboxes.validate({}, [0, 1, 2]), '删除后端口数量不能小于1')

  assert.deepEqual(setDataCalls, [
    {
      value: {
        name: '节点A',
        inputPorts: [{ name: '端口B' }],
        isEnabled: true,
      },
      options: { overwrite: true },
    },
  ])
  assert.deepEqual(selectCalls, [{ node, options: { highlight: true } }])
  assert.deepEqual(triggerCalls, [{ eventName: 'node:click', payload: { node } }])
})
