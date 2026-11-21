import test from 'node:test'
import assert from 'node:assert/strict'

import {
  LOGICFLOW_TOOLBAR_COMMAND_IDS,
  LOGICFLOW_TOOLBAR_GROUPS,
  LOGICFLOW_TOOLBAR_PRESENTATIONS,
  createLogicFlowToolbarCommandEntryDescriptor,
  createLogicFlowToolbarSchemaEntryDescriptor,
  buildLogicFlowToolbarEntries,
  groupLogicFlowToolbarEntries,
} from '../src/logicFlowEditor/orchestration/editorToolbarSurface.mjs'

test('buildLogicFlowToolbarEntries creates meta and view entries from chrome visibility', () => {
  const entries = buildLogicFlowToolbarEntries({
    chromeVisibility: {
      palette: {
        available: true,
        visible: true,
      },
      inspector: {
        available: true,
        visible: false,
      },
    },
    canvasAssistState: {
      gridVisible: true,
      gridSnappingEnabled: false,
      nodeAlignmentEnabled: true,
    },
    canvasAssistAvailability: {
      visible: true,
      enabled: true,
    },
    flowEntry: createLogicFlowToolbarSchemaEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW,
      label: '流程属性',
      iconClassName: 'fa-solid fa-circle-info',
    }),
    commandEntries: [
      createLogicFlowToolbarCommandEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.DELETE_SELECTION,
        enabled: true,
        visible: true,
      }),
    ],
    validationEntry: { type: 'validation-indicator' },
  })

  assert.deepEqual(
    entries.map((entry) => entry.id),
    [
      'toggle-palette',
      'toggle-inspector',
      'toggle-grid-visibility',
      'toggle-node-alignment',
      'delete-selection',
      'edit-flow',
      'toggle-position-persistence',
      'auto-layout',
      'validation-indicator',
    ]
  )
  assert.equal(entries[0].group, LOGICFLOW_TOOLBAR_GROUPS.VIEW)
  assert.equal(entries[2].group, LOGICFLOW_TOOLBAR_GROUPS.CANVAS_ASSIST)
  assert.equal(entries[4].group, LOGICFLOW_TOOLBAR_GROUPS.SELECTION)
  assert.equal(entries[5].group, LOGICFLOW_TOOLBAR_GROUPS.META)
  assert.equal(entries[0].presentation, LOGICFLOW_TOOLBAR_PRESENTATIONS.ICON_ONLY_TOGGLE)
  assert.equal(entries[0].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_PALETTE)
  assert.equal(entries[0].payload.pressed, true)
  assert.equal(entries[1].payload.pressed, false)
  assert.equal(entries[2].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_GRID_VISIBILITY)
  assert.equal(entries[2].payload.iconClassName, 'fa-solid fa-border-all')
  assert.equal(entries[2].payload.pressed, false)
  assert.equal(entries[3].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_NODE_ALIGNMENT)
  assert.equal(entries[3].payload.iconClassName, 'fa-solid fa-magnet')
  assert.equal(entries[3].payload.pressed, true)
  assert.equal(entries[5].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW)
  assert.equal(entries[6].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_POSITION_PERSISTENCE)
  assert.equal(entries[6].presentation, LOGICFLOW_TOOLBAR_PRESENTATIONS.CHECKBOX_TEXT_TOGGLE)
  assert.equal(entries[6].payload.label, '保存布局')
  assert.equal(entries[6].payload.iconClassName, '')
  assert.equal(entries[6].payload.tooltip, '保存布局')
  assert.equal(entries[6].payload.pressed, false)
})

test('buildLogicFlowToolbarEntries keeps readonly palette toggle visible but disabled', () => {
  const entries = buildLogicFlowToolbarEntries({
    chromeVisibility: {
      palette: {
        available: false,
        visible: false,
      },
      inspector: {
        available: true,
        visible: true,
      },
    },
    canvasAssistState: {
      gridVisible: true,
      gridSnappingEnabled: true,
      nodeAlignmentEnabled: true,
    },
    canvasAssistAvailability: {
      visible: true,
      enabled: false,
    },
    positionPersistence: {
      visible: true,
      enabled: false,
      pressed: false,
    },
    flowEntry: createLogicFlowToolbarSchemaEntryDescriptor({
      commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW,
      label: '流程属性',
      iconClassName: 'fa-solid fa-circle-info',
    }),
  })

  assert.deepEqual(
    entries.map((entry) => entry.id),
    [
      'toggle-palette',
      'toggle-inspector',
      'toggle-grid-visibility',
      'toggle-node-alignment',
      'edit-flow',
      'toggle-position-persistence',
      'auto-layout',
    ]
  )
  assert.equal(entries[0].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_PALETTE)
  assert.equal(entries[0].payload.enabled, false)
  assert.equal(entries[1].payload.pressed, true)
  assert.equal(entries[2].payload.enabled, false)
  assert.equal(entries[3].payload.enabled, false)
  assert.equal(entries[5].payload.commandId, LOGICFLOW_TOOLBAR_COMMAND_IDS.TOGGLE_POSITION_PERSISTENCE)
  assert.equal(entries[5].payload.enabled, false)
})

test('groupLogicFlowToolbarEntries returns visible groups in stable order', () => {
  const groups = groupLogicFlowToolbarEntries(
    buildLogicFlowToolbarEntries({
      chromeVisibility: {
        palette: {
          available: true,
          visible: false,
        },
        inspector: {
          available: true,
          visible: false,
        },
      },
      canvasAssistState: {
        gridVisible: true,
        gridSnappingEnabled: true,
        nodeAlignmentEnabled: true,
      },
      canvasAssistAvailability: {
        visible: true,
        enabled: true,
      },
      flowEntry: createLogicFlowToolbarSchemaEntryDescriptor({
        commandId: LOGICFLOW_TOOLBAR_COMMAND_IDS.EDIT_FLOW,
        label: '流程属性',
        iconClassName: 'fa-solid fa-circle-info',
      }),
      positionPersistence: {
        visible: true,
        enabled: true,
        pressed: true,
      },
      validationEntry: { type: 'validation-indicator' },
    })
  )

  assert.deepEqual(
    groups.map((group) => ({
      id: group.id,
      entries: group.entries.map((entry) => entry.id),
    })),
    [
      {
        id: LOGICFLOW_TOOLBAR_GROUPS.VIEW,
        entries: ['toggle-palette', 'toggle-inspector'],
      },
      {
        id: LOGICFLOW_TOOLBAR_GROUPS.CANVAS_ASSIST,
        entries: ['toggle-grid-visibility', 'toggle-node-alignment'],
      },
      {
        id: LOGICFLOW_TOOLBAR_GROUPS.META,
        entries: ['edit-flow', 'toggle-position-persistence', 'auto-layout', 'validation-indicator'],
      },
    ]
  )
  assert.equal(
    groups[2].entries.find((entry) => entry.id === 'toggle-position-persistence')?.payload
      ?.pressed,
    true
  )
})
