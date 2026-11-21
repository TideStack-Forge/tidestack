import test from 'node:test'
import assert from 'node:assert/strict'

import {
  applyStoredNodePositions,
  extractNodePositions,
  hasStoredNodePositions,
  getStoredNodePositions,
} from '../src/node/positionSync.mjs'

test('accepts nodePositions input', () => {
  assert.deepEqual(
    getStoredNodePositions({
      nodePositions: {
        start: { x: 10, y: 20 },
      },
    }),
    {
      start: { x: 10, y: 20 },
    }
  )
})

test('ignores legacy nodePosition input', () => {
  assert.deepEqual(
    getStoredNodePositions({
      nodePosition: {
        done: { x: 30, y: 40 },
      },
    }),
    {}
  )
})

test('detects persisted node positions only when nodePositions is non-empty', () => {
  assert.equal(hasStoredNodePositions({}), false)
  assert.equal(hasStoredNodePositions({ nodePositions: {} }), false)
  assert.equal(
    hasStoredNodePositions({
      nodePositions: {
        done: { x: 30, y: 40 },
      },
    }),
    true
  )
})

test('applies stored node positions by node data name', () => {
  const x6Data = {
    nodes: [
      {
        id: 'node0',
        data: { name: 'start' },
        x: 1,
        y: 2,
      },
      {
        id: 'node1',
        data: { name: 'done' },
        x: 3,
        y: 4,
      },
    ],
    edges: [],
  }

  const result = applyStoredNodePositions(x6Data, {
    nodePositions: {
      done: { x: 300, y: 400 },
    },
  })

  assert.deepEqual(
    result.nodes.map((node) => ({ name: node.data.name, x: node.x, y: node.y })),
    [
      { name: 'start', x: 1, y: 2 },
      { name: 'done', x: 300, y: 400 },
    ]
  )
})

test('extracts node positions from x6 json cells using position field and node data name', () => {
  const cells = [
    {
      shape: 'rect',
      data: { name: 'start' },
      position: { x: 120, y: 80 },
    },
    {
      shape: 'rect',
      data: { name: 'VirtualEntryNode', __virtual: true },
      position: { x: 20, y: 80 },
    },
    {
      shape: 'edge',
      data: { sourceNode: 'start', targetNode: 'done' },
    },
    {
      shape: 'rect',
      data: { name: 'done' },
      position: { x: 360, y: 80 },
    },
  ]

  assert.deepEqual(extractNodePositions(cells), {
    start: { x: 120, y: 80 },
    done: { x: 360, y: 80 },
  })
})

test('ignores virtual node positions when applying stored positions', () => {
  const x6Data = {
    nodes: [
      {
        id: 'virtual-entry-node',
        data: { name: 'VirtualEntryNode', __virtual: true },
        x: 1,
        y: 2,
      },
      {
        id: 'node0',
        data: { name: 'start' },
        x: 3,
        y: 4,
      },
    ],
    edges: [],
  }

  const result = applyStoredNodePositions(x6Data, {
    nodePositions: {
      VirtualEntryNode: { x: 100, y: 200 },
      start: { x: 300, y: 400 },
    },
  })

  assert.deepEqual(
    result.nodes.map((node) => ({ name: node.data.name, x: node.x, y: node.y })),
    [
      { name: 'VirtualEntryNode', x: 1, y: 2 },
      { name: 'start', x: 300, y: 400 },
    ]
  )
})
