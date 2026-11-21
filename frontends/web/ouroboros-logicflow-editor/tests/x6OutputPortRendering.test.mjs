import test from 'node:test'
import assert from 'node:assert/strict'

import { buildOutputPortAttrs } from '../src/node/outputPortPresentation.mjs'

test('buildOutputPortAttrs marks default branch output ports with read-only classes', () => {
  const node = {
    type: 'switch',
    outputPorts: [
      { name: '命中', exclusiveGroupId: 'switch-group' },
      { name: '默认', exclusiveGroupId: 'switch-group', defaultBranch: true },
    ],
  }

  const attrs = buildOutputPortAttrs(node, node.outputPorts[1], 1, () => ({
    hasExclusiveOutputs: false,
  }))

  assert.match(attrs.text.class, /logicflow-node-port-out-text-exclusive/)
  assert.match(attrs.text.class, /logicflow-node-port-out-text-default/)
  assert.match(attrs.circle.class, /logicflow-node-port-out-exclusive/)
  assert.match(attrs.circle.class, /logicflow-node-port-out-default/)
})

test('buildOutputPortAttrs keeps legacy exclusive styling but does not invent default styling', () => {
  const node = {
    type: 'switch',
    outputPorts: [{ name: '通过' }, { name: '拒绝' }],
  }

  const attrs = buildOutputPortAttrs(node, node.outputPorts[0], 0, () => ({
    hasExclusiveOutputs: true,
  }))

  assert.match(attrs.text.class, /logicflow-node-port-out-text-exclusive/)
  assert.doesNotMatch(attrs.text.class, /logicflow-node-port-out-text-default/)
  assert.match(attrs.circle.class, /logicflow-node-port-out-exclusive/)
  assert.doesNotMatch(attrs.circle.class, /logicflow-node-port-out-default/)
})
