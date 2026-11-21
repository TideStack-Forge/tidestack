import { expect, test } from 'vitest'
import { act } from 'react'

import { createLinearFixture } from './harness/fixtures.mjs'
import { createLogicflowEditorHarness } from './harness/createLogicflowEditorHarness.mjs'

async function setInputValue(input, value) {
  const descriptor = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')

  await act(async () => {
    input.focus?.()
    descriptor?.set?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
    input.dispatchEvent(new Event('change', { bubbles: true }))
    await Promise.resolve()
  })
}

function createPort(name, description = '') {
  return {
    name,
    description,
    dataType: 'any',
  }
}

function createValidationToolbarNodeDefines() {
  return [
    {
      type: 'start',
      label: '入口',
      group: '基础',
      icon: 'unknown',
      editorCapabilities: {
        canBeEntry: true,
      },
      scaffold: {
        type: 'start',
        name: '入口节点',
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
      type: 'merge',
      label: '聚合节点',
      group: '基础',
      icon: 'unknown',
      scaffold: {
        type: 'merge',
        name: '聚合节点',
        inputPorts: [createPort('first'), createPort('second'), createPort('third')],
        outputPorts: [createPort('next')],
      },
      portRules: {
        input: { ruleType: 'limit', min: 3, max: 3 },
        output: { ruleType: 'limit', min: 1, max: 1 },
      },
      properties: [],
    },
    {
      type: 'return',
      label: '返回',
      group: '终止',
      icon: 'unknown',
      scaffold: {
        type: 'return',
        name: '返回节点',
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

function createValidationToolbarFixture() {
  return {
    name: 'logicflow-editor-validation-toolbar',
    description: 'validation toolbar fixture',
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
      },
      {
        type: 'merge',
        name: '聚合节点',
        inputPorts: [createPort('first'), createPort('second'), createPort('third')],
        outputPorts: [createPort('next')],
        isEnabled: true,
      },
      {
        type: 'return',
        name: '返回节点',
        inputPorts: [createPort('input')],
        outputPorts: [],
        isEnabled: true,
      },
    ],
    connections: [
      {
        sourceNode: '入口节点',
        sourceOutputPort: 0,
        targetNode: '聚合节点',
        targetInputPort: 0,
      },
      {
        sourceNode: '聚合节点',
        sourceOutputPort: 0,
        targetNode: '返回节点',
        targetInputPort: 0,
      },
    ],
  }
}

function createPersistedBackendInvalidFixture() {
  return {
    ...createLinearFixture(),
    validationErrors: ['后端校验错误一', '后端校验错误二'],
    validationSummary: '后端校验摘要',
  }
}

function createPersistedBackendInvalidSummaryOnlyFixture() {
  return {
    ...createLinearFixture(),
    validationErrors: [],
    validationSummary: '仅返回后端校验摘要',
  }
}

function createPersistedBackendDiagnosticFixture() {
  return {
    ...createLinearFixture(),
    validationErrors: ['脚本节点存在后端校验错误'],
    validationSummary: '脚本节点存在后端校验错误',
    validationDiagnostics: [
      {
        diagnosticKind: 'NODE_CONFIG_INVALID',
        message: '脚本节点存在后端校验错误',
        nodeNames: ['脚本节点'],
      },
    ],
  }
}

function createValidationSnapshotFixture() {
  return {
    validationState: 'INVALID',
    validationErrors: ['脚本节点存在后端校验错误'],
    validationSummary: '脚本节点存在后端校验错误',
    validationDiagnostics: [
      {
        diagnosticKind: 'NODE_CONFIG_INVALID',
        message: '脚本节点存在后端校验错误',
        nodeNames: ['脚本节点'],
      },
    ],
  }
}

test('mounted editor exposes initial graph nodes and edges from fixture data', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const graph = harness.getGraph()
    expect(graph.getNodes().map((node) => node.getData()?.name)).toEqual([
      'VirtualEntryNode',
      '入口节点',
      '脚本节点',
      '返回节点',
    ])
    expect(graph.getEdges()).toHaveLength(3)
  } finally {
    await harness.unmount()
  }
})

test('phantom nodes and edges stay non-selectable while real cells remain selectable', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const graph = harness.getGraph()
    const realNode = graph.getNodes().find((node) => node.getData()?.name === '脚本节点')
    const phantomNode = graph
      .getNodes()
      .find((node) => node.getData()?.__virtual === true && node.getData()?.name === 'VirtualEntryNode')
    const realEdge = graph.getEdges().find(
      (edge) => !edge.getData()?.__virtual && edge.getData()?.sourceNode === '脚本节点'
    )
    const phantomEdge = graph.getEdges().find((edge) => edge.getData()?.__virtual === true)

    expect(realNode).toBeTruthy()
    expect(phantomNode).toBeTruthy()
    expect(realEdge).toBeTruthy()
    expect(phantomEdge).toBeTruthy()

    graph.cleanSelection()
    graph.select(realNode, { highlight: true })
    await harness.waitFor(() => graph.getSelectedCells().length === 1)
    expect(graph.getSelectedCells()).toContain(realNode)

    graph.cleanSelection()
    graph.select(phantomNode, { highlight: true })
    await harness.waitFor(() => graph.getSelectedCells().length === 0)
    expect(graph.getSelectedCells()).toHaveLength(0)

    graph.cleanSelection()
    graph.select(realEdge, { highlight: true })
    await harness.waitFor(() => graph.getSelectedCells().length === 1)
    expect(graph.getSelectedCells()).toContain(realEdge)

    graph.cleanSelection()
    graph.select(phantomEdge, { highlight: true })
    await harness.waitFor(() => graph.getSelectedCells().length === 0)
    expect(graph.getSelectedCells()).toHaveLength(0)
  } finally {
    await harness.unmount()
  }
})

test('node property edits round-trip through real amis rendering into flowData', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.openNodeProperties('脚本节点')
    expect(harness.query('[data-testid="logicflow-inspector-title"]').textContent).toBe('节点属性')
    expect(harness.readRenderedFormState().text()).toContain('节点属性')

    await harness.submitPropertyForm({ name: '脚本节点已更新' })
    await harness.waitFor(() =>
      harness.readFlowData()?.nodes?.some((node) => node.name === '脚本节点已更新')
    )

    expect(
      harness.readFlowData().nodes.find((node) => node.name === '脚本节点已更新')
    ).toBeTruthy()
  } finally {
    await harness.unmount()
  }
})

test('opening node properties does not emit redundant flow change events', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const initialFlowData = harness.readFlowData()
    const initialChangeEventCount = harness.graphEvents.filter(
      (event) => event.type === 'change'
    ).length
    const initialValidateFailedCount = harness.graphEvents.filter(
      (event) => event.type === 'validateFailed'
    ).length

    await harness.openNodeProperties('脚本节点')
    await harness.flush(8)

    const nextChangeEventCount = harness.graphEvents.filter(
      (event) => event.type === 'change'
    ).length
    const nextValidateFailedCount = harness.graphEvents.filter(
      (event) => event.type === 'validateFailed'
    ).length

    expect(harness.readFlowData()).toEqual(initialFlowData)
    expect(nextChangeEventCount).toBe(initialChangeEventCount)
    expect(nextValidateFailedCount).toBe(initialValidateFailedCount)
  } finally {
    await harness.unmount()
  }
})

test('flow property edits round-trip through real amis rendering into flowData', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.openFlowProperties()
    expect(harness.query('[data-testid="logicflow-inspector-title"]').textContent).toBe('流程属性')
    await harness.submitPropertyForm({
      description: 'logicflow editor integration fixture updated',
    })
    await harness.waitFor(
      () =>
        harness.readFlowData()?.description === 'logicflow editor integration fixture updated'
    )

    expect(harness.readFlowData().description).toBe('logicflow editor integration fixture updated')
    expect(harness.readFlowData().name).toBe('logicflow-editor-integration')
  } finally {
    await harness.unmount()
  }
})

test('flow property form saves invalid values through change events and keeps description rows stable', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.openFlowProperties()

    const propertyForm = harness.readPropertiesForm()
    const descriptionControl = harness.readPropertiesControl('description')

    expect(propertyForm).toBeTruthy()
    expect(propertyForm.props.submitOnChange).toBe(false)
    expect(typeof propertyForm.props.onChange).toBe('function')
    expect(descriptionControl?.props?.minRows).toBe(3)
    expect(descriptionControl?.props?.maxRows).toBe(3)

    await harness.submitPropertyForm({
      entryNode: '',
      description: 'saved before frontend validation passes',
    })

    await harness.waitFor(
      () => harness.readFlowData()?.description === 'saved before frontend validation passes'
    )

    expect(harness.readFlowData().entryNode).toBe('')
    expect(harness.readFlowData().description).toBe('saved before frontend validation passes')
  } finally {
    await harness.unmount()
  }
})

test('flow properties expose role dropdown options even when the flow has no name', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: {
      ...createLinearFixture(),
      name: '',
      exitNode: '返回节点',
    },
  }).mount()

  try {
    await harness.openFlowProperties()

    const entryControl = harness.readPropertiesControl('entryNode')
    const exitControl = harness.readPropertiesControl('exitNode')
    const errorEntryControl = harness.readPropertiesControl('errorEntryNode')

    expect(entryControl).toBeTruthy()
    expect(exitControl).toBeTruthy()
    expect(errorEntryControl).toBeTruthy()
    expect(entryControl.props.source).toBe('${__ENTRY_NODES}')
    expect(exitControl.props.source).toBe('${__EXIT_NODES}')
    expect(errorEntryControl.props.source).toBe('${__ERROR_ENTRY_NODES}')

    const formState = harness.readRenderedFormState()
    expect(formState.text()).toContain('入口节点')
    expect(formState.text()).toContain('出口节点')
    expect(formState.text()).toContain('异常入口节点')

    await harness.waitFor(
      () => Array.isArray(harness.readPropertiesControl('entryNode')?.props?.options)
    )

    const entryOptions = harness
      .readPropertiesControl('entryNode')
      ?.props?.options?.map((option) => option?.value)
      .filter(Boolean)

    expect(entryOptions).toEqual(expect.arrayContaining(['入口节点', '脚本节点', '返回节点']))

    expect(harness.readFlowData().name).toBe('')
    expect(harness.readFlowData().nodes.length).toBeGreaterThan(0)
  } finally {
    await harness.unmount()
  }
})

test('properties panel scroll cue tracks real scroll progress and only appears during active scrolling', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.openFlowProperties()

    const formContainer = harness.query('.properties-panel .form-container')
    const scrollIndicator = harness.query('.properties-panel .scroll-indicator')
    const scrollIndicatorThumb = harness.query('.properties-panel .scroll-indicator-thumb')
    expect(formContainer).toBeTruthy()
    expect(scrollIndicator).toBeTruthy()
    expect(scrollIndicatorThumb).toBeTruthy()
    expect(formContainer.classList.contains('is-scrolling')).toBe(false)

    Object.defineProperties(formContainer, {
      clientHeight: {
        configurable: true,
        value: 240,
      },
      scrollHeight: {
        configurable: true,
        value: 960,
      },
      scrollTop: {
        configurable: true,
        writable: true,
        value: 180,
      },
    })

    formContainer.dispatchEvent(new Event('scroll', { bubbles: true }))
    await harness.flush()
    await harness.waitFor(() => formContainer.classList.contains('is-scrolling'))

    expect(formContainer.classList.contains('is-scrolling')).toBe(true)
    expect(scrollIndicator.classList.contains('visible')).toBe(true)
    expect(scrollIndicatorThumb.style.height).toBe('60px')
    expect(scrollIndicatorThumb.style.transform).toBe('translateY(45px)')

    await harness.waitFor(() => !formContainer.classList.contains('is-scrolling'), {
      timeoutMs: 1200,
    })

    expect(formContainer.classList.contains('is-scrolling')).toBe(false)
  } finally {
    await harness.unmount()
  }
})

test('harness addNode appends a serialized node that preserves port contracts', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const previousNodeCount = harness.readFlowData().nodes.length

    await harness.addNode({
      type: 'script',
      name: '脚本节点新增',
      position: { x: 520, y: 260 },
    })

    await harness.waitFor(() => harness.readFlowData().nodes.length === previousNodeCount + 1)

    const appendedNode = harness.readFlowData().nodes.find((node) => node.name === '脚本节点新增')
    expect(appendedNode).toBeTruthy()
    expect(appendedNode.inputPorts).toHaveLength(1)
    expect(appendedNode.outputPorts).toHaveLength(1)
    expect(appendedNode.inputPorts[0]).toMatchObject({ dataType: 'any' })
    expect(appendedNode.outputPorts[0]).toMatchObject({ dataType: 'any' })
  } finally {
    await harness.unmount()
  }
})

test('harness connect appends a serialized connection with source and target port references', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.addNode({
      type: 'script',
      name: '脚本节点新增',
      position: { x: 520, y: 260 },
    })
    await harness.waitFor(() =>
      harness.readFlowData().nodes.some((node) => node.name === '脚本节点新增')
    )

    const previousConnectionCount = harness.readFlowData().connections.length

    await harness.connect({
      sourceNode: '脚本节点',
      sourceOutputPort: 0,
      targetNode: '脚本节点新增',
      targetInputPort: 0,
    })

    await harness.waitFor(
      () => harness.readFlowData().connections.length === previousConnectionCount + 1
    )

    expect(harness.readFlowData().connections).toContainEqual({
      sourceNode: '脚本节点',
      sourceOutputPort: 0,
      targetNode: '脚本节点新增',
      targetInputPort: 0,
    })
  } finally {
    await harness.unmount()
  }
})

test('edge property edits round-trip through real amis rendering into flowData connections', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    await harness.openEdgeProperties({
      sourceNode: '脚本节点',
      sourceOutputPort: 0,
      targetNode: '返回节点',
      targetInputPort: 0,
    })
    expect(harness.query('[data-testid="logicflow-inspector-title"]').textContent).toBe('连线属性')

    await harness.submitPropertyForm({
      description: 'script-to-return edge',
    })

    await harness.waitFor(() =>
      harness.readFlowData().connections.some(
        (connection) =>
          connection.sourceNode === '脚本节点' &&
          connection.sourceOutputPort === 0 &&
          connection.targetNode === '返回节点' &&
          connection.targetInputPort === 0 &&
          connection.description === 'script-to-return edge'
      )
    )

    expect(harness.readFlowData().connections).toContainEqual({
      sourceNode: '脚本节点',
      sourceOutputPort: 0,
      targetNode: '返回节点',
      targetInputPort: 0,
      description: 'script-to-return edge',
    })
  } finally {
    await harness.unmount()
  }
})

test('runtime validation failures surface in the toolbar indicator instead of toast notifications and clear after recovery', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createValidationToolbarFixture(),
    nodeDefines: createValidationToolbarNodeDefines(),
  }).mount()

  try {
    const graph = harness.getGraph()
    graph.flushLogicFlowChange?.()

    await harness.waitFor(() => harness.graphEvents.some((event) => event.type === 'validateFailed'))
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-surface"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')

    await harness.click(indicator)
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-popover"]'))

    const errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')
    const validationButtons = harness.queryAll('.logicflow-editor-diagnostics-item[type="button"]')

    expect(indicator).toBeTruthy()
    expect(indicator.getAttribute('data-diagnostics-status')).toBe('draft-invalid')
    expect(indicator.textContent).toContain('存在错误')
    expect(indicator.textContent).toContain('2')
    expect(errorItems.map((item) => item.textContent)).toEqual([
      '错误节点「聚合节点」的输入端口「second」未连接',
      '错误节点「聚合节点」的输入端口「third」未连接',
    ])
    expect(validationButtons).toHaveLength(2)
    expect(harness.toastMessages).toEqual([])

    await harness.connect({
      sourceNode: '入口节点',
      sourceOutputPort: 0,
      targetNode: '聚合节点',
      targetInputPort: 1,
    })
    await harness.connect({
      sourceNode: '入口节点',
      sourceOutputPort: 0,
      targetNode: '聚合节点',
      targetInputPort: 2,
    })

    await harness.waitFor(() =>
      harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')?.getAttribute('data-diagnostics-status') ===
        'clean'
    )
    expect(harness.toastMessages).toEqual([])
  } finally {
    await harness.unmount()
  }
})

test('clicking a runtime validation entry locates the related node through the existing editor navigation path', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createValidationToolbarFixture(),
    nodeDefines: createValidationToolbarNodeDefines(),
  }).mount()

  try {
    const graph = harness.getGraph()
    graph.flushLogicFlowChange?.()

    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    indicator.click()
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-item[type="button"]'))

    const firstButton = harness.query('.logicflow-editor-diagnostics-item[type="button"]')
    await harness.click(firstButton)

    await harness.waitFor(() => graph.getSelectedCells().length === 1)
    await harness.waitFor(
      () => harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible') === 'true'
    )
    const selectedNode = graph.getSelectedCells()[0]
    expect(selectedNode?.getData?.()?.name).toBe('聚合节点')
    expect(selectedNode?.getData?.()?.__virtual).not.toBe(true)
    expect(harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('persisted backend-invalid flowData shows the toolbar validation indicator on mount', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendInvalidFixture(),
  }).mount()

  try {
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    await harness.click(indicator)
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-list li'))

    const errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')

    expect(errorItems.map((item) => item.textContent)).toEqual([
      '错误后端校验错误一',
      '错误后端校验错误二',
    ])
    expect(harness.graphEvents.some((event) => event.type === 'validateFailed')).toBe(false)
  } finally {
    await harness.unmount()
  }
})

test('persisted backend diagnostics survive runtime validation and local recovery until backend state changes', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: {
      ...createPersistedBackendInvalidFixture(),
      ...createValidationToolbarFixture(),
      validationErrors: ['后端校验错误一', '后端校验错误二'],
      validationSummary: '后端校验摘要',
    },
    nodeDefines: createValidationToolbarNodeDefines(),
  }).mount()

  try {
    const graph = harness.getGraph()

    graph.flushLogicFlowChange?.()

    await harness.waitFor(() => harness.graphEvents.some((event) => event.type === 'validateFailed'))
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    await harness.click(indicator)
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-list li'))

    let errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')
    expect(errorItems.map((item) => item.textContent)).toEqual([
      '错误后端校验错误一',
      '错误后端校验错误二',
      '错误节点「聚合节点」的输入端口「second」未连接',
      '错误节点「聚合节点」的输入端口「third」未连接',
    ])

    await harness.connect({
      sourceNode: '入口节点',
      sourceOutputPort: 0,
      targetNode: '聚合节点',
      targetInputPort: 1,
    })
    await harness.connect({
      sourceNode: '入口节点',
      sourceOutputPort: 0,
      targetNode: '聚合节点',
      targetInputPort: 2,
    })

    await harness.waitFor(() => {
      const nextIndicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
      return nextIndicator?.getAttribute('data-diagnostics-status') === 'draft-invalid'
    })

    await harness.waitFor(() => {
      const currentItems = harness.queryAll('.logicflow-editor-diagnostics-list li')
      if (currentItems.length === 2) {
        return true
      }
      const popover = harness.query('[data-testid="logicflow-editor-diagnostics-popover"]')
      return !popover
    })

    if (!harness.query('[data-testid="logicflow-editor-diagnostics-popover"]')) {
      const reopenedIndicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
      await harness.click(reopenedIndicator)
      await harness.waitFor(() => harness.queryAll('.logicflow-editor-diagnostics-list li').length === 2)
    }

    errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')
    expect(errorItems.map((item) => item.textContent)).toEqual([
      '错误后端校验错误一',
      '错误后端校验错误二',
    ])
  } finally {
    await harness.unmount()
  }
})

test('persisted backend-invalid flowData falls back to validationSummary when detailed errors are absent', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendInvalidSummaryOnlyFixture(),
  }).mount()

  try {
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    await harness.click(indicator)
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-list li'))

    const errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')

    expect(errorItems.map((item) => item.textContent)).toEqual(['错误仅返回后端校验摘要'])
    expect(harness.graphEvents.some((event) => event.type === 'validateFailed')).toBe(false)
  } finally {
    await harness.unmount()
  }
})

test('clicking a persisted backend diagnostic entry locates the related node when nodeNames are available', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendDiagnosticFixture(),
  }).mount()

  try {
    const graph = harness.getGraph()
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))

    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    await harness.click(indicator)
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-item[type="button"]'))

    const firstButton = harness.query('.logicflow-editor-diagnostics-item[type="button"]')
    await harness.click(firstButton)

    await harness.waitFor(() => graph.getSelectedCells().length === 1)
    await harness.waitFor(
      () => harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible') === 'true'
    )
    const selectedNode = graph.getSelectedCells()[0]
    expect(selectedNode?.getData?.()?.name).toBe('脚本节点')
    expect(selectedNode?.getData?.()?.__virtual).not.toBe(true)
    await harness.waitFor(() => selectedNode?.attr?.('errorGlow/visibility') === 'visible')
    expect(selectedNode?.attr?.('errorGlow/visibility')).toBe('visible')
    expect(selectedNode?.attr?.('errorOutline/visibility')).toBe('visible')
    expect(selectedNode?.attr?.('errorBadgeStem/visibility')).toBe('visible')
    expect(selectedNode?.attr?.('errorBadgeDot/visibility')).toBe('visible')
    expect(harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('selecting a node with persisted backend diagnostics shows the error in the property panel', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendDiagnosticFixture(),
  }).mount()

  try {
    await harness.openNodeProperties('脚本节点')

    expect(harness.query('[data-testid="logicflow-inspector-title"]').textContent).toBe('节点属性')
    expect(harness.readRenderedFormState().text()).toContain('校验错误')
    expect(harness.readRenderedFormState().text()).toContain('脚本节点存在后端校验错误')
  } finally {
    await harness.unmount()
  }
})

test('external validationSnapshot drives diagnostics even when flowData stays formal-only', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
    initialValidationSnapshot: createValidationSnapshotFixture(),
  }).mount()

  try {
    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))
    const indicator = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')
    await harness.click(indicator)
    await harness.waitFor(() => harness.query('.logicflow-editor-diagnostics-list li'))

    const errorItems = harness.queryAll('.logicflow-editor-diagnostics-list li')
    expect(errorItems.map((item) => item.textContent)).toEqual(['错误脚本节点存在后端校验错误'])

    await harness.replaceValidationSnapshot(null)
    await harness.waitFor(
      () => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')?.getAttribute('data-diagnostics-status') ===
        'clean'
    )
  } finally {
    await harness.unmount()
  }
})

test('external flowData replacement clears stale toolbar validation errors from an older invalid graph', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createValidationToolbarFixture(),
    nodeDefines: createValidationToolbarNodeDefines(),
  }).mount()

  try {
    const graph = harness.getGraph()
    graph.flushLogicFlowChange?.()

    await harness.waitFor(() => harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]'))
    expect(
      harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]').getAttribute('data-diagnostics-status')
    ).toBe('draft-invalid')

    await harness.replaceFlowData(createLinearFixture())

    await harness.waitFor(() => harness.readFlowData()?.name === 'logicflow-editor-integration')
    await harness.waitFor(() =>
      harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')?.getAttribute('data-diagnostics-status') ===
        'clean'
    )
    expect(harness.toastMessages).toEqual([])
  } finally {
    await harness.unmount()
  }
})

test('editable canvas keeps the minimap anchored beside the properties panel while it expands and resizes', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const editorRoot = harness.query('.wrapper-editor-container')
    const canvasContainer = harness.query('.logicflow-canvas-container')
    const miniMap = harness.query('[data-testid="logicflow-viewport-cluster"]')
    const miniMapToggle = harness.query('.mini-map [data-control-id="toggle-minimap"]')
    const miniMapZoomInButton = harness.query('.mini-map .mini-map-control-button[aria-label="放大"]')
    const miniMapZoomOutButton = harness.query('.mini-map .mini-map-control-button[aria-label="缩小"]')
    const toolbarZoomButtons = harness.queryAll(
      '.toolbar .fa-magnifying-glass-plus, .toolbar .fa-magnifying-glass-minus'
    )
    const propertiesPanel = harness.query('.properties-panel')

    expect(canvasContainer.classList.contains('is-editable')).toBe(true)
    expect(miniMap).toBeTruthy()
    expect(miniMapToggle).toBeTruthy()
    expect(miniMapZoomInButton).toBeTruthy()
    expect(miniMapZoomOutButton).toBeTruthy()
    expect(toolbarZoomButtons).toHaveLength(0)
    expect(canvasContainer.contains(miniMap)).toBe(true)
    expect(miniMap.getAttribute('data-layout')).toBe('minimap-above-control-strip')
    expect(editorRoot.style.getPropertyValue('--logicflow-mini-map-right')).toBe('16px')

    await harness.openNodeProperties('脚本节点')

    await harness.waitFor(() => editorRoot.style.getPropertyValue('--logicflow-mini-map-right') === '482px')
    expect(propertiesPanel.style.width).toBe('450px')
    expect(editorRoot.style.getPropertyValue('--logicflow-mini-map-right')).toBe('482px')

    const resizeHandle = harness.query('.properties-panel .resize-handle')
    resizeHandle.dispatchEvent(
      new MouseEvent('mousedown', { bubbles: true, clientX: 1000, clientY: 200 })
    )
    document.dispatchEvent(new MouseEvent('mousemove', { bubbles: true, clientX: 950, clientY: 200 }))
    document.dispatchEvent(new MouseEvent('mouseup', { bubbles: true, clientX: 950, clientY: 200 }))

    await harness.flush(4)
    await harness.waitFor(() => propertiesPanel.style.width === '500px')
    await harness.waitFor(() => editorRoot.style.getPropertyValue('--logicflow-mini-map-right') === '532px')

    const panelClose = harness.query('[data-testid="logicflow-right-panel-close"]')
    panelClose.click()
    await harness.waitFor(() => harness.query('.properties-panel.collapsed'))
    await harness.waitFor(() => editorRoot.style.getPropertyValue('--logicflow-mini-map-right') === '16px')

    await harness.click(miniMapToggle)
    await harness.waitFor(() => miniMap.getAttribute('data-layout') === 'expand-button-only')
    expect(harness.query('.mini-map .mini-map-preview')).toBeFalsy()
    expect(harness.query('.mini-map .mini-map-control-button[aria-label="放大"]')).toBeFalsy()
    expect(harness.query('.mini-map .mini-map-control-button[aria-label="缩小"]')).toBeFalsy()

    const miniMapReopen = harness.query('.mini-map [data-control-id="toggle-minimap"]')
    await harness.click(miniMapReopen)
    await harness.waitFor(() => miniMap.getAttribute('data-layout') === 'minimap-above-control-strip')
    await harness.waitFor(() => harness.query('.mini-map .mini-map-preview .x6-widget-minimap'))
  } finally {
    await harness.unmount()
  }
})

test('inspector close-only button hides panel and toolbar toggle reopens the latest scope', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const toolbar = harness.query('[data-testid="logicflow-editor-toolbar"]')
    const inspector = harness.query('[data-testid="logicflow-panel-inspector"]')
    const inspectorToggle = harness.query('[data-command-id="toggle-inspector"]')

    await harness.openNodeProperties('脚本节点')
    await harness.waitFor(() => inspector.getAttribute('data-panel-visible') === 'true')

    const panelClose = harness.query('[data-testid="logicflow-right-panel-close"]')
    expect(panelClose).toBeTruthy()
    expect(harness.query('.properties-panel .collapse-handle')).toBeFalsy()
    expect(inspectorToggle.getAttribute('data-command-pressed')).toBe('true')

    await harness.click(panelClose)
    await harness.waitFor(() => inspector.getAttribute('data-panel-visible') === 'false')

    expect(toolbar.getAttribute('data-palette-visible')).toBe('true')
    expect(inspectorToggle.getAttribute('data-command-pressed')).toBe('false')

    await harness.click(inspectorToggle)
    await harness.waitFor(() => inspector.getAttribute('data-panel-visible') === 'true')
    await harness.waitFor(() => harness.readRenderedFormState().text().includes('节点属性'))

    expect(inspectorToggle.getAttribute('data-command-pressed')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('clearing node selection closes inspector without falling back to flow scope', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const graph = harness.getGraph()
    const inspector = harness.query('[data-testid="logicflow-panel-inspector"]')
    const inspectorToggle = harness.query('[data-command-id="toggle-inspector"]')

    await harness.openNodeProperties('脚本节点')
    await harness.waitFor(() => inspector.getAttribute('data-panel-visible') === 'true')
    expect(harness.readRenderedFormState().text()).toContain('节点属性')
    expect(inspectorToggle.getAttribute('data-command-pressed')).toBe('true')

    graph.cleanSelection()
    await harness.waitFor(() => inspector.getAttribute('data-panel-visible') === 'false')

    expect(graph.getSelectedCells()).toHaveLength(0)
    expect(inspector.classList.contains('collapsed')).toBe(true)
    expect(inspectorToggle.getAttribute('data-command-pressed')).toBe('false')
  } finally {
    await harness.unmount()
  }
})

test('toolbar owns palette visibility and docks beside the expanded palette', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const toolbar = harness.query('[data-testid="logicflow-editor-toolbar"]')
    const leftPanel = harness.query('[data-testid="logicflow-panel-left"]')
    const paletteToggle = harness.query('[data-command-id="toggle-palette"]')
    const panelClose = harness.query('[data-testid="logicflow-left-panel-close"]')
    const nodeSearch = harness.query('[data-testid="logicflow-node-search"]')

    expect(toolbar).toBeTruthy()
    expect(leftPanel).toBeTruthy()
    expect(paletteToggle).toBeTruthy()
    expect(panelClose).toBeTruthy()
    expect(nodeSearch).toBeTruthy()
    expect(harness.query('.left-panel .collapse-handle')).toBeFalsy()
    expect(toolbar.getAttribute('data-palette-visible')).toBe('true')
    expect(toolbar.getAttribute('data-toolbar-left-inset')).toBe('272')
    expect(paletteToggle.getAttribute('data-command-pressed')).toBe('true')
    expect(leftPanel.getAttribute('data-panel-visible')).toBe('true')
    expect(leftPanel.textContent).toContain('脚本节点')
    expect(leftPanel.textContent).toContain('返回')

    const returnPaletteItem = leftPanel.querySelector(
      '.node-list-item-terminal.node-list-item-terminal-successful'
    )
    expect(returnPaletteItem).toBeTruthy()
    expect(returnPaletteItem.getAttribute('data-terminal-kind')).toBe('successful')
    expect(returnPaletteItem.textContent).toContain('返回')
    expect(
      returnPaletteItem.style.getPropertyValue('--logicflow-palette-node-fill')
    ).toBe('#F4FBF6')
    expect(
      returnPaletteItem.style.getPropertyValue('--logicflow-palette-node-stroke')
    ).toBe('#6CC08B')

    await setInputValue(nodeSearch, '返回')
    await harness.flush()
    await harness.waitFor(
      () => leftPanel.textContent.includes('返回') && !leftPanel.textContent.includes('脚本节点')
    )

    expect(leftPanel.textContent).toContain('返回')
    expect(leftPanel.textContent).not.toContain('脚本节点')

    await setInputValue(nodeSearch, '不存在的节点')
    await harness.flush()
    await harness.waitFor(() => leftPanel.textContent.includes('无匹配节点'))

    expect(leftPanel.textContent).toContain('无匹配节点')

    await setInputValue(nodeSearch, '')
    await harness.flush()
    await harness.waitFor(
      () => leftPanel.textContent.includes('脚本节点') && leftPanel.textContent.includes('返回')
    )

    await harness.click(panelClose)
    await harness.waitFor(() => toolbar.getAttribute('data-palette-visible') === 'false')

    expect(leftPanel.classList.contains('collapsed')).toBe(true)
    expect(leftPanel.getAttribute('data-panel-visible')).toBe('false')
    expect(toolbar.getAttribute('data-toolbar-left-inset')).toBe('16')
    expect(paletteToggle.getAttribute('data-command-pressed')).toBe('false')

    await harness.click(paletteToggle)
    await harness.waitFor(() => toolbar.getAttribute('data-palette-visible') === 'true')

    expect(leftPanel.classList.contains('collapsed')).toBe(false)
    expect(leftPanel.getAttribute('data-panel-visible')).toBe('true')
    expect(toolbar.getAttribute('data-toolbar-left-inset')).toBe('272')
    expect(paletteToggle.getAttribute('data-command-pressed')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('readonly shell keeps palette toggle disabled while hiding the palette panel itself', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendInvalidFixture(),
    initialReadOnly: true,
  }).mount()

  try {
    const toolbar = harness.query('[data-testid="logicflow-editor-toolbar"]')
    const navigator = harness.query('[data-testid="logicflow-viewport-cluster"]')
    const inspectorToggle = harness.query('[data-command-id="toggle-inspector"]')
    const paletteToggle = harness.query('[data-command-id="toggle-palette"]')
    const leftPanel = harness.query('[data-testid="logicflow-panel-left"]')
    const diagnosticsTrigger = harness.query('[data-testid="logicflow-editor-diagnostics-trigger"]')

    expect(toolbar).toBeTruthy()
    expect(navigator).toBeTruthy()
    expect(inspectorToggle).toBeTruthy()
    expect(diagnosticsTrigger).toBeTruthy()
    expect(paletteToggle).toBeTruthy()
    expect(paletteToggle.disabled).toBe(true)
    expect(leftPanel).toBeFalsy()
    expect(toolbar.getAttribute('data-palette-visible')).toBe('false')

    await harness.click(inspectorToggle)
    await harness.waitFor(
      () => harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible') === 'true'
    )

    expect(harness.query('[data-testid="logicflow-panel-inspector"]').getAttribute('data-panel-visible')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('toolbar exposes a flow properties entry and selection actions instead of context menus', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const flowEntry = harness.query('[data-command-id="edit-flow"]')
    expect(flowEntry).toBeTruthy()
    expect(flowEntry.textContent).toContain('流程属性')
    expect(flowEntry.getAttribute('data-logicflow-tooltip')).toBe('流程属性')

    await harness.click(flowEntry)
    await harness.waitFor(() => harness.readRenderedFormState().text().includes('输入'))

    await harness.openNodeProperties('脚本节点')
    const deleteSelection = harness.query('[data-command-id="delete-selection"]')
    expect(deleteSelection).toBeTruthy()
    expect(harness.query('#right-click-menu')).toBeFalsy()
  } finally {
    await harness.unmount()
  }
})

test('toolbar exposes canvas assist toggles and keeps readonly assist buttons disabled', async () => {
  const editableHarness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const toolbar = editableHarness.query('[data-testid="logicflow-editor-toolbar"]')
    const gridToggle = editableHarness.query('[data-command-id="toggle-grid-visibility"]')
    const alignmentToggle = editableHarness.query('[data-command-id="toggle-node-alignment"]')

    expect(toolbar).toBeTruthy()
    expect(toolbar.querySelector('[data-command-group="canvas-assist"]')).toBeTruthy()
    expect(gridToggle).toBeTruthy()
    expect(editableHarness.query('[data-command-id="toggle-grid-snapping"]')).toBeFalsy()
    expect(alignmentToggle).toBeTruthy()
    expect(gridToggle.getAttribute('data-logicflow-tooltip')).toBe('网格')
    expect(alignmentToggle.getAttribute('data-logicflow-tooltip')).toBe('节点对齐')
    expect(gridToggle.querySelector('.hover-content')).toBeFalsy()
    expect(gridToggle.getAttribute('data-command-pressed')).toBe('true')
    expect(alignmentToggle.getAttribute('data-command-pressed')).toBe('true')
    expect(gridToggle.querySelector('.fa-border-all')).toBeTruthy()
    expect(alignmentToggle.querySelector('.fa-magnet')).toBeTruthy()

    await editableHarness.click(gridToggle)
    await editableHarness.waitFor(() => gridToggle.getAttribute('data-command-pressed') === 'false')
    await editableHarness.click(alignmentToggle)
    await editableHarness.waitFor(() => alignmentToggle.getAttribute('data-command-pressed') === 'false')
  } finally {
    await editableHarness.unmount()
  }

  const readonlyHarness = await createLogicflowEditorHarness({
    initialFlowData: createPersistedBackendInvalidFixture(),
    initialReadOnly: true,
  }).mount()

  try {
    const gridToggle = readonlyHarness.query('[data-command-id="toggle-grid-visibility"]')
    const alignmentToggle = readonlyHarness.query('[data-command-id="toggle-node-alignment"]')

    expect(gridToggle.disabled).toBe(true)
    expect(readonlyHarness.query('[data-command-id="toggle-grid-snapping"]')).toBeFalsy()
    expect(alignmentToggle.disabled).toBe(true)
  } finally {
    await readonlyHarness.unmount()
  }
})

test('canvas assist toolbar toggles do not mutate flowData or emit flow change events', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const initialFlowData = harness.readFlowData()
    const initialChangeEventCount = harness.graphEvents.filter(
      (event) => event.type === 'change'
    ).length
    const gridToggle = harness.query('[data-command-id="toggle-grid-visibility"]')
    const alignmentToggle = harness.query('[data-command-id="toggle-node-alignment"]')

    expect(gridToggle).toBeTruthy()
    expect(harness.query('[data-command-id="toggle-grid-snapping"]')).toBeFalsy()
    expect(alignmentToggle).toBeTruthy()

    await harness.click(gridToggle)
    await harness.waitFor(() => gridToggle.getAttribute('data-command-pressed') === 'false')
    await harness.click(alignmentToggle)
    await harness.waitFor(() => alignmentToggle.getAttribute('data-command-pressed') === 'false')

    const nextChangeEventCount = harness.graphEvents.filter(
      (event) => event.type === 'change'
    ).length

    expect(harness.readFlowData()).toEqual(initialFlowData)
    expect(nextChangeEventCount).toBe(initialChangeEventCount)
  } finally {
    await harness.unmount()
  }
})

test('position persistence toolbar entry renders as checkbox text toggle', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const positionToggle = harness.query('[data-command-id="toggle-position-persistence"]')

    expect(positionToggle).toBeTruthy()
    expect(positionToggle.textContent).toContain('保存布局')
    expect(positionToggle.getAttribute('data-command-presentation')).toBe('checkbox-text-toggle')
    expect(positionToggle.getAttribute('data-logicflow-tooltip')).toBe('保存布局')
    expect(positionToggle.getAttribute('data-command-pressed')).toBe('false')
    expect(positionToggle.getAttribute('aria-checked')).toBe('false')
    expect(positionToggle.querySelector('.toolbar-checkbox-entry-box')).toBeTruthy()
    expect(positionToggle.querySelector('.fa-thumbtack')).toBeFalsy()

    await harness.click(positionToggle)
    await harness.waitFor(() => positionToggle.getAttribute('data-command-pressed') === 'true')
    expect(positionToggle.getAttribute('aria-checked')).toBe('true')
  } finally {
    await harness.unmount()
  }
})

test('position persistence toolbar entry auto-checks when flow data contains persisted node positions', async () => {
  const harness = await createLogicflowEditorHarness({
    initialFlowData: createLinearFixture(),
  }).mount()

  try {
    const positionToggle = harness.query('[data-command-id="toggle-position-persistence"]')

    expect(positionToggle.getAttribute('data-command-pressed')).toBe('false')
    expect(positionToggle.getAttribute('aria-checked')).toBe('false')

    await harness.replaceFlowData({
      ...createLinearFixture(),
      nodePositions: {
        脚本节点: { x: 620, y: 240 },
      },
    })

    await harness.waitFor(() => positionToggle.getAttribute('data-command-pressed') === 'true')
    expect(positionToggle.getAttribute('aria-checked')).toBe('true')
  } finally {
    await harness.unmount()
  }
})
