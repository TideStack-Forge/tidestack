import { describe, expect, test } from 'vitest'
import { transpile } from '../../../src/property-transpiler/transpiler.js'
import '../../../src/property-transpiler/transpilers/notification-select.js'
import '../../../src/property-transpiler/transpilers/configuration-select.js'
import '../../../src/property-transpiler/transpilers/script-editor.js'
import '../../../src/property-transpiler/transpilers/flow-select.js'
import '../../../src/property-transpiler/transpilers/mq-binding-select.js'
import '../../../src/property-transpiler/transpilers/data-model.js'
import '../../../src/property-transpiler/transpilers/rpc-model.js'
import '../../../src/property-transpiler/transpilers/fieldSet.js'
import '../../../src/property-transpiler/transpilers/combo.js'

describe('registered property transpilers preserve host-provided API schemas', () => {
  const sources = [
    ['notification-select', '/api/dev/${appName}/metadata/notification-template'],
    ['configuration-select', '/api/dev/${appName}/metadata/configuration'],
    ['flow-select', '/api/dev/${appName}/metadata/logic-flow'],
    ['data-model', '/api/dev/${appName}/metadata/data-model'],
    ['rpc-model', '/api/dev/${appName}/metadata/rpc-model?mode=raw'],
    ['mq-binding-select', '/api/dev/${appName}/mq-binding?bindingType=consumer'],
  ]
  test.each(sources)('%s keeps its development source when the definition has no override', (type, url) => {
    const result = transpile({ type, name: 'selected', bindingType: 'consumer' })
    expect(result.type).toBe(type === 'rpc-model' ? 'picker' : 'select')
    expect(typeof result.source === 'string' ? result.source : result.source.url).toBe(url)
  })
  test.each(sources)('%s accepts an application API schema with its own response mapping', (type) => {
    const source = { method: 'post', url: '/application/drafts/${draftId}/options', data: { revision: '${revision}' }, responseData: { items: '${options}' } }
    const definition = { type, name: 'selected', source }
    const result = transpile(definition)
    expect(result.source).toBe(source)
    expect(result.name).toBe('selected')
    expect(definition.type).toBe(type)
  })
  test('script intelligence uses the supplied application endpoint and scene', () => {
    const result = transpile({ type: 'script-editor', intelligenceApi: '/application/drafts/42/intelligence', intelligenceScene: 'draft-script' })
    expect(result.intelligenceApi).toBe('/application/drafts/42/intelligence')
    expect(result.intelligenceScene).toBe('draft-script')
  })
  test('RPC browsing preserves name values and supports explicit source field mappings', () => {
    const model = transpile({ type: 'rpc-model', name: 'model' })
    expect(model).toMatchObject({ type: 'picker', valueField: 'name', labelField: 'title' })
    const custom = transpile({ type: 'rpc-model', name: 'model', source: '/custom/models', valueField: 'id', labelField: 'caption', pickerSchema: { perPage: 20 } })
    expect(custom).toMatchObject({ source: '/custom/models', valueField: 'id', labelField: 'caption', pickerSchema: { perPage: 20 } })
    expect(custom.pickerSchema.columns.map((column) => column.name)).toEqual(['caption', 'id', 'group'])
    expect(transpile({ type: 'rpc-model', source: '/custom/models' })).toMatchObject({ valueField: 'value', labelField: 'label' })
  })
  test('RPC native picker schema searches within a subtree and respects mapped fields', () => {
    const { pickerSchema } = transpile({ type: 'rpc-model', valueField: 'id', labelField: 'caption' })
    const filter = new Function('items', 'itemsRaw', 'options', pickerSchema.matchFunc)
    const items = [
      { id: 'member', caption: '读取成员', group: '钉钉/通讯录' },
      { id: 'message', caption: '发送消息', group: '钉钉/消息' },
      { id: 'other', caption: '其他', group: '钉钉其他' },
      { id: 'legacy', caption: '旧接口' },
    ]
    const select = (query) => filter(items, items, { query }).map((item) => item.id)
    expect(select({ __rpcGroup: 'group:钉钉' })).toEqual(['member', 'message'])
    expect(select({ __rpcGroup: 'group:钉钉/通讯录' })).toEqual(['member'])
    expect(select({ __rpcGroup: 'group:钉钉', __rpcKeyword: ' MEMBER ' })).toEqual(['member'])
    expect(select({ __rpcGroup: 'group:钉钉', __rpcKeyword: '发送' })).toEqual(['message'])
    expect(select({ __rpcGroup: 'group:钉钉', __rpcKeyword: 'legacy' })).toEqual([])
    expect(select({ __rpcGroup: 'group:' })).toEqual(['legacy'])
    expect(select({ __rpcGroup: 'all' })).toHaveLength(4)
    expect(pickerSchema.filter.canAccessSuperData).toBe(true)
  })
  test('nested fields keep their API overrides through the registered container transpilers', () => {
    const result = transpile({ type: 'fieldSet', body: { type: 'combo', items: [{ type: 'flow-select', source: '/application/drafts/42/flows' }] } })
    expect(result.body[0].items[0]).toEqual({ type: 'select', source: '/application/drafts/42/flows' })
  })
})
