import test from 'node:test'
import assert from 'node:assert/strict'

import {
  applyPropertyPanelFieldErrors,
  closePropertyPanel,
  disposePropertyPanelRuntime,
  notifyPropertyPanelLayoutSettled,
  renderPropertyPanelSchema,
  resolvePropertyPanelFormComponent,
  syncPropertyPanelFormRuntime,
  waitForPropertyPanelLayoutFrame,
} from '../src/logicFlowEditor/components/propertiesPanel/propertyPanelLifecycle.mjs'

function createContainerHarness() {
  return {
    children: [],
    replaceChildrenCalls: 0,
    appendChild(child) {
      this.children.push(child)
    },
    replaceChildren() {
      this.replaceChildrenCalls++
      this.children = []
    },
  }
}

test('renderPropertyPanelSchema replaces previous mount node and destroys previous scope before rendering', async () => {
  const formContainer = createContainerHarness()
  formContainer.children = [{ stale: true }]

  const previousScope = {
    destroyCalls: 0,
    destroy() {
      this.destroyCalls++
    },
  }
  const createdElements = []
  const renderCalls = []
  const nextScope = { id: 'next-scope' }

  const renderedScope = await renderPropertyPanelSchema({
    formContainer,
    amisScoped: previousScope,
    schema: { body: [] },
    formData: { name: '节点' },
    createElementFn(tagName) {
      const element = { tagName }
      createdElements.push(element)
      return element
    },
    renderAmisSchemaFn(target, schema, options) {
      renderCalls.push([target, schema, options])
      return Promise.resolve(nextScope)
    },
  })

  assert.equal(previousScope.destroyCalls, 1)
  assert.equal(formContainer.replaceChildrenCalls, 1)
  assert.deepEqual(createdElements, [{ tagName: 'div' }])
  assert.deepEqual(formContainer.children, [{ tagName: 'div' }])
  assert.equal(renderCalls.length, 1)
  assert.deepEqual(renderCalls[0][0], { tagName: 'div' })
  assert.deepEqual(renderCalls[0][1], { body: [] })
  assert.deepEqual(renderCalls[0][2].data, { name: '节点' })
  assert.equal(typeof renderCalls[0][2].scopeRef, 'function')
  assert.equal(renderedScope, nextScope)
})

test('renderPropertyPanelSchema materializes inherited form data before handing it to amis', async () => {
  const formContainer = createContainerHarness()
  const superData = {
    __ENTRY_NODES: [{ label: '入口节点', value: 'entry' }],
    __EXIT_NODES: [{ label: '出口节点', value: 'exit' }],
  }
  const formData = Object.create(superData)
  formData.name = '节点A'

  const renderCalls = []

  await renderPropertyPanelSchema({
    formContainer,
    amisScoped: null,
    schema: { body: [] },
    formData,
    createElementFn(tagName) {
      return { tagName }
    },
    renderAmisSchemaFn(target, schema, options) {
      renderCalls.push([target, schema, options])
      return Promise.resolve({ id: 'scope' })
    },
  })

  assert.equal(renderCalls.length, 1)
  assert.deepEqual(renderCalls[0][2].data, {
    __ENTRY_NODES: [{ label: '入口节点', value: 'entry' }],
    __EXIT_NODES: [{ label: '出口节点', value: 'exit' }],
    name: '节点A',
  })
  assert.equal(typeof renderCalls[0][2].scopeRef, 'function')
})

test('renderPropertyPanelSchema awaits async previous scope destruction before rendering next schema', async () => {
  const formContainer = createContainerHarness()
  const steps = []
  let releaseDestroy = null

  const renderPromise = renderPropertyPanelSchema({
    formContainer,
    amisScoped: {
      async destroy() {
        steps.push('destroy:start')
        await new Promise((resolve) => {
          releaseDestroy = resolve
        })
        steps.push('destroy:end')
      },
    },
    schema: { body: [] },
    formData: { name: '节点' },
    createElementFn(tagName) {
      return { tagName }
    },
    renderAmisSchemaFn() {
      steps.push('render')
      return Promise.resolve({ id: 'next-scope' })
    },
  })

  await Promise.resolve()
  assert.deepEqual(steps, ['destroy:start'])

  releaseDestroy()
  await renderPromise

  assert.deepEqual(steps, ['destroy:start', 'destroy:end', 'render'])
})

test('renderPropertyPanelSchema injects field errors through the rendered amis form', async () => {
  const formContainer = createContainerHarness()
  const setErrorsCalls = []
  const nextScope = {
    getScopeRef() {
      return {
        getComponentByName(name) {
          if (name !== 'propertyForm') return null
          return {
            setErrors(errors, tag) {
              setErrorsCalls.push({ errors, tag })
            },
          }
        },
      }
    },
  }

  await renderPropertyPanelSchema({
    formContainer,
    amisScoped: null,
    schema: { body: [] },
    formData: { entryNode: '' },
    fieldErrors: {
      entryNode: ['入口节点未找到'],
    },
    createElementFn(tagName) {
      return { tagName }
    },
    renderAmisSchemaFn() {
      return Promise.resolve(nextScope)
    },
  })

  assert.deepEqual(setErrorsCalls, [
    {
      errors: {
        entryNode: '入口节点未找到',
      },
      tag: 'logicflow-validation',
    },
  ])
})

test('syncPropertyPanelFormRuntime updates existing form data and field errors without remounting', () => {
  const setValuesCalls = []
  const componentSetDataCalls = []
  const setExternalErrorsCalls = []
  const formComponent = {
    props: {
      name: 'propertyForm',
      store: {
        setValues(data) {
          setValuesCalls.push(data)
        },
        setExternalErrors(errors, source) {
          setExternalErrorsCalls.push({ errors, source })
        },
      },
    },
    setData(data) {
      componentSetDataCalls.push(data)
    },
  }
  const inheritedData = Object.create({
    __ENTRY_NODES: [{ label: '入口节点', value: '入口节点' }],
  })
  inheritedData.name = '脚本节点'

  const synced = syncPropertyPanelFormRuntime(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            return name === 'propertyForm' ? formComponent : null
          },
        }
      },
    },
    {
      formData: inheritedData,
      fieldErrors: {
        name: ['节点名重复'],
      },
    }
  )

  assert.equal(synced, true)
  assert.deepEqual(setValuesCalls, [
    {
      __ENTRY_NODES: [{ label: '入口节点', value: '入口节点' }],
      name: '脚本节点',
    },
  ])
  assert.deepEqual(componentSetDataCalls, [])
  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {
        name: ['节点名重复'],
      },
      source: 'logicflow-validation',
    },
  ])
})

test('syncPropertyPanelFormRuntime clears stale external errors when latest errors are empty', () => {
  const setExternalErrorsCalls = []
  const synced = syncPropertyPanelFormRuntime(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            if (name !== 'propertyForm') return null
            return {
              props: {
                name: 'propertyForm',
                store: {
                  setValues() {},
                  setExternalErrors(errors, source) {
                    setExternalErrorsCalls.push({ errors, source })
                  },
                },
              },
            }
          },
        }
      },
    },
    {
      formData: { name: '脚本节点' },
      fieldErrors: {},
    }
  )

  assert.equal(synced, true)
  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {},
      source: 'logicflow-validation',
    },
  ])
})

test('syncPropertyPanelFormRuntime falls back to remount when form data cannot be updated', () => {
  const synced = syncPropertyPanelFormRuntime(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            return name === 'propertyForm' ? { props: { name: 'propertyForm' } } : null
          },
        }
      },
    },
    {
      formData: { name: '脚本节点' },
    }
  )

  assert.equal(synced, false)
})

test('syncPropertyPanelFormRuntime does not use component setData because it emits form changes', () => {
  const setDataCalls = []
  const synced = syncPropertyPanelFormRuntime(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            if (name !== 'propertyForm') return null
            return {
              props: {
                name: 'propertyForm',
              },
              setData(data) {
                setDataCalls.push(data)
              },
            }
          },
        }
      },
    },
    {
      formData: { name: '脚本节点' },
    }
  )

  assert.equal(synced, false)
  assert.deepEqual(setDataCalls, [])
})

test('applyPropertyPanelFieldErrors can fall back to amis form store error injection', () => {
  const setFormItemErrorsCalls = []
  const applied = applyPropertyPanelFieldErrors(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            if (name !== 'propertyForm') return null
            return {
              props: {
                store: {
                  setFormItemErrors(errors, tag) {
                    setFormItemErrorsCalls.push({ errors, tag })
                  },
                },
              },
            }
          },
        }
      },
    },
    {
      flowName: ['流程名未配置', '流程不存在'],
    }
  )

  assert.equal(applied, true)
  assert.deepEqual(setFormItemErrorsCalls, [
    {
      errors: {
        flowName: '流程名未配置\n流程不存在',
      },
      tag: 'logicflow-validation',
    },
  ])
})

test('applyPropertyPanelFieldErrors prefers host external error injection when available', () => {
  const setExternalErrorsCalls = []
  const applied = applyPropertyPanelFieldErrors(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            if (name !== 'propertyForm') return null
            return {
              setExternalErrors(errors, source) {
                setExternalErrorsCalls.push({ errors, source })
              },
            }
          },
        }
      },
    },
    {
      flowName: ['流程名未配置', '流程不存在'],
    }
  )

  assert.equal(applied, true)
  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {
        flowName: ['流程名未配置', '流程不存在'],
      },
      source: 'logicflow-validation',
    },
  ])
})

test('applyPropertyPanelFieldErrors prefers host store external error injection before legacy setErrors', () => {
  const setErrorsCalls = []
  const setExternalErrorsCalls = []
  const applied = applyPropertyPanelFieldErrors(
    {
      getScopeRef() {
        return {
          getComponentByName(name) {
            if (name !== 'propertyForm') return null
            return {
              setErrors(errors, tag) {
                setErrorsCalls.push({ errors, tag })
              },
              props: {
                store: {
                  setExternalErrors(errors, source) {
                    setExternalErrorsCalls.push({ errors, source })
                  },
                },
              },
            }
          },
        }
      },
    },
    {
      flowName: ['流程名未配置'],
    }
  )

  assert.equal(applied, true)
  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {
        flowName: ['流程名未配置'],
      },
      source: 'logicflow-validation',
    },
  ])
  assert.deepEqual(setErrorsCalls, [])
})

test('applyPropertyPanelFieldErrors resolves amis form from scope component collection', () => {
  const setExternalErrorsCalls = []
  const formComponent = {
    props: {
      name: undefined,
      store: {
        setExternalErrors(errors, source) {
          setExternalErrorsCalls.push({ errors, source })
        },
      },
    },
  }
  const scopeRef = {
    getComponentByName() {
      return null
    },
    getComponentById() {
      return null
    },
    getComponents() {
      return []
    },
    children: [
      {
        getComponents() {
          return [formComponent]
        },
      },
    ],
  }
  const amisScoped = {
    getScopeRef() {
      return scopeRef
    },
  }

  assert.equal(resolvePropertyPanelFormComponent(amisScoped), formComponent)
  assert.equal(
    applyPropertyPanelFieldErrors(amisScoped, {
      flowName: ['流程调用节点不能引用当前流程自身'],
    }),
    true
  )
  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {
        flowName: ['流程调用节点不能引用当前流程自身'],
      },
      source: 'logicflow-validation',
    },
  ])
})

test('renderPropertyPanelSchema waits for amis form store external API binding before injecting errors', async () => {
  const formContainer = createContainerHarness()
  const setExternalErrorsCalls = []
  const formStore = {}
  const nextScope = {
    getScopeRef() {
      return null
    },
  }

  await renderPropertyPanelSchema({
    formContainer,
    amisScoped: null,
    schema: { body: [] },
    formData: { flowName: '' },
    fieldErrors: {
      flowName: ['流程调用节点不能引用当前流程自身'],
    },
    createElementFn(tagName) {
      return { tagName }
    },
    renderAmisSchemaFn(_target, _schema, options) {
      options.scopeRef({
        component: {
          props: {
            store: formStore,
          },
        },
      })
      Promise.resolve().then(() => {
        formStore.setExternalErrors = (errors, source) => {
          setExternalErrorsCalls.push({ errors, source })
        }
      })
      return Promise.resolve(nextScope)
    },
  })

  assert.deepEqual(setExternalErrorsCalls, [
    {
      errors: {
        flowName: ['流程调用节点不能引用当前流程自身'],
      },
      source: 'logicflow-validation',
    },
  ])
})

test('closePropertyPanel cancels pending toggle work and collapses the panel immediately', () => {
  const collapsed = { value: false }
  const calls = []

  closePropertyPanel({
    collapsed,
    toggleFn: {
      cancel() {
        calls.push('cancel')
      },
    },
  })

  assert.deepEqual(calls, ['cancel'])
  assert.equal(collapsed.value, true)
})

test('waitForPropertyPanelLayoutFrame waits for two animation frames before resolving', async () => {
  const callbacks = []
  let resolved = false

  const promise = waitForPropertyPanelLayoutFrame((callback) => {
    callbacks.push(callback)
  }).then(() => {
    resolved = true
  })

  assert.equal(callbacks.length, 1)
  callbacks.shift()()
  assert.equal(resolved, false)
  assert.equal(callbacks.length, 1)

  callbacks.shift()()
  await promise

  assert.equal(resolved, true)
})

test('notifyPropertyPanelLayoutSettled dispatches a resize event for layout-measuring controls', () => {
  const events = []

  notifyPropertyPanelLayoutSettled({
    Event,
    dispatchEvent(event) {
      events.push(event)
    },
  })

  assert.equal(events.length, 1)
  assert.equal(events[0].type, 'resize')
})

test('disposePropertyPanelRuntime tears down scoped renderer, container nodes and dragging resources', async () => {
  const calls = []
  const formContainer = createContainerHarness()
  formContainer.children = [{ mounted: true }]

  await disposePropertyPanelRuntime({
    draggingProvider: {
      destroy() {
        calls.push('dragging-destroy')
      },
    },
    toggleFn: {
      cancel() {
        calls.push('toggle-cancel')
      },
    },
    amisScoped: {
      destroy() {
        calls.push('scope-destroy')
      },
    },
    formContainer,
  })

  assert.deepEqual(calls, ['toggle-cancel', 'scope-destroy', 'dragging-destroy'])
  assert.equal(formContainer.replaceChildrenCalls, 1)
  assert.deepEqual(formContainer.children, [])
})

test('disposePropertyPanelRuntime awaits async scope destruction before finishing teardown', async () => {
  const calls = []
  const formContainer = createContainerHarness()
  let releaseDestroy = null

  const disposePromise = disposePropertyPanelRuntime({
    draggingProvider: {
      destroy() {
        calls.push('dragging-destroy')
      },
    },
    toggleFn: {
      cancel() {
        calls.push('toggle-cancel')
      },
    },
    amisScoped: {
      async destroy() {
        calls.push('scope-destroy:start')
        await new Promise((resolve) => {
          releaseDestroy = resolve
        })
        calls.push('scope-destroy:end')
      },
    },
    formContainer,
  })

  await Promise.resolve()
  assert.deepEqual(calls, ['toggle-cancel', 'scope-destroy:start'])

  releaseDestroy()
  await disposePromise

  assert.deepEqual(calls, [
    'toggle-cancel',
    'scope-destroy:start',
    'scope-destroy:end',
    'dragging-destroy',
  ])
})
