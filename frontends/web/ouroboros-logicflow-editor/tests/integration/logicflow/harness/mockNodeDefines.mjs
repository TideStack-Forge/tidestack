function createPort(name, description = '') {
  return {
    name,
    description,
    dataType: 'any',
  }
}

export function createMockNodeDefines() {
  return [
    {
      type: 'start',
      label: '入口',
      group: '基础',
      icon: 'unknown',
      editorCapabilities: {
        canBeEntry: true,
        canBeExit: false,
        canBeErrorEntry: false,
      },
      scaffold: {
        type: 'start',
        name: '入口',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      portRules: {
        input: { ruleType: 'limit', min: 0, max: 0 },
        output: { ruleType: 'limit', min: 1, max: 1 },
      },
      properties: [],
    },
    {
      type: 'script',
      label: '脚本节点',
      group: '基础',
      icon: 'unknown',
      scaffold: {
        type: 'script',
        name: '脚本节点',
        inputPorts: [createPort('input')],
        outputPorts: [createPort('next')],
      },
      portRules: {
        input: { ruleType: 'limit', min: 1, max: 1 },
        output: { ruleType: 'limit', min: 1, max: 1 },
      },
      properties: [
        {
          type: 'input-text',
          name: 'script',
          label: '脚本',
        },
      ],
    },
    {
      type: 'return',
      label: '返回',
      group: '终止',
      icon: 'unknown',
      editorCapabilities: {
        isSuccessfulTerminal: true,
      },
      scaffold: {
        type: 'return',
        name: '返回',
        inputPorts: [createPort('input')],
        outputPorts: [],
      },
      portRules: {
        input: { ruleType: 'limit', min: 1, max: 1 },
        output: { ruleType: 'limit', min: 0, max: 0 },
      },
      properties: [],
    },
  ]
}
