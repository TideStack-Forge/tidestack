import React from 'react'
import * as ReactDOM from 'react-dom'
import { FormItem, Renderer, OptionsControl, addRule, registerAction } from 'amis-core'
import { renderSchema, getPublicAssetUrl } from './platformAmisRuntime.js'
import { createRoot, type Root } from 'react-dom/client'
import { AlertComponent, type SchemaObject } from 'amis'
import { alert, confirm } from './ideRuntimeShims/modal'
import { Editor } from 'amis-editor'
import 'amis-editor-core/lib/style.css'
import './ouroborosAmisEditorPreset.js'
import type { EditorManager } from 'amis-editor-core'
import { installAmisEditorDragFallback } from './amisEditorDragFallback'
import {
  createSchemaChangeSourceTracker,
  type SchemaChangeSourceTracker,
} from './schemaKeyOrderBridge.js'
import { createVisualEditorChangeValue } from './visualEditorSchemaValue'

export interface AmisEditorRuntimeState {
  value: unknown
  theme: string
  locale: string
}

export interface AmisEditorRuntimeOptions extends AmisEditorRuntimeState {
  root: HTMLElement
  onChange: (value: unknown) => void
  onError?: (error: unknown) => void
  fetcher?: (api: unknown, data?: unknown) => Promise<any>
  dragFallback?: boolean | 'auto'
}

export interface AmisEditorRuntimeApp {
  applyState(state: AmisEditorRuntimeState): void
  setTheme(theme: string): void
  dispose(): void
}

declare global {
  interface Window {
    OuroborosAmisEditorRuntime?: {
      mount: typeof mountAmisEditor
    }
  }
}

function normalizeTheme(_theme: string): 'cxd' {
  return 'cxd'
}

function normalizeLocale(locale: string): string {
  return locale.trim() || 'zh-CN'
}

function getModalContainer(): HTMLElement {
  return document.body
}

interface AmisEditorLifecycleHandlers {
  onEditorMount: (manager: EditorManager) => void
  onEditorUnmount: (manager: EditorManager) => void
}

function renderEditor(
  state: AmisEditorRuntimeState,
  onChange: (value: unknown) => void,
  lifecycle: AmisEditorLifecycleHandlers,
  schemaChangeSource: SchemaChangeSourceTracker,
  fetcher?: AmisEditorRuntimeOptions['fetcher'],
): React.ReactElement {
  const amisEnv = { fetcher, getPublicAssetUrl, notify: () => undefined, alert, confirm, getModalContainer }
  return React.createElement(React.Fragment, null,
    React.createElement(AlertComponent, { locale: normalizeLocale(state.locale) }),
    React.createElement(Editor, {
    value: state.value as SchemaObject,
    onChange: (schema: unknown) => onChange(
      createVisualEditorChangeValue(state.value, schema, schemaChangeSource.consume()),
    ),
    theme: normalizeTheme(state.theme),
    appLocale: normalizeLocale(state.locale),
    autoFocus: true,
    className: 'ouroboros-amis-editor amis-scope',
    amisEnv,
    onEditorMount: lifecycle.onEditorMount,
    onEditorUnmount: lifecycle.onEditorUnmount,
    onBuildPanels: schemaChangeSource.decorateOnBuildPanels,
  }))
}

export function mountAmisEditor(options: AmisEditorRuntimeOptions): AmisEditorRuntimeApp {
  const reactRoot: Root = createRoot(options.root)
  let state: AmisEditorRuntimeState = {
    value: options.value,
    theme: normalizeTheme(options.theme),
    locale: normalizeLocale(options.locale),
  }
  let dragFallbackDispose: (() => void) | undefined
  const schemaChangeSource = createSchemaChangeSourceTracker()

  const lifecycle: AmisEditorLifecycleHandlers = {
    onEditorMount(manager) {
      dragFallbackDispose?.()
      dragFallbackDispose = installAmisEditorDragFallback({
        root: options.root,
        manager,
        enabled: options.dragFallback,
        onError: options.onError,
      })
    },
    onEditorUnmount() {
      dragFallbackDispose?.()
      dragFallbackDispose = undefined
    },
  }

  const render = (): void => {
    try {
      reactRoot.render(renderEditor(state, options.onChange, lifecycle, schemaChangeSource, options.fetcher))
    } catch (error) {
      options.onError?.(error)
    }
  }

  render()
  return {
    applyState(nextState) {
      state = {
        value: nextState.value,
        theme: normalizeTheme(nextState.theme),
        locale: normalizeLocale(nextState.locale),
      }
      render()
    },
    setTheme(theme) {
      state = { ...state, theme: normalizeTheme(theme) }
      render()
    },
    dispose() {
      dragFallbackDispose?.()
      dragFallbackDispose = undefined
      reactRoot.unmount()
    },
  }
}

const runtime = { mount: mountAmisEditor, renderSchema, FormItem, Renderer, OptionsControl, addRule, registerAction }

export const mount = mountAmisEditor

if (typeof window !== 'undefined') {
  window.OuroborosAmisEditorRuntime = runtime
  Object.assign(window, { React, ReactDOM })
}

export type AmisEditorRuntime = typeof runtime

export { renderSchema, FormItem, Renderer, OptionsControl, addRule, registerAction }
