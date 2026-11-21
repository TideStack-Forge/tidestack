import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createX6Node,
  createX6PhantomEdge,
  createX6VirtualFlowNode,
  updateNodeSize,
} from '../src/node/x6Cells.js'

function createMutableNodeHarness(nodeDefinition) {
  const attrs = JSON.parse(JSON.stringify(nodeDefinition.attrs || {}))
  const propState = {
    size: {
      width: nodeDefinition.width,
      height: nodeDefinition.height,
    },
  }

  function resolvePathSegments(path) {
    return Array.isArray(path) ? path : String(path).split('/')
  }

  function readPath(target, path) {
    return resolvePathSegments(path).reduce(
      (current, segment) => (current == null ? current : current[segment]),
      target
    )
  }

  function writePath(target, path, value) {
    const segments = resolvePathSegments(path)
    let current = target
    segments.slice(0, -1).forEach((segment) => {
      if (current[segment] == null || typeof current[segment] !== 'object') {
        current[segment] = {}
      }
      current = current[segment]
    })
    current[segments.at(-1)] = value
  }

  return {
    getData() {
      return nodeDefinition.data
    },
    attr(path, value) {
      if (arguments.length === 1) {
        return readPath(attrs, path)
      }

      if (typeof path === 'string' && path.includes('/')) {
        writePath(attrs, path, value)
        return value
      }

      attrs[path] = value
      return value
    },
    prop(path, value) {
      propState[path] = value
      return value
    },
    readAttr(path) {
      return readPath(attrs, path)
    },
    readProp(path) {
      return propState[path]
    },
  }
}

test('createX6Node keeps concrete-node flow-role marker scope limited to error entry', () => {
  const node = createX6Node(
    {
      type: 'switch',
      label: '判断节点',
      editorCapabilities: {
        hasExclusiveOutputs: true,
      },
    },
    {
      name: '判断节点',
      inputPorts: [{ __id: 'in-0', name: '输入' }],
      outputPorts: [
        { __id: 'out-0', name: '通过', exclusiveGroupId: 'switch-group' },
        {
          __id: 'out-1',
          name: '默认',
          exclusiveGroupId: 'switch-group',
          defaultBranch: true,
        },
      ],
    }
  )

  assert.equal(node.attrs.errorEntry.d, 'M -10 12 L 4 12')
  assert.equal(node.attrs.errorEntry.stroke, '#DC2626')
  assert.equal(node.attrs.entry, undefined)
  assert.equal(node.attrs.exit, undefined)

  assert.equal(node.attrs.entryLabel, undefined)
  assert.equal(node.attrs.exitLabel, undefined)
  assert.equal(node.attrs.errorEntryLabel, undefined)
})

test('createX6Node renders entry-capable concrete nodes with a restrained entry accent', () => {
  const entryNode = createX6Node(
    {
      type: 'start',
      label: '入口',
      icon: 'unknown',
      editorCapabilities: {
        canBeEntry: true,
      },
    },
    {
      name: '入口',
      inputPorts: [],
      outputPorts: [{ __id: 'out-0', name: 'next' }],
    }
  )

  assert.equal(entryNode.attrs.icon.visibility, 'hidden')
  assert.equal(entryNode.attrs.iconBg.visibility, 'hidden')
  assert.equal(entryNode.attrs.specialAccent.visibility, 'visible')
  assert.equal(entryNode.attrs.specialAccent.x, 8)
  assert.equal(entryNode.attrs.specialAccent.fill, '#2563EB')
  assert.equal(entryNode.attrs.body.fill, '#F5F9FF')
  assert.equal(entryNode.attrs.body.stroke, '#B7D0FF')
  assert.equal(entryNode.attrs.label.x, 18)
  const entryMark = entryNode.markup.find((item) => item.selector === 'entryMark')
  assert.ok(entryMark)
  assert.deepEqual(entryMark.children, [])
  assert.equal(entryNode.attrs.entry, undefined)

  const errorEntryNode = createX6Node(
    {
      type: 'errorStart',
      label: '异常入口',
      icon: 'unknown',
      editorCapabilities: {
        canBeErrorEntry: true,
      },
    },
    {
      name: '异常入口',
      inputPorts: [],
      outputPorts: [{ __id: 'out-0', name: 'next' }],
    }
  )

  assert.equal(errorEntryNode.attrs.icon.visibility, 'hidden')
  assert.equal(errorEntryNode.attrs.iconBg.visibility, 'hidden')
  assert.equal(errorEntryNode.attrs.specialAccent.visibility, 'visible')
  assert.equal(errorEntryNode.attrs.specialAccent.x, 8)
  assert.equal(errorEntryNode.attrs.specialAccent.fill, '#DC2626')
  assert.equal(errorEntryNode.attrs.body.fill, '#FFF5F5')
  assert.equal(errorEntryNode.attrs.body.stroke, '#F1B8B8')
  assert.equal(errorEntryNode.attrs.label.x, 18)
  const errorEntryMark = errorEntryNode.markup.find((item) => item.selector === 'errorEntryMark')
  assert.ok(errorEntryMark)
  assert.deepEqual(
    errorEntryMark.children.map((item) => item.selector),
    ['errorEntry']
  )
  assert.equal(errorEntryNode.attrs.errorEntry.d, '')
  assert.equal(errorEntryNode.attrs.errorEntry.strokeWidth, 0)
})

test('createX6Node keeps validation-error presentation aligned with approval diagnostics styling', () => {
  const node = createX6Node(
    {
      type: 'action',
      label: '处理节点',
      icon: 'unknown',
    },
    {
      name: '处理节点',
      inputPorts: [{ __id: 'in-0', name: '输入' }],
      outputPorts: [{ __id: 'out-0', name: '输出' }],
    }
  )

  assert.equal(node.markup.some((item) => item.selector === 'errorGlow'), true)
  assert.equal(node.markup.some((item) => item.selector === 'errorOutline'), true)
  assert.equal(node.markup.some((item) => item.selector === 'errorBadgeStem'), true)
  assert.equal(node.markup.some((item) => item.selector === 'errorBadgeDot'), true)
  assert.equal(node.attrs.errorGlow.stroke, 'rgba(220, 38, 38, 0.2)')
  assert.equal(node.attrs.errorGlow.strokeWidth, 5)
  assert.equal(node.attrs.errorGlow.visibility, 'hidden')
  assert.equal(node.attrs.errorGlow.pointerEvents, 'none')
  assert.equal(node.attrs.errorOutline.stroke, '#DC2626')
  assert.equal(node.attrs.errorOutline.strokeWidth, 1.25)
  assert.equal(node.attrs.errorOutline.visibility, 'hidden')
  assert.equal(node.attrs.errorBadgeStem.stroke, '#DC2626')
  assert.equal(node.attrs.errorBadgeStem.d, `M ${node.width - 14} 8 L ${node.width - 14} 13`)
  assert.equal(node.attrs.errorBadgeStem.visibility, 'hidden')
  assert.equal(node.attrs.errorBadgeDot.fill, '#DC2626')
  assert.equal(node.attrs.errorBadgeDot.cx, node.width - 14)
  assert.equal(node.attrs.errorBadgeDot.cy, 16)
  assert.equal(node.attrs.errorBadgeDot.visibility, 'hidden')
})

test('updateNodeSize restores validation error decoration from stable validation highlight state', () => {
  const nodeDefinition = createX6Node(
    {
      type: 'action',
      label: '处理节点',
      icon: 'unknown',
    },
    {
      name: '处理节点',
      inputPorts: [{ __id: 'in-0', name: '输入' }],
      outputPorts: [{ __id: 'out-0', name: '输出' }],
    }
  )
  const node = createMutableNodeHarness(nodeDefinition)

  node.attr('errors/validationHighlighted', true)
  node.attr('errorGlow/visibility', 'hidden')
  node.attr('errorOutline/visibility', 'hidden')
  node.attr('errorBadgeStem/visibility', 'hidden')
  node.attr('errorBadgeDot/visibility', 'hidden')

  updateNodeSize(node)

  assert.equal(node.readAttr('errorGlow/visibility'), 'visible')
  assert.equal(node.readAttr('errorOutline/visibility'), 'visible')
  assert.equal(node.readAttr('errorBadgeStem/visibility'), 'visible')
  assert.equal(node.readAttr('errorBadgeDot/visibility'), 'visible')
  assert.deepEqual(node.readProp('size'), {
    width: nodeDefinition.width,
    height: nodeDefinition.height,
  })
})

test('createX6VirtualFlowNode renders editor phantom nodes as compact node-shaped endpoints', () => {
  const entryNode = createX6VirtualFlowNode('entry')
  const errorNode = createX6VirtualFlowNode('errorEntry')
  const returnNode = createX6VirtualFlowNode('return')
  const phantomEdge = createX6PhantomEdge('return')

  assert.equal(entryNode.data.name, 'VirtualEntryNode')
  assert.equal(entryNode.data.__virtual, true)
  assert.equal(entryNode.selectable, false)
  assert.equal(entryNode.movable, false)
  assert.equal(entryNode.width, 132)
  assert.equal(entryNode.height, 40)
  assert.equal(entryNode.attrs.body.stroke, '#D3E5D9')
  assert.equal(entryNode.attrs.body.fill, '#F5FBF7')
  assert.equal(entryNode.attrs.body.rx, 6)
  assert.equal(entryNode.attrs.body.ry, 6)
  assert.equal(entryNode.attrs.body.strokeDasharray, '4 3')
  assert.equal(entryNode.attrs.body.strokeWidth, 1.25)
  assert.equal(entryNode.attrs.iconBg.fill, '#C9E3D2')
  assert.equal(entryNode.attrs.roleIcon.d, 'M 12 20 L 24 20 M 20 16 L 24 20 L 20 24')
  assert.equal(entryNode.attrs.roleIcon.stroke, '#FFFFFF')
  assert.equal(entryNode.attrs.label.x, 81)
  assert.equal(entryNode.attrs.label.text, '入口')
  assert.equal(entryNode.attrs.label.textAnchor, 'middle')
  assert.equal(entryNode.attrs.label.fill, '#667C72')
  assert.equal(entryNode.attrs.label.fontWeight, 500)

  assert.equal(errorNode.data.name, 'VirtualErrorEntryNode')
  assert.equal(errorNode.selectable, false)
  assert.equal(errorNode.movable, false)
  assert.equal(errorNode.attrs.body.stroke, '#F1B8B8')
  assert.equal(errorNode.attrs.body.fill, '#FFFFFF')
  assert.equal(errorNode.attrs.iconBg.fill, '#E38B5B')
  assert.equal(errorNode.attrs.label.text, '异常入口')

  assert.equal(returnNode.data.name, 'VirtualReturnNode')
  assert.equal(returnNode.selectable, false)
  assert.equal(returnNode.movable, false)
  assert.equal(returnNode.attrs.body.stroke, '#BFE7CB')
  assert.equal(returnNode.attrs.body.fill, '#FFFFFF')
  assert.equal(returnNode.attrs.iconBg.fill, '#6CC08B')
  assert.equal(returnNode.attrs.label.text, '返回')

  assert.equal(phantomEdge.data.__virtual, true)
  assert.equal(phantomEdge.data.__virtualKind, 'return')
  assert.equal(phantomEdge.selectable, false)
  assert.equal(phantomEdge.movable, false)
  assert.equal(phantomEdge.attrs.line.stroke, '#CCC')
  assert.equal(phantomEdge.attrs.line.strokeDasharray, '8 6')
})
