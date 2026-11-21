function buildUniqueNameValidator(currentGraph, currentNode) {
  return (name) => {
    const allNames = (currentGraph?.getNodes?.() || [])
      .filter((node) => node !== currentNode)
      .map((node) => node.data?.name)
    return !allNames.includes(name)
  }
}

function compactFormItems(items) {
  return items.filter(Boolean)
}

function normalizeText(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null
}

function normalizeInteger(value) {
  return Number.isInteger(value) ? value : null
}

function normalizeNodeNames(entry = {}, nodeName = null) {
  if (Array.isArray(entry?.nodeNames)) {
    return entry.nodeNames
      .map((name) => normalizeText(name))
      .filter(Boolean)
  }
  return nodeName ? [nodeName] : []
}

function normalizeInspectorValidationEntries(entries = []) {
  return entries
    .map((entry) => {
      const message = normalizeText(entry?.message)
      if (!message) {
        return null
      }

      const nodeName = normalizeText(entry?.nodeName)
      const nodeNames = normalizeNodeNames(entry, nodeName)

      return {
        ...entry,
        message,
        nodeName: nodeName || nodeNames[0] || null,
        nodeNames,
        scope: normalizeText(entry?.scope),
        fieldPath: normalizeText(entry?.fieldPath),
        portKind: normalizeText(entry?.portKind),
        portIndex: normalizeInteger(entry?.portIndex),
        sourceNode: normalizeText(entry?.sourceNode),
        sourceOutputPort: normalizeInteger(entry?.sourceOutputPort),
        targetNode: normalizeText(entry?.targetNode),
        targetInputPort: normalizeInteger(entry?.targetInputPort),
      }
    })
    .filter(Boolean)
}

function resolveInspectorValidationEntries(getValidationEntries) {
  if (typeof getValidationEntries !== 'function') {
    return []
  }

  return normalizeInspectorValidationEntries(getValidationEntries())
}

function collectFieldNames(items, names = new Set()) {
  if (Array.isArray(items)) {
    items.forEach((item) => collectFieldNames(item, names))
    return names
  }

  if (!items || typeof items !== 'object') {
    return names
  }

  const name = normalizeText(items.name)
  if (name) {
    names.add(name)
  }

  collectFieldNames(items.body, names)
  collectFieldNames(items.items, names)
  collectFieldNames(items.columns, names)
  collectFieldNames(items.tabs, names)

  return names
}

function collectFormFieldIndex(items, fieldIndex = { names: new Set(), nestedNamesByRoot: new Map() }) {
  if (Array.isArray(items)) {
    items.forEach((item) => collectFormFieldIndex(item, fieldIndex))
    return fieldIndex
  }

  if (!items || typeof items !== 'object') {
    return fieldIndex
  }

  const name = normalizeText(items.name)
  if (name) {
    fieldIndex.names.add(name)
  }

  if (name && items.type === 'combo') {
    fieldIndex.nestedNamesByRoot.set(name, collectFieldNames(items.items))
  }

  collectFormFieldIndex(items.body, fieldIndex)
  if (items.type !== 'combo') {
    collectFormFieldIndex(items.items, fieldIndex)
  }
  collectFormFieldIndex(items.columns, fieldIndex)
  collectFormFieldIndex(items.tabs, fieldIndex)

  return fieldIndex
}

function parseFieldPath(fieldPath) {
  const normalizedFieldPath = normalizeText(fieldPath)
  if (!normalizedFieldPath) {
    return []
  }
  return normalizedFieldPath.split(/[.[\]]/).filter(Boolean)
}

function fieldPathExists(fieldPath, formFieldIndex) {
  const normalizedFieldPath = normalizeText(fieldPath)
  if (!normalizedFieldPath) {
    return false
  }

  if (formFieldIndex.names.has(normalizedFieldPath)) {
    return true
  }

  const fieldPathParts = parseFieldPath(normalizedFieldPath)
  if (!fieldPathParts.length) {
    return false
  }

  const [rootFieldPath] = fieldPathParts
  if (fieldPathParts.length === 1) {
    return formFieldIndex.names.has(rootFieldPath)
  }

  const nestedNames = formFieldIndex.nestedNamesByRoot.get(rootFieldPath)
  if (!nestedNames) {
    return false
  }

  const leafFieldPath = fieldPathParts[fieldPathParts.length - 1]
  return nestedNames.has(leafFieldPath)
}

function matchesEdgeIdentity(entry, edgeData = {}) {
  return (
    entry.sourceNode === normalizeText(edgeData?.sourceNode) &&
    entry.sourceOutputPort === normalizeInteger(edgeData?.sourceOutputPort) &&
    entry.targetNode === normalizeText(edgeData?.targetNode) &&
    entry.targetInputPort === normalizeInteger(edgeData?.targetInputPort)
  )
}

function isEntryRelevantToPanel({ entry, targetKind, targetName = null, edgeData = null }) {
  if (targetKind === 'flow') {
    return entry.scope === 'flow' || (!entry.scope && entry.nodeName == null)
  }

  if (targetKind === 'node') {
    if (entry.scope === 'node') {
      return targetName != null && entry.nodeNames.includes(targetName)
    }
    return !entry.scope && targetName != null && entry.nodeName === targetName
  }

  if (targetKind === 'edge') {
    return entry.scope === 'edge' && matchesEdgeIdentity(entry, edgeData)
  }

  return false
}

function canUseFieldError({ entry, targetKind, targetName = null, formFieldIndex }) {
  if (!fieldPathExists(entry.fieldPath, formFieldIndex)) {
    return false
  }

  if (targetKind === 'flow' && entry.fieldPath === 'entryNode') {
    return isEntryNodeReferenceMissingDiagnostic(entry)
  }

  if (targetKind === 'node' && entry.scope === 'node') {
    return targetName != null && entry.nodeNames.length === 1 && entry.nodeNames[0] === targetName
  }

  return true
}

function isEntryNodeReferenceMissingDiagnostic(entry = {}) {
  const message = normalizeText(entry.message)
  if (!message) {
    return false
  }

  return message.includes('入口节点') && message.includes('未找到')
}

function isReturnNodeData(nodeData = {}) {
  return nodeData?.type === 'return'
}

function flowHasReturnNode(graph) {
  return (graph?.getNodes?.() || []).some((node) => {
    const nodeData = node?.getData?.() || node?.data || {}
    return isReturnNodeData(nodeData)
  })
}

function filterFlowFormItems(defaultFormItems = [], graph, flowProps = {}) {
  if (flowProps?.exitNode || !flowHasReturnNode(graph)) {
    return defaultFormItems
  }

  return defaultFormItems.filter((item) => item?.title !== '输出')
}

export function buildPropertyPanelValidationState({
  targetKind,
  targetName = null,
  edgeData = null,
  validationEntries = [],
  formItems = [],
}) {
  const normalizedTargetName = normalizeText(targetName)
  const formFieldIndex = collectFormFieldIndex(formItems)
  const fieldErrors = {}
  const panelEntries = []

  validationEntries.forEach((entry) => {
    if (!isEntryRelevantToPanel({
      entry,
      targetKind,
      targetName: normalizedTargetName,
      edgeData,
    })) {
      return
    }

    if (entry.fieldPath && canUseFieldError({
      entry,
      targetKind,
      targetName: normalizedTargetName,
      formFieldIndex,
    })) {
      if (!fieldErrors[entry.fieldPath]) {
        fieldErrors[entry.fieldPath] = []
      }
      fieldErrors[entry.fieldPath].push(entry.message)
      return
    }

    panelEntries.push(entry)
  })

  return {
    fieldErrors,
    panelEntries,
  }
}

function createPropertyPanelValidationAlert(panelEntries) {
  if (!panelEntries.length) {
    return null
  }

  return {
    type: 'alert',
    level: 'danger',
    title: '校验错误',
    showIcon: true,
    label: false,
    className: 'logicflow-property-validation-alert',
    body:
      panelEntries.length === 1
        ? panelEntries[0].message
        : {
            type: 'container',
            className: 'logicflow-property-validation-alert-list',
            body: panelEntries.map((entry, index) => ({
              type: 'tpl',
              tpl: `${index + 1}. ${entry.message}`,
              className: 'logicflow-property-validation-alert-item',
            })),
          },
  }
}

function collectSelectFieldSummaries(fields, path = 'root', summaries = []) {
  if (Array.isArray(fields)) {
    fields.forEach((field, index) => {
      collectSelectFieldSummaries(field, `${path}[${index}]`, summaries)
    })
    return summaries
  }

  if (!fields || typeof fields !== 'object') {
    return summaries
  }

  if (fields.type === 'select') {
    summaries.push({
      path,
      name: fields.name,
      source: fields.source,
      searchable: fields.searchable,
      optionsCount: Array.isArray(fields.options) ? fields.options.length : null,
    })
  }

  if (fields.body) {
    collectSelectFieldSummaries(fields.body, `${path}.body`, summaries)
  }

  if (fields.items) {
    collectSelectFieldSummaries(fields.items, `${path}.items`, summaries)
  }

  if (fields.columns) {
    collectSelectFieldSummaries(fields.columns, `${path}.columns`, summaries)
  }

  return summaries
}

export function buildFlowPanelConfig({ graph, getFlowDefaultProperties, wrapData, getValidationEntries }) {
  const flowProps = graph.getLogicFlowProps()
  const defaultFormItems = filterFlowFormItems(getFlowDefaultProperties(), graph, flowProps)
  const validationState = buildPropertyPanelValidationState({
    targetKind: 'flow',
    validationEntries: resolveInspectorValidationEntries(getValidationEntries),
    formItems: defaultFormItems,
  })
  const formItems = compactFormItems([
    ...defaultFormItems,
    createPropertyPanelValidationAlert(validationState.panelEntries),
  ])
  const wrappedData = wrapData(flowProps)

  console?.debug?.('[logicflow] propertyPanel.flowConfig', {
    flowName: flowProps?.name,
    formItemCount: Array.isArray(formItems) ? formItems.length : null,
    selectFields: collectSelectFieldSummaries(formItems),
    wrappedDataKeys: Object.keys(wrappedData || {}),
    entryNodeCount: Array.isArray(wrappedData?.__ENTRY_NODES) ? wrappedData.__ENTRY_NODES.length : null,
    entryNodeOptions: Array.isArray(wrappedData?.__ENTRY_NODES)
      ? wrappedData.__ENTRY_NODES.map((option) => ({
          label: option?.label,
          value: option?.value,
        }))
      : [],
  })

  return {
    panelTitle: '流程属性',
    runtimeKey: 'flow',
    formItems,
    fieldErrors: validationState.fieldErrors,
    data: wrappedData,
    saveAction(value) {
      graph.updateLogicFlowProps({
        ...(graph.getLogicFlowProps?.() || {}),
        ...value,
      })
      graph.flushLogicFlowChange?.()
    },
  }
}

export function buildEdgePanelConfig({
  edge,
  graph,
  getEdgeDefaultProperties,
  wrapData,
  getValidationEntries,
}) {
  const data = edge.getData()
  const defaultFormItems = getEdgeDefaultProperties()
  const validationState = buildPropertyPanelValidationState({
    targetKind: 'edge',
    edgeData: data,
    validationEntries: resolveInspectorValidationEntries(getValidationEntries),
    formItems: defaultFormItems,
  })
  return {
    panelTitle: '连线属性',
    runtimeKey: [
      'edge',
      data?.sourceNode ?? '',
      data?.sourceOutputPort ?? '',
      data?.targetNode ?? '',
      data?.targetInputPort ?? '',
    ].join(':'),
    formItems: compactFormItems([
      ...defaultFormItems,
      createPropertyPanelValidationAlert(validationState.panelEntries),
    ]),
    fieldErrors: validationState.fieldErrors,
    data: wrapData(data),
    saveAction(value) {
      edge.setData(value)
      graph?.flushLogicFlowChange?.()
    },
  }
}

export function buildNodePanelConfig({
  node,
  currentGraph,
  getNodeDefaultProperties,
  transpileAll,
  wrapData,
  getValidationEntries,
}) {
  const nodeDefine = node.attr('meta/nodeDefine')
  const data = node.getData()
  const nodeProperties = nodeDefine.properties || []
  const portRules = nodeDefine?.portRules
  const { input: inputPortRules = {}, output: outputPortRules = {} } = portRules || {}
  const defaultFormItems = getNodeDefaultProperties()
  const nodePropertiesBody = transpileAll(nodeProperties)
  const nodePortsBody = transpileAll([
    {
      type: 'ports-editor',
      props: { ...inputPortRules, name: 'inputPorts', title: '输入端口' },
    },
    {
      type: 'ports-editor',
      props: { ...outputPortRules, name: 'outputPorts', title: '输出端口' },
    },
  ])
  const validationState = buildPropertyPanelValidationState({
    targetKind: 'node',
    targetName: data?.name,
    validationEntries: resolveInspectorValidationEntries(getValidationEntries),
    formItems: [
      ...defaultFormItems,
      ...nodePropertiesBody,
      ...nodePortsBody,
    ],
  })
  const formItems = compactFormItems([
    ...defaultFormItems,
    nodeProperties.length
      ? {
          type: 'fieldSet',
          title: '节点属性',
          collapsable: true,
          collapsed: false,
          className: 'mb-4',
          body: nodePropertiesBody,
        }
      : null,
    {
      type: 'container',
      body: nodePortsBody,
    },
    createPropertyPanelValidationAlert(validationState.panelEntries),
  ])
  const wrappedData = wrapData(data, {
    __validate_unique_name: buildUniqueNameValidator(currentGraph, node),
    __CURRENT_NODE_NAME: data.name,
    __CURRENT_NODE_TYPE: nodeDefine?.type || data.type,
    __CURRENT_NODE_LABEL: data.label || nodeDefine?.label || data.name,
  })

  console?.debug?.('[logicflow] propertyPanel.nodeConfig', {
    nodeName: data?.name,
    nodeType: nodeDefine?.type || data?.type,
    nodePropertyCount: nodeProperties.length,
    nodePropertyKeys: nodeProperties.map((property) => property?.name || property?.type),
    formItemCount: formItems.length,
    selectFields: collectSelectFieldSummaries(formItems),
    wrappedDataKeys: Object.keys(wrappedData || {}),
    entryNodeCount: Array.isArray(wrappedData?.__ENTRY_NODES)
      ? wrappedData.__ENTRY_NODES.length
      : null,
    entryNodeOptions: Array.isArray(wrappedData?.__ENTRY_NODES)
      ? wrappedData.__ENTRY_NODES.map((option) => ({
          label: option?.label,
          value: option?.value,
        }))
      : [],
  })

  return {
    panelTitle: '节点属性',
    runtimeKey: `node:${node.id}`,
    formItems,
    fieldErrors: validationState.fieldErrors,
    data: wrappedData,
    saveAction(value) {
      node.setData(value, { overwrite: true })
      currentGraph?.flushLogicFlowChange?.()
    },
  }
}
