import { useLocale } from '../../../useLocale.js'
import { t } from '../../../i18n.js'
import React, {
  forwardRef,
  useCallback,
  useLayoutEffect,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react'
import { debounce } from 'lodash'
import isEqual from 'lodash/isEqual.js'

import { renderAmisSchema } from '@/utils'

import DraggingProvider from './draggingProvider'
import EditorPanel from '../panel/EditorPanel.jsx'
import {
  closePropertyPanel,
  disposePropertyPanelRuntime,
  notifyPropertyPanelLayoutSettled,
  renderPropertyPanelSchema,
  syncPropertyPanelFormRuntime,
  waitForPropertyPanelLayoutFrame,
} from './propertyPanelLifecycle.mjs'

const DEFAULT_PANEL_TITLE = '属性面板'
const RUNTIME_SIGNATURE_IGNORED_SCHEMA_KEYS = new Set([
  'externalFieldErrors',
  'onChange',
  'onSubmit',
])

function normalizePropertyFormValues(values = {}) {
  const value = { ...(values || {}) }
  Object.keys(value).forEach((key) => {
    if (key === 'appName' || key.startsWith('__')) {
      delete value[key]
    }
  })
  return value
}

function normalizePropertyPanelSchemaForSignature(value) {
  if (typeof value === 'function') {
    return undefined
  }

  if (Array.isArray(value)) {
    return value.map((item) => normalizePropertyPanelSchemaForSignature(item))
  }

  if (!value || typeof value !== 'object') {
    return value
  }

  return Object.keys(value)
    .sort()
    .reduce((acc, key) => {
      if (RUNTIME_SIGNATURE_IGNORED_SCHEMA_KEYS.has(key)) {
        return acc
      }

      const normalizedValue = normalizePropertyPanelSchemaForSignature(value[key])
      if (typeof normalizedValue !== 'undefined') {
        acc[key] = normalizedValue
      }
      return acc
    }, {})
}

function createPropertyPanelRuntimeSignature(schema, runtimeKey) {
  return JSON.stringify({
    runtimeKey: runtimeKey || '',
    schema: normalizePropertyPanelSchemaForSignature(schema),
  })
}

function summarizeRenderedControl(control) {
  if (!control) {
    return null
  }

  return {
    rendererName: control?.constructor?.name || null,
    optionCount: Array.isArray(control?.props?.options) ? control.props.options.length : null,
    options: Array.isArray(control?.props?.options)
      ? control.props.options.map((option) => ({
          label: option?.label ?? null,
          value: option?.value ?? null,
          hidden: option?.hidden ?? null,
          visible: option?.visible ?? null,
        }))
      : null,
    source: control?.props?.source ?? null,
    searchable: control?.props?.searchable ?? null,
    dataKeys:
      control?.props?.data && typeof control.props.data === 'object'
        ? Object.keys(control.props.data)
        : null,
  }
}

function logRenderedFlowControls(scopeHandle) {
  const scopeRef = scopeHandle?.getScopeRef?.()
  const formComponent = scopeRef?.getComponentByName?.('propertyForm')
  const names = ['entryNode', 'exitNode', 'errorEntryNode']
  const controls = Object.fromEntries(
    names.map((name) => {
      const control =
        scopeRef?.getComponentByName?.(`propertyForm.${name}`) ||
        formComponent?.context?.getComponentByName?.(name) ||
        scopeRef?.getComponentByName?.(name) ||
        null

      return [name, summarizeRenderedControl(control)]
    })
  )

  console?.debug?.('[logicflow] propertiesPanel.renderedFlowControls', {
    hasScopeRef: !!scopeRef,
    hasFormComponent: !!formComponent,
    controls,
  })
}

const PropertiesPanel = forwardRef(function PropertiesPanel(
  { readonly = false, visible = false, onVisibleChange, onLayoutChange },
  ref
) {
  useLocale()
  const MIN_SCROLL_INDICATOR_HEIGHT = 24
  const draggingProviderRef = useRef(new DraggingProvider())
  const formContainerRef = useRef(null)
  const resizeHandleRef = useRef(null)
  const amisScopedRef = useRef(null)
  const currentConfigFactoryRef = useRef(null)
  const scrollCueTimerRef = useRef(null)
  const runtimeOperationRef = useRef(Promise.resolve())
  const renderVersionRef = useRef(0)
  const runtimeSignatureRef = useRef(null)
  const propertyFormBaselineRef = useRef({})
  const unmountedRef = useRef(false)

  const collapsed = !visible
  const [inited, setInited] = useState(false)
  const [panelWidth, setPanelWidth] = useState(450)
  const [panelTitle, setPanelTitle] = useState(DEFAULT_PANEL_TITLE)
  const [isFormScrolling, setIsFormScrolling] = useState(false)
  const [scrollIndicator, setScrollIndicator] = useState({
    visible: false,
    height: 0,
    offset: 0,
  })

  const collapsedRef = useRef(collapsed)
  useEffect(() => {
    collapsedRef.current = collapsed
  }, [collapsed])

  const toggle = useMemo(
    () =>
      debounce(() => {
        onVisibleChange?.(collapsedRef.current)
      }, 100),
    [onVisibleChange]
  )

  useEffect(() => {
    const resizeHandle = resizeHandleRef.current
    if (!resizeHandle) return
    draggingProviderRef.current.init(resizeHandle, ({ x }) => {
      setPanelWidth((width) => width - x)
    })
  }, [resizeHandleRef.current])

  const syncScrollIndicator = useCallback(() => {
    const formContainer = formContainerRef.current
    if (!formContainer) {
      setScrollIndicator((current) =>
        current.visible || current.height !== 0 || current.offset !== 0
          ? { visible: false, height: 0, offset: 0 }
          : current
      )
      return
    }

    const { clientHeight, scrollHeight, scrollTop } = formContainer
    const maxScrollTop = Math.max(scrollHeight - clientHeight, 0)
    const hasScrollableContent = clientHeight > 0 && scrollHeight > clientHeight

    if (!hasScrollableContent) {
      setScrollIndicator((current) =>
        current.visible || current.height !== 0 || current.offset !== 0
          ? { visible: false, height: 0, offset: 0 }
          : current
      )
      return
    }

    const visibleRatio = clientHeight / scrollHeight
    const thumbHeight = Math.max(clientHeight * visibleRatio, MIN_SCROLL_INDICATOR_HEIGHT)
    const maxOffset = Math.max(clientHeight - thumbHeight, 0)
    const progress = maxScrollTop === 0 ? 0 : scrollTop / maxScrollTop
    const thumbOffset = maxOffset * progress

    setScrollIndicator({
      visible: true,
      height: thumbHeight,
      offset: thumbOffset,
    })
  }, [])

  const handleFormScroll = useCallback(() => {
    syncScrollIndicator()
    setIsFormScrolling(true)
    if (scrollCueTimerRef.current) {
      clearTimeout(scrollCueTimerRef.current)
    }
    scrollCueTimerRef.current = setTimeout(() => {
      setIsFormScrolling(false)
      scrollCueTimerRef.current = null
    }, 480)
  }, [])

  useEffect(() => {
    const formContainer = formContainerRef.current
    if (!formContainer) {
      return
    }

    const handleNativeScroll = () => {
      handleFormScroll()
    }

    formContainer.addEventListener('scroll', handleNativeScroll, { passive: true })
    return () => {
      formContainer.removeEventListener('scroll', handleNativeScroll)
    }
  }, [handleFormScroll])

  useEffect(() => {
    onLayoutChange?.({ collapsed, panelWidth })
  }, [collapsed, panelWidth, onLayoutChange])

  useEffect(() => {
    if (!collapsed) {
      return
    }
    if (scrollCueTimerRef.current) {
      clearTimeout(scrollCueTimerRef.current)
      scrollCueTimerRef.current = null
    }
    setIsFormScrolling(false)
    syncScrollIndicator()
  }, [collapsed])

  useLayoutEffect(() => {
    syncScrollIndicator()
  }, [syncScrollIndicator, collapsed, panelWidth, inited])

  useEffect(() => {
    const formContainer = formContainerRef.current
    if (!formContainer || typeof ResizeObserver === 'undefined') {
      return
    }

    const resizeObserver = new ResizeObserver(() => {
      syncScrollIndicator()
    })

    resizeObserver.observe(formContainer)
    Array.from(formContainer.children).forEach((child) => {
      resizeObserver.observe(child)
    })

    return () => {
      resizeObserver.disconnect()
    }
  }, [syncScrollIndicator, inited])

  const destroyRuntime = useCallback(async () => {
    renderVersionRef.current += 1
    const queuedDestroy = runtimeOperationRef.current
      .catch(() => {})
      .then(async () => {
        const currentScope = amisScopedRef.current
        amisScopedRef.current = null
        runtimeSignatureRef.current = null
        await disposePropertyPanelRuntime({
          draggingProvider: draggingProviderRef.current,
          toggleFn: toggle,
          amisScoped: currentScope,
          formContainer: formContainerRef.current,
        })
      })

    runtimeOperationRef.current = queuedDestroy.catch(() => {})
    await queuedDestroy
  }, [toggle])

  useEffect(() => {
    return () => {
      unmountedRef.current = true
      if (scrollCueTimerRef.current) {
        clearTimeout(scrollCueTimerRef.current)
        scrollCueTimerRef.current = null
      }
      void destroyRuntime()
    }
  }, [destroyRuntime])

  async function renderForm(
    formItems,
    formData,
    saveAction,
    fieldErrors = {},
    forceRemount = false,
    runtimeKey = ''
  ) {
    console?.debug?.('[logicflow] propertiesPanel.renderForm', {
      formDataKeys: Object.keys(formData || {}),
      entryCount: Array.isArray(formData?.__ENTRY_NODES) ? formData.__ENTRY_NODES.length : null,
      exitCount: Array.isArray(formData?.__EXIT_NODES) ? formData.__EXIT_NODES.length : null,
      errorEntryCount: Array.isArray(formData?.__ERROR_ENTRY_NODES)
        ? formData.__ERROR_ENTRY_NODES.length
        : null,
      nodeTypeCount: Array.isArray(formData?.__NODE_TYPES) ? formData.__NODE_TYPES.length : null,
    })

    const baselineData = formData && typeof formData === 'object' ? formData : {}
    const normalizedBaselineData = normalizePropertyFormValues(baselineData)
    propertyFormBaselineRef.current = normalizedBaselineData
    const saveFormValues = (values) => {
      const value = normalizePropertyFormValues(values)
      if (isEqual(value, propertyFormBaselineRef.current)) {
        return
      }
      saveAction(value)
    }

    const schema = {
      type: 'container',
      body: {
        type: 'form',
        name: 'propertyForm',
        mode: 'horizontal',
        title: '',
        wrapWithPanel: false,
        body: formItems,
        externalErrorSource: 'logicflow-validation',
        externalFieldErrors: fieldErrors,
        submitOnChange: false,
        static: readonly,
        onChange: saveFormValues,
        onSubmit: saveFormValues,
      },
    }

    const runtimeSignature = createPropertyPanelRuntimeSignature(schema, runtimeKey)
    const renderVersion = ++renderVersionRef.current
    if (
      !forceRemount &&
      amisScopedRef.current &&
      runtimeSignatureRef.current === runtimeSignature &&
      syncPropertyPanelFormRuntime(amisScopedRef.current, { formData, fieldErrors })
    ) {
      setTimeout(() => {
        logRenderedFlowControls(amisScopedRef.current)
      }, 0)
      return
    }

    const nextScoped = await renderPropertyPanelSchema({
      formContainer: formContainerRef.current,
      amisScoped: amisScopedRef.current,
      schema,
      formData,
      fieldErrors,
      createElementFn: (tagName) => document.createElement(tagName),
      renderAmisSchemaFn: renderAmisSchema,
    })

    if (unmountedRef.current || renderVersion !== renderVersionRef.current) {
      await nextScoped?.destroy?.()
      return
    }

    amisScopedRef.current = nextScoped
    runtimeSignatureRef.current = runtimeSignature

    setTimeout(() => {
      logRenderedFlowControls(amisScopedRef.current)
    }, 0)
  }

  async function renderCurrentConfig({ forceRemount = false } = {}) {
    const queuedRender = runtimeOperationRef.current
      .catch(() => {})
      .then(async () => {
        const configFactory = currentConfigFactoryRef.current
        if (!configFactory || unmountedRef.current) {
          return
        }

        const config = configFactory()
        setPanelTitle(config?.panelTitle || DEFAULT_PANEL_TITLE)
        await renderForm(
          config.formItems,
          config.data,
          config.saveAction,
          config.fieldErrors,
          forceRemount,
          config.runtimeKey
        )
      })

    runtimeOperationRef.current = queuedRender.catch(() => {})
    await queuedRender
  }

  async function show(configFactory) {
    currentConfigFactoryRef.current = configFactory
    const openedFromCollapsed = collapsedRef.current
    if (collapsedRef.current) {
      onVisibleChange?.(true)
      if (!inited) {
        setInited(true)
      }
      await waitForPropertyPanelLayoutFrame()
    }
    if (unmountedRef.current || currentConfigFactoryRef.current !== configFactory) {
      return
    }
    await renderCurrentConfig({ forceRemount: true })
    if (openedFromCollapsed) {
      await waitForPropertyPanelLayoutFrame()
      notifyPropertyPanelLayoutSettled()
    }
    if (unmountedRef.current || currentConfigFactoryRef.current !== configFactory) {
      return
    }
    if (!inited) {
      setInited(true)
    }
  }

  async function refresh() {
    await renderCurrentConfig()
  }

  function close() {
    closePropertyPanel({
      collapsed: {
        get value() {
          return collapsedRef.current
        },
        set value(next) {
          onVisibleChange?.(!next)
        },
      },
      toggleFn: toggle,
    })
  }

  useImperativeHandle(ref, () => ({
    show,
    refresh,
    close,
    toggle,
    async flushChanges() {
      if (readonly || collapsedRef.current) return
      const form = amisScopedRef.current?.getScopeRef?.()?.getComponentByName?.('propertyForm')
      await form?.flush?.()
    },
    async destroyRuntime() {
      currentConfigFactoryRef.current = null
      await destroyRuntime()
    },
    getAmisScoped() {
      return amisScopedRef.current
    },
    getFormContainerElement() {
      return formContainerRef.current
    },
  }))

  return (
    <EditorPanel
      side="right"
      visible={!collapsed}
      data-testid="logicflow-panel-inspector"
      style={collapsed ? undefined : { width: `${panelWidth}px` }}
      onClick={(event) => event.stopPropagation()}
      onContextMenu={(event) => event.stopPropagation()}
      header={<>
        <h3 className="panel-title" data-testid="logicflow-inspector-title">
          {t(panelTitle)}
        </h3>
        <button
          type="button"
          className="panel-close-button"
          data-testid="logicflow-right-panel-close"
          aria-label={t('隐藏属性面板')}
          disabled={!inited}
          onClick={() => {
            if (inited) {
              onVisibleChange?.(false)
            }
          }}
        >
          <i className="fa-solid fa-xmark"></i>
        </button>
      </>}
    >
      <div
        className={`form-container ${isFormScrolling ? 'is-scrolling' : ''}`}
        ref={formContainerRef}
      ></div>
      <div
        className={`scroll-indicator ${scrollIndicator.visible ? 'visible' : ''} ${
          isFormScrolling ? 'is-scrolling' : ''
        }`}
        aria-hidden="true"
      >
        <div
          className="scroll-indicator-thumb"
          style={{
            height: `${scrollIndicator.height}px`,
            transform: `translateY(${scrollIndicator.offset}px)`,
          }}
        ></div>
      </div>
      <div className="resize-handle" ref={resizeHandleRef}></div>
    </EditorPanel>
  )
})

export default PropertiesPanel
