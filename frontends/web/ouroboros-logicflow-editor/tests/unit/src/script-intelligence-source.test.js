const assert = require('node:assert/strict')

test('code field transpilers preserve declared source positions and use the unified editor', async () => {
  const { transpile } = await import('../../../src/property-transpiler/transpiler.js')
  await import('../../../src/property-transpiler/transpilers/script-editor.js')
  await import('../../../src/property-transpiler/transpilers/expression.js')
  await import('../../../src/property-transpiler/transpilers/mapping.js')
  for (const type of ['script-editor', 'expression', 'mapping']) {
    const value = { nested: { name: '$input.name' }, flags: [true, null, 3] }
    const result = transpile({ type, name: 'code', value, codeSite: 'node.code', codePointer: '/code' })
    const editor = type === 'mapping' ? result.codeContext : result
    assert.equal(result.type, type === 'mapping' ? 'json-data-editor' : 'script-editor')
    assert.deepEqual(result.value, value)
    assert.equal(editor.codeSite, 'node.code')
    assert.equal(editor.codePointer, '/code')
    assert.equal(editor.intelligenceApi, '/api/dev/${appName}/code-intelligence/context')
    if (type !== 'script-editor') assert.equal(editor.language, 'spel')
  }
})

test('script editor preserves a declared intelligence endpoint', async () => {
  const { transpile } = await import('../../../src/property-transpiler/transpiler.js')
  await import('../../../src/property-transpiler/transpilers/script-editor.js')
  const schema = transpile({ type: 'script-editor', name: 'script', intelligenceApi: '/custom/context' })
  assert.equal(schema.intelligenceApi, '/custom/context')
  assert.equal(schema.name, 'script')
})

test('property panel carries the full flow and current node to editor fields', async () => {
  const { buildEditorWrapData } = await import('../../../src/logicFlowEditor/state/editorStateHelpers.mjs')
  const { buildNodePanelConfig } = await import('../../../src/logicFlowEditor/propertyPanel/logicFlowPropertyPanelController.mjs')
  const flow = { name: 'flow', nodes: [{ name: 'map', type: 'mapping', mapping: { n: 3 } }] }
  const node = { getData: () => flow.nodes[0], attr: () => ({ type: 'mapping', properties: [] }) }
  const panel = buildNodePanelConfig({
    node, currentGraph: { getNodes: () => [node] }, getNodeDefaultProperties: () => [], transpileAll: value => value,
    wrapData: (data, superProps) => buildEditorWrapData({
      createObjectFn: (parent, own) => Object.assign(Object.create(parent), own),
      flowModel: flow, rootData: { appName: 'demo' }, data, superProps,
    }),
  })
  assert.equal(panel.data.__FLOW_MODEL, flow)
  assert.equal(panel.data.appName, 'demo')
  assert.equal(panel.data.__CURRENT_NODE_NAME, 'map')
  assert.equal(panel.data.__CURRENT_NODE_TYPE, 'mapping')
  assert.deepEqual(panel.data.mapping, { n: 3 })
})
