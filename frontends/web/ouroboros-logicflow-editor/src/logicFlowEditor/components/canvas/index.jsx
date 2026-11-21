import { t } from '../../../i18n.js'
import React, {
  useCallback,
  forwardRef,
  useEffect,
  useImperativeHandle,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
} from 'react'
import { Graph } from '@antv/x6'
import { Snapline } from '@antv/x6-plugin-snapline'
import { Scroller } from '@antv/x6-plugin-scroller'
import { Selection } from '@antv/x6-plugin-selection'
import { History } from '@antv/x6-plugin-history'
import { MiniMap } from '@antv/x6-plugin-minimap'
import { DagreLayout } from '@antv/layout'
import { debounce } from 'lodash'

import { createX6Edge, LogicFlowPlugin } from '@/node'

import { getFlowProps, getFlowSyncAction, snapshotFlowData } from './flowSync.mjs'
import { shouldEmitEdgeCreated, shouldEmitNodeCreated } from './creationEvents.mjs'
import {
  isCanvasPortLinkingEnabled,
  resolveCanvasDoubleClickForwarding,
} from './canvasEventSemantics.mjs'
import { disposeCanvasRuntime } from './logicFlowCanvasLifecycle.mjs'

function isHydratingGraph(graph) {
  return !!graph?.isLogicFlowHydrating?.()
}

function forwardCanvasEvent(emitFn, forwarding) {
  if (!forwarding?.eventName) return
  emitFn(forwarding.eventName, ...(forwarding.args || []))
}

const Canvas = forwardRef(function Canvas(
  {
    data,
    nodeDefines,
    isReadOnly = false,
    onMapZoom,
    onFitGraph,
    onCenterGraph,
    onNodeSelected,
    onEdgeSelected,
    onFlowSelected,
    onSelectionCleared,
    onNodeDblclick,
    onEdgeDblclick,
    onBlankDblclick,
    onNodeCreated,
    onEdgeCreated,
    onGraphInited,
    onTranspileWarnings,
    onValidateFailed,
    onChange,
  },
  ref
) {
  const graphParentRef = useRef(null)
  const graphContainerRef = useRef(null)
  const miniMapRef = useRef(null)
  const miniMapPluginRef = useRef(null)
  const graphRef = useRef(null)
  const [graphReadyVersion, setGraphReadyVersion] = useState(0)
  const readonlyRef = useRef(isReadOnly)
  readonlyRef.current = isReadOnly
  const scrollerElementRef = useRef(null)
  const renderedDataRef = useRef(null)
  const renderedNodeDefineTypesRef = useRef('')
  const emitNodeSelectedRef = useRef(null)
  const emitEdgeSelectedRef = useRef(null)
  const canvasEventHandlersRef = useRef({
    onNodeSelected: null,
    onEdgeSelected: null,
    onFlowSelected: null,
    onSelectionCleared: null,
    onNodeDblclick: null,
    onEdgeDblclick: null,
    onBlankDblclick: null,
    onNodeCreated: null,
    onEdgeCreated: null,
    onGraphInited: null,
    onTranspileWarnings: null,
    onValidateFailed: null,
    onChange: null,
  })
  const scrollIndicatorHideTimerRef = useRef(null)
  const [showMiniMap, setShowMiniMap] = useState(true)
  const [zoomPercent, setZoomPercent] = useState(100)

  const remountMiniMapPlugin = useCallback(() => {
    const graph = graphRef.current
    const miniMapPlugin = miniMapPluginRef.current || graph?.getPlugin?.('minimap')
    const miniMapHost = miniMapRef.current
    if (!showMiniMap || !miniMapPlugin || !miniMapHost) {
      return
    }

    if (miniMapPlugin.container?.parentElement !== miniMapHost) {
      miniMapHost.replaceChildren()
      miniMapHost.appendChild(miniMapPlugin.container)
    }

    requestAnimationFrame(() => {
      const width = graphContainerRef.current?.clientWidth || graph?.container?.clientWidth || 0
      const height = graphContainerRef.current?.clientHeight || graph?.container?.clientHeight || 0
      if (width > 0 && height > 0) {
        miniMapPlugin.updatePaper?.(width, height)
      }
      miniMapPlugin.updateViewport?.()
    })
  }, [showMiniMap])

  const clearScrollIndicatorHideTimer = useCallback(() => {
    if (!scrollIndicatorHideTimerRef.current) {
      return
    }
    clearTimeout(scrollIndicatorHideTimerRef.current)
    scrollIndicatorHideTimerRef.current = null
  }, [])

  const updateCanvasScrollIndicators = useCallback((scrollerElement = scrollerElementRef.current) => {
    const host = graphParentRef.current
    if (!host || !scrollerElement) {
      return
    }

    const trackInset = 6
    const minThumbSize = 36
    const horizontalOverflow = scrollerElement.scrollWidth - scrollerElement.clientWidth
    const verticalOverflow = scrollerElement.scrollHeight - scrollerElement.clientHeight
    const hasHorizontalScroll = horizontalOverflow > 1
    const hasVerticalScroll = verticalOverflow > 1

    const horizontalTrackLength = Math.max(scrollerElement.clientWidth - trackInset * 2, 0)
    const verticalTrackLength = Math.max(scrollerElement.clientHeight - trackInset * 2, 0)

    const horizontalThumbLength = hasHorizontalScroll
      ? Math.max(minThumbSize, horizontalTrackLength * (scrollerElement.clientWidth / scrollerElement.scrollWidth))
      : 0
    const verticalThumbLength = hasVerticalScroll
      ? Math.max(minThumbSize, verticalTrackLength * (scrollerElement.clientHeight / scrollerElement.scrollHeight))
      : 0

    const horizontalTravel = Math.max(horizontalTrackLength - horizontalThumbLength, 0)
    const verticalTravel = Math.max(verticalTrackLength - verticalThumbLength, 0)

    const horizontalProgress = hasHorizontalScroll ? scrollerElement.scrollLeft / horizontalOverflow : 0
    const verticalProgress = hasVerticalScroll ? scrollerElement.scrollTop / verticalOverflow : 0

    host.style.setProperty('--logicflow-canvas-scrollbar-x-length', `${Math.round(horizontalThumbLength)}px`)
    host.style.setProperty(
      '--logicflow-canvas-scrollbar-x-offset',
      `${Math.round(trackInset + horizontalTravel * horizontalProgress)}px`
    )
    host.style.setProperty('--logicflow-canvas-scrollbar-y-length', `${Math.round(verticalThumbLength)}px`)
    host.style.setProperty(
      '--logicflow-canvas-scrollbar-y-offset',
      `${Math.round(trackInset + verticalTravel * verticalProgress)}px`
    )
    host.classList.toggle('has-canvas-horizontal-scroll', hasHorizontalScroll)
    host.classList.toggle('has-canvas-vertical-scroll', hasVerticalScroll)
  }, [])

  const revealCanvasScrollIndicators = useCallback(() => {
    const host = graphParentRef.current
    if (!host) {
      return
    }
    host.classList.add('show-canvas-scroll-indicators')
    clearScrollIndicatorHideTimer()
    scrollIndicatorHideTimerRef.current = setTimeout(() => {
      host.classList.remove('show-canvas-scroll-indicators')
      scrollIndicatorHideTimerRef.current = null
    }, 480)
  }, [clearScrollIndicatorHideTimer])

  function getNodeDefineTypesSignature(defines = []) {
    return defines.map((item) => item?.type || '').join('|')
  }

  const emitNodeSelected = useMemo(
    () => debounce((node, graph) => onNodeSelected?.(node, graph), 100),
    [onNodeSelected]
  )
  const emitEdgeSelected = useMemo(
    () => debounce((edge, graph) => onEdgeSelected?.(edge, graph), 100),
    [onEdgeSelected]
  )

  useEffect(() => {
    emitNodeSelectedRef.current = emitNodeSelected
  }, [emitNodeSelected])

  useEffect(() => {
    emitEdgeSelectedRef.current = emitEdgeSelected
  }, [emitEdgeSelected])

  useEffect(() => {
    remountMiniMapPlugin()
  }, [remountMiniMapPlugin, showMiniMap, data, nodeDefines])

  useEffect(() => {
    canvasEventHandlersRef.current = {
      onNodeSelected,
      onEdgeSelected,
      onFlowSelected,
      onSelectionCleared,
      onNodeDblclick,
      onEdgeDblclick,
      onBlankDblclick,
      onNodeCreated,
      onEdgeCreated,
      onGraphInited,
      onTranspileWarnings,
      onValidateFailed,
      onChange,
    }
  }, [
    onNodeSelected,
    onEdgeSelected,
    onFlowSelected,
    onSelectionCleared,
    onNodeDblclick,
    onEdgeDblclick,
    onBlankDblclick,
    onNodeCreated,
    onEdgeCreated,
    onGraphInited,
    onTranspileWarnings,
    onValidateFailed,
    onChange,
  ])

  useEffect(() => {
    const host = graphParentRef.current
    if (!host) {
      return undefined
    }

    let disposed = false
    let resizeObserver = null
    const attachScroller = () => {
      if (disposed) {
        return undefined
      }
      const scrollerElement = host.querySelector('.x6-graph-scroller')
      if (!scrollerElement) {
        return undefined
      }

      scrollerElementRef.current = scrollerElement

      const handleScroll = () => {
        updateCanvasScrollIndicators(scrollerElement)
        revealCanvasScrollIndicators()
      }

      scrollerElement.addEventListener('scroll', handleScroll, { passive: true })
      if (typeof ResizeObserver === 'function') {
        resizeObserver = new ResizeObserver(() => {
          updateCanvasScrollIndicators(scrollerElement)
        })
        resizeObserver.observe(scrollerElement)
      }
      requestAnimationFrame(() => {
        updateCanvasScrollIndicators(scrollerElement)
      })

      return () => {
        scrollerElement.removeEventListener('scroll', handleScroll)
      }
    }

    const detachScroller = attachScroller()

    return () => {
      disposed = true
      clearScrollIndicatorHideTimer()
      host.classList.remove('show-canvas-scroll-indicators')
      host.classList.remove('has-canvas-horizontal-scroll')
      host.classList.remove('has-canvas-vertical-scroll')
      host.style.removeProperty('--logicflow-canvas-scrollbar-x-length')
      host.style.removeProperty('--logicflow-canvas-scrollbar-x-offset')
      host.style.removeProperty('--logicflow-canvas-scrollbar-y-length')
      host.style.removeProperty('--logicflow-canvas-scrollbar-y-offset')
      scrollerElementRef.current = null
      detachScroller?.()
      resizeObserver?.disconnect?.()
    }
  }, [data, nodeDefines, showMiniMap, clearScrollIndicatorHideTimer, revealCanvasScrollIndicators, updateCanvasScrollIndicators])

  function emit(name, ...args) {
    const handlers = canvasEventHandlersRef.current
    if (name === 'nodeSelected') handlers.onNodeSelected?.(...args)
    else if (name === 'edgeSelected') handlers.onEdgeSelected?.(...args)
    else if (name === 'flowSelected') handlers.onFlowSelected?.(...args)
    else if (name === 'selectionCleared') handlers.onSelectionCleared?.(...args)
    else if (name === 'nodeDblclick') handlers.onNodeDblclick?.(...args)
    else if (name === 'edgeDblclick') handlers.onEdgeDblclick?.(...args)
    else if (name === 'blankDblclick') handlers.onBlankDblclick?.(...args)
    else if (name === 'nodeCreated') handlers.onNodeCreated?.(...args)
    else if (name === 'edgeCreated') handlers.onEdgeCreated?.(...args)
    else if (name === 'graphInited') handlers.onGraphInited?.(...args)
    else if (name === 'transpileWarnings') handlers.onTranspileWarnings?.(...args)
    else if (name === 'validateFailed') handlers.onValidateFailed?.(...args)
    else if (name === 'change') handlers.onChange?.(...args)
  }

  const syncZoomPercent = useCallback((graph = graphRef.current) => {
    const zoom = Number(graph?.zoom?.() || 1)
    setZoomPercent(Math.round(zoom * 100))
  }, [])

  function isVirtualCell(cell) {
    return cell?.getData?.()?.__virtual === true || cell?.data?.__virtual === true
  }

  function initEvent(graph) {
    graph.on('node:dblclick', ({ node }) => {
      if (isVirtualCell(node)) return
      forwardCanvasEvent(
        emit,
        resolveCanvasDoubleClickForwarding({
          targetType: 'node',
          target: node,
          graph,
        })
      )
    })
    graph.on('edge:dblclick', ({ edge }) => {
      if (isVirtualCell(edge)) return
      forwardCanvasEvent(
        emit,
        resolveCanvasDoubleClickForwarding({
          targetType: 'edge',
          target: edge,
          graph,
        })
      )
    })
    graph.on('blank:dblclick', () => {
      forwardCanvasEvent(
        emit,
        resolveCanvasDoubleClickForwarding({
          targetType: 'blank',
          graph,
        })
      )
    })

    graph.on('node:selected', ({ node }) => {
      if (isVirtualCell(node)) {
        graph.cleanSelection?.()
        return
      }
      emitNodeSelectedRef.current?.(node, graph)
    })
    graph.on('edge:selected', ({ edge }) => {
      if (isVirtualCell(edge)) {
        graph.cleanSelection?.()
        return
      }
      emitEdgeSelectedRef.current?.(edge, graph)
    })
    graph.on('blank:click', () => emit('flowSelected', graph))
    graph.on('node:unselected', ({ node }) => {
      if (isVirtualCell(node)) {
        return
      }
      if ((graph.getSelectedCells?.() || []).length === 0) {
        emit('selectionCleared', graph)
      }
    })
    graph.on('edge:unselected', ({ edge }) => {
      if (isVirtualCell(edge)) {
        return
      }
      if ((graph.getSelectedCells?.() || []).length === 0) {
        emit('selectionCleared', graph)
      }
    })
    graph.on('node:added', ({ node }) => {
      if (!shouldEmitNodeCreated({ isHydrating: isHydratingGraph(graph) })) return
      emit('nodeCreated', node, graph)
    })
    graph.on('edge:connected', ({ isNew, edge }) => {
      if (!shouldEmitEdgeCreated({ isHydrating: isHydratingGraph(graph), isNew })) return
      emit('edgeCreated', edge, graph)
    })

    graph.on('logicflow:change:data', ({ data: nextData }) => {
      renderedDataRef.current = snapshotFlowData(nextData)
      emit('change', nextData)
    })
    graph.on('logicflow:transpileWarnings', (event) => {
      renderedDataRef.current = snapshotFlowData(event?.data)
      emit('transpileWarnings', event)
    })
    graph.on('logicflow:validateFailed', (event) => {
      renderedDataRef.current = snapshotFlowData(event?.data)
      emit('validateFailed', event)
    })
  }

  function initGraph(initialData, currentNodeDefines) {
    const dagreLayout = new DagreLayout({
      type: 'dagre',
      rankdir: 'LR',
      ranker: 'tight-tree',
      align: 'UR',
      ranksepFunc(node) {
        return node.width / 2 + 20
      },
      nodesepFunc(node) {
        return node.height - 60
      },
      controlPoints: false,
    })

    const miniMapPlugin = new MiniMap({
      container: miniMapRef.current,
      width: 208,
      height: 160,
    })

    const graph = new Graph({
      container: graphContainerRef.current,
      autoResize: true,
      background: {
        color: '#F2F7FA',
      },
      grid: {
        size: 10,
        visible: true,
      },
      interacting: isReadOnly ? false : {
        nodeMovable: true,
        edgeMovable: false,
      },
      connecting: {
        allowPort: isCanvasPortLinkingEnabled(isReadOnly),
        allowEdge: false,
        allowNode: false,
        allowLoop: false,
        allowMulti: false,
        allowBlank: false,
        snap: {
          radius: 20,
        },
        createEdge() {
          return this.createEdge({
            shape: 'edge',
            ...createX6Edge(),
          })
        },
        validateMagnet({ magnet }) {
          return magnet.getAttribute('port-group') !== 'in'
        },
        validateConnection({ sourceCell, targetCell, sourceMagnet, targetMagnet }) {
          if (sourceCell === targetCell) return false
          if (!sourceMagnet || sourceMagnet.getAttribute('port-group') === 'in') return false
          if (!targetMagnet || targetMagnet.getAttribute('port-group') !== 'in') return false
          if (this.isPredecessor(sourceCell, targetCell)) return false
          return true
        },
      },
      mousewheel: {
        enabled: true,
        zoomAtMousePosition: true,
        modifiers: ['ctrl', 'meta'],
        minScale: 0.2,
        factor: 1.05,
        maxScale: 3,
      },
      highlighting: {
        default: {
          name: 'stroke',
          args: {
            attrs: {
              'stroke-width': 2,
              fill: '#fff',
              stroke: 'red',
            },
          },
        },
      },
    })
      .resize(graphParentRef.current.clientWidth, graphParentRef.current.clientHeight)
      .use(new Snapline({ enabled: true }))
      .use(
        new Scroller({
          enabled: true,
          pannable: true,
          padding: {
            top: 100,
            right: 600,
            left: 600,
            bottom: 100,
          },
          autoResizeOptions: {
            border: 20,
          },
        })
      )
      .use(
        new Selection({
          enabled: true,
          multiple: true,
          showNodeSelectionBox: false,
          showEdgeSelectionBox: false,
          rubberband: true,
          rubberEdge: false,
          rubberNode: true,
          pageVisible: true,
          pageBreak: true,
          modifiers: 'shift',
          padding: 10,
        })
      )
      .setRubberbandModifiers(['alt', 'meta'])
      .use(new History({ enabled: true }))
      .use(miniMapPlugin)
      .use(
        new LogicFlowPlugin({
          enabled: true,
          layout: dagreLayout,
          data: initialData,
          nodeDefines: currentNodeDefines,
        })
      )
      .centerContent()

    initEvent(graph)
    miniMapPluginRef.current = miniMapPlugin
    graphRef.current = graph
    setGraphReadyVersion((value) => value + 1)
    syncZoomPercent(graph)
    graph.on?.('scale', () => syncZoomPercent(graph))
    graph.rehydrateLogicFlowGraph?.()
    remountMiniMapPlugin()
    emit('graphInited', graph)
    return graph
  }

  useEffect(() => {
    const nextData = snapshotFlowData(data)
    if (!nextData) return

    if (!graphRef.current) {
      initGraph(nextData, nodeDefines)
      renderedDataRef.current = nextData
      renderedNodeDefineTypesRef.current = getNodeDefineTypesSignature(nodeDefines)
      return
    }

    const nextNodeDefineTypes = getNodeDefineTypesSignature(nodeDefines)
    if (renderedNodeDefineTypesRef.current !== nextNodeDefineTypes) {
      graphRef.current.updateLogicFlowNodeDefines?.(nodeDefines)
      renderedNodeDefineTypesRef.current = nextNodeDefineTypes
      graphRef.current.updateLogicFlowData(nextData)
      renderedDataRef.current = nextData
      return
    }

    const syncAction = getFlowSyncAction(renderedDataRef.current, nextData)
    if (syncAction === 'noop') return
    if (syncAction === 'update-props') {
      graphRef.current.updateLogicFlowProps(getFlowProps(nextData))
    } else {
      graphRef.current.updateLogicFlowData(nextData)
    }
    renderedDataRef.current = nextData
    renderedNodeDefineTypesRef.current = nextNodeDefineTypes
  }, [data, nodeDefines])

  useLayoutEffect(() => {
    const graph = graphRef.current
    if (!graph) return undefined
    graph.options.interacting = isReadOnly ? false : { nodeMovable: true, edgeMovable: false }
    graph.options.connecting.allowPort = isCanvasPortLinkingEnabled(isReadOnly)
    if (!isReadOnly) return undefined
    emitNodeSelectedRef.current?.cancel?.()
    emitEdgeSelectedRef.current?.cancel?.()
    const plugins = ['selection', 'logicflow'].map(name => graph.getPlugin(name))
    const enabled = plugins.filter(plugin => plugin?.isEnabled())
    enabled.forEach(plugin => plugin.disable())
    return () => enabled.forEach(plugin => plugin.enable())
  }, [isReadOnly, graphReadyVersion])

  useEffect(() => {
    return () => {
      disposeCanvasRuntime({
        graph: graphRef.current,
        emitNodeSelected: emitNodeSelectedRef.current,
        emitEdgeSelected: emitEdgeSelectedRef.current,
      })
      miniMapPluginRef.current = null
      graphRef.current = null
      renderedDataRef.current = null
      renderedNodeDefineTypesRef.current = ''
    }
  }, [])

  useImperativeHandle(ref, () => ({
    zoom(scale) {
      graphRef.current?.zoom?.(scale)
      syncZoomPercent()
    },
    undo() {
      if (!readonlyRef.current) graphRef.current?.undo?.()
    },
    redo() {
      if (!readonlyRef.current) graphRef.current?.redo?.()
    },
    autoLayout() {
      if (!readonlyRef.current) graphRef.current?.layout?.()
    },
    fitGraph() {
      graphRef.current?.zoomToFit?.({ padding: 80, maxScale: 1.2 })
      syncZoomPercent()
    },
    centerGraph() {
      graphRef.current?.centerContent?.()
    },
    getGraph() {
      return graphRef.current
    },
  }))

  return (
    <div
      className={`logicflow-canvas-container ${isReadOnly ? 'is-readonly' : 'is-editable'}`}
      ref={graphParentRef}
    >
      <div className="logicflow-canvas" ref={graphContainerRef}></div>
      <div
        className={`mini-map logicflow-viewport-cluster${showMiniMap ? '' : ' logicflow-viewport-cluster--collapsed'}`}
        data-testid="logicflow-viewport-cluster"
        data-command-group="viewport"
        data-minimap-state={showMiniMap ? 'expanded' : 'collapsed'}
        data-layout={showMiniMap ? 'minimap-above-control-strip' : 'expand-button-only'}
        data-navigator-owner="canvas-local"
      >
        {showMiniMap ? (
          <div className="mini-map-preview">
            <div className="mini-map-container" ref={miniMapRef}></div>
          </div>
        ) : null}
        {showMiniMap ? (
          <div className="mini-map-controls">
            <button
              type="button"
              className="mini-map-control-button"
              onClick={() => onMapZoom?.('-')}
              aria-label={t('缩小')}
              data-command-id="zoom-out">
              <i className="fa-solid fa-magnifying-glass-minus"></i>
            </button>
            <span
              className="mini-map-zoom-percent"
              aria-label={t('当前缩放比例 {{p0}}%', { p0: zoomPercent })}
              data-command-id="zoom-percent">
              {zoomPercent}%
            </span>
            <button
              type="button"
              className="mini-map-control-button"
              onClick={() => onMapZoom?.('+')}
              aria-label={t('放大')}
              data-command-id="zoom-in">
              <i className="fa-solid fa-magnifying-glass-plus"></i>
            </button>
            <button
              type="button"
              className="mini-map-control-button"
              onClick={() => onFitGraph?.()}
              aria-label={t('适配画布')}
              data-command-id="fit-graph">
              <i className="fa-solid fa-maximize"></i>
            </button>
            <button
              type="button"
              className="mini-map-control-button"
              onClick={() => onCenterGraph?.()}
              aria-label={t('居中画布')}
              data-command-id="center-graph">
              <i className="fa-solid fa-crosshairs"></i>
            </button>
            <button
              type="button"
              className="mini-map-control-button mini-map-toggle-button"
              onClick={() => setShowMiniMap(false)}
              aria-label={t('收起小地图')}
              data-control-id="toggle-minimap"
              data-minimap-state="expanded"
            >
              <i className="fa-solid fa-chevron-down"></i>
            </button>
          </div>
        ) : (
          <button
            type="button"
            className="mini-map-control-button mini-map-toggle-button mini-map-toggle-button-collapsed"
            onClick={() => setShowMiniMap(true)}
            aria-label={t('展开小地图')}
            data-control-id="toggle-minimap"
            data-minimap-state="collapsed"
          >
            <i className="fa-solid fa-chevron-up"></i>
          </button>
        )}
      </div>
    </div>
  )
})

export default Canvas
