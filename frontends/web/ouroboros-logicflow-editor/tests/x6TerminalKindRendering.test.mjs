import test from 'node:test'
import assert from 'node:assert/strict'

import { createX6Node } from '../src/node/x6Cells.js'

function createTerminalNode(terminalKind) {
  return createX6Node(
    {
      type: `terminal-${terminalKind || 'plain'}`,
      label: '终点节点',
      isTerminal: true,
      ...(terminalKind ? { terminalKind } : {}),
      portRules: {},
    },
    {
      name: '终点节点',
      inputPorts: [],
      outputPorts: [],
    }
  )
}

test('createX6Node renders successful terminals without an internal return badge pill', () => {
  const node = createTerminalNode('successful')

  assert.equal(node.attrs.terminalBadge.text, '')
  assert.equal(node.attrs.terminalBadge.visibility, 'hidden')
  assert.equal(node.attrs.terminalBadgeBg.visibility, 'hidden')
  assert.equal(node.attrs.icon.visibility, 'hidden')
  assert.equal(node.attrs.iconBg.visibility, 'hidden')
  assert.equal(node.attrs.specialAccent.visibility, 'visible')
  assert.equal(node.attrs.specialAccent.x, node.width - 12)
  assert.equal(node.attrs.specialAccent.fill, '#6CC08B')
  assert.equal(node.attrs.body.fill, '#F4FBF6')
  assert.equal(node.attrs.label.x, 18)
  assert.equal(node.attrs.body.stroke, '#6CC08B')
})

test('createX6Node keeps exception terminals on node-level styling only', () => {
  const node = createTerminalNode('exception')

  assert.equal(node.attrs.terminalBadge.text, '')
  assert.equal(node.attrs.terminalBadge.visibility, 'hidden')
  assert.equal(node.attrs.terminalBadgeBg.visibility, 'hidden')
  assert.equal(node.attrs.icon.visibility, 'hidden')
  assert.equal(node.attrs.iconBg.visibility, 'hidden')
  assert.equal(node.attrs.specialAccent.fill, '#E38B5B')
  assert.equal(node.attrs.body.fill, '#FFF7F2')
  assert.equal(node.attrs.body.stroke, '#E38B5B')
})

test('createX6Node keeps generic terminals on node-level styling only', () => {
  const node = createTerminalNode('generic')

  assert.equal(node.attrs.terminalBadge.text, '')
  assert.equal(node.attrs.terminalBadge.visibility, 'hidden')
  assert.equal(node.attrs.terminalBadgeBg.visibility, 'hidden')
  assert.equal(node.attrs.icon.visibility, 'hidden')
  assert.equal(node.attrs.iconBg.visibility, 'hidden')
  assert.equal(node.attrs.specialAccent.fill, '#94A3B8')
  assert.equal(node.attrs.body.fill, '#F8FAFC')
  assert.equal(node.attrs.body.stroke, '#94A3B8')
})

test('createX6Node removes exclusive branch badge pills entirely', () => {
  const node = createX6Node(
    {
      type: 'terminal-exception-xor',
      label: '异常终点',
      isTerminal: true,
      terminalKind: 'exception',
      editorCapabilities: {
        hasExclusiveOutputs: true,
      },
    },
    {
      name: '异常终点',
      inputPorts: [],
      outputPorts: [
        { __id: 'out-0', name: '命中', exclusiveGroupId: 'exception-group' },
        {
          __id: 'out-1',
          name: '默认',
          exclusiveGroupId: 'exception-group',
          defaultBranch: true,
        },
      ],
    }
  )

  assert.equal(node.attrs.capabilityBadge.text, '')
  assert.equal(node.attrs.capabilityBadge.visibility, 'hidden')
  assert.equal(node.attrs.capabilityBadgeBg.visibility, 'hidden')
  assert.equal(node.attrs.terminalBadge.text, '')
  assert.equal(node.attrs.terminalBadge.visibility, 'hidden')
})
