import React from 'react'
import { initLayoutComponent } from 'ouroboros-sdk'
import TopBar from './TopBar.jsx'
import { createRoot } from 'react-dom'
import { initPlatformUtils } from './utils/index.js'
import registerIcons from './utils/registerIcons.js'

let root
let rootContainer

function resolveMountContainer(container) {
  const host = container || document
  if (!host || typeof host.querySelector !== 'function') {
    return null
  }

  const mountContainer = host.querySelector('#custom-topbar-container')
  if (mountContainer) {
    return mountContainer
  }

  if (host !== document && typeof host.appendChild === 'function') {
    const nextContainer = document.createElement('div')
    nextContainer.id = 'custom-topbar-container'
    host.appendChild(nextContainer)
    return nextContainer
  }

  return null
}

function render(props) {
  const { container, ...restProps } = props
  const mountContainer = resolveMountContainer(container)

  if (!mountContainer) {
    return root
  }

  if (root && rootContainer !== mountContainer) {
    root.unmount?.()
    root = null
    rootContainer = null
  }

  if (!root) {
    root = createRoot(mountContainer)
    rootContainer = mountContainer
  }

  root.render(<TopBar {...restProps} />)
  return root
}
// --------- sdk导出生命周期------------ //

const { bootstrap, mount, update, unmount } = initLayoutComponent({
  render,
  async beforeMount(props) {
    // 初始化平台工具
    await initPlatformUtils(props.__utils__)
    await registerIcons()
  },
  afterUpdate(props) {
    render(props)
  },
  afterUnmount() {
    root?.unmount?.()
    root = null
    rootContainer = null
  },
})
export { bootstrap, mount, update, unmount }
