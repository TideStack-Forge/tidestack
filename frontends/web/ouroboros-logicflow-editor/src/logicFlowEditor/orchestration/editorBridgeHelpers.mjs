export function createEditorPropertyAccessors({
  getProps,
  nodeDefaultPropertiesFn,
  edgeDefaultPropertiesFn,
  flowDefaultPropertiesFn,
  transpileAllRawFn,
}) {
  function getNodeDefaultProperties() {
    return nodeDefaultPropertiesFn(getProps())
  }

  function getEdgeDefaultProperties() {
    return edgeDefaultPropertiesFn(getProps())
  }

  function getFlowDefaultProperties() {
    return flowDefaultPropertiesFn(getProps())
  }

  function transpileAll(properties) {
    return transpileAllRawFn(properties, getProps())
  }

  return {
    getNodeDefaultProperties,
    getEdgeDefaultProperties,
    getFlowDefaultProperties,
    transpileAll,
  }
}

export function createEditorPanelBridge({ propertiesPanelRef, toastFn, translate }) {
  let lastTranspileWarningSignature = null

  function showPropertiesPanel(configFactory) {
    return propertiesPanelRef.value?.show?.(configFactory)
  }

  function togglePropertiesPanel() {
    propertiesPanelRef.value.toggle()
  }

  function showTranspileWarnings({ warnings = [] } = {}) {
    if (!warnings.length) return

    const signature = JSON.stringify(
      warnings.map((warning) => ({
        message: warning?.message || '',
        reason: warning?.payload?.reason || 'unknown',
      }))
    )
    if (signature === lastTranspileWarningSignature) return
    lastTranspileWarningSignature = signature

    const counts = warnings.reduce((acc, warning) => {
      const reason = warning?.payload?.reason || 'unknown'
      acc[reason] = (acc[reason] || 0) + 1
      return acc
    }, {})
    const detail = Object.entries(counts)
      .map(([reason, count]) => translate('{{reason}} {{count}} 条', {
        reason: translate(getTranspileWarningReasonLabel(reason)),
        count,
      }))
      .join('，')

    toastFn(translate('发现并跳过 {{count}} 条无效连线：{{detail}}', {
      count: warnings.length,
      detail,
    }), 'warning')
  }

  return {
    showPropertiesPanel,
    togglePropertiesPanel,
    showTranspileWarnings,
  }
}

function getTranspileWarningReasonLabel(reason) {
  switch (reason) {
    case 'missing-source-node':
      return '源节点不存在'
    case 'missing-target-node':
      return '目标节点不存在'
    case 'missing-source-port':
      return '源输出端口不存在'
    case 'missing-target-port':
      return '目标输入端口不存在'
    default:
      return '连线引用无效'
  }
}
