export const DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE = Object.freeze({
  gridVisible: true,
  gridSnappingEnabled: true,
  nodeAlignmentEnabled: true,
})

const LOGICFLOW_CANVAS_ASSIST_BRIDGE_KEY = '__logicflowCanvasAssistBridge'
const LOGICFLOW_NODE_DRAG_PATCH_FLAG = '__logicflowCanvasNodeDragPatchApplied'
const LOGICFLOW_DND_DROP_PATCH_FLAG = '__logicflowCanvasDndDropPatchApplied'
const LOGICFLOW_NODE_VIEW_DRAG_PATCH_FLAG = '__logicflowCanvasNodeViewDragPatchApplied'

function resolveBoolean(value, fallback) {
  if (value == null) {
    return fallback
  }

  return Boolean(value)
}

function applyGridVisibility(graph, enabled) {
  if (enabled) {
    graph?.showGrid?.()
    return
  }

  graph?.hideGrid?.()
}

function applyNodeAlignment(graph, enabled) {
  if (enabled) {
    graph?.enableSnapline?.()
    return
  }

  graph?.disableSnapline?.()
  graph?.hideSnapline?.()
}

function getBridge(graph) {
  return graph?.[LOGICFLOW_CANVAS_ASSIST_BRIDGE_KEY] || null
}

function isNodeDraggingAction(graphView, evt) {
  const data = graphView?.getEventData?.(evt)
  return data?.action === 'move' && data?.currentView
}

function withRawLocalPointOverride(graph, execute) {
  const originalSnapToGrid = graph?.snapToGrid?.bind(graph)
  if (typeof originalSnapToGrid !== 'function') {
    return execute()
  }

  graph.snapToGrid = function snapToRawLocalPoint(x, y) {
    if (y == null && x && typeof x === 'object') {
      return graph.clientToLocal(x)
    }
    return graph.clientToLocal(x, y)
  }

  try {
    return execute()
  } finally {
    graph.snapToGrid = originalSnapToGrid
  }
}

function patchNodeViewForGridSnapping(nodeView, bridge) {
  if (!nodeView || nodeView[LOGICFLOW_NODE_VIEW_DRAG_PATCH_FLAG]) {
    return null
  }

  const originalDragNode = nodeView.dragNode?.bind(nodeView)
  if (typeof originalDragNode !== 'function') {
    return null
  }

  nodeView.dragNode = function dragNodeWithLogicFlowCanvasAssist(e, x, y) {
    if (bridge.isGridSnappingEnabled()) {
      return originalDragNode(e, x, y)
    }

    const node = this.cell
    const graph = this.graph
    const data = this.getEventData(e)
    const offset = data.offset
    const restrict = data.restrict

    if (!data.moving) {
      data.moving = true
      this.addClass('node-moving')
      this.notifyNodeMove('node:move', e, x, y, this.cell)
    }

    this.autoScrollGraph(e.clientX, e.clientY)

    node.setPosition(x + offset.x, y + offset.y, {
      restrict,
      deep: true,
      ui: true,
    })

    if (graph.options.embedding.enabled) {
      if (!data.embedding) {
        this.prepareEmbedding(e)
        data.embedding = true
      }
      this.processEmbedding(e, data)
    }
  }

  nodeView[LOGICFLOW_NODE_VIEW_DRAG_PATCH_FLAG] = {
    restore() {
      nodeView.dragNode = originalDragNode
      delete nodeView[LOGICFLOW_NODE_VIEW_DRAG_PATCH_FLAG]
    },
  }

  return nodeView[LOGICFLOW_NODE_VIEW_DRAG_PATCH_FLAG]
}

function patchGraphViewForNodeDragging(graph, bridge) {
  const graphView = graph?.view
  if (!graphView || graphView[LOGICFLOW_NODE_DRAG_PATCH_FLAG]) {
    return null
  }

  const originalOnMouseMove = graphView.onMouseMove?.bind(graphView)
  const originalOnMouseUp = graphView.onMouseUp?.bind(graphView)

  if (typeof originalOnMouseMove !== 'function' || typeof originalOnMouseUp !== 'function') {
    return null
  }

  graphView.onMouseMove = function onMouseMoveWithLogicFlowCanvasAssist(evt) {
    if (!bridge.isGridSnappingEnabled() && isNodeDraggingAction(this, evt)) {
      return withRawLocalPointOverride(this.graph, () => originalOnMouseMove(evt))
    }

    return originalOnMouseMove(evt)
  }

  graphView.onMouseUp = function onMouseUpWithLogicFlowCanvasAssist(evt) {
    if (!bridge.isGridSnappingEnabled() && isNodeDraggingAction(this, evt)) {
      return withRawLocalPointOverride(this.graph, () => originalOnMouseUp(evt))
    }

    return originalOnMouseUp(evt)
  }

  graphView[LOGICFLOW_NODE_DRAG_PATCH_FLAG] = {
    restore() {
      graphView.onMouseMove = originalOnMouseMove
      graphView.onMouseUp = originalOnMouseUp
      delete graphView[LOGICFLOW_NODE_DRAG_PATCH_FLAG]
    },
  }

  return graphView[LOGICFLOW_NODE_DRAG_PATCH_FLAG]
}

function patchDndDropForGridSnapping(dndInstance, bridge) {
  if (!dndInstance || dndInstance[LOGICFLOW_DND_DROP_PATCH_FLAG]) {
    return null
  }

  const originalDrop = dndInstance.drop?.bind(dndInstance)
  if (typeof originalDrop !== 'function') {
    return null
  }

  dndInstance.drop = function dropWithLogicFlowCanvasAssist(draggingNode, pos) {
    if (bridge.isGridSnappingEnabled()) {
      return originalDrop(draggingNode, pos)
    }

    if (!this.isInsideValidArea(pos)) {
      return null
    }

    const targetGraph = this.targetGraph
    const targetModel = targetGraph.model
    const local = targetGraph.clientToLocal(pos)
    const sourceNode = this.sourceNode
    const droppingNode = this.options.getDropNode(draggingNode, {
      sourceNode,
      draggingNode,
      targetGraph: this.targetGraph,
      draggingGraph: this.draggingGraph,
    })
    const bbox = droppingNode.getBBox()
    local.x += bbox.x - bbox.width / 2
    local.y += bbox.y - bbox.height / 2

    droppingNode.position(local.x, local.y)
    droppingNode.removeZIndex()

    const validateNode = this.options.validateNode
    const ret = validateNode
      ? validateNode(droppingNode, {
          sourceNode,
          draggingNode,
          droppingNode,
          targetGraph,
          draggingGraph: this.draggingGraph,
        })
      : true

    if (typeof ret === 'boolean') {
      if (ret) {
        targetModel.addCell(droppingNode, { stencil: this.cid })
        return droppingNode
      }
      return null
    }

    return Promise.resolve(ret).then((valid) => {
      if (valid) {
        targetModel.addCell(droppingNode, { stencil: this.cid })
        return droppingNode
      }
      return null
    })
  }

  dndInstance[LOGICFLOW_DND_DROP_PATCH_FLAG] = {
    restore() {
      dndInstance.drop = originalDrop
      delete dndInstance[LOGICFLOW_DND_DROP_PATCH_FLAG]
    },
  }

  return dndInstance[LOGICFLOW_DND_DROP_PATCH_FLAG]
}

export function normalizeLogicFlowCanvasAssistState(state = {}) {
  return {
    gridVisible: resolveBoolean(
      state.gridVisible,
      DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE.gridVisible
    ),
    gridSnappingEnabled: resolveBoolean(
      state.gridSnappingEnabled,
      DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE.gridSnappingEnabled
    ),
    nodeAlignmentEnabled: resolveBoolean(
      state.nodeAlignmentEnabled,
      DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE.nodeAlignmentEnabled
    ),
  }
}

export function installLogicFlowCanvasAssistBridge(graph, { initialState } = {}) {
  if (!graph) {
    return null
  }

  const existingBridge = getBridge(graph)
  if (existingBridge) {
    if (initialState) {
      existingBridge.applyState(initialState)
    }
    return existingBridge
  }

  let state = normalizeLogicFlowCanvasAssistState(initialState)
  const restorers = []

  const bridge = {
    applyState(nextState = {}) {
      state = normalizeLogicFlowCanvasAssistState({
        ...state,
        ...nextState,
      })

      applyGridVisibility(graph, state.gridVisible)
      applyNodeAlignment(graph, state.nodeAlignmentEnabled)

      return bridge.readState()
    },
    attachDnd(dndInstance) {
      const dndPatch = patchDndDropForGridSnapping(dndInstance, bridge)
      if (dndPatch) {
        restorers.push(dndPatch.restore)
      }
    },
    isGridSnappingEnabled() {
      return state.gridSnappingEnabled
    },
    readState() {
      return {
        ...state,
      }
    },
    restore() {
      while (restorers.length) {
        const restore = restorers.pop()
        restore?.()
      }
      delete graph[LOGICFLOW_CANVAS_ASSIST_BRIDGE_KEY]
    },
  }

  const graphViewPatch = patchGraphViewForNodeDragging(graph, bridge)
  if (graphViewPatch) {
    restorers.push(graphViewPatch.restore)
  }

  const handleNodeMouseDown = ({ view }) => {
    const targetView = view?.getDelegatedView?.() || view
    const nodeViewPatch = patchNodeViewForGridSnapping(targetView, bridge)
    if (nodeViewPatch) {
      restorers.push(nodeViewPatch.restore)
    }
  }
  graph.on?.('node:mousedown', handleNodeMouseDown)
  restorers.push(() => {
    graph.off?.('node:mousedown', handleNodeMouseDown)
  })

  graph[LOGICFLOW_CANVAS_ASSIST_BRIDGE_KEY] = bridge
  bridge.applyState(state)
  return bridge
}

export function readLogicFlowCanvasAssistState(graph) {
  return (
    getBridge(graph)?.readState?.() || {
      ...DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
    }
  )
}
