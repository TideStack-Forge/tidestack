const NODE_STATES = {
  'node.started': 'running',
  'node.completed': 'completed',
  'node.failed': 'failed',
  'node.skipped': 'skipped',
  'node.blocked': 'blocked',
}

export function connectionKey(connection) {
  return JSON.stringify([
    connection.sourceNode, connection.sourceOutputPort,
    connection.targetNode, connection.targetInputPort,
  ])
}

export function invocationKey(event) {
  return JSON.stringify([event.flowInvocationId, event.scope])
}

export function replayInvocations(events) {
  const seen = new Map()
  events.forEach((event) => {
    if (event.kind === 'flow.started') {
      seen.set(invocationKey(event), {
        key: invocationKey(event), invocationId: event.flowInvocationId,
        graphId: event.graphId, flowName: event.flowName, scope: event.scope,
        parentInvocationId: event.parentInvocationId, callerNodeName: event.callerNodeName,
      })
    }
  })
  return [...seen.values()]
}

export function mergeReplayEvents(existing, incoming, { captureId, runId }) {
  const bySeq = new Map(existing.map((event) => [event.seq, event]))
  for (const event of incoming) {
    if (event.schemaVersion !== '2.0' || event.captureId !== captureId || event.runId !== runId
        || !Number.isSafeInteger(event.seq) || event.seq < 1 || event.seq > 2000) {
      throw new Error('收到与所选执行不匹配的调试事件')
    }
    const previous = bySeq.get(event.seq)
    if (previous && JSON.stringify(previous) !== JSON.stringify(event)) {
      throw new Error('调试事件发生冲突，请重新加载记录')
    }
    bySeq.set(event.seq, event)
  }
  if (bySeq.size > 2000) throw new Error('该执行超出了回放容量')
  return [...bySeq.values()].sort((left, right) => left.seq - right.seq)
}

export function replayFrame(events, position, invocation, { allTransfers = false } = {}) {
  const frame = { nodes: new Map(), transfers: new Set(), missing: [], current: null }
  if (!invocation) return frame
  const through = Math.max(0, Math.min(events.length, position))
  let expected = 1
  events.slice(0, through).forEach((event, index) => {
    if (event.seq !== expected) frame.missing.push(expected)
    expected = event.seq + 1
    if (invocationKey(event) !== invocation.key) return
    frame.current = event
    if (event.nodeName && NODE_STATES[event.kind]) {
      const previous = frame.nodes.get(event.nodeName) || {}
      frame.nodes.set(event.nodeName, {
        ...previous, state: NODE_STATES[event.kind],
        input: event.payload?.inputSnapshots ?? previous.input,
        output: event.payload?.outputSnapshots ?? previous.output,
        exception: event.payload?.exceptionSnapshot ?? previous.exception,
        truncated: event.payload?.dataTruncated || previous.truncated,
        event,
      })
    }
    const transfer = event.payload?.transfer
    if (event.kind === 'input.transfer' && transfer?.active && transfer?.selected
        && transfer.sourceNode && (allTransfers || index === through - 1)) {
      frame.transfers.add(connectionKey(transfer))
    }
  })
  return frame
}

export function snapshotText(snapshot) {
  if (!snapshot) return '未采集到快照'
  return JSON.stringify(snapshot.value ?? snapshot, null, 2)
}

export function exceptionText(snapshot) {
  const value = snapshot?.value ?? snapshot
  if (!value?.type) return snapshotText(snapshot)
  const lines = []
  function append(exception, prefix = '', indent = '') {
    lines.push(`${indent}${prefix}${exception.type}${exception.message ? ': ' + exception.message : ''}`)
    for (const frame of exception.frames || []) lines.push(`${indent}    at ${frame}`)
    for (const suppressed of exception.suppressed || []) append(suppressed, 'Suppressed: ', indent + '    ')
    if (exception.cause) append(exception.cause, 'Caused by: ', indent)
  }
  append(value)
  return lines.join('\n')
}
