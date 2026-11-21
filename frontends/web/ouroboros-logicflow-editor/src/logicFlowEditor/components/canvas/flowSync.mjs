import cloneDeep from 'lodash/cloneDeep.js'
import isEqual from 'lodash/isEqual.js'

export function snapshotFlowData(data) {
  if (!data) return null
  return cloneDeep(data)
}

export function splitFlowData(data = {}) {
  const { nodes = [], connections = [], nodePosition: _nodePosition, nodePositions, ...flowProps } = data
  const graphState = {
    nodes,
    connections,
  }
  if (nodePositions) {
    graphState.nodePositions = nodePositions
  }
  return {
    flowProps,
    graphState,
  }
}

export function getFlowProps(data = {}) {
  return splitFlowData(data).flowProps
}

export function getFlowSyncAction(renderedData, incomingData) {
  if (!incomingData) return 'noop'
  if (!renderedData) return 'reload'
  if (isEqual(renderedData, incomingData)) return 'noop'

  const renderedState = splitFlowData(renderedData)
  const incomingState = splitFlowData(incomingData)

  if (isEqual(renderedState.graphState, incomingState.graphState)) {
    return 'update-props'
  }

  return 'reload'
}
