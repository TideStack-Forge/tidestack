import test from 'node:test'
import assert from 'node:assert/strict'

import {
  isCanvasPortLinkingEnabled,
  resolveCanvasDoubleClickForwarding,
} from '../src/logicFlowEditor/components/canvas/canvasEventSemantics.mjs'

test('isCanvasPortLinkingEnabled disables port linking when editor is readonly', () => {
  assert.equal(isCanvasPortLinkingEnabled(false), true)
  assert.equal(isCanvasPortLinkingEnabled(true), false)
})

test('resolveCanvasDoubleClickForwarding preserves target-specific emit names', () => {
  const graph = { id: 'graph' }
  const node = { id: 'node' }
  const edge = { id: 'edge' }

  assert.deepEqual(resolveCanvasDoubleClickForwarding({ targetType: 'node', target: node, graph }), {
    eventName: 'nodeDblclick',
    args: [node, graph],
  })
  assert.deepEqual(resolveCanvasDoubleClickForwarding({ targetType: 'edge', target: edge, graph }), {
    eventName: 'edgeDblclick',
    args: [edge, graph],
  })
  assert.deepEqual(resolveCanvasDoubleClickForwarding({ targetType: 'blank', graph }), {
    eventName: 'blankDblclick',
    args: [graph],
  })
})
