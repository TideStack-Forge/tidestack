import { resolveConnectionReference } from './connectionResolution.mjs'

export function transpileConnectionsToX6Edges({
  connections = [],
  nodePortsMap = {},
  createEdgeFn = () => ({}),
  warnFn = () => {},
  collectDiagnosticFn = () => {},
}) {
  const edges = []

  connections.forEach((edgeData) => {
    if (!edgeData) return

    const resolvedConnection = resolveConnectionReference(edgeData, nodePortsMap)
    if (!resolvedConnection.isValid) {
      warnFn('跳过无效连线', {
        reason: resolvedConnection.reason,
        edgeData,
      })
      if (resolvedConnection.reason === 'missing-source-port') {
        collectDiagnosticFn({
          message: '源输出端口不存在',
          severity: 'error',
          blocking: true,
          scope: 'edge',
          fieldPath: 'sourceOutputPort',
          nodeName: edgeData?.sourceNode || null,
          sourceNode: edgeData?.sourceNode || null,
          sourceOutputPort: Number.isInteger(edgeData?.sourceOutputPort)
            ? edgeData.sourceOutputPort
            : null,
          targetNode: edgeData?.targetNode || null,
          targetInputPort: Number.isInteger(edgeData?.targetInputPort)
            ? edgeData.targetInputPort
            : null,
        })
      }
      if (resolvedConnection.reason === 'missing-target-port') {
        collectDiagnosticFn({
          message: '目标输入端口不存在',
          severity: 'error',
          blocking: true,
          scope: 'edge',
          fieldPath: 'targetInputPort',
          nodeName: edgeData?.targetNode || null,
          sourceNode: edgeData?.sourceNode || null,
          sourceOutputPort: Number.isInteger(edgeData?.sourceOutputPort)
            ? edgeData.sourceOutputPort
            : null,
          targetNode: edgeData?.targetNode || null,
          targetInputPort: Number.isInteger(edgeData?.targetInputPort)
            ? edgeData.targetInputPort
            : null,
        })
      }
      return
    }

    edges.push({
      ...createEdgeFn(),
      data: { ...edgeData },
      source: resolvedConnection.source,
      target: resolvedConnection.target,
      label: edgeData.label || null,
    })
  })

  return edges
}
