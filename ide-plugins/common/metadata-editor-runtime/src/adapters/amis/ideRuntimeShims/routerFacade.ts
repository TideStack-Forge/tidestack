import { unsupported } from './unsupported'
export function pushLocation(_to: string) { return unsupported('platform navigation') }
export function replaceLocation(_to: string) { return unsupported('platform navigation') }
export function readCurrentRoute() { return { params: {}, query: {} } }
export function getRouter() {
  return { currentRoute: readCurrentRoute(), push: pushLocation, replace: replaceLocation }
}
export function subscribeRouteChanges() { return unsupported('platform route subscriptions') }
