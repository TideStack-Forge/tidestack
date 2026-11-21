import test from 'node:test'
import assert from 'node:assert/strict'

import { transpileToX6Data } from '../src/node/transpile.js'
import { LogicFlowPlugin } from '../src/node/LogicFlowPlugin.js'

function createPort(name) {
  return {
    name,
    description: '',
    dataType: 'any',
  }
}

function createNodeDefines() {
  return [
    {
      type: 'start',
      title: '开始',
    },
    {
      type: 'action',
      title: '处理',
    },
  ]
}

function createFlowWithInvalidConnection() {
  return {
    name: 'demo-flow',
    description: '',
    entryNode: 'start',
    exitNode: '',
    errorEntryNode: '',
    nodes: [
      {
        type: 'start',
        name: 'start',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'action',
        name: 'action',
        inputPorts: [createPort('input')],
        outputPorts: [],
      },
    ],
    connections: [
      {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'ok',
      },
      {
        sourceNode: 'missing',
        sourceOutputPort: 0,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'invalid',
      },
    ],
  }
}

function createFlowWithValidationWarning() {
  return {
    name: 'demo-flow',
    description: '',
    entryNode: 'start',
    exitNode: '',
    errorEntryNode: '',
    nodes: [
      {
        type: 'start',
        name: 'start',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'action',
        name: 'other',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'action',
        name: 'target',
        inputPorts: [createPort('input')],
        outputPorts: [],
      },
    ],
    connections: [
      {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'target',
        targetInputPort: 0,
      },
      {
        sourceNode: 'other',
        sourceOutputPort: 0,
        targetNode: 'target',
        targetInputPort: 0,
      },
    ],
  }
}

function createFlowWithUnconnectedInput() {
  return {
    name: 'demo-flow',
    description: '',
    entryNode: 'start',
    exitNode: '',
    errorEntryNode: '',
    nodes: [
      {
        type: 'start',
        name: 'start',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'action',
        name: 'target',
        inputPorts: [createPort('left'), createPort('right')],
        outputPorts: [],
      },
    ],
    connections: [
      {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'target',
        targetInputPort: 0,
      },
    ],
  }
}

function createFlowWithPhantomRoles({ directReturn = false } = {}) {
  return {
    name: 'demo-flow',
    description: '',
    entryNode: 'start',
    exitNode: 'end',
    errorEntryNode: 'error-start',
    nodes: [
      {
        type: 'start',
        name: 'start',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'action',
        name: 'work',
        inputPorts: [createPort('input')],
        outputPorts: [createPort('next')],
      },
      {
        type: 'errorStart',
        name: 'error-start',
        inputPorts: [],
        outputPorts: [createPort('next')],
      },
      {
        type: 'exit',
        name: 'end',
        inputPorts: [createPort('input')],
        outputPorts: [createPort('next')],
      },
      {
        type: 'return',
        name: 'done',
        inputPorts: [createPort('input')],
        outputPorts: [],
      },
    ],
    connections: directReturn
      ? [
          {
            sourceNode: 'end',
            sourceOutputPort: 0,
            targetNode: 'done',
            targetInputPort: 0,
          },
        ]
      : [],
  }
}

test('transpileToX6Data normalizes backend auto output rules before invocation', () => {
  const x6Data = transpileToX6Data(
    {
      name: 'demo-flow',
      description: '',
      entryNode: 'action',
      exitNode: '',
      errorEntryNode: '',
      nodes: [
        {
          type: 'action',
          name: 'action',
          inputPorts: [],
          outputPorts: [],
        },
      ],
      connections: [],
    },
    [
      {
        type: 'action',
        title: '处理',
        portRules: {
          output: {
            ruleType: 'auto',
            autoFunction: 'return [{ name: "next" }]',
          },
        },
      },
    ]
  )

  const actionNode = x6Data.nodes.find((node) => node.data?.type === 'action')

  assert.ok(actionNode)
  assert.equal(actionNode.data.outputPorts.length, 1)
  assert.equal(actionNode.ports.items.filter((item) => item.group === 'out').length, 1)
})

test('transpileToX6Data falls back when an auto output rule cannot be constructed', () => {
  const originalFunction = globalThis.Function

  function BrokenFunctionConstructor() {
    return {
      ruleType: 'auto',
      autoFunction: 'return [{ name: "next" }]',
    }
  }

  globalThis.Function = BrokenFunctionConstructor

  try {
    const x6Data = transpileToX6Data(
      {
        name: 'demo-flow',
        description: '',
        entryNode: 'action',
        exitNode: '',
        errorEntryNode: '',
        nodes: [
          {
            type: 'action',
            name: 'action',
            inputPorts: [],
            outputPorts: [],
          },
        ],
        connections: [],
      },
      [
        {
          type: 'action',
          title: '处理',
          portRules: {
            output: {
              ruleType: 'auto',
              autoFunction: 'return [{ name: "next" }]',
            },
          },
        },
      ]
    )

    const actionNode = x6Data.nodes.find((node) => node.data?.type === 'action')

    assert.ok(actionNode)
    assert.equal(actionNode.data.outputPorts.length, 0)
    assert.equal(actionNode.ports.items.filter((item) => item.group === 'out').length, 0)
  } finally {
    globalThis.Function = originalFunction
  }
})

function createGraphHarness() {
  const fromJSONCalls = []
  const triggerCalls = []
  return {
    fromJSONCalls,
    triggerCalls,
    fromJSON(data) {
      fromJSONCalls.push(data)
    },
    trigger(name, payload) {
      triggerCalls.push({ name, payload })
    },
  }
}

function createTriggerGraphHarness(x6Data) {
  const triggerCalls = []
  const attrCalls = []
  let currentX6Data = x6Data

  function getCellsFromCurrentData() {
    return [...(currentX6Data.nodes || []), ...(currentX6Data.edges || [])].map((cell) => ({
      toJSON() {
        return cell
      },
    }))
  }

  function getNodesFromCurrentData() {
    return (currentX6Data.nodes || []).map((node) => ({
      data: node.data,
      attr(path, value) {
        attrCalls.push({ name: node.data?.name, path, value })
      },
    }))
  }

  return {
    triggerCalls,
    attrCalls,
    on() {},
    off() {},
    updateX6Data(nextX6Data) {
      currentX6Data = nextX6Data
    },
    fromJSON(nextX6Data) {
      currentX6Data = nextX6Data
    },
    getCells() {
      return getCellsFromCurrentData()
    },
    getNodes() {
      return getNodesFromCurrentData()
    },
    trigger(name, payload) {
      triggerCalls.push({ name, payload })
    },
    disableHistory() {},
    enableHistory() {},
  }
}

test('transpileToX6Data exposes invalid connection warnings through onWarning', () => {
  const warnings = []
  const diagnostics = []
  const originalWarn = console.warn
  console.warn = () => {}

  try {
    const x6Data = transpileToX6Data(createFlowWithInvalidConnection(), createNodeDefines(), {
      onWarning(warning) {
        warnings.push(warning)
      },
      collectDiagnosticFn(diagnostic) {
        diagnostics.push(diagnostic)
      },
    })

    assert.equal(x6Data.nodes.length, 3)
    assert.equal(x6Data.edges.length, 2)
    assert.deepEqual(warnings, [
      {
        message: '跳过无效连线',
        payload: {
          reason: 'missing-source-node',
          edgeData: {
            sourceNode: 'missing',
            sourceOutputPort: 0,
            targetNode: 'action',
            targetInputPort: 0,
            label: 'invalid',
          },
        },
      },
    ])
    assert.deepEqual(diagnostics, [])
  } finally {
    console.warn = originalWarn
  }
})

test('transpileToX6Data exposes missing port invalid connections as diagnostics', () => {
  const warnings = []
  const diagnostics = []
  const originalWarn = console.warn
  console.warn = () => {}

  try {
    const nodeDefines = [
      {
        type: 'start',
        title: '开始',
      },
      {
        type: 'action',
        title: '处理',
      },
      {
        type: 'sourceless',
        title: '空输出',
        portRules: {
          input: { ruleType: 'limit', min: 0, max: 0 },
          output: { ruleType: 'limit', min: 0, max: 0 },
        },
      },
      {
        type: 'sinkless',
        title: '空输入',
        portRules: {
          input: { ruleType: 'limit', min: 0, max: 0 },
          output: { ruleType: 'limit', min: 0, max: 0 },
        },
      },
    ]

    transpileToX6Data(
      {
        name: 'demo-flow',
        description: '',
        entryNode: 'start',
        exitNode: '',
        errorEntryNode: '',
        nodes: [
          {
            type: 'start',
            name: 'start',
            inputPorts: [],
            outputPorts: [createPort('next')],
          },
          {
            type: 'action',
            name: 'action',
            inputPorts: [createPort('input')],
            outputPorts: [],
          },
          {
            type: 'sourceless',
            name: 'sourceless',
            inputPorts: [],
            outputPorts: [],
          },
          {
            type: 'sinkless',
            name: 'sinkless',
            inputPorts: [],
            outputPorts: [],
          },
        ],
        connections: [
          {
            sourceNode: 'sourceless',
            targetNode: 'action',
            targetInputPort: 0,
            label: 'missing-source-port',
          },
          {
            sourceNode: 'start',
            sourceOutputPort: 0,
            targetNode: 'sinkless',
            label: 'missing-target-port',
          },
        ],
      },
      nodeDefines,
      {
        onWarning(warning) {
          warnings.push(warning)
        },
        collectDiagnosticFn(diagnostic) {
          diagnostics.push(diagnostic)
        },
      }
    )

    assert.deepEqual(warnings, [
      {
        message: '跳过无效连线',
        payload: {
          reason: 'missing-source-port',
          edgeData: {
            sourceNode: 'sourceless',
            targetNode: 'action',
            targetInputPort: 0,
            label: 'missing-source-port',
          },
        },
      },
      {
        message: '跳过无效连线',
        payload: {
          reason: 'missing-target-port',
          edgeData: {
            sourceNode: 'start',
            sourceOutputPort: 0,
            targetNode: 'sinkless',
            label: 'missing-target-port',
          },
        },
      },
    ])
    assert.deepEqual(diagnostics, [
      {
        message: '源输出端口不存在',
        severity: 'error',
        blocking: true,
        scope: 'edge',
        fieldPath: 'sourceOutputPort',
        nodeName: 'sourceless',
        sourceNode: 'sourceless',
        sourceOutputPort: null,
        targetNode: 'action',
        targetInputPort: 0,
      },
      {
        message: '目标输入端口不存在',
        severity: 'error',
        blocking: true,
        scope: 'edge',
        fieldPath: 'targetInputPort',
        nodeName: 'sinkless',
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'sinkless',
        targetInputPort: null,
      },
    ])
  } finally {
    console.warn = originalWarn
  }
})

test('transpileToX6Data injects editor-only phantom entry, error entry, and return nodes', () => {
  const x6Data = transpileToX6Data(createFlowWithPhantomRoles(), createNodeDefines())

  assert.deepEqual(
    x6Data.nodes.map((node) => node.data?.name),
    [
      'VirtualEntryNode',
      'start',
      'work',
      'VirtualErrorEntryNode',
      'error-start',
      'end',
      'VirtualReturnNode',
      'done',
    ]
  )
  assert.deepEqual(
    x6Data.edges.map((edge) => ({
      source: edge.source,
      target: edge.target,
      phantom: edge.data?.__virtual === true,
    })),
    [
      {
        source: { cell: 'virtual-entry-node' },
        target: { cell: 'node0' },
        phantom: true,
      },
      {
        source: { cell: 'virtual-error-entry-node' },
        target: { cell: 'node2' },
        phantom: true,
      },
      {
        source: { cell: 'node3' },
        target: { cell: 'virtual-return-node' },
        phantom: true,
      },
    ]
  )
})

test('transpileToX6Data suppresses the phantom return node when exit already connects to a real return node', () => {
  const x6Data = transpileToX6Data(
    createFlowWithPhantomRoles({ directReturn: true }),
    createNodeDefines()
  )

  assert.equal(
    x6Data.nodes.some((node) => node.data?.name === 'VirtualReturnNode'),
    false
  )
  assert.equal(
    x6Data.edges.some((edge) => edge.data?.__virtual === true && edge.target?.cell === 'virtual-return-node'),
    false
  )
})

test('LogicFlowPlugin triggerChange still emits change data when graph validation fails', () => {
  const flow = createFlowWithValidationWarning()
  const x6Data = transpileToX6Data(flow, createNodeDefines())
  const graph = createTriggerGraphHarness(x6Data)
  const plugin = new LogicFlowPlugin({
    data: flow,
    nodeDefines: createNodeDefines(),
    layout: null,
  })

  plugin.graph = graph
  plugin.logicFlowProps = {
    name: flow.name,
    entryNode: flow.entryNode,
    exitNode: flow.exitNode,
    errorEntryNode: flow.errorEntryNode,
  }

  plugin.triggerChange()
  plugin.triggerChange.flush()

  assert.equal(
    graph.triggerCalls.some(({ name }) => name === 'logicflow:change:data'),
    true
  )
  assert.equal(
    graph.triggerCalls.some(({ name }) => name === 'logicflow:validateFailed'),
    true
  )

  const validateFailedCall = graph.triggerCalls.find(({ name }) => name === 'logicflow:validateFailed')
  assert.deepEqual(validateFailedCall?.payload?.errors, ['请检查输入端口是否冲突'])
  assert.deepEqual(validateFailedCall?.payload?.entries, [{ message: '请检查输入端口是否冲突' }])
})

test('LogicFlowPlugin triggerChange does not round-trip legacy nodePosition props', () => {
  const flow = {
    ...createFlowWithUnconnectedInput(),
    nodePosition: {
      start: { x: 10, y: 20 },
    },
  }
  const x6Data = transpileToX6Data(flow, createNodeDefines())
  const graph = createTriggerGraphHarness(x6Data)
  const plugin = new LogicFlowPlugin({
    data: flow,
    nodeDefines: createNodeDefines(),
    layout: null,
  })

  plugin.graph = graph
  plugin.initData(flow, createNodeDefines())
  graph.triggerCalls.length = 0

  plugin.triggerChange()
  plugin.triggerChange.flush()

  const changeCall = graph.triggerCalls.find(({ name }) => name === 'logicflow:change:data')

  assert.equal(changeCall?.name, 'logicflow:change:data')
  assert.equal('nodePosition' in (changeCall?.payload?.data || {}), false)
  assert.equal('nodePositions' in (changeCall?.payload?.data || {}), false)
})

test('LogicFlowPlugin updateLogicFlowProps rehydrates role virtual nodes when entry node changes', () => {
  const flow = createFlowWithUnconnectedInput()
  const nodeDefines = createNodeDefines()
  const graph = createTriggerGraphHarness(transpileToX6Data(flow, nodeDefines))
  const plugin = new LogicFlowPlugin({
    data: flow,
    nodeDefines,
    layout: null,
  })

  plugin.init(graph)
  graph.triggerCalls.length = 0

  graph.updateLogicFlowProps({
    name: flow.name,
    description: flow.description,
    entryNode: '',
    exitNode: flow.exitNode,
    errorEntryNode: flow.errorEntryNode,
  })
  plugin.triggerChange.flush()

  const currentCells = graph.getCells().map((cell) => cell.toJSON())

  assert.equal(currentCells.some((cell) => cell.id === 'virtual-entry-node'), false)
  assert.equal(
    currentCells.some((cell) => cell.data?.__virtualKind === 'entry'),
    false
  )
  assert.equal(graph.triggerCalls.some(({ name }) => name === 'logicflow:change:data'), true)
  const changeCall = graph.triggerCalls.find(({ name }) => name === 'logicflow:change:data')
  assert.equal(changeCall?.payload?.data?.entryNode, '')
})

test('LogicFlowPlugin validateFailed payload keeps message compatibility and expands unconnected inputs per port', () => {
  const flow = createFlowWithUnconnectedInput()
  const x6Data = transpileToX6Data(flow, createNodeDefines())
  const graph = createTriggerGraphHarness(x6Data)
  const plugin = new LogicFlowPlugin({
    data: flow,
    nodeDefines: createNodeDefines(),
    layout: null,
  })

  plugin.graph = graph
  plugin.logicFlowProps = {
    name: flow.name,
    entryNode: flow.entryNode,
    exitNode: flow.exitNode,
    errorEntryNode: flow.errorEntryNode,
  }

  plugin.triggerChange()
  plugin.triggerChange.flush()

  const validateFailedCall = graph.triggerCalls.find(({ name }) => name === 'logicflow:validateFailed')

  assert.equal(validateFailedCall?.name, 'logicflow:validateFailed')
  assert.equal(validateFailedCall?.payload?.message, '请检查所有节点的输入端口是否都已经连接')
  assert.deepEqual(validateFailedCall?.payload?.errors, ['节点「target」的输入端口「right」未连接'])
  assert.deepEqual(validateFailedCall?.payload?.entries, [
    {
      message: '节点「target」的输入端口「right」未连接',
      messageKey: '节点「{{p0}}」的输入端口「{{p1}}」未连接',
      messageParams: { p0: 'target', p1: 'right' },
      nodeName: 'target',
    },
  ])
  assert.equal(validateFailedCall?.payload?.data?.nodes?.[1]?.name, 'target')
  assert.equal(validateFailedCall?.payload?.data?.nodes?.[1]?.inputPorts?.[1]?.name, 'right')
})

test('LogicFlowPlugin clears stale node highlights when a different validation warning has no node targets', () => {
  const plugin = new LogicFlowPlugin({
    data: createFlowWithUnconnectedInput(),
    nodeDefines: createNodeDefines(),
    layout: null,
  })
  const graph = createTriggerGraphHarness(
    transpileToX6Data(createFlowWithUnconnectedInput(), createNodeDefines())
  )

  plugin.graph = graph
  plugin.logicFlowProps = {
    name: 'demo-flow',
    entryNode: 'start',
    exitNode: '',
    errorEntryNode: '',
  }

  plugin.triggerChange()
  plugin.triggerChange.flush()

  graph.attrCalls.length = 0
  graph.updateX6Data(transpileToX6Data(createFlowWithValidationWarning(), createNodeDefines()))

  plugin.triggerChange()
  plugin.triggerChange.flush()

  assert.equal(
    graph.attrCalls.some(
      ({ name, path, value }) =>
        name === 'target' && path === 'errorGlow/visibility' && value === 'hidden'
    ),
    true
  )
})

test('LogicFlowPlugin merges external validation diagnostics into node error highlights', () => {
  const flow = createFlowWithUnconnectedInput()
  const plugin = new LogicFlowPlugin({
    data: flow,
    nodeDefines: createNodeDefines(),
    layout: null,
  })
  const graph = createTriggerGraphHarness(transpileToX6Data(flow, createNodeDefines()))

  plugin.graph = graph
  plugin.logicFlowProps = {
    name: flow.name,
    entryNode: flow.entryNode,
    exitNode: flow.exitNode,
    errorEntryNode: flow.errorEntryNode,
  }
  plugin.externalValidationNodeNames = ['start']

  plugin.triggerChange()
  plugin.triggerChange.flush()

  assert.equal(
    graph.attrCalls.some(
      ({ name, path, value }) =>
        name === 'start' && path === 'errorGlow/visibility' && value === 'visible'
    ),
    true
  )
  assert.equal(
    graph.attrCalls.some(
      ({ name, path, value }) =>
        name === 'target' && path === 'errorGlow/visibility' && value === 'visible'
    ),
    true
  )
})

test('LogicFlowPlugin initData emits transpile warnings for skipped invalid connections', () => {
  const graph = createGraphHarness()
  const flow = createFlowWithInvalidConnection()
  const nodeDefines = createNodeDefines()
  const originalWarn = console.warn
  console.warn = () => {}

  try {
    const plugin = new LogicFlowPlugin({
      data: flow,
      nodeDefines,
      layout: null,
    })
    plugin.graph = graph

    plugin.initData(flow, nodeDefines)

    assert.equal(graph.fromJSONCalls.length, 1)
    assert.equal(graph.triggerCalls.length, 1)
    assert.deepEqual(graph.triggerCalls[0], {
      name: 'logicflow:transpileWarnings',
      payload: {
        data: flow,
        warnings: [
          {
            message: '跳过无效连线',
            payload: {
              reason: 'missing-source-node',
              edgeData: {
                sourceNode: 'missing',
                sourceOutputPort: 0,
                targetNode: 'action',
                targetInputPort: 0,
                label: 'invalid',
              },
            },
          },
        ],
        diagnostics: [],
      },
    })
  } finally {
    console.warn = originalWarn
  }
})
