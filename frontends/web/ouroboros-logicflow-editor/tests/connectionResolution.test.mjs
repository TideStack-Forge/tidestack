import test from 'node:test'
import assert from 'node:assert/strict'

import { resolveConnectionReference } from '../src/node/connectionResolution.mjs'

function createNodePortsMap() {
  return {
    start: {
      nodeId: 'node0',
      outputPorts: [{ __id: 'out-0' }],
      inputPorts: [],
    },
    action: {
      nodeId: 'node1',
      outputPorts: [{ __id: 'out-1' }],
      inputPorts: [{ __id: 'in-0' }],
    },
  }
}

test('resolveConnectionReference returns resolved source and target ids for valid connections', () => {
  const result = resolveConnectionReference(
    {
      sourceNode: 'start',
      sourceOutputPort: 0,
      targetNode: 'action',
      targetInputPort: 0,
    },
    createNodePortsMap()
  )

  assert.deepEqual(result, {
    isValid: true,
    reason: null,
    source: {
      cell: 'node0',
      port: 'out-0',
    },
    target: {
      cell: 'node1',
      port: 'in-0',
    },
  })
})

test('resolveConnectionReference reports missing source node', () => {
  const result = resolveConnectionReference(
    {
      sourceNode: 'missing',
      sourceOutputPort: 0,
      targetNode: 'action',
      targetInputPort: 0,
    },
    createNodePortsMap()
  )

  assert.equal(result.isValid, false)
  assert.equal(result.reason, 'missing-source-node')
})

test('resolveConnectionReference reports missing target node', () => {
  const result = resolveConnectionReference(
    {
      sourceNode: 'start',
      sourceOutputPort: 0,
      targetNode: 'missing',
      targetInputPort: 0,
    },
    createNodePortsMap()
  )

  assert.equal(result.isValid, false)
  assert.equal(result.reason, 'missing-target-node')
})

test('resolveConnectionReference reports missing source port', () => {
  const result = resolveConnectionReference(
    {
      sourceNode: 'start',
      sourceOutputPort: 3,
      targetNode: 'action',
      targetInputPort: 0,
    },
    createNodePortsMap()
  )

  assert.equal(result.isValid, false)
  assert.equal(result.reason, 'missing-source-port')
})

test('resolveConnectionReference reports missing target port', () => {
  const result = resolveConnectionReference(
    {
      sourceNode: 'start',
      sourceOutputPort: 0,
      targetNode: 'action',
      targetInputPort: 4,
    },
    createNodePortsMap()
  )

  assert.equal(result.isValid, false)
  assert.equal(result.reason, 'missing-target-port')
})
