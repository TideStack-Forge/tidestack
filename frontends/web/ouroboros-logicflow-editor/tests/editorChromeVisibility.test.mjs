import test from 'node:test'
import assert from 'node:assert/strict'

import {
  LOGICFLOW_CHROME_VISIBILITY_ACTIONS,
  LOGICFLOW_INSPECTOR_SCOPES,
  createLogicFlowChromeVisibilityState,
  normalizeLogicFlowInspectorScope,
  reconcileLogicFlowChromeVisibilityAvailability,
  reduceLogicFlowChromeVisibilityState,
} from '../src/logicFlowEditor/orchestration/editorChromeVisibility.mjs'

test('createLogicFlowChromeVisibilityState defaults to visible palette and hidden inspector', () => {
  assert.deepEqual(createLogicFlowChromeVisibilityState(), {
    palette: {
      available: true,
      visible: true,
    },
    inspector: {
      available: true,
      visible: false,
    },
  })
})

test('reconcileLogicFlowChromeVisibilityAvailability hides unavailable palette and inspector', () => {
  const state = {
    palette: {
      available: true,
      visible: true,
    },
    inspector: {
      available: true,
      visible: true,
    },
  }

  assert.deepEqual(
    reconcileLogicFlowChromeVisibilityAvailability(state, {
      paletteAvailable: false,
      inspectorAvailable: false,
    }),
    {
      palette: {
        available: false,
        visible: false,
      },
      inspector: {
        available: false,
        visible: false,
      },
    }
  )
})

test('reduceLogicFlowChromeVisibilityState applies toolbar and panel visibility actions', () => {
  let state = createLogicFlowChromeVisibilityState()

  state = reduceLogicFlowChromeVisibilityState(state, {
    type: LOGICFLOW_CHROME_VISIBILITY_ACTIONS.TOGGLE_PALETTE,
  })
  assert.equal(state.palette.visible, false)

  state = reduceLogicFlowChromeVisibilityState(state, {
    type: LOGICFLOW_CHROME_VISIBILITY_ACTIONS.OPEN_INSPECTOR,
  })
  assert.equal(state.inspector.visible, true)

  state = reduceLogicFlowChromeVisibilityState(state, {
    type: LOGICFLOW_CHROME_VISIBILITY_ACTIONS.CLOSE_INSPECTOR,
  })
  assert.equal(state.inspector.visible, false)
})

test('normalizeLogicFlowInspectorScope falls back to flow scope', () => {
  assert.equal(normalizeLogicFlowInspectorScope(LOGICFLOW_INSPECTOR_SCOPES.NODE), 'node')
  assert.equal(normalizeLogicFlowInspectorScope('unknown'), 'flow')
})
