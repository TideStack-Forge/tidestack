function materializePropertyPanelData(formData) {
  if (!formData || typeof formData !== 'object') {
    return formData
  }

  const materialized = {}
  Object.keys(formData).forEach((key) => {
    materialized[key] = formData[key]
  })

  for (const key in formData) {
    if (Object.prototype.hasOwnProperty.call(materialized, key)) {
      continue
    }

    materialized[key] = formData[key]
  }

  return materialized
}

function normalizeFieldErrors(fieldErrors = {}) {
  if (!fieldErrors || typeof fieldErrors !== 'object') {
    return {}
  }

  return Object.entries(fieldErrors).reduce((acc, [fieldPath, messages]) => {
    if (typeof fieldPath !== 'string' || !fieldPath.trim()) {
      return acc
    }
    const normalizedMessages = (Array.isArray(messages) ? messages : [messages])
      .map((message) => (typeof message === 'string' && message.trim() ? message.trim() : null))
      .filter(Boolean)
    if (normalizedMessages.length) {
      acc[fieldPath.trim()] = normalizedMessages.join('\n')
    }
    return acc
  }, {})
}

function resolvePropertyPanelFormStore(amisScoped) {
  const formComponent = resolvePropertyPanelFormComponent(amisScoped)
  return formComponent?.props?.store || amisScoped?.getFormStore?.() || null
}

function isPropertyPanelFormComponent(component) {
  return !!(
    component &&
    typeof component === 'object' &&
    (
      component.props?.name === 'propertyForm' ||
      component.props?.id === 'propertyForm' ||
      typeof component.setExternalErrors === 'function' ||
      typeof component.setErrors === 'function' ||
      typeof component.props?.store?.setExternalErrors === 'function' ||
      typeof component.props?.store?.setFormItemErrors === 'function'
    )
  )
}

function collectScopeComponents(scopeRef, components = [], visited = new Set()) {
  if (!scopeRef || visited.has(scopeRef)) {
    return components
  }
  visited.add(scopeRef)

  const scopeComponents =
    typeof scopeRef.getComponents === 'function' ? scopeRef.getComponents() : []
  if (Array.isArray(scopeComponents)) {
    components.push(...scopeComponents)
  }

  if (Array.isArray(scopeRef.children)) {
    scopeRef.children.forEach((childScope) =>
      collectScopeComponents(childScope, components, visited)
    )
  }

  return components
}

export function resolvePropertyPanelFormComponent(amisScoped) {
  const scopeRef = amisScoped?.getScopeRef?.()
  const formStore = amisScoped?.getFormStore?.()
  if (
    formStore &&
    (
      typeof formStore.setExternalErrors === 'function' ||
      typeof formStore.setFormItemErrors === 'function'
    )
  ) {
    return {
      props: {
        store: formStore,
      },
    }
  }

  return (
    scopeRef?.getComponentByName?.('propertyForm') ||
    scopeRef?.getComponentById?.('propertyForm') ||
    collectScopeComponents(scopeRef).find(isPropertyPanelFormComponent) ||
    null
  )
}

export function applyPropertyPanelFieldErrors(amisScoped, fieldErrors = {}) {
  const normalizedErrors = normalizeFieldErrors(fieldErrors)
  const formComponent = resolvePropertyPanelFormComponent(amisScoped)

  if (typeof formComponent?.setExternalErrors === 'function') {
    formComponent.setExternalErrors(
      Object.keys(normalizedErrors).length ? { ...fieldErrors } : {},
      'logicflow-validation'
    )
    return true
  }

  const formStore = formComponent?.props?.store
  if (typeof formStore?.setExternalErrors === 'function') {
    formStore.setExternalErrors(
      Object.keys(normalizedErrors).length ? { ...fieldErrors } : {},
      'logicflow-validation'
    )
    return true
  }

  if (typeof formComponent?.setErrors === 'function') {
    formComponent.setErrors({ ...normalizedErrors }, 'logicflow-validation')
    return true
  }

  if (typeof formStore?.setFormItemErrors === 'function') {
    formStore.setFormItemErrors({ ...normalizedErrors }, 'logicflow-validation')
    return true
  }

  return false
}

export function syncPropertyPanelFormRuntime(amisScoped, { formData, fieldErrors = {} } = {}) {
  if (!amisScoped) {
    return false
  }

  const formStore = resolvePropertyPanelFormStore(amisScoped)
  const materializedData = materializePropertyPanelData(formData)
  let synced = false

  if (typeof formStore?.setValues === 'function') {
    formStore.setValues(materializedData)
    synced = true
  } else if (typeof formStore?.setData === 'function') {
    formStore.setData(materializedData)
    synced = true
  } else if (typeof formStore?.updateData === 'function') {
    formStore.updateData(materializedData, undefined, true)
    synced = true
  }

  applyPropertyPanelFieldErrors(amisScoped, fieldErrors)
  return synced
}

export async function renderPropertyPanelSchema({
  formContainer,
  amisScoped,
  schema,
  formData,
  fieldErrors = {},
  createElementFn,
  renderAmisSchemaFn,
}) {
  await amisScoped?.destroy?.()
  formContainer?.replaceChildren?.()

  const mountNode = createElementFn('div')
  formContainer?.appendChild?.(mountNode)

  const materializedData = materializePropertyPanelData(formData)
  let renderedFormStore = null

  const nextScoped = await renderAmisSchemaFn(mountNode, schema, {
    data: materializedData,
    scopeRef(scopeRef) {
      renderedFormStore = scopeRef?.component?.props?.store || null
    },
  })
  if (nextScoped && typeof nextScoped === 'object' && typeof nextScoped.getFormStore !== 'function') {
    nextScoped.getFormStore = () => renderedFormStore
  }
  nextScoped?.getScopeRef?.()
  await Promise.resolve()
  applyPropertyPanelFieldErrors(nextScoped, fieldErrors)
  return nextScoped
}

export function waitForPropertyPanelLayoutFrame(
  requestAnimationFrameFn = globalThis.requestAnimationFrame
) {
  const scheduleFrame =
    typeof requestAnimationFrameFn === 'function'
      ? requestAnimationFrameFn
      : (callback) => setTimeout(callback, 0)

  return new Promise((resolve) => {
    scheduleFrame(() => {
      scheduleFrame(resolve)
    })
  })
}

export function notifyPropertyPanelLayoutSettled(windowRef = globalThis.window) {
  if (!windowRef || typeof windowRef.dispatchEvent !== 'function') {
    return
  }

  const EventConstructor =
    typeof windowRef.Event === 'function' ? windowRef.Event : globalThis.Event
  const resizeEvent =
    typeof EventConstructor === 'function' ? new EventConstructor('resize') : { type: 'resize' }

  windowRef.dispatchEvent(resizeEvent)
}

export function closePropertyPanel({ collapsed, toggleFn }) {
  toggleFn?.cancel?.()
  collapsed.value = true
}

export async function disposePropertyPanelRuntime({
  draggingProvider,
  toggleFn,
  amisScoped,
  formContainer,
}) {
  toggleFn?.cancel?.()
  await amisScoped?.destroy?.()
  draggingProvider?.destroy?.()
  formContainer?.replaceChildren?.()
}
