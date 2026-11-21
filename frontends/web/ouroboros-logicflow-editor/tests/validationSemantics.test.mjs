import test from 'node:test'
import assert from 'node:assert/strict'

import {
  checkAllInputPortsConnected,
  checkInputPortConflicts,
} from '../src/node/validate.js'

test('checkAllInputPortsConnected ignores disabled nodes with missing required inputs', () => {
  const flowModel = {
    nodes: [
      {
        name: 'source',
        isEnabled: true,
        inputPorts: [],
        outputPorts: [{ name: 'out' }],
      },
      {
        name: 'disabledMerge',
        isEnabled: false,
        inputPorts: [{ name: 'left' }, { name: 'right' }],
        outputPorts: [{ name: 'merged' }],
      },
    ],
    connections: [],
  }

  const [valid, unconnectedNodes] = checkAllInputPortsConnected(flowModel)

  assert.equal(valid, true)
  assert.deepEqual(unconnectedNodes, [])
})

test('checkInputPortConflicts ignores disabled reconvergence branches', () => {
  const flowModel = {
    nodes: [
      {
        name: 'left',
        isEnabled: false,
        inputPorts: [],
        outputPorts: [{ name: 'out' }],
      },
      {
        name: 'right',
        isEnabled: false,
        inputPorts: [],
        outputPorts: [{ name: 'out' }],
      },
      {
        name: 'disabledTarget',
        isEnabled: false,
        inputPorts: [{ name: 'input' }],
        outputPorts: [],
      },
    ],
    connections: [
      {
        sourceNode: 'left',
        sourceOutputPort: 0,
        targetNode: 'disabledTarget',
        targetInputPort: 0,
      },
      {
        sourceNode: 'right',
        sourceOutputPort: 0,
        targetNode: 'disabledTarget',
        targetInputPort: 0,
      },
    ],
  }

  assert.equal(checkInputPortConflicts(flowModel), true)
})
