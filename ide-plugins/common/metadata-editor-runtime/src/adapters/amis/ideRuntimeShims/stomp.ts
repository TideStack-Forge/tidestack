import { unsupported } from './unsupported'

export function isStompInitDestination(destination: string) {
  return destination.startsWith('/app/') && destination.endsWith('/init')
}

export function subscribeWithInitial() { return unsupported('platform messaging') }

export function subscribeTopic(destination: string, listener: () => void) {
  const match = destination.match(/^\/dev-helper\/([^/]+)$/)
  if (!match) return unsupported('platform messaging')
  let disposed = false
  let previous: string | undefined
  let timer: ReturnType<typeof setTimeout> | undefined
  const poll = async () => {
    try {
      const host = (window as any).OuroborosMetadataEditorHost
      if (!host?.service || !host.requestService) return
      const result = await host.requestService({ method: 'GET', url: '/api/dev/' + encodeURIComponent(match[1]) + '/code-intelligence/environment' })
      const identity = JSON.stringify(result)
      if (!disposed && previous !== undefined && identity !== previous) listener()
      previous = identity
    } catch (error) {
      if (!disposed && previous !== 'unavailable') { previous = 'unavailable'; listener() }
      if (!disposed) console.debug('Editor environment is unavailable', error)
    } finally { if (!disposed) timer = setTimeout(poll, 5000) }
  }
  void poll()
  return () => { disposed = true; clearTimeout(timer) }
}

export function subscribeQueue() { return unsupported('platform messaging') }

export function subscribeUserTopic() { return unsupported('platform messaging') }

export function subscribe() { return unsupported('platform messaging') }

export function unsubscribeTopic() {}

export function unsubscribeQueue() {}

export function unsubscribeUserTopic() {}

export function unsubscribe() {}

export function unsubscribeAll() {}

export function unsubscribeAllTopics() {}

export function unsubscribeAllQueues() {}

export function unsubscribeAllUserTopics() {}

export async function sendBinary() { return unsupported('platform messaging') }

export async function sendText() { return unsupported('platform messaging') }

export async function sendJson() { return unsupported('platform messaging') }

export async function send() { return unsupported('platform messaging') }

export async function connect() { return unsupported('platform messaging') }

export async function disconnect() {}

export async function reconnect() { return unsupported('platform messaging') }

export function onStompConnected() { return unsupported('platform messaging') }

export function onStompDisconnected() { return unsupported('platform messaging') }

export function isConnected() {
  return false
}

export function isActive() {
  return false
}

export function isStompAuthenticated() {
  return false
}

export default class StompClient {}
