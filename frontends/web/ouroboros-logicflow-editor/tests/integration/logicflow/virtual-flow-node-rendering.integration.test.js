import { afterEach, expect, test } from 'vitest'
import { Graph } from '@antv/x6'

import { createX6VirtualFlowNode } from '../../../src/node/x6Cells.js'
import { ensureSvgGeometryPolyfills } from './harness/ensureSvgGeometryPolyfills.mjs'

ensureSvgGeometryPolyfills()

let graph
let container

afterEach(() => {
  graph?.dispose()
  container?.remove()
})

test.each(['entry', 'errorEntry', 'return'])(
  'keeps the rendered %s label inside the node and clear of its icon',
  (kind) => {
    container = document.createElement('div')
    document.body.appendChild(container)
    graph = new Graph({ container, width: 600, height: 200, async: false })

    const node = graph.addNode(createX6VirtualFlowNode(kind, { x: 100, y: 50 }))
    const view = graph.findViewByCell(node)
    const label = view.findOne('label')
    const labelBounds = view.getBBoxOfElement(label)
    const nodeBounds = node.getBBox()

    expect(label.textContent).toBe(node.getData().label)
    expect(labelBounds.width).toBeGreaterThan(0)
    expect(labelBounds.x).toBeGreaterThan(nodeBounds.x + 38)
    expect(labelBounds.x + labelBounds.width).toBeLessThan(nodeBounds.x + nodeBounds.width)
    expect(labelBounds.y).toBeGreaterThanOrEqual(nodeBounds.y)
    expect(labelBounds.y + labelBounds.height).toBeLessThanOrEqual(nodeBounds.y + nodeBounds.height)
  }
)
