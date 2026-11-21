import { expect, test } from 'vitest'

import { createLogicflowEditorHarness } from './harness/createLogicflowEditorHarness.mjs'

function createPort(name, description = '') {
  return {
    name,
    description,
    dataType: 'any',
  }
}

function createCallFlowNodeDefines() {
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
      type: 'callFlow',
      label: '调用流程',
      group: '功能节点',
      icon: 'unknown',
      scaffold: {
        type: 'callFlow',
        name: '调用流程',
        inputPorts: [createPort('input')],
        outputPorts: [createPort('output')],
        flowName: '',
      },
      portRules: {
        input: { ruleType: 'limit', min: 1, max: 1 },
        output: { ruleType: 'limit', min: 1, max: 1 },
      },
      properties: [
        {
          type: 'flow-select',
          name: 'flowName',
          label: '流程名称',
          required: true,
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

function createNodeFieldValidationSnapshotFixture() {
  return {
    validationState: 'INVALID',
    validationErrors: ['流程调用节点不能引用当前流程自身: logicflow-call-flow-validation'],
    validationSummary: '流程调用节点不能引用当前流程自身: logicflow-call-flow-validation',
    validationDiagnostics: [
      {
        diagnosticKind: 'NODE_CONFIG_INVALID',
        message: '流程调用节点不能引用当前流程自身: logicflow-call-flow-validation',
        nodeNames: ['调用子流程'],
        scope: 'node',
        fieldPath: 'flowName',
      },
    ],
  }
}

function createCallFlowFixture() {
  return {
    name: 'logicflow-call-flow-validation',
    description: 'logicflow call flow validation fixture',
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
        outputPorts: [{ name: 'next', description: '', dataType: 'any' }],
        isEnabled: true,
        isAsync: false,
      },
      {
        type: 'callFlow',
        name: '调用子流程',
        inputPorts: [{ name: 'input', description: '', dataType: 'any' }],
        outputPorts: [{ name: 'output', description: '', dataType: 'any' }],
        isEnabled: true,
        isAsync: false,
        flowName: 'logicflow-call-flow-validation',
      },
      {
        type: 'return',
        name: '返回节点',
        inputPorts: [{ name: 'input', description: '', dataType: 'any' }],
        outputPorts: [],
        isEnabled: true,
        isAsync: false,
      },
    ],
    connections: [
      {
        sourceNode: '入口节点',
        sourceOutputPort: 0,
        targetNode: '调用子流程',
        targetInputPort: 0,
      },
      {
        sourceNode: '调用子流程',
        sourceOutputPort: 0,
        targetNode: '返回节点',
        targetInputPort: 0,
      },
    ],
  }
}

test('node field diagnostics from external validationSnapshot surface on the matching property field', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createCallFlowFixture(),
    initialValidationSnapshot: createNodeFieldValidationSnapshotFixture(),
    nodeDefines: createCallFlowNodeDefines(),
  }).mount()

  try {
    await harness.openNodeProperties('调用子流程')
    await harness.waitFor(() => harness.readRenderedFormState().text().includes('流程名称'))
    await harness.waitFor(() =>
      harness
        .readRenderedFormState()
        .text()
        .includes('流程调用节点不能引用当前流程自身: logicflow-call-flow-validation')
    )

    const formText = harness.readRenderedFormState().text()
    expect(formText).toContain('流程名称')
    expect(formText).toContain('流程调用节点不能引用当前流程自身: logicflow-call-flow-validation')
  } finally {
    await harness.unmount()
  }
}, 15000)
