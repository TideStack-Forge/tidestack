/** A supplied runtime host is authoritative; missing capabilities never fall back to development APIs. */
export function referenceSource(property, editorProps, kind, legacy) {
  if (property.source !== undefined) return property.source
  const host = editorProps?.logicFlowHost
  if (!host) return legacy
  const source = host.referenceApis?.[kind]
  if (!source) return []
  return kind === 'mq-binding'
    ? { ...(typeof source === 'string' ? {url: source} : source), data: {bindingType: property.bindingType || 'producer'} }
    : source
}

export function intelligenceApi(property, editorProps) {
  const host = editorProps?.logicFlowHost
  if (host) return host.intelligenceApi ?? null
  return property.intelligenceApi === undefined
    ? '/api/dev/${appName}/code-intelligence/context' : property.intelligenceApi
}
