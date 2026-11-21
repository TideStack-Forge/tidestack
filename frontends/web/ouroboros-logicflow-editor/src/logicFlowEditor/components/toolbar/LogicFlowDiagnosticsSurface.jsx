import { t } from '../../../i18n.js'
import React, { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'

const DIAGNOSTIC_SEVERITY_LABEL = Object.freeze({
  error: '错误',
  warning: '警告',
})

export default function LogicFlowDiagnosticsSurface({
  diagnosticsContract,
  onNavigate,
}) {
  const containerRef = useRef(null)
  const popoverRef = useRef(null)
  const [isOpen, setIsOpen] = useState(false)
  const [popoverMaxHeight, setPopoverMaxHeight] = useState(null)

  const status = diagnosticsContract?.status || 'clean'
  const trigger = diagnosticsContract?.trigger || {}
  const popover = diagnosticsContract?.popover || {}
  const diagnosticItems = Array.isArray(popover.items) ? popover.items : []

  const closeDiagnosticsLayer = useCallback(() => {
    setIsOpen(false)
  }, [])

  const syncPopoverMaxHeight = useCallback(() => {
    const popoverElement = popoverRef.current
    if (!popoverElement) {
      setPopoverMaxHeight(null)
      return
    }

    const editorElement = popoverElement.closest('.wrapper-editor-container')
    const boundaryRect = editorElement?.getBoundingClientRect?.()
    const popoverRect = popoverElement.getBoundingClientRect()
    const boundaryBottom =
      typeof boundaryRect?.bottom === 'number' ? boundaryRect.bottom : window.innerHeight
    const availableHeight = Math.floor(boundaryBottom - popoverRect.top - 16)

    setPopoverMaxHeight(Math.max(0, Math.min(420, availableHeight)))
  }, [])

  useEffect(() => {
    if (!isOpen) {
      return undefined
    }

    const handlePointerDown = (event) => {
      if (containerRef.current?.contains(event.target)) {
        return
      }
      closeDiagnosticsLayer()
    }

    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        closeDiagnosticsLayer()
      }
    }

    document.addEventListener('pointerdown', handlePointerDown)
    document.addEventListener('keydown', handleKeyDown)

    return () => {
      document.removeEventListener('pointerdown', handlePointerDown)
      document.removeEventListener('keydown', handleKeyDown)
    }
  }, [closeDiagnosticsLayer, isOpen])

  useLayoutEffect(() => {
    if (!isOpen) {
      setPopoverMaxHeight(null)
      return undefined
    }

    syncPopoverMaxHeight()
    window.addEventListener('resize', syncPopoverMaxHeight)
    const editorElement = popoverRef.current?.closest('.wrapper-editor-container')
    const resizeObserver =
      typeof ResizeObserver === 'function' && editorElement
        ? new ResizeObserver(syncPopoverMaxHeight)
        : null
    resizeObserver?.observe(editorElement)

    return () => {
      window.removeEventListener('resize', syncPopoverMaxHeight)
      resizeObserver?.disconnect()
    }
  }, [isOpen, syncPopoverMaxHeight])

  const handleItemNavigate = useCallback(
    async (item) => {
      const didNavigate = await onNavigate?.(item)
      if (didNavigate !== false) {
        closeDiagnosticsLayer()
      }
    },
    [closeDiagnosticsLayer, onNavigate]
  )

  const tooltipText = `${t(popover.summary)}${diagnosticItems.length > 0 ? ` (${t('共 {{p0}} 项，阻塞项 {{p1}} 个', { p0: diagnosticItems.length, p1: popover.blockingCount || 0 })})` : ''}`

  return (
    <div
      ref={containerRef}
      className={`logicflow-editor-diagnostics-surface logicflow-editor-diagnostics-${status}`}
      data-testid="logicflow-editor-diagnostics-surface"
    >
      <button
        type="button"
        className={`logicflow-editor-diagnostics-trigger${isOpen ? ' is-open' : ''}`}
        title={tooltipText}
        aria-label={
          tooltipText || t('流程诊断：{{status}}', { status: t(trigger.label || '') })
        }
        aria-expanded={isOpen}
        aria-haspopup="dialog"
        data-testid="logicflow-editor-diagnostics-trigger"
        data-diagnostics-status={status}
        onClick={() => {
          setIsOpen((currentOpen) => !currentOpen)
        }}
      >
        <span className="logicflow-editor-diagnostics-trigger-icon" aria-hidden="true">
          <i className={trigger.iconClassName || 'fa-solid fa-check'}></i>
        </span>
        <span className="logicflow-editor-diagnostics-trigger-label">
          {t(trigger.label)}
        </span>
        {trigger.count > 0 ? (
          <span className="logicflow-editor-diagnostics-trigger-count" aria-hidden="true">
            {trigger.count}
          </span>
        ) : null}
      </button>
      {isOpen ? (
        <div
          ref={popoverRef}
          className="logicflow-editor-diagnostics-popover"
          data-testid="logicflow-editor-diagnostics-popover"
          role="dialog"
          aria-label={t('流程诊断')}
          style={
            popoverMaxHeight != null
              ? { '--logicflow-diagnostics-popover-max-height': `${popoverMaxHeight}px` }
              : undefined
          }>
          <div
            className="logicflow-editor-diagnostics-summary"
            data-testid="logicflow-editor-diagnostics-summary">
            <span className="logicflow-editor-diagnostics-badge">{t(trigger.label)}</span>
            <div className="logicflow-editor-diagnostics-summary-text">
              <strong>{t(popover.summary)}</strong>
              {diagnosticItems.length > 0 ? (
                <span>
                  {t('共 {{p0}} 项，阻塞项 {{p1}} 个', {
                    p0: diagnosticItems.length,
                    p1: popover.blockingCount || 0,
                  })}
                </span>
              ) : (
                <span>{t('允许继续编辑，最终准入以后端校验为准。')}</span>
              )}
            </div>
          </div>
          {diagnosticItems.length > 0 ? (
            <ul className="logicflow-editor-diagnostics-list">
              {diagnosticItems.map((item) => {
                const message = t(item.messageKey || item.message, item.messageParams)
                const itemContent = (
                  <>
                    <span className="logicflow-editor-diagnostics-item-meta">
                      {t(
                        DIAGNOSTIC_SEVERITY_LABEL[item.severity] ||
                          DIAGNOSTIC_SEVERITY_LABEL.warning
                      )}
                    </span>
                    <span className="logicflow-editor-diagnostics-item-message">
                      {message}
                    </span>
                  </>
                )

                return (
                  <li key={item.id}>
                    {item.nodeName ? (
                      <button
                        type="button"
                        className={`logicflow-editor-diagnostics-item logicflow-editor-diagnostics-item-${item.severity}`}
                        title={message}
                        onClick={() => {
                          void handleItemNavigate(item)
                        }}
                      >
                        {itemContent}
                      </button>
                    ) : (
                      <div
                        className={`logicflow-editor-diagnostics-item logicflow-editor-diagnostics-item-${item.severity}`}
                        title={message}>
                        {itemContent}
                      </div>
                    )}
                  </li>
                )
              })}
            </ul>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
