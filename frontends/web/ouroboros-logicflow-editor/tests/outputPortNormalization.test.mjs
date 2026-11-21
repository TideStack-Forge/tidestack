import test from 'node:test'
import assert from 'node:assert/strict'

import { normalizeNodeOutputPorts } from '../src/node/outputPortNormalization.mjs'

test('normalizeNodeOutputPorts preserves backend-provided switch exclusive metadata', () => {
  const outputPorts = normalizeNodeOutputPorts(
    { type: 'switch' },
    {
      type: 'switch',
      name: '条件分支',
      outputPorts: [
        { name: '通过', exclusiveGroupId: 'switch-branches', defaultBranch: false },
        { name: '兜底', exclusiveGroupId: 'switch-branches', defaultBranch: true },
      ],
    }
  )

  assert.deepEqual(
    outputPorts.map(({ name, exclusiveGroupId, defaultBranch }) => ({
      name,
      exclusiveGroupId,
      defaultBranch,
    })),
    [
      {
        name: '通过',
        exclusiveGroupId: 'switch-branches',
        defaultBranch: false,
      },
      {
        name: '兜底',
        exclusiveGroupId: 'switch-branches',
        defaultBranch: true,
      },
    ]
  )
})

test('normalizeNodeOutputPorts no longer backfills switch metadata from node type', () => {
  const outputPorts = normalizeNodeOutputPorts(
    { type: 'switch' },
    {
      type: 'switch',
      name: '条件分支',
      outputPorts: [{ name: '命中' }, { name: '默认' }],
    }
  )

  assert.deepEqual(outputPorts, [{ name: '命中' }, { name: '默认' }])
})

test('normalizeNodeOutputPorts leaves unknown legacy node types unchanged', () => {
  const outputPorts = normalizeNodeOutputPorts(
    { type: 'processStatusRouter' },
    {
      type: 'processStatusRouter',
      name: '流程状态路由',
      outputPorts: [
        { name: '审批中' },
        { name: '通过' },
        { name: '否决' },
        { name: '撤销' },
        { name: '终止' },
        { name: '其他' },
      ],
    }
  )

  assert.deepEqual(outputPorts, [
    { name: '审批中' },
    { name: '通过' },
    { name: '否决' },
    { name: '撤销' },
    { name: '终止' },
    { name: '其他' },
  ])
})
