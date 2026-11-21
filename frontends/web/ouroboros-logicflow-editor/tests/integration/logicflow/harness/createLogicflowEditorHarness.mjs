import { act } from 'react'
import React, { createRef } from 'react'
import { createRoot } from 'react-dom/client'
import { initAmisComponentLib } from 'ouroboros-sdk'
import LogicFlowEditor from '../../../../src/logicFlowEditor/index.jsx'
import { createX6Edge, createX6Node, getUniqueName } from '../../../../src/node/index.js'
import { transpileToLogicFlow } from '../../../../src/node/transpile.js'
import createStoreFn from '../../../../src/store/index.js'
import { initPlatformUtils } from '../../../../src/utils/index.js'
import { createFrontendTestPlatform } from './platform/createFrontendTestPlatform.mjs'
import { createMountMicroFrontendHarness } from './platform/mountMicroFrontendHarness.mjs'
import { createMockNodeDefines } from './mockNodeDefines.mjs'
import { ensureSvgGeometryPolyfills } from './ensureSvgGeometryPolyfills.mjs'

ensureSvgGeometryPolyfills()

const HARNESS_DEBUG_ENABLED = process.env.LOGICFLOW_HARNESS_DEBUG === '1'

function debugHarness(label, payload = null) {
  if (!HARNESS_DEBUG_ENABLED) {
    return
  }
  const resolvedPayload = typeof payload === 'function' ? payload() : payload
  if (payload == null) {
    console.error(`[logicflow-harness] ${label}`)
    return
  }
  console.error(`[logicflow-harness] ${label}`, resolvedPayload)
}

function summarizeFlowData(flowData) {
  if (!flowData) {
    return null
  }
  return {
    name: flowData.name,
    entryNode: flowData.entryNode,
    exitNode: flowData.exitNode,
    errorEntryNode: flowData.errorEntryNode,
    nodeNames: (flowData.nodes || []).map((node) => node?.name),
    connections: (flowData.connections || []).map((connection) => ({
      sourceNode: connection?.sourceNode,
      sourceOutputPort: connection?.sourceOutputPort,
      targetNode: connection?.targetNode,
      targetInputPort: connection?.targetInputPort,
    })),
    nodePositionKeys: Object.keys(flowData.nodePositions || {}),
  }
}

function summarizeGraphEvents(events = []) {
  return events.map((event) => ({
    type: event.type,
    data: summarizeFlowData(event.data),
    currentNodeName: event.current?.name || null,
    previousNodeName: event.previous?.name || null,
    currentSourceNode: event.current?.sourceNode || null,
    currentTargetNode: event.current?.targetNode || null,
    previousSourceNode: event.previous?.sourceNode || null,
    previousTargetNode: event.previous?.targetNode || null,
    message: event.message || null,
    errors: event.errors || null,
    nodeName: event.node?.getData?.()?.name || event.node?.data?.name || null,
    edgeData: event.edge?.getData?.() || event.edge?.data || null,
  }))
}

function isDeadFormStoreError(error) {
  return String(error?.message || '').includes('no longer part of a state tree')
}

function readFormDataSafely(component) {
  try {
    return component?.getData?.() || null
  } catch (error) {
    if (isDeadFormStoreError(error)) {
      return null
    }
    throw error
  }
}

function resolveNodeName(node) {
  return node?.getData?.()?.name || node?.data?.name || null
}

function matchesConnection(edge, connectionSpec = {}) {
  const data = edge?.getData?.() || edge?.data || {}
  return (
    data.sourceNode === connectionSpec.sourceNode &&
    data.sourceOutputPort === connectionSpec.sourceOutputPort &&
    data.targetNode === connectionSpec.targetNode &&
    data.targetInputPort === connectionSpec.targetInputPort
  )
}

function createNodeDefineFetcher(nodeDefines) {
  return async () => ({
    status: 0,
    data: nodeDefines,
  })
}

async function setControlValue(control, value) {
  const prototype =
    control instanceof HTMLTextAreaElement
      ? HTMLTextAreaElement.prototype
      : HTMLInputElement.prototype
  const descriptor = Object.getOwnPropertyDescriptor(prototype, 'value')

  await act(async () => {
    control.focus?.()
    descriptor?.set?.call(control, value)
    control.dispatchEvent(new Event('input', { bubbles: true }))
    control.dispatchEvent(new Event('change', { bubbles: true }))
    await Promise.resolve()
  })
}

function requireGraph(editorVm) {
  const graph = editorVm?.getGraph?.()
  if (!graph) {
    throw new Error('LogicFlow graph is not ready')
  }
  return graph
}

function findNodeByName(graph, nodeName) {
  const node = (graph.getNodes?.() || []).find((item) => resolveNodeName(item) === nodeName)
  if (!node) {
    throw new Error(`LogicFlow node not found: ${nodeName}`)
  }
  return node
}

function findEdgeByConnection(graph, connectionSpec) {
  const edge = (graph.getEdges?.() || []).find((item) => matchesConnection(item, connectionSpec))
  if (!edge) {
    throw new Error(`LogicFlow edge not found: ${JSON.stringify(connectionSpec)}`)
  }
  return edge
}

function findNodeDefineByType(nodeDefines, nodeType) {
  const nodeDefine = (nodeDefines || []).find((item) => item?.type === nodeType)
  if (!nodeDefine) {
    throw new Error(`LogicFlow node define not found: ${nodeType}`)
  }
  return nodeDefine
}

function describeComponent(component) {
  if (!component) {
    return null
  }
  const props = component?.props || {}
  const options = Array.isArray(props.options)
    ? props.options.map((option) => ({
        label: option?.label ?? null,
        value: option?.value ?? null,
      }))
    : null
  return {
    type: props.type || null,
    name: props.name || null,
    id: props.id || null,
    source: props.source || null,
    searchable: typeof props.searchable === 'boolean' ? props.searchable : null,
    optionCount: Array.isArray(props.options) ? props.options.length : null,
    options,
    dataKeys: props.data && typeof props.data === 'object' ? Object.keys(props.data) : null,
    hasSetData: typeof component?.setData === 'function',
    hasGetData: typeof component?.getData === 'function',
    hasSubmit: typeof component?.submit === 'function',
    hasDoAction: typeof component?.doAction === 'function',
    hasFlush: typeof component?.flush === 'function',
    hasHandleBulkChange: typeof component?.handleBulkChange === 'function',
    hasHandleInputChange: typeof component?.handleInputChange === 'function',
    hasHandleChange: typeof component?.handleChange === 'function',
    hasFlushChange: typeof component?.flushChange === 'function',
    hasSetValue: typeof component?.setValue === 'function',
    hasOnSubmit: typeof component?.props?.onSubmit === 'function',
  }
}

function describeScope(scope) {
  if (!scope) {
    return null
  }
  const components = typeof scope.getComponents === 'function' ? scope.getComponents() : []
  return {
    rendererType: scope?.rendererType || null,
    componentCount: components.length,
    components: components.map(describeComponent),
  }
}

function resolveNodeNameForCreation(graph, requestedName, fallbackName) {
  const allNames = (graph.getNodes?.() || []).map(resolveNodeName).filter(Boolean)
  const baseName = requestedName || fallbackName || '节点'
  if (!allNames.includes(baseName)) {
    return baseName
  }
  return getUniqueName(baseName, allNames, false) || `${baseName}1`
}

function readDomControlValues(formState, fieldNames = []) {
  return Object.fromEntries(
    fieldNames.map((name) => {
      const control =
        formState.query(`input[name="${name}"]`) || formState.query(`textarea[name="${name}"]`)
      return [name, control?.value ?? null]
    })
  )
}

function resolvePort(node, group, index, label) {
  const ports = node.getPortsByGroup?.(group) || []
  const port = ports[index]
  if (!port) {
    throw new Error(`LogicFlow ${label} port not found: ${index}`)
  }
  return port
}

class HarnessRenderErrorBoundary extends React.Component {
  constructor(props) {
    super(props)
    this.state = {
      error: null,
    }
  }

  static getDerivedStateFromError(error) {
    return {
      error,
    }
  }

  componentDidCatch(error, errorInfo) {
    this.props.onError?.(error, errorInfo)
  }

  render() {
    if (this.state.error) {
      return null
    }
    return this.props.children
  }
}

export function createLogicflowEditorHarness({
  initialFlowData,
  initialValidationSnapshot = null,
  nodeDefines = createMockNodeDefines(),
  initialReadOnly = false,
} = {}) {
  const platform = createFrontendTestPlatform()
  const domHarness = createMountMicroFrontendHarness()

  let reactRoot = null
  let editorRef = null
  let store = null
  let editorVm = null
  let latestFlowData = initialFlowData
  let latestValidationSnapshot = initialValidationSnapshot
  const graphEvents = []
  const mountErrors = []

  const editorProps = {
    nodeDefineApi: 'mock://logicflow-editor/node-defines',
    disableBuiltIn: true,
    env: {
      fetcher: createNodeDefineFetcher(nodeDefines),
    },
    data: {
      appName: 'logicflow-editor-integration',
    },
  }

  const spriteNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  spriteNode.setAttribute('id', '__SVG_SPRITE_NODE__')
  spriteNode.style.display = 'none'
  domHarness.container.appendChild(spriteNode)

  const mountTarget = document.createElement('div')
  mountTarget.style.width = '1440px'
  mountTarget.style.height = '900px'
  domHarness.container.appendChild(mountTarget)

  async function flush(cycles = 2) {
    for (let index = 0; index < cycles; index += 1) {
      await domHarness.flush()
    }
  }

  async function click(target) {
    if (!target) {
      throw new Error('Cannot click an empty target')
    }

    target.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }))
    await flush(2)
  }

  async function waitFor(predicate, { timeoutMs = 4000, intervalMs = 20 } = {}) {
    const start = Date.now()
    while (Date.now() - start < timeoutMs) {
      const result = predicate()
      if (result) {
        return result
      }
      await new Promise((resolve) => setTimeout(resolve, intervalMs))
    }
    throw new Error('Timed out waiting for integration harness condition')
  }

  function readFlowData() {
    return latestFlowData || store?.state?.flowData || null
  }

  function readGraphFlowData() {
    const graph = editorVm?.getGraph?.()
    if (!graph) {
      return null
    }
    const props = graph.getLogicFlowProps?.() || {}
    const x6Data = (graph.getCells?.() || []).map((cell) => cell.toJSON?.() || cell)
    return transpileToLogicFlow(x6Data, props, true)
  }

  function readPropertiesPanelScope() {
    return editorVm?.getPropertiesPanelScope?.() || null
  }

  function readRenderedFormState() {
    const formElement = editorVm?.getPropertiesPanelElement?.()
    return {
      element: () => formElement,
      text: () => formElement?.textContent || '',
      query: (selector) => formElement?.querySelector?.(selector) || null,
      queryAll: (selector) => Array.from(formElement?.querySelectorAll?.(selector) || []),
    }
  }

  function readPropertiesForm() {
    const scopeRef = readPropertiesPanelScope()?.getScopeRef?.() || null
    const formComponent = scopeRef?.getComponentByName?.('propertyForm') || null
    debugHarness('readPropertiesForm', () => ({
      scope: describeScope(scopeRef),
      form: describeComponent(formComponent),
    }))
    return formComponent
  }

  function readPropertiesControl(name) {
    const scopeRef = readPropertiesPanelScope()?.getScopeRef?.() || null
    const formComponent = scopeRef?.getComponentByName?.('propertyForm') || null
    const controlComponent =
      scopeRef?.getComponentByName?.(`propertyForm.${name}`) ||
      formComponent?.context?.getComponentByName?.(name) ||
      scopeRef?.getComponentByName?.(name) ||
      null
    debugHarness('readPropertiesControl', () => ({
      name,
      control: describeComponent(controlComponent),
    }))
    return controlComponent
  }

  async function settlePropertyPanel() {
    await flush(4)
    await new Promise((resolve) => setTimeout(resolve, 20))
    await flush(2)
  }

  async function waitForFormTargets(values = {}) {
    const fieldNames = Object.keys(values)
    if (!fieldNames.length) {
      return
    }
    await waitFor(() => {
      const formState = readRenderedFormState()
      const hasAllFieldTargets = fieldNames.every(
        (name) =>
          readPropertiesControl(name) ||
          formState.query(`input[name="${name}"]`) ||
          formState.query(`textarea[name="${name}"]`)
      )
      if (hasAllFieldTargets) {
        return true
      }
      return false
    })
  }

  async function mount() {
    await initAmisComponentLib({}).mount(platform)
    initPlatformUtils(platform.__utils__)

    store = createStoreFn(initialFlowData)
    if (initialReadOnly) {
      store.commit('changeIsReadOnly', true)
    }
    latestFlowData = store.state.flowData
    if (store?.commit) {
      const originalCommit = store.commit.bind(store)
      store.commit = (type, payload, options) => {
        debugHarness('store:commit', {
          type,
          payload: summarizeFlowData(payload),
        })
        return originalCommit(type, payload, options)
      }
    }

    const handleWindowError = (event) => {
      const error = event?.error || event
      mountErrors.push({
        type: 'window-error',
        name: error?.name || null,
        message: error?.message || String(error),
        stack: error?.stack || null,
      })
    }
    const handleWindowUnhandledRejection = (event) => {
      const reason = event?.reason
      mountErrors.push({
        type: 'unhandled-rejection',
        name: reason?.name || null,
        message: reason?.message || String(reason),
        stack: reason?.stack || null,
      })
    }

    window.addEventListener('error', handleWindowError)
    window.addEventListener('unhandledrejection', handleWindowUnhandledRejection)

    reactRoot = createRoot(mountTarget)
    editorRef = createRef()
    await act(async () => {
      reactRoot.render(
        React.createElement(
          HarnessRenderErrorBoundary,
          {
            onError(error, errorInfo) {
              mountErrors.push({
                type: 'react-render-error',
                name: error?.name || null,
                message: error?.message || String(error),
                stack: error?.stack || null,
                componentStack: errorInfo?.componentStack || null,
              })
            },
          },
          React.createElement(LogicFlowEditor, {
            ref: editorRef,
            onChange(value) {
              debugHarness('app:onChange', () => summarizeFlowData(value))
              latestFlowData = value
              store.commit('changeFlowData', value)
            },
            getProps() {
              return {
                ...editorProps,
                validationSnapshot: latestValidationSnapshot,
                store,
              }
            },
            rootData: {
              appName: 'logicflow-editor-integration',
            },
          })
        )
      )
      await Promise.resolve()
    })
    editorVm = editorRef.current

    if (editorVm?.showNodeProperties) {
      const originalShowNodeProperties = editorVm.showNodeProperties.bind(editorVm)
      editorVm.showNodeProperties = async (...args) => {
        debugHarness('editorVm:showNodeProperties', {
          nodeName: resolveNodeName(args[0]),
        })
        return originalShowNodeProperties(...args)
      }
    }

    domHarness.addCleanup(async () => {
      window.removeEventListener('error', handleWindowError)
      window.removeEventListener('unhandledrejection', handleWindowUnhandledRejection)
      if (reactRoot?.unmount) {
        await act(async () => {
          reactRoot.unmount()
          await Promise.resolve()
        })
      }
      editorVm = null
      editorRef = null
      reactRoot = null
    })

    await flush(4)
    try {
      await waitFor(() => {
        editorVm = editorRef.current
        return editorVm?.readFlowData?.()
      })
    } catch (error) {
      const flowData = editorRef.current?.readFlowData?.() || null
      const graph = editorRef.current?.getGraph?.() || null
      const debugSnapshot = {
        hasEditorRef: !!editorRef,
        hasEditorVm: !!editorRef.current,
        hasFlowData: !!flowData,
        hasGraph: !!graph,
        mountErrors,
        mountTargetHtml: mountTarget.innerHTML.slice(0, 400),
        containerHtml: domHarness.container.innerHTML.slice(0, 400),
      }
      throw new Error(
        `Timed out waiting for mounted editor state: ${JSON.stringify(debugSnapshot)}`,
        { cause: error }
      )
    }
    await waitFor(() => domHarness.container.querySelector('.logicflow-canvas-container'))
    await waitFor(() => {
      const graph = editorRef.current?.getGraph?.()
      if (!graph) {
        return false
      }
      const expectedNodeCount = (initialFlowData?.nodes || []).length
      const expectedEdgeCount = (initialFlowData?.connections || []).length
      return (
        (graph.getNodes?.().length || 0) >= expectedNodeCount &&
        (graph.getEdges?.().length || 0) >= expectedEdgeCount
      )
    })

    const graph = requireGraph(editorVm)
    if (graph?.updateLogicFlowData) {
      const originalUpdateLogicFlowData = graph.updateLogicFlowData.bind(graph)
      graph.updateLogicFlowData = (data) => {
        debugHarness('graph:updateLogicFlowData', summarizeFlowData(data))
        return originalUpdateLogicFlowData(data)
      }
    }
    if (graph?.updateLogicFlowProps) {
      const originalUpdateLogicFlowProps = graph.updateLogicFlowProps.bind(graph)
      graph.updateLogicFlowProps = (data) => {
        debugHarness('graph:updateLogicFlowProps', data)
        return originalUpdateLogicFlowProps(data)
      }
    }
    graph.on?.('node:change:data', ({ current, previous }) => {
      graphEvents.push({
        type: 'node:change:data',
        at: Date.now(),
        current,
        previous,
      })
    })
    graph.on?.('edge:change:data', ({ current, previous }) => {
      graphEvents.push({
        type: 'edge:change:data',
        at: Date.now(),
        current,
        previous,
      })
    })
    graph.on?.('node:selected', ({ node }) => {
      graphEvents.push({
        type: 'node:selected',
        at: Date.now(),
        node,
      })
    })
    graph.on?.('edge:selected', ({ edge }) => {
      graphEvents.push({
        type: 'edge:selected',
        at: Date.now(),
        edge,
      })
    })
    graph.on?.('logicflow:change:data', ({ data }) => {
      graphEvents.push({
        type: 'change',
        at: Date.now(),
        data,
      })
    })
    graph.on?.('logicflow:debug:triggerChange', ({ data }) => {
      graphEvents.push({
        type: 'debug:triggerChange',
        at: Date.now(),
        data,
      })
    })
    graph.on?.('logicflow:validateFailed', ({ data, message, errors }) => {
      graphEvents.push({
        type: 'validateFailed',
        at: Date.now(),
        data,
        message,
        errors,
      })
    })

    return api
  }

  async function openNodeProperties(nodeName) {
    const graph = requireGraph(editorVm)
    graph.select(findNodeByName(graph, nodeName), { highlight: true })
    await flush(4)
    await waitFor(() => readRenderedFormState().text().includes('节点属性'))
    await settlePropertyPanel()
  }

  async function openFlowProperties() {
    const graph = requireGraph(editorVm)
    await editorVm.showLogicFlowProperties(graph)
    await flush(4)
    await waitFor(() => readRenderedFormState().text().includes('输入'))
    await settlePropertyPanel()
  }

  async function openEdgeProperties(connectionSpec) {
    const graph = requireGraph(editorVm)
    graph.select(findEdgeByConnection(graph, connectionSpec), { highlight: true })
    await flush(4)
    await waitFor(() => readRenderedFormState().text().includes('标签'))
    await settlePropertyPanel()
  }

  async function addNode(nodeMeta = {}) {
    const graph = requireGraph(editorVm)
    const nodeDefine = findNodeDefineByType(editorVm?.getNodeDefines?.() || [], nodeMeta.type)
    const scaffold = nodeDefine.scaffold || { type: nodeMeta.type }
    const name = resolveNodeNameForCreation(
      graph,
      nodeMeta.name,
      scaffold.name || nodeDefine.label || nodeMeta.type
    )
    const x6Node = createX6Node(nodeDefine, {
      ...scaffold,
      ...nodeMeta,
      name,
    })
    const node = graph.createNode?.(x6Node)
    graph.addNode?.(node)
    if (nodeMeta.position?.x != null && nodeMeta.position?.y != null) {
      node?.position?.(nodeMeta.position.x, nodeMeta.position.y)
    }
    const result = { graph, node, name }
    await flush(4)
    return result
  }

  async function connect(connectionSpec) {
    const graph = requireGraph(editorVm)
    const source = findNodeByName(graph, connectionSpec.sourceNode)
    const target = findNodeByName(graph, connectionSpec.targetNode)
    const sourcePort = resolvePort(
      source,
      'out',
      connectionSpec.sourceOutputPort || 0,
      'source output'
    )
    const targetPort = resolvePort(
      target,
      'in',
      connectionSpec.targetInputPort || 0,
      'target input'
    )
    const edge = graph.createEdge?.(
      createX6Edge({
        source: {
          cell: source.id,
          port: sourcePort.id,
        },
        target: {
          cell: target.id,
          port: targetPort.id,
        },
      })
    )
    graph.addEdge?.(edge)
    graph.trigger?.('edge:connected', {
      edge,
      isNew: true,
    })
    const result = { graph, edge }
    await flush(4)
    return result
  }

  async function submitPropertyForm(values = {}) {
    await waitForFormTargets(values)

    const formState = readRenderedFormState()
    const propertyForm = readPropertiesForm()
    const initialFormData = readFormDataSafely(propertyForm) || {}
    const canUseDomControls = Object.keys(values).every(
      (name) =>
        formState.query(`input[name="${name}"]`) ||
        formState.query(`textarea[name="${name}"]`)
    )

    if (canUseDomControls) {
      debugHarness('submitPropertyForm:dom-path', () => ({
        values,
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      for (const [name, value] of Object.entries(values)) {
        const control =
          formState.query(`input[name="${name}"]`) ||
          formState.query(`textarea[name="${name}"]`)
        if (!control) {
          throw new Error(`Form control not found: ${name}`)
        }
        await setControlValue(control, value)
        await settlePropertyPanel()
      }

      return
    }

    if (propertyForm?.handleBulkChange) {
      const nextFormData = {
        ...initialFormData,
        ...values,
      }
      await act(async () => {
        propertyForm.handleBulkChange(nextFormData, true, { type: 'input' })
        await propertyForm.flush?.()
      })

      await flush(2)
      const latestPropertyForm = readPropertiesForm() || propertyForm
      debugHarness('submitPropertyForm:after-setData', () => ({
        initialForm: describeComponent(propertyForm),
        latestForm: describeComponent(latestPropertyForm),
        latestFormData: readFormDataSafely(latestPropertyForm),
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      await settlePropertyPanel()
      debugHarness('submitPropertyForm:after-submit', () => ({
        flowData: summarizeFlowData(readFlowData()),
        graphFlowData: summarizeFlowData(readGraphFlowData()),
        graphEvents: summarizeGraphEvents(graphEvents),
        domValues: readDomControlValues(readRenderedFormState(), Object.keys(values)),
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      return
    }

    debugHarness('submitPropertyForm:before', () => ({
      values,
      form: describeComponent(propertyForm),
      controls: Object.fromEntries(
        Object.keys(values).map((name) => [name, describeComponent(readPropertiesControl(name))])
      ),
      formData: initialFormData,
      domValues: readDomControlValues(formState, Object.keys(values)),
      domText: readRenderedFormState().text().slice(0, 240),
    }))
    const controlEntries = Object.entries(values).map(([name, value]) => [
      name,
      value,
      readPropertiesControl(name),
    ])
    const canUseControlPath = controlEntries.every(([, , control]) => control)

    if (canUseControlPath) {
      await act(async () => {
        for (let index = 0; index < controlEntries.length; index += 1) {
          const [name, value, control] = controlEntries[index]
          if (control?.handleInputChange) {
            await control.handleInputChange({ currentTarget: { value } })
            await Promise.resolve()
          } else if (control?.handleChange) {
            const shouldSubmit = index === controlEntries.length - 1
            control.handleChange(value, shouldSubmit, true)
            await Promise.resolve()
          } else if (control?.setValue) {
            control.setValue(value, name)
            await Promise.resolve()
          } else {
            throw new Error(`Property control does not support change API: ${name}`)
          }
        }
      })

      await settlePropertyPanel()
      debugHarness('submitPropertyForm:after-submit', () => ({
        flowData: summarizeFlowData(readFlowData()),
        graphFlowData: summarizeFlowData(readGraphFlowData()),
        graphEvents: summarizeGraphEvents(graphEvents),
        domValues: readDomControlValues(readRenderedFormState(), Object.keys(values)),
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      return
    }
    if (propertyForm?.setData && propertyForm?.props?.onSubmit) {
      const nextFormData = {
        ...initialFormData,
        ...values,
      }
      await act(async () => {
        propertyForm.setData(nextFormData)
        await Promise.resolve()
      })

      await flush(2)
      const latestPropertyForm = readPropertiesForm() || propertyForm
      debugHarness('submitPropertyForm:after-setData', () => ({
        initialForm: describeComponent(propertyForm),
        latestForm: describeComponent(latestPropertyForm),
        latestFormData: readFormDataSafely(latestPropertyForm),
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      await act(async () => {
        await latestPropertyForm.props.onSubmit(nextFormData)
      })

      await settlePropertyPanel()
      debugHarness('submitPropertyForm:after-submit', () => ({
        flowData: summarizeFlowData(readFlowData()),
        graphFlowData: summarizeFlowData(readGraphFlowData()),
        graphEvents: summarizeGraphEvents(graphEvents),
        domText: readRenderedFormState().text().slice(0, 240),
      }))

      return
    }

    for (const [name, value] of Object.entries(values)) {
      const control =
        formState.query(`input[name="${name}"]`) ||
        formState.query(`textarea[name="${name}"]`)
      if (!control) {
        throw new Error(`Form control not found: ${name}`)
      }
      await setControlValue(control, value)
      await settlePropertyPanel()
    }

  }

  async function replaceFlowData(nextFlowData) {
    await act(async () => {
      latestFlowData = nextFlowData
      store.commit('changeFlowData', nextFlowData)
      await Promise.resolve()
    })
    await flush(4)
  }

  async function replaceValidationSnapshot(nextValidationSnapshot) {
    await act(async () => {
      latestValidationSnapshot = nextValidationSnapshot
      reactRoot.render(
        React.createElement(LogicFlowEditor, {
          ref: editorRef,
          onChange(value) {
            debugHarness('app:onChange', () => summarizeFlowData(value))
            latestFlowData = value
            store.commit('changeFlowData', value)
          },
          getProps() {
            return {
              ...editorProps,
              validationSnapshot: latestValidationSnapshot,
              store,
            }
          },
          rootData: {
            appName: 'logicflow-editor-integration',
          },
        })
      )
      await Promise.resolve()
    })
    await flush(4)
  }

  async function unmount() {
    await act(async () => {
      await editorVm?.disposePropertiesPanel?.()
      await Promise.resolve()
    })
    await flush(4)
    await new Promise((resolve) => setTimeout(resolve, 20))
    await act(async () => {
      await domHarness.destroy()
      await Promise.resolve()
    })
    await flush(4)
  }

  const api = {
    mount,
    flush,
    waitFor,
    getGraph() {
      return requireGraph(editorVm)
    },
    openNodeProperties,
    openFlowProperties,
    openEdgeProperties,
    addNode,
    connect,
    readFlowData,
    readGraphFlowData,
    readPropertiesPanelScope,
    readPropertiesForm,
    readPropertiesControl,
    readRenderedFormState,
    submitPropertyForm,
    replaceFlowData,
    replaceValidationSnapshot,
    query(selector) {
      return domHarness.container.querySelector(selector)
    },
    queryAll(selector) {
      return Array.from(domHarness.container.querySelectorAll(selector))
    },
    unmount,
    click,
    toastMessages: platform.toastMessages,
    graphEvents,
  }

  return api
}
