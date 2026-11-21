export async function navigateToValidationNode({
  nodeName,
  graph,
  showNodeProperties,
}) {
  const nodes = graph?.getNodes?.() || []
  const node = nodes.find((currentNode) => resolveNodeName(currentNode) === nodeName)

  if (!node) {
    return {
      ok: false,
      reason: 'node_not_found',
      message: `无法定位节点：节点可能已不存在（${nodeName}）`,
    }
  }

  graph?.cleanSelection?.()
  if (typeof graph?.select === 'function') {
    graph.select(node, { highlight: true })
    graph?.refreshLogicFlowValidationHighlights?.()
  } else {
    await showNodeProperties(node, graph)
  }

  return {
    ok: true,
    node,
    centered: centerNode(graph, node),
  }
}

function resolveNodeName(node) {
  if (!node) return null
  if (node.data?.name) return node.data.name
  return node.getData?.()?.name || null
}

function centerNode(graph, node) {
  if (typeof graph?.centerCell === 'function') {
    graph.centerCell(node)
    return true
  }
  if (typeof graph?.scrollToCell === 'function') {
    graph.scrollToCell(node)
    return true
  }
  return false
}
