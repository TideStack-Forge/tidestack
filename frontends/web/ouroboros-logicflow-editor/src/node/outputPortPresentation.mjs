import { isDefaultOutputPort, isExclusiveOutputPort } from './outputPortSemantics.mjs'

function joinClassNames(classNames = []) {
  return classNames.filter((item) => item).join(' ')
}

export function buildOutputPortAttrs(
  node = {},
  port = {},
  portIndex,
  getNodeCapabilities = () => ({})
) {
  const isExclusive = isExclusiveOutputPort(node, portIndex, getNodeCapabilities)
  const isDefaultBranch = isDefaultOutputPort(node, portIndex)

  return {
    text: {
      text: port?.name || '',
      class: joinClassNames([
        'logicflow-node-port-text',
        'logicflow-node-port-out-text',
        isExclusive ? 'logicflow-node-port-out-text-exclusive' : '',
        isDefaultBranch ? 'logicflow-node-port-out-text-default' : '',
      ]),
    },
    circle: {
      class: joinClassNames([
        'logicflow-node-port',
        'logicflow-node-port-out',
        isExclusive ? 'logicflow-node-port-out-exclusive' : '',
        isDefaultBranch ? 'logicflow-node-port-out-default' : '',
      ]),
    },
  }
}
