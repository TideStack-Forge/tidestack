import {expect, test} from 'vitest'
import {referenceSource, intelligenceApi} from '../../../src/property-transpiler/editorHost.mjs'

test('runtime host replaces development catalogs and can explicitly disable intelligence', () => {
  const props = {logicFlowHost: {intelligenceApi: null, referenceApis: {flow: '/api/runtime/logicflows/catalog/references/flow', 'mq-binding':'/runtime/mq'}}}
  expect(referenceSource({}, props, 'flow', '/api/dev')).toBe('/api/runtime/logicflows/catalog/references/flow')
  expect(referenceSource({}, props, 'missing', '/api/dev')).toEqual([])
  expect(referenceSource({bindingType:'consumer'}, props, 'mq-binding', '/api/dev')).toEqual({url:'/runtime/mq',data:{bindingType:'consumer'}})
  expect(intelligenceApi({intelligenceApi: '/api/dev'}, props)).toBeNull()
  expect(intelligenceApi({}, {})).toBe('/api/dev/${appName}/code-intelligence/context')
  expect(intelligenceApi({intelligenceApi:null}, {})).toBeNull()
})
