export function subscribeMenuExtension(_listener: () => void): () => void {
  return () => undefined
}

export function getMenuBadgeForItem(_item?: unknown): undefined {
  return undefined
}

export function getRegisteredUserMenuItems(): never[] {
  return []
}

export const menu = {
  setMenuBadge: (_ref: string, _badge: unknown) => undefined,
  getMenuBadge: (_ref: string) => undefined,
  getMenu: (_ref: string) => undefined,
  getMenus: (_options?: unknown) => [],
  traverseMenus: (_visitor: unknown, _options?: unknown) => undefined,
  addMenuItem: (_options: unknown) => false,
  removeMenuItem: (_ref: string) => false,
}

export const userMenu = {
  register: (_item: unknown, _owner?: string) => '',
  unregister: (_id: string) => undefined,
  getItems: getRegisteredUserMenuItems,
  unregisterAll: (_owner: string) => undefined,
}
