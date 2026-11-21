import test from 'node:test'
import assert from 'node:assert/strict'

import {
  shouldEmitEdgeCreated,
  shouldEmitNodeCreated,
} from '../src/logicFlowEditor/components/canvas/creationEvents.mjs'

test('suppresses nodeCreated while graph is hydrating', () => {
  assert.equal(shouldEmitNodeCreated({ isHydrating: true }), false)
})

test('emits nodeCreated for user-created nodes after hydration', () => {
  assert.equal(shouldEmitNodeCreated({ isHydrating: false }), true)
})

test('suppresses edgeCreated while graph is hydrating even if edge is new', () => {
  assert.equal(shouldEmitEdgeCreated({ isHydrating: true, isNew: true }), false)
})

test('suppresses edgeCreated for non-new edge connection events', () => {
  assert.equal(shouldEmitEdgeCreated({ isHydrating: false, isNew: false }), false)
})

test('emits edgeCreated for user-created new edge after hydration', () => {
  assert.equal(shouldEmitEdgeCreated({ isHydrating: false, isNew: true }), true)
})
