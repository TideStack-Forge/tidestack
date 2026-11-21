import { readFile } from 'node:fs/promises'
import * as path from 'node:path'
import { randomBytes } from 'node:crypto'
import * as vscode from 'vscode'
import {
  EditorServiceClient,
  editorConnection,
  createMetadataEditorHost,
  createMetadataEditorSession,
  createMetadataEditorWebviewHtml,
  createVsCodeMetadataEditorHostScript,
  handleMetadataEditorSessionRequest,
  mapMetadataPackagedResourcePath,
  mapMetadataSourcePath,
  MetadataRevisionConflictError,
  normalizeIdeLocale,
  type MetadataEditorState,
  metadataEditorRuntimeFileName,
  metadataEditorWebviewTemplateFileName,
  type MetadataDiagnostic,
  type MetadataIdeFileBridge,
  type MetadataReferenceTarget,
} from 'ouroboros-metadata-editor'

export interface MetadataCustomEditorRegistration {
  viewType: string
  metadataType: string
  schemaName: string
}

interface PendingFlushRequest {
  resolve(): void
  reject(error: unknown): void
  timeout: ReturnType<typeof setTimeout>
}

interface ActiveMetadataEditor {
  uri: vscode.Uri
  uriKey: string
  viewType: string
  webviewPanel: vscode.WebviewPanel
  flushPendingChanges(): Promise<void>
}

export class VsCodeMetadataFileBridge implements MetadataIdeFileBridge, vscode.Disposable {
  private diagnostics = vscode.languages.createDiagnosticCollection('ouroboros-metadata')

  dispose(): void {
    this.diagnostics.dispose()
  }

  async loadText(uri: string): Promise<{ text: string; revision: string }> {
    const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(uri))
    return { text: document.getText(), revision: String(document.version) }
  }

  async saveText(uri: string, text: string, baseRevision: string): Promise<{ revision: string }> {
    const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(uri))
    const expectedVersion = Number(baseRevision)
    if (!Number.isInteger(expectedVersion) || document.version !== expectedVersion) {
      throw new MetadataRevisionConflictError(
        `Revision conflict for ${uri}: expected ${baseRevision}, actual ${document.version}`,
      )
    }

    // WorkspaceEdit has no expected-version precondition, so observe the edit and restore a concurrent snapshot if needed.
    let externalText: string | undefined
    let externalVersion: number | undefined
    const changeSubscription = vscode.workspace.onDidChangeTextDocument((event) => {
      if (event.document.uri.toString() !== document.uri.toString() || event.document.version <= expectedVersion) {
        return
      }
      if (event.document.getText() !== text) {
        externalText = event.document.getText()
        externalVersion = event.document.version
      }
    })
    const edit = new vscode.WorkspaceEdit()
    const fullRange = new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length))
    edit.replace(document.uri, fullRange, text)
    try {
      const applied = await vscode.workspace.applyEdit(edit)
      if (!applied) {
        throw new Error(`Unable to apply metadata edit for ${uri}`)
      }
      if (document.version !== expectedVersion + 1) {
        if (externalText !== undefined && externalVersion !== undefined && document.version === externalVersion + 1) {
          const restore = new vscode.WorkspaceEdit()
          const restoreRange = new vscode.Range(document.positionAt(0), document.positionAt(document.getText().length))
          restore.replace(document.uri, restoreRange, externalText)
          const restored = await vscode.workspace.applyEdit(restore)
          if (!restored) {
            throw new Error(`Unable to restore concurrent metadata edit for ${uri}`)
          }
        }
        throw new MetadataRevisionConflictError(
          `Metadata document changed while saving: expected ${expectedVersion}, actual ${document.version}`,
        )
      }
      return { revision: String(document.version) }
    } finally {
      changeSubscription.dispose()
    }
  }

  watchText(uri: string, listener: (change: { uri: string; revision: string }) => void): { dispose(): void } {
    const target = vscode.Uri.parse(uri).toString()
    return vscode.workspace.onDidChangeTextDocument((event) => {
      if (event.document.uri.toString() === target) {
        listener({ uri, revision: String(event.document.version) })
      }
    })
  }

  async listMetadataFiles(root: string): Promise<Array<{ uri: string; type: string; packagedPath: string }>> {
    const sourceFiles = await vscode.workspace.findFiles(new vscode.RelativePattern(root, 'src/main/metadata/**/*.json'))
    const packagedFiles = await vscode.workspace.findFiles(new vscode.RelativePattern(root, '**/resources/META-INF/ouroboros/**/*.json'))
    const files = [...new Map([...sourceFiles, ...packagedFiles].map((file) => [file.toString(), file])).values()]
    return files
      .map((file) => mapMetadataSourcePath(file.toString()) ?? mapMetadataPackagedResourcePath(file.toString()))
      .filter((mapping): mapping is NonNullable<typeof mapping> => Boolean(mapping))
      .map((mapping) => ({ uri: mapping.sourcePath, type: mapping.type, packagedPath: mapping.packagedPath }))
  }

  async openReference(target: MetadataReferenceTarget): Promise<void> {
    const document = await vscode.workspace.openTextDocument(vscode.Uri.parse(target.uri))
    await vscode.window.showTextDocument(document)
  }

  async publishDiagnostics(uri: string, diagnostics: MetadataDiagnostic[]): Promise<void> {
    const vscodeDiagnostics = diagnostics.map((diagnostic) => {
      const range = new vscode.Range(0, 0, 0, 1)
      const severity = diagnostic.severity === 'error'
        ? vscode.DiagnosticSeverity.Error
        : diagnostic.severity === 'warning'
          ? vscode.DiagnosticSeverity.Warning
          : vscode.DiagnosticSeverity.Information
      const item = new vscode.Diagnostic(range, diagnostic.message, severity)
      item.code = diagnostic.code
      item.source = 'ouroboros-metadata'
      return item
    })
    this.diagnostics.set(vscode.Uri.parse(uri), vscodeDiagnostics)
  }
}

export class MetadataCustomEditorProvider implements vscode.CustomTextEditorProvider, vscode.Disposable {
  private static readonly activeEditors = new Map<string, ActiveMetadataEditor>()
  private static lastActiveEditorKey: string | undefined

  private readonly fileBridge = new VsCodeMetadataFileBridge()

  static register(context: vscode.ExtensionContext, registration: MetadataCustomEditorRegistration): vscode.Disposable {
    const provider = new MetadataCustomEditorProvider(context, registration)
    const providerRegistration = vscode.window.registerCustomEditorProvider(
      registration.viewType,
      provider,
      { webviewOptions: { retainContextWhenHidden: true } },
    )
    return vscode.Disposable.from(providerRegistration, provider)
  }

  static getActiveResource(): vscode.Uri | undefined {
    const activeEditor = this.getActiveEditor()
    return activeEditor?.uri
  }

  static getActiveViewColumn(uri?: vscode.Uri): vscode.ViewColumn | undefined {
    return this.getActiveEditor(uri)?.webviewPanel.viewColumn
  }

  static async flushPendingChanges(uri?: vscode.Uri): Promise<void> {
    const activeEditor = this.getActiveEditor(uri)
    if (activeEditor) {
      await activeEditor.flushPendingChanges()
    }
  }

  private static rememberActiveEditor(editor: ActiveMetadataEditor): void {
    this.activeEditors.set(editor.uriKey, editor)
    this.lastActiveEditorKey = editor.uriKey
  }

  private static forgetActiveEditor(editor: ActiveMetadataEditor): void {
    if (this.activeEditors.get(editor.uriKey) === editor) {
      this.activeEditors.delete(editor.uriKey)
      if (this.lastActiveEditorKey === editor.uriKey) {
        this.lastActiveEditorKey = undefined
      }
    }
  }

  private static getActiveEditor(uri?: vscode.Uri): ActiveMetadataEditor | undefined {
    if (uri) {
      return this.activeEditors.get(uri.toString())
    }
    for (const editor of this.activeEditors.values()) {
      if (editor.webviewPanel.active) {
        this.lastActiveEditorKey = editor.uriKey
        return editor
      }
    }
    return this.lastActiveEditorKey ? this.activeEditors.get(this.lastActiveEditorKey) : undefined
  }

  private constructor(
    private readonly context: vscode.ExtensionContext,
    private readonly registration: MetadataCustomEditorRegistration,
  ) {}

  dispose(): void {
    this.fileBridge.dispose()
  }

  async resolveCustomTextEditor(
    document: vscode.TextDocument,
    webviewPanel: vscode.WebviewPanel,
    _token: vscode.CancellationToken,
  ): Promise<void> {
    const host = createMetadataEditorHost(this.fileBridge)
    const uri = document.uri.toString()
    const locale = this.getEditorLocale()
    const session = createMetadataEditorSession({
      host,
      uri,
      metadataType: this.registration.schemaName,
      locale,
    })
    const webviewAssetRoot = vscode.Uri.joinPath(this.context.extensionUri, 'dist', 'media')
    const amisSdkRoot = vscode.Uri.joinPath(webviewAssetRoot, 'amis-sdk')
    const runtimeRoot = vscode.Uri.joinPath(webviewAssetRoot, 'runtime')
    const xlsxRoot = vscode.Uri.joinPath(webviewAssetRoot, 'xlsx')
    webviewPanel.webview.options = { enableScripts: true, localResourceRoots: [webviewAssetRoot] }
    const workspaceRoot = vscode.workspace.getWorkspaceFolder(document.uri)?.uri.fsPath
    let services: EditorServiceClient | undefined
    let serviceError = 'No editor service configured in .tidestack/develop.json'
    if (workspaceRoot) {
      try {
        let directory = path.dirname(document.uri.fsPath)
        while (directory.startsWith(workspaceRoot)) {
          let projectText: string
          try { projectText = await readFile(path.join(directory, '.tidestack/develop.json'), 'utf8') }
          catch (error) {
            if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error
            if (directory === workspaceRoot) break
            directory = path.dirname(directory); continue
          }
          let local = {}
          try { local = JSON.parse(await readFile(path.join(directory, '.tidestack/develop.local.json'), 'utf8')) }
          catch (error) { if ((error as NodeJS.ErrnoException).code !== 'ENOENT') throw error }
          const connection = editorConnection(JSON.parse(projectText), local)
          if (connection) services = new EditorServiceClient(connection)
          break
        }
      } catch (error) { serviceError = error instanceof Error ? error.message : String(error) }
    }
    const initialState = await session.loadState()
    const htmlTemplate = await this.loadWebviewTemplate(runtimeRoot)
    webviewPanel.webview.html = this.renderHtml(
      webviewPanel.webview,
      document.fileName,
      amisSdkRoot,
      runtimeRoot,
      xlsxRoot,
      webviewAssetRoot,
      initialState,
      htmlTemplate,
      locale,
      services ? { appName: services.connection.appName } : undefined,
    )

    const publishState = async (): Promise<void> => {
      await webviewPanel.webview.postMessage({
        type: 'state',
        ...(await session.loadState()),
      })
    }

    const pendingFlushRequests = new Map<string, PendingFlushRequest>()
    const activeEditor: ActiveMetadataEditor = {
      uri: document.uri,
      uriKey: uri,
      viewType: this.registration.viewType,
      webviewPanel,
      flushPendingChanges: () => this.requestWebviewFlush(webviewPanel, pendingFlushRequests),
    }
    MetadataCustomEditorProvider.rememberActiveEditor(activeEditor)

    const documentSubscription = this.fileBridge.watchText(uri, () => {
      void publishState()
    })
    const themeSubscription = vscode.window.onDidChangeActiveColorTheme(() => {
      void webviewPanel.webview.postMessage({ type: 'theme', theme: this.getAmisTheme() })
    })
    const viewStateSubscription = webviewPanel.onDidChangeViewState((event) => {
      if (event.webviewPanel.active) {
        MetadataCustomEditorProvider.rememberActiveEditor(activeEditor)
      }
    })
    webviewPanel.onDidDispose(() => {
      void services?.dispose()
      documentSubscription.dispose()
      themeSubscription.dispose()
      viewStateSubscription.dispose()
      this.rejectPendingFlushRequests(pendingFlushRequests, new Error('Metadata visual editor was closed before pending changes were flushed.'))
      MetadataCustomEditorProvider.forgetActiveEditor(activeEditor)
    })

    webviewPanel.webview.onDidReceiveMessage(async (message) => {
      if (message?.type === 'flushPendingChangesResult' && typeof message.requestId === 'string') {
        const pending = pendingFlushRequests.get(message.requestId)
        if (pending) {
          pendingFlushRequests.delete(message.requestId)
          clearTimeout(pending.timeout)
          if (message.ok) {
            pending.resolve()
          } else {
            pending.reject(new Error(message.error || 'Unable to flush pending metadata editor changes.'))
          }
        }
        return
      }
      if (message?.type === 'editorService' && typeof message.requestId === 'string') {
        try {
          if (!services) throw new Error(serviceError)
          const result = await services.request(message.request)
          await webviewPanel.webview.postMessage({ type: 'response', requestId: message.requestId, ok: true, result })
        } catch (error) {
          await webviewPanel.webview.postMessage({ type: 'response', requestId: message.requestId, ok: false,
            error: error instanceof Error ? error.message : String(error), errorCode: 'editor-service' })
        }
        return
      }
      if (message?.type === 'ready') {
        await publishState()
        await webviewPanel.webview.postMessage({ type: 'theme', theme: this.getAmisTheme() })
        return
      }
      const response = await handleMetadataEditorSessionRequest(session, message)
      if (response) {
        await webviewPanel.webview.postMessage(response)
      }
    })
  }

  private renderHtml(
    webview: vscode.Webview,
    fileName: string,
    amisSdkRoot: vscode.Uri,
    runtimeRoot: vscode.Uri,
    xlsxRoot: vscode.Uri,
    webviewAssetRoot: vscode.Uri,
    initialState: MetadataEditorState,
    htmlTemplate: string,
    locale: 'zh-CN' | 'en-US',
    service?: { appName: string },
  ): string {
    const nonce = randomBytes(16).toString('base64')
    return createMetadataEditorWebviewHtml({
      template: htmlTemplate,
      fileName,
      nonce,
      cspSource: webview.cspSource,
      amisSdkScriptUri: webview.asWebviewUri(vscode.Uri.joinPath(amisSdkRoot, 'sdk.js')).toString(),
      amisSdkCssUri: webview.asWebviewUri(vscode.Uri.joinPath(amisSdkRoot, 'sdk.css')).toString(),
      amisThemeCssUri: webview.asWebviewUri(vscode.Uri.joinPath(amisSdkRoot, 'cxd.css')).toString(),
      amisDarkThemeCssUri: webview.asWebviewUri(vscode.Uri.joinPath(amisSdkRoot, 'dark.css')).toString(),
      amisEditorRuntimeScriptUri: webview.asWebviewUri(vscode.Uri.joinPath(runtimeRoot, 'metadata-editor-amis-editor.js')).toString(),
      amisEditorCssUri: webview.asWebviewUri(vscode.Uri.joinPath(runtimeRoot, 'metadata-editor-amis-editor.css')).toString(),
      editorIntegrationScriptUri: webview.asWebviewUri(vscode.Uri.joinPath(webviewAssetRoot, 'editor-integrations', 'metadata-editor-integrations.js')).toString(),
      editorBundleScriptUris: [
        webview.asWebviewUri(vscode.Uri.joinPath(webviewAssetRoot, 'editor-bundles', 'logicflow', 'static', 'js', 'app.js')).toString(),
        webview.asWebviewUri(vscode.Uri.joinPath(webviewAssetRoot, 'editor-bundles', 'approval-workflow', 'static', 'js', 'app.js')).toString(),
      ],
      editorBundleStyleUris: [
        webview.asWebviewUri(vscode.Uri.joinPath(webviewAssetRoot, 'editor-bundles', 'logicflow', 'static', 'css', 'app.css')).toString(),
        webview.asWebviewUri(vscode.Uri.joinPath(webviewAssetRoot, 'editor-bundles', 'approval-workflow', 'static', 'css', 'app.css')).toString(),
      ],
      xlsxRuntimeScriptUri: webview.asWebviewUri(vscode.Uri.joinPath(xlsxRoot, 'xlsx.full.min.js')).toString(),
      runtimeScriptUri: webview.asWebviewUri(vscode.Uri.joinPath(runtimeRoot, metadataEditorRuntimeFileName)).toString(),
      hostScript: createVsCodeMetadataEditorHostScript({
        initialState,
        service,
        theme: this.getAmisTheme(),
        locale,
      }),
    })
  }

  private async loadWebviewTemplate(runtimeRoot: vscode.Uri): Promise<string> {
    const bytes = await vscode.workspace.fs.readFile(
      vscode.Uri.joinPath(runtimeRoot, metadataEditorWebviewTemplateFileName),
    )
    return Buffer.from(bytes).toString('utf8')
  }

  private getAmisTheme(): 'cxd' | 'dark' {
    const kind = vscode.window.activeColorTheme.kind
    return kind === vscode.ColorThemeKind.Dark || kind === vscode.ColorThemeKind.HighContrast ? 'dark' : 'cxd'
  }

  private getEditorLocale(): 'zh-CN' | 'en-US' {
    return normalizeIdeLocale(vscode.env.language)
  }

  private requestWebviewFlush(
    webviewPanel: vscode.WebviewPanel,
    pendingFlushRequests: Map<string, PendingFlushRequest>,
  ): Promise<void> {
    const requestId = randomBytes(8).toString('hex')
    return new Promise((resolve, reject) => {
      const timeout = setTimeout(() => {
        pendingFlushRequests.delete(requestId)
        reject(new Error('Timed out while flushing pending metadata editor changes.'))
      }, 10000)
      pendingFlushRequests.set(requestId, { resolve, reject, timeout })
      void webviewPanel.webview.postMessage({ type: 'flushPendingChanges', requestId }).then((posted) => {
        if (!posted) {
          const pending = pendingFlushRequests.get(requestId)
          if (pending) {
            pendingFlushRequests.delete(requestId)
            clearTimeout(pending.timeout)
            pending.reject(new Error('Unable to reach the metadata visual editor.'))
          }
        }
      }, (error) => {
        const pending = pendingFlushRequests.get(requestId)
        if (pending) {
          pendingFlushRequests.delete(requestId)
          clearTimeout(pending.timeout)
          pending.reject(error)
        }
      })
    })
  }

  private rejectPendingFlushRequests(
    pendingFlushRequests: Map<string, PendingFlushRequest>,
    error: Error,
  ): void {
    for (const [requestId, pending] of pendingFlushRequests) {
      pendingFlushRequests.delete(requestId)
      clearTimeout(pending.timeout)
      pending.reject(error)
    }
  }
}
