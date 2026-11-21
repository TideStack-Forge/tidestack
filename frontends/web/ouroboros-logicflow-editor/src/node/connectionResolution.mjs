export function resolveConnectionReference(edgeData = {}, nodePortsMap = {}) {
  const sourceMap = nodePortsMap[edgeData.sourceNode]
  if (!sourceMap) {
    return { isValid: false, reason: 'missing-source-node' }
  }

  const targetMap = nodePortsMap[edgeData.targetNode]
  if (!targetMap) {
    return { isValid: false, reason: 'missing-target-node' }
  }

  const sourcePortId = sourceMap.outputPorts?.[edgeData.sourceOutputPort || 0]?.__id
  if (sourcePortId == null) {
    return { isValid: false, reason: 'missing-source-port' }
  }

  const targetPortId = targetMap.inputPorts?.[edgeData.targetInputPort || 0]?.__id
  if (targetPortId == null) {
    return { isValid: false, reason: 'missing-target-port' }
  }

  return {
    isValid: true,
    reason: null,
    source: {
      cell: sourceMap.nodeId,
      port: sourcePortId,
    },
    target: {
      cell: targetMap.nodeId,
      port: targetPortId,
    },
  }
}
