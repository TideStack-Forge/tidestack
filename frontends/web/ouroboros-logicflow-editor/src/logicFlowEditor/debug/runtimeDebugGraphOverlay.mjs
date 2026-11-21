import { connectionKey } from './runtimeDebugReplay.mjs'
import cloneDeep from 'lodash/cloneDeep.js'

export const RUNTIME_NODE_COLORS = {
  idle: '#94a3b8', running: '#2563eb', completed: '#059669',
  failed: '#dc2626', skipped: '#64748b', blocked: '#b45309',
}

export const RUNTIME_NODE_LABELS = {
  idle: '尚未执行', running: '执行中', completed: '正常执行', failed: '抛出异常',
  skipped: '已跳过', blocked: '上游异常，未执行',
}

export function runtimeGraphMatchesEditor(graph, snapshot, flowName) {
  if (!snapshot || snapshot.flowName !== flowName) return false
  const nodes = graph.getNodes().filter((node) => !node.getData()?.__virtual)
  const edges = graph.getEdges().filter((edge) => !edge.getData()?.__virtual)
  if (nodes.length !== snapshot.nodes.length || edges.length !== snapshot.connections.length) return false
  const byName = new Map(nodes.map((node) => [node.getData().name, node.getData()]))
  if (!snapshot.nodes.every((node) => {
    const current = byName.get(node.name)
    return current?.type === node.type && current.inputPorts?.length === node.inputPorts
      && current.outputPorts?.length === node.outputPorts
  })) return false
  const connections = new Set(edges.map((edge) => connectionKey(edge.getData())))
  return snapshot.connections.every((connection) => connections.has(connectionKey(connection)))
}

/** Presentation only: retain the editor cells, positions, data and history. */
export function createRuntimeDebugGraphOverlay({ graph }) {
  const originals = new Map([...graph.getNodes(), ...graph.getEdges()].map((cell) => [cell, cloneDeep(cell.getAttrs())]))
  function withoutHistory(work) {
    const enabled = graph.isHistoryEnabled?.()
    if (enabled) graph.disableHistory()
    try { work() } finally { if (enabled) graph.enableHistory() }
  }
  function restore() {
    withoutHistory(() => originals.forEach((attrs, cell) => cell.setAttrs(cloneDeep(attrs), { overwrite: true })))
  }
  function applyFrame(frame, { animate = true, selectedNode = '' } = {}) {
    withoutHistory(() => {
      graph.getNodes().forEach((node) => {
        if (node.getData()?.__virtual) return
        const state = frame.nodes.get(node.getData().name)?.state || 'idle'
        node.attr('body/stroke', RUNTIME_NODE_COLORS[state])
        node.attr('body/strokeWidth', node.getData().name === selectedNode ? 3 : state === 'idle' ? 1 : 2)
        node.attr('body/fill', state === 'failed' ? '#fef2f2' : '#fff')
        node.attr('body/style/filter', node.getData().name === selectedNode ? 'drop-shadow(0 2px 4px rgba(37, 99, 235, .18))' : '')
      })
      graph.getEdges().forEach((edge) => {
        if (edge.getData()?.__virtual) return
        const active = frame.transfers.has(connectionKey(edge.getData()))
        edge.attr('line/stroke', active ? '#2563eb' : '#94a3b8')
        edge.attr('line/strokeWidth', active ? 3 : 1)
        edge.attr('line/strokeDasharray', active ? '7 5' : '')
        edge.attr('line/style/animation', active && animate ? 'logicflow-debug-stream 600ms linear infinite' : '')
      })
    })
  }
  return { applyFrame, restore }
}
