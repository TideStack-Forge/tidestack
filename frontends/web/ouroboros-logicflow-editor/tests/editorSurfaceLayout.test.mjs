import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createLogicFlowNavigatorDockingContext,
  createLogicFlowToolbarDockingContext,
  resolveLogicFlowMiniMapRightOffset,
  resolveLogicFlowToolbarDockingGeometry,
} from '../src/logicFlowEditor/orchestration/editorSurfaceLayout.mjs'

test('resolveLogicFlowMiniMapRightOffset keeps a stable 16px outer gap before and after inspector expansion', () => {
  assert.equal(resolveLogicFlowMiniMapRightOffset({ collapsed: true }), 16)
  assert.equal(resolveLogicFlowMiniMapRightOffset({ collapsed: false, panelWidth: 450 }), 482)
  assert.equal(
    resolveLogicFlowMiniMapRightOffset({ collapsed: false, panelWidth: 500 }),
    532
  )
})

test('createLogicFlowToolbarDockingContext follows palette visibility', () => {
  assert.deepEqual(
    createLogicFlowToolbarDockingContext({
      paletteAvailable: true,
      paletteVisible: true,
    }),
    {
      followsPalette: true,
      paletteAvailable: true,
      paletteVisible: true,
      paletteOccupiedWidth: 240,
      topInset: 16,
      leftInset: 272,
    }
  )

  assert.equal(
    resolveLogicFlowToolbarDockingGeometry({
      paletteAvailable: true,
      paletteVisible: false,
    }).left,
    16
  )
})

test('createLogicFlowNavigatorDockingContext follows inspector occupied width', () => {
  assert.deepEqual(
    createLogicFlowNavigatorDockingContext({
      inspectorVisible: true,
      inspectorOccupiedWidth: 450,
    }),
    {
      followsInspector: true,
      inspectorVisible: true,
      inspectorOccupiedWidth: 450,
      rightInset: 482,
      bottomInset: 16,
    }
  )

  assert.equal(
    createLogicFlowNavigatorDockingContext({
      inspectorVisible: false,
      inspectorOccupiedWidth: 450,
    }).rightInset,
    16
  )
})
