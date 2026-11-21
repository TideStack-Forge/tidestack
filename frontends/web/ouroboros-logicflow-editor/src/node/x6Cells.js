import { calcNodeSize } from './utils.js'
import { hasSelectiveOutputs } from './outputPortSemantics.mjs'
import { buildOutputPortAttrs } from './outputPortPresentation.mjs'
import { normalizeNodeOutputPorts } from './outputPortNormalization.mjs'
import { getTerminalVisualState } from '../node-define/displayState.mjs'

function getIconXlinkHref(icon) {
  if (!icon) return ['use', '']
  if (
    icon.startsWith('data:image') ||
    icon.startsWith('http://') ||
    icon.startsWith('https://') ||
    icon.startsWith('/')
  ) {
    return ['image', icon]
  }
  return ['use', '#icon-' + icon]
}

function getNodeCapabilityState(nodeDefine = {}, nodeData = {}) {
  const capabilities = nodeDefine.editorCapabilities || {}
  const getNodeCapabilities = () => capabilities
  return {
    isExclusive: hasSelectiveOutputs(nodeData, getNodeCapabilities),
    terminalState: getTerminalVisualState(nodeDefine),
  }
}

function getNodeBodyClassName(nodeDefine = {}, nodeData = {}) {
  const { terminalState } = getNodeCapabilityState(nodeDefine, nodeData)
  return [
    'logicflow-node-body',
    terminalState.isTerminal ? 'logicflow-node-body-terminal' : '',
    terminalState.bodyClassName,
  ]
    .filter((item) => item)
    .join(' ')
}

function getVirtualNodeVisualState(nodeDefine = {}) {
  const capabilities = nodeDefine.editorCapabilities || {}
  if (capabilities.canBeErrorEntry) {
    return {
      bodyFill: '#FFF5F5',
      bodyStroke: '#F1B8B8',
      accentFill: '#DC2626',
    }
  }
  if (capabilities.canBeEntry) {
    return {
      bodyFill: '#F5F9FF',
      bodyStroke: '#B7D0FF',
      accentFill: '#2563EB',
    }
  }
  return null
}

function buildSpecialNodePresentation(size, nodeDefine = {}, nodeData = {}, iconUrl = '') {
  const { terminalState } = getNodeCapabilityState(nodeDefine, nodeData)
  const virtualNodeState = getVirtualNodeVisualState(nodeDefine)
  const specialState = terminalState.isTerminal
    ? {
        bodyFill: terminalState.bodyFill || '#F8FAFC',
        bodyStroke: terminalState.bodyStroke || '#94A3B8',
        accentFill: terminalState.bodyStroke || '#94A3B8',
        accentX: size.width - 12,
      }
    : virtualNodeState
    ? {
        ...virtualNodeState,
        accentX: 8,
      }
    : null

  const isSpecial = !!specialState

  return {
    isSpecial,
    bodyFill: specialState?.bodyFill || '#FFFFFF',
    bodyStroke: specialState?.bodyStroke || '#ccc',
    labelX: isSpecial ? 18 : 50,
    iconVisibility: isSpecial ? 'hidden' : iconUrl ? 'visible' : 'hidden',
    iconBgVisibility: isSpecial ? 'hidden' : 'visible',
    accent: {
      x: specialState?.accentX || 0,
      y: 8,
      width: isSpecial ? 4 : 0,
      height: isSpecial ? Math.max(size.height - 16, 16) : 0,
      rx: 2,
      ry: 2,
      fill: specialState?.accentFill || 'transparent',
      visibility: isSpecial ? 'visible' : 'hidden',
      class: 'logicflow-node-special-accent',
    },
  }
}

function buildCapabilityBadgeAttrs(size, nodeDefine = {}, nodeData = {}) {
  const { terminalState } = getNodeCapabilityState(nodeDefine, nodeData)
  return {
    capabilityBadgeBg: {
      x: size.width - 8,
      y: 8,
      width: 0,
      height: 0,
      visibility: 'hidden',
      class: 'logicflow-node-capability-badge-bg',
    },
    capabilityBadge: {
      text: '',
      x: size.width - 8,
      y: 8,
      fontSize: 10,
      fontWeight: 600,
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      visibility: 'hidden',
      class: 'logicflow-node-capability-badge',
    },
    terminalBadgeBg: {
      x: size.width - 8,
      y: size.height - 8,
      width: 0,
      height: 0,
      visibility: 'hidden',
      class: 'logicflow-node-terminal-badge-bg',
    },
    terminalBadge: {
      text: '',
      x: size.width - 8,
      y: size.height - 8,
      fontSize: 10,
      fontWeight: 600,
      textAnchor: 'middle',
      textVerticalAnchor: 'middle',
      fill: terminalState.badgeTextFill || '#2D7C46',
      visibility: 'hidden',
      class: 'logicflow-node-terminal-badge',
    },
  }
}

function buildErrorIndicatorAttrs(size, visibility = 'hidden') {
  const indicatorX = Math.max(size.width - 14, 18)
  const indicatorTop = 8
  return {
    errorGlow: {
      x: -3,
      y: -3,
      width: size.width + 6,
      height: size.height + 6,
      rx: 9,
      ry: 9,
      fill: 'none',
      stroke: 'rgba(220, 38, 38, 0.2)',
      strokeWidth: 5,
      strokeOpacity: 0.56,
      pointerEvents: 'none',
      visibility,
    },
    errorOutline: {
      x: -1,
      y: -1,
      width: size.width + 2,
      height: size.height + 2,
      rx: 7,
      ry: 7,
      fill: 'none',
      stroke: '#DC2626',
      strokeWidth: 1.25,
      pointerEvents: 'none',
      visibility,
    },
    errorBadgeStem: {
      d: `M ${indicatorX} ${indicatorTop} L ${indicatorX} ${indicatorTop + 5}`,
      fill: 'none',
      stroke: '#DC2626',
      strokeWidth: 2,
      strokeLinecap: 'round',
      pointerEvents: 'none',
      visibility,
    },
    errorBadgeDot: {
      cx: indicatorX,
      cy: indicatorTop + 8,
      r: 1.4,
      fill: '#DC2626',
      stroke: 'none',
      pointerEvents: 'none',
      visibility,
    },
  }
}

function resolveValidationHighlightVisibility(node) {
  const isValidationHighlighted = node.attr('errors/validationHighlighted')
  if (isValidationHighlighted === true) {
    return 'visible'
  }
  if (isValidationHighlighted === false) {
    return 'hidden'
  }
  return node.attr('errorGlow/visibility') || 'hidden'
}

function buildFlowRoleAttrs(nodeDefine = {}) {
  const capabilities = nodeDefine.editorCapabilities || {}
  return {
    errorEntry: {
      d: capabilities.canBeErrorEntry
        ? ''
        : `M ${-10} ${12} L ${4} ${12}`,
      fill: 'none',
      stroke: '#DC2626',
      strokeWidth: capabilities.canBeErrorEntry ? 0 : 2,
      strokeLinecap: 'round',
    },
  }
}
/**
 * x6 节点的定义
 * @param {object} nodeConfig
 * @returns x6节点配置
 */
export function createX6Node(nodeDefine = {}, nodeData = {}, props = {}) {
  const capabilities = nodeDefine.editorCapabilities || {}
  const normalizedOutputPorts = normalizeNodeOutputPorts(nodeDefine, nodeData)
  const normalizedNodeData = {
    ...nodeData,
    outputPorts: normalizedOutputPorts,
  }
  const {
    shape,
    icon,
    iconWidth,
    iconHeight,
    iconBg = null,
    iconColor = '#fff',
  } = nodeDefine
  const label = normalizedNodeData.name || nodeDefine.label
  const [iconTag, iconUrl] = getIconXlinkHref(icon) // 根据传入的图标名称构建图标
  const fontSize = 14
  const size = calcNodeSize(normalizedNodeData)
  const getNodeCapabilities = () => capabilities
  const { terminalState } = getNodeCapabilityState(nodeDefine, normalizedNodeData)
  const capabilityBadgeAttrs = buildCapabilityBadgeAttrs(size, nodeDefine, normalizedNodeData)
  const errorIndicatorAttrs = buildErrorIndicatorAttrs(size)
  const flowRoleAttrs = buildFlowRoleAttrs(nodeDefine)
  const specialPresentation = buildSpecialNodePresentation(
    size,
    nodeDefine,
    normalizedNodeData,
    iconUrl
  )
  const { inputPorts = [], outputPorts = [] } = normalizedNodeData
  const nodeInputPortItems = inputPorts
    .map((port, index) => (port.__id ? port : { ...port, __id: `in-${index}` }))
    .map(createX6NodeInputPort)
  const nodeOutputPortItems = outputPorts
    .map((port, index) => (port.__id ? port : { ...port, __id: `out-${index}` }))
    .map((port, index) =>
      createX6NodeOutputPort(port, {
        node: normalizedNodeData,
        portIndex: index,
        getNodeCapabilities,
      })
    )

  // 生成端口数组
  const portItems = [...nodeInputPortItems, ...nodeOutputPortItems]

  const finalIconBg = iconBg
    ? iconBg
    : terminalState.isTerminal
    ? terminalState.iconBg || '#6CC08B'
    : 'rgba(64, 158, 252, 0.6)'

  return {
    ...props,
    ...size,
    shape: shape || 'rect',
    label: label || '',
    // 连接桩 DOM 结构定义
    markup: [
      {
        tagName: 'rect', // 标签名称
        selector: 'body', // 选择器
        className: 'logicflow-node-body', // 类名
      },
      {
        tagName: 'rect',
        selector: 'errorGlow',
      },
      {
        tagName: 'rect',
        selector: 'errorOutline',
      },
      {
        tagName: 'rect',
        selector: 'specialAccent',
      },
      {
        tagName: 'g',
        children: [
          {
            tagName: 'path',
            selector: 'iconBg',
          },
          {
            tagName: iconTag,
            selector: 'icon',
          },
        ],
      },
      {
        tagName: 'text',
        selector: 'label',
      },
      {
        tagName: 'path',
        selector: 'errorBadgeStem',
      },
      {
        tagName: 'circle',
        selector: 'errorBadgeDot',
      },
      {
        tagName: 'rect',
        selector: 'capabilityBadgeBg',
      },
      {
        tagName: 'text',
        selector: 'capabilityBadge',
      },
      {
        tagName: 'rect',
        selector: 'terminalBadgeBg',
      },
      {
        tagName: 'text',
        selector: 'terminalBadge',
      },
      {
        tagName: 'g',
        selector: 'entryMark',
        children: [],
      },
      {
        tagName: 'g',
        selector: 'exitMark',
        children: [],
      },
      {
        tagName: 'g',
        selector: 'errorEntryMark',
        children: [
          {
            tagName: 'path',
            selector: 'errorEntry',
          },
        ],
      },
    ],
    // 属性和样式
    attrs: {
      ...errorIndicatorAttrs,
      body: {
        rx: 6,
        ry: 6,
        strokeWidth: 1,
        stroke: specialPresentation.bodyStroke,
        fill: specialPresentation.bodyFill,
        class: getNodeBodyClassName(nodeDefine, normalizedNodeData),
      },
      specialAccent: specialPresentation.accent,
      iconBg: {
        d: 'M0,6 a6,6 0 0 1 6,-6 h34 v' + size.height + ' h-34 a6,6 0 0 1 -6,-6 z',
        fill: finalIconBg,
        strokeWidth: 0,
        strokeLinejoin: 'round',
        visibility: specialPresentation.iconBgVisibility,
      },
      icon: {
        'xlink:href': iconUrl,
        width: iconWidth || 24,
        height: iconHeight || 24,
        x: 5,
        y: (size.height - (iconHeight || 24)) / 2,
        visibility: specialPresentation.iconVisibility,
        fill: iconColor || 'white',
        color: iconColor || 'white',
      },
      label: {
        fontSize,
        fill: '#333333',
        refY: '50%',
        refX: '0%',
        textAnchor: 'start',
        textVerticalAnchor: 'middle',
        x: specialPresentation.labelX,
      },
      ...capabilityBadgeAttrs,
      entryMark: {
        visibility: 'hidden',
      },
      exitMark: {
        visibility: 'hidden',
      },
      errorEntryMark: {
        visibility: 'hidden',
      },
      ...flowRoleAttrs,
      meta: {
        nodeDefine,
      },
      io: {
        inputData: [],
        outputData: [],
      },
      errors: {
        errors: [],
        validationHighlighted: false,
      },
    },
    ports: {
      // 链接桩组定义
      groups: {
        // 输入链接桩群组定义
        in: {
          position: 'left',
          label: {
            position: 'left', // 标签位置
          },
          attrs: {
            text: {
              fill: '#6669',
              class: 'logicflow-node-port-text logicflow-node-port-in-text',
            },
            circle: {
              r: 5,
              magnet: true,
              stroke: '#31d0c6',
              class: 'logicflow-node-port logicflow-node-port-in',
            },
          },
        },
        // 输出链接桩群组定义
        out: {
          position: 'right',
          label: {
            position: 'right', // 标签位置
          },
          attrs: {
            text: {
              class: 'logicflow-node-port-text logicflow-node-port-out-text',
            },
            circle: {
              r: 5,
              magnet: true,
              stroke: '#31d0c6',
              class: 'logicflow-node-port logicflow-node-port-out',
            },
          },
        },
      },
      items: portItems,
    },
    data: {
      name: label,
      isEnabled: true,
      isAsync: false,
      ...normalizedNodeData,
    },
    zIndex: 100,
  }
}

const defaultEdgeColor = '#CCC'
const defaultEdgeWidth = 4
const defaultEdgeArrowSize = 1
const defaultEdgeOpacity = 0.6

const hoverEdgeWidth = 4
const hoverEdgeArrowSize = 2
const hoverEdgeOpacity = 1

const activeEdgeColor = '#1890ff'
const activeEdgeWidth = 4
const activeEdgeArrowSize = 2

export const incomingEdgeColor = '#66B6FF'

export const outgoingEdgeColor = '#FFC55C'

export const defaultEdgeStyle = {
  stroke: defaultEdgeColor,
  strokeWidth: defaultEdgeWidth,
  opacity: defaultEdgeOpacity,
}
export const defaultTargetMarkerStyle = {
  stroke: defaultEdgeColor,
  fill: defaultEdgeColor,
  strokeWidth: defaultEdgeArrowSize,
}

export const hoverEdgeStyle = {
  strokeWidth: hoverEdgeWidth,
  opacity: hoverEdgeOpacity,
}

export const hoverTargetMarkerStyle = {
  strokeWidth: hoverEdgeArrowSize,
  opacity: hoverEdgeOpacity,
}

export const activeEdgeStyle = {
  stroke: activeEdgeColor,
  strokeWidth: activeEdgeWidth,
}

export const activeTargetMarkerStyle = {
  strokeWidth: activeEdgeArrowSize,
  fill: activeEdgeColor,
  stroke: activeEdgeColor,
}

/**
 * 定义连线默认样式
 */
export function createX6Edge(props = {}) {
  return {
    shape: 'edge',
    defaultLabel: {
      markup: [
        {
          tagName: 'rect',
          selector: 'body',
          className: 'flow-connection-label-container',
        },
        {
          tagName: 'text',
          selector: 'label',
          className: 'flow-connection-label',
        },
      ],
      attrs: {
        label: {
          textAnchor: 'middle',
          textVerticalAnchor: 'middle',
        },
        body: {
          ref: 'label',
          rx: 4,
          ry: 4,
          refWidth: 1,
          refHeight: 1,
          refX: 0,
          refY: 0,
        },
      },
      position: {
        distance: 0.5,
      },
    },
    attrs: {
      line: {
        class: 'flow-connection',
        stroke: defaultEdgeColor,
        strokeWidth: defaultEdgeWidth,
        opacity: defaultEdgeOpacity,
        targetMarker: {
          name: 'classic',
          fill: defaultEdgeColor,
          stroke: defaultEdgeColor,
        },
      },
    },
    router: {
      name: 'metro',
      args: {
        padding: {
          vertical: 10,
          left: 20,
          right: 10,
        },
      },
    },
    connector: { name: 'rounded', args: { radius: 5 } },
    zIndex: 90,
    ...props,
  }
}

const virtualFlowNodePresets = {
  entry: {
    name: 'VirtualEntryNode',
    label: '入口',
    width: 132,
    stroke: '#D3E5D9',
    fill: '#F5FBF7',
    strokeWidth: 1.25,
    strokeDasharray: '4 3',
    iconFill: '#C9E3D2',
    textFill: '#667C72',
    fontWeight: 500,
    textX: 81,
    textAnchor: 'middle',
    iconPath: 'M 12 20 L 24 20 M 20 16 L 24 20 L 20 24',
  },
  errorEntry: {
    name: 'VirtualErrorEntryNode',
    label: '异常入口',
    width: 132,
    stroke: '#F1B8B8',
    fill: '#FFFFFF',
    strokeWidth: 1,
    iconFill: '#E38B5B',
    textFill: '#7F1D1D',
    fontWeight: 600,
    iconPath: 'M 18 13 L 18 21 M 18 26 L 18 26',
  },
  return: {
    name: 'VirtualReturnNode',
    label: '返回',
    width: 96,
    stroke: '#BFE7CB',
    fill: '#FFFFFF',
    strokeWidth: 1,
    iconFill: '#6CC08B',
    textFill: '#1F4D2D',
    fontWeight: 600,
    iconPath: 'M 24 20 L 12 20 M 16 16 L 12 20 L 16 24',
  },
}

export function createX6VirtualFlowNode(kind, props = {}) {
  const preset = virtualFlowNodePresets[kind]
  if (!preset) return { shape: 'rect', ...props }

  const width = preset.width || (kind === 'errorEntry' ? 132 : kind === 'entry' ? 112 : 96)
  const height = 40

  return {
    shape: 'rect',
    selectable: false,
    movable: false,
    width,
    height,
    markup: [
      {
        tagName: 'rect',
        selector: 'body',
      },
      {
        tagName: 'path',
        selector: 'iconBg',
      },
      {
        tagName: 'path',
        selector: 'roleIcon',
      },
      {
        tagName: 'text',
        selector: 'label',
      },
    ],
    attrs: {
      body: {
        rx: 6,
        ry: 6,
        strokeWidth: preset.strokeWidth || 1,
        stroke: preset.stroke,
        fill: preset.fill || '#FFFFFF',
        strokeDasharray: preset.strokeDasharray || undefined,
      },
      iconBg: {
        d: `M0,6 a6,6 0 0 1 6,-6 h32 v${height} h-32 a6,6 0 0 1 -6,-6 z`,
        fill: preset.iconFill,
        strokeWidth: 0,
      },
      roleIcon: {
        d: preset.iconPath,
        fill: 'none',
        stroke: '#FFFFFF',
        strokeWidth: 2,
        strokeLinecap: 'round',
        strokeLinejoin: 'round',
      },
      label: {
        text: props.label || preset.label,
        fontSize: 13,
        fontWeight: preset.fontWeight || 600,
        fill: preset.textFill,
        textAnchor: preset.textAnchor || 'start',
        textVerticalAnchor: 'middle',
        x: preset.textX || 50,
        refX: 0,
        refY: '50%',
      },
    },
    zIndex: 80,
    data: {
      name: preset.name,
      type: `virtual-${kind}`,
      label: props.label || preset.label,
      __virtual: true,
      __virtualKind: kind,
      ...props.data,
    },
    ...props,
  }
}

export function createX6PhantomEdge(kind, props = {}) {
  const stroke = defaultEdgeColor
  return createX6Edge({
    selectable: false,
    movable: false,
    ...props,
    attrs: {
      line: {
        class: 'flow-connection flow-connection-phantom',
        stroke,
        strokeWidth: defaultEdgeWidth,
        strokeDasharray: '8 6',
        opacity: defaultEdgeOpacity,
        targetMarker: {
          name: 'classic',
          fill: stroke,
          stroke,
        },
      },
    },
    zIndex: 70,
    data: {
      __virtual: true,
      __virtualKind: kind,
      ...(props.data || {}),
    },
  })
}

export function createX6NodeOutputPort(port = {}, options = {}) {
  const { node = {}, portIndex = 0, getNodeCapabilities = () => ({}) } = options
  const attrs = buildOutputPortAttrs(node, port, portIndex, getNodeCapabilities)
  return {
    id: port.__id,
    group: 'out',
    attrs: {
      text: attrs.text,
      circle: attrs.circle,
    },
  }
}

export function createX6NodeInputPort(port = {}) {
  return {
    id: port.__id,
    group: 'in',
    attrs: {
      text: {
        text: port.name || '',
      },
    },
  }
}

/**
 * 修改节点大小
 */
export function updateNodeSize(node) {
  const data = node.getData()
  const size = calcNodeSize(data)
  const nodeDefine = node.attr('meta/nodeDefine') || {}
  const { capabilityBadgeBg, capabilityBadge, terminalBadgeBg, terminalBadge } =
    buildCapabilityBadgeAttrs(size, nodeDefine, data)
  const errorIndicatorAttrs = buildErrorIndicatorAttrs(size, resolveValidationHighlightVisibility(node))
  const flowRoleAttrs = buildFlowRoleAttrs(nodeDefine)
  const specialPresentation = buildSpecialNodePresentation(
    size,
    nodeDefine,
    data,
    node.attr('icon/xlink:href') || ''
  )
  node.prop('size', size)
  //  修改icon 位置
  const iconHeight = node.attr('icon/height')
  node.attr('icon/y', (size.height - iconHeight) / 2)
  // 修改path 高度
  const pathD = 'M0,6 a6,6 0 0 1 6,-6 h34 v' + size.height + ' h-34 a6,6 0 0 1 -6,-6 z'
  node.attr('iconBg/d', pathD)
  node.attr('iconBg/visibility', specialPresentation.iconBgVisibility)
  node.attr('icon/visibility', specialPresentation.iconVisibility)
  node.attr('body/stroke', specialPresentation.bodyStroke)
  node.attr('body/fill', specialPresentation.bodyFill)
  node.attr('body/class', getNodeBodyClassName(nodeDefine, data))
  node.attr('specialAccent', specialPresentation.accent)
  node.attr('label/x', specialPresentation.labelX)
  node.attr('capabilityBadgeBg', capabilityBadgeBg)
  node.attr('capabilityBadge', capabilityBadge)
  node.attr('terminalBadgeBg', terminalBadgeBg)
  node.attr('terminalBadge', terminalBadge)
  node.attr('errorGlow', errorIndicatorAttrs.errorGlow)
  node.attr('errorOutline', errorIndicatorAttrs.errorOutline)
  node.attr('errorBadgeStem', errorIndicatorAttrs.errorBadgeStem)
  node.attr('errorBadgeDot', errorIndicatorAttrs.errorBadgeDot)
  node.attr('errorEntry', flowRoleAttrs.errorEntry)
}
