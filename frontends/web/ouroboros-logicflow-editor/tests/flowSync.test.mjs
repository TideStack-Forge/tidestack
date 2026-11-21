import test from 'node:test'
import assert from 'node:assert/strict'

import { getFlowProps, getFlowSyncAction } from '../src/logicFlowEditor/components/canvas/flowSync.mjs'

function createFlow(overrides = {}) {
  return {
    name: 'demo-flow',
    description: '',
    entryNode: 'start',
    exitNode: '',
    errorEntryNode: '',
    nodes: [
      {
        type: 'start',
        name: 'start',
        inputPorts: [],
        outputPorts: [{ name: 'next', dataType: 'any', description: '' }],
      },
      {
        type: 'return',
        name: 'done',
        inputPorts: [{ name: 'input', dataType: 'any', description: '' }],
        outputPorts: [],
      },
    ],
    connections: [
      {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'done',
        targetInputPort: 0,
      },
    ],
    ...overrides,
  }
}

test('returns noop when external data matches rendered graph data', () => {
  const renderedData = createFlow()
  const incomingData = createFlow()

  assert.equal(getFlowSyncAction(renderedData, incomingData), 'noop')
})

test('returns update-props when only flow-level properties change', () => {
  const renderedData = createFlow()
  const incomingData = createFlow({
    description: 'updated',
    exitNode: 'done',
    errorEntryNode: 'error-start',
  })

  assert.equal(getFlowSyncAction(renderedData, incomingData), 'update-props')
})

test('getFlowProps drops legacy nodePosition from flow-level props', () => {
  const flowProps = getFlowProps(
    createFlow({
      nodePosition: {
        start: { x: 10, y: 20 },
      },
      nodePositions: {
        start: { x: 30, y: 40 },
      },
    })
  )

  assert.equal('nodePosition' in flowProps, false)
  assert.equal('nodePositions' in flowProps, false)
})

test('returns reload when nodes change', () => {
  const renderedData = createFlow()
  const incomingData = createFlow({
    nodes: [
      ...renderedData.nodes,
      {
        type: 'action',
        name: 'middle',
        inputPorts: [{ name: 'input', dataType: 'any', description: '' }],
        outputPorts: [{ name: 'next', dataType: 'any', description: '' }],
      },
    ],
  })

  assert.equal(getFlowSyncAction(renderedData, incomingData), 'reload')
})

test('returns reload when connections change', () => {
  const renderedData = createFlow()
  const incomingData = createFlow({
    connections: [],
  })

  assert.equal(getFlowSyncAction(renderedData, incomingData), 'reload')
})
