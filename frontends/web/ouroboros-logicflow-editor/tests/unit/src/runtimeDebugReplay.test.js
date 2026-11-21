import { describe, expect, it } from 'vitest'
import { connectionKey, exceptionText, mergeReplayEvents, replayFrame, replayInvocations } from '../../../src/logicFlowEditor/debug/runtimeDebugReplay.mjs'

const event = (seq, kind, invocation = 'run', scope = 'main', payload = {}) => ({
  schemaVersion: '2.0', captureId: 'capture', runId: 'run', seq, kind,
  flowInvocationId: invocation, scope, nodeName: 'script', graphId: invocation,
  payload, flowName: 'flow',
})

describe('runtime execution replay', () => {
  it('renders exception frames, causes and suppressed exceptions as a readable stack', () => {
    expect(exceptionText({ value: {
      type: 'FlowRuntimeException', message: '脚本执行失败', frames: ['Flow.run(Flow.java:10)'],
      suppressed: [{ type: 'CloseException', frames: ['Resource.close(Resource.java:7)'] }],
      cause: { type: 'IllegalArgumentException', message: '金额不能小于零', frames: ['Script1.run(Script1.groovy:1)'] },
    } })).toBe([
      'FlowRuntimeException: 脚本执行失败', '    at Flow.run(Flow.java:10)',
      '    Suppressed: CloseException', '        at Resource.close(Resource.java:7)',
      'Caused by: IllegalArgumentException: 金额不能小于零', '    at Script1.run(Script1.groovy:1)',
    ].join('\n'))
  })

  it('keeps unavailable and truncated exception snapshots visible', () => {
    expect(exceptionText()).toBe('未采集到快照')
    expect(exceptionText({ value: { truncated: true } })).toContain('"truncated": true')
  })

  it('sorts and deduplicates events for the selected execution and rejects cross-capture data', () => {
    const envelope = { captureId: 'capture', runId: 'run' }
    expect(mergeReplayEvents([event(2, 'node.started')], [event(1, 'flow.started'), event(2, 'node.started')], envelope).map((item) => item.seq)).toEqual([1, 2])
    expect(() => mergeReplayEvents([], [{ ...event(1, 'node.started'), runId: 'other' }], envelope)).toThrow('不匹配')
    expect(() => mergeReplayEvents([event(1, 'node.started')], [event(1, 'node.failed')], envelope)).toThrow('冲突')
  })

  it('keeps nested and error scope node states separate and can rewind deterministically', () => {
    const events = [
      event(1, 'flow.started'), event(2, 'node.started', 'run', 'main', { inputSnapshots: { value: [1] } }),
      event(3, 'flow.started', 'child'), event(4, 'node.failed', 'child', 'main', { exceptionSnapshot: { value: 'boom' } }),
      event(5, 'flow.started', 'run', 'error'), event(6, 'node.completed', 'run', 'error'),
      event(7, 'node.completed', 'run', 'main', { outputSnapshots: { value: [2] } }),
    ]
    const invocations = replayInvocations(events)
    expect(invocations).toHaveLength(3)
    expect(replayFrame(events, 4, invocations[0]).nodes.get('script')).toMatchObject({ state: 'running', input: { value: [1] } })
    expect(replayFrame(events, 7, invocations[0]).nodes.get('script')).toMatchObject({ state: 'completed', output: { value: [2] } })
    expect(replayFrame(events, 7, invocations[1]).nodes.get('script')).toMatchObject({ state: 'failed', exception: { value: 'boom' } })
    expect(replayFrame(events, 7, invocations[2]).nodes.get('script').state).toBe('completed')
    expect(replayFrame(events, 0, invocations[0]).nodes.size).toBe(0)
  })

  it('animates only selected active actual connections and exposes sequence gaps', () => {
    const transfer = { sourceNode: 'source', sourceOutputPort: 1, targetNode: 'script', targetInputPort: 2, active: true, selected: true }
    const events = [event(1, 'flow.started'), event(3, 'input.transfer', 'run', 'main', { transfer })]
    const invocation = replayInvocations(events)[0]
    expect([...replayFrame(events, 2, invocation).transfers]).toEqual([connectionKey(transfer)])
    expect(replayFrame(events, 2, invocation).missing).toEqual([2])
    events[1].payload.transfer = { ...transfer, selected: false }
    expect(replayFrame(events, 2, invocation).transfers.size).toBe(0)
  })

  it('shows every traversed connection at once without including inactive or other invocation paths', () => {
    const first = { sourceNode: 'entry', sourceOutputPort: 0, targetNode: 'middle', targetInputPort: 0, active: true, selected: true }
    const second = { ...first, sourceNode: 'middle', targetNode: 'script' }
    const events = [
      event(1, 'flow.started'),
      event(2, 'input.transfer', 'run', 'main', { transfer: first }),
      event(3, 'input.transfer', 'run', 'main', { transfer: second }),
      event(4, 'input.transfer', 'child', 'main', { transfer: { ...first, targetNode: 'child' } }),
      event(5, 'input.transfer', 'run', 'main', { transfer: { ...second, targetNode: 'unused', selected: false } }),
      event(6, 'node.failed', 'run', 'main', { exceptionSnapshot: { value: 'invalid amount' } }),
    ]
    const frame = replayFrame(events, events.length, replayInvocations(events)[0], { allTransfers: true })
    expect([...frame.transfers]).toEqual([connectionKey(first), connectionKey(second)])
    expect(frame.nodes.get('script')).toMatchObject({ state: 'failed', exception: { value: 'invalid amount' } })
  })
})
