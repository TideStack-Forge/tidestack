import {
  hasPendingValidationNavigationRequest,
} from './validationNavigationRequest.mjs'

export function createValidationNavigationRequestPoller({
  getProps = () => ({}),
  getLastRequest = () => ({}),
  onPendingRequest = () => {},
  hasPendingRequestFn = hasPendingValidationNavigationRequest,
  intervalMs = 100,
  setIntervalFn = globalThis.setInterval?.bind(globalThis),
  clearIntervalFn = globalThis.clearInterval?.bind(globalThis),
} = {}) {
  let intervalId = null

  function poll() {
    const nextProps = getProps() || {}
    const lastRequest = getLastRequest() || {}
    if (!hasPendingRequestFn(lastRequest, nextProps)) {
      return false
    }
    onPendingRequest(nextProps)
    return true
  }

  function start() {
    if (intervalId != null || typeof setIntervalFn !== 'function') {
      return intervalId
    }
    intervalId = setIntervalFn(() => {
      poll()
    }, intervalMs)
    return intervalId
  }

  function stop() {
    if (intervalId == null) {
      return
    }
    if (typeof clearIntervalFn === 'function') {
      clearIntervalFn(intervalId)
    }
    intervalId = null
  }

  return {
    poll,
    start,
    stop,
  }
}
