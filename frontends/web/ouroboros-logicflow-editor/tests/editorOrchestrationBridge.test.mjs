import test from 'node:test'
import assert from 'node:assert/strict'

import {
  createEditorPanelBridge,
  createEditorPropertyAccessors,
} from '../src/logicFlowEditor/orchestration/editorBridgeHelpers.mjs'

test('createEditorPropertyAccessors pulls fresh editor props for default property builders and transpileAll', () => {
  const seenProps = []
  let callIndex = 0
  const accessors = createEditorPropertyAccessors({
    getProps() {
      callIndex++
      return { token: 'props-' + callIndex }
    },
    nodeDefaultPropertiesFn(editorProps) {
      seenProps.push(['node', editorProps])
      return ['node-defaults', editorProps.token]
    },
    edgeDefaultPropertiesFn(editorProps) {
      seenProps.push(['edge', editorProps])
      return ['edge-defaults', editorProps.token]
    },
    flowDefaultPropertiesFn(editorProps) {
      seenProps.push(['flow', editorProps])
      return ['flow-defaults', editorProps.token]
    },
    transpileAllRawFn(properties, editorProps) {
      seenProps.push(['transpile', properties, editorProps])
      return { properties, token: editorProps.token }
    },
  })

  assert.deepEqual(accessors.getNodeDefaultProperties(), ['node-defaults', 'props-1'])
  assert.deepEqual(accessors.getEdgeDefaultProperties(), ['edge-defaults', 'props-2'])
  assert.deepEqual(accessors.getFlowDefaultProperties(), ['flow-defaults', 'props-3'])
  assert.deepEqual(accessors.transpileAll([{ type: 'script' }]), {
    properties: [{ type: 'script' }],
    token: 'props-4',
  })

  assert.deepEqual(seenProps, [
    ['node', { token: 'props-1' }],
    ['edge', { token: 'props-2' }],
    ['flow', { token: 'props-3' }],
    ['transpile', [{ type: 'script' }], { token: 'props-4' }],
  ])
})

test('createEditorPanelBridge forwards show config factory and toggle calls without validation toast handling', () => {
  const calls = []
  const bridge = createEditorPanelBridge({
    propertiesPanelRef: {
      value: {
        show(configFactory) {
          calls.push(['show', configFactory()])
        },
        toggle() {
          calls.push(['toggle'])
        },
      },
    },
    toastFn(message, type) {
      calls.push(['toast', message, type])
    },
  })

  const saveAction = () => {}
  bridge.showPropertiesPanel(() => ({
    formItems: ['field'],
    data: { name: '节点A' },
    saveAction,
  }))
  bridge.togglePropertiesPanel()

  assert.deepEqual(calls, [
    [
      'show',
      {
        formItems: ['field'],
        data: { name: '节点A' },
        saveAction,
      },
    ],
    ['toggle'],
  ])
})

test('createEditorPanelBridge preserves async property-panel show lifecycle', async () => {
  const calls = []
  let releaseShow = null
  const bridge = createEditorPanelBridge({
    propertiesPanelRef: {
      value: {
        show(configFactory) {
          calls.push(['show', configFactory()])
          return new Promise((resolve) => {
            releaseShow = () => {
              calls.push(['show:resolved'])
              resolve('panel-ready')
            }
          })
        },
      },
    },
    toastFn() {},
  })

  const showPromise = bridge.showPropertiesPanel(() => ({
    formItems: ['field'],
    data: { name: '节点A' },
    saveAction: 'save-action',
  }))
  calls.push(['after-show-call'])

  assert.equal(typeof showPromise?.then, 'function')
  assert.deepEqual(calls, [
    [
      'show',
      {
        formItems: ['field'],
        data: { name: '节点A' },
        saveAction: 'save-action',
      },
    ],
    ['after-show-call'],
  ])

  releaseShow()
  assert.equal(await showPromise, 'panel-ready')
  assert.deepEqual(calls, [
    [
      'show',
      {
        formItems: ['field'],
        data: { name: '节点A' },
        saveAction: 'save-action',
      },
    ],
    ['after-show-call'],
    ['show:resolved'],
  ])
})
