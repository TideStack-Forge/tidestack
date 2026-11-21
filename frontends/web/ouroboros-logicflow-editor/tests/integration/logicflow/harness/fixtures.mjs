function createPort(name, description = '') {
  return {
    name,
    description,
    dataType: 'any',
  }
}

export function createLinearFixture() {
  return {
    name: 'logicflow-editor-integration',
    description: 'logicflow editor integration fixture',
    entryNode: '入口节点',
    exitNode: '',
    errorEntryNode: '',
    isEnabled: true,
    inputDataType: 'any',
    outputDataType: 'any',
    nodes: [
      {
        type: 'start',
        name: '入口节点',
        inputPorts: [],
        outputPorts: [createPort('next')],
        isEnabled: true,
        isAsync: false,
      },
      {
        type: 'script',
        name: '脚本节点',
        inputPorts: [createPort('input')],
        outputPorts: [createPort('next')],
        isEnabled: true,
        isAsync: false,
        script: 'return input',
      },
      {
        type: 'return',
        name: '返回节点',
        inputPorts: [createPort('input')],
        outputPorts: [],
        isEnabled: true,
        isAsync: false,
      },
    ],
    connections: [
      {
        sourceNode: '入口节点',
        sourceOutputPort: 0,
        targetNode: '脚本节点',
        targetInputPort: 0,
      },
      {
        sourceNode: '脚本节点',
        sourceOutputPort: 0,
        targetNode: '返回节点',
        targetInputPort: 0,
      },
    ],
  }
}
