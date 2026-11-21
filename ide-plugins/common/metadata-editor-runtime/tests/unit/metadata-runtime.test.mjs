import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { runInNewContext } from 'node:vm'
import { describe, expect, it } from 'vitest'

function loadBrowserRuntime() {
  const window = {}
  const source = readFileSync(fileURLToPath(new URL('../../runtime/metadata-editor-runtime.js', import.meta.url)), 'utf8')
  runInNewContext(source, { window })
  return window
}

function createElement(tagName = 'div') {
  const listeners = new Map()
  const attributes = new Map()
  const classes = new Set()
  const element = {
    tagName: String(tagName).toUpperCase(),
    value: '',
    textContent: '',
    innerHTML: '',
    className: '',
    hidden: false,
    disabled: false,
    media: '',
    sheet: { disabled: false },
    listeners,
    attributes,
    classList: {
      toggle(className, enabled) {
        const shouldEnable = enabled === undefined ? !classes.has(className) : Boolean(enabled)
        if (shouldEnable) {
          classes.add(className)
        } else {
          classes.delete(className)
        }
        element.className = Array.from(classes).join(' ')
        return shouldEnable
      },
      contains(className) {
        return classes.has(className)
      },
    },
    addEventListener(event, listener) {
      listeners.set(event, listener)
    },
    setAttribute(name, value) {
      attributes.set(name, value)
    },
    appendChild(child) {
      this.children.push(child)
      return child
    },
    click() {
      const listener = listeners.get('click')
      if (listener) {
        listener()
      }
    },
    children: [],
  }
  return element
}

function createRuntimeDom(window) {
  const elements = new Map()
  const createdElements = []
  for (const id of [
    'metadata-amis-root',
    'metadata-diagnostics',
    'metadata-diagnostics-indicator',
    'metadata-diagnostics-message',
    'metadata-diagnostics-close',
    'metadata-dirty',
    'metadata-import-toolbar',
    'metadata-import-button',
    'metadata-import-file',
    'metadata-import-hint',
    'metadata-import-status',
    'metadata-amis-theme-cxd',
    'metadata-amis-theme-dark',
  ]) {
    elements.set(id, createElement())
  }
  const bodyClasses = new Set()
  const body = {
    classList: {
      toggle(className, enabled) {
        if (enabled) {
          bodyClasses.add(className)
        } else {
          bodyClasses.delete(className)
        }
      },
      contains(className) {
        return bodyClasses.has(className)
      },
    },
  }
  window.document = {
    body,
    getElementById: (id) => elements.get(id),
    createElement: (tagName) => {
      const element = createElement(tagName)
      createdElements.push(element)
      return element
    },
  }
  window.__createdElements = createdElements
  return elements
}

async function flushPromises() {
  await Promise.resolve()
  await Promise.resolve()
}

describe('shared browser runtime', () => {
  it('serializes AMIS form changes and saves them through the host transport', async () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    let onAmisChange
    let savedText = ''
    let savedRevision = ''
    window.amisRequire = () => ({
      embed: (_root, _schema, props) => {
        onAmisChange = props.onChange
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text, revision) => {
          savedText = text
          savedRevision = revision
          return {
            document: {
              revision: '8',
              text,
              value: JSON.parse(text),
              type: 'menu',
              diagnostics: [],
            },
            editorSchema: { type: 'form', actions: [], body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    onAmisChange(
      { fullName: 'main', title: 'Changed', apis: ['GET /api/main'] },
      { title: 'Changed', apis: ['GET /api/main'] },
      { type: 'form' },
    )
    await flushPromises()

    expect(savedText).toBe('{\n  "fullName": "main",\n  "title": "Changed",\n  "apis": [\n    "GET /api/main"\n  ]\n}\n')
    expect(savedRevision).toBe('7')
    expect(app.getRevision()).toBe('8')
    expect(elements.get('metadata-diagnostics').hidden).toBe(true)
  })

  it('preserves existing JSON property order when saving form changes', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let onAmisChange
    let savedText = ''
    window.amisRequire = () => ({
      embed: (_root, _schema, props) => {
        onAmisChange = props.onChange
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: {
              revision: '8',
              text,
              value: JSON.parse(text),
              type: 'dev-menu',
              diagnostics: [],
            },
            editorSchema: { type: 'form', actions: [], body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "title": "Main",\n  "fullName": "main",\n  "children": [\n    {\n      "title": "Child",\n      "fullName": "child"\n    }\n  ]\n}\n',
        value: { title: 'Main', fullName: 'main', children: [{ title: 'Child', fullName: 'child' }] },
        type: 'dev-menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    onAmisChange({
      fullName: 'main',
      title: 'Changed',
      children: [{ fullName: 'child', title: 'Child Changed', url: '/child' }],
      url: '/main',
    })
    await flushPromises()

    expect(savedText).toBe(`{
  "title": "Changed",
  "fullName": "main",
  "children": [
    {
      "title": "Child Changed",
      "fullName": "child",
      "url": "/child"
    }
  ],
  "url": "/main"
}
`)
  })

  it('wraps embedded editor form data and saves the metadata root field as the full document', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let renderedProps
    let savedText = ''
    window.amisRequire = () => ({
      embed: (_root, _schema, props) => {
        renderedProps = props
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: {
              revision: '8',
              text,
              value: JSON.parse(text),
              type: 'logicflow',
              diagnostics: [],
            },
            editorSchema: {
              type: 'form',
              metadataRootField: '__metadataDocument',
              body: [{ type: 'logic-flow-editor', name: '__metadataDocument' }],
            },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"order","nodes":[],"connections":[],"nodePositions":{}}\n',
        value: { name: 'order', nodes: [], connections: [], nodePositions: {} },
        type: 'logicflow',
        diagnostics: [],
      },
      editorSchema: {
        type: 'form',
        metadataRootField: '__metadataDocument',
        body: [{ type: 'logic-flow-editor', name: '__metadataDocument' }],
      },
    })

    expect(renderedProps.data).toEqual({
      __metadataDocument: { name: 'order', nodes: [], connections: [], nodePositions: {} },
    })

    renderedProps.onChange({
      __metadataDocument: {
        name: 'order',
        nodes: [{ id: 'start', type: 'start' }],
        connections: [],
        nodePositions: {},
      },
    })
    await flushPromises()

    expect(JSON.parse(savedText)).toEqual({
      name: 'order',
      nodes: [{ id: 'start', type: 'start' }],
      connections: [],
      nodePositions: {},
    })
  })

  it('registers embedded editor library renderers before rendering AMIS schemas', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    const registeredRenderers = new Set()
    let embedCalled = false
    let mountedWithUtils = false
    window.React = { Component: class {} }
    window.amisRequire = (moduleName) => {
      if (moduleName === 'amis/embed') {
        return {
          embed: (_root, schema) => {
            if (!registeredRenderers.has('logic-flow-editor')) {
              throw new Error('找不到对应的渲染器')
            }
            embedCalled = true
            expect(schema.body).toEqual([{ type: 'logic-flow-editor', name: '__metadataDocument' }])
          },
        }
      }
      if (moduleName === 'amis' || moduleName === 'amis-core') {
        return {
          FormItem: (config) => (Component) => {
            registeredRenderers.add(config.type)
            return Component
          },
          getRendererByName: (type) => (registeredRenderers.has(type) ? { type } : undefined),
        }
      }
      return {}
    }
    window['logicflow-app'] = {
      mount: (props) => {
        mountedWithUtils = Boolean(props.__utils__?.FormItem)
        props.__utils__.FormItem({ type: 'logic-flow-editor', autoVar: true })(function LogicFlowEditorRenderer() {})
      },
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({ saveDelayMs: 0 })
    app.applyState({
      document: {
        revision: '1',
        text: '{"name":"order","nodes":[],"connections":[],"nodePositions":{}}\n',
        value: { name: 'order', nodes: [], connections: [], nodePositions: {} },
        type: 'logicflow',
        diagnostics: [],
      },
      editorSchema: {
        type: 'form',
        metadataRootField: '__metadataDocument',
        body: [{ type: 'logic-flow-editor', name: '__metadataDocument' }],
      },
    })

    expect(mountedWithUtils).toBe(true)
    expect(embedCalled).toBe(true)
  })

  it.each(['component', 'amis'])('shows %s service failures through the editor diagnostics disclosure', (source) => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    let componentUtils
    let amisEnv
    window['logicflow-app'] = { mount: (props) => { componentUtils = props.__utils__ } }
    window.amisRequire = (name) => name === 'amis/embed'
      ? { embed: (_root, _schema, _props, env) => { amisEnv = env } }
      : {}
    const value = { name: 'order', nodes: [], connections: [] }
    const app = window.OuroborosMetadataEditorRuntime.mount({
      initialState: {
        document: { revision: '1', text: JSON.stringify(value), value, type: 'logicflow', diagnostics: [] },
        editorSchema: { type: 'form', metadataRootField: '__metadataDocument', body: [] },
      },
    })
    if (source === 'component') componentUtils.toast.error('Editor service HTTP 403')
    else amisEnv.notify('error', 'Editor service HTTP 403')
    expect(elements.get('metadata-diagnostics').hidden).toBe(false)
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('Editor service HTTP 403')
    expect(elements.get('metadata-diagnostics-indicator').attributes.get('aria-expanded')).toBe('true')
    componentUtils.toast.success('Saved')
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('Editor service HTTP 403')
    elements.get('metadata-diagnostics-close').click()
    expect(elements.get('metadata-diagnostics-indicator').attributes.get('aria-expanded')).toBe('false')
    app.applyDiagnostics([])
    expect(elements.get('metadata-diagnostics').hidden).toBe(true)
  })

  it('serves embedded editor static integration sources through the AMIS fetcher contract', async () => {
    const window = loadBrowserRuntime()
    window.OuroborosMetadataEditorIntegrations = {
      logicflowNodeDefines: {
        items: [{ type: 'start', label: 'Start' }],
      },
      approvalWorkflowCandidateSource: {
        items: [{ name: 'User', label: 'User', items: [{ label: 'Alice', value: 'alice' }] }],
      },
      approvalWorkflowFormFieldSource: {
        fields: [{ name: 'amount', label: 'Amount' }],
      },
      businessFieldTypes: {
        normal: [{ label: '单行文本', value: '单行文本' }],
        primary: [{ label: '雪花主键', value: '雪花主键' }],
        uiSchemas: {
          单行文本: [{ type: 'input-number', name: 'size', label: '长度', value: 100 }],
        },
      },
    }
    const fetcher = window.OuroborosMetadataEditorRuntime.__test__.createEditorIntegrationFetcher()

    await expect(fetcher('ide://editor-integrations/logicflow/node-defines')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ type: 'start', label: 'Start' }] },
    })
    await expect(fetcher({ url: '/api/dev/demo/logicflow/node-define' })).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ type: 'start', label: 'Start' }] },
    })
    await expect(fetcher('ide://editor-integrations/approval-workflow/candidate')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ name: 'User', label: 'User', items: [{ label: 'Alice', value: 'alice' }] }] },
    })
    await expect(fetcher('ide://editor-integrations/approval-workflow/candidate/User/items')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ label: 'Alice', value: 'alice' }] },
    })
    await expect(fetcher('ide://editor-integrations/approval-workflow/form-fields')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { fields: [{ name: 'amount', label: 'Amount' }] },
    })
    await expect(fetcher('ide://editor-integrations/business/field-types/normal')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ label: '单行文本', value: '单行文本' }] },
    })
    await expect(fetcher('ide://editor-integrations/business/field-types/primary')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ label: '雪花主键', value: '雪花主键' }] },
    })
    await expect(fetcher('ide://editor-integrations/business/field-types/%E5%8D%95%E8%A1%8C%E6%96%87%E6%9C%AC/ui-schema')).resolves.toEqual({
      status: 0,
      msg: '',
      data: { type: 'container', body: [{ type: 'input-number', name: 'size', label: '长度', value: 100 }] },
    })
    await expect(fetcher({ url: '/api/app/demo/field-types/normal' })).resolves.toEqual({
      status: 0,
      msg: '',
      data: { items: [{ label: '单行文本', value: '单行文本' }] },
    })
    await expect(fetcher({ url: '/api/app/demo/field-types/单行文本/ui-schema' })).resolves.toEqual({
      status: 0,
      msg: '',
      data: { type: 'container', body: [{ type: 'input-number', name: 'size', label: '长度', value: 100 }] },
    })
    await expect(fetcher('ide://editor-integrations/unknown')).rejects.toThrow('Unsupported offline editor API')
  })

  it('flushes pending delayed saves before switching away from the visual editor', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    window.setTimeout = setTimeout
    window.clearTimeout = clearTimeout
    window.amisRequire = () => ({ embed: () => {} })
    const saves = []

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 1000,
      transport: {
        saveText: async (text, revision) => {
          saves.push({ text, revision })
          return {
            document: {
              revision: '8',
              text,
              value: JSON.parse(text),
              type: 'menu',
              diagnostics: [],
            },
            editorSchema: { type: 'form', actions: [], body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"fullName":"main","title":"Main"}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.applyEditorValue({ title: 'Changed' })
    expect(saves).toHaveLength(0)

    await app.flushPendingChanges()

    expect(saves).toHaveLength(1)
    expect(saves[0].revision).toBe('7')
    expect(JSON.parse(saves[0].text)).toEqual({ fullName: 'main', title: 'Changed' })
    expect(app.getRevision()).toBe('8')
  })

  it('edits legacy array metadata through a combo form without changing the document root', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let onAmisChange
    let savedText = ''
    window.amisRequire = () => ({
      embed: (_root, _schema, props) => {
        onAmisChange = props.onChange
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: {
              revision: '8',
              text,
              value: JSON.parse(text),
              type: 'configuration',
              diagnostics: [],
            },
            documentShape: 'array',
            editorSchema: { type: 'form', body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '[{"key":"one","label":"One","vendorField":{"enabled":true}}]\n',
        value: [{ key: 'one', label: 'One', vendorField: { enabled: true } }],
        type: 'configuration',
        diagnostics: [],
      },
      documentShape: 'array',
      editorSchema: { type: 'form', body: [{ type: 'combo', name: 'items', multiple: true }] },
    })

    onAmisChange({
      items: [{ key: 'one', label: 'Updated' }, { key: 'two', label: 'Two' }],
    })
    await flushPromises()

    expect(JSON.parse(savedText)).toEqual([
      { key: 'one', label: 'Updated', vendorField: { enabled: true } },
      { key: 'two', label: 'Two' },
    ])
    expect(app.getText()).toContain('vendorField')
  })

  it('imports i18n CSV rows into the messages map', async () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })
    const runtimeTest = window.OuroborosMetadataEditorRuntime.__test__
    let savedText = ''

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'i18n', diagnostics: [] },
            editorSchema: { type: 'form', body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"locale":"zh-CN","messages":{"common":{"existing":"已有"}}}\n',
        value: { locale: 'zh-CN', messages: { common: { existing: '已有' } } },
        type: 'i18n',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.importRows(runtimeTest.parseDelimitedRows('namespace,key,value\ncommon,hello,你好\nadmin,title,"管理,控制台"\n'))
    await flushPromises()

    expect(JSON.parse(savedText).messages).toEqual({
      common: { existing: '已有', hello: '你好' },
      admin: { title: '管理,控制台' },
    })
    expect(elements.get('metadata-import-toolbar').hidden).toBe(false)
    expect(elements.get('metadata-import-status').textContent).toBe('已导入 2 条国际化消息。')
  })

  it('imports data dictionary rows and replaces the item list', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })
    const runtimeTest = window.OuroborosMetadataEditorRuntime.__test__
    let savedText = ''

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'data-dict', diagnostics: [] },
            editorSchema: { type: 'form', body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"status","title":"状态","items":[{"code":"old","label":"Old","extraProps":{"obsolete":true}}]}\n',
        value: { name: 'status', title: '状态', items: [{ code: 'old', label: 'Old', extraProps: { obsolete: true } }] },
        type: 'data-dict',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.importRows(runtimeTest.parseDelimitedRows('code,label,order,parent,color,extra.enabled\nactive,Active,1,,red,true\nchild,Child,2,active,blue,false\n'))
    await flushPromises()

    expect(JSON.parse(savedText).items).toEqual([
      { code: 'active', label: 'Active', order: 1, extraProps: { color: 'red', enabled: true } },
      { code: 'child', label: 'Child', order: 2, parent: 'active', extraProps: { color: 'blue', enabled: false } },
    ])
  })

  it('imports data-init rows into records and infers the current single block', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })
    const runtimeTest = window.OuroborosMetadataEditorRuntime.__test__
    let savedText = ''

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'data-init', diagnostics: [] },
            editorSchema: { type: 'form', body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"id":"users","blocks":[{"id":"user-records","targetModel":"User","matchFields":["code"],"records":[]}]}\n',
        value: { id: 'users', blocks: [{ id: 'user-records', targetModel: 'User', matchFields: ['code'], records: [] }] },
        type: 'data-init',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.importRows(runtimeTest.parseDelimitedRows('code,name,enabled,age\nu1,Alice,true,30\nu2,Bob,false,28\n'))
    await flushPromises()

    expect(JSON.parse(savedText).blocks).toEqual([
      {
        id: 'user-records',
        targetModel: 'User',
        matchFields: ['code'],
        records: [
          { code: 'u1', name: 'Alice', enabled: true, age: 30 },
          { code: 'u2', name: 'Bob', enabled: false, age: 28 },
        ],
      },
    ])
  })

  it('reads the first Excel worksheet through the bundled XLSX runtime', () => {
    const window = loadBrowserRuntime()
    const calls = []
    window.XLSX = {
      read: (bytes, options) => {
        calls.push({ bytes: Array.from(bytes), options })
        return { SheetNames: ['Sheet1'], Sheets: { Sheet1: { ref: 'A1:B2' } } }
      },
      utils: {
        sheet_to_json: (sheet, options) => {
          calls.push({ sheet, options })
          return [['namespace', 'key', 'value'], ['common', 'hello', '你好']]
        },
      },
    }

    const rows = window.OuroborosMetadataEditorRuntime.__test__.parseWorkbookRows(new Uint8Array([1, 2, 3]).buffer)

    expect(rows).toEqual([['namespace', 'key', 'value'], ['common', 'hello', '你好']])
    expect(calls[0]).toEqual({ bytes: [1, 2, 3], options: { type: 'array' } })
    expect(calls[1]).toEqual({ sheet: { ref: 'A1:B2' }, options: { header: 1, defval: '' } })
  })

  it('hides the import toolbar for unsupported metadata types', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{"fullName":"main","title":"Main"}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    expect(elements.get('metadata-import-toolbar').hidden).toBe(true)
    expect(elements.get('metadata-import-button').disabled).toBe(true)
  })

  it('keeps unknown UI Schema properties when structured fields change', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let onAmisChange
    let savedText = ''
    window.amisRequire = () => ({
      embed: (_root, _schema, props) => {
        onAmisChange = props.onChange
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'ui-schema', diagnostics: [] },
            editorSchema: { type: 'form', body: [] },
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"type":"page","vendorField":{"enabled":true}}\n',
        value: { type: 'page', vendorField: { enabled: true } },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    onAmisChange({ type: 'page', title: 'Updated' })
    await flushPromises()

    expect(JSON.parse(savedText)).toEqual({ type: 'page', vendorField: { enabled: true }, title: 'Updated' })
  })

  it('merges field-level AMIS changes into the current JSON document', () => {
    const window = loadBrowserRuntime()
    const extract = window.OuroborosMetadataEditorRuntime.__test__.extractValueFromAmisChange

    expect(extract(['secondary', 'placement'])).toEqual({ placement: 'secondary' })
    expect(extract([[{ fullName: 'child', title: 'Child' }], 'children'])).toEqual({
      children: [{ fullName: 'child', title: 'Child' }],
    })
    expect(extract([{ fullName: 'main', title: 'Main', data: { mode: 'new' } }, { title: 'Main' }, { type: 'form' }])).toEqual({
      fullName: 'main',
      title: 'Main',
      data: { mode: 'new' },
    })
    expect(extract([{ fullName: 'main', values: { mode: 'new' } }, { title: 'Main' }, { type: 'form' }])).toEqual({
      fullName: 'main',
      values: { mode: 'new' },
    })
    expect(extract([{ data: { fullName: 'wrapper', title: 'Wrapper' } }])).toEqual({
      fullName: 'wrapper',
      title: 'Wrapper',
    })
  })

  it('renders AMIS forms when the embed module itself is callable', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    let renderedSchema
    window.amisRequire = () => (_root, schema) => {
      renderedSchema = schema
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [{ type: 'input-text', name: 'title' }] },
    })

    expect(renderedSchema).toEqual({ type: 'form', body: [{ type: 'input-text', name: 'title' }], actions: [], title: false })
    expect(elements.get('metadata-amis-root').textContent).toBe('')
  })

  it('passes zh-CN locale into AMIS by default', () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let renderedProps
    let renderedEnv
    window.amisRequire = () => ({
      embed: (_root, _schema, props, env) => {
        renderedProps = props
        renderedEnv = env
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    expect(app.getLocale()).toBe('zh-CN')
    expect(renderedProps.locale).toBe('zh-CN')
    expect(renderedEnv.locale).toBe('zh-CN')
  })

  it('shows a concrete diagnostic when AMIS form rendering fails', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({
      embed: () => {
        throw new Error('render failed')
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    expect(elements.get('metadata-amis-root').textContent).toBe('Unable to render metadata form.')
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('amis-render')
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('render failed')
  })

  it('keeps diagnostics compact until the user pins the message', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })

    const app = window.OuroborosMetadataEditorRuntime.mount({})

    app.applyDiagnostics([{ severity: 'error', code: 'schema-property-type', message: 'fullName must be a string' }])

    expect(elements.get('metadata-diagnostics').hidden).toBe(false)
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('fullName must be a string')
    expect(elements.get('metadata-diagnostics-indicator').attributes.get('aria-expanded')).toBe('false')
    expect(elements.get('metadata-diagnostics-close').hidden).toBe(true)
    expect(elements.get('metadata-diagnostics').classList.contains('is-pinned')).toBe(false)

    elements.get('metadata-diagnostics-indicator').click()

    expect(elements.get('metadata-diagnostics-indicator').attributes.get('aria-expanded')).toBe('true')
    expect(elements.get('metadata-diagnostics-close').hidden).toBe(false)
    expect(elements.get('metadata-diagnostics').classList.contains('is-pinned')).toBe(true)

    elements.get('metadata-diagnostics-close').click()

    expect(elements.get('metadata-diagnostics').hidden).toBe(false)
    expect(elements.get('metadata-diagnostics-indicator').attributes.get('aria-expanded')).toBe('false')
    expect(elements.get('metadata-diagnostics-close').hidden).toBe(true)
    expect(elements.get('metadata-diagnostics').classList.contains('is-pinned')).toBe(false)

    app.applyDiagnostics([])

    expect(elements.get('metadata-diagnostics').hidden).toBe(true)
    expect(elements.get('metadata-diagnostics-message').textContent).toBe('')
  })

  it('switches AMIS themes and re-renders the current form', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    const renderedThemes = []
    window.amisRequire = () => ({
      embed: (_root, _schema, _props, env) => {
        renderedThemes.push(env.theme)
      },
    })

    const app = window.OuroborosMetadataEditorRuntime.mount({ theme: 'cxd' })
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.setTheme('dark')

    expect(renderedThemes).toEqual(['cxd', 'dark'])
    expect(app.getTheme()).toBe('dark')
    expect(elements.get('metadata-amis-theme-cxd').disabled).toBe(true)
    expect(elements.get('metadata-amis-theme-cxd').media).toBe('not all')
    expect(elements.get('metadata-amis-theme-cxd').sheet.disabled).toBe(true)
    expect(elements.get('metadata-amis-theme-dark').disabled).toBe(false)
    expect(elements.get('metadata-amis-theme-dark').media).toBe('all')
    expect(elements.get('metadata-amis-theme-dark').sheet.disabled).toBe(false)
    expect(window.document.body.classList.contains('metadata-theme-dark')).toBe(true)
    expect(window.document.body.classList.contains('metadata-amis-editor-light')).toBe(false)
  })

  it('keeps the AMIS visual editor on the light theme even when the IDE theme is dark', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    const mountedThemes = []
    const themeUpdates = []
    window.OuroborosAmisEditorRuntime = {
      mount: (options) => {
        mountedThemes.push(options.theme)
        return {
          applyState: (state) => themeUpdates.push(['applyState', state.theme]),
          setTheme: (theme) => themeUpdates.push(['setTheme', theme]),
          dispose: () => undefined,
        }
      },
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({ theme: 'dark' })
    app.applyState({
      document: {
        revision: '7',
        text: '{"type":"page","body":[]}',
        value: { type: 'page', body: [] },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorKind: 'amis-editor',
      editorSchema: { type: 'form', body: [] },
    })

    app.setTheme('dark')

    expect(mountedThemes).toEqual(['cxd'])
    expect(themeUpdates).toEqual([])
    expect(app.getTheme()).toBe('cxd')
    expect(elements.get('metadata-amis-theme-cxd').disabled).toBe(false)
    expect(elements.get('metadata-amis-theme-cxd').media).toBe('all')
    expect(elements.get('metadata-amis-theme-cxd').sheet.disabled).toBe(false)
    expect(elements.get('metadata-amis-theme-dark').disabled).toBe(true)
    expect(elements.get('metadata-amis-theme-dark').media).toBe('not all')
    expect(elements.get('metadata-amis-theme-dark').sheet.disabled).toBe(true)
    expect(window.document.body.classList.contains('metadata-theme-dark')).toBe(false)
    expect(window.document.body.classList.contains('metadata-amis-editor-light')).toBe(true)
  })

  it('keeps the LogicFlow metadata editor on the light AMIS theme even when the IDE theme is dark', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    const renderedThemes = []
    window.amisRequire = (moduleName) => {
      if (moduleName === 'amis/embed') {
        return {
          embed: (_root, _schema, _props, env) => {
            renderedThemes.push(env.theme)
          },
        }
      }
      return {}
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({ theme: 'dark' })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"order","nodes":[],"connections":[],"nodePositions":{}}\n',
        value: { name: 'order', nodes: [], connections: [], nodePositions: {} },
        type: 'logicflow',
        diagnostics: [],
      },
      editorSchema: {
        type: 'form',
        metadataRootField: '__metadataDocument',
        body: [{ type: 'logic-flow-editor', name: '__metadataDocument' }],
      },
    })

    app.setTheme('dark')

    expect(renderedThemes).toEqual(['cxd'])
    expect(app.getTheme()).toBe('cxd')
    expect(elements.get('metadata-amis-theme-cxd').disabled).toBe(false)
    expect(elements.get('metadata-amis-theme-cxd').media).toBe('all')
    expect(elements.get('metadata-amis-theme-cxd').sheet.disabled).toBe(false)
    expect(elements.get('metadata-amis-theme-dark').disabled).toBe(true)
    expect(elements.get('metadata-amis-theme-dark').media).toBe('not all')
    expect(elements.get('metadata-amis-theme-dark').sheet.disabled).toBe(true)
    expect(window.document.body.classList.contains('metadata-theme-dark')).toBe(false)
    expect(window.document.body.classList.contains('metadata-theme-light')).toBe(true)
    expect(window.document.body.classList.contains('metadata-amis-editor-light')).toBe(false)
  })

  it('keeps original text and revision when the visual editor emits an equivalent value', async () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    let change
    let saves = 0
    window.OuroborosAmisEditorRuntime = {
      mount: options => {
        change = options.onChange
        return { applyState() {}, setTheme() {}, dispose() {} }
      },
    }
    const text = '{ "schema": {"type":"page","body":[]}, "name":"Keep.Order" }'
    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      initialState: {document: {text, revision: '7', value: JSON.parse(text), type: 'ui-schema', diagnostics: []}, editorKind: 'amis-editor'},
      transport: {saveText: async () => { saves++; throw new Error('No-op must not save') }},
    })
    change({type: 'page', body: []})
    await app.flushPendingChanges()
    expect(saves).toBe(0)
    expect(app.getText()).toBe(text)
    expect(app.getRevision()).toBe('7')
    expect(elements.get('metadata-dirty').textContent).toBe('')
  })

  it('saves an intentional code-panel property reorder even when values are unchanged', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let change
    let savedText
    window.OuroborosAmisEditorRuntime = {
      mount: options => {
        change = options.onChange
        return { applyState() {}, setTheme() {}, dispose() {} }
      },
    }
    const text = '{"type":"page","title":"Code order","body":[]}'
    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      initialState: {document: {text, revision: '7', value: JSON.parse(text), type: 'ui-schema', diagnostics: []}, editorKind: 'amis-editor'},
      transport: {saveText: async next => {
        savedText = next
        return {document: {text: next, revision: '8', value: JSON.parse(next), type: 'ui-schema', diagnostics: []}, editorKind: 'amis-editor'}
      }},
    })
    change({body: [], title: 'Code order', type: 'page'})
    await app.flushPendingChanges()
    expect(savedText).toBe('{\n  "body": [],\n  "title": "Code order",\n  "type": "page"\n}\n')
  })

  it('edits the inner schema when UI Schema metadata uses the wrapper format', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let editorChange
    let mountedValue
    let savedText = ''
    window.OuroborosAmisEditorRuntime = {
      mount: (options) => {
        mountedValue = options.value
        editorChange = options.onChange
        return {
          applyState: () => undefined,
          setTheme: () => undefined,
          dispose: () => undefined,
        }
      },
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'ui-schema', diagnostics: [] },
            editorKind: 'amis-editor',
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"/resource-management","title":"资源管理","description":"管理资源","schema":{"type":"page","body":[]}}\n',
        value: {
          name: '/resource-management',
          title: '资源管理',
          description: '管理资源',
          schema: { type: 'page', body: [] },
        },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorKind: 'amis-editor',
    })

    expect(mountedValue).toEqual({ type: 'page', body: [] })

    editorChange({ type: 'page', title: '资源列表', body: [{ type: 'tpl', tpl: 'Hello' }] })
    await flushPromises()

    expect(JSON.parse(savedText)).toEqual({
      name: '/resource-management',
      title: '资源管理',
      description: '管理资源',
      schema: { type: 'page', title: '资源列表', body: [{ type: 'tpl', tpl: 'Hello' }] },
    })
  })

  it('keeps visual editor schema order from the AMIS runtime without restoring removed keys', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let editorChange
    let savedText = ''
    window.OuroborosAmisEditorRuntime = {
      mount: (options) => {
        editorChange = options.onChange
        return {
          applyState: () => undefined,
          setTheme: () => undefined,
          dispose: () => undefined,
        }
      },
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'ui-schema', diagnostics: [] },
            editorKind: 'amis-editor',
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"Runtime.Page","title":"Runtime","description":"Old","schema":{"type":"page","title":"Legacy","body":[],"className":"legacy"}}\n',
        value: {
          name: 'Runtime.Page',
          title: 'Runtime',
          description: 'Old',
          schema: { type: 'page', title: 'Legacy', body: [], className: 'legacy' },
        },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorKind: 'amis-editor',
    })

    editorChange({ type: 'page', body: [], aside: [] })
    await flushPromises()

    expect(savedText).toBe(`{
  "name": "Runtime.Page",
  "title": "Runtime",
  "description": "Old",
  "schema": {
    "type": "page",
    "body": [],
    "aside": []
  }
}
`)
  })

  it('edits UI Schema wrapper fields without changing the inner schema', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let savedText = ''
    window.OuroborosAmisEditorRuntime = {
      mount: () => ({
        applyState: () => undefined,
        setTheme: () => undefined,
        dispose: () => undefined,
      }),
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text) => {
          savedText = text
          return {
            document: { revision: '8', text, value: JSON.parse(text), type: 'ui-schema', diagnostics: [] },
            editorKind: 'amis-editor',
          }
        },
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"Runtime.Page","title":"Runtime","description":"Old","schema":{"type":"page","body":[]}}\n',
        value: {
          name: 'Runtime.Page',
          title: 'Runtime',
          description: 'Old',
          schema: { type: 'page', body: [] },
        },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorKind: 'amis-editor',
    })

    const nameInput = window.__createdElements.find((element) => element.attributes.get('name') === 'name')
    const descriptionInput = window.__createdElements.find((element) => element.attributes.get('name') === 'description')
    const nameLabelRemark = window.__createdElements.find((element) => element.className === 'metadata-ui-schema-wrapper-label-remark')
    expect(nameInput.value).toBe('Runtime.Page')
    expect(descriptionInput.value).toBe('Old')
    expect(nameLabelRemark.textContent).toBe('?')
    expect(nameLabelRemark.attributes.get('title')).toBe('按低代码页面命名习惯填写点分名称，例如 Admin.UserList；系统运行时会映射成 /admin/user-list。')
    expect(nameLabelRemark.attributes.get('aria-label')).toBe('按低代码页面命名习惯填写点分名称，例如 Admin.UserList；系统运行时会映射成 /admin/user-list。')

    nameInput.value = 'Runtime.Detail.Page'
    nameInput.listeners.get('input')()
    descriptionInput.value = 'Detail page'
    descriptionInput.listeners.get('input')()
    await flushPromises()

    expect(JSON.parse(savedText)).toEqual({
      name: 'Runtime.Detail.Page',
      title: 'Runtime',
      description: 'Detail page',
      schema: { type: 'page', body: [] },
    })
  })

  it('collapses UI Schema wrapper fields without remounting the visual editor', () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    let mountCount = 0
    window.OuroborosAmisEditorRuntime = {
      mount: () => {
        mountCount += 1
        return {
          applyState: () => undefined,
          setTheme: () => undefined,
          dispose: () => undefined,
        }
      },
    }

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{"name":"Runtime.Page","title":"Runtime","description":"Old","schema":{"type":"page","body":[]}}\n',
        value: {
          name: 'Runtime.Page',
          title: 'Runtime',
          description: 'Old',
          schema: { type: 'page', body: [] },
        },
        type: 'ui-schema',
        diagnostics: [],
      },
      editorKind: 'amis-editor',
    })

    const fieldsRoot = window.__createdElements.find((element) => element.className === 'metadata-ui-schema-wrapper-fields')
    const toggle = window.__createdElements.find((element) => element.className === 'metadata-ui-schema-wrapper-toggle')

    expect(fieldsRoot).toBeTruthy()
    expect(toggle.textContent).toBe('收起页面信息')
    expect(toggle.attributes.get('aria-expanded')).toBe('true')

    toggle.listeners.get('click')()

    expect(fieldsRoot.className).toBe('metadata-ui-schema-wrapper-fields is-collapsed')
    expect(toggle.textContent).toBe('展开页面信息')
    expect(toggle.attributes.get('aria-expanded')).toBe('false')
    expect(mountCount).toBe(1)
  })

  it('keeps local dirty form text when a newer host state arrives', () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })

    const app = window.OuroborosMetadataEditorRuntime.mount({})
    app.applyState({
      document: {
        revision: '7',
        text: '{\n  "fullName": "main",\n  "title": "Main"\n}\n',
        value: { fullName: 'main', title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.applyEditorValue({ title: 'Local' })

    app.applyState({
      document: {
        revision: '8',
        text: '{\n  "fullName": "main",\n  "title": "External"\n}\n',
        value: { fullName: 'main', title: 'External' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    expect(app.getText()).toContain('Local')
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('revision-conflict')

    app.applyState({
      document: {
        revision: '8',
        text: '{\n  "fullName": "main",\n  "title": "External"\n}\n',
        value: { fullName: 'main', title: 'External' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    }, { force: true })

    expect(app.getText()).toContain('External')
    expect(elements.get('metadata-dirty').textContent).toBe('')
    expect(elements.get('metadata-diagnostics').hidden).toBe(true)
  })

  it('reloads the latest revision and retries local form changes after a save conflict', async () => {
    const window = loadBrowserRuntime()
    createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })
    const saves = []
    let saveAttempt = 0
    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text, revision) => {
          saves.push({ text, revision })
          saveAttempt += 1
          if (saveAttempt === 1) {
            throw Object.assign(new Error('stale revision'), { code: 'revision-conflict' })
          }
          return {
            document: {
              revision: '9',
              text,
              value: JSON.parse(text),
              type: 'menu',
              diagnostics: [],
            },
            editorSchema: { type: 'form', body: [] },
          }
        },
        loadState: async () => ({
          document: {
            revision: '8',
            text: '{"fullName":"main","title":"External","url":"/external"}\n',
            value: { fullName: 'main', title: 'External', url: '/external' },
            type: 'menu',
            diagnostics: [],
          },
          editorSchema: { type: 'form', body: [] },
        }),
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"fullName":"main","title":"Main","url":"/main"}\n',
        value: { fullName: 'main', title: 'Main', url: '/main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.applyEditorValue({ title: 'Local' })
    await flushPromises()
    await flushPromises()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(saves).toHaveLength(2)
    expect(saves[0].revision).toBe('7')
    expect(saves[1].revision).toBe('8')
    expect(JSON.parse(saves[1].text)).toEqual({
      fullName: 'main',
      title: 'Local',
      url: '/external',
    })
    expect(app.getRevision()).toBe('9')
  })

  it('rejects unsafe array rebases instead of creating holes or overwriting shifted entries', () => {
    const window = loadBrowserRuntime()
    const runtimeTest = window.OuroborosMetadataEditorRuntime.__test__
    const localPatch = runtimeTest.createValuePatch(
      { items: ['a', 'b', 'c'] },
      { items: ['a', 'b', 'local'] },
    )

    expect(runtimeTest.applyValuePatch({ items: ['a'] }, localPatch).conflict).toBe(true)
    expect(runtimeTest.applyValuePatch({ items: ['a', 'external', 'c'] }, localPatch).conflict).toBe(true)
    expect(runtimeTest.applyValuePatch({ items: ['a', 'b', 'c'] }, localPatch)).toEqual({
      value: { items: ['a', 'b', 'local'] },
      conflict: false,
    })
  })

  it('keeps local form text when the latest conflicting document cannot be rebased', async () => {
    const window = loadBrowserRuntime()
    const elements = createRuntimeDom(window)
    window.amisRequire = () => ({ embed: () => {} })
    const saves = []
    const app = window.OuroborosMetadataEditorRuntime.mount({
      saveDelayMs: 0,
      transport: {
        saveText: async (text, revision) => {
          saves.push({ text, revision })
          throw Object.assign(new Error('stale revision'), { code: 'revision-conflict' })
        },
        loadState: async () => ({
          document: {
            revision: '8',
            text: '{invalid',
            type: 'menu',
            diagnostics: [{ severity: 'error', code: 'metadata-json-invalid', message: 'Invalid JSON' }],
          },
          editorSchema: { type: 'form', body: [] },
        }),
      },
    })
    app.applyState({
      document: {
        revision: '7',
        text: '{"title":"Main"}\n',
        value: { title: 'Main' },
        type: 'menu',
        diagnostics: [],
      },
      editorSchema: { type: 'form', body: [] },
    })

    app.applyEditorValue({ title: 'Local' })
    await flushPromises()
    await flushPromises()
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(saves).toHaveLength(1)
    expect(app.getText()).toContain('Local')
    expect(elements.get('metadata-diagnostics-message').textContent).toContain('revision-conflict-manual')
  })

})
