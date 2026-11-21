import { t } from '../../../i18n.js'
import React from 'react'

import {
  LOGICFLOW_TOOLBAR_COMMAND_IDS,
  LOGICFLOW_TOOLBAR_ENTRY_KINDS,
  LOGICFLOW_TOOLBAR_PRESENTATIONS,
  buildLogicFlowToolbarEntries,
  groupLogicFlowToolbarEntries,
} from '../../orchestration/editorToolbarSurface.mjs'

function renderToolbarEntry(entry, onExecuteCommand) {
  if (!entry?.visible) {
    return null
  }

  if (entry.kind === LOGICFLOW_TOOLBAR_ENTRY_KINDS.STATUS_ENTRY) {
    return (
      <div
        key={entry.id}
        className="toolbar-status-entry"
        data-command-id={entry.id}
        data-command-presentation={entry.presentation}
      >
        {entry.payload?.element || null}
      </div>
    )
  }

  if (entry.kind === LOGICFLOW_TOOLBAR_ENTRY_KINDS.SCHEMA_ENTRY) {
    const { commandId, label, iconClassName, enabled, pressed } = entry.payload || {}
    const tooltipText = label

    return (
      <button
        key={entry.id}
        type="button"
        className={`toolbar-schema-entry${pressed ? ' toolbar-schema-entry-pressed' : ''}`}
        disabled={!enabled}
        title={t(tooltipText)}
        aria-label={t(tooltipText)}
        aria-pressed={pressed ? 'true' : 'false'}
        data-command-id={commandId}
        data-command-presentation={entry.presentation}
        data-command-pressed={pressed ? 'true' : 'false'}
        data-logicflow-tooltip={t(tooltipText || undefined)}
        onClick={() => {
          void onExecuteCommand?.(commandId)
        }}>
        {iconClassName ? (
          <i
            className={`${iconClassName} toolbar-schema-entry-icon`}
            aria-hidden="true"></i>
        ) : null}
        <span className="toolbar-schema-entry-label">{t(label)}</span>
      </button>
    )
  }

  if (entry.kind !== LOGICFLOW_TOOLBAR_ENTRY_KINDS.COMMAND_BUTTON) {
    return null
  }

  const { commandId, label, iconClassName, enabled, pressed, tooltip, disabledReason } = entry.payload || {}
  const isCheckboxToggle =
    entry.presentation === LOGICFLOW_TOOLBAR_PRESENTATIONS.CHECKBOX_TEXT_TOGGLE
  const isToggle = entry.presentation === LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE
  const tooltipText = tooltip || disabledReason || label

  if (isCheckboxToggle) {
    return (
      <button
        key={entry.id}
        type="button"
        role="checkbox"
        className={`toolbar-checkbox-entry${pressed ? ' toolbar-checkbox-entry-pressed' : ''}`}
        disabled={!enabled}
        title={t(tooltipText)}
        aria-label={t(tooltipText)}
        aria-checked={Boolean(pressed)}
        data-command-id={commandId}
        data-command-presentation={entry.presentation}
        data-command-pressed={pressed ? 'true' : 'false'}
        data-logicflow-tooltip={t(tooltipText || undefined)}
        onClick={() => {
          void onExecuteCommand?.(commandId)
        }}
      >
        <span className="toolbar-checkbox-entry-box" aria-hidden="true"></span>
        <span className="toolbar-checkbox-entry-label">{t(label)}</span>
      </button>
    )
  }

  return (
    <button
      key={entry.id}
      type="button"
      className={`icon-box toolbar-command-button${isToggle && pressed ? ' toolbar-command-button-pressed' : ''}`}
      disabled={!enabled}
      title={t(tooltipText)}
      aria-label={t(tooltipText)}
      aria-pressed={isToggle ? Boolean(pressed) : undefined}
      data-command-id={commandId}
      data-command-presentation={entry.presentation}
      data-command-pressed={isToggle ? (pressed ? 'true' : 'false') : undefined}
      data-logicflow-tooltip={t(tooltipText || undefined)}
      onClick={() => {
        void onExecuteCommand?.(commandId)
      }}
    >
      <i className={`${iconClassName} icon`}></i>
    </button>
  )
}

export default function Toolbar({
  onLayout,
  entries = null,
  onExecuteCommand,
  dockingContext,
  children,
}) {
  const resolvedEntries =
    entries || buildLogicFlowToolbarEntries()
  const visibleGroups = groupLogicFlowToolbarEntries(resolvedEntries)

  return (
    <div
      className="toolbar"
      data-testid="logicflow-editor-toolbar"
      data-follows-palette={dockingContext?.followsPalette ? 'true' : 'false'}
      data-palette-visible={dockingContext?.paletteVisible ? 'true' : 'false'}
      data-toolbar-left-inset={dockingContext?.leftInset ?? ''}
      data-toolbar-top-inset={dockingContext?.topInset ?? ''}
    >
      {children || visibleGroups.map((group) => (
        <div key={group.id} className="toolbar-command-group" data-command-group={group.id}>
          {group.entries
            .map((entry) =>
              renderToolbarEntry(entry, (commandId) => {
                if (commandId === LOGICFLOW_TOOLBAR_COMMAND_IDS.AUTO_LAYOUT) {
                  onLayout?.()
                  return
                }
                onExecuteCommand?.(commandId)
              })
            )
            .filter(Boolean)}
        </div>
      ))}
    </div>
  )
}
