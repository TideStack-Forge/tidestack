import test from 'node:test'
import assert from 'node:assert/strict'

import { transpileConnectionsToX6Edges } from '../src/node/edgeTranspilation.mjs'

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

test('transpileConnectionsToX6Edges keeps valid edges and skips invalid ones with warnings', () => {
  const warnings = []
  const edges = transpileConnectionsToX6Edges({
    connections: [
      {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'ok',
      },
      {
        sourceNode: 'missing',
        sourceOutputPort: 0,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'bad-node',
      },
      {
        sourceNode: 'start',
        sourceOutputPort: 5,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'bad-port',
      },
    ],
    nodePortsMap: createNodePortsMap(),
    createEdgeFn: () => ({ shape: 'edge', marker: 'base' }),
    warnFn: (message, payload) => warnings.push({ message, payload }),
  })

  assert.deepEqual(edges, [
    {
      shape: 'edge',
      marker: 'base',
      data: {
        sourceNode: 'start',
        sourceOutputPort: 0,
        targetNode: 'action',
        targetInputPort: 0,
        label: 'ok',
      },
      source: {
        cell: 'node0',
        port: 'out-0',
      },
      target: {
        cell: 'node1',
        port: 'in-0',
      },
      label: 'ok',
    },
  ])

  assert.deepEqual(warnings, [
    {
      message: '跳过无效连线',
      payload: {
        reason: 'missing-source-node',
        edgeData: {
          sourceNode: 'missing',
          sourceOutputPort: 0,
          targetNode: 'action',
          targetInputPort: 0,
          label: 'bad-node',
        },
      },
    },
    {
      message: '跳过无效连线',
      payload: {
        reason: 'missing-source-port',
        edgeData: {
          sourceNode: 'start',
          sourceOutputPort: 5,
          targetNode: 'action',
          targetInputPort: 0,
          label: 'bad-port',
        },
      },
    },
  ])
})
