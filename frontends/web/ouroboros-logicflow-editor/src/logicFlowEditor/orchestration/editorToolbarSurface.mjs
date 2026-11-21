export const LOGICFLOW_TOOLBAR_GROUPS = Object.freeze({
  VIEW: 'view',
  CANVAS_ASSIST: 'canvas-assist',
  SELECTION: 'selection',
  META: 'meta',
})

export const LOGICFLOW_TOOLBAR_GROUP_ORDER = Object.freeze([
  LOGICFLOW_TOOLBAR_GROUPS.VIEW,
  LOGICFLOW_TOOLBAR_GROUPS.CANVAS_ASSIST,
  LOGICFLOW_TOOLBAR_GROUPS.SELECTION,
  LOGICFLOW_TOOLBAR_GROUPS.META,
])

export const LOGICFLOW_TOOLBAR_ENTRY_KINDS = Object.freeze({
  SCHEMA_ENTRY: 'schema-entry',
  COMMAND_BUTTON: 'command-button',
  STATUS_ENTRY: 'status-entry',
})

export const LOGICFLOW_TOOLBAR_PRESENTATIONS = Object.freeze({
  ICON_TEXT: 'icon-text',
  TEXT_ONLY: 'text-only',
  ICON_ONLY_COMMAND: 'icon-only-command',
  ICON_ONLY_TOGGLE: 'icon-only-toggle',
  CHECKBOX_TEXT_TOGGLE: 'checkbox-text-toggle',
  STATUS_TRIGGER: 'status-trigger',
})

export const LOGICFLOW_TOOLBAR_COMMAND_IDS = Object.freeze({
  TOGGLE_PALETTE: 'toggle-palette',
  TOGGLE_INSPECTOR: 'toggle-inspector',
  TOGGLE_GRID_VISIBILITY: 'toggle-grid-visibility',
  TOGGLE_NODE_ALIGNMENT: 'toggle-node-alignment',
  TOGGLE_POSITION_PERSISTENCE: 'toggle-position-persistence',
  EDIT_FLOW: 'edit-flow',
  DELETE_SELECTION: 'delete-selection',
  DELETE_INPUT_PORT: 'delete-input-port',
  ADD_INPUT_PORT: 'add-input-port',
  AUTO_LAYOUT: 'auto-layout',
  ONLINE_DEBUG: 'online-debug',
  VALIDATION_INDICATOR: 'validation-indicator',
})

export const LOGICFLOW_TOOLBAR_ENTRY_ORDER = Object.freeze({
  TOGGLE_PALETTE: 10,
  TOGGLE_INSPECTOR: 11,
  TOGGLE_GRID_VISIBILITY: 20,
  TOGGLE_NODE_ALIGNMENT: 22,
  EDIT_FLOW: 20,
  TOGGLE_POSITION_PERSISTENCE: 30,
  DELETE_SELECTION: 30,
  DELETE_INPUT_PORT: 31,
  ADD_INPUT_PORT: 32,
  AUTO_LAYOUT: 40,
  VALIDATION_INDICATOR: 90,
})

const LOGICFLOW_TOOLBAR_COMMAND_ENTRY_META = Object.freeze({
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.ONLINE_DEBUG]: {
    id: 'online-debug',
    group: LOGICFLOW_TOOLBAR_GROUPS.META,
    order: 50,
    label: '在线调试',
    iconClassName: 'fa-solid fa-bug',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_TEXT,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_SELECTION]: {
    id: 'delete-selection',
    group: LOGICFLOW_TOOLBAR_GROUPS.SELECTION,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.DELETE_SELECTION,
    label: '删除',
    iconClassName: 'fa-solid fa-trash',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_COMMAND,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_INPUT_PORT]: {
    id: 'delete-input-port',
    group: LOGICFLOW_TOOLBAR_GROUPS.SELECTION,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.DELETE_INPUT_PORT,
    label: '删除输入端口',
    iconClassName: 'fa-solid fa-minus',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_COMMAND,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.ADD_INPUT_PORT]: {
    id: 'add-input-port',
    group: LOGICFLOW_TOOLBAR_GROUPS.SELECTION,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.ADD_INPUT_PORT,
    label: '添加输入端口',
    iconClassName: 'fa-solid fa-plus',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_COMMAND,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.AUTO_LAYOUT]: {
    id: 'auto-layout',
    group: LOGICFLOW_TOOLBAR_GROUPS.META,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.AUTO_LAYOUT,
    label: '自动布局',
    iconClassName: 'fa-solid fa-expand',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_COMMAND,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_POSITION_PERSISTENCE]: {
    id: 'toggle-position-persistence',
    group: LOGICFLOW_TOOLBAR_GROUPS.META,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.TOGGLE_POSITION_PERSISTENCE,
    label: '保存布局',
    tooltip: '保存布局',
    iconClassName: '',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.CHECKBOX_TEXT_TOGGLE,
  },
})

const LOGICFLOW_TOOLBAR_SCHEMA_ENTRY_META = Object.freeze({
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW]: {
    id: 'edit-flow',
    group: LOGICFLOW_TOOLBAR_GROUPS.META,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.EDIT_FLOW,
  },
})

const LOGICFLOW_TOOLBAR_VIEW_ENTRY_META = Object.freeze({
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_PALETTE]: {
    id: 'toggle-palette',
    group: LOGICFLOW_TOOLBAR_GROUPS.VIEW,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.TOGGLE_PALETTE,
    label: '节点面板',
    iconClassName: 'fa-solid fa-table-columns',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_INSPECTOR]: {
    id: 'toggle-inspector',
    group: LOGICFLOW_TOOLBAR_GROUPS.VIEW,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.TOGGLE_INSPECTOR,
    label: '属性面板',
    iconClassName: 'fa-solid fa-sliders',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE,
  },
})

const LOGICFLOW_TOOLBAR_CANVAS_ASSIST_ENTRY_META = Object.freeze({
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_GRID_VISIBILITY]: {
    id: 'toggle-grid-visibility',
    group: LOGICFLOW_TOOLBAR_GROUPS.CANVAS_ASSIST,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.TOGGLE_GRID_VISIBILITY,
    label: '网格',
    iconClassName: 'fa-solid fa-border-all',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE,
  },
  [LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_NODE_ALIGNMENT]: {
    id: 'toggle-node-alignment',
    group: LOGICFLOW_TOOLBAR_GROUPS.CANVAS_ASSIST,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.TOGGLE_NODE_ALIGNMENT,
    label: '节点对齐',
    iconClassName: 'fa-solid fa-magnet',
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE,
  },
})

function groupRank(group) {
  const rank = LOGICFLOW_TOOLBAR_GROUP_ORDER.indexOf(group)
  return rank === -1 ? Number.MAX_SAFE_INTEGER : rank
}

function entrySort(leftEntry, rightEntry) {
  const groupOrderDelta = groupRank(leftEntry.group) - groupRank(rightEntry.group)
  if (groupOrderDelta !== 0) {
    return groupOrderDelta
  }

  return (leftEntry.order || 0) - (rightEntry.order || 0)
}

export function createLogicFlowToolbarCommandEntryDescriptor({
  commandId,
  enabled = true,
  visible = true,
  tooltip = null,
  disabledReason = '',
  pressed = false,
} = {}) {
  const commandMeta = LOGICFLOW_TOOLBAR_COMMAND_ENTRY_META[commandId]
  if (!commandMeta || !visible) {
    return null
  }

  return {
    id: commandMeta.id,
    group: commandMeta.group,
    kind: LOGICFLOW_TOOLBAR_ENTRY_KINDS.COMMAND_BUTTON,
    presentation: commandMeta.presentation,
    order: commandMeta.order,
    visible: true,
    payload: {
      commandId,
      label: commandMeta.label,
      iconClassName: commandMeta.iconClassName,
      enabled: Boolean(enabled),
      tooltip: tooltip ?? commandMeta.tooltip ?? '',
      disabledReason,
      pressed: Boolean(pressed),
    },
  }
}

export function createLogicFlowToolbarSchemaEntryDescriptor({
  commandId,
  label = '',
  iconClassName = '',
  visible = true,
  pressed = false,
  enabled = true,
} = {}) {
  const entryMeta = LOGICFLOW_TOOLBAR_SCHEMA_ENTRY_META[commandId]
  if (!entryMeta || !visible) {
    return null
  }

  return {
    id: entryMeta.id,
    group: entryMeta.group,
    kind: LOGICFLOW_TOOLBAR_ENTRY_KINDS.SCHEMA_ENTRY,
    presentation: iconClassName
      ? LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_TEXT
      : LOGICFLOW_TOOLBAR_PRESENTATIONS.TEXT_ONLY,
    order: entryMeta.order,
    visible: true,
    payload: {
      commandId,
      label,
      iconClassName,
      enabled: Boolean(enabled),
      pressed: Boolean(pressed),
    },
  }
}

export function createLogicFlowToolbarViewToggleEntryDescriptor({
  commandId,
  available,
  enabled = true,
  pressed,
} = {}) {
  const commandMeta = LOGICFLOW_TOOLBAR_VIEW_ENTRY_META[commandId]
  if (!commandMeta || !available) {
    return null
  }

  return {
    id: commandMeta.id,
    group: commandMeta.group,
    kind: LOGICFLOW_TOOLBAR_ENTRY_KINDS.COMMAND_BUTTON,
    presentation: commandMeta.presentation,
    order: commandMeta.order,
    visible: true,
    payload: {
      commandId,
      label: commandMeta.label,
      iconClassName: commandMeta.iconClassName,
      enabled: Boolean(enabled),
      pressed: Boolean(pressed),
    },
  }
}

export function createLogicFlowToolbarCanvasAssistToggleEntryDescriptor({
  commandId,
  available = true,
  enabled = true,
  pressed,
} = {}) {
  const commandMeta = LOGICFLOW_TOOLBAR_CANVAS_ASSIST_ENTRY_META[commandId]
  if (!commandMeta || !available) {
    return null
  }

  return {
    id: commandMeta.id,
    group: commandMeta.group,
    kind: LOGICFLOW_TOOLBAR_ENTRY_KINDS.COMMAND_BUTTON,
    presentation: commandMeta.presentation,
    order: commandMeta.order,
    visible: true,
    payload: {
      commandId,
      label: commandMeta.label,
      iconClassName: commandMeta.iconClassName,
      enabled: Boolean(enabled),
      pressed: Boolean(pressed),
    },
  }
}

export function createLogicFlowToolbarStatusEntryDescriptor(element) {
  if (!element) {
    return null
  }

  return {
    id: 'validation-indicator',
    group: LOGICFLOW_TOOLBAR_GROUPS.META,
    kind: LOGICFLOW_TOOLBAR_ENTRY_KINDS.STATUS_ENTRY,
    presentation: LOGICFLOW_TOOLBAR_PRESENTATIONS.STATUS_TRIGGER,
    order: LOGICFLOW_TOOLBAR_ENTRY_ORDER.VALIDATION_INDICATOR,
    visible: true,
    payload: {
      element,
    },
  }
}

export function buildLogicFlowToolbarEntries({
  chromeVisibility = null,
  canvasAssistState = null,
  canvasAssistAvailability = null,
  positionPersistence = null,
  flowEntry = null,
  commandEntries = [],
  validationEntry = null,
} = {}) {
  return [
    createLogicFlowToolbarViewToggleEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_PALETTE,
      available: true,
      enabled: chromeVisibility?.palette?.available,
      pressed: chromeVisibility?.palette?.visible,
    }),
    createLogicFlowToolbarViewToggleEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_INSPECTOR,
      available: chromeVisibility?.inspector?.available,
      pressed: chromeVisibility?.inspector?.visible,
    }),
    createLogicFlowToolbarCanvasAssistToggleEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_GRID_VISIBILITY,
      available: canvasAssistAvailability?.visible !== false,
      enabled: canvasAssistAvailability?.enabled,
      pressed: Boolean(canvasAssistState?.gridVisible && canvasAssistState?.gridSnappingEnabled),
    }),
    createLogicFlowToolbarCanvasAssistToggleEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_NODE_ALIGNMENT,
      available: canvasAssistAvailability?.visible !== false,
      enabled: canvasAssistAvailability?.enabled,
      pressed: canvasAssistState?.nodeAlignmentEnabled,
    }),
    flowEntry,
    createLogicFlowToolbarCommandEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_POSITION_PERSISTENCE,
      visible: positionPersistence?.visible !== false,
      enabled: positionPersistence?.enabled !== false,
      pressed: positionPersistence?.pressed === true,
    }),
    ...commandEntries,
    createLogicFlowToolbarCommandEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.AUTO_LAYOUT,
    }),
    createLogicFlowToolbarStatusEntryDescriptor(validationEntry),
  ]
    .filter(Boolean)
    .sort(entrySort)
}

export function groupLogicFlowToolbarEntries(entries = []) {
  const groupedEntries = entries
    .filter((entry) => entry?.visible)
    .sort(entrySort)
    .reduce((result, entry) => {
      if (!result[entry.group]) {
        result[entry.group] = []
      }
      result[entry.group].push(entry)
      return result
    }, {})

  return LOGICFLOW_TOOLBAR_GROUP_ORDER.map((groupId) => {
    const groupEntries = groupedEntries[groupId] || []
    if (groupEntries.length === 0) {
      return null
    }

    return {
      id: groupId,
      entries: groupEntries,
    }
  }).filter(Boolean)
}
