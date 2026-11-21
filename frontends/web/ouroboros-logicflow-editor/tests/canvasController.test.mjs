import test from 'node:test'
import assert from 'node:assert/strict'

import { createLogicFlowCanvasController } from '../src/logicFlowEditor/canvas/logicFlowCanvasController.mjs'

function createDndHarness() {
  const instances = []

  class FakeDnd {
    constructor(options) {
      this.options = options
      this.disposeCalls = 0
      this.startCalls = []
      this.onDragEndCalls = []
      this.targetGraph = options.target
      this.draggingGraph = { id: 'dragging-graph' }
      this.cid = 'fake-dnd'
      instances.push(this)
    }

    dispose() {
      this.disposeCalls++
    }

    start(nodeInstance, event) {
      this.startCalls.push({
        nodeInstance,
        event,
      })
    }

    onDragEnd(event) {
      this.onDragEndCalls.push({
        event,
      })
    }

    drop() {
      return null
    }
  }

  return { FakeDnd, instances }
}

function createGraphHarness() {
  const calls = []
  const listeners = new Map()
  let gridSize = 10
  let snapToGridCalls = 0
  let clientToLocalCalls = 0
  const eventData = new WeakMap()
  const nodeSetPositionCalls = []
  const nodeInstance = {
    setPosition(x, y, options) {
      nodeSetPositionCalls.push({ x, y, options })
    },
  }
  const currentView = {
    cell: nodeInstance,
    graph: null,
    addClass() {},
    autoScrollGraph() {},
    notifyNodeMove() {},
    getEventData(event) {
      return eventData.get(event)
    },
    prepareEmbedding() {},
    processEmbedding() {},
  }

  const graph = {
    options: {
      grid: {
        size: 10,
      },
      moveThreshold: 0,
      embedding: {
        enabled: false,
      },
    },
    view: {
      graph: null,
      options: {
        moveThreshold: 0,
      },
      normalizeEvent(event) {
        return event
      },
      getEventData(event) {
        return eventData.get(event)
      },
      setEventData(event, data) {
        eventData.set(event, data)
      },
      delegateEvents() {
        calls.push(['delegateEvents'])
      },
      undelegateDocumentEvents() {
        calls.push(['undelegateDocumentEvents'])
      },
      onClick() {
        calls.push(['onClick'])
      },
      onMouseMove(event) {
        const data = eventData.get(event)
        data.mouseMovedCount = (data.mouseMovedCount || 0) + 1
        const local = graph.snapToGrid(event.clientX, event.clientY)
        data.currentView.onMouseMove(event, local.x, local.y)
      },
      onMouseUp(event) {
        const local = graph.snapToGrid(event.clientX, event.clientY)
        const data = eventData.get(event)
        data.currentView.onMouseUp?.(event, local.x, local.y)
        if (!event.isPropagationStopped()) {
          this.onClick(event)
        }
        event.stopImmediatePropagation?.()
        this.delegateEvents()
      },
    },
    getGridSize() {
      return gridSize
    },
    setGridSize(nextGridSize) {
      gridSize = nextGridSize
      calls.push(['setGridSize', nextGridSize])
    },
    showGrid() {
      calls.push(['showGrid'])
    },
    hideGrid() {
      calls.push(['hideGrid'])
    },
    enableSnapline() {
      calls.push(['enableSnapline'])
    },
    disableSnapline() {
      calls.push(['disableSnapline'])
    },
    hideSnapline() {
      calls.push(['hideSnapline'])
    },
    snapToGrid(x, y) {
      snapToGridCalls += 1
      return {
        x: Math.round(x / gridSize) * gridSize,
        y: Math.round(y / gridSize) * gridSize,
      }
    },
    clientToLocal(x, y) {
      clientToLocalCalls += 1
      if (typeof x === 'object') {
        return { x: x.x, y: x.y }
      }
      return { x, y }
    },
    on(name, handler) {
      const handlers = listeners.get(name) || []
      handlers.push(handler)
      listeners.set(name, handlers)
    },
    off(name, handler) {
      const handlers = listeners.get(name) || []
      const nextHandlers = handlers.filter((item) => item !== handler)
      listeners.set(name, nextHandlers)
    },
    emit(name, payload = {}) {
      ;(listeners.get(name) || []).forEach((handler) => handler(payload))
    },
    getNodes() {
      return [{ data: { name: '入口' } }]
    },
    findViewByCell() {
      return currentView
    },
    createNode(value) {
      return { id: 'graph-node', value }
    },
    model: {
      addCell(cell, options) {
        calls.push(['addCell', cell, options])
      },
    },
  }

  graph.view.graph = graph
  currentView.graph = graph
  currentView.getDelegatedView = () => currentView
  currentView.dragNode = function dragNode() {}
  currentView.onMouseMove = function onMouseMove(event, x, y) {
    this.dragNode(event, x, y)
  }
  currentView.onMouseUp = function onMouseUp() {}

  return {
    graph,
    calls,
    currentView,
    eventData,
    nodeSetPositionCalls,
    readSnapToGridCalls() {
      return snapToGridCalls
    },
    readClientToLocalCalls() {
      return clientToLocalCalls
    },
  }
}

test('createLogicFlowCanvasController forwards canvas commands to flowCanvasRef', () => {
  const calls = []
  const flowCanvasRef = {
    value: {
      zoom(value) {
        calls.push(['zoom', value])
      },
      undo() {
        calls.push(['undo'])
      },
      redo() {
        calls.push(['redo'])
      },
      autoLayout() {
        calls.push(['autoLayout'])
      },
    },
  }

  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: false },
    flowCanvasRef,
    createNodeFn: () => ({}),
    getUniqueNameFn: () => 'unused',
    DndClass: class FakeDnd {},
  })

  controller.mapZoom('+')
  controller.mapZoom('-')
  controller.undo()
  controller.redo()
  controller.autoLayout()

  assert.deepEqual(calls, [
    ['zoom', 0.1],
    ['zoom', -0.1],
    ['undo'],
    ['redo'],
    ['autoLayout'],
  ])
})

test('createLogicFlowCanvasController skips DnD init and drag when readonly', () => {
  const { FakeDnd, instances } = createDndHarness()
  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: true },
    flowCanvasRef: { value: {} },
    createNodeFn: () => ({ created: true }),
    getUniqueNameFn: () => 'unused',
    DndClass: FakeDnd,
  })

  const graph = {
    createNode() {
      return { id: 'graph-node' }
    },
  }

  assert.equal(controller.initDnd(graph), false)
  assert.equal(controller.startDrag('event', { scaffold: { name: '节点' } }), false)
  assert.equal(controller.getCurrentGraph(), null)
  assert.equal(instances.length, 0)
})

test('createLogicFlowCanvasController disposes previous DnD and preserves drop-node naming semantics', () => {
  const { FakeDnd, instances } = createDndHarness()
  const createNodeCalls = []
  const getUniqueNameCalls = []

  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: false },
    flowCanvasRef: { value: {} },
    createNodeFn(nodeDefine, data) {
      createNodeCalls.push({ nodeDefine, data })
      return { nodeDefine, data, marker: 'x6-node' }
    },
    getUniqueNameFn(prefix, names, alwaysAddIndex) {
      getUniqueNameCalls.push({ prefix, names, alwaysAddIndex })
      return prefix + '-1'
    },
    DndClass: FakeDnd,
  })

  const graph1 = {
    getNodes() {
      return [{ data: { name: '入口' } }]
    },
    createNode(value) {
      return { wrapped: value }
    },
  }
  const graph2 = {
    getNodes() {
      return [{ data: { name: '入口' } }, { data: { name: '处理节点' } }]
    },
    createNode(value) {
      return { wrapped: value }
    },
  }

  controller.initDnd(graph1)
  controller.initDnd(graph2)

  assert.equal(instances.length, 2)
  assert.equal(instances[0].disposeCalls, 1)
  assert.equal(controller.getCurrentGraph(), graph2)

  const dropped = instances[1].options.getDropNode({
    getData() {
      return { name: '处理节点', isAsync: true }
    },
    attr(path) {
      if (path === 'meta/nodeDefine') return { type: 'action' }
      return null
    },
  })

  assert.deepEqual(getUniqueNameCalls, [
    {
      prefix: '处理节点',
      names: ['入口', '处理节点'],
      alwaysAddIndex: false,
    },
  ])
  assert.deepEqual(createNodeCalls, [
    {
      nodeDefine: { type: 'action' },
      data: { name: '处理节点-1', isAsync: true },
    },
  ])
  assert.deepEqual(dropped, {
    wrapped: {
      nodeDefine: { type: 'action' },
      data: { name: '处理节点-1', isAsync: true },
      marker: 'x6-node',
    },
  })
})

test('createLogicFlowCanvasController applies canvas assist state without mutating global grid size', () => {
  const { FakeDnd, instances } = createDndHarness()
  const { graph, calls, currentView, eventData, nodeSetPositionCalls, readSnapToGridCalls, readClientToLocalCalls } =
    createGraphHarness()

  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: false },
    flowCanvasRef: { value: {} },
    createNodeFn: () => ({ marker: 'x6-node' }),
    getUniqueNameFn: () => 'unused',
    DndClass: FakeDnd,
  })

  controller.initDnd(graph)
  calls.length = 0

  controller.applyCanvasAssistState({
    gridVisible: false,
    gridSnappingEnabled: false,
    nodeAlignmentEnabled: false,
  })

  assert.deepEqual(calls, [
    ['hideGrid'],
    ['disableSnapline'],
    ['hideSnapline'],
  ])
  assert.equal(graph.getGridSize(), 10)

  graph.emit('node:mousedown', { view: currentView })

  const moveEvent = {
    clientX: 17,
    clientY: 23,
  }
  eventData.set(moveEvent, {
    action: 'move',
    currentView,
    mouseMovedCount: 0,
    startPosition: { x: 0, y: 0 },
    offset: { x: 2.5, y: -1.5 },
    restrict: null,
    moving: false,
  })
  graph.view.onMouseMove(moveEvent)

  assert.deepEqual(nodeSetPositionCalls, [
    {
      x: 19.5,
      y: 21.5,
      options: {
        restrict: null,
        deep: true,
        ui: true,
      },
    },
  ])
  assert.equal(readClientToLocalCalls(), 1)
  assert.equal(readSnapToGridCalls(), 0)

  const upEvent = {
    clientX: 17,
    clientY: 23,
    isPropagationStopped() {
      return false
    },
    stopImmediatePropagation() {},
  }
  eventData.set(upEvent, {
    action: 'move',
    currentView,
  })
  graph.view.onMouseUp(upEvent)
  assert.equal(graph.getGridSize(), 10)

  controller.startDrag('drag-event', {
    type: 'action',
    scaffold: { name: '处理节点' },
  })
  const dropNode = {
    getBBox() {
      return {
        x: 4,
        y: 6,
        width: 12,
        height: 10,
      }
    },
    position(x, y) {
      calls.push(['dropPosition', x, y])
    },
    removeZIndex() {
      calls.push(['dropRemoveZIndex'])
    },
  }
  instances[0].sourceNode = { id: 'source-node' }
  instances[0].targetGraph = graph
  instances[0].isInsideValidArea = () => true
  instances[0].options.getDropNode = () => dropNode
  const dropped = instances[0].drop({ id: 'dragging-node' }, { x: 15, y: 27 })

  assert.equal(dropped, dropNode)
  assert.deepEqual(
    calls.filter((entry) => entry[0] === 'dropPosition' || entry[0] === 'addCell' || entry[0] === 'dropRemoveZIndex'),
    [
      ['dropPosition', 13, 28],
      ['dropRemoveZIndex'],
      ['addCell', dropNode, { stencil: 'fake-dnd' }],
    ]
  )
  assert.equal(graph.getGridSize(), 10)
})

test('createLogicFlowCanvasController startDrag creates scaffold node through current graph and starts DnD', () => {
  const { FakeDnd, instances } = createDndHarness()
  const createNodeCalls = []
  const graphCreateCalls = []

  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: false },
    flowCanvasRef: { value: {} },
    createNodeFn(nodeDefine, data) {
      createNodeCalls.push({ nodeDefine, data })
      return { nodeDefine, data, marker: 'x6-node' }
    },
    getUniqueNameFn: () => 'unused',
    DndClass: FakeDnd,
  })

  const graph = {
    getGridSize() {
      return 10
    },
    getNodes() {
      return []
    },
    createNode(value) {
      graphCreateCalls.push(value)
      return { id: 'graph-node', value }
    },
  }

  controller.initDnd(graph)
  const result = controller.startDrag('drag-event', {
    type: 'action',
    scaffold: { name: '处理节点', inputPorts: [] },
  })

  assert.equal(result, true)
  assert.deepEqual(createNodeCalls, [
    {
      nodeDefine: {
        type: 'action',
        scaffold: { name: '处理节点', inputPorts: [] },
      },
      data: { name: '处理节点', inputPorts: [] },
    },
  ])
  assert.deepEqual(graphCreateCalls, [
    {
      nodeDefine: {
        type: 'action',
        scaffold: { name: '处理节点', inputPorts: [] },
      },
      data: { name: '处理节点', inputPorts: [] },
      marker: 'x6-node',
    },
  ])
  assert.deepEqual(instances[0].startCalls, [
    {
      nodeInstance: {
        id: 'graph-node',
        value: {
          nodeDefine: {
            type: 'action',
            scaffold: { name: '处理节点', inputPorts: [] },
          },
          data: { name: '处理节点', inputPorts: [] },
          marker: 'x6-node',
        },
      },
      event: 'drag-event',
    },
  ])
})

test('createLogicFlowCanvasController dispose clears current graph and disposes active dnd', () => {
  const { FakeDnd, instances } = createDndHarness()

  const controller = createLogicFlowCanvasController({
    isReadOnly: { value: false },
    flowCanvasRef: { value: {} },
    createNodeFn: () => ({}),
    getUniqueNameFn: () => 'unused',
    DndClass: FakeDnd,
  })

  const graph = {
    getNodes() {
      return []
    },
    createNode() {
      return { id: 'graph-node' }
    },
  }

  controller.initDnd(graph)
  controller.dispose()

  assert.equal(instances.length, 1)
  assert.equal(instances[0].disposeCalls, 1)
  assert.equal(controller.getCurrentGraph(), null)
  assert.equal(
    controller.startDrag('drag-event', {
      type: 'action',
      scaffold: { name: '处理节点' },
    }),
    false
  )
})
