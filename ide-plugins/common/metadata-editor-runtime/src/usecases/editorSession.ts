import { metadataHostErrorCode } from '../core/errors'
import { createMetadataEditorState, createMetadataEditorStateFromDocument } from './state'
import type { MetadataDiagnostic, MetadataEditorHost, MetadataEditorState } from '../core/types'

export interface MetadataEditorSessionOptions {
  host: MetadataEditorHost
  uri: string
  metadataType: string
  locale?: string
}

export interface MetadataEditorSession {
  loadState(): Promise<MetadataEditorState>
  saveText(text: string, baseRevision: string): Promise<MetadataEditorState>
  validate(text: string): Promise<MetadataDiagnostic[]>
}

export interface MetadataEditorSessionResponse {
  type: 'response'
  requestId: string
  ok: boolean
  result?: unknown
  error?: string
  errorCode?: string
}

export function createMetadataEditorSession(options: MetadataEditorSessionOptions): MetadataEditorSession {
  return {
    async loadState() {
      return createMetadataEditorState(options.host, options.uri, options.metadataType, options.locale)
    },
    async saveText(text, baseRevision) {
      const document = await options.host.applyChange(options.uri, text, baseRevision)
      return createMetadataEditorStateFromDocument(
        options.host,
        options.uri,
        options.metadataType,
        document,
        options.locale,
      )
    },
    async validate(text) {
      return options.host.validateDocument(options.uri, text)
    },
  }
}

export async function handleMetadataEditorSessionRequest(
  session: MetadataEditorSession,
  message: unknown,
): Promise<MetadataEditorSessionResponse | undefined> {
  if (!isRecord(message) || typeof message.requestId !== 'string') {
    return undefined
  }

  if (message.type === 'loadState') {
    return metadataEditorSessionResponse(message.requestId, () => session.loadState())
  }

  if (message.type === 'saveText' && typeof message.text === 'string' && typeof message.revision === 'string') {
    const text = message.text
    const revision = message.revision
    return metadataEditorSessionResponse(message.requestId, () => session.saveText(text, revision))
  }

  if (message.type === 'validate' && typeof message.text === 'string') {
    const text = message.text
    return metadataEditorSessionResponse(message.requestId, () => session.validate(text))
  }

  return undefined
}

async function metadataEditorSessionResponse(
  requestId: string,
  action: () => Promise<unknown>,
): Promise<MetadataEditorSessionResponse> {
  try {
    return {
      type: 'response',
      requestId,
      ok: true,
      result: await action(),
    }
  } catch (error) {
    return {
      type: 'response',
      requestId,
      ok: false,
      error: error instanceof Error ? error.message : String(error),
      errorCode: metadataHostErrorCode(error),
    }
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}
