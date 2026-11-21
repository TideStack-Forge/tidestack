export function disposeCanvasRuntime({ graph, emitNodeSelected, emitEdgeSelected }) {
  emitNodeSelected?.cancel?.()
  emitEdgeSelected?.cancel?.()
  graph?.dispose?.()
}
