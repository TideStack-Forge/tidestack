import { Disposable } from '@antv/x6'
import {
  transpileToX6Data,
  transpileToLogicFlow,
  buildInputPortsNomalizer,
  buildOutputPortsNomalizer,
} from './transpile.js'
import { applyStoredNodePositions } from './positionSync.mjs'
import lodash from 'lodash'
import {
  activeEdgeStyle,
  activeTargetMarkerStyle,
  createX6NodeInputPort,
  createX6NodeOutputPort,
  defaultEdgeStyle,
  defaultTargetMarkerStyle,
  hoverEdgeStyle,
  hoverTargetMarkerStyle,
  incomingEdgeColor,
  outgoingEdgeColor,
  updateNodeSize,
} from './x6Cells.js'
import {
  checkAllInputPortsConnected,
  checkRacingCondition,
  checkInputPortConflicts,
} from './validate.js'
import { buildCapabilityGetter } from '../node-define/index.js'

const { isEqual, debounce } = lodash

export class LogicFlowPlugin extends Disposable {
  name = 'logicflow'
  /**
   * @type {Graph?}
   */
  graph = null
  options = null
  enabled = false
  data = null
  logicFlowProps = null
  layout = null
  nodeDefines = []
  runtimeValidationNodeNames = []
  externalValidationNodeNames = []
  isHydrating = false
  saveNodePositions = false
  constructor(options = {}) {
    super(options)
    this.options = options
    this.layout = options.layout
    this.data = options.data
    this.nodeDefines = options.nodeDefines
    this.enabled = options.enabled !== false
    this.saveNodePositions = options.saveNodePositions === true

    this.onEdgeDataChange = this.onEdgeDataChange.bind(this)
    this.onEdgeRemoved = this.onEdgeRemoved.bind(this)
    this.onEdgeConnected = this.onEdgeConnected.bind(this)

    this.onNodeDataChange = this.onNodeDataChange.bind(this)
    this.onNodePositionChange = this.onNodePositionChange.bind(this)
    this.onNodePortAdded = this.onNodePortAdded(this)
    this.onNodePortChange = this.onNodePortChange.bind(this)
    this.onNodeRemoved = this.onNodeRemoved.bind(this)

    this.onViewMounted = this.onViewMounted.bind(this)

    this.edgeSelectedStyle = this.edgeSelectedStyle.bind(this)
    this.edgeResetStyle = this.edgeResetStyle.bind(this)
    this.edgeHoverStyle = this.edgeHoverStyle.bind(this)
    this.edgeUnhoverStyle = this.edgeUnhoverStyle.bind(this)
    this.nodeSelectedStyle = this.nodeSelectedStyle.bind(this)
    this.nodeUnselectedStyle = this.nodeUnselectedStyle.bind(this)

    this.onRenderDone = this.onRenderDone.bind(this)
    this.updateFlowRoleMarks = this.updateFlowRoleMarks.bind(this)
    this.dispatchLogicFlowChange = this.dispatchLogicFlowChange.bind(this)
    this.triggerChange = debounce(this.dispatchLogicFlowChange, 500)
  }

  // 初始化插件
  init(graph) {
    this.graph = graph
    this.graph.updateLogicFlowProps = (props) => {
      const previousProps = this.logicFlowProps || {}
      const hasChange = !isEqual(props, previousProps)
      const needRehydrateRoleCells =
        props.entryNode !== previousProps.entryNode ||
        props.exitNode !== previousProps.exitNode ||
        props.errorEntryNode !== previousProps.errorEntryNode
      this.logicFlowProps = props
      if (needRehydrateRoleCells && this.data) {
        const nextData = {
          ...this.data,
          ...props,
        }
        this.withoutHistory(() => {
          this.initData(nextData, this.nodeDefines)
        })
      } else if (needRehydrateRoleCells) {
        this.withoutHistory(() => {
          this.updateFlowRoleMarks()
        })
      }
      if (hasChange) this.triggerChange()
    }
    this.graph.getLogicFlowProps = () => {
      return this.logicFlowProps
    }
    this.graph.setLogicFlowPositionPersistenceEnabled = (enabled) => {
      return this.setPositionPersistenceEnabled(enabled)
    }
    this.graph.isLogicFlowPositionPersistenceEnabled = () => {
      return this.isPositionPersistenceEnabled()
    }
    this.graph.flushLogicFlowChange = () => {
      this.triggerChange.cancel?.()
      this.dispatchLogicFlowChange()
    }
    this.graph.isLogicFlowHydrating = () => {
      return this.isHydrating
    }
    this.graph.rehydrateLogicFlowGraph = () => {
      if (!this.data) return
      this.initData(this.data, this.nodeDefines)
    }
    this.graph.updateLogicFlowData = (data) => {
      if (isEqual(data, this.data)) return
      this.initData(data, this.nodeDefines)
    }
    this.graph.updateLogicFlowNodeDefines = (nodeDefines) => {
      if (isEqual(nodeDefines, this.nodeDefines)) return
      this.nodeDefines = nodeDefines || []
      if (this.data) {
        this.initData(this.data, this.nodeDefines)
      }
    }
    this.graph.updateLogicFlowValidationEntries = (entries = []) => {
      const nextValidationEntries = Array.isArray(entries) ? entries : []
      const nextNodeNames = Array.from(
        new Set(
          nextValidationEntries
            .flatMap((entry) => {
              if (typeof entry?.nodeName === 'string' && entry.nodeName.trim()) {
                return [entry.nodeName.trim()]
              }
              if (Array.isArray(entry?.nodeNames)) {
                return entry.nodeNames
                  .map((name) => (typeof name === 'string' && name.trim() ? name.trim() : null))
                  .filter(Boolean)
              }
              return []
            })
            .filter(Boolean)
        )
      )
      if (!isEqual(nextNodeNames, this.externalValidationNodeNames)) {
        this.externalValidationNodeNames = nextNodeNames
      }
      this.withoutHistory(() => {
        this.applyNodeValidationHighlights({
          runtimeInvalidNodeNames: [],
          externalInvalidNodeNames: this.externalValidationNodeNames,
        })
      })
    }
    this.graph.refreshLogicFlowValidationHighlights = () => {
      this.withoutHistory(() => {
        this.applyNodeValidationHighlights({
          runtimeInvalidNodeNames: this.runtimeValidationNodeNames,
          externalInvalidNodeNames: this.externalValidationNodeNames,
        })
      })
    }
    this.graph.layout = (customLayout) => {
      const layout = customLayout || this.layout
      const nodeDefines = this.nodeDefines
      if (layout && nodeDefines) {
        this.layout = layout
        const { x6Data, warnings, diagnostics } = collectTranspileWarnings(this.data, nodeDefines)
        const { nodes } = this.layout?.layout?.(x6Data) || x6Data
        this.graph.startBatch('layout')
        nodes.forEach((node) => {
          const cell = this.graph
            .getCells()
            .find((cell) => cell.data?.name === node.data.name)
          if (!cell) return
          const position = {
            x: node.x,
            y: node.y,
          }
          cell.prop('position', position)
        })
        this.graph.stopBatch('layout')
        this.emitTranspileWarnings(this.data, warnings, diagnostics)
      }
    }
    this.initEvent()
    this.initData(this.data, this.nodeDefines)
  }

  // 销毁插件
  dispose() {
    this.disposeEvent()
    super.dispose()
  }

  // 初始化数据
  initData(data, nodeDefines) {
    if (!data) return
    this.data = data
    const {
      nodes: _nodes,
      connections: _connections,
      nodePosition: _nodePosition,
      nodePositions: _nodePositions,
      ...props
    } = data
    this.logicFlowProps = props
    this.isHydrating = true
    try {
      const { x6Data, warnings, diagnostics } = collectTranspileWarnings(data, nodeDefines)
      const layoutedData = this.layout?.layout?.(x6Data) || x6Data
      const positionedData = applyStoredNodePositions(layoutedData, data)
      this.graph.fromJSON(positionedData)
      this.emitTranspileWarnings(data, warnings, diagnostics)
    } finally {
      this.isHydrating = false
    }
  }

  emitTranspileWarnings(data, warnings = [], diagnostics = []) {
    const nextDiagnostics = diagnostics.map((diagnostic) => ({
      ...diagnostic,
      blocking: diagnostic?.blocking !== false,
    }))
    this.graph.trigger('logicflow:transpileWarnings', { data, warnings, diagnostics: nextDiagnostics })
  }

  // 初始化事件监听
  initEvent() {
    this.initStyleEvents()
    this.graph.on('node:change:data', this.onNodeDataChange)
    this.graph.on('node:change:position', this.onNodePositionChange)
    this.graph.on('node:removed', this.onNodeRemoved)
    this.graph.on('node:ports:add', this.onNodePortAdded)
    this.graph.on('node:ports:change', this.onNodePortChange)

    this.graph.on('edge:change:data', this.onEdgeDataChange)
    this.graph.on('edge:removed', this.onEdgeRemoved)
    this.graph.on('edge:connected', this.onEdgeConnected)

    this.graph.on('view:mounted', this.onViewMounted)
    this.graph.on('render:done', this.onRenderDone)
  }

  // 销毁事件监听
  disposeEvent() {
    this.disposeStyleEvents()

    this.graph.off('node:change:data', this.onNodeDataChange)
    this.graph.off('node:change:position', this.onNodePositionChange)
    this.graph.off('node:removed', this.onNodeRemoved)
    this.graph.off('node:ports:add', this.onNodePortAdded)
    this.graph.off('node:ports:change', this.onNodePortChange)

    this.graph.off('edge:change:data', this.onEdgeDataChange)
    this.graph.off('edge:removed', this.onEdgeRemoved)
    this.graph.off('edge:connected', this.onEdgeConnected)

    this.graph.off('view:mounted', this.onViewMounted)
    this.graph.off('render:done', this.onRenderDone)
  }

  // 初始化用于修改样式的事件监听
  initStyleEvents() {
    const graph = this.graph
    graph.on('node:selected', this.nodeSelectedStyle)
    graph.on('node:unselected', this.nodeUnselectedStyle)
    graph.on('edge:selected', this.edgeSelectedStyle)
    graph.on('edge:unselected', this.edgeResetStyle)
    graph.on('edge:mouseenter', this.edgeHoverStyle)
    graph.on('edge:mouseleave', this.edgeUnhoverStyle)
  }

  // 销毁用于修改样式的事件监听
  disposeStyleEvents() {
    const graph = this.graph
    graph.off('node:selected', this.nodeSelectedStyle)
    graph.off('node:unselected', this.nodeUnselectedStyle)
    graph.off('edge:selected', this.edgeSelectedStyle)
    graph.off('edge:unselected', this.edgeResetStyle)
    graph.off('edge:mouseenter', this.edgeHoverStyle)
    graph.off('edge:mouseleave', this.edgeUnhoverStyle)
  }

  // 修改样式时禁用历史记录
  withoutHistory(fn) {
    this.graph.disableHistory()
    fn?.()
    this.graph.enableHistory()
  }

  setPositionPersistenceEnabled(enabled) {
    this.saveNodePositions = Boolean(enabled)
    return this.saveNodePositions
  }

  isPositionPersistenceEnabled() {
    return this.saveNodePositions
  }

  // 修改选中连线的样式（主要是箭头）
  edgeSelectedStyle({ edge }) {
    this.withoutHistory(() => {
      edge.attr('line', { ...activeEdgeStyle })
      edge.attr('line/targetMarker', { ...activeTargetMarkerStyle })
    })
  }

  // 连线未选中时的样式
  edgeResetStyle({ edge }) {
    this.withoutHistory(() => {
      edge.attr('line', { ...defaultEdgeStyle })
      edge.attr('line/targetMarker', { ...defaultTargetMarkerStyle })
    })
  }

  // 鼠标悬浮在连线上时的样式
  edgeHoverStyle({ edge }) {
    if (this.graph.isSelected(edge)) return
    this.withoutHistory(() => {
      edge.attr('line', { ...hoverEdgeStyle })
      edge.attr('line/targetMarker', { ...hoverTargetMarkerStyle })
    })
  }

  // 鼠标移出连线时的样式
  edgeUnhoverStyle({ edge }) {
    this.withoutHistory(() => {
      edge.attr('line/opacity', defaultEdgeStyle.opacity)
      edge.attr('line/targetMarker/strokeWidth', defaultTargetMarkerStyle.strokeWidth)
    })
  }

  setOutgoingEdgeStyle(edge) {
    this.withoutHistory(() => {
      edge.attr('line/stroke', outgoingEdgeColor)
      edge.attr('line/targetMarker', {
        fill: outgoingEdgeColor,
        stroke: outgoingEdgeColor,
      })
    })
  }

  setIncomingEdgeStyle(edge) {
    this.withoutHistory(() => {
      edge.attr('line/stroke', incomingEdgeColor)
      edge.attr('line/targetMarker', {
        fill: incomingEdgeColor,
        stroke: incomingEdgeColor,
      })
    })
  }

  nodeSelectedStyle(e) {
    const { node } = e
    this.withoutHistory(() => {
      const incomingEdges = this.graph.getIncomingEdges(node)
      const outgoingEdges = this.graph.getOutgoingEdges(node)
      incomingEdges?.forEach((edge) => this.setIncomingEdgeStyle(edge))
      outgoingEdges?.forEach((edge) => this.setOutgoingEdgeStyle(edge))
      this.applyNodeValidationHighlights({
        runtimeInvalidNodeNames: this.runtimeValidationNodeNames,
        externalInvalidNodeNames: this.externalValidationNodeNames,
      })
    })
  }

  nodeUnselectedStyle({ node }) {
    this.withoutHistory(() => {
      const allEdges = this.graph.getConnectedEdges(node)
      allEdges?.forEach((edge) => this.edgeResetStyle({ edge }))
      this.applyNodeValidationHighlights({
        runtimeInvalidNodeNames: this.runtimeValidationNodeNames,
        externalInvalidNodeNames: this.externalValidationNodeNames,
      })
    })
  }

  // 启用插件
  enable() {
    if (!this.isEnabled()) {
      this.enabled = true
      this.initEvent()
    }
  }

  // 禁用插件
  disable() {
    if (this.isEnabled()) {
      this.enabled = false
      this.disposeEvent()
    }
  }

  // 判断插件是否启用
  isEnabled() {
    return this.enabled
  }

  // 连线数据变化
  onEdgeDataChange({ edge, current, previous }) {
    const currentLabel = current.label
    if (!currentLabel) {
      edge.setLabels([])
    } else if (currentLabel !== previous.label) {
      edge.setLabels([currentLabel])
    }
    this.triggerChange()
  }

  // 连线被删除
  onEdgeRemoved() {
    this.triggerChange()
  }

  // 连线连接（新建连线）
  onEdgeConnected({ edge }) {
    const {
      source: { port: sourcePort },
      target: { port: targetPort },
    } = edge
    const sourceNode = edge.getSourceNode()
    const targetNode = edge.getTargetNode()
    const sourceOutputPort = sourceNode
      .getPortsByGroup('out')
      .findIndex((item) => item.id === sourcePort)
    const targetInputPort = targetNode
      .getPortsByGroup('in')
      .findIndex((item) => item.id === targetPort)
    const data = {
      sourceOutputPort,
      sourceNode: sourceNode.data.name,
      targetInputPort,
      targetNode: targetNode.data.name,
    }
    edge.setData(data, { silent: true })
    this.triggerChange()
  }

  // 节点数据变化
  onNodeDataChange(e) {
    const { node, current, previous } = e
    try {
      // 更新显示名称
      if (current.name !== previous.name) {
        node.label = current.name
      }

      // 更新端口
      const { inputPorts, outputPorts } = normalizePorts(node, current)
      const allPortsData = [...inputPorts, ...outputPorts]
      const outputPortIds = outputPorts.map((port) => port.__id)
      const getNodeCapabilities = buildCapabilityGetter(this.nodeDefines)
      const nextNodeData = { ...current, inputPorts, outputPorts }
      allPortsData.forEach((port, index) => {
        const portId = port.__id
        // 更新端口 label
        if (portId && node.hasPort(portId)) {
          if (outputPortIds.includes(portId)) {
            const outputIndex = outputPorts.findIndex((item) => item.__id === portId)
            const x6Port = createX6NodeOutputPort(port, {
              node: nextNodeData,
              portIndex: outputIndex,
              getNodeCapabilities,
            })
            node.portProp(portId, 'attrs/text', x6Port.attrs.text)
            node.portProp(portId, 'attrs/circle', x6Port.attrs.circle)
          } else {
            node.portProp(portId, 'attrs/text/text', port.name)
          }
        }

        // 新增端口
        else if (portId) {
          const x6Port = outputPortIds.includes(portId)
            ? createX6NodeOutputPort(port, {
                node: nextNodeData,
                portIndex: outputPorts.findIndex((item) => item.__id === portId),
                getNodeCapabilities,
              })
            : createX6NodeInputPort(port)
            // 用 insertPort 以保持顺序
            node.insertPort(index, x6Port)
        }
      })

      // 删除端口
      node
        .getPorts()
        .map((port) => port.id)
        .forEach((portId) => {
          if (!allPortsData.find((port) => port.__id === portId)) {
            node.removePort(portId)
          }
        })

      // 更新连线
      const incomingEdges = this.graph.getIncomingEdges(node)
      const outgoingEdges = this.graph.getOutgoingEdges(node)
      incomingEdges?.forEach((edge) => {
        const data = edge.getData()
        const targetInputPort = node
          .getPortsByGroup('in')
          .findIndex((port) => port.id === edge.target.port)
        edge.setData({ ...data, targetInputPort, targetNode: current.name })
      })
      outgoingEdges?.forEach((edge) => {
        const data = edge.getData()
        const sourceOutputPort = node
          .getPortsByGroup('out')
          .findIndex((port) => port.id === edge.source.port)
        edge.setData({ ...data, sourceOutputPort, sourceNode: current.name })
      })

      // 更新出口入口
      let needUpdateMark = false
      if (this.logicFlowProps.entryNode === previous.name) {
        needUpdateMark = true
        this.logicFlowProps.entryNode = current.name
      }
      if (this.logicFlowProps.exitNode === previous.name) {
        needUpdateMark = true
        this.logicFlowProps.exitNode = current.name
      }
      if (this.logicFlowProps.errorEntryNode === previous.name) {
        needUpdateMark = true
        this.logicFlowProps.errorEntryNode = current.name
      }
      if (needUpdateMark) this.updateFlowRoleMarks()

      // 更新尺寸
      updateNodeSize(node)
    } finally {
      this.graph.stopBatch('node-change:' + current.name)
      // 数据同步不应被视觉更新链路阻断
      this.triggerChange()
    }
  }

  onNodePositionChange() {
    if (this.isHydrating) return
    this.triggerChange()
  }

  // 节点被删除
  onNodeRemoved() {
    this.triggerChange()
  }

  // 节点端口被添加
  onNodePortAdded() {}

  // 节点端口变化
  onNodePortChange() {}

  // 视图挂载（初始化时修改节点的 setData 方法）
  onViewMounted({ view: { cell } }) {
    if (cell.isNode()) {
      const oldSetData = cell.setData.bind(cell)
      const newSetData = (data, options) => {
        this.graph.startBatch('node-change:' + data.name)
        oldSetData(data, options)
      }
      cell.setData = newSetData.bind(cell)
    }
  }

  onRenderDone() {
    this.withoutHistory(() => {
      this.updateFlowRoleMarks()
      this.applyNodeValidationHighlights({
        runtimeInvalidNodeNames: this.runtimeValidationNodeNames,
        externalInvalidNodeNames: this.externalValidationNodeNames,
      })
    })
  }

  // 渲染完成，处理流程角色节点的显示
  updateFlowRoleMarks() {
    const { entryNode, exitNode, errorEntryNode } = this.logicFlowProps || {}
    this.graph.getNodes().forEach((node) => {
      const nodeData = node.getData()
      node.attr('entryMark/visibility', nodeData.name === entryNode ? 'visible' : 'hidden')
      node.attr('exitMark/visibility', nodeData.name === exitNode ? 'visible' : 'hidden')
      node.attr(
        'errorEntryMark/visibility',
        nodeData.name === errorEntryNode ? 'visible' : 'hidden'
      )
    })
  }

  applyNodeValidationHighlights({
    runtimeInvalidNodeNames = [],
    externalInvalidNodeNames = [],
  } = {}) {
    const highlightedNodeNames = new Set([
      ...(Array.isArray(runtimeInvalidNodeNames) ? runtimeInvalidNodeNames : []),
      ...(Array.isArray(externalInvalidNodeNames) ? externalInvalidNodeNames : []),
    ])
    this.graph.getNodes().forEach((node) => {
      const nodeName = node.getData?.()?.name || node.data?.name
      const isValidationHighlighted = highlightedNodeNames.has(nodeName)
      const visibility = isValidationHighlighted ? 'visible' : 'hidden'
      node.attr('errors/validationHighlighted', isValidationHighlighted)
      node.attr('errorGlow/visibility', visibility)
      node.attr('errorOutline/visibility', visibility)
      node.attr('errorBadgeStem/visibility', visibility)
      node.attr('errorBadgeDot/visibility', visibility)
    })
  }

  // 触发数据变化
  dispatchLogicFlowChange() {
    const props = this.logicFlowProps
    const x6Data = this.graph.getCells().map((cell) => cell.toJSON())
    const data = transpileToLogicFlow(x6Data, props, this.saveNodePositions)
    this.data = data
    const [validate, message, errors, nodes, entries] = validateFlow(data, this.nodeDefines)
    const runtimeInvalidNodeNames = Array.isArray(nodes)
      ? nodes
          .map(({ name }) => (typeof name === 'string' ? name : null))
          .filter(Boolean)
      : []
    this.runtimeValidationNodeNames = runtimeInvalidNodeNames
    this.withoutHistory(() => {
      this.applyNodeValidationHighlights({
        runtimeInvalidNodeNames,
        externalInvalidNodeNames: this.externalValidationNodeNames,
      })
    })
    if (!validate) {
      this.graph.trigger('logicflow:validateFailed', { data, message, errors, entries })
      return this.graph.trigger('logicflow:change:data', { data })
    }
    this.graph.trigger('logicflow:change:data', { data })
  }
}

function collectTranspileWarnings(data, nodeDefines) {
  const warnings = []
  const diagnostics = []
  const x6Data = transpileToX6Data(data, nodeDefines, {
    onWarning(warning) {
      warnings.push(warning)
    },
    collectDiagnosticFn(diagnostic) {
      if (diagnostic) {
        diagnostics.push(diagnostic)
      }
    },
  })
  return { x6Data, warnings, diagnostics }
}

// 处理端口变化
function normalizePorts(node, nodeData) {
  const nodeDefine = node.attr('meta/nodeDefine')
  const inputPortNormalizer = buildInputPortsNomalizer(nodeDefine, nodeData)
  const outputPortNormalizer = buildOutputPortsNomalizer(nodeDefine, nodeData)
  const inputPorts = inputPortNormalizer(nodeData.inputPorts)
  const outputPorts = outputPortNormalizer(nodeData.outputPorts)
  node.updateData({ inputPorts, outputPorts }, { silent: true })
  return { inputPorts, outputPorts }
}

function validateFlow(flow, nodeDefines = []) {
  if (flow.errorEntryNode && flow.errorEntryNode === flow.entryNode) {
    return [
      false,
      '异常入口节点不能与入口节点相同',
      ['异常入口节点不能与入口节点相同'],
      undefined,
      [{ message: '异常入口节点不能与入口节点相同' }],
    ]
  }
  if (flow.errorEntryNode && flow.exitNode && flow.errorEntryNode === flow.exitNode) {
    return [
      false,
      '异常入口节点不能与出口节点相同',
      ['异常入口节点不能与出口节点相同'],
      undefined,
      [{ message: '异常入口节点不能与出口节点相同' }],
    ]
  }

  const getNodeCapabilities = buildCapabilityGetter(nodeDefines)
  const [allInputPortsConnected, unConnected] = checkAllInputPortsConnected(flow)
  if (!allInputPortsConnected) {
    return [
      false,
      '请检查所有节点的输入端口是否都已经连接',
      buildUnconnectedInputErrors(flow, unConnected),
      unConnected,
      buildUnconnectedInputEntries(flow, unConnected),
    ]
  }
  if (!checkRacingCondition(flow, getNodeCapabilities)) {
    return [
      false,
      '请检查条件节点的输出端口是否连接到同一个节点的不同端口',
      ['请检查条件节点的输出端口是否连接到同一个节点的不同端口'],
      undefined,
      [{ message: '请检查条件节点的输出端口是否连接到同一个节点的不同端口' }],
    ]
  }
  if (!checkInputPortConflicts(flow, getNodeCapabilities)) {
    return [
      false,
      '请检查输入端口是否冲突',
      ['请检查输入端口是否冲突'],
      undefined,
      [{ message: '请检查输入端口是否冲突' }],
    ]
  }
  return [true]
}

function buildUnconnectedInputErrors(flow, unconnectedNodes = []) {
  return buildUnconnectedInputEntries(flow, unconnectedNodes).map((entry) => entry.message)
}

function buildUnconnectedInputEntries(flow, unconnectedNodes = []) {
  const flowNodes = flow?.nodes || []
  const entries = unconnectedNodes.flatMap(({ name, ports = [] }) => {
    const node = flowNodes.find((item) => item?.name === name)
    return ports.map((portIndex) => {
      const portName = node?.inputPorts?.[portIndex]?.name
      if (portName) {
        return {
          message: `节点「${name}」的输入端口「${portName}」未连接`,
          messageKey: '节点「{{p0}}」的输入端口「{{p1}}」未连接',
          messageParams: { p0: name, p1: portName },
          nodeName: name,
        }
      }
      return {
        message: `节点「${name}」的第 ${portIndex + 1} 个输入端口未连接`,
        messageKey: '节点「{{p0}}」的第 {{p1}} 个输入端口未连接',
        messageParams: { p0: name, p1: portIndex + 1 },
        nodeName: name,
      }
    })
  })
  return entries.length ? entries : [{ message: '请检查所有节点的输入端口是否都已经连接' }]
}
