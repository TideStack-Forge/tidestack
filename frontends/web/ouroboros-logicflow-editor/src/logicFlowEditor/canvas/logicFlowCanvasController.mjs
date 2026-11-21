import {
  DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
  installLogicFlowCanvasAssistBridge,
  readLogicFlowCanvasAssistState,
} from './logicFlowCanvasAssistBridge.mjs'

export function createLogicFlowCanvasController({
  isReadOnly,
  flowCanvasRef,
  createNodeFn,
  getUniqueNameFn,
  DndClass,
}) {
  let dnd = null
  let currentGraph = null
  let canvasAssistState = {
    ...DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
  }
  let canvasAssistBridge = null

  function dispose() {
    dnd?.dispose?.()
    canvasAssistBridge?.restore?.()
    dnd = null
    currentGraph = null
    canvasAssistBridge = null
  }

  function mapZoom(type) {
    if (type == '+') {
      flowCanvasRef.value.zoom(0.1)
    } else if (type == '-') {
      flowCanvasRef.value.zoom(-0.1)
    }
  }

  function undo() {
    flowCanvasRef.value.undo()
  }

  function redo() {
    flowCanvasRef.value.redo()
  }

  function autoLayout() {
    flowCanvasRef.value.autoLayout()
  }

  function fitGraph() {
    flowCanvasRef.value.fitGraph()
  }

  function centerGraph() {
    flowCanvasRef.value.centerGraph()
  }

  function initDnd(graph) {
    dnd?.dispose?.()
    dnd = null
    canvasAssistBridge?.restore?.()
    canvasAssistBridge = null
    currentGraph = null

    if (isReadOnly.value) {
      return false
    }

    currentGraph = graph
    canvasAssistBridge = installLogicFlowCanvasAssistBridge(graph, {
      initialState: canvasAssistState,
    })
    canvasAssistState = canvasAssistBridge?.readState?.() || canvasAssistState

    dnd = new DndClass({
      target: graph,
      getDropNode(node) {
        const data = node.getData()
        const nodeDefine = node.attr('meta/nodeDefine')
        const allNames = graph.getNodes().map((node) => node.data.name)
        const name = getUniqueNameFn(data.name, allNames, false)
        const newNode = createNodeFn(nodeDefine, { ...data, name })
        return graph.createNode(newNode)
      },
    })
    if (canvasAssistBridge && dnd) {
      canvasAssistBridge.attachDnd(dnd)
    }
    return dnd
  }

  function startDrag(e, nodeDefine) {
    if (isReadOnly.value) return false
    const x6node = createNodeFn(nodeDefine, { ...nodeDefine.scaffold })
    const nodeInstance = currentGraph?.createNode(x6node)
    if (!nodeInstance) return false
    dnd?.start(nodeInstance, e)
    return true
  }

  function getCurrentGraph() {
    return currentGraph
  }

  function applyCanvasAssistState(nextState = {}) {
    canvasAssistState = {
      ...canvasAssistState,
      ...nextState,
    }

    if (currentGraph) {
      canvasAssistBridge =
        canvasAssistBridge ||
        installLogicFlowCanvasAssistBridge(currentGraph, {
          initialState: canvasAssistState,
        })
      canvasAssistState = canvasAssistBridge?.applyState?.(canvasAssistState) || canvasAssistState
    }

    return {
      ...canvasAssistState,
    }
  }

  function readCanvasAssistState() {
    if (!currentGraph) {
      return {
        ...canvasAssistState,
      }
    }

    return readLogicFlowCanvasAssistState(currentGraph)
  }

  return {
    mapZoom,
    undo,
    redo,
    autoLayout,
    fitGraph,
    centerGraph,
    initDnd,
    startDrag,
    getCurrentGraph,
    applyCanvasAssistState,
    readCanvasAssistState,
    dispose,
  }
}
