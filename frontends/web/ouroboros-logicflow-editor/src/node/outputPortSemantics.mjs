function getOutputPorts(node = {}) {
  return Array.isArray(node?.outputPorts) ? node.outputPorts : []
}

function getExclusiveGroupId(port = {}) {
  const groupId = port?.exclusiveGroupId
  return typeof groupId === 'string' && groupId.trim() ? groupId : null
}

export function hasMetadataExclusiveOutputs(node = {}) {
  return getOutputPorts(node).some((port) => getExclusiveGroupId(port) || port?.defaultBranch === true)
}

export function getExclusiveOutputGroups(node = {}) {
  const groups = new Map()

  getOutputPorts(node).forEach((port, index) => {
    const groupId = getExclusiveGroupId(port)
    if (!groupId) return
    if (!groups.has(groupId)) {
      groups.set(groupId, [])
    }
    groups.get(groupId).push(index)
  })

  return groups
}

export function hasSelectiveOutputs(node = {}, getNodeCapabilities = () => ({})) {
  if (hasMetadataExclusiveOutputs(node)) {
    return true
  }
  return !!getNodeCapabilities(node)?.hasExclusiveOutputs
}

export function areOutputPortsMutex(
  node = {},
  leftPortIndex,
  rightPortIndex,
  getNodeCapabilities = () => ({})
) {
  if (leftPortIndex === rightPortIndex) {
    return false
  }

  const outputPorts = getOutputPorts(node)
  const leftPort = outputPorts[leftPortIndex]
  const rightPort = outputPorts[rightPortIndex]
  if (!leftPort || !rightPort) {
    return false
  }

  if (hasMetadataExclusiveOutputs(node)) {
    const leftGroupId = getExclusiveGroupId(leftPort)
    const rightGroupId = getExclusiveGroupId(rightPort)
    return !!leftGroupId && leftGroupId === rightGroupId
  }

  return !!getNodeCapabilities(node)?.hasExclusiveOutputs
}

export function isExclusiveOutputPort(
  node = {},
  portIndex,
  getNodeCapabilities = () => ({})
) {
  const outputPorts = getOutputPorts(node)
  const port = outputPorts[portIndex]
  if (!port) {
    return false
  }

  if (hasMetadataExclusiveOutputs(node)) {
    return !!getExclusiveGroupId(port)
  }

  return !!getNodeCapabilities(node)?.hasExclusiveOutputs
}

export function getMutexOutputPortPairs(node = {}, getNodeCapabilities = () => ({})) {
  const outputPorts = getOutputPorts(node)
  if (outputPorts.length < 2) {
    return []
  }

  if (hasMetadataExclusiveOutputs(node)) {
    const pairs = []
    getExclusiveOutputGroups(node).forEach((indexes) => {
      for (let i = 0; i < indexes.length; i++) {
        for (let j = i + 1; j < indexes.length; j++) {
          pairs.push([indexes[i], indexes[j]])
        }
      }
    })
    return pairs
  }

  if (!getNodeCapabilities(node)?.hasExclusiveOutputs) {
    return []
  }

  const pairs = []
  for (let i = 0; i < outputPorts.length; i++) {
    for (let j = i + 1; j < outputPorts.length; j++) {
      pairs.push([i, j])
    }
  }
  return pairs
}

export function getDefaultOutputPortIndex(node = {}) {
  const index = getOutputPorts(node).findIndex((port) => port?.defaultBranch === true)
  return index >= 0 ? index : null
}

export function isDefaultOutputPort(node = {}, portIndex) {
  return getDefaultOutputPortIndex(node) === portIndex
}

export function normalizeExclusiveOutputPorts(
  outputPorts = [],
  { exclusiveGroupId, defaultPortIndex = null } = {}
) {
  return outputPorts.map((port, index) => ({
    ...port,
    exclusiveGroupId: port?.exclusiveGroupId || exclusiveGroupId || null,
    defaultBranch:
      typeof port?.defaultBranch === 'boolean'
        ? port.defaultBranch
        : index === defaultPortIndex,
  }))
}
