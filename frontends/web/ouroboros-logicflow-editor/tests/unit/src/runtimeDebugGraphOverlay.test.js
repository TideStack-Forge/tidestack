import { describe, expect, it } from 'vitest'
import { createRuntimeDebugGraphOverlay, runtimeGraphMatchesEditor } from '../../../src/logicFlowEditor/debug/runtimeDebugGraphOverlay.mjs'
import { connectionKey } from '../../../src/logicFlowEditor/debug/runtimeDebugReplay.mjs'

describe('immutable runtime canvas presentation', () => {
  const nodes = [
    { name: 'entry', type: 'Entry', inputPorts: [{}], outputPorts: [{}] },
    { name: 'script', type: 'Script', inputPorts: [{}], outputPorts: [{}] },
  ]
  const connection = { sourceNode: 'entry', sourceOutputPort: 0, targetNode: 'script', targetInputPort: 0 }
  const snapshot = {
    flowName: 'flow',
    nodes: nodes.map((node) => ({ ...node, inputPorts: 1, outputPorts: 1 })),
    connections: [connection],
  }
  const graphFor = (currentNodes = nodes, currentEdges = [connection]) => ({
    getNodes: () => [...currentNodes, { __virtual: true }].map((data) => ({ getData: () => data })),
    getEdges: () => [...currentEdges, { __virtual: true }].map((data) => ({ getData: () => data })),
  })
  it('matches business topology while ignoring editor-only virtual entry cells', () => {
    expect(runtimeGraphMatchesEditor(graphFor(), snapshot, 'flow')).toBe(true)
  })
  it.each([
    ['another invocation', { ...snapshot, flowName: 'child' }],
    ['another node type', { ...snapshot, nodes: [snapshot.nodes[0], { ...snapshot.nodes[1], type: 'Switch' }] }],
    ['changed input ports', { ...snapshot, nodes: [snapshot.nodes[0], { ...snapshot.nodes[1], inputPorts: 2 }] }],
    ['changed output ports', { ...snapshot, nodes: [snapshot.nodes[0], { ...snapshot.nodes[1], outputPorts: 0 }] }],
    ['renamed node', { ...snapshot, nodes: [snapshot.nodes[0], { ...snapshot.nodes[1], name: 'other' }] }],
    ['removed node', { ...snapshot, nodes: snapshot.nodes.slice(0, 1) }],
    ['removed connection', { ...snapshot, connections: [] }],
    ['rewired connection', { ...snapshot, connections: [{ ...connection, targetInputPort: 1 }] }],
    ['missing snapshot', null],
  ])('rejects canvas annotation for %s', (_reason, value) => {
    expect(runtimeGraphMatchesEditor(graphFor(), value, 'flow')).toBe(false)
  })
  it('renders node outcomes and actual transfers, then clears playback by applying an empty frame', () => {
    const nodeAttrs = new Map()
    const edgeAttrs = new Map()
    const node = { getData: () => ({ name: 'script' }), getAttrs: () => ({}), setAttrs: () => nodeAttrs.clear(), attr: (key, value) => nodeAttrs.set(key, value) }
    const connection = { sourceNode: 'source', sourceOutputPort: 0, targetNode: 'script', targetInputPort: 1 }
    const edge = { getData: () => connection, getAttrs: () => ({}), setAttrs: () => edgeAttrs.clear(), attr: (key, value) => edgeAttrs.set(key, value) }
    const graph = { getNodes: () => [node], getEdges: () => [edge], findViewByCell: () => null }
    const overlay = createRuntimeDebugGraphOverlay({ graph })
    overlay.applyFrame({ nodes: new Map([['script', { state: 'failed' }]]), transfers: new Set([connectionKey(connection)]) })
    expect(nodeAttrs.get('body/stroke')).toBe('#dc2626')
    expect(edgeAttrs.get('line/strokeDasharray')).toBe('7 5')
    overlay.applyFrame({ nodes: new Map(), transfers: new Set() })
    expect(nodeAttrs.get('body/strokeWidth')).toBe(1)
    expect(edgeAttrs.get('line/strokeDasharray')).toBe('')
    overlay.restore()
    expect(nodeAttrs.size).toBe(0)
    expect(edgeAttrs.size).toBe(0)
  })
})
