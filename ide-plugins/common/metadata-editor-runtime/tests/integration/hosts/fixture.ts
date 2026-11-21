import { expect, test as base, type Page } from '@playwright/test'
import { createServer } from 'node:http'
import { mkdtemp, readFile, writeFile, rename, rm } from 'node:fs/promises'
import { readFileSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { LanguageServiceFixture } from './languageServiceFixture'
import {
  createMetadataEditorHost, createMetadataEditorSession, handleMetadataEditorSessionRequest,
  createMetadataEditorWebviewHtml, createVsCodeMetadataEditorHostScript,
  EditorServiceClient, MetadataRevisionConflictError, metadataHostErrorCode,
  type EditorServiceRequest, type MetadataDiagnostic, type MetadataEditorDocumentBridge, type MetadataEditorState,
} from '../../../dist'

export const repo = resolve(__dirname, '../../../../../..')
export const corpus = JSON.parse(readFileSync(join(repo, 'testing/common/fixtures/data/ide-metadata-workspace/documents.json'), 'utf8')) as
  Record<string, { fileName: string; metadataType: string; value: Record<string, unknown> }>
export type HostKind = 'vscode' | 'intellij'

declare global {
  interface Window {
    acquireVsCodeApi(): { postMessage(message: unknown): void }
    fixtureMessage(message: unknown): Promise<unknown>
    fixtureSave(text: string, revision: string): Promise<unknown>
    fixtureService(request: EditorServiceRequest): Promise<unknown>
    fixtureReady(): Promise<void>
    OuroborosMetadataEditorHost: {
      requestService(request: EditorServiceRequest): Promise<unknown>
      applyExternalDocument(document: {text: string; revision: string}): Promise<MetadataEditorState>
      transport: {loadState(): Promise<MetadataEditorState>}
    }
    OuroborosMetadataEditorApp: { flushPendingChanges(): Promise<void> }
  }
}

export class EditorFixture implements MetadataEditorDocumentBridge {
  private revision = 1
  private saves = 0
  private ready = false
  private file = ''
  private uri = ''
  private name = ''
  private service!: EditorServiceClient
  private session!: ReturnType<typeof createMetadataEditorSession>
  private directory = ''
  readonly errors: string[] = []
  onlineService = true
  diagnostics: MetadataDiagnostic[] = []
  private server = createServer()
  readonly backend = { status: 200, catalogVersion: 1, nodeDefines: undefined as unknown[] | undefined,
    requests: [] as Array<{path: string; cookie?: string; key?: string; body: unknown}> }

  constructor(readonly page: Page, readonly kind: HostKind) {}

  async start() {
    this.page.on('pageerror', error => this.errors.push(error.message))
    this.page.on('response', response => {
      if (response.status() >= 400 && /\.(js|css|wasm)(?:$|\?)/.test(response.url())) this.errors.push(`${response.status()} ${response.url()}`)
    })
    this.directory = await mkdtemp(join(repo, 'tmp/ide-metadata-fixture/document-'))
    let sessionSequence = 0
    const languageService = new LanguageServiceFixture()
    this.server.on('request', async (request, response) => {
      let raw = ''
      for await (const chunk of request) raw += chunk
      const path = request.url || ''
      const body: unknown = raw ? JSON.parse(raw) : undefined
      this.backend.requests.push({path, cookie: request.headers.cookie, key: String(request.headers['x-tidestack-dev-key'] || ''), body})
      response.setHeader('Content-Type', 'application/json')
      if (this.backend.status !== 200) {
        response.writeHead(this.backend.status).end(JSON.stringify({status: 1, message: 'fixture failure'})); return
      }
      if (!request.headers.cookie) response.setHeader('Set-Cookie', 'JSESSIONID=fixture-owner; HttpOnly')
      const data = languageService.respond(path, request.method, body) ??
        (path.endsWith('/sessions') ? {sessionId: `lang-${++sessionSequence}`} :
        path.includes('/node-define') ? this.backend.nodeDefines ?? [{name: `custom-${this.backend.catalogVersion}`, title: 'Custom node'}] : body || {identity: 'app-instance'})
      response.end(JSON.stringify({status: 0, data}))
    })
    await new Promise<void>(resolve => this.server.listen(0, '127.0.0.1', resolve))
    const address = this.server.address()
    if (!address || typeof address === 'string') throw new Error('Fixture HTTP server failed to bind')
    this.service = new EditorServiceClient({url: `http://127.0.0.1:${address.port}`, appName: 'fixture', devKey: 'fixture-key', accessToken: ''})
    await this.page.exposeBinding('fixtureSave', async (_source, text: string, revision: string) => {
      try { return {ok: true, result: await this.saveText(this.uri, text, revision)} }
      catch (error) {
        if (error instanceof MetadataRevisionConflictError) await this.publish()
        return {ok: false, error: String(error), errorCode: metadataHostErrorCode(error)}
      }
    })
    await this.page.exposeBinding('fixtureService', (_source, request: EditorServiceRequest) => this.service.request(request))
    await this.page.exposeBinding('fixtureReady', () => { this.ready = true })
    await this.page.exposeBinding('fixtureMessage', async (_source, message: Record<string, unknown>) => {
      if (message.type === 'ready') { this.ready = true; return }
      const response = message.type === 'editorService'
        ? await this.serviceResponse(message)
        : await handleMetadataEditorSessionRequest(this.session, message)
      if (response) await this.page.evaluate(value => window.dispatchEvent(new MessageEvent('message', {data: value})), response)
    })
    await this.page.addInitScript(() => {
      window.acquireVsCodeApi = () => ({postMessage: message => { void window.fixtureMessage(message) }})
    })
  }

  private async serviceResponse(message: Record<string, unknown>) {
    try { return {type: 'response', requestId: message.requestId, ok: true, result: await this.service.request(message.request as EditorServiceRequest)} }
    catch (error) { return {type: 'response', requestId: message.requestId, ok: false, error: String(error)} }
  }

  async open(name: string) {
    this.name = name
    const fixture = corpus[name]
    this.file = join(this.directory, fixture.fileName)
    this.uri = `file:///fixture/src/main/metadata/${fixture.fileName}`
    await this.writeDocument(JSON.stringify(fixture.value))
    await this.reopen()
  }

  async reopen() {
    this.ready = false
    this.session = createMetadataEditorSession({host: createMetadataEditorHost(this), uri: this.uri, metadataType: corpus[this.name].metadataType, locale: 'zh-CN'})
    const state = await this.session.loadState()
    const root = '/ide-plugins/vscode-plugin/dist/media'
    const html = this.kind === 'intellij'
      ? (await readFile(join(repo, `tmp/ide-metadata-fixture/intellij/${this.name}${this.onlineService ? '' : '-offline'}.html`), 'utf8'))
          .replace(JSON.stringify('__FIXTURE_TEXT__'), JSON.stringify((await this.loadText()).text).replaceAll('</', '<\\/'))
          .replace(JSON.stringify('__FIXTURE_REVISION__'), JSON.stringify(String(this.revision)))
      : createMetadataEditorWebviewHtml({
          template: await readFile(join(repo, 'ide-plugins/vscode-plugin/dist/media/runtime/metadata-editor-webview.html'), 'utf8'),
          fileName: corpus[this.name].fileName, nonce: 'fixture', cspSource: "'self'",
          amisSdkScriptUri: `${root}/amis-sdk/sdk.js`, amisSdkCssUri: `${root}/amis-sdk/sdk.css`,
          amisThemeCssUri: `${root}/amis-sdk/cxd.css`, amisDarkThemeCssUri: `${root}/amis-sdk/dark.css`,
          amisEditorRuntimeScriptUri: `${root}/runtime/metadata-editor-amis-editor.js`, amisEditorCssUri: `${root}/runtime/metadata-editor-amis-editor.css`,
          editorIntegrationScriptUri: `${root}/editor-integrations/metadata-editor-integrations.js`,
          editorBundleScriptUris: ['logicflow', 'approval-workflow'].map(type => `${root}/editor-bundles/${type}/static/js/app.js`),
          editorBundleStyleUris: ['logicflow', 'approval-workflow'].map(type => `${root}/editor-bundles/${type}/static/css/app.css`),
          xlsxRuntimeScriptUri: `${root}/xlsx/xlsx.full.min.js`, runtimeScriptUri: `${root}/runtime/metadata-editor-runtime.js`,
          hostScript: createVsCodeMetadataEditorHostScript({initialState: state, theme: 'cxd', locale: 'zh-CN', ...(this.onlineService ? {service: {appName: 'fixture'}} : {})}),
        })
    await this.page.route('**/host-fixture.html', route => route.fulfill({contentType: 'text/html', body: html}))
    await this.page.goto('/host-fixture.html')
    await expect.poll(() => this.ready).toBe(true)
    const type = corpus[this.name].metadataType
    await expect(this.page.locator(type === 'logicflow' ? '.wrapper-editor-container' : type === 'ui-schema' ? '.ae-Editor' : '#metadata-amis-root .cxd-Form').first()).toBeVisible()
    // AMIS autofocuses the root and initializes property forms asynchronously.
    await this.page.waitForTimeout(600)
  }

  async loadText() { return {text: await readFile(this.file, 'utf8'), revision: String(this.revision)} }
  private async writeDocument(text: string) {
    // Native Document replacement is atomic; disk observers must also see a complete version.
    const pending = this.file + '.pending'
    await writeFile(pending, text)
    await rename(pending, this.file)
  }
  async saveText(_uri: string, text: string, baseRevision: string) {
    if (baseRevision !== String(this.revision)) throw new MetadataRevisionConflictError('Fixture document changed externally')
    await this.writeDocument(text)
    this.saves++
    return {revision: String(++this.revision)}
  }
  async publishDiagnostics(_uri: string, diagnostics: MetadataDiagnostic[]) { this.diagnostics = diagnostics }
  async text() { return (await this.loadText()).text }
  async value() { return JSON.parse(await this.text()) }
  get saveCount() { return this.saves }
  async flush() { await this.page.evaluate(() => window.OuroborosMetadataEditorApp.flushPendingChanges()) }
  async external(value: unknown, publish = true) {
    await this.writeDocument(JSON.stringify(value))
    this.revision++
    if (publish) await this.publish()
  }
  private async publish() {
    await this.page.waitForFunction(() => Boolean(window.OuroborosMetadataEditorApp))
    if (this.kind === 'intellij') await this.page.evaluate(value => window.OuroborosMetadataEditorHost.applyExternalDocument(value), await this.loadText())
    else await this.page.evaluate(state => window.dispatchEvent(new MessageEvent('message', {data: {type: 'state', ...state}})), await this.session.loadState())
  }
  async closeService() { await this.service.dispose() }
  async dispose() {
    await this.service?.dispose()
    if (this.server.listening) await new Promise<void>((resolve, reject) => this.server.close(error => error ? reject(error) : resolve()))
    if (this.directory) await rm(this.directory, {recursive: true, force: true})
  }
}

export const test = base.extend<{editor: EditorFixture; hostKind: HostKind}>({
  hostKind: ['vscode', {option: true}],
  editor: async ({page, hostKind}, use) => {
    const fixture = new EditorFixture(page, hostKind)
    try {
      await fixture.start()
      await use(fixture)
      expect(fixture.errors).toEqual([])
    } finally { await fixture.dispose() }
  },
})
