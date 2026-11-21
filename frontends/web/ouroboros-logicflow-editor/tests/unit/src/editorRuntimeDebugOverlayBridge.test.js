import { afterEach, describe, expect, it, vi } from 'vitest'
import { subscribeToEditorRuntimeDebugCapture } from '../../../src/logicFlowEditor/orchestration/editorRuntimeDebugOverlayBridge.mjs'

afterEach(() => vi.useRealTimers())
describe('runtime debug capture notification recovery', () => {
  it('uses the host subscription, coalesces concurrent hints and disposes polling and subscription', async () => {
    vi.useFakeTimers()
    let notify
    const unsubscribe = vi.fn()
    const subscribe = vi.fn((_captureId, callback) => { notify = callback; return unsubscribe })
    const refresh = vi.fn(async () => {})
    const dispose = subscribeToEditorRuntimeDebugCapture({ subscribe, captureId: 'capture', refresh })
    expect(subscribe.mock.calls[0][0]).toBe('capture')
    notify(); notify()
    await Promise.resolve()
    expect(refresh).toHaveBeenCalledTimes(1)
    await vi.advanceTimersByTimeAsync(2000)
    expect(refresh).toHaveBeenCalledTimes(2)
    dispose()
    await vi.advanceTimersByTimeAsync(10000)
    expect(refresh).toHaveBeenCalledTimes(2)
    expect(unsubscribe).toHaveBeenCalledTimes(1)
  })

  it('recovers through bounded polling when websocket is unavailable', async () => {
    vi.useFakeTimers()
    const refresh = vi.fn(async () => {})
    const dispose = subscribeToEditorRuntimeDebugCapture({ captureId: 'capture', refresh })
    await vi.advanceTimersByTimeAsync(4000)
    expect(refresh).toHaveBeenCalledTimes(2)
    dispose()
  })
})
