import test from 'node:test'
import assert from 'node:assert/strict'

import {
  dispatchValidationNavigationDomSequence,
  findValidationNavigationNodeElement,
  performValidationNavigation,
  resolveValidationNavigationInvoker,
} from '../src/logicFlowEditor/orchestration/validationNavigationRuntime.mjs'

function createTextNode(textContent) {
  return { textContent }
}

function createNodeElement({
  texts = [],
  textContent = '',
  immovable = false,
  dispatchSink = [],
} = {}) {
  return {
    textContent,
    classList: {
      contains(className) {
        return immovable && className === 'x6-node-immovable'
      },
    },
    querySelectorAll(selector) {
      if (selector === 'text') {
        return texts.map((text) => createTextNode(text))
      }
      return []
    },
    getBoundingClientRect() {
      return { left: 10, top: 20, width: 30, height: 40 }
    },
    dispatchEvent(event) {
      dispatchSink.push(event.type)
      return true
    },
  }
}

function createRootElement(nodeElements) {
  return {
    querySelectorAll(selector) {
      if (selector === '.x6-node') {
        return nodeElements
      }
      return []
    },
  }
}

test('resolveValidationNavigationInvoker resolves mounted editor root method', () => {
  const calls = []
  const editorRoot = {
    navigateToValidationNode(nodeName) {
      calls.push(nodeName)
    },
  }

  const invoker = resolveValidationNavigationInvoker({ editorRoot })

  assert.ok(invoker)
  invoker.fn.call(invoker.thisArg, 'caller')
  assert.deepEqual(calls, ['caller'])
})

test('resolveValidationNavigationInvoker returns null when editor root is missing', () => {
  const invoker = resolveValidationNavigationInvoker({})
  assert.equal(invoker, null)
})

test('findValidationNavigationNodeElement prefers main-canvas nodes with exact text matches', () => {
  const miniMapNode = createNodeElement({
    texts: ['caller'],
    textContent: 'callerinputoutput',
    immovable: true,
  })
  const mainCanvasNode = createNodeElement({
    texts: ['caller'],
    textContent: 'callerinputoutput',
  })

  const matched = findValidationNavigationNodeElement({
    rootElement: createRootElement([miniMapNode, mainCanvasNode]),
    nodeName: 'caller',
  })

  assert.equal(matched, mainCanvasNode)
})

test('dispatchValidationNavigationDomSequence emits the pointer and mouse click sequence', () => {
  const dispatchSink = []
  const nodeElement = createNodeElement({
    texts: ['caller'],
    textContent: 'callerinputoutput',
    dispatchSink,
  })

  const dispatched = dispatchValidationNavigationDomSequence({
    rootElement: createRootElement([nodeElement]),
    nodeName: 'caller',
    mouseEventClass: class MouseEventMock {
      constructor(type) {
        this.type = type
      }
    },
    pointerEventClass: class PointerEventMock {
      constructor(type) {
        this.type = type
      }
    },
    view: {},
  })

  assert.equal(dispatched, true)
  assert.deepEqual(dispatchSink, [
    'pointerdown',
    'mousedown',
    'pointerup',
    'mouseup',
    'click',
  ])
})

test('dispatchValidationNavigationDomSequence returns false when the node is missing', () => {
  const dispatched = dispatchValidationNavigationDomSequence({
    rootElement: createRootElement([]),
    nodeName: 'caller',
    mouseEventClass: class MouseEventMock {
      constructor(type) {
        this.type = type
      }
    },
    pointerEventClass: class PointerEventMock {
      constructor(type) {
        this.type = type
      }
    },
    view: {},
  })

  assert.equal(dispatched, false)
})

test('performValidationNavigation falls back to the DOM sequence when the editor invoker reports failure', async () => {
  const dispatchSink = []
  const nodeElement = createNodeElement({
    texts: ['caller'],
    textContent: 'callerinputoutput',
    dispatchSink,
  })
  const editorRoot = {
    async navigateToValidationNode() {
      return {
        ok: false,
        reason: 'node_not_found',
      }
    },
  }

  const result = await performValidationNavigation({
    editorRoot,
    rootElement: createRootElement([nodeElement]),
    nodeName: 'caller',
    mouseEventClass: class MouseEventMock {
      constructor(type) {
        this.type = type
      }
    },
    pointerEventClass: class PointerEventMock {
      constructor(type) {
        this.type = type
      }
    },
    view: {},
  })

  assert.deepEqual(result, {
    ok: true,
    strategy: 'dom-fallback',
    result: true,
  })
  assert.deepEqual(dispatchSink, [
    'pointerdown',
    'mousedown',
    'pointerup',
    'mouseup',
    'click',
  ])
})

test('performValidationNavigation preserves successful editor invoker result without DOM fallback', async () => {
  const dispatchSink = []
  const nodeElement = createNodeElement({
    texts: ['caller'],
    textContent: 'callerinputoutput',
    dispatchSink,
  })
  const editorRoot = {
    async navigateToValidationNode(nodeName) {
      return {
        ok: true,
        nodeName,
      }
    },
  }

  const result = await performValidationNavigation({
    editorRoot,
    rootElement: createRootElement([nodeElement]),
    nodeName: 'caller',
    mouseEventClass: class MouseEventMock {
      constructor(type) {
        this.type = type
      }
    },
    pointerEventClass: class PointerEventMock {
      constructor(type) {
        this.type = type
      }
    },
    view: {},
  })

  assert.deepEqual(result, {
    ok: true,
    strategy: 'editor-invoker',
    result: {
      ok: true,
      nodeName: 'caller',
    },
  })
  assert.deepEqual(dispatchSink, [])
})
