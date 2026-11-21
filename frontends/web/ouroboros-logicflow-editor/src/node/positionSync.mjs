export function getStoredNodePositions(flowData = {}) {
  return flowData.nodePositions || {}
}

export function hasStoredNodePositions(flowData = {}) {
  return Object.keys(getStoredNodePositions(flowData)).length > 0
}

function hasValidPosition(position) {
  return Number.isFinite(position?.x) && Number.isFinite(position?.y)
}

export function applyStoredNodePositions(x6Data = {}, flowData = {}) {
  const nodePositions = getStoredNodePositions(flowData)
  if (!hasStoredNodePositions(flowData)) return x6Data

  return {
    ...x6Data,
    nodes: (x6Data.nodes || []).map((node) => {
      if (node?.data?.__virtual) return node
      const nodeName = node?.data?.name
      const position = nodeName ? nodePositions[nodeName] : null
      if (!hasValidPosition(position)) return node
      return {
        ...node,
        x: position.x,
        y: position.y,
      }
    }),
  }
}

export function extractNodePositions(x6Cells = []) {
  return x6Cells.reduce((acc, cell) => {
    if (cell?.shape === 'edge') return acc
    if (cell?.data?.__virtual) return acc
    const nodeName = cell?.data?.name
    const position = cell?.position
    if (!nodeName || !hasValidPosition(position)) return acc
    acc[nodeName] = {
      x: position.x,
      y: position.y,
    }
    return acc
  }, {})
}
