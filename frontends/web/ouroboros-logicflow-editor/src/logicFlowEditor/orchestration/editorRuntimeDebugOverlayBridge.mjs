/** Notifications are hints. Bounded periodic reads also recover notifications lost during reconnect. */
export function subscribeToEditorRuntimeDebugCapture({ subscribe, captureId, refresh, interval = 2000 }) {
  let disposed = false
  let pending = false
  let timer
  let unsubscribe
  async function pull() {
    if (disposed || pending) return
    pending = true
    try {
      await refresh()
    } finally {
      pending = false
    }
  }
  function schedule() {
    timer = setTimeout(async () => {
      try { await pull() } finally { if (!disposed) schedule() }
    }, interval)
  }
  if (typeof subscribe === 'function') {
    unsubscribe = subscribe(captureId, () => { void pull() })
  }
  schedule()
  return () => {
    disposed = true
    clearTimeout(timer)
    unsubscribe?.()
  }
}
