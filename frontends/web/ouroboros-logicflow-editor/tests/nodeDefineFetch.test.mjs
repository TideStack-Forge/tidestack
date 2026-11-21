import test from 'node:test'
import assert from 'node:assert/strict'

import { fetchNodeDefines } from '../src/node-define/index.js'

test('fetchNodeDefines preserves server-provided return terminal capabilities', async () => {
  const items = await fetchNodeDefines(
    async () => ({
      status: 0,
      data: [
        {
          type: 'return',
          label: '成功返回',
          isTerminal: true,
          terminalKind: 'successful',
          editorCapabilities: {
            canBeEntry: false,
            canBeExit: false,
            canBeErrorEntry: false,
            isSuccessfulTerminal: true,
          },
          portRules: {},
        },
      ],
    }),
    '/mock',
    {}
  )

  assert.equal(items[0].isTerminal, true)
  assert.equal(items[0].terminalKind, 'successful')
  assert.deepEqual(items[0].editorCapabilities, {
    canBeEntry: false,
    canBeExit: false,
    canBeErrorEntry: false,
    isSuccessfulTerminal: true,
    hasExclusiveOutputs: false,
  })
})

test('fetchNodeDefines does not infer return terminal capabilities when server metadata is absent', async () => {
  const items = await fetchNodeDefines(
    async () => ({
      status: 0,
      data: [
        {
          type: 'return',
          label: '成功返回',
          portRules: {},
        },
      ],
    }),
    '/mock',
    {}
  )

  assert.deepEqual(items[0].editorCapabilities, {
    canBeEntry: null,
    canBeExit: null,
    canBeErrorEntry: null,
    isSuccessfulTerminal: false,
    hasExclusiveOutputs: false,
  })
})

test('fetchNodeDefines preserves explicit exception terminal kind', async () => {
  const items = await fetchNodeDefines(
    async () => ({
      status: 0,
      data: [
        {
          type: 'ouroborosExceptions',
          label: '抛出异常',
          isTerminal: true,
          terminalKind: 'exception',
          portRules: {},
        },
      ],
    }),
    '/mock',
    {}
  )

  assert.equal(items[0].isTerminal, true)
  assert.equal(items[0].terminalKind, 'exception')
  assert.equal(items[0].editorCapabilities.isSuccessfulTerminal, false)
})

test('fetchNodeDefines falls back safely when backend auto rules omit a callable autoFunction', async () => {
  const items = await fetchNodeDefines(
    async () => ({
      status: 0,
      data: [
        {
          type: 'action',
          label: '处理',
          portRules: {
            output: {
              ruleType: 'auto',
              autoFunction: null,
            },
          },
        },
      ],
    }),
    '/mock',
    {}
  )

  assert.equal(typeof items[0].portRules.output.autoFunction, 'function')
  assert.deepEqual(items[0].portRules.output.autoFunction({ name: 'action' }), [])
})
