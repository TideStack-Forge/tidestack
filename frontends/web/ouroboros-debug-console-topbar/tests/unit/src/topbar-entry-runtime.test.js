import assert from 'node:assert/strict'
import fs from 'node:fs'
import path from 'node:path'
import vm from 'node:vm'

import { transformSync } from '@babel/core'
import { expect, test, vi } from 'vitest'

const packageRoot = path.resolve(__dirname, '..', '..', '..')

function loadEntryRuntime({ createRoot, documentRef = {} }) {
  const entryPath = path.join(packageRoot, 'src/main.js')
  const source = fs.readFileSync(entryPath, 'utf8')
  let capturedOptions = null

  const { code } = transformSync(source, {
    filename: entryPath,
    babelrc: false,
    configFile: false,
    presets: [
      ['@babel/preset-env', { modules: 'commonjs', targets: { node: 'current' } }],
      '@babel/preset-react',
    ],
  })

  const module = { exports: {} }
  const context = {
    module,
    exports: module.exports,
    require(specifier) {
      if (specifier === 'react') {
        return {
          createElement(type, props) {
            return { type, props }
          },
        }
      }

      if (specifier === 'ouroboros-sdk') {
        return {
          initLayoutComponent(options) {
            capturedOptions = options
            return {
              bootstrap: vi.fn(),
              mount: vi.fn((props) => options.render(props)),
              update: vi.fn((props) => options.afterUpdate(props)),
              unmount: vi.fn((props) => options.afterUnmount(props)),
            }
          },
        }
      }

      if (specifier === 'react-dom') {
        return { createRoot }
      }

      if (specifier === './TopBar.jsx') {
        return {
          __esModule: true,
          default(props) {
            return { component: 'TopBar', props }
          },
        }
      }

      if (specifier === './utils/index.js') {
        return {
          initPlatformUtils: vi.fn(() => Promise.resolve()),
        }
      }

      if (specifier === './utils/registerIcons.js') {
        return {
          __esModule: true,
          default: vi.fn(() => Promise.resolve()),
        }
      }

      throw new Error(`Unexpected dependency in topbar entry runtime test: ${specifier}`)
    },
    console,
    document: documentRef,
  }

  vm.runInNewContext(code, context, { filename: entryPath })

  assert.ok(capturedOptions, 'initLayoutComponent should receive the entry lifecycle options')

  return {
    entry: module.exports,
    lifecycle: capturedOptions,
  }
}

test('topbar updates reuse the mounted React root across route changes', () => {
  const renderSpy = vi.fn()
  const unmountSpy = vi.fn()
  const mountContainer = { id: 'custom-topbar-container' }
  const hostContainer = {
    querySelector: vi.fn(() => mountContainer),
  }
  const createRoot = vi.fn(() => ({
    render: renderSpy,
    unmount: unmountSpy,
  }))

  const { lifecycle } = loadEntryRuntime({ createRoot })
  const app = lifecycle.render({
    container: hostContainer,
    currentPath: '/dev/study-project/business-model',
    appName: 'Workspace Shell',
  })

  expect(createRoot).toHaveBeenCalledTimes(1)
  expect(createRoot).toHaveBeenCalledWith(mountContainer)
  expect(renderSpy).toHaveBeenCalledTimes(1)

  lifecycle.afterUpdate({
    container: hostContainer,
    currentPath: '/user-dashboard?page=1',
    appName: 'Platform Shell',
  })

  expect(createRoot).toHaveBeenCalledTimes(1)
  expect(unmountSpy).not.toHaveBeenCalled()
  expect(renderSpy).toHaveBeenCalledTimes(2)

  lifecycle.afterUnmount({}, app)

  expect(unmountSpy).toHaveBeenCalledTimes(1)
})

test('topbar update does not recreate the root when the mount container disappears mid-transition', () => {
  const renderSpy = vi.fn()
  const unmountSpy = vi.fn()
  const mountContainer = { id: 'custom-topbar-container' }
  const hostContainer = {
    querySelector: vi.fn().mockReturnValueOnce(mountContainer).mockReturnValueOnce(null),
  }
  const createRoot = vi.fn(() => ({
    render: renderSpy,
    unmount: unmountSpy,
  }))

  const { lifecycle } = loadEntryRuntime({ createRoot })
  const app = lifecycle.render({
    container: hostContainer,
    currentPath: '/dev/study-project/business-model',
    appName: 'Workspace Shell',
  })

  expect(createRoot).toHaveBeenCalledTimes(1)
  expect(renderSpy).toHaveBeenCalledTimes(1)
  expect(() => {
    lifecycle.afterUpdate({
      container: hostContainer,
      currentPath: '/user-dashboard?page=1',
      appName: 'Platform Shell',
    })
  }).not.toThrow()
  expect(createRoot).toHaveBeenCalledTimes(1)
  expect(unmountSpy).not.toHaveBeenCalled()
  expect(renderSpy).toHaveBeenCalledTimes(1)

  lifecycle.afterUnmount({}, app)

  expect(unmountSpy).toHaveBeenCalledTimes(1)
})

test('topbar mount creates the host mount container when qiankun host has not injected one yet', () => {
  const renderSpy = vi.fn()
  const unmountSpy = vi.fn()
  const appended = []
  const hostContainer = {
    querySelector: vi.fn(() => null),
    appendChild: vi.fn((node) => {
      appended.push(node)
    }),
  }
  const documentRef = {
    createElement: vi.fn(() => ({ id: '', dataset: {} })),
  }
  const createRoot = vi.fn(() => ({
    render: renderSpy,
    unmount: unmountSpy,
  }))

  const { lifecycle } = loadEntryRuntime({ createRoot, documentRef })

  lifecycle.render({
    container: hostContainer,
    currentPath: '/dev/study-project/business-model',
    appName: 'Workspace Shell',
  })

  expect(documentRef.createElement).toHaveBeenCalledWith('div')
  expect(hostContainer.appendChild).toHaveBeenCalledTimes(1)
  expect(appended[0].id).toBe('custom-topbar-container')
  expect(createRoot).toHaveBeenCalledWith(appended[0])
  expect(renderSpy).toHaveBeenCalledTimes(1)
  expect(unmountSpy).not.toHaveBeenCalled()
})
