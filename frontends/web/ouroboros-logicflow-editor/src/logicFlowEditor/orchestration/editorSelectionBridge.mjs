import { navigateToValidationNode as navigateToValidationNodeDefault } from './validationDiagnosticNavigation.mjs'

function isSingleSelectedCell(graph, target) {
  const selectedCells = graph?.getSelectedCells?.() || []
  return selectedCells.length === 1 && selectedCells[0] === target
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

export function createEditorSelectionBridge({
  showPropertiesPanel,
  getCurrentGraph,
  getValidationEntries = () => [],
  getFlowDefaultProperties,
  getNodeDefaultProperties,
  getEdgeDefaultProperties,
  transpileAll,
  wrapData,
  buildFlowPanelConfigFn,
  buildNodePanelConfigFn,
  buildEdgePanelConfigFn,
  navigateToValidationNodeFn = navigateToValidationNodeDefault,
}) {
  async function showLogicFlowProperties(graph) {
    graph.cleanSelection()
    return showPropertiesPanel(() =>
      buildFlowPanelConfigFn({
        graph,
        getValidationEntries,
        getFlowDefaultProperties,
        wrapData,
      })
    )
  }

  async function showNodeProperties(node, graph) {
    if (graph.getSelectedCells().length > 1) return

    if (!isSingleSelectedCell(graph, node)) {
      graph.select(node, { highlight: true })
    }
    const configFactory = () =>
      buildNodePanelConfigFn({
        node,
        currentGraph: getCurrentGraph() || graph,
        getValidationEntries,
        getNodeDefaultProperties,
        transpileAll,
        wrapData,
      })
    const nodeData = node?.getData?.() || {}
    const config = configFactory()

    console?.debug?.('[logicflow] selectionBridge.nodePanelConfig', {
      nodeName: nodeData?.name,
      nodeType: nodeData?.type,
      formItemCount: Array.isArray(config.formItems) ? config.formItems.length : null,
      dataKeys: Object.keys(config.data || {}),
      entryNodeCount: Array.isArray(config.data?.__ENTRY_NODES)
        ? config.data.__ENTRY_NODES.length
        : null,
      entryNodeOptions: Array.isArray(config.data?.__ENTRY_NODES)
        ? config.data.__ENTRY_NODES.map((option) => ({
            label: option?.label,
            value: option?.value,
          }))
        : [],
      selectFields: collectSelectFieldSummaries(config.formItems),
    })

    return showPropertiesPanel(configFactory)
  }

  async function showEdgeProperties(edge, graph) {
    if (!isSingleSelectedCell(graph, edge)) {
      graph.select(edge, { highlight: true })
    }
    return showPropertiesPanel(() =>
      buildEdgePanelConfigFn({
        edge,
        graph,
        getValidationEntries,
        getEdgeDefaultProperties,
        wrapData,
      })
    )
  }

  function navigateToValidationNode(nodeName, graph = null) {
    return navigateToValidationNodeFn({
      nodeName,
      graph: getCurrentGraph() || graph,
      showNodeProperties,
    })
  }

  return {
    showLogicFlowProperties,
    showNodeProperties,
    showEdgeProperties,
    navigateToValidationNode,
  }
}
