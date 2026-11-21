export const LOGICFLOW_CHROME_VISIBILITY_ACTIONS = Object.freeze({
  TOGGLE_PALETTE: 'toggle-palette',
  CLOSE_PALETTE: 'close-palette',
  TOGGLE_INSPECTOR: 'toggle-inspector',
  OPEN_INSPECTOR: 'open-inspector',
  CLOSE_INSPECTOR: 'close-inspector',
})

export const LOGICFLOW_CHROME_VISIBILITY_REASONS = Object.freeze({
  TOOLBAR_TOGGLE: 'toolbar-toggle',
  PANEL_CLOSE: 'panel-close',
  SELECTION: 'selection',
  SELECTION_CLEAR: 'selection-clear',
  DIAGNOSTICS_NAVIGATION: 'diagnostics-navigation',
  EXPLICIT_OPEN: 'explicit-open',
})

export const LOGICFLOW_INSPECTOR_SCOPES = Object.freeze({
  FLOW: 'flow',
  NODE: 'node',
  EDGE: 'edge',
})

export function normalizeLogicFlowInspectorScope(scope) {
  return Object.values(LOGICFLOW_INSPECTOR_SCOPES).includes(scope)
    ? scope
    : LOGICFLOW_INSPECTOR_SCOPES.FLOW
}

export function createLogicFlowChromeVisibilityState({
  paletteAvailable = true,
  inspectorAvailable = true,
  paletteVisible = paletteAvailable,
  inspectorVisible = false,
} = {}) {
  return {
    palette: {
      available: Boolean(paletteAvailable),
      visible: Boolean(paletteAvailable) && Boolean(paletteVisible),
    },
    inspector: {
      available: Boolean(inspectorAvailable),
      visible: Boolean(inspectorAvailable) && Boolean(inspectorVisible),
    },
  }
}

export function reconcileLogicFlowChromeVisibilityAvailability(
  state,
  {
    paletteAvailable = true,
    inspectorAvailable = true,
  } = {}
) {
  return {
    palette: {
      ...state.palette,
      available: Boolean(paletteAvailable),
      visible: paletteAvailable ? state.palette.visible : false,
    },
    inspector: {
      ...state.inspector,
      available: Boolean(inspectorAvailable),
      visible: inspectorAvailable ? state.inspector.visible : false,
    },
  }
}

export function reduceLogicFlowChromeVisibilityState(state, action = {}) {
  switch (action.type) {
    case LOGICFLOW_CHROME_VISIBILITY_ACTIONS.TOGGLE_PALETTE:
      return {
        ...state,
        palette: {
          ...state.palette,
          visible: state.palette.available ? !state.palette.visible : false,
        },
      }

    case LOGICFLOW_CHROME_VISIBILITY_ACTIONS.CLOSE_PALETTE:
      return {
        ...state,
        palette: {
          ...state.palette,
          visible: false,
        },
      }

    case LOGICFLOW_CHROME_VISIBILITY_ACTIONS.TOGGLE_INSPECTOR:
      return {
        ...state,
        inspector: {
          ...state.inspector,
          visible: state.inspector.available ? !state.inspector.visible : false,
        },
      }

    case LOGICFLOW_CHROME_VISIBILITY_ACTIONS.OPEN_INSPECTOR:
      return {
        ...state,
        inspector: {
          ...state.inspector,
          visible: state.inspector.available,
        },
      }

    case LOGICFLOW_CHROME_VISIBILITY_ACTIONS.CLOSE_INSPECTOR:
      return {
        ...state,
        inspector: {
          ...state.inspector,
          visible: false,
        },
      }

    default:
      return state
  }
}
