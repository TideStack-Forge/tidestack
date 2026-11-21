import { createMetadataEditorHost } from '../../usecases/host'
import { MetadataRevisionConflictError } from '../../core/errors'
import { createMetadataEditorSession } from '../../usecases/editorSession'
import type { MetadataDiagnostic, MetadataEditorDocumentBridge, MetadataEditorState } from '../../core/types'

export interface BrowserMetadataDocument {
  text: string
  revision: string
}

export interface BrowserMetadataEditorHostOptions {
  uri: string
  metadataType: string
  locale?: string
  initialDocument: BrowserMetadataDocument
  saveText(text: string, baseRevision: string): Promise<{ revision: string }>
  publishDiagnostics?(diagnostics: MetadataDiagnostic[]): Promise<void>
}

export interface BrowserMetadataEditorHost {
  loadState(): Promise<MetadataEditorState>
  saveText(text: string, baseRevision: string): Promise<MetadataEditorState>
  validate(text: string): Promise<MetadataDiagnostic[]>
  applyExternalDocument(document: BrowserMetadataDocument): Promise<MetadataEditorState>
}

export function createBrowserMetadataEditorHost(options: BrowserMetadataEditorHostOptions): BrowserMetadataEditorHost {
  let currentDocument = { ...options.initialDocument }
  const bridge: MetadataEditorDocumentBridge = {
    async loadText() {
      return { ...currentDocument }
    },
    async saveText(_uri, text, baseRevision) {
      const saved = await options.saveText(text, baseRevision)
      if (currentDocument.revision !== baseRevision) {
        if (currentDocument.revision === saved.revision && currentDocument.text === text) {
          return saved
        }
        throw new MetadataRevisionConflictError(
          `Metadata document changed while saving: expected ${baseRevision}, current ${currentDocument.revision}`,
        )
      }
      currentDocument = { text, revision: saved.revision }
      return saved
    },
    async publishDiagnostics(_uri, diagnostics) {
      await options.publishDiagnostics?.(diagnostics)
    },
  }
  const host = createMetadataEditorHost(bridge)
  const session = createMetadataEditorSession({
    host,
    uri: options.uri,
    metadataType: options.metadataType,
    locale: options.locale,
  })

  return {
    async loadState() {
      return session.loadState()
    },
    async saveText(text, baseRevision) {
      return session.saveText(text, baseRevision)
    },
    async validate(text) {
      return session.validate(text)
    },
    async applyExternalDocument(document) {
      currentDocument = { ...document }
      return session.loadState()
    },
  }
}
