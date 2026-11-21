function trimText(value) {
  return String(value || '').trim()
}

function readNodeTexts(nodeElement) {
  const textElements = Array.from(nodeElement?.querySelectorAll?.('text') || [])
  return textElements
    .map((textElement) => trimText(textElement.textContent))
    .filter(Boolean)
}

function matchesNodeName(nodeElement, nodeName) {
  const expectedName = trimText(nodeName)
  if (!expectedName) return false

  const exactTexts = readNodeTexts(nodeElement)
  if (exactTexts.some((text) => text === expectedName)) {
    return true
  }

  return trimText(nodeElement?.textContent).includes(expectedName)
}

function preferMainCanvasNodes(nodeElements = []) {
  const mainCanvasNodes = nodeElements.filter(
    (nodeElement) => !nodeElement?.classList?.contains?.('x6-node-immovable')
  )
  return mainCanvasNodes.length ? mainCanvasNodes : nodeElements
}

export function resolveValidationNavigationInvoker({
  editorRoot,
} = {}) {
  const directInvoker =
    typeof editorRoot?.navigateToValidationNode === 'function'
      ? { fn: editorRoot.navigateToValidationNode, thisArg: editorRoot }
      : null
  if (directInvoker) return directInvoker

  return null
}

export function findValidationNavigationNodeElement({
  rootElement,
  nodeName,
} = {}) {
  const allNodeElements = Array.from(rootElement?.querySelectorAll?.('.x6-node') || [])
  if (!allNodeElements.length) return null

  const preferredNodeElements = preferMainCanvasNodes(allNodeElements)
  return (
    preferredNodeElements.find((nodeElement) => matchesNodeName(nodeElement, nodeName)) ||
    allNodeElements.find((nodeElement) => matchesNodeName(nodeElement, nodeName)) ||
    null
  )
}

function resolvePointerCoordinates(nodeElement) {
  const rect = nodeElement?.getBoundingClientRect?.()
  if (!rect) {
    return { clientX: 0, clientY: 0 }
  }
  return {
    clientX: rect.left + rect.width / 2,
    clientY: rect.top + rect.height / 2,
  }
}

export function dispatchValidationNavigationDomSequence({
  rootElement,
  nodeName,
  mouseEventClass = globalThis.MouseEvent,
  pointerEventClass = globalThis.PointerEvent,
  view = globalThis.window,
} = {}) {
  const nodeElement = findValidationNavigationNodeElement({ rootElement, nodeName })
  if (!nodeElement || typeof mouseEventClass !== 'function') {
    return false
  }

  const pointerCoordinates = resolvePointerCoordinates(nodeElement)
  const baseInit = {
    bubbles: true,
    cancelable: true,
    view,
    ...pointerCoordinates,
  }

  if (typeof pointerEventClass === 'function') {
    nodeElement.dispatchEvent(
      new pointerEventClass('pointerdown', {
        ...baseInit,
        pointerId: 1,
      })
    )
  }
  nodeElement.dispatchEvent(new mouseEventClass('mousedown', baseInit))
  if (typeof pointerEventClass === 'function') {
    nodeElement.dispatchEvent(
      new pointerEventClass('pointerup', {
        ...baseInit,
        pointerId: 1,
      })
    )
  }
  nodeElement.dispatchEvent(new mouseEventClass('mouseup', baseInit))
  nodeElement.dispatchEvent(new mouseEventClass('click', baseInit))
  return true
}

function isFailedInvokerResult(result) {
  return result === false || result?.ok === false
}

export async function performValidationNavigation({
  editorRoot,
  rootElement,
  nodeName,
  mouseEventClass = globalThis.MouseEvent,
  pointerEventClass = globalThis.PointerEvent,
  view = globalThis.window,
} = {}) {
  const navigationInvoker = resolveValidationNavigationInvoker({
    editorRoot,
  })

  if (navigationInvoker) {
    try {
      const result = await navigationInvoker.fn.call(navigationInvoker.thisArg, nodeName)
      if (!isFailedInvokerResult(result)) {
        return {
          ok: true,
          strategy: 'editor-invoker',
          result,
        }
      }
    } catch {
      // Fall through to the DOM sequence when the direct invoker fails unexpectedly.
    }
  }

  const dispatched = dispatchValidationNavigationDomSequence({
    rootElement,
    nodeName,
    mouseEventClass,
    pointerEventClass,
    view,
  })

  return {
    ok: dispatched,
    strategy: dispatched ? 'dom-fallback' : 'unresolved',
    result: dispatched,
  }
}
