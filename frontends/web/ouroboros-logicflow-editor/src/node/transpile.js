import {
  createX6Edge,
  createX6Node,
  createX6PhantomEdge,
  createX6VirtualFlowNode,
} from './x6Cells.js'
import { getDefalutPortData, getUniqueName } from './utils.js'
import { normalizeEditorCapabilities } from '../node-define/index.js'
import unknow from '../node-define/unknow.js'
import {
  applyStoredNodePositions,
  extractNodePositions,
} from './positionSync.mjs'
import { normalizeNodeOutputPorts } from './outputPortNormalization.mjs'
import { transpileConnectionsToX6Edges } from './edgeTranspilation.mjs'

function normalizeAutoPortRule(portRule) {
  if (portRule?.ruleType !== 'auto') return portRule
  if (typeof portRule.autoFunction === 'function') return portRule

  const autoFunctionSource =
    typeof portRule.autoFunction === 'string' && portRule.autoFunction.trim()
      ? portRule.autoFunction
      : 'return []'

  return {
    ...portRule,
    autoFunction: new Function('node', autoFunctionSource),
  }
}

function resolveAutoPortRulePorts(portRule, nodeData) {
  const normalizedRule = normalizeAutoPortRule(portRule)
  if (typeof normalizedRule?.autoFunction !== 'function') return []
  return normalizedRule.autoFunction(nodeData) || []
}

function buildNodeDefineGetter(allNodeDefines) {
  return function (type) {
    const define = allNodeDefines.find((node) => node.type === type)
    if (define) return define
    return {
      ...unknow,
      type: type,
      title: type,
      editorCapabilities: normalizeEditorCapabilities({ type }),
    }
  }
}

export function buildPortsNomalizer(nodeDefine, type, nodeData, flowData) {
  const portRule = normalizeAutoPortRule(nodeDefine.portRules?.[type])

  function normalizePortsByNodeType(finalPorts) {
    if (type !== 'output') return finalPorts
    return normalizeNodeOutputPorts(nodeDefine, { ...nodeData, outputPorts: finalPorts })
  }

  function finalizePorts(finalPorts) {
    const normalizedPorts = normalizePortsByNodeType(finalPorts)
    return normalizedPorts.map(portIdPatcher(normalizedPorts))
  }

  // 根据连线补全端口
  function fillByConnections(finalPorts, portProps = {}) {
    const connections = flowData?.connections || []
    // 根据连线补全端口
    const maxPortNo = connections
      .filter(
        type === 'input'
          ? (conn) => conn.targetNode === nodeData.name
          : (conn) => conn.sourceNode === nodeData.name
      )
      .map(
        type === 'input'
          ? (conn) => conn.targetInputPort
          : (conn) => conn.sourceOutputPort
      )
      .reduce((a, b) => Math.max(a, b), -1)
    if (maxPortNo + 1 > finalPorts.length) {
      for (let i = finalPorts.length; i < maxPortNo + 1; i++) {
        finalPorts.push(getDefalutPortData(portProps))
      }
    }
  }

  function portIdPatcher(ports) {
    const idPrefix = type === 'input' ? 'in-' : 'out-'
    const ids = ports.map((port) => port.__id).filter((id) => id)
    return (port) => {
      const id = getUniqueName(idPrefix, ids)
      ids.push(id)
      return { __id: id, ...port }
    }
  }

  // 固定端口
  if (portRule?.ruleType === 'fixed') {
    return () => {
      const finalPorts = [...(portRule.fixedPorts || [])]
      fillByConnections(finalPorts, { __is_error: true })
      return finalizePorts(finalPorts)
    }
  }

  // 自动端口
  if (portRule?.ruleType === 'auto') {
    return () => {
      const finalPorts = resolveAutoPortRulePorts(portRule, nodeData)
      fillByConnections(finalPorts)
      return finalizePorts(finalPorts)
    }
  }

  // 限制端口
  return (ports) => {
    const finalPorts = []
    if (ports?.length) {
      finalPorts.push(...ports)
    }

    // 根据连线补全端口
    fillByConnections(finalPorts)

    // 根据limit规则补全端口
    if (portRule?.ruleType === 'limit') {
      const limit = portRule.min || 0
      for (let i = finalPorts.length; i < limit; i++) {
        finalPorts.push(getDefalutPortData())
      }
    }
    return finalizePorts(finalPorts)
  }
}

export function buildInputPortsNomalizer(nodeDefine, nodeData, flowData) {
  return buildPortsNomalizer(nodeDefine, 'input', nodeData, flowData)
}

export function buildOutputPortsNomalizer(nodeDefine, nodeData, flowData) {
  return buildPortsNomalizer(nodeDefine, 'output', nodeData, flowData)
}

export function transpileToX6Data(flowData, allNodeDefines, options = {}) {
  const x6Data = {
    nodes: [],
    edges: [],
  }
  const { onWarning, warnFn = (...args) => console?.warn?.(...args) } = options
  const { collectDiagnosticFn = () => {} } = options
  const getNodeDefine = buildNodeDefineGetter(allNodeDefines)
  const nodes = flowData.nodes || []
  const connections = flowData.connections || []
  if (!nodes || !nodes.length) return x6Data
  // 节点端口信息
  const nodePortsMap = {}
  const nodesByName = nodes.reduce((acc, nodeData) => {
    if (nodeData?.name) acc[nodeData.name] = nodeData
    return acc
  }, {})
  // 创建节点
  nodes.forEach((nodeData, index) => {
    if (!nodeData) return
    const nodeDefine = getNodeDefine(nodeData.type)
    if (!nodeDefine) return

    const inputPortNormalizer = buildInputPortsNomalizer(nodeDefine, nodeData, flowData)
    const outputPortNormalizer = buildOutputPortsNomalizer(nodeDefine, nodeData, flowData)

    const inputPorts = inputPortNormalizer(nodeData.inputPorts)
    const outputPorts = outputPortNormalizer(nodeData.outputPorts)

    // 绘制节点
    const node = createX6Node(
      nodeDefine,
      { ...nodeData, inputPorts, outputPorts },
      { id: 'node' + index }
    )
    // 存在节点与端口关系
    nodePortsMap[nodeData.name] = {
      inputPorts,
      outputPorts,
      nodeId: 'node' + index,
    }
    if (nodeData.name === flowData?.entryNode) {
      x6Data.nodes.push(createX6VirtualFlowNode('entry', { id: 'virtual-entry-node' }))
    }
    if (nodeData.name === flowData?.errorEntryNode) {
      x6Data.nodes.push(
        createX6VirtualFlowNode('errorEntry', { id: 'virtual-error-entry-node' })
      )
    }
    x6Data.nodes.push(node)
    if (
      nodeData.name === flowData?.exitNode &&
      !hasDirectReturnEdge(connections, nodesByName, flowData?.exitNode)
    ) {
      x6Data.nodes.push(createX6VirtualFlowNode('return', { id: 'virtual-return-node' }))
    }
  })

  // 创建连接
  x6Data.edges.push(
    ...transpileConnectionsToX6Edges({
      connections,
      nodePortsMap,
      createEdgeFn: () => createX6Edge(),
      warnFn: (message, payload) => {
        warnFn?.(message, payload)
        onWarning?.({ message, payload })
      },
      collectDiagnosticFn,
    })
  )

  x6Data.edges.push(...buildEditorPhantomEdges({ flowData, nodesByName, nodePortsMap }))
  return applyStoredNodePositions(x6Data, flowData)
}

export function transpileToLogicFlow(x6Cells, flowProps, withPosition = false) {
  const connections = []
  const nodes = []
  x6Cells.forEach((cell) => {
    if (cell?.data?.__virtual) return
    if (cell.shape === 'edge') {
      const { __id: _id, __is_error: _isError, ...conn } = cell.data || {}
      connections.push(conn)
    } else {
      const data = cell.data
      nodes.push({ ...data })
    }
  })
  const data = flowProps ? { ...flowProps } : {}
  data.nodes = nodes
  data.connections = connections
  if (withPosition) {
    data.nodePositions = extractNodePositions(x6Cells)
  }
  return data
}

function buildEditorPhantomEdges({ flowData, nodesByName, nodePortsMap }) {
  const edges = []
  const entryNodeName = flowData?.entryNode
  const errorEntryNodeName = flowData?.errorEntryNode
  const exitNodeName = flowData?.exitNode

  if (entryNodeName && nodePortsMap[entryNodeName]) {
    edges.push(
      createX6PhantomEdge('entry', {
        source: { cell: 'virtual-entry-node' },
        target: { cell: nodePortsMap[entryNodeName].nodeId },
      })
    )
  }

  if (errorEntryNodeName && nodePortsMap[errorEntryNodeName]) {
    edges.push(
      createX6PhantomEdge('errorEntry', {
        source: { cell: 'virtual-error-entry-node' },
        target: { cell: nodePortsMap[errorEntryNodeName].nodeId },
      })
    )
  }

  if (
    exitNodeName &&
    nodePortsMap[exitNodeName] &&
    !hasDirectReturnEdge(flowData?.connections || [], nodesByName, exitNodeName)
  ) {
    edges.push(
      createX6PhantomEdge('return', {
        source: { cell: nodePortsMap[exitNodeName].nodeId },
        target: { cell: 'virtual-return-node' },
      })
    )
  }

  return edges
}

function hasDirectReturnEdge(connections = [], nodesByName = {}, exitNodeName) {
  return connections.some((connection) => {
    if (connection?.sourceNode !== exitNodeName) return false
    const targetNode = nodesByName[connection.targetNode]
    return targetNode?.type === 'return'
  })
}
