import React, { createRef } from 'react'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import { expect, test, vi } from 'vitest'

import { LogicFlowEditorRenderer } from '../../../src/LogicFlowEditorRenderer.js'
import { initPlatformUtils } from '../../../src/utils/index.js'
import { createLinearFixture } from './harness/fixtures.mjs'
import { createMountMicroFrontendHarness } from './harness/platform/mountMicroFrontendHarness.mjs'
import { createFrontendTestPlatform } from './harness/platform/createFrontendTestPlatform.mjs'
import { createMockNodeDefines } from './harness/mockNodeDefines.mjs'
import { ensureSvgGeometryPolyfills } from './harness/ensureSvgGeometryPolyfills.mjs'

ensureSvgGeometryPolyfills()

function cloneValue(value) {
  return value == null ? value : JSON.parse(JSON.stringify(value))
}

function createNodeDefineFetcher(nodeDefines) {
  return async () => ({
    status: 0,
    data: nodeDefines,
  })
}

function resolveNodeName(node) {
  return node?.getData?.()?.name || node?.data?.name || null
}

function findNodeByName(graph, nodeName) {
  const node = (graph?.getNodes?.() || []).find((item) => resolveNodeName(item) === nodeName)
  if (!node) {
    throw new Error(`LogicFlow node not found: ${nodeName}`)
  }
  return node
}

function readNodePosition(node) {
  const position = node?.getPosition?.() || node?.position?.()
  return {
    x: position?.x ?? null,
    y: position?.y ?? null,
  }
}

async function waitFor(predicate, { timeoutMs = 4000, intervalMs = 20 } = {}) {
  const start = Date.now()
  while (Date.now() - start < timeoutMs) {
    const result = predicate()
    if (result) {
      return result
    }
    await new Promise((resolve) => setTimeout(resolve, intervalMs))
  }
  throw new Error('Timed out waiting for renderer integration condition')
}

async function createRendererHarness({
  initialValue = createLinearFixture(),
  onChange = () => {},
} = {}) {
  const platform = createFrontendTestPlatform()
  initPlatformUtils(platform.__utils__)

  const domHarness = createMountMicroFrontendHarness()
  const spriteNode = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
  spriteNode.setAttribute('id', '__SVG_SPRITE_NODE__')
  spriteNode.style.display = 'none'
  domHarness.container.appendChild(spriteNode)

  const mountTarget = document.createElement('div')
  mountTarget.style.width = '1440px'
  mountTarget.style.height = '900px'
  domHarness.container.appendChild(mountTarget)

  const reactRoot = createRoot(mountTarget)
  const rendererRef = createRef()
  const nodeDefines = createMockNodeDefines()

  let currentProps = {
    value: initialValue,
    onChange,
    formStore: {
      updatedAt: 1,
      data: {},
    },
    nodeDefineApi: 'mock://logicflow-editor-renderer/node-defines',
    disableBuiltIn: true,
    env: {
      fetcher: createNodeDefineFetcher(nodeDefines),
    },
    data: {
      appName: 'logicflow-editor-renderer-integration',
    },
  }

  async function flush(cycles = 4) {
    for (let index = 0; index < cycles; index += 1) {
      await domHarness.flush()
    }
  }

  async function render(nextProps = {}) {
    currentProps = {
      ...currentProps,
      ...nextProps,
    }
    await act(async () => {
      reactRoot.render(React.createElement(LogicFlowEditorRenderer, { ref: rendererRef, ...currentProps }))
      await Promise.resolve()
    })
    await flush(4)
  }

  async function destroy() {
    await act(async () => {
      await rendererRef.current?.editorRef?.current?.disposePropertiesPanel?.()
      await Promise.resolve()
    })
    await flush(2)
    await act(async () => {
      reactRoot.unmount()
      await Promise.resolve()
    })
    await domHarness.destroy()
  }

  await render()
  await waitFor(() => rendererRef.current?.editorRef?.current?.getGraph?.())
  await waitFor(() => {
    const graph = rendererRef.current?.editorRef?.current?.getGraph?.()
    return (graph?.getNodes?.().length || 0) >= (initialValue?.nodes?.length || 0)
  })

  return {
    render,
    destroy,
    getRenderer() {
      return rendererRef.current
    },
    getGraph() {
      return rendererRef.current?.editorRef?.current?.getGraph?.() || null
    },
    getCurrentProps() {
      return currentProps
    },
  }
}

test('renderer accepts application debug hosts without appName, isolates draft changes and restores editing when disabled', async () => {
  const harness = await createRendererHarness()
  let completeOldStart
  const oldStart = new Promise((resolve) => { completeOldStart = resolve })
  const dispose = vi.fn()
  function debugHost(key, start) {
    return { key, api: {
      targets: async () => [{ runtimeId: key, label: key, supported: true }],
      captures: async () => [],
      start,
      read: async () => ({ captureId: key, state: 'ACTIVE', runs: [] }),
      renew: vi.fn(),
    }, subscribe: vi.fn(() => dispose) }
  }
  const first = debugHost('draft:1', () => oldStart)
  const second = debugHost('draft:2', async () => ({ captureId: 'draft:2', state: 'ACTIVE', createdAt: 1000, runs: [] }))
  const graph = harness.getGraph()
  const cells = graph.getCells()
  const container = harness.getRenderer().reactContainer.current
  const button = (label) => [...container.querySelectorAll('button')].find((item) => item.getAttribute('aria-label') === label || item.textContent.trim() === label)
  async function click(label) {
    expect(button(label), label).toBeTruthy()
    await act(async () => { button(label).click() })
  }
  const env = harness.getCurrentProps().env
  try {
    await harness.render({ data: {}, env: { ...env, logicFlowDebug: first } })
    expect(button('在线调试').disabled).toBe(false)
    await click('在线调试')
    await waitFor(() => button('开始记录') && !button('开始记录').disabled)
    await click('开始记录')
    await harness.render({ env: { ...env, logicFlowDebug: second } })
    await act(async () => { completeOldStart({ captureId: 'draft:1', state: 'ACTIVE', runs: [] }) })
    await waitFor(() => button('开始记录') && !button('开始记录').disabled)
    expect(container.querySelector('.logicflow-debug-target select').value).toBe('draft:2')
    expect(first.subscribe).not.toHaveBeenCalled()
    await click('开始记录')
    expect(second.subscribe).toHaveBeenCalledWith('draft:2', expect.any(Function))
    await harness.render({ env: { ...env, logicFlowDebug: false } })
    expect(container.querySelector('[aria-label="逻辑流在线调试"]')).toBeNull()
    expect(button('在线调试')).toBeUndefined()
    expect(dispose).toHaveBeenCalledTimes(1)
    expect(harness.getGraph()).toBe(graph)
    expect(graph.getCells()).toEqual(cells)
    expect(graph.options.interacting.nodeMovable).toBe(true)
    await new Promise((resolve) => setTimeout(resolve, 1100))
    expect(second.api.renew).not.toHaveBeenCalled()
  } finally {
    await harness.destroy()
  }
})

test('renderer validate flushes pending position persistence changes into the current flow value', async () => {
  const emittedValues = []
  const harness = await createRendererHarness({
    onChange(value) {
      emittedValues.push(cloneValue(value))
    },
  })

  try {
    const renderer = harness.getRenderer()
    const graph = harness.getGraph()
    const node = findNodeByName(graph, '脚本节点')

    graph.setLogicFlowPositionPersistenceEnabled?.(true)
    node.position(620, 240)

    const validationMessage = await renderer.validate()

    expect(validationMessage).toBeUndefined()

    await waitFor(() => renderer.store?.state?.flowData?.nodePositions?.['脚本节点'])

    const expectedPosition = readNodePosition(node)
    expect(renderer.store.state.flowData.nodePositions['脚本节点']).toEqual(expectedPosition)
    expect(emittedValues.at(-1)?.nodePositions?.['脚本节点']).toEqual(expectedPosition)
  } finally {
    await harness.destroy()
  }
})

test('inspector edits are included when validating immediately', async () => {
  const emitted = []
  const harness = await createRendererHarness({ onChange: value => emitted.push(cloneValue(value)) })
  try {
    const renderer = harness.getRenderer()
    const editor = renderer.editorRef.current
    const node = findNodeByName(harness.getGraph(), '脚本节点')
    await act(async () => { await editor.showNodeProperties(node) })
    const form = await waitFor(() => editor.getPropertiesPanelScope()?.getScopeRef?.()
      ?.getComponentByName?.('propertyForm'))
    await act(async () => {
      form.handleChange('pending inspector edit', 'description')
      await renderer.validate()
    })
    expect(node.getData().description).toBe('pending inspector edit')
    expect(emitted.at(-1).nodes.find(item => item.name === '脚本节点').description).toBe('pending inspector edit')
  } finally {
    await harness.destroy()
  }
})

test('renderer keeps local node positions when formStore updates without a new external value', async () => {
  const initialValue = createLinearFixture()
  const harness = await createRendererHarness({
    initialValue,
  })

  try {
    const renderer = harness.getRenderer()
    const graph = harness.getGraph()
    const node = findNodeByName(graph, '脚本节点')

    graph.setLogicFlowPositionPersistenceEnabled?.(true)
    node.position(680, 280)
    graph.flushLogicFlowChange?.()

    await waitFor(() => renderer.store?.state?.flowData?.nodePositions?.['脚本节点'])

    const expectedPosition = readNodePosition(node)

    await harness.render({
      formStore: {
        updatedAt: 2,
        data: {},
      },
    })

    const nextNode = findNodeByName(harness.getGraph(), '脚本节点')
    expect(readNodePosition(nextNode)).toEqual(expectedPosition)
    expect(renderer.store.state.flowData.nodePositions['脚本节点']).toEqual(expectedPosition)
    expect(harness.getCurrentProps().value).toBe(initialValue)
  } finally {
    await harness.destroy()
  }
})
