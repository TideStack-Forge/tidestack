import type { MetadataEditorState } from '../../core/types'

export interface MetadataEditorWebviewHtmlOptions {
  template: string
  fileName: string
  nonce?: string
  cspSource?: string
  amisSdkScriptUri: string
  amisSdkCssUri: string
  amisThemeCssUri: string
  amisDarkThemeCssUri: string
  amisEditorRuntimeScriptUri?: string
  amisEditorCssUri?: string
  editorIntegrationScriptUri?: string
  editorBundleScriptUris?: string[]
  editorBundleStyleUris?: string[]
  xlsxRuntimeScriptUri?: string
  runtimeScriptUri: string
  hostRuntimeScriptUri?: string
  hostScript: string
}

export const metadataEditorRuntimeFileName = 'metadata-editor-runtime.js'
export const metadataEditorWebviewTemplateFileName = 'metadata-editor-webview.html'

export interface VsCodeMetadataEditorHostScriptOptions {
  initialState: MetadataEditorState
  theme: 'cxd' | 'dark'
  locale: 'zh-CN' | 'en-US'
  service?: { appName: string }
}

export function createVsCodeMetadataEditorHostScript(options: VsCodeMetadataEditorHostScriptOptions): string {
  return `
    const vscode = acquireVsCodeApi();
    let nextRequestId = 1;
    const pendingRequests = new Map();
    function requestHost(type, payload) {
      const requestId = String(nextRequestId++);
      vscode.postMessage({ ...payload, type, requestId });
      return new Promise((resolve, reject) => {
        const timeout = setTimeout(() => { pendingRequests.delete(requestId); reject(new Error('Editor host request timed out')); }, 130000);
        pendingRequests.set(requestId, { resolve, reject, timeout });
      });
    }
    window.OuroborosMetadataEditorHost = {
      initialState: ${toScriptJson(options.initialState)},
      theme: ${JSON.stringify(options.theme)},
      locale: ${JSON.stringify(options.locale)},
      service: ${toScriptJson(options.service ?? null)},
      requestService: request => requestHost('editorService', { request }),
      transport: {
        saveText: (text, revision) => requestHost('saveText', { text, revision }),
        validate: (text) => requestHost('validate', { text }),
        loadState: () => requestHost('loadState', {})
      },
      connect: editorApp => {
        window.addEventListener('message', event => {
          if (event.data.type === 'response') {
            const pending = pendingRequests.get(event.data.requestId);
            if (pending) {
              clearTimeout(pending.timeout);
              pendingRequests.delete(event.data.requestId);
              if (event.data.ok) {
                pending.resolve(event.data.result);
              } else {
                const error = Object.assign(
                  new Error(event.data.error || 'Metadata host request failed'),
                  { code: event.data.errorCode || 'host-error' }
                );
                pending.reject(error);
              }
            }
          }
          if (event.data.type === 'state') {
            editorApp.applyState(event.data);
          }
          if (event.data.type === 'diagnostics') {
            editorApp.applyDiagnostics(event.data.diagnostics || []);
          }
          if (event.data.type === 'theme') {
            editorApp.setTheme(event.data.theme);
          }
          if (event.data.type === 'flushPendingChanges') {
            Promise.resolve(
              typeof editorApp.flushPendingChanges === 'function' ? editorApp.flushPendingChanges() : undefined
            ).then(() => {
              vscode.postMessage({ type: 'flushPendingChangesResult', requestId: event.data.requestId, ok: true });
            }).catch(error => {
              vscode.postMessage({
                type: 'flushPendingChangesResult',
                requestId: event.data.requestId,
                ok: false,
                error: error && error.message ? error.message : String(error)
              });
            });
          }
        });
        vscode.postMessage({ type: 'ready' });
      }
    };`
}

export function createMetadataEditorWebviewHtml(options: MetadataEditorWebviewHtmlOptions): string {
  const nonce = options.nonce ?? ''
  const cspSource = options.cspSource ?? 'self'
  const nonceAttribute = nonce ? ` nonce="${escapeHtml(nonce)}"` : ''
  const needsUnsafeEval = Boolean(options.amisEditorRuntimeScriptUri || options.editorBundleScriptUris?.length)
  const evalPolicy = needsUnsafeEval ? " 'unsafe-eval'" : ''
  const workerPolicy = options.amisEditorRuntimeScriptUri ? ' worker-src blob:;' : ''
  const scriptPolicy = nonce ? `${cspSource} 'nonce-${escapeHtml(nonce)}'${evalPolicy}` : `${cspSource} 'unsafe-inline'${evalPolicy}`
  const cspMeta = `<meta http-equiv="Content-Security-Policy" content="default-src 'none'; connect-src data:; img-src ${cspSource} data:; font-src ${cspSource} data:; style-src ${cspSource} 'unsafe-inline'; script-src ${scriptPolicy};${workerPolicy}">`
  const hostRuntimeScriptTag = options.hostRuntimeScriptUri
    ? `<script${nonceAttribute} src="${escapeHtml(options.hostRuntimeScriptUri)}"></script>`
    : ''
  const xlsxRuntimeScriptTag = options.xlsxRuntimeScriptUri
    ? `<script${nonceAttribute} src="${escapeHtml(options.xlsxRuntimeScriptUri)}"></script>`
    : ''
  const editorIntegrationScriptTag = options.editorIntegrationScriptUri
    ? `<script${nonceAttribute} src="${escapeHtml(options.editorIntegrationScriptUri)}"></script>`
    : ''
  const editorBundleScriptTags = (options.editorBundleScriptUris ?? [])
    .map((uri) => `<script${nonceAttribute} src="${escapeHtml(uri)}"></script>`)
    .join('\n  ')
  const editorBundleCssLinks = (options.editorBundleStyleUris ?? [])
    .map((uri) => `<link rel="stylesheet" href="${escapeHtml(uri)}" />`)
    .join('\n  ')
  const reactGlobalsScriptTag = `<script${nonceAttribute}>
    (function () {
      if (!window.amisRequire) return;
      try { window.React = window.React || window.amisRequire('react'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react.', _error); }
      try { window.ReactDOM = window.ReactDOM || window.amisRequire('react-dom/client'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react-dom/client.', _error); }
      try { window.ReactDOM = window.ReactDOM || window.amisRequire('react-dom'); } catch (_error) { console.debug('Metadata editor React shim fallback failed for react-dom.', _error); }
    })();
  </script>`
  const replacements: Record<string, string> = {
    '{{CSP_META}}': cspMeta,
    '{{NONCE_ATTRIBUTE}}': nonceAttribute,
    '{{AMIS_SDK_SCRIPT_URI}}': escapeHtml(options.amisSdkScriptUri),
    '{{AMIS_SDK_CSS_URI}}': escapeHtml(options.amisSdkCssUri),
    '{{AMIS_THEME_CSS_URI}}': escapeHtml(options.amisThemeCssUri),
    '{{AMIS_DARK_THEME_CSS_URI}}': escapeHtml(options.amisDarkThemeCssUri),
    '{{AMIS_EDITOR_CSS_LINK}}': options.amisEditorCssUri
      ? `<link rel="stylesheet" href="${escapeHtml(options.amisEditorCssUri)}" />`
      : '',
    '{{EDITOR_BUNDLE_CSS_LINKS}}': editorBundleCssLinks,
    '{{RUNTIME_SCRIPT_URI}}': escapeHtml(options.runtimeScriptUri),
    '{{AMIS_EDITOR_RUNTIME_SCRIPT_TAG}}': options.amisEditorRuntimeScriptUri
      ? `<script${nonceAttribute} src="${escapeHtml(options.amisEditorRuntimeScriptUri)}"></script>`
      : '',
    '{{XLSX_RUNTIME_SCRIPT_TAG}}': xlsxRuntimeScriptTag,
    '{{REACT_GLOBALS_SCRIPT_TAG}}': reactGlobalsScriptTag,
    '{{EDITOR_INTEGRATION_SCRIPT_TAG}}': editorIntegrationScriptTag,
    '{{EDITOR_BUNDLE_SCRIPT_TAGS}}': editorBundleScriptTags,
    '{{HOST_RUNTIME_SCRIPT_TAG}}': hostRuntimeScriptTag,
    '{{HOST_SCRIPT}}': options.hostScript,
  }
  const html = Object.entries(replacements).reduce(
    (current, [token, value]) => current.replaceAll(token, value),
    options.template,
  )
  if (/\{\{[A-Z_]+\}\}/.test(html)) {
    throw new Error('Metadata editor webview template contains unresolved placeholders')
  }
  return html
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function toScriptJson(value: unknown): string {
  return JSON.stringify(value)
    .replace(/</g, '\\u003c')
    .replace(/>/g, '\\u003e')
    .replace(/&/g, '\\u0026')
    .replace(/\u2028/g, '\\u2028')
    .replace(/\u2029/g, '\\u2029')
}
