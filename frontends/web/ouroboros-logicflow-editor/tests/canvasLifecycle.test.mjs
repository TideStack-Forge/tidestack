import test from 'node:test'
import assert from 'node:assert/strict'

import { disposeCanvasRuntime } from '../src/logicFlowEditor/components/canvas/logicFlowCanvasLifecycle.mjs'

test('disposeCanvasRuntime cancels debounced emitters and disposes the graph', () => {
  const calls = []

  disposeCanvasRuntime({
    graph: {
      dispose() {
        calls.push('graph-dispose')
      },
    },
    emitNodeSelected: {
      cancel() {
        calls.push('node-cancel')
      },
    },
    emitEdgeSelected: {
      cancel() {
        calls.push('edge-cancel')
      },
    },
  })

  assert.deepEqual(calls, ['node-cancel', 'edge-cancel', 'graph-dispose'])
})

test('disposeCanvasRuntime tolerates missing runtime handles', () => {
  assert.doesNotThrow(() => {
    disposeCanvasRuntime({
      graph: null,
      emitNodeSelected: null,
      emitEdgeSelected: null,
    })
  })
})
