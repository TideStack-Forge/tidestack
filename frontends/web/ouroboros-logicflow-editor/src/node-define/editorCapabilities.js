import { defaultEditorCapabilities } from './editorCapabilityDefaults.mjs'

export function normalizeEditorCapabilities(nodeDefine = {}) {
  return {
    ...defaultEditorCapabilities,
    ...(nodeDefine.editorCapabilities || {}),
  }
}

export function resolveRoleCapability(capabilities, key) {
  return typeof capabilities?.[key] === 'boolean' ? capabilities[key] : true
}

export function buildCapabilityGetter(nodeDefines = []) {
  const capabilityMap = new Map(
    nodeDefines.map((define) => [
      define.type,
      define.editorCapabilities || normalizeEditorCapabilities(define),
    ])
  )

  return function getNodeCapabilities(node) {
    return capabilityMap.get(node?.type) || normalizeEditorCapabilities({ type: node?.type })
  }
}

export function buildNodeOptionsByCapability(nodes = []) {
  return nodes.map(({ name }) => ({ label: name, value: name }))
}

export { defaultEditorCapabilities }
