// @vitest-environment jsdom
import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, expect, test, vi } from 'vitest'
import { initAmisComponentLib } from 'ouroboros-sdk'
import LeftPanel from '../../../src/logicFlowEditor/components/leftPanel/index.jsx'
import LogicFlowDiagnosticsSurface from '../../../src/logicFlowEditor/components/toolbar/LogicFlowDiagnosticsSurface.jsx'
import { createEditorInteractionActionsBridge } from '../../../src/logicFlowEditor/orchestration/editorInteractionActionsBridge.mjs'
import { createEditorPanelBridge } from '../../../src/logicFlowEditor/orchestration/editorBridgeHelpers.mjs'
import { deleteInputPortsWithDialog } from '../../../src/logicFlowEditor/interactions/logicFlowInteractionController.mjs'
import { normalizeValidationEntries } from '../../../src/logicFlowEditor/debug/persistedValidationDiagnostics.mjs'
import { buildLogicFlowDiagnosticsSurfaceContract } from '../../../src/logicFlowEditor/orchestration/editorDiagnosticsSurface.mjs'
import { LogicFlowPlugin } from '../../../src/node/LogicFlowPlugin.js'
import { transpileToX6Data } from '../../../src/node/transpile.js'
import { t } from '../../../src/i18n.js'
import { useLocale } from '../../../src/useLocale.js'

const listeners = new Set()
let locale = 'en-US'
const translations = {
  搜索节点: 'Search nodes',
  隐藏节点面板: 'Hide nodes',
  隐藏属性面板: 'Hide properties',
  审批: 'Approve',
  常量: 'Constant',
  无匹配节点: 'No matches',
  '端口{{index}}': 'Port {{index}}',
  '端口{{index}}（{{name}}）': 'Port {{index}} ({{name}})',
  '删除后端口数量不能小于{{min}}': 'At least {{min}} ports must remain',
  '节点「{{p0}}」的输入端口「{{p1}}」未连接': 'Input "{{p1}}" of "{{p0}}" is not connected',
  '节点「{{p0}}」的第 {{p1}} 个输入端口未连接': 'Input {{p1}} of "{{p0}}" is not connected',
  '发现并跳过 {{count}} 条无效连线：{{detail}}': 'Skipped {{count}} invalid connections: {{detail}}',
  '{{reason}} {{count}} 条': '{{reason}}: {{count}}',
  源节点不存在: 'Missing source node',
}
const platformI18n = {
  getLocale: () => locale,
  onLocaleChange: (callback) => {
    listeners.add(callback)
    return () => listeners.delete(callback)
  },
  tn: vi.fn((_namespace, key, fallback, params = {}) =>
    (locale === 'en-US' ? translations[key] || fallback : fallback).replace(
      /\{\{(\w+)\}\}/g,
      (_, name) => String(params[name] ?? '')
    )
  ),
}
let root
let container
async function mount(element) {
  globalThis.IS_REACT_ACT_ENVIRONMENT = true
  locale = 'en-US'
  await initAmisComponentLib({}).mount({ i18n: platformI18n })
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  await act(async () => root.render(element))
}
afterEach(async () => {
  if (root) await act(async () => root.unmount())
  container?.remove()
  root = undefined
  container = undefined
  expect(listeners.size).toBe(0)
  vi.clearAllMocks()
})

test('palette uses its development namespace and refreshes existing inputs on language changes', async () => {
  const nodes = [
    { type: 'const', label: '常量', group: '通用', scaffold: { name: '常量_1' } },
  ]
  const before = structuredClone(nodes)
  await mount(React.createElement(LeftPanel, { nodeDefineList: nodes }))
  const input = container.querySelector('input[type="search"]')
  expect(input.placeholder).toBe('Search nodes')
  expect(container.querySelector('[aria-label="Hide nodes"]')).not.toBeNull()
  expect(container.textContent).toContain('Constant')
  expect(platformI18n.tn).toHaveBeenCalledWith(
    'logicflow-editor',
    '搜索节点',
    '搜索节点',
    undefined
  )
  await act(async () => {
    locale = 'zh-CN'
    for (const listener of listeners) listener(locale)
  })
  expect(container.querySelector('input[type="search"]')).toBe(input)
  expect(input.placeholder).toBe('搜索节点')
  expect(nodes).toEqual(before)
})

test('delete-port dialog translates dynamic labels and validation without changing port data', async () => {
  await initAmisComponentLib({}).mount({ i18n: platformI18n })
  locale = 'en-US'
  const data = { inputPorts: [{ name: '原始输入' }, {}] }
  const before = structuredClone(data)
  let dialog
  const bridge = createEditorInteractionActionsBridge({
    translate: t,
    openDialogFn: async (schema) => { dialog = schema; return false },
    deleteInputPortsWithDialogFn: deleteInputPortsWithDialog,
  })
  await bridge.showDeleteInputPortDialog({
    getData: () => data,
    attr: () => ({ portRules: { input: { min: 1 } } }),
  }, {})
  const control = dialog.body.body[0]
  expect(control.options).toEqual([
    { label: 'Port 0 (原始输入)', value: 0 },
    { label: 'Port 1', value: 1 },
  ])
  expect(control.validate({}, [0, 1])).toBe('At least 1 ports must remain')
  expect(control.validate({}, [0])).toBeUndefined()
  expect(data).toEqual(before)
})

test('runtime diagnostic templates remain translatable after normalization and language changes', async () => {
  const flow = {
    name: 'flow', entryNode: 'start', exitNode: '', errorEntryNode: '',
    nodes: [
      { type: 'action', name: 'start', inputPorts: [], outputPorts: [{ name: 'next' }] },
      { type: 'action', name: '原始节点', inputPorts: [{ name: 'first' }, { name: '原始端口' }, {}], outputPorts: [] },
    ],
    connections: [{ sourceNode: 'start', sourceOutputPort: 0, targetNode: '原始节点', targetInputPort: 0 }],
  }
  const x6Data = transpileToX6Data(flow, [{ type: 'action', title: '处理' }])
  const trigger = vi.fn()
  const plugin = new LogicFlowPlugin({ nodeDefines: [] })
  plugin.graph = {
    getCells: () => [...x6Data.nodes, ...x6Data.edges].map((cell) => ({ toJSON: () => cell })),
    getNodes: () => [], disableHistory() {}, enableHistory() {}, trigger,
  }
  plugin.logicFlowProps = flow
  plugin.dispatchLogicFlowChange()
  const payload = trigger.mock.calls.find(([name]) => name === 'logicflow:validateFailed')[1]
  const before = structuredClone(payload.data)
  const contract = buildLogicFlowDiagnosticsSurfaceContract({
    validationErrors: normalizeValidationEntries(payload.entries),
  })
  function Diagnostics() {
    useLocale()
    return React.createElement(LogicFlowDiagnosticsSurface, { diagnosticsContract: contract })
  }
  await mount(React.createElement(Diagnostics))
  await act(async () => container.querySelector('button').click())
  expect(container.textContent).toContain('Input "原始端口" of "原始节点" is not connected')
  expect(container.textContent).toContain('Input 3 of "原始节点" is not connected')
  await act(async () => {
    locale = 'zh-CN'
    for (const listener of listeners) listener(locale)
  })
  expect(container.textContent).toContain('节点「原始节点」的输入端口「原始端口」未连接')
  expect(payload.data).toEqual(before)
})

test('invalid-connection warnings translate reason summaries and counts', async () => {
  locale = 'en-US'
  await initAmisComponentLib({}).mount({ i18n: platformI18n })
  const toast = vi.fn()
  const bridge = createEditorPanelBridge({ propertiesPanelRef: {}, toastFn: toast, translate: t })
  bridge.showTranspileWarnings({ warnings: [{ payload: { reason: 'missing-source-node' } }] })
  expect(toast).toHaveBeenCalledWith('Skipped 1 invalid connections: Missing source node: 1', 'warning')
})
