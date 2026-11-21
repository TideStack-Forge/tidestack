export function normalizeValidationEntries(entries = []) {
  return entries
    .map((entry) => {
      if (typeof entry === 'string') {
        const message = entry.trim()
        return message ? { message } : null
      }

      const message = typeof entry?.message === 'string' ? entry.message.trim() : ''
      if (!message) {
        return null
      }

      const nodeName =
        typeof entry?.nodeName === 'string' && entry.nodeName.trim() ? entry.nodeName.trim() : null
      const nodeNames = Array.isArray(entry?.nodeNames)
        ? entry.nodeNames
            .map((name) => (typeof name === 'string' && name.trim() ? name.trim() : null))
            .filter(Boolean)
        : nodeName
          ? [nodeName]
          : []
      const severity =
        typeof entry?.severity === 'string' && entry.severity.trim() ? entry.severity.trim() : null
      const blocking = typeof entry?.blocking === 'boolean' ? entry.blocking : null
      const scope =
        typeof entry?.scope === 'string' && entry.scope.trim() ? entry.scope.trim() : null
      const fieldPath =
        typeof entry?.fieldPath === 'string' && entry.fieldPath.trim()
          ? entry.fieldPath.trim()
          : null
      const portKind =
        typeof entry?.portKind === 'string' && entry.portKind.trim()
          ? entry.portKind.trim()
          : null
      const portIndex = Number.isInteger(entry?.portIndex) ? entry.portIndex : null
      const sourceNode =
        typeof entry?.sourceNode === 'string' && entry.sourceNode.trim()
          ? entry.sourceNode.trim()
          : null
      const sourceOutputPort = Number.isInteger(entry?.sourceOutputPort)
        ? entry.sourceOutputPort
        : null
      const targetNode =
        typeof entry?.targetNode === 'string' && entry.targetNode.trim()
          ? entry.targetNode.trim()
          : null
      const targetInputPort = Number.isInteger(entry?.targetInputPort)
        ? entry.targetInputPort
        : null

      return {
        message,
        ...(typeof entry.messageKey === 'string'
          ? { messageKey: entry.messageKey, messageParams: entry.messageParams }
          : null),
        nodeName: nodeName || nodeNames[0] || null,
        ...(nodeNames.length ? { nodeNames } : null),
        ...(scope ? { scope } : null),
        ...(fieldPath ? { fieldPath } : null),
        ...(portKind ? { portKind } : null),
        ...(portIndex == null ? null : { portIndex }),
        ...(sourceNode ? { sourceNode } : null),
        ...(sourceOutputPort == null ? null : { sourceOutputPort }),
        ...(targetNode ? { targetNode } : null),
        ...(targetInputPort == null ? null : { targetInputPort }),
        ...(severity ? { severity } : null),
        ...(blocking == null ? null : { blocking }),
      }
    })
    .filter(Boolean)
}

export function derivePersistedValidationErrors(flowData = {}) {
  const diagnostics = Array.isArray(flowData?.validationDiagnostics)
    ? flowData.validationDiagnostics
    : []
  const diagnosticsByMessage = new Map(
    diagnostics
      .map((diagnostic) => {
        const message = typeof diagnostic?.message === 'string' ? diagnostic.message.trim() : ''

        if (!message) {
          return null
        }

        return [message, diagnostic]
      })
      .filter(Boolean)
  )

  const normalizedErrors = normalizeValidationEntries(
    (flowData?.validationErrors || []).map((message) => {
      const diagnostic = diagnosticsByMessage.get(typeof message === 'string' ? message.trim() : '')
      return {
        ...(diagnostic || null),
        message,
      }
    })
  )

  if (normalizedErrors.length) {
    return normalizedErrors
  }

  const normalizedDiagnostics = normalizeValidationEntries(
    diagnostics.map((diagnostic) => ({
      message: diagnostic?.message,
      nodeName:
        Array.isArray(diagnostic?.nodeNames) && typeof diagnostic.nodeNames[0] === 'string'
          ? diagnostic.nodeNames[0]
          : null,
      nodeNames: diagnostic?.nodeNames,
      scope: diagnostic?.scope,
      fieldPath: diagnostic?.fieldPath,
      portKind: diagnostic?.portKind,
      portIndex: diagnostic?.portIndex,
      sourceNode: diagnostic?.sourceNode,
      sourceOutputPort: diagnostic?.sourceOutputPort,
      targetNode: diagnostic?.targetNode,
      targetInputPort: diagnostic?.targetInputPort,
      severity: diagnostic?.severity,
      blocking: diagnostic?.blocking,
    }))
  )

  if (normalizedDiagnostics.length) {
    return normalizedDiagnostics
  }

  if (typeof flowData?.validationSummary === 'string' && flowData.validationSummary.trim()) {
    return [{ message: flowData.validationSummary.trim() }]
  }

  return []
}
