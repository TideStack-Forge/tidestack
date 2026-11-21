import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import RuntimeDebugWorkbench from '../../../src/logicFlowEditor/debug/RuntimeDebugWorkbench.jsx'
import { createDevRuntimeDebugHost } from '../../../src/logicFlowEditor/debug/devRuntimeDebugHost.jsx'
import { Graph } from '@antv/x6'
import { History } from '@antv/x6-plugin-history'
import { createX6Node, createX6Edge } from '../../../src/node/x6Cells.js'
import { ensureSvgGeometryPolyfills } from './harness/ensureSvgGeometryPolyfills.mjs'

ensureSvgGeometryPolyfills()
// jsdom has no native canvas text metrics; X6 still performs real SVG rendering/layout.
HTMLCanvasElement.prototype.getContext = () => ({ measureText: (value) => ({ width: String(value).length * 8 }) })
globalThis.IS_REACT_ACT_ENVIRONMENT = true
let host
let root
let editorGraph
const graph = {
  id: 'graph', flowName: 'flow', exactRevision: true,
  nodes: [
    { name: 'entry', type: 'Entry', inputPorts: 1, outputPorts: 1 },
    { name: 'script', type: 'Script', inputPorts: 1, outputPorts: 1 },
  ],
  connections: [{ sourceNode: 'entry', sourceOutputPort: 0, targetNode: 'script', targetInputPort: 0 }],
}
function event(seq, kind, extra = {}) {
  return { schemaVersion: '2.0', captureId: 'capture', runId: 'run', flowInvocationId: 'run', scope: 'main', graphId: 'graph', flowName: 'flow', seq, kind, payload: {}, ...extra }
}
const events = [
  event(1, 'flow.started'),
  event(2, 'node.started', { nodeName: 'entry', payload: { inputSnapshots: { value: ['hello'] } } }),
  event(3, 'node.completed', { nodeName: 'entry', payload: { outputSnapshots: { value: ['world'] } } }),
  event(4, 'input.transfer', { payload: { transfer: { ...graph.connections[0], active: true, selected: true } } }),
  event(5, 'node.failed', { nodeName: 'script', payload: { inputSnapshots: { value: ['script input'] }, exceptionSnapshot: { value: { type: 'FlowRuntimeException', message: 'script failed', frames: ['demo.Flow.invoke(Flow.java:23)'], cause: { type: 'IllegalStateException', message: 'demo failure', frames: ['demo.Script.invoke(Script.java:7)'] } } } } }),
  event(6, 'flow.failed'),
]
function fixture({ snapshot = graph } = {}) {
  let capture = { captureId: 'capture', runtimeId: 'container', profile: 'alice', state: 'ACTIVE', createdAt: 1000, runs: [] }
  const fetcher = vi.fn(async ({ url, method }) => {
    let data
    if (url.endsWith('/targets')) data = { items: [{ runtimeId: 'container', supported: true, label: 'test-app', backend: 'DOCKER' }] }
    else if (url.includes('/captures?')) data = { items: [] }
    else if (url.endsWith('/captures') && method === 'post') {
      capture = { ...capture, runs: [{ runId: 'run', startedAt: 1000, highestSeq: 6, status: 'FAILED', incomplete: false }] }
      data = capture
    } else if (url.includes('/events?')) data = { items: events, afterSeq: 6, highestSeq: 6, hasMore: false, complete: true, availability: 'AVAILABLE' }
    else if (url.includes('/graphs/')) data = snapshot
    else if (url.endsWith('/stop')) data = { ...capture, state: 'CLOSED' }
    else data = capture
    return { status: 0, data }
  })
  const dispose = vi.fn()
  return { fetcher, stomp: { subscribe: vi.fn(() => dispose) }, dispose }
}
async function mount(props) {
  const debugHost = props.host || createDevRuntimeDebugHost({ appName: 'crm', ...props })
  await act(async () => { root.render(React.createElement(RuntimeDebugWorkbench, { flowName: 'flow', graph: editorGraph, onClose: vi.fn(), host: debugHost })) })
}
async function click(label) {
  const button = [...host.querySelectorAll('button')].find((item) => item.getAttribute('aria-label') === label || item.textContent === label)
  expect(button, label).toBeTruthy()
  await act(async () => { button.click() })
}
async function until(condition) {
  await vi.waitFor(async () => { await act(async () => { await new Promise((resolve) => setTimeout(resolve, 20)) }); expect(condition(), host.innerHTML).toBeTruthy() })
}
beforeEach(() => {
  host = document.createElement('div'); document.body.appendChild(host)
  const canvasHost = document.createElement('div'); host.appendChild(canvasHost)
  editorGraph = new Graph({ container: canvasHost })
  graph.nodes.forEach((node) => editorGraph.addNode(createX6Node({ type: node.type }, { ...node, inputPorts: [{}], outputPorts: [{}] }, { id: node.name })))
  editorGraph.addEdge(createX6Edge({ source: { cell: 'entry', port: 'out-0' }, target: { cell: 'script', port: 'in-0' }, data: graph.connections[0] }))
  editorGraph.use(new History({ enabled: true }))
  const controls = document.createElement('div'); host.appendChild(controls); root = createRoot(controls)
})
afterEach(async () => {
  if (root) await act(async () => { root.unmount() })
  editorGraph.dispose(); host.remove(); vi.restoreAllMocks()
})
describe('runtime debug workbench with real X6 canvas', () => {
  test('an application host supplies different endpoints, notifications and a fixed draft target while reusing replay', async () => {
    const capture = { captureId: 'capture', runtimeId: 'draft-sandbox', state: 'ACTIVE', createdAt: 1000,
      runs: [{ runId: 'run', startedAt: 1000, highestSeq: 6, status: 'FAILED', incomplete: false }] }
    const request = vi.fn(async (url) => {
      if (url.endsWith('/targets')) return [{ runtimeId: 'draft-sandbox', label: '克隆草稿的测试环境', supported: true }]
      if (url.endsWith('/history')) return []
      if (url.endsWith('/events')) return { items: events, afterSeq: 6, complete: true }
      if (url.endsWith('/graph')) return graph
      if (url.endsWith('/stop')) return { ...capture, state: 'CLOSED' }
      return capture
    })
    const base = '/application/flow-drafts/42/debug'
    const api = {
      targets: () => request(base + '/targets'),
      captures: () => request(base + '/history'),
      start: vi.fn((data) => request(base + '/start', data)),
      read: () => request(base + '/capture'),
      renew: () => request(base + '/lease'),
      stop: () => request(base + '/stop'),
      events: () => request(base + '/events'),
      graph: () => request(base + '/graph'),
    }
    const dispose = vi.fn()
    const subscribe = vi.fn(() => dispose)
    const applicationHost = {
      key: 'application:orders:draft:42:revision:1', api, subscribe,
      TargetControl: ({ targets, runtimeId }) => React.createElement('output', { 'aria-label': '草稿测试环境' }, targets.find((target) => target.runtimeId === runtimeId)?.label),
      recordingHint: '记录当前克隆草稿的测试执行。',
    }
    await mount({ host: applicationHost })
    await until(() => host.querySelector('[aria-label="草稿测试环境"]').textContent === '克隆草稿的测试环境')
    expect(host.textContent).not.toContain('测试容器')
    expect(host.textContent).toContain('记录当前克隆草稿的测试执行。')
    await click('开始记录')
    await until(() => host.querySelector('.x6-edge path[stroke="#2563eb"]'))
    expect(api.start).toHaveBeenCalledWith({ runtimeId: 'draft-sandbox', flowName: 'flow', operationId: expect.any(String) })
    expect(subscribe).toHaveBeenCalledWith('capture', expect.any(Function))
    await click('事件时间线'); await click('5 · script · node.failed')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).toContain('demo.Script.invoke')
    await click('停止记录')
    expect(host.textContent).toContain('已停止记录')
    expect(request.mock.calls.every(([url]) => url.startsWith(base))).toBe(true)
    await act(async () => root.unmount()); root = null
    expect(dispose).toHaveBeenCalledTimes(1)
  })
  test('selecting an execution immediately shows its path and final node results; pausing flow keeps the path visible', async () => {
    const originalPosition = editorGraph.getCellById('script').position()
    editorGraph.getCellById('script').position(originalPosition.x + 40, originalPosition.y)
    await mount(fixture()); await click('开始记录')
    await until(() => host.querySelectorAll('.x6-node').length === 2)
    const edge = () => host.querySelector('.x6-edge path[stroke="#2563eb"]')
    await until(() => edge()?.style.animation.includes('logicflow-debug-stream'))
    expect(editorGraph.canUndo()).toBe(true)
    expect(host.querySelector('.x6-node[data-cell-id="script"] rect')?.getAttribute('stroke')).toBe('#dc2626')
    expect([...host.querySelectorAll('button')].find((button) => button.textContent === '完整路径')?.getAttribute('aria-pressed')).toBe('true')
    expect(host.querySelector('.logicflow-debug-step-controls').open).toBe(false)
    await click('暂停流动')
    await until(() => edge()?.style.animation === '')
    expect(edge()?.getAttribute('stroke')).toBe('#2563eb')
    await click('继续流动')
    await until(() => edge()?.style.animation.includes('logicflow-debug-stream'))
    await act(async () => { editorGraph.undo() })
    expect(editorGraph.getCellById('script').position()).toEqual(originalPosition)
    expect(editorGraph.getCellById('script').attr('body/stroke')).toBe('#dc2626')
    expect(editorGraph.canUndo()).toBe(false)
  })
  test.each([
    ['changed topology', { ...graph, connections: [] }, '当前设计与采集时的节点或连线不同'],
    ['another flow invocation', { ...graph, flowName: 'child' }, '该调用属于 child'],
  ])('keeps the original canvas and execution data available for %s', async (_reason, snapshot, message) => {
    await mount(fixture({ snapshot })); await click('开始记录')
    await until(() => host.textContent.includes(message))
    expect(editorGraph.getCellById('script').attr('body/stroke')).toBe('#ccc')
    expect(host.querySelector('.x6-edge path[stroke="#2563eb"]')).toBeNull()
    await click('事件时间线'); await click('5 · script · node.failed')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).toContain('demo failure')
    expect(editorGraph.canUndo()).toBe(false)
  })
  test('LF-DBG-UI-001 starts capture, renders runtime graph and replays selected transfer and exception', async () => {
    const setup = fixture()
    await mount(setup); await click('开始记录')
    await until(() => host.querySelectorAll('.x6-node').length === 2)
    expect(host.querySelectorAll('.x6-edge').length).toBe(1)
    expect(setup.stomp.subscribe.mock.calls[0][0]).toBe('/user/queue/dev/logicflow/debug/capture')
    await click('事件时间线')
    await click('4 · flow · input.transfer')
    await until(() => host.querySelector('.x6-edge path[stroke="#2563eb"]')?.getAttribute('stroke') === '#2563eb')
    expect(host.querySelector('.x6-edge path[stroke="#2563eb"]').style.animation).toContain('logicflow-debug-stream')
    await click('5 · script · node.failed')
    await until(() => host.querySelector('.x6-node[data-cell-id="script"] rect')?.getAttribute('stroke') === '#dc2626')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).toContain('demo.Flow.invoke')
    expect(host.textContent).toContain('抛出异常')
    expect(host.querySelector('.logicflow-debug-exception-summary').textContent).toContain('demo failure')
    await click('输入 / 输出')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).toContain('script input')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).not.toContain('demo.Flow.invoke')
    await click('异常堆栈')
    expect(host.querySelector('[aria-label="节点运行详情"]').textContent).toContain('demo.Script.invoke')
    await click('展开节点详情')
    expect(host.querySelector('[aria-label="节点运行详情"]').classList.contains('is-expanded')).toBe(true)
    await click('收起节点详情')
    await click('停止记录'); expect(host.textContent).toContain('已停止记录')
  })
  test('LF-DBG-UI-002 unmount disposes subscriptions and stops polling or renewal', async () => {
    const setup = fixture()
    await mount(setup); await click('开始记录')
    await until(() => host.querySelectorAll('.x6-node').length === 2)
    await act(async () => root.unmount()); root = null
    const calls = setup.fetcher.mock.calls.length
    expect(setup.dispose).toHaveBeenCalledTimes(1)
    await new Promise((resolve) => setTimeout(resolve, 2200))
    expect(setup.fetcher.mock.calls.length).toBe(calls)
    expect(editorGraph.getNodes()).toHaveLength(2)
    expect(editorGraph.getCellById('script').attr('body/stroke')).toBe('#ccc')
    expect(editorGraph.canUndo()).toBe(false)
  })
})
