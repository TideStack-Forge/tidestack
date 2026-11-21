import { afterEach, expect, test, vi } from 'vitest'
import { EventBus } from '../../src/adapters/amis/ideRuntimeShims/eventBus'
import { Client } from '../../src/adapters/amis/ideRuntimeShims/http'
import { saveAs } from '../../src/adapters/amis/ideRuntimeShims/fileSaver'
import { openDialog, openDrawer, pushModalMethods, popModalMethods, confirm, alert } from '../../src/adapters/amis/ideRuntimeShims/modal'
import { subscribeTopic } from '../../src/adapters/amis/ideRuntimeShims/stomp'

vi.mock('amis', () => ({ confirm: vi.fn(async () => true), alert: vi.fn(async () => undefined), prompt: vi.fn() }))

afterEach(() => vi.unstubAllGlobals())

test('unsupported platform requests and dialogs reject instead of returning success', async () => {
  await expect(Client.get()).rejects.toThrow(/HTTP/)
  await expect(openDialog()).rejects.toThrow(/dialog/)
})

test('configured editor HTTP forwards requests and preserves service failures', async () => {
  const requestService = vi.fn().mockResolvedValueOnce({ status: 0, data: ['node'] })
    .mockRejectedValueOnce(new Error('Service disconnected'))
  vi.stubGlobal('window', { OuroborosMetadataEditorHost: { requestService } })
  await expect(Client.get('/nodes', { kinds: ['a', 'b'] })).resolves.toEqual({
    data: { status: 0, data: ['node'] },
  })
  expect(requestService).toHaveBeenCalledWith({ url: '/nodes', method: 'GET', data: { kinds: ['a', 'b'] } })
  await expect(Client.post('/context', { draft: {} })).rejects.toThrow('Service disconnected')
})

test('helper environment subscription detects changes and stops polling when disposed', async () => {
  vi.useFakeTimers()
  const listener = vi.fn()
  const requestService = vi.fn().mockResolvedValueOnce({ identity: 'one' }).mockResolvedValue({ identity: 'two' })
  vi.stubGlobal('window', { OuroborosMetadataEditorHost: { service: { appName: 'demo' }, requestService } })
  const dispose = subscribeTopic('/dev-helper/demo', listener)
  try {
    await vi.advanceTimersByTimeAsync(0)
    expect(listener).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(5000)
    expect(listener).toHaveBeenCalledTimes(1)
    expect(requestService).toHaveBeenCalledWith({ method: 'GET', url: '/api/dev/demo/code-intelligence/environment' })
    dispose()
    await vi.advanceTimersByTimeAsync(10000)
    expect(requestService).toHaveBeenCalledTimes(2)
    expect(() => subscribeTopic('/other', listener)).toThrow(/messaging/)
  } finally {
    dispose()
    vi.useRealTimers()
  }
})

test('supported confirmation and alert use the Amis interaction result', async () => {
  expect(await confirm('Continue?')).toBe(true)
  await alert('Saved')
  expect(alert).toHaveBeenCalledWith('Saved')
})

test('active Amis modal scopes dispatch nested dialogs and release their scope on exit', async () => {
  const parent = vi.fn()
  const nested = vi.fn()
  const onConfirm = vi.fn()
  pushModalMethods(parent)
  pushModalMethods(nested)
  try {
    const confirmed = openDialog({ title: 'Nested', onConfirm }, { draft: true })
    expect(parent).not.toHaveBeenCalled()
    const [action, context] = nested.mock.calls[0]
    expect(action.actionType).toBe('dialog')
    expect(context).toEqual({ draft: true })
    action.dialog.onConfirm(['saved'])
    await expect(confirmed).resolves.toEqual(['saved'])
    expect(onConfirm).toHaveBeenCalledWith(['saved'])
    popModalMethods()
    const cancelled = openDrawer({ title: 'Parent' })
    parent.mock.calls[0][0].drawer.onClose()
    await expect(cancelled).resolves.toBe(false)
  } finally {
    popModalMethods()
    popModalMethods()
  }
  await expect(openDialog()).rejects.toThrow(/active Amis scope/)
})

test('file export clicks a named Blob URL and releases it', () => {
  vi.useFakeTimers()
  try {
    const click = vi.fn()
    const anchor = { href: '', download: '', click }
    const blob = new Blob(['content'])
    const createObjectURL = vi.fn(() => 'blob:test')
    const revokeObjectURL = vi.fn()
    vi.stubGlobal('document', { createElement: vi.fn(() => anchor) })
    vi.stubGlobal('URL', { createObjectURL, revokeObjectURL })
    saveAs(blob, 'schema.json')
    expect(createObjectURL).toHaveBeenCalledWith(blob)
    expect(anchor.download).toBe('schema.json')
    expect(anchor.href).toBe('blob:test')
    expect(click).toHaveBeenCalledOnce()
    vi.runAllTimers()
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:test')
  } finally { vi.useRealTimers() }
})

test('local editor events support once and explicit unsubscription', () => {
  const once = vi.fn(), regular = vi.fn()
  EventBus.subscribeOnce('capability-test', once)
  EventBus.subscribe('capability-test', regular)
  EventBus.publish('capability-test', 'first')
  EventBus.publish('capability-test', 'second')
  EventBus.unsubscribe('capability-test', regular)
  EventBus.publish('capability-test', 'third')
  expect(once).toHaveBeenCalledExactlyOnceWith('first')
  expect(regular.mock.calls).toEqual([['first'], ['second']])
  EventBus.unsubscribeAll('capability-test')
})
