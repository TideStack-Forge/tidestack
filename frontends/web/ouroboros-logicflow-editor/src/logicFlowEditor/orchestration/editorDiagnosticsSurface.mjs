export const LOGICFLOW_DIAGNOSTICS_STATUSES = Object.freeze({
  CLEAN: 'clean',
  WARNING: 'warning',
  ERROR: 'error',
  DRAFT_INVALID: 'draft-invalid',
})

export const LOGICFLOW_DIAGNOSTIC_SEVERITIES = Object.freeze({
  WARNING: 'warning',
  ERROR: 'error',
})

export const LOGICFLOW_DIAGNOSTICS_STATUS_META = Object.freeze({
  [LOGICFLOW_DIAGNOSTICS_STATUSES.CLEAN]: {
    label: '正常',
    tone: 'clean',
    summary: '当前未发现结构错误或警告',
    iconClassName: 'fa-solid fa-check',
    ariaLabel: '流程诊断：正常',
  },
  [LOGICFLOW_DIAGNOSTICS_STATUSES.WARNING]: {
    label: '警告',
    tone: 'warning',
    summary: '存在待确认的结构告警',
    iconClassName: 'fa-solid fa-triangle-exclamation',
    ariaLabel: '流程诊断：警告',
  },
  [LOGICFLOW_DIAGNOSTICS_STATUSES.ERROR]: {
    label: '错误',
    tone: 'error',
    summary: '存在需要处理的结构错误',
    iconClassName: 'fa-solid fa-xmark',
    ariaLabel: '流程诊断：错误',
  },
  [LOGICFLOW_DIAGNOSTICS_STATUSES.DRAFT_INVALID]: {
    label: '存在错误',
    tone: 'draft-invalid',
    summary: '当前流程存在阻塞性错误，请继续修正',
    iconClassName: 'fa-solid fa-xmark',
    ariaLabel: '流程诊断：存在错误',
  },
})

function normalizeDiagnosticSeverity(entry) {
  if (entry?.severity === LOGICFLOW_DIAGNOSTIC_SEVERITIES.WARNING) {
    return LOGICFLOW_DIAGNOSTIC_SEVERITIES.WARNING
  }
  return LOGICFLOW_DIAGNOSTIC_SEVERITIES.ERROR
}

function normalizeDiagnosticBlocking(entry, severity) {
  if (typeof entry?.blocking === 'boolean') {
    return entry.blocking
  }
  return severity !== LOGICFLOW_DIAGNOSTIC_SEVERITIES.WARNING
}

function normalizeDiagnosticEntry(entry, index) {
  const message = typeof entry?.message === 'string' ? entry.message.trim() : ''
  if (!message) {
    return null
  }

  const severity = normalizeDiagnosticSeverity(entry)
  const blocking = normalizeDiagnosticBlocking(entry, severity)
  const nodeName =
    typeof entry?.nodeName === 'string' && entry.nodeName.trim() ? entry.nodeName.trim() : null

  return {
    id: `${message}-${nodeName || 'static'}-${index}`,
    message,
    ...(entry.messageKey
      ? { messageKey: entry.messageKey, messageParams: entry.messageParams }
      : null),
    nodeName,
    severity,
    blocking,
  }
}

function resolveDiagnosticsStatus(items) {
  if (!items.length) {
    return LOGICFLOW_DIAGNOSTICS_STATUSES.CLEAN
  }

  const hasBlockingItem = items.some((item) => item.blocking)
  if (hasBlockingItem) {
    return LOGICFLOW_DIAGNOSTICS_STATUSES.DRAFT_INVALID
  }

  const hasErrorItem = items.some(
    (item) => item.severity === LOGICFLOW_DIAGNOSTIC_SEVERITIES.ERROR
  )
  if (hasErrorItem) {
    return LOGICFLOW_DIAGNOSTICS_STATUSES.ERROR
  }

  return LOGICFLOW_DIAGNOSTICS_STATUSES.WARNING
}

export function buildLogicFlowDiagnosticsSurfaceContract({ validationErrors = [] } = {}) {
  const items = validationErrors.map(normalizeDiagnosticEntry).filter(Boolean)
  const status = resolveDiagnosticsStatus(items)
  const statusMeta =
    LOGICFLOW_DIAGNOSTICS_STATUS_META[status] ||
    LOGICFLOW_DIAGNOSTICS_STATUS_META[LOGICFLOW_DIAGNOSTICS_STATUSES.CLEAN]
  const blockingCount = items.filter((item) => item.blocking).length
  const tooltip =
    items.length > 0
      ? `${statusMeta.summary}（共 ${items.length} 项，阻塞项 ${blockingCount} 个）`
      : statusMeta.summary

  return {
    owner: 'toolbar-meta',
    status,
    trigger: {
      geometry: 'approval-aligned-command-button',
      tone: statusMeta.tone,
      label: statusMeta.label,
      count: items.length,
      iconClassName: statusMeta.iconClassName,
      ariaLabel: statusMeta.ariaLabel,
      tooltip,
    },
    popover: {
      summary: statusMeta.summary,
      badgeTone: statusMeta.tone,
      blockingCount,
      items,
    },
  }
}
