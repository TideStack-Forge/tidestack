import React, { act } from 'react'
import { createRoot } from 'react-dom/client'
import {
  render as renderAmis,
  registerIcon as registerAmisIcon,
  extractObjectChain,
  createObjectFromChain,
} from 'amis'
import { utils as amisCoreUtils } from 'amis-core'

function createImmediateIntersectionObserver() {
  return class ImmediateIntersectionObserver {
    constructor(callback) {
      this.callback = callback
    }

    observe(target) {
      this.callback?.(
        [
          {
            isIntersecting: true,
            intersectionRatio: 1,
            target,
          },
        ],
        this
      )
    }

    unobserve() {}

    disconnect() {}

    takeRecords() {
      return []
    }
  }
}

function createNoopResizeObserver() {
  return class NoopResizeObserver {
    observe() {}

    unobserve() {}

    disconnect() {}
  }
}

function ensureTestBrowserPolyfills() {
  const view = globalThis.window

  globalThis.IS_REACT_ACT_ENVIRONMENT = true

  if (typeof globalThis.IntersectionObserver === 'undefined') {
    globalThis.IntersectionObserver = createImmediateIntersectionObserver()
  }
  if (view && typeof view.IntersectionObserver === 'undefined') {
    view.IntersectionObserver = globalThis.IntersectionObserver
  }

  if (typeof globalThis.ResizeObserver === 'undefined') {
    globalThis.ResizeObserver = createNoopResizeObserver()
  }
  if (view && typeof view.ResizeObserver === 'undefined') {
    view.ResizeObserver = globalThis.ResizeObserver
  }

  if (view && typeof view.matchMedia === 'undefined') {
    view.matchMedia = () => ({
      matches: false,
      media: '',
      onchange: null,
      addListener() {},
      removeListener() {},
      addEventListener() {},
      removeEventListener() {},
      dispatchEvent() {
        return false
      },
    })
  }
}

function normalizeSchema(schema) {
  if (Array.isArray(schema)) {
    return {
      type: 'page',
      body: schema,
    }
  }
  return schema
}

async function settleRender(containerEl, attempts = 100, delayMs = 10) {
  for (let index = 0; index < attempts; index += 1) {
    await Promise.resolve()
    await new Promise((resolve) => setTimeout(resolve, delayMs))

    if (
      containerEl.childNodes.length &&
      !containerEl.querySelector('[data-testid="spinner"]')
    ) {
      return
    }
  }
}

export function createAmisTestRenderer() {
  ensureTestBrowserPolyfills()
  const mountedRoots = new WeakMap()
  const rootUnmountPromises = new WeakMap()

  async function renderAmisSchema(containerEl, schema, props = {}, env = {}) {
    const root = mountedRoots.get(containerEl) || createRoot(containerEl)
    mountedRoots.set(containerEl, root)

    let scopeRef = null
    const normalizedSchema = normalizeSchema(schema)
    const dataChain = extractObjectChain(props?.data || {})
    const renderProps = {
      ...(props || {}),
      data: createObjectFromChain(dataChain),
      scopeRef(ref) {
        scopeRef = ref
        props?.scopeRef?.(ref)
      },
    }
    const renderEnv = {
      ...(env || {}),
    }

    await act(async () => {
      root.render(renderAmis(normalizedSchema, renderProps, renderEnv))
      await new Promise((resolve) => setTimeout(resolve, 0))
    })
    await settleRender(containerEl)

    return {
      getScopeRef() {
        return scopeRef
      },
      async update(nextSchema = normalizedSchema, nextProps = renderProps, nextEnv = renderEnv) {
        await act(async () => {
          root.render(renderAmis(normalizeSchema(nextSchema), nextProps, nextEnv))
          await new Promise((resolve) => setTimeout(resolve, 0))
        })
        await settleRender(containerEl)
      },
      async destroy() {
        const pendingUnmount = rootUnmountPromises.get(containerEl)
        if (pendingUnmount) {
          await pendingUnmount
          return
        }

        const unmountPromise = (async () => {
          await act(async () => {
            root.unmount()
            await new Promise((resolve) => setTimeout(resolve, 0))
          })
        })()

        rootUnmountPromises.set(containerEl, unmountPromise)
        await unmountPromise
        rootUnmountPromises.delete(containerEl)
        mountedRoots.delete(containerEl)
      },
    }
  }

  return {
    amisUtils: amisCoreUtils,
    registerIcon: registerAmisIcon,
    renderAmisSchema,
  }
}
