export function collectNodeDefineSvgs(nodeDefines = []) {
  return nodeDefines
    .map((node) => node.svgs || {})
    .reduce((acc, curr) => Object.assign(acc, curr), {})
}

export function buildEditorWrapData({
  createObjectFn,
  entryNodes = [],
  exitNodes = [],
  errorEntryNodes = [],
  nodeTypes = [],
  isReadOnly = false,
  flowModel = null,
  rootData = {},
  data = {},
  superProps = {},
} = {}) {
  const superData = {
    __FLOW_MODEL: flowModel,
    __ENTRY_NODES: entryNodes,
    __EXIT_NODES: exitNodes,
    __ERROR_ENTRY_NODES: errorEntryNodes,
    __NODE_TYPES: nodeTypes,
    __IS_READONLY: isReadOnly,
    ...superProps,
    ...(rootData || {}),
  }
  return createObjectFn(superData, data)
}
