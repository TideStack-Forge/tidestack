import test from 'node:test'
import assert from 'node:assert/strict'

import {
  areOutputPortsMutex,
  getDefaultOutputPortIndex,
  hasMetadataExclusiveOutputs,
  hasSelectiveOutputs,
  normalizeExclusiveOutputPorts,
} from '../src/node/outputPortSemantics.mjs'

test('metadata-defined exclusive groups take precedence over legacy capability absence', () => {
  const node = {
    type: 'customRouter',
    outputPorts: [
      { name: '命中', exclusiveGroupId: 'branch-group' },
      { name: '默认', exclusiveGroupId: 'branch-group', defaultBranch: true },
    ],
  }

  const getNodeCapabilities = () => ({ hasExclusiveOutputs: false })

  assert.equal(hasMetadataExclusiveOutputs(node), true)
  assert.equal(hasSelectiveOutputs(node, getNodeCapabilities), true)
  assert.equal(areOutputPortsMutex(node, 0, 1, getNodeCapabilities), true)
  assert.equal(getDefaultOutputPortIndex(node), 1)
})

test('backend capability metadata still works when per-port metadata is absent', () => {
  const node = {
    type: 'switch',
    outputPorts: [{ name: '通过' }, { name: '拒绝' }],
  }

  const getNodeCapabilities = () => ({ hasExclusiveOutputs: true })

  assert.equal(hasMetadataExclusiveOutputs(node), false)
  assert.equal(hasSelectiveOutputs(node, getNodeCapabilities), true)
  assert.equal(areOutputPortsMutex(node, 0, 1, getNodeCapabilities), true)
  assert.equal(getDefaultOutputPortIndex(node), null)
})

test('metadata-first semantics do not fall back to legacy exclusivity when groups do not match', () => {
  const node = {
    type: 'customRouter',
    outputPorts: [
      { name: '命中', exclusiveGroupId: 'branch-a' },
      { name: '旁路', exclusiveGroupId: 'branch-b' },
      { name: '其他' },
    ],
  }

  const getNodeCapabilities = () => ({ hasExclusiveOutputs: true })

  assert.equal(hasMetadataExclusiveOutputs(node), true)
  assert.equal(areOutputPortsMutex(node, 0, 1, getNodeCapabilities), false)
  assert.equal(areOutputPortsMutex(node, 0, 2, getNodeCapabilities), false)
})

test('normalizeExclusiveOutputPorts marks one default branch inside a shared group', () => {
  const ports = normalizeExclusiveOutputPorts(
    [{ name: '命中' }, { name: '默认' }],
    {
      exclusiveGroupId: 'switch-group',
      defaultPortIndex: 1,
    }
  )

  assert.deepEqual(
    ports.map(({ name, exclusiveGroupId, defaultBranch }) => ({
      name,
      exclusiveGroupId,
      defaultBranch,
    })),
    [
      {
        name: '命中',
        exclusiveGroupId: 'switch-group',
        defaultBranch: false,
      },
      {
        name: '默认',
        exclusiveGroupId: 'switch-group',
        defaultBranch: true,
      },
    ]
  )
})
