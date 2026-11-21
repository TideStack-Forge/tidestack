export function createEditorInteractionActionsBridge({
  confirmFn,
  openDialogFn,
  translate,
  createDefaultPortData,
  confirmAndDeleteNodeFn,
  confirmAndDeleteEdgeFn,
  addInputPortToNodeFn,
  deleteInputPortsWithDialogFn,
}) {
  async function deleteNode(node, graph) {
    return await confirmAndDeleteNodeFn({
      node,
      graph,
      confirmFn,
    })
  }

  async function deleteEdge(edge, graph) {
    return await confirmAndDeleteEdgeFn({
      edge,
      graph,
      confirmFn,
    })
  }

  function addInPort(node, graph) {
    return addInputPortToNodeFn({
      node,
      graph,
      createDefaultPortData,
    })
  }

  async function showDeleteInputPortDialog(node, graph) {
    return await deleteInputPortsWithDialogFn({
      node,
      graph,
      openDialogFn,
      translate,
    })
  }

  return {
    deleteNode,
    deleteEdge,
    addInPort,
    showDeleteInputPortDialog,
  }
}
