export function normalizeNodeOutputPorts(nodeDefine = {}, nodeData = {}) {
  const outputSource = nodeData || nodeDefine
  const outputPorts = Array.isArray(outputSource?.outputPorts) ? outputSource.outputPorts : []
  return outputPorts.map((port) => ({ ...port }))
}
