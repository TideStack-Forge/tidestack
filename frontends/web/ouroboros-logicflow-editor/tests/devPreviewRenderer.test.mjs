import test from 'node:test'
import assert from 'node:assert/strict'

import { createPreviewAmisRenderer } from '../src/devPreview/previewRenderer.mjs'

test('createPreviewAmisRenderer writes preview content into the provided container', async () => {
  const container = { innerHTML: '' }
  const renderPreview = createPreviewAmisRenderer()

  const scoped = await renderPreview(container, {}, {
    data: {
      name: '条件分支',
      exitNode: '成功返回',
    },
  })

  assert.match(container.innerHTML, /Preview Mode/)
  assert.match(container.innerHTML, /条件分支/)
  assert.match(container.innerHTML, /成功返回/)

  scoped.destroy()
  assert.equal(container.innerHTML, '')
})
