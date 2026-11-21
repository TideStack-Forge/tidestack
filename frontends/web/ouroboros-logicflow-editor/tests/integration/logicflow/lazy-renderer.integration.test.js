import React, { act, createRef } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, expect, test, vi } from 'vitest'

const { load, registrations } = vi.hoisted(() => ({ load: vi.fn(), registrations: [] }))
vi.mock('../../../src/editorModuleLoader.mjs', () => ({ loadLogicFlowRenderer: load }))
vi.mock('ouroboros-sdk', () => ({ FormItem: (options) => (renderer) => registrations.push({ options, renderer }) }))
import { LogicFlowEditorRenderer } from '../../../src/registerComponent.js'

const mounted = []
afterEach(async () => {
  for (const { root, container } of mounted.splice(0)) {
    await act(async () => root.unmount())
    container.remove()
  }
  load.mockReset()
})

function deferred() {
  let resolve, reject
  const promise = new Promise((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

class LoadedRenderer extends React.Component {
  validate() { return this.props.value.problem || undefined }
  flushPendingChanges() { this.props.onChange?.(this.props.value) }
  render() {
    return React.createElement('div', { 'data-graph': this.props.value.name,
      'data-readonly': String(!!this.props.readonly) }, this.props.value.label)
  }
}

async function mount(props) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const ref = createRef()
  mounted.push({ root, container })
  const render = async (next) => act(async () => root.render(React.createElement(LogicFlowEditorRenderer, { ...next, ref })))
  await render(props)
  return { container, root, ref, render }
}

test('registers once and blocks saving until ready, then renders latest value and readonly props', async () => {
  expect(registrations).toHaveLength(1)
  expect(registrations[0].options.type).toBe('logic-flow-editor')
  const pending = deferred()
  load.mockReturnValue(pending.promise)
  const view = await mount({ value: { name: 'first', label: 'old' } })
  expect(view.container.textContent).toContain('正在加载')
  expect(view.ref.current.validate()).toContain('请稍后再保存')
  await view.render({ readonly: true, value: { name: 'latest', label: 'draft retained', problem: 'invalid draft' } })
  await act(async () => pending.resolve(LoadedRenderer))
  expect(view.container.querySelector('[data-graph="latest"]').dataset.readonly).toBe('true')
  expect(view.container.textContent).toBe('draft retained')
  expect(view.ref.current.validate()).toBe('invalid draft')
})

test('a failed load keeps the draft and an explicit retry can succeed', async () => {
  const pending = deferred()
  load.mockReturnValueOnce(pending.promise).mockResolvedValue(LoadedRenderer)
  const view = await mount({ value: { name: 'draft', label: 'unsaved draft' } })
  await act(async () => pending.reject(new Error('chunk unavailable')))
  expect(view.container.querySelector('[role="alert"]').textContent).toContain('编辑内容已保留')
  expect(view.ref.current.validate()).toContain('加载失败')
  await act(async () => view.container.querySelector('button').click())
  expect(load).toHaveBeenCalledTimes(2)
  expect(view.container.textContent).toBe('unsaved draft')
  expect(view.ref.current.validate()).toBeUndefined()
})

test('closing during loading creates no graph and concurrent editors retain separate values', async () => {
  const pending = deferred()
  load.mockReturnValue(pending.promise)
  const first = await mount({ value: { name: 'one', label: 'one' } })
  const second = await mount({ value: { name: 'two', label: 'two' } })
  await act(async () => first.root.unmount())
  await act(async () => pending.resolve(LoadedRenderer))
  expect(first.container.querySelector('[data-graph]')).toBeNull()
  expect(second.container.querySelector('[data-graph="two"]')).not.toBeNull()
})

test('a persistently failing download offers at most two manual retries', async () => {
  load.mockRejectedValue(new Error('offline'))
  const view = await mount({ value: { name: 'draft' } })
  for (let retry = 0; retry < 2; retry++) {
    await act(async () => view.container.querySelector('button').click())
  }
  expect(load).toHaveBeenCalledTimes(3)
  expect(view.container.querySelector('button')).toBeNull()
  expect(view.ref.current.validate()).toContain('加载失败')
})

test('host form flush publishes the current editor value and is detached on close', async () => {
  load.mockResolvedValue(LoadedRenderer)
  let flush
  const detach = vi.fn()
  const onChange = vi.fn()
  const draft = { name: 'current', label: 'latest inspector value' }
  const view = await mount({ value: draft, onChange, addHook: (hook, type) => {
    if (type === 'flush') flush = hook
    return detach
  } })
  await act(async () => { await flush() })
  expect(onChange).toHaveBeenLastCalledWith(draft)
  await act(async () => view.root.unmount())
  expect(detach).toHaveBeenCalledTimes(1)
})
