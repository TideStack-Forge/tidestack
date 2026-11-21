import { useLocale } from '../useLocale.js'
import { t } from '../i18n.js'
import React, {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react'
import { confirm, openDialog, Stomp } from 'ouroboros-sdk'
import { Dnd } from '@antv/x6-plugin-dnd'

import { createX6Node, getDefalutPortData, getUniqueName } from '@/node'
import { hasStoredNodePositions } from '@/node/positionSync.mjs'
import { transpileAll as transpileAllRaw } from '@/property-transpiler'
import { toast } from '@/utils'

import LeftPanel from './components/leftPanel/index.jsx'
import Toolbar from './components/toolbar/index.jsx'
import LogicFlowDiagnosticsSurface from './components/toolbar/LogicFlowDiagnosticsSurface.jsx'
import PropertiesPanel from './components/propertiesPanel/index.jsx'
import Canvas from './components/canvas/index.jsx'
import { createLogicFlowCanvasController } from './canvas/logicFlowCanvasController.mjs'
import {
  addInputPortToNode,
  confirmAndDeleteEdge,
  confirmAndDeleteNode,
  deleteInputPortsWithDialog,
} from './interactions/logicFlowInteractionController.mjs'
import {
  createEditorPanelBridge,
  createEditorPropertyAccessors,
} from './orchestration/editorBridgeHelpers.mjs'
import { createEditorInteractionActionsBridge } from './orchestration/editorInteractionActionsBridge.mjs'
import { createEditorSelectionBridge } from './orchestration/editorSelectionBridge.mjs'
import { useLogicFlowEditorState } from './state/useLogicFlowEditorState'
import {
  buildEdgePanelConfig,
  buildFlowPanelConfig,
  buildNodePanelConfig,
} from './propertyPanel/logicFlowPropertyPanelController.mjs'
import {
  edgeDefaultProperties,
  flowDefaultProperties,
  nodeDefaultProperties,
} from './defaultProperties'
import {
  derivePersistedValidationErrors,
  normalizeValidationEntries,
} from './debug/persistedValidationDiagnostics.mjs'
const RuntimeDebugWorkbench = React.lazy(() =>
  import(/* webpackChunkName: "logicflow-debug" */ './debug/RuntimeDebugWorkbench.jsx'))
import { createDevRuntimeDebugHost } from './debug/devRuntimeDebugHost.jsx'
import {
  LOGICFLOW_CHROME_VISIBILITY_ACTIONS,
  LOGICFLOW_INSPECTOR_SCOPES,
  createLogicFlowChromeVisibilityState,
  reconcileLogicFlowChromeVisibilityAvailability,
  reduceLogicFlowChromeVisibilityState,
} from './orchestration/editorChromeVisibility.mjs'
import {
  createLogicFlowToolbarDockingContext,
  createLogicFlowNavigatorDockingContext,
  resolveLogicFlowToolbarDockingGeometry,
} from './orchestration/editorSurfaceLayout.mjs'
import {
  LOGICFLOW_TOOLBAR_COMMAND_IDS,
  createLogicFlowToolbarCommandEntryDescriptor,
  createLogicFlowToolbarSchemaEntryDescriptor,
  buildLogicFlowToolbarEntries,
} from './orchestration/editorToolbarSurface.mjs'
import {
  DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
  normalizeLogicFlowCanvasAssistState,
} from './canvas/logicFlowCanvasAssistBridge.mjs'
import { buildLogicFlowDiagnosticsSurfaceContract } from './orchestration/editorDiagnosticsSurface.mjs'
import { navigateToValidationNode as navigateToValidationNodeDefault } from './orchestration/validationDiagnosticNavigation.mjs'
import './styles.scss'

function createEmptyToolbarSelectionState() {
  return {
    count: 0,
    kind: 'none',
    canDeleteInputPort: false,
    canAddInputPort: false,
  }
}

function deriveToolbarSelectionState(graph) {
  const selectedCells = graph?.getSelectedCells?.() || []
  if (!selectedCells.length) {
    return createEmptyToolbarSelectionState()
  }

  if (selectedCells.length > 1) {
    return {
      count: selectedCells.length,
      kind: 'multi',
      canDeleteInputPort: false,
      canAddInputPort: false,
    }
  }

  const selectedCell = selectedCells[0]
  if (selectedCell?.isEdge?.()) {
    return {
      count: 1,
      kind: 'edge',
      canDeleteInputPort: false,
      canAddInputPort: false,
    }
  }

  if (!selectedCell?.isNode?.()) {
    return createEmptyToolbarSelectionState()
  }

  const nodeDefine = selectedCell.attr?.('meta/nodeDefine')
  const inputPortRule = nodeDefine?.portRules?.input
  const inputPorts = selectedCell?.getData?.()?.inputPorts
  const inputPortCount = Array.isArray(inputPorts) ? inputPorts.length : 0
  const isLimitedInputPortNode = inputPortRule?.ruleType === 'limit'

  return {
    count: 1,
    kind: 'node',
    canDeleteInputPort:
      isLimitedInputPortNode && Number.isFinite(inputPortRule?.min)
        ? inputPortCount > inputPortRule.min
        : false,
    canAddInputPort:
      isLimitedInputPortNode &&
      (inputPortRule?.max == null || inputPortCount < inputPortRule.max),
  }
}

function mergeValidationErrors(...errorGroups) {
  const mergedEntries = []
  const seenEntries = new Set()

  errorGroups.forEach((errorGroup) => {
    ;(Array.isArray(errorGroup) ? errorGroup : []).forEach((entry) => {
      const message = typeof entry?.message === 'string' ? entry.message.trim() : ''
      if (!message) {
        return
      }

      const nodeName =
        typeof entry?.nodeName === 'string' && entry.nodeName.trim() ? entry.nodeName.trim() : ''
      const severity =
        typeof entry?.severity === 'string' && entry.severity.trim() ? entry.severity.trim() : ''
      const blocking = typeof entry?.blocking === 'boolean' ? String(entry.blocking) : ''
      const scope = typeof entry?.scope === 'string' && entry.scope.trim() ? entry.scope.trim() : ''
      const fieldPath =
        typeof entry?.fieldPath === 'string' && entry.fieldPath.trim() ? entry.fieldPath.trim() : ''
      const edgeIdentity = [
        typeof entry?.sourceNode === 'string' ? entry.sourceNode.trim() : '',
        Number.isInteger(entry?.sourceOutputPort) ? entry.sourceOutputPort : '',
        typeof entry?.targetNode === 'string' ? entry.targetNode.trim() : '',
        Number.isInteger(entry?.targetInputPort) ? entry.targetInputPort : '',
      ].join(':')
      const entryKey = `${message}::${nodeName}::${scope}::${fieldPath}::${edgeIdentity}::${severity}::${blocking}`

      if (seenEntries.has(entryKey)) {
        return
      }

      seenEntries.add(entryKey)
      mergedEntries.push(entry)
    })
  })

  return mergedEntries
}

const LogicFlowEditor = forwardRef(function LogicFlowEditor(
  { onChange, getProps = () => ({}), rootData = {} },
  ref
) {
  useLocale()
  const initialReadonly = !!(
    getProps?.()?.readonly ||
    getProps?.()?.static ||
    getProps?.()?.store?.getters?.isReadOnly
  )
  const validationSnapshot = getProps?.()?.validationSnapshot || null
  const propertiesPanelRef = useRef(null)
  const flowCanvasRef = useRef(null)
  const lastInspectorOpenRequestRef = useRef(null)
  const lastValidationFailureDataRef = useRef(null)
  const skipNextFlowDataValidationClearRef = useRef(false)
  const validationErrorsRef = useRef([])
  const transpileValidationSignatureRef = useRef('[]')
  const [graphReadyVersion, setGraphReadyVersion] = useState(0)
  const [debugOpen, setDebugOpen] = useState(false)
  const debugOpenRef = useRef(false)
  debugOpenRef.current = debugOpen
  const [persistedValidationErrors, setPersistedValidationErrors] = useState([])
  const [runtimeValidationErrors, setRuntimeValidationErrors] = useState([])
  const [transpileValidationErrors, setTranspileValidationErrors] = useState([])
  const [propertiesPanelLayout, setPropertiesPanelLayout] = useState({
    collapsed: true,
    panelWidth: 450,
  })
  const [toolbarSelectionState, setToolbarSelectionState] = useState(() =>
    createEmptyToolbarSelectionState()
  )
  const [canvasAssistState, setCanvasAssistState] = useState(() => ({
    ...DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
  }))
  const canvasAssistStateRef = useRef({
    ...DEFAULT_LOGICFLOW_CANVAS_ASSIST_STATE,
  })
  const [positionPersistenceEnabled, setPositionPersistenceEnabled] = useState(() =>
    hasStoredNodePositions(getProps?.()?.value)
  )
  const positionPersistenceEnabledRef = useRef(hasStoredNodePositions(getProps?.()?.value))
  const [chromeVisibility, setChromeVisibility] = useState(() =>
    createLogicFlowChromeVisibilityState({
      paletteAvailable: !initialReadonly,
      inspectorAvailable: true,
      paletteVisible: !initialReadonly,
    })
  )

  const { getNodeDefaultProperties, getEdgeDefaultProperties, getFlowDefaultProperties, transpileAll } =
    useMemo(
      () =>
        createEditorPropertyAccessors({
          getProps,
          nodeDefaultPropertiesFn: nodeDefaultProperties,
          edgeDefaultPropertiesFn: edgeDefaultProperties,
          flowDefaultPropertiesFn: flowDefaultProperties,
          transpileAllRawFn: transpileAllRaw,
        }),
      [getProps]
    )

  const {
    isReadOnly,
    allNodeDefines,
    flowData,
    propertyPanelDataVersion,
    wrapData,
  } = useLogicFlowEditorState({
    store: getProps()?.store,
    getProps,
    getRootData: () => rootData,
  })

  const configuredDebugHost = getProps?.()?.logicFlowHost?.debug === false ? false : getProps?.()?.env?.logicFlowDebug
  const debugFetcher = getProps?.()?.env?.fetcher
  const debugHost = useMemo(
    () => configuredDebugHost === undefined
      ? (rootData?.appName ? createDevRuntimeDebugHost({ fetcher: debugFetcher, appName: rootData.appName, stomp: Stomp }) : null)
      : configuredDebugHost,
    [configuredDebugHost, debugFetcher, rootData?.appName]
  )
  useEffect(() => {
    if (!debugHost) setDebugOpen(false)
  }, [debugHost])

  const isReadOnlyRef = useMemo(
    () => ({
      get value() {
        return isReadOnly || debugOpenRef.current
      },
    }),
    [isReadOnly]
  )
  const flowCanvasAdapterRef = useMemo(
    () => ({
      get value() {
        return flowCanvasRef.current
      },
    }),
    []
  )
  const validationErrors = useMemo(
    () => mergeValidationErrors(persistedValidationErrors, runtimeValidationErrors, transpileValidationErrors),
    [persistedValidationErrors, runtimeValidationErrors, transpileValidationErrors]
  )

  useEffect(() => {
    validationErrorsRef.current = validationErrors
  }, [validationErrors])

  useEffect(() => {
    canvasAssistStateRef.current = canvasAssistState
  }, [canvasAssistState])

  useEffect(() => {
    positionPersistenceEnabledRef.current = positionPersistenceEnabled
  }, [positionPersistenceEnabled])

  useEffect(() => {
    const nextPositionPersistenceEnabled = hasStoredNodePositions(flowData)
    if (positionPersistenceEnabledRef.current === nextPositionPersistenceEnabled) {
      return
    }
    positionPersistenceEnabledRef.current = nextPositionPersistenceEnabled
    setPositionPersistenceEnabled(nextPositionPersistenceEnabled)
  }, [flowData])

  const { showPropertiesPanel, showTranspileWarnings } =
    useMemo(
      () =>
        createEditorPanelBridge({
          propertiesPanelRef: {
            get value() {
              return propertiesPanelRef.current
            },
          },
          toastFn: toast,
          translate: t,
        }),
      []
    )

  const handleValidateFailed = useCallback((payload = {}) => {
    lastValidationFailureDataRef.current = payload?.data || null
    const normalizedEntries = normalizeValidationEntries(payload?.entries || [])

    if (normalizedEntries.length) {
      setRuntimeValidationErrors(normalizedEntries)
      return
    }

    const normalizedErrors = normalizeValidationEntries(payload?.errors || [])

    if (normalizedErrors.length) {
      setRuntimeValidationErrors(normalizedErrors)
      return
    }

    if (payload?.message) {
      setRuntimeValidationErrors([{ message: payload.message }])
      return
    }

    setRuntimeValidationErrors([])
  }, [])

  const handleTranspileWarnings = useCallback(
    (payload = {}) => {
      const diagnostics = Array.isArray(payload?.diagnostics) ? payload.diagnostics : []
      const nextDiagnostics = normalizeValidationEntries(diagnostics)
      const nextSignature = JSON.stringify(nextDiagnostics)
      if (nextSignature === transpileValidationSignatureRef.current) {
        return
      }
      transpileValidationSignatureRef.current = nextSignature
      setTranspileValidationErrors(nextDiagnostics)
    },
    []
  )

  const handleCanvasChange = useCallback(
    (nextData) => {
      const isFailedChange = lastValidationFailureDataRef.current === nextData
      lastValidationFailureDataRef.current = null
      skipNextFlowDataValidationClearRef.current = isFailedChange
      if (!isFailedChange) {
        setRuntimeValidationErrors([])
      }
      onChange?.(nextData)
    },
    [onChange]
  )

  useEffect(() => {
    if (validationSnapshot == null) {
      return
    }
    lastValidationFailureDataRef.current = null
    setPersistedValidationErrors(derivePersistedValidationErrors(validationSnapshot))
  }, [validationSnapshot])

  useEffect(() => {
    if (validationSnapshot != null) {
      skipNextFlowDataValidationClearRef.current = false
      return
    }
    if (skipNextFlowDataValidationClearRef.current) {
      skipNextFlowDataValidationClearRef.current = false
      return
    }
    lastValidationFailureDataRef.current = null
    setPersistedValidationErrors(derivePersistedValidationErrors(flowData))
    setRuntimeValidationErrors([])
  }, [flowData, validationSnapshot])

  useEffect(() => {
    const graph = flowCanvasRef.current?.getGraph?.()
    graph?.updateLogicFlowValidationEntries?.(validationErrors)
  }, [graphReadyVersion, validationErrors])

  useEffect(() => {
    void propertiesPanelRef.current?.refresh?.()
  }, [propertyPanelDataVersion])

  useEffect(() => {
    void propertiesPanelRef.current?.refresh?.()
  }, [validationErrors])

  const toolbarDockingContext = useMemo(
    () =>
      createLogicFlowToolbarDockingContext({
        paletteAvailable: debugOpen || chromeVisibility.palette.available,
        paletteVisible: debugOpen || chromeVisibility.palette.visible,
      }),
    [debugOpen, chromeVisibility.palette.available, chromeVisibility.palette.visible]
  )
  const toolbarDockingGeometry = useMemo(
    () => resolveLogicFlowToolbarDockingGeometry(toolbarDockingContext),
    [toolbarDockingContext]
  )
  const navigatorDockingContext = useMemo(
    () =>
      createLogicFlowNavigatorDockingContext({
        inspectorVisible: chromeVisibility.inspector.visible,
        inspectorOccupiedWidth: chromeVisibility.inspector.visible
          ? propertiesPanelLayout.panelWidth
          : 0,
      }),
    [chromeVisibility.inspector.visible, propertiesPanelLayout.panelWidth]
  )

  useEffect(() => {
    setChromeVisibility((current) =>
      reconcileLogicFlowChromeVisibilityAvailability(current, {
        paletteAvailable: !isReadOnly,
        inspectorAvailable: true,
      })
    )
  }, [isReadOnly])

  const dispatchChromeVisibility = useCallback((action) => {
    setChromeVisibility((current) => reduceLogicFlowChromeVisibilityState(current, action))
  }, [])

  const setPaletteVisible = useCallback(
    (visible) => {
      setChromeVisibility((current) => {
        const nextVisible = current.palette.available ? Boolean(visible) : false
        return {
          ...current,
          palette: {
            ...current.palette,
            visible: nextVisible,
          },
        }
      })
    },
    []
  )
  const closePalette = useCallback(() => {
    setPaletteVisible(false)
  }, [setPaletteVisible])

  const setInspectorVisible = useCallback(
    (visible) => {
      dispatchChromeVisibility({
        type: visible
          ? LOGICFLOW_CHROME_VISIBILITY_ACTIONS.OPEN_INSPECTOR
          : LOGICFLOW_CHROME_VISIBILITY_ACTIONS.CLOSE_INSPECTOR,
      })
    },
    [dispatchChromeVisibility]
  )
  const closeInspector = useCallback(() => {
    setInspectorVisible(false)
  }, [setInspectorVisible])

  const syncToolbarSelectionState = useCallback((graph) => {
    setToolbarSelectionState(deriveToolbarSelectionState(graph))
  }, [])

  const {
    mapZoom,
    fitGraph,
    centerGraph,
    autoLayout,
    initDnd,
    startDrag,
    getCurrentGraph,
    applyCanvasAssistState,
    readCanvasAssistState,
    dispose: disposeCanvasController,
  } = useMemo(
    () =>
      createLogicFlowCanvasController({
        isReadOnly: isReadOnlyRef,
        flowCanvasRef: flowCanvasAdapterRef,
        createNodeFn: createX6Node,
        getUniqueNameFn: getUniqueName,
        DndClass: Dnd,
      }),
    [isReadOnlyRef, flowCanvasAdapterRef]
  )

  useEffect(() => {
    setCanvasAssistState(
      normalizeLogicFlowCanvasAssistState(readCanvasAssistState())
    )
  }, [graphReadyVersion, readCanvasAssistState])

  useEffect(() => {
    const graph = flowCanvasRef.current?.getGraph?.()
    graph?.setLogicFlowPositionPersistenceEnabled?.(positionPersistenceEnabledRef.current)
  }, [graphReadyVersion, positionPersistenceEnabled])

  const updateCanvasAssistState = useCallback(
    (updater) => {
      const current = canvasAssistStateRef.current
      const nextState = normalizeLogicFlowCanvasAssistState(
        typeof updater === 'function' ? updater(current) : { ...current, ...updater }
      )
      canvasAssistStateRef.current = nextState
      applyCanvasAssistState(nextState)
      setCanvasAssistState(nextState)
    },
    [applyCanvasAssistState]
  )

  const { showLogicFlowProperties, showNodeProperties, showEdgeProperties } =
    useMemo(
      () =>
        createEditorSelectionBridge({
          showPropertiesPanel,
          getCurrentGraph,
          getValidationEntries: () => validationErrorsRef.current,
          getFlowDefaultProperties,
          getNodeDefaultProperties,
          getEdgeDefaultProperties,
          transpileAll,
          wrapData,
          buildFlowPanelConfigFn: buildFlowPanelConfig,
          buildNodePanelConfigFn: buildNodePanelConfig,
          buildEdgePanelConfigFn: buildEdgePanelConfig,
        }),
      [
        showPropertiesPanel,
        getCurrentGraph,
        getFlowDefaultProperties,
        getNodeDefaultProperties,
        getEdgeDefaultProperties,
        transpileAll,
        wrapData,
        validationErrorsRef,
      ]
    )

  const { deleteNode, deleteEdge, addInPort, showDeleteInputPortDialog } = useMemo(
    () =>
      createEditorInteractionActionsBridge({
        confirmFn: (message) => confirm(t(message)),
        openDialogFn: (schema, context) =>
          openDialog({ ...schema, localeNamespace: 'logicflow-editor' }, context),
        translate: t,
        createDefaultPortData: getDefalutPortData,
        confirmAndDeleteNodeFn: confirmAndDeleteNode,
        confirmAndDeleteEdgeFn: confirmAndDeleteEdge,
        addInputPortToNodeFn: addInputPortToNode,
        deleteInputPortsWithDialogFn: deleteInputPortsWithDialog,
      }),
    []
  )

  const rememberInspectorOpenRequest = useCallback((scope, showRequest) => {
    lastInspectorOpenRequestRef.current = {
      scope,
      showRequest,
    }
  }, [])

  const openFlowInspector = useCallback(
    async (graph) => {
      const nextGraph = graph || getCurrentGraph() || flowCanvasRef.current?.getGraph?.()
      if (!nextGraph) {
        return
      }

      const showRequest = () => showLogicFlowProperties(nextGraph)
      rememberInspectorOpenRequest(LOGICFLOW_INSPECTOR_SCOPES.FLOW, showRequest)
      await showRequest()
    },
    [getCurrentGraph, rememberInspectorOpenRequest, showLogicFlowProperties]
  )

  const openNodeInspector = useCallback(
    async (node, graph) => {
      const nextGraph = graph || getCurrentGraph() || flowCanvasRef.current?.getGraph?.()
      if (!node || !nextGraph) {
        return
      }

      const showRequest = () => showNodeProperties(node, nextGraph)
      rememberInspectorOpenRequest(LOGICFLOW_INSPECTOR_SCOPES.NODE, showRequest)
      await showRequest()
    },
    [getCurrentGraph, rememberInspectorOpenRequest, showNodeProperties]
  )

  const openEdgeInspector = useCallback(
    async (edge, graph) => {
      const nextGraph = graph || getCurrentGraph() || flowCanvasRef.current?.getGraph?.()
      if (!edge || !nextGraph) {
        return
      }

      const showRequest = () => showEdgeProperties(edge, nextGraph)
      rememberInspectorOpenRequest(LOGICFLOW_INSPECTOR_SCOPES.EDGE, showRequest)
      await showRequest()
    },
    [getCurrentGraph, rememberInspectorOpenRequest, showEdgeProperties]
  )

  const reopenLastInspectorScope = useCallback(async () => {
    const lastRequest = lastInspectorOpenRequestRef.current
    if (typeof lastRequest?.showRequest === 'function') {
      await lastRequest.showRequest()
      return
    }

    await openFlowInspector()
  }, [openFlowInspector])

  const handleSelectionCleared = useCallback(() => {
    if (debugOpenRef.current) return
    syncToolbarSelectionState(getCurrentGraph())
    closeInspector()
  }, [closeInspector, getCurrentGraph, syncToolbarSelectionState])

  const handleCanvasBlankClick = useCallback((graph) => {
    if (debugOpenRef.current) return
    syncToolbarSelectionState(graph)
    closeInspector()
  }, [closeInspector, syncToolbarSelectionState])

  const handleNodeSelected = useCallback(
    async (node, graph) => {
      if (debugOpenRef.current) return
      syncToolbarSelectionState(graph)
      graph?.updateLogicFlowValidationEntries?.(validationErrors)
      await openNodeInspector(node, graph)
    },
    [openNodeInspector, syncToolbarSelectionState, validationErrors]
  )

  const handleEdgeSelected = useCallback(
    async (edge, graph) => {
      if (debugOpenRef.current) return
      syncToolbarSelectionState(graph)
      await openEdgeInspector(edge, graph)
    },
    [openEdgeInspector, syncToolbarSelectionState]
  )

  const handleToolbarCommand = useCallback(
    async (commandId) => {
      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.ONLINE_DEBUG) {
        if (!debugHost || !flowData?.name) return
        setDebugOpen(true)
        return
      }
      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_GRID_VISIBILITY) {
        if (isReadOnlyRef.value) {
          return
        }
        updateCanvasAssistState((current) => {
          const nextGridEnabled = !(current.gridVisible && current.gridSnappingEnabled)
          return {
            ...current,
            gridVisible: nextGridEnabled,
            gridSnappingEnabled: nextGridEnabled,
          }
        })
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_NODE_ALIGNMENT) {
        if (isReadOnlyRef.value) {
          return
        }
        updateCanvasAssistState((current) => ({
          ...current,
          nodeAlignmentEnabled: !current.nodeAlignmentEnabled,
        }))
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_POSITION_PERSISTENCE) {
        if (isReadOnlyRef.value) {
          return
        }
        const nextPositionPersistenceEnabled = !positionPersistenceEnabledRef.current
        positionPersistenceEnabledRef.current = nextPositionPersistenceEnabled
        setPositionPersistenceEnabled(nextPositionPersistenceEnabled)
        const graph = getCurrentGraph() || flowCanvasRef.current?.getGraph?.()
        graph?.setLogicFlowPositionPersistenceEnabled?.(nextPositionPersistenceEnabled)
        graph?.flushLogicFlowChange?.()
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_PALETTE) {
        if (!chromeVisibility.palette.available) {
          return
        }
        dispatchChromeVisibility({
          type: LOGICFLOW_CHROME_VISIBILITY_ACTIONS.TOGGLE_PALETTE,
        })
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_INSPECTOR) {
        if (chromeVisibility.inspector.visible) {
          dispatchChromeVisibility({
            type: LOGICFLOW_CHROME_VISIBILITY_ACTIONS.CLOSE_INSPECTOR,
          })
          return
        }

        await reopenLastInspectorScope()
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW) {
        await openFlowInspector()
        return
      }

      const graph = getCurrentGraph()
      const selectedCells = graph?.getSelectedCells?.() || []
      const selectedCell = selectedCells.length === 1 ? selectedCells[0] : null

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_SELECTION) {
        if (!graph || !selectedCells.length || isReadOnlyRef.value) {
          return
        }

        if (selectedCells.length > 1) {
          if (await confirm(t('确定要删除已选中的元素吗？'))) {
            graph.removeCells(selectedCells)
            syncToolbarSelectionState(graph)
          }
          return
        }

        if (selectedCell?.isNode?.()) {
          await deleteNode(selectedCell, graph)
          syncToolbarSelectionState(graph)
          return
        }

        if (selectedCell?.isEdge?.()) {
          await deleteEdge(selectedCell, graph)
          syncToolbarSelectionState(graph)
        }
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_INPUT_PORT) {
        if (!graph || !selectedCell?.isNode?.() || isReadOnlyRef.value) {
          return
        }
        await showDeleteInputPortDialog(selectedCell, graph)
        syncToolbarSelectionState(graph)
        return
      }

      if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.ADD_INPUT_PORT) {
        if (!graph || !selectedCell?.isNode?.() || isReadOnlyRef.value) {
          return
        }
        addInPort(selectedCell, graph)
        syncToolbarSelectionState(graph)
      }
    },
    [
      addInPort,
      chromeVisibility.inspector.visible,
      chromeVisibility.palette.available,
      deleteEdge,
      deleteNode,
      debugHost,
      flowData?.name,
      dispatchChromeVisibility,
      getCurrentGraph,
      updateCanvasAssistState,
      openFlowInspector,
      reopenLastInspectorScope,
      showDeleteInputPortDialog,
      isReadOnlyRef,
      syncToolbarSelectionState,
    ]
  )

  const navigateToValidationNode = useCallback(
    (nodeName, graph = null) =>
      navigateToValidationNodeDefault({
        nodeName,
        graph: getCurrentGraph() || graph,
        showNodeProperties: openNodeInspector,
      }),
    [getCurrentGraph, openNodeInspector]
  )

  const handleValidationEntryClick = useCallback(
    async (entry) => {
      const nodeName = typeof entry?.nodeName === 'string' ? entry.nodeName.trim() : ''
      if (!nodeName) {
        return false
      }
      const navigationResult = await navigateToValidationNode(nodeName)
      return navigationResult?.ok !== false
    },
    [navigateToValidationNode]
  )

  const diagnosticsContract = useMemo(
    () => buildLogicFlowDiagnosticsSurfaceContract({ validationErrors }),
    [validationErrors]
  )

  const validationEntry = useMemo(
    () => (
      <LogicFlowDiagnosticsSurface
        diagnosticsContract={diagnosticsContract}
        onNavigate={handleValidationEntryClick}
      />
    ),
    [diagnosticsContract, handleValidationEntryClick]
  )

  const selectedToolbarCommandEntries = useMemo(() => {
    return [
      createLogicFlowToolbarCommandEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.ONLINE_DEBUG,
        enabled: Boolean(debugHost && flowData?.name),
        visible: Boolean(debugHost),
      }),
      createLogicFlowToolbarCommandEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_SELECTION,
        enabled: !isReadOnly && toolbarSelectionState.count > 0,
        visible: !isReadOnly && toolbarSelectionState.count > 0,
      }),
      createLogicFlowToolbarCommandEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_INPUT_PORT,
        enabled: !isReadOnly && toolbarSelectionState.canDeleteInputPort,
        visible: !isReadOnly && toolbarSelectionState.kind === 'node',
      }),
      createLogicFlowToolbarCommandEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.ADD_INPUT_PORT,
        enabled: !isReadOnly && toolbarSelectionState.canAddInputPort,
        visible: !isReadOnly && toolbarSelectionState.kind === 'node',
      }),
    ].filter(Boolean)
  }, [isReadOnly, toolbarSelectionState, debugHost, flowData?.name])

  const toolbarEntries = useMemo(
    () =>
      buildLogicFlowToolbarEntries({
        chromeVisibility,
        canvasAssistState,
        canvasAssistAvailability: {
          visible: true,
          enabled: !isReadOnly,
        },
        positionPersistence: {
          visible: true,
          enabled: !isReadOnly,
          pressed: positionPersistenceEnabled,
        },
        flowEntry: createLogicFlowToolbarSchemaEntryDescriptor({
          commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW,
          label: '流程属性',
          iconClassName: 'fa-solid fa-circle-info',
          enabled: true,
          visible: true,
          pressed:
            chromeVisibility.inspector.visible &&
            lastInspectorOpenRequestRef.current?.scope === LOGICFLOW_INSPECTOR_SCOPES.FLOW,
        }),
        commandEntries: selectedToolbarCommandEntries,
        validationEntry,
      }),
    [
      chromeVisibility,
      canvasAssistState,
      selectedToolbarCommandEntries,
      validationEntry,
      isReadOnly,
      positionPersistenceEnabled,
    ]
  )

  useEffect(() => {
    return () => {
      disposeCanvasController()
    }
  }, [disposeCanvasController])


  useImperativeHandle(ref, () => ({
    navigateToValidationNode,
    async flushPendingChanges() {
      await propertiesPanelRef.current?.flushChanges?.()
      const graph = getCurrentGraph() || flowCanvasRef.current?.getGraph?.()
      graph?.flushLogicFlowChange?.()
    },
    getGraph() {
      return getCurrentGraph() || flowCanvasRef.current?.getGraph?.() || null
    },
    getNodeDefines() {
      return allNodeDefines
    },
    showNodeProperties: openNodeInspector,
    showEdgeProperties: openEdgeInspector,
    showLogicFlowProperties: openFlowInspector,
    readFlowData() {
      return flowData
    },
    getPropertiesPanelScope() {
      return propertiesPanelRef.current?.getAmisScoped?.() || null
    },
    getPropertiesPanelElement() {
      return propertiesPanelRef.current?.getFormContainerElement?.() || null
    },
    async disposePropertiesPanel() {
      await propertiesPanelRef.current?.destroyRuntime?.()
    },
  }))

  return (
    <div
      className={`wrapper-editor-container${debugOpen ? ' is-debugging' : ''}`}
      style={{
        '--logicflow-mini-map-right': `${navigatorDockingContext.rightInset}px`,
        '--logicflow-mini-map-bottom': `${navigatorDockingContext.bottomInset}px`,
        '--logicflow-toolbar-left': `${toolbarDockingGeometry.left}px`,
        '--logicflow-toolbar-top': `${toolbarDockingGeometry.top}px`,
      }}
    >
      {!isReadOnly ? (
        <LeftPanel
          nodeDefineList={allNodeDefines}
          onStartDrag={startDrag}
          visible={!debugOpen && chromeVisibility.palette.visible}
          onRequestClose={closePalette}
        />
      ) : null}
      <div className="logicflow-editor-canvas-slot" aria-label={debugOpen ? '执行运行图' : '逻辑流设计画布'}><Canvas
        className="logicflow-canvas"
        ref={flowCanvasRef}
        nodeDefines={allNodeDefines}
        data={flowData}
        onMapZoom={mapZoom}
        onFitGraph={fitGraph}
        onCenterGraph={centerGraph}
        onNodeSelected={handleNodeSelected}
        onEdgeSelected={handleEdgeSelected}
        onFlowSelected={handleCanvasBlankClick}
        onSelectionCleared={handleSelectionCleared}
        onGraphInited={(graph) => {
          initDnd(graph)
          syncToolbarSelectionState(graph)
          setGraphReadyVersion((value) => value + 1)
        }}
        onTranspileWarnings={(payload) => {
          showTranspileWarnings(payload)
          handleTranspileWarnings(payload)
        }}
        onValidateFailed={handleValidateFailed}
        onChange={handleCanvasChange}
        isReadOnly={isReadOnly || debugOpen}
      /></div>
      {!debugOpen && <Toolbar
        onLayout={autoLayout}
        entries={toolbarEntries}
        onExecuteCommand={handleToolbarCommand}
        dockingContext={toolbarDockingContext}
      />}
      {debugOpen && debugHost && (
        <React.Suspense fallback={<div role="status">正在加载执行调试…<button type="button" onClick={() => setDebugOpen(false)}>取消加载</button></div>}><RuntimeDebugWorkbench
          key={debugHost.key + ':' + flowData?.name}
          host={debugHost}
          flowName={flowData?.name}
          graph={flowCanvasRef.current?.getGraph?.()}
          onClose={() => setDebugOpen(false)}
        /></React.Suspense>
      )}
      <PropertiesPanel
        ref={propertiesPanelRef}
        readonly={isReadOnly}
        visible={!debugOpen && chromeVisibility.inspector.visible}
        onVisibleChange={setInspectorVisible}
        onLayoutChange={setPropertiesPanelLayout}
      />
    </div>
  )
})

export default LogicFlowEditor
