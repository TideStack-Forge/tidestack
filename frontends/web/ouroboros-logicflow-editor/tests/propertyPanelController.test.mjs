import test from 'node:test'
import assert from 'node:assert/strict'

import {
  buildEdgePanelConfig,
  buildFlowPanelConfig,
  buildNodePanelConfig,
} from '../src/logicFlowEditor/propertyPanel/logicFlowPropertyPanelController.mjs'

test('buildFlowPanelConfig wires flow props through wrapData and saveAction', () => {
  let saved = null
  let flushes = 0
  const graph = {
    getLogicFlowProps() {
      return { name: 'demo-flow', description: 'before', isEnabled: true }
    },
    updateLogicFlowProps(value) {
      saved = value
    },
    flushLogicFlowChange() {
      flushes += 1
    },
  }

  const config = buildFlowPanelConfig({
    graph,
    getFlowDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [{ message: '流程层错误' }],
    wrapData: (data) => ({ ...data, wrapped: true }),
  })

  assert.equal(config.panelTitle, '流程属性')
  assert.equal(config.runtimeKey, 'flow')
  assert.deepEqual(config.formItems, [
    { name: 'name', type: 'input-text' },
    {
      type: 'alert',
      level: 'danger',
      title: '校验错误',
      showIcon: true,
      label: false,
      className: 'logicflow-property-validation-alert',
      body: '流程层错误',
    },
  ])
  assert.deepEqual(config.data, {
    name: 'demo-flow',
    description: 'before',
    isEnabled: true,
    wrapped: true,
  })

  config.saveAction({ description: 'after' })
  assert.deepEqual(saved, {
    name: 'demo-flow',
    description: 'after',
    isEnabled: true,
  })
  assert.equal(flushes, 1)
})

test('buildFlowPanelConfig routes structured flow field diagnostics to fieldErrors', () => {
  const graph = {
    getLogicFlowProps() {
      return { name: 'demo-flow', entryNode: '' }
    },
    updateLogicFlowProps() {},
  }

  const config = buildFlowPanelConfig({
    graph,
    getFlowDefaultProperties: () => [{ name: 'entryNode', type: 'entry-node' }],
    getValidationEntries: () => [
      {
        message: '入口节点未找到',
        scope: 'flow',
        fieldPath: 'entryNode',
      },
    ],
    wrapData: (data) => data,
  })

  assert.deepEqual(config.fieldErrors, {
    entryNode: ['入口节点未找到'],
  })
  assert.deepEqual(config.formItems, [{ name: 'entryNode', type: 'entry-node' }])
})

test('buildFlowPanelConfig keeps selected invalid node diagnostics out of entryNode field errors', () => {
  const graph = {
    getLogicFlowProps() {
      return { name: 'demo-flow', entryNode: '脚本节点' }
    },
    updateLogicFlowProps() {},
  }

  const config = buildFlowPanelConfig({
    graph,
    getFlowDefaultProperties: () => [{ name: 'entryNode', type: 'entry-node' }],
    getValidationEntries: () => [
      {
        message: '脚本节点配置错误',
        scope: 'flow',
        fieldPath: 'entryNode',
        nodeName: '脚本节点',
      },
    ],
    wrapData: (data) => data,
  })

  assert.deepEqual(config.fieldErrors, {})
  assert.equal(config.formItems.at(-1).body, '脚本节点配置错误')
})

test('buildFlowPanelConfig hides output section when return node exists and exitNode is empty', () => {
  const graph = {
    getLogicFlowProps() {
      return { name: 'demo-flow', exitNode: '' }
    },
    getNodes() {
      return [
        {
          getData() {
            return { name: '成功返回', type: 'return' }
          },
        },
      ]
    },
    updateLogicFlowProps() {},
  }

  const config = buildFlowPanelConfig({
    graph,
    getFlowDefaultProperties: () => [
      { type: 'fieldSet', title: '输入', body: [{ name: 'entryNode' }] },
      { type: 'fieldSet', title: '输出', body: [{ name: 'exitNode' }] },
    ],
    getValidationEntries: () => [],
    wrapData: (data) => data,
  })

  assert.deepEqual(config.formItems, [
    { type: 'fieldSet', title: '输入', body: [{ name: 'entryNode' }] },
  ])
})

test('buildFlowPanelConfig keeps output section when exitNode is explicitly configured', () => {
  const graph = {
    getLogicFlowProps() {
      return { name: 'demo-flow', exitNode: '成功返回' }
    },
    getNodes() {
      return [
        {
          getData() {
            return { name: '成功返回', type: 'return' }
          },
        },
      ]
    },
    updateLogicFlowProps() {},
  }

  const config = buildFlowPanelConfig({
    graph,
    getFlowDefaultProperties: () => [
      { type: 'fieldSet', title: '输入', body: [{ name: 'entryNode' }] },
      { type: 'fieldSet', title: '输出', body: [{ name: 'exitNode' }] },
    ],
    getValidationEntries: () => [],
    wrapData: (data) => data,
  })

  assert.deepEqual(config.formItems, [
    { type: 'fieldSet', title: '输入', body: [{ name: 'entryNode' }] },
    { type: 'fieldSet', title: '输出', body: [{ name: 'exitNode' }] },
  ])
})

test('buildEdgePanelConfig wires edge data through wrapData and saveAction', () => {
  let saved = null
  let flushes = 0
  const edge = {
    getData() {
      return { label: '连线' }
    },
    setData(value) {
      saved = value
    },
  }
  const graph = {
    flushLogicFlowChange() {
      flushes += 1
    },
  }

  const config = buildEdgePanelConfig({
    edge,
    graph,
    getEdgeDefaultProperties: () => [{ name: 'label', type: 'input-text' }],
    getValidationEntries: () => [{ message: '边错误', nodeName: '脚本节点' }],
    wrapData: (data) => ({ ...data, wrapped: true }),
  })

  assert.equal(config.panelTitle, '连线属性')
  assert.equal(config.runtimeKey, 'edge::::')
  assert.deepEqual(config.formItems, [{ name: 'label', type: 'input-text' }])
  assert.deepEqual(config.data, { label: '连线', wrapped: true })

  config.saveAction({ label: '已更新' })
  assert.deepEqual(saved, { label: '已更新' })
  assert.equal(flushes, 1)
})

test('buildEdgePanelConfig only routes diagnostics matching the selected edge endpoint identity', () => {
  const edge = {
    getData() {
      return {
        sourceNode: 'A',
        sourceOutputPort: 0,
        targetNode: 'B',
        targetInputPort: 2,
      }
    },
    setData() {},
  }

  const config = buildEdgePanelConfig({
    edge,
    graph: {},
    getEdgeDefaultProperties: () => [{ name: 'targetInputPort', type: 'input-text' }],
    getValidationEntries: () => [
      {
        message: '当前连线目标端口错误',
        scope: 'edge',
        fieldPath: 'targetInputPort',
        sourceNode: 'A',
        sourceOutputPort: 0,
        targetNode: 'B',
        targetInputPort: 2,
      },
      {
        message: '其他连线目标端口错误',
        scope: 'edge',
        fieldPath: 'targetInputPort',
        sourceNode: 'A',
        sourceOutputPort: 0,
        targetNode: 'C',
        targetInputPort: 2,
      },
    ],
    wrapData: (data) => data,
  })

  assert.equal(config.runtimeKey, 'edge:A:0:B:2')
  assert.deepEqual(config.fieldErrors, {
    targetInputPort: ['当前连线目标端口错误'],
  })
  assert.deepEqual(config.formItems, [{ name: 'targetInputPort', type: 'input-text' }])
})

test('buildNodePanelConfig builds node form items, unique-name validator and node-scoped validation alert', () => {
  let saved = null
  let flushes = 0
  const nodeDefine = {
    type: 'script',
    label: '脚本节点',
    properties: [{ type: 'input-text', name: 'script' }],
    portRules: {
      input: { ruleType: 'limit', min: 1, max: 2 },
      output: { ruleType: 'limit', min: 1, max: 2 },
    },
  }
  const node = {
    id: 'node-current',
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return {
        name: '当前节点',
        inputPorts: [],
        outputPorts: [],
      }
    },
    setData(value, options) {
      saved = { value, options }
    },
  }
  const currentGraph = {
    getNodes() {
      return [
        node,
        {
          data: { name: '已存在节点' },
        },
      ]
    },
    flushLogicFlowChange() {
      flushes += 1
    },
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph,
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [
      { message: '当前节点配置错误', nodeName: '当前节点' },
      { message: '其他节点错误', nodeName: '其他节点' },
      { message: '流程层错误' },
    ],
    transpileAll: (items) => items.map((item) => ({ transpiled: item })),
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.equal(config.panelTitle, '节点属性')
  assert.equal(config.runtimeKey, 'node:node-current')
  assert.equal(config.formItems.length, 4)
  assert.equal(config.data.name, '当前节点')
  assert.equal(config.data.__CURRENT_NODE_NAME, '当前节点')
  assert.equal(config.data.__CURRENT_NODE_TYPE, 'script')
  assert.equal(config.data.__CURRENT_NODE_LABEL, '脚本节点')
  assert.equal(config.data.__validate_unique_name('新节点'), true)
  assert.equal(config.data.__validate_unique_name('已存在节点'), false)
  assert.deepEqual(config.formItems.at(-1), {
    type: 'alert',
    level: 'danger',
    title: '校验错误',
    showIcon: true,
    label: false,
    className: 'logicflow-property-validation-alert',
    body: '当前节点配置错误',
  })

  config.saveAction({ name: '已更新节点' })
  assert.deepEqual(saved, {
    value: { name: '已更新节点' },
    options: { overwrite: true },
  })
  assert.equal(flushes, 1)
})

test('buildNodePanelConfig routes single-node structured diagnostics to fieldErrors', () => {
  const nodeDefine = {
    type: 'callFlow',
    label: '调用流程',
    properties: [{ type: 'input-text', name: 'flowName' }],
    portRules: {},
  }
  const node = {
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return {
        name: '调用子流程',
        inputPorts: [],
        outputPorts: [],
      }
    },
    setData() {},
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph: { getNodes: () => [node] },
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [
      {
        message: '流程调用节点的流程名未配置',
        scope: 'node',
        nodeNames: ['调用子流程'],
        fieldPath: 'flowName',
      },
    ],
    transpileAll: (items) => items,
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.deepEqual(config.fieldErrors, {
    flowName: ['流程调用节点的流程名未配置'],
  })
  assert.equal(config.formItems.some((item) => item?.type === 'alert'), false)
})

test('buildNodePanelConfig falls back to panel alert when fieldPath is missing or cannot be matched', () => {
  const nodeDefine = {
    type: 'script',
    properties: [{ type: 'input-text', name: 'script' }],
    portRules: {},
  }
  const node = {
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return {
        name: '脚本节点',
        inputPorts: [],
        outputPorts: [],
      }
    },
    setData() {},
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph: { getNodes: () => [node] },
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [
      {
        message: '聚合配置错误',
        scope: 'node',
        nodeNames: ['脚本节点'],
      },
      {
        message: '字段不存在',
        scope: 'node',
        nodeNames: ['脚本节点'],
        fieldPath: 'missingField',
      },
    ],
    transpileAll: (items) => items,
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.deepEqual(config.fieldErrors, {})
  assert.deepEqual(config.formItems.at(-1), {
    type: 'alert',
    level: 'danger',
    title: '校验错误',
    showIcon: true,
    label: false,
    className: 'logicflow-property-validation-alert',
    body: {
      type: 'container',
      className: 'logicflow-property-validation-alert-list',
      body: [
        {
          type: 'tpl',
          tpl: '1. 聚合配置错误',
          className: 'logicflow-property-validation-alert-item',
        },
        {
          type: 'tpl',
          tpl: '2. 字段不存在',
          className: 'logicflow-property-validation-alert-item',
        },
      ],
    },
  })
})

test('buildNodePanelConfig matches combo child field paths without treating hidden child fields as visible', () => {
  const nodeDefine = {
    type: 'script',
    properties: [],
    portRules: {},
  }
  const node = {
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return {
        name: '脚本节点',
        inputPorts: [{ name: '' }],
        outputPorts: [{ name: 'out' }],
      }
    },
    setData() {},
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph: { getNodes: () => [node] },
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [
      {
        message: '输入端口名不能为空',
        scope: 'node',
        nodeNames: ['脚本节点'],
        fieldPath: 'inputPorts[0].name',
      },
      {
        message: '输出端口缺少互斥组',
        scope: 'node',
        nodeNames: ['脚本节点'],
        fieldPath: 'outputPorts[0].exclusiveGroupId',
      },
    ],
    transpileAll: (items) => items.map((item) => {
      if (item.type !== 'ports-editor') {
        return item
      }
      return {
        type: 'fieldSet',
        body: [
          {
            type: 'combo',
            name: item.props.name,
            items: [
              {
                type: 'group',
                body: [
                  { type: 'input-text', name: 'name' },
                  { type: 'input-text', name: 'dataType' },
                  { type: 'input-text', name: 'description' },
                ],
              },
            ],
          },
        ],
      }
    }),
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.deepEqual(config.fieldErrors, {
    'inputPorts[0].name': ['输入端口名不能为空'],
  })
  assert.equal(config.formItems.at(-1).body, '输出端口缺少互斥组')
})

test('buildNodePanelConfig keeps multi-node structured diagnostics at panel level', () => {
  const nodeDefine = {
    type: 'script',
    properties: [{ type: 'input-text', name: 'script' }],
    portRules: {},
  }
  const node = {
    attr(path) {
      if (path === 'meta/nodeDefine') return nodeDefine
      return null
    },
    getData() {
      return {
        name: '脚本节点',
        inputPorts: [],
        outputPorts: [],
      }
    },
    setData() {},
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph: { getNodes: () => [node] },
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [
      {
        message: '跨节点结构错误',
        scope: 'node',
        nodeNames: ['脚本节点', '其他节点'],
        fieldPath: 'script',
      },
    ],
    transpileAll: (items) => items,
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.deepEqual(config.fieldErrors, {})
  assert.equal(config.formItems.at(-1).body, '跨节点结构错误')
})

test('buildNodePanelConfig omits empty node-property fieldsets instead of leaking null form items', () => {
  const node = {
    attr(path) {
      if (path === 'meta/nodeDefine') {
        return {
          properties: [],
          portRules: {},
        }
      }
      return null
    },
    getData() {
      return {
        name: '无属性节点',
        inputPorts: [],
        outputPorts: [],
      }
    },
    setData() {},
  }

  const config = buildNodePanelConfig({
    node,
    currentGraph: { getNodes: () => [node] },
    getNodeDefaultProperties: () => [{ name: 'name', type: 'input-text' }],
    getValidationEntries: () => [],
    transpileAll: (items) => items.map((item) => ({ transpiled: item })),
    wrapData: (data, superProps) => ({ ...data, ...superProps }),
  })

  assert.equal(config.formItems.includes(null), false)
  assert.deepEqual(config.formItems, [
    { name: 'name', type: 'input-text' },
    {
      type: 'container',
      body: [
        {
          transpiled: {
            type: 'ports-editor',
            props: { name: 'inputPorts', title: '输入端口' },
          },
        },
        {
          transpiled: {
            type: 'ports-editor',
            props: { name: 'outputPorts', title: '输出端口' },
          },
        },
      ],
    },
  ])
})
