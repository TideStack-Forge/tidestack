let __utils__ = {}

export function initPlatformUtils(utils) {
  __utils__ = utils
}

export function logout() {
  __utils__?.logoutHandler?.()
}

export function toast(message, type) {
  __utils__?.toast?.[type]?.(message)
}
export function registerIcon(key, component) {
  __utils__?.registerIcon?.(key, component)
}

export function getTheme() {
  return __utils__?.getTheme?.()
}

export function setTheme(theme) {
  __utils__?.setTheme?.(theme)
}

export function openOrSwitchTab(url) {
  __utils__?.openOrSwitchTab?.(url)
}