import {
  areOutputPortsMutex,
  getMutexOutputPortPairs,
  hasSelectiveOutputs,
} from './outputPortSemantics.mjs'

/**
 * 查找入度为0的节点
 * @param {*} flowModel
 * @returns
 */
function findZeroIndegreeNodes(flowModel) {
  let connections = getEnabledConnections(flowModel)
  let nodes = getEnabledNodes(flowModel)
  let indegreeMap = new Map()

  // 初始化所有节点的入度为 0
  nodes.forEach((node) => indegreeMap.set(node.name, 0))

  // 计算每个节点的入度
  connections.forEach((connection) => {
    let targetNode = connection.targetNode
    let indegree = indegreeMap.get(targetNode) + 1
    indegreeMap.set(targetNode, indegree)
  })

  // 找到入度为 0 的节点
  let indegreeNodes = []
  indegreeMap.forEach((indegree, node) => {
    if (indegree === 0) {
      indegreeNodes.push(node)
    }
  })

  return indegreeNodes
}
/**
 * 检查所有节点的输入端口是否都已经连接
 *
 * @param flowModel 逻辑流模型
 * @return true：所有节点的输入端口都已经连接；false：存在节点的输入端口未连接
 */
export function checkAllInputPortsConnected(flowModel) {
  // 找到所有入度节点，不在编译期间校验
  const allNodes = getEnabledNodes(flowModel)
  const allConnections = getEnabledConnections(flowModel)
  const zeroIndegreeNodes = findZeroIndegreeNodes(flowModel)
  const result = allNodes.map((node) => {
    if (zeroIndegreeNodes.includes(node.name)) return [true]
    const inPortsCount = node.inputPorts.length
    const incoming = allConnections.filter((conn) => conn.targetNode === node.name)
    const unconnected = Array.from({ length: inPortsCount }, (_, i) => i).filter(
      (index) => !incoming.some((conn) => conn.targetInputPort === index)
    )
    return [unconnected.length === 0, node, unconnected]
  })
  const unconnectedNodes = result
    .filter(([connected]) => !connected)
    .map(([, node, unconnected]) => ({ name: node.name, ports: unconnected }))
  return [unconnectedNodes.length === 0, unconnectedNodes]
}
/**
 * 检查输入端口互斥
 * <p>连接同一个输入端口的所有上游路径必须是互斥的，即路径上必须能证明来自同一节点的互斥输出端口组</p>
 * <ol>
 *   <li>先找出所有同一个输入端口有多个上游路径的节点+端口 </li>
 *   <li>对于每个节点+输入端口，找出所有上游路径 </li>
 *   <li>检查每个节点+输入端口的所有上游路径是否互斥 </li>
 *   <li>如果所有路径都互斥，则返回true，否则返回false </li>
 * </ol>
 *
 * @param flowModel 逻辑流模型
 * @return true：所有输入端口互斥；false：存在输入端口不互斥
 */
export function checkInputPortConflicts(flowModel, getNodeCapabilities = () => ({})) {
  const allConnections = getEnabledConnections(flowModel)
  const nodes = getEnabledNodes(flowModel)
  // 连接同一个输入端口的所有上游路径必须是互斥的，即必须最终能回溯到同一节点上的互斥输出端口对
  // 1. 先找出所有同一个输入端口有多个上游路径的节点+端口
  const nodesWithMultipleInConnections = nodes
    .map((node) => {
      const incoming = (
        allConnections.filter((connection) => connection.targetNode === node.name) || []
      ).reduce((acc, connection) => {
        const targetInputPort = connection.targetInputPort
        if (!acc[targetInputPort]) {
          acc[targetInputPort] = []
        }
        acc[targetInputPort].push(connection)
        return acc
      }, {})
      return { node, incoming }
    })
    .filter(({ incoming }) =>
      Object.values(incoming).some((connections) => connections.length > 1)
    )
    .flatMap(({ node, incoming }) =>
      Object.entries(incoming).map(([port, connections]) => ({ node, port, connections }))
    )

  // 2. 对于每个节点+输入端口，找出所有上游路径
  const paths = nodesWithMultipleInConnections.map(({ node, port, connections }) => {
    const pathList = getUpstreamNodesPaths(flowModel, node, port, connections, [], allConnections)
    return { node, port, pathList }
  })
  // 3. 检查每个节点+输入端口的所有上游路径是否互斥
  return paths.every(({ pathList }) => {
    // 只有一个路径，必然互斥
    if (pathList.length < 2) {
      return true
    }
    // 检查每个路径是否互斥
    for (let i = 0; i < pathList.length; i++) {
      const path1 = pathList[i]
      for (let j = i + 1; j < pathList.length; j++) {
        const path2 = pathList[j]
        // 不互斥则代表有冲突，直接返回false
        if (!isPathMutex(path1, path2, getNodeCapabilities)) {
          return false
        }
      }
    }
    return true
  })
}
/**
 * 判断两个路径是否互斥
 * <ul>
 *   <li>如果上游路径中有相同节点，且两个输出端口可由共享 exclusiveGroupId 证明互斥，则互斥</li>
 *   <li>如果节点本身没有可识别的互斥输出语义，则非互斥</li>
 *   <li>如果两条路径中找不到能证明互斥的同节点输出端口对，则非互斥</li>
 * </ul>
 *
 * @param path1 路径1
 * @param path2 路径2
 * @return true：互斥；false：非互斥
 */
function isPathMutex(path1, path2, getNodeCapabilities) {
  for (const [node1, port1] of path1) {
    for (const [node2, port2] of path2) {
      if (
        node1.name === node2.name &&
        areOutputPortsMutex(node1, port1, port2, getNodeCapabilities)
      ) {
        return true
      }
    }
  }
  return false
}

/**
 * 获取节点+输入端口的所有上游路径
 *
 * @param node 节点
 * @param port 输入端口
 * @param connections 上游连接
 * @param path 当前路径
 * @return 所有上游路径列表
 */
function getUpstreamNodesPaths(flowModel, _node, port, connections, path = [], allConnections) {
  const incomingConnections = connections.filter(
    (connection) => connection.targetInputPort == port
  )

  let result = []
  if (incomingConnections.length === 0) {
    // 当没有上游节点时，返回单独的路径
    result.push(path)
    return result
  }
  // 对每个上游路径执行递归调用
  for (const connection of incomingConnections) {
    const sourceNode = flowModel.nodes.find((node) => node.name === connection.sourceNode)

    const sourcePort = connection.sourceOutputPort
    const targetPort = connection.targetInputPort
    const newPath = [[sourceNode, sourcePort], ...path]
    const inConnections =
      allConnections.filter((connection) => connection.targetNode === sourceNode.name) || []
    result = result.concat(
      getUpstreamNodesPaths(flowModel, sourceNode, targetPort, inConnections, newPath, allConnections)
    )
  }

  return result
}

/**
 * 检查竞态条件
 * 条件选择节点的输出端口不能连接到同一个节点的不同端口
 * <ol>
 *   <li>找出所有条件选择节点</li>
 *   <li>找出所有条件选择节点每个输出端口的下游路径</li>
 *   <li>检查是否存在不同输出端口连接到同一个节点的不同输入端口的情况</li>
 * <ol/>
 *
 * @param flowModel 逻辑流模型
 * @return true：不存在竞态条件；false：存在竞态条件
 */
export function checkRacingCondition(flowModel, getNodeCapabilities = () => ({})) {
  const nodes = flowModel.nodes.filter((node) => node.isEnabled)
  // 1. 找出所有条件选择节点
  const decisionNodes = nodes.filter((node) => hasSelectiveOutputs(node, getNodeCapabilities))
  // 2. 找出所有条件选择节点每个输出端口的下游路径
  return decisionNodes.every((decisionNode) => {
    const mutexPortPairs = getMutexOutputPortPairs(decisionNode, getNodeCapabilities)
    if (mutexPortPairs.length === 0) {
      return true
    }

    const pathCache = new Map()
    const getPortPaths = (port) => {
      if (!pathCache.has(port)) {
        pathCache.set(port, getDownstreamNodesPaths(flowModel, [decisionNode, port]))
      }
      return pathCache.get(port)
    }

    // 3. 检查是否存在不同输出端口连接到同一个节点的不同输入端口的情况
    return mutexPortPairs.every(([leftPort, rightPort]) => {
      return !isPathCollectionRacing(getPortPaths(leftPort), getPortPaths(rightPort))
    })
  })
}

/**
 * 检查两组路径之间是否存在竞态条件
 *
 * @param paths1 路径组1
 * @param paths2 路径组2
 * @return true：不存在竞态条件；false：存在竞态条件
 */
function isPathCollectionRacing(paths1 = [], paths2 = []) {
  if (paths1.length === 0 || paths2.length === 0) {
    return false
  }

  for (const path1 of paths1) {
    for (const path2 of paths2) {
      if (isPathRacing(path1, path2)) {
        return true
      }
    }
  }

  return false
}
/**
 * 检查两个路径是否存在竞态条件
 *
 * @param path1 路径1
 * @param path2 路径2
 * @return true：存在竞态条件；false：不存在竞态条件
 */

function isPathRacing(path1, path2) {
  for (const [node1, port1] of path1) {
    for (const [node2, port2] of path2) {
      if (node1 !== node2) continue
      // 如果两个路径连接到同一个节点的不同端口，则存在竞态条件
      if (port1 !== port2) return true
    }
  }
  return false
}
/**
 * 获取节点+输出端口的所有下游路径
 *
 * @param nodePort 节点 + 输出端口
 * @return 所有下游路径列表
 */

function getDownstreamNodesPaths(flowModel, nodePort) {
  const allConnections = getEnabledConnections(flowModel)
  const enabledNodes = getEnabledNodes(flowModel)
  const [node, port] = nodePort
  const outgoingConnections = (
    allConnections.filter((connection) => connection.sourceNode === node.name) ||
    []
  ).filter((connection) => {
    return connection.sourceOutputPort === port
  })

  const result = []
  if (outgoingConnections.length === 0) {
    result.push([])
    return result
  }

  // 对每个下游路径执行递归调用
  for (const connection of outgoingConnections) {
    const targetNode = enabledNodes.find((node) => node.name === connection.targetNode)
    const targetPort = connection.targetInputPort
    const targetNodeOutputCount = targetNode.outputPorts.length
    const paths = Array.from({ length: targetNodeOutputCount }, (_, i) => {
      return getDownstreamNodesPaths(flowModel, [targetNode, i]).map((path) => {
        path.unshift([targetNode, targetPort])
        return path
      })
    }).flat()
    result.push(...paths)
  }
  return result
}

/**
 * 检查所有节点是否都已经连接
 * @param flowModel
 * @return
 */
export function checkAllNodesConnected(flowModel) {
  const nodes = flowModel.getNodes()
  for (const node of nodes) {
    const connectedEdges = flowModel.getConnectedEdges(node, {
      incoming: true,
      outgoing: true,
    })
    const isFullyConnected = connectedEdges.length > 0
    // 判断节点是否连上线
    if (!isFullyConnected) {
      return false
    }
  }
  return true
}

function getEnabledNodes(flowModel) {
  return (flowModel.nodes || []).filter((node) => node?.isEnabled !== false)
}

function getEnabledConnections(flowModel) {
  const enabledNodeNames = new Set(getEnabledNodes(flowModel).map((node) => node.name))
  return (flowModel.connections || []).filter(
    (connection) =>
      enabledNodeNames.has(connection.sourceNode) && enabledNodeNames.has(connection.targetNode)
  )
}
