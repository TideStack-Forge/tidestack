export function isCanvasPortLinkingEnabled(isReadOnly = false) {
  return !isReadOnly
}

export function resolveCanvasDoubleClickForwarding({ targetType, target, graph }) {
  switch (targetType) {
    case 'node':
      return {
        eventName: 'nodeDblclick',
        args: [target, graph],
      }
    case 'edge':
      return {
        eventName: 'edgeDblclick',
        args: [target, graph],
      }
    default:
      return {
        eventName: 'blankDblclick',
        args: [graph],
      }
  }
}
