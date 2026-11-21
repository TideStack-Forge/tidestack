export const TERMINAL_KINDS = Object.freeze({
  SUCCESSFUL: 'successful',
  EXCEPTION: 'exception',
  GENERIC: 'generic',
})

const TERMINAL_VISUALS = Object.freeze({
  [TERMINAL_KINDS.SUCCESSFUL]: {
    badgeText: 'RETURN',
    bodyStroke: '#6CC08B',
    bodyFill: '#F4FBF6',
    badgeBgFill: '#E8F8ED',
    badgeBgStroke: '#68B882',
    badgeTextFill: '#2D7C46',
    iconBg: '#6CC08B',
    bodyClassName: 'logicflow-node-body-terminal-successful',
    paletteClassName: 'terminal-successful',
  },
  [TERMINAL_KINDS.EXCEPTION]: {
    badgeText: 'ERROR',
    bodyStroke: '#E38B5B',
    bodyFill: '#FFF7F2',
    badgeBgFill: '#FFF1E8',
    badgeBgStroke: '#E38B5B',
    badgeTextFill: '#B45309',
    iconBg: '#E38B5B',
    bodyClassName: 'logicflow-node-body-terminal-exception',
    paletteClassName: 'terminal-exception',
  },
  [TERMINAL_KINDS.GENERIC]: {
    badgeText: 'TERMINAL',
    bodyStroke: '#94A3B8',
    bodyFill: '#F8FAFC',
    badgeBgFill: '#F1F5F9',
    badgeBgStroke: '#94A3B8',
    badgeTextFill: '#475569',
    iconBg: '#94A3B8',
    bodyClassName: 'logicflow-node-body-terminal-generic',
    paletteClassName: 'terminal-generic',
  },
})

function isKnownTerminalKind(value) {
  return Object.values(TERMINAL_KINDS).includes(value)
}

export function resolveTerminalKind(nodeDefine = {}) {
  if (isKnownTerminalKind(nodeDefine?.terminalKind)) {
    return nodeDefine.terminalKind
  }
  if (nodeDefine?.editorCapabilities?.isSuccessfulTerminal) {
    return TERMINAL_KINDS.SUCCESSFUL
  }
  if (nodeDefine?.isTerminal) {
    return TERMINAL_KINDS.GENERIC
  }
  return null
}

export function getTerminalDisplayState(nodeDefine = {}) {
  const terminalKind = resolveTerminalKind(nodeDefine)
  return {
    isTerminal: !!terminalKind,
    terminalKind,
    badgeText:
      terminalKind === TERMINAL_KINDS.SUCCESSFUL
        ? 'RETURN'
        : terminalKind === TERMINAL_KINDS.EXCEPTION
        ? 'ERROR'
        : terminalKind === TERMINAL_KINDS.GENERIC
        ? 'TERMINAL'
        : '',
  }
}

export function getTerminalVisualState(nodeDefine = {}) {
  const displayState = getTerminalDisplayState(nodeDefine)
  const visuals = displayState.terminalKind ? TERMINAL_VISUALS[displayState.terminalKind] : null
  return {
    ...displayState,
    bodyStroke: visuals?.bodyStroke || null,
    bodyFill: visuals?.bodyFill || null,
    badgeBgFill: visuals?.badgeBgFill || null,
    badgeBgStroke: visuals?.badgeBgStroke || null,
    badgeTextFill: visuals?.badgeTextFill || null,
    iconBg: visuals?.iconBg || null,
    bodyClassName: visuals?.bodyClassName || '',
    paletteClassName: visuals?.paletteClassName || '',
  }
}

export function isTerminalNodeDefine(nodeDefine = {}) {
  return !!resolveTerminalKind(nodeDefine)
}
