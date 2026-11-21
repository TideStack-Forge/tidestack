import React from 'react'
import { createRoot } from 'react-dom/client'
import { render, AlertComponent } from 'amis'
import { alert, confirm } from './ideRuntimeShims/modal'
import onigasmUrl from 'onigasm/lib/onigasm.wasm?url&inline'
import './ouroborosAmisEditorPreset.js'

export function getPublicAssetUrl(path) {
  if (path === 'vendor/onigasm.wasm') return onigasmUrl
  throw new Error('Unsupported IDE editor asset: ' + path)
}

// The IDE owns the React root; shared renderers come from the runtime preset.
export async function renderSchema(container, schema, props = {}, env = {}) {
  const root = createRoot(container)
  let scopeRef = null
  let disposed = false
  const handle = {
    getScopeRef: () => scopeRef,
    destroy() {
      disposed = true
      root.unmount()
      scopeRef = null
    },
    async update(nextSchema = schema, nextProps = props, nextEnv = env) {
      if (disposed) throw new Error('IDE schema renderer is disposed')
      schema = nextSchema
      props = nextProps
      env = nextEnv
      await renderOnce()
    },
  }
  function renderOnce() {
    return new Promise(resolve => {
      root.render(React.createElement(React.Fragment, null,
        render(schema, {
          ...props,
          scopeRef(ref) {
            scopeRef = ref
            props.scopeRef?.(ref)
            resolve()
          },
        }, { getPublicAssetUrl, alert, confirm, ...env }),
        React.createElement(AlertComponent)))
    })
  }
  await renderOnce()
  return handle
}
