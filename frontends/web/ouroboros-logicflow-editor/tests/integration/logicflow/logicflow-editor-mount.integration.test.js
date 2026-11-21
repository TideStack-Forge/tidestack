import React, { createRef } from 'react'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { expect, test } from 'vitest'

import LogicFlowEditor from '../../../src/logicFlowEditor/index.jsx'
import Canvas from '../../../src/logicFlowEditor/components/canvas/index.jsx'
import createStoreFn from '../../../src/store/index.js'
import { initPlatformUtils } from '../../../src/utils/index.js'
import { createLinearFixture } from './harness/fixtures.mjs'
import { createMockNodeDefines } from './harness/mockNodeDefines.mjs'
import { ensureSvgGeometryPolyfills } from './harness/ensureSvgGeometryPolyfills.mjs'
import { createFrontendTestPlatform } from './harness/platform/createFrontendTestPlatform.mjs'

ensureSvgGeometryPolyfills()

test('Canvas applies readonly interaction rules when asynchronous data first creates the graph and restores editing', async () => {
  const host = document.createElement('div')
  document.body.appendChild(host)
  const root = createRoot(host)
  const ref = createRef()
  const nodeDefines = createMockNodeDefines()
  const data = createLinearFixture()
  try {
    await act(async () => { root.render(React.createElement(Canvas, { ref, nodeDefines, isReadOnly: true })) })
    expect(ref.current.getGraph()).toBeNull()
    await act(async () => { root.render(React.createElement(Canvas, { ref, nodeDefines, data, isReadOnly: true })) })
    const graph = ref.current.getGraph()
    expect(graph.options.interacting).toBe(false)
    expect(graph.options.connecting.allowPort).toBe(false)
    expect(graph.getPlugin('selection').isEnabled()).toBe(false)
    await act(async () => { root.render(React.createElement(Canvas, { ref, nodeDefines, data, isReadOnly: false })) })
    expect(ref.current.getGraph()).toBe(graph)
    expect(graph.options.interacting.nodeMovable).toBe(true)
    expect(graph.options.connecting.allowPort).toBe(true)
    expect(graph.getPlugin('selection').isEnabled()).toBe(true)
  } finally {
    await act(async () => { root.unmount() })
    host.remove()
  }
})

function waitFor(predicate, { timeoutMs = 4000, intervalMs = 20, label = 'condition' } = {}) {
  const start = Date.now()
  return new Promise((resolve, reject) => {
    const tick = () => {
      try {
        const result = predicate()
        if (result) {
          resolve(result)
          return
        }
        if (Date.now() - start >= timeoutMs) {
          reject(new Error(`Timed out waiting for direct editor mount ${label}`))
          return
        }
        setTimeout(tick, intervalMs)
      } catch (error) {
        reject(error)
      }
    }
    tick()
  })
}

test('LogicFlowEditor hydrates its graph and reuses its cells, viewport and history across debug mode', async () => {
  const platform = createFrontendTestPlatform()
  initPlatformUtils(platform.__utils__)

  const nodeDefines = createMockNodeDefines()
  const store = createStoreFn(createLinearFixture())
  const editorRef = createRef()

  const mountTarget = document.createElement('div')
  mountTarget.style.width = '1440px'
  mountTarget.style.height = '900px'
  Object.defineProperty(mountTarget, 'clientWidth', { value: 1440, configurable: true })
  Object.defineProperty(mountTarget, 'clientHeight', { value: 900, configurable: true })
  document.body.appendChild(mountTarget)

  const spriteNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  spriteNode.setAttribute('id', '__SVG_SPRITE_NODE__')
  spriteNode.style.display = 'none'
  document.body.appendChild(spriteNode)

  const root = createRoot(mountTarget)

  try {
    await act(async () => {
      root.render(
        React.createElement(LogicFlowEditor, {
          ref: editorRef,
          onChange(value) {
            store.commit('changeFlowData', value)
          },
          getProps() {
            return {
              nodeDefineApi: 'mock://logicflow-editor/node-defines',
              disableBuiltIn: true,
              env: {
                fetcher: async ({ url }) => ({ status: 0, data: url.includes('/logicflow/debug/') ? { items: [] } : nodeDefines }),
              },
              data: {
                appName: 'logicflow-editor-integration',
              },
              store,
            }
          },
          rootData: {
            appName: 'logicflow-editor-integration',
          },
        })
      )
      await Promise.resolve()
    })

    await waitFor(() => editorRef.current?.getGraph?.(), { label: 'graph handle' })
    const graph = editorRef.current.getGraph()
    await waitFor(() => graph.getNodes().length > 0, { label: 'initial graph nodes' })

    expect(graph.getNodes().map((node) => node.getData()?.name)).toEqual([
      'VirtualEntryNode',
      '入口节点',
      '脚本节点',
      '返回节点',
    ])
    expect(graph.getEdges()).toHaveLength(3)
    const cells = graph.getCells()
    const flow = JSON.stringify(editorRef.current.readFlowData())
    const canvas = mountTarget.querySelector('.logicflow-canvas-container')
    graph.zoomTo(0.85)
    graph.setScrollbarPosition(180, 90)
    const zoom = graph.zoom()
    const scrollbar = graph.getScrollbarPosition()
    const translation = graph.translate()
    const node = graph.getNodes().find((cell) => cell.getData().name === '脚本节点')
    const position = node.position()
    await act(async () => { node.position(position.x + 24, position.y) })
    expect(graph.canUndo()).toBe(true)
    const button = (label) => mountTarget.querySelector(`button[aria-label="${label}"]`) || [...mountTarget.querySelectorAll('button')].find((item) => item.textContent === label)
    await act(async () => { button('在线调试').click() })
    expect(editorRef.current.getGraph()).toBe(graph)
    expect(graph.getCells()).toEqual(cells)
    expect(mountTarget.querySelector('.logicflow-canvas-container')).toBe(canvas)
    expect(graph.zoom()).toBe(zoom)
    expect(graph.getScrollbarPosition()).toEqual(scrollbar)
    expect(graph.translate()).toEqual(translation)
    expect(graph.options.interacting).toBe(false)
    expect(graph.options.connecting.allowPort).toBe(false)
    expect(graph.getPlugin('selection').isEnabled()).toBe(false)
    expect(graph.getPlugin('logicflow').isEnabled()).toBe(false)
    expect(node.position()).toEqual({ x: position.x + 24, y: position.y })
    await waitFor(() => button('返回编辑'), { label: 'lazy debug workbench' })
    await act(async () => { button('返回编辑').click() })
    expect(editorRef.current.getGraph()).toBe(graph)
    expect(graph.options.interacting.nodeMovable).toBe(true)
    expect(graph.getPlugin('selection').isEnabled()).toBe(true)
    expect(graph.getPlugin('logicflow').isEnabled()).toBe(true)
    expect(JSON.stringify(editorRef.current.readFlowData())).toBe(flow)
    await act(async () => { await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))) })
    expect(graph.zoom()).toBe(zoom)
    expect(graph.getScrollbarPosition()).toEqual(scrollbar)
    expect(graph.translate()).toEqual(translation)
    await act(async () => { button('在线调试').click() })
    await waitFor(() => button('返回编辑'), { label: 'lazy debug workbench' })
    await act(async () => { button('返回编辑').click() })
    expect(editorRef.current.getGraph()).toBe(graph)
    expect(graph.getScrollbarPosition()).toEqual(scrollbar)
    expect(graph.translate()).toEqual(translation)
    await act(async () => { graph.undo() })
    expect(node.position()).toEqual(position)
    const editor = mountTarget.querySelector('.wrapper-editor-container')
    await act(async () => { button('节点面板').click() })
    expect(editor.style.getPropertyValue('--logicflow-toolbar-left')).toBe('16px')
    await act(async () => { button('在线调试').click() })
    expect(editor.style.getPropertyValue('--logicflow-toolbar-left')).toBe('272px')
    expect(graph.getScrollbarPosition()).toEqual(scrollbar)
    await waitFor(() => button('返回编辑'), { label: 'lazy debug workbench' })
    await act(async () => { button('返回编辑').click() })
    expect(editor.style.getPropertyValue('--logicflow-toolbar-left')).toBe('16px')
    expect(mountTarget.querySelector('[data-testid="logicflow-panel-left"]').getAttribute('data-panel-visible')).toBe('false')
    expect(graph.getScrollbarPosition()).toEqual(scrollbar)
  } finally {
    await editorRef.current?.disposePropertiesPanel?.()
    root.unmount()
    mountTarget.remove()
    spriteNode.remove()
  }
})

test('LogicFlowEditor hydrates a blank graph after async node defines and flow data arrive together', async () => {
  const platform = createFrontendTestPlatform()
  initPlatformUtils(platform.__utils__)

  let nodeDefines = []
  const store = createStoreFn()
  const editorRef = createRef()

  const mountTarget = document.createElement('div')
  mountTarget.style.width = '1440px'
  mountTarget.style.height = '900px'
  Object.defineProperty(mountTarget, 'clientWidth', { value: 1440, configurable: true })
  Object.defineProperty(mountTarget, 'clientHeight', { value: 900, configurable: true })
  document.body.appendChild(mountTarget)

  const spriteNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  spriteNode.setAttribute('id', '__SVG_SPRITE_NODE__')
  spriteNode.style.display = 'none'
  document.body.appendChild(spriteNode)

  const root = createRoot(mountTarget)

  try {
    await act(async () => {
      root.render(
        React.createElement(LogicFlowEditor, {
          ref: editorRef,
          onChange(value) {
            store.commit('changeFlowData', value)
          },
          getProps() {
            return {
              nodeDefineApi: 'mock://logicflow-editor/node-defines',
              disableBuiltIn: true,
              env: {
                fetcher: async () => ({ status: 0, data: nodeDefines }),
              },
              data: {
                appName: 'logicflow-editor-integration',
              },
              store,
            }
          },
          rootData: {
            appName: 'logicflow-editor-integration',
          },
        })
      )
      await Promise.resolve()
    })

    await waitFor(() => editorRef.current?.getGraph?.(), { label: 'graph handle' })
    const graph = editorRef.current.getGraph()
    await waitFor(() => graph.getNodes().length === 0, { label: 'blank initial graph' })
    expect(graph.getPlugin('logicflow').data).toMatchObject({
      name: '',
      nodes: [],
      connections: [],
    })

    nodeDefines = createMockNodeDefines()

    await act(async () => {
      store.commit('changeFlowData', createLinearFixture())
      await Promise.resolve()
    })

    await waitFor(() => graph.getNodes().length > 0, { label: 'rehydrated graph nodes' })
    expect(graph.getPlugin('logicflow').data).toMatchObject({
      name: 'logicflow-editor-integration',
      nodes: expect.any(Array),
      connections: expect.any(Array),
    })

    expect(graph.getNodes().map((node) => node.getData()?.name)).toEqual([
      'VirtualEntryNode',
      '入口节点',
      '脚本节点',
      '返回节点',
    ])
    expect(graph.getEdges()).toHaveLength(3)
  } finally {
    await editorRef.current?.disposePropertiesPanel?.()
    root.unmount()
    mountTarget.remove()
    spriteNode.remove()
  }
})
