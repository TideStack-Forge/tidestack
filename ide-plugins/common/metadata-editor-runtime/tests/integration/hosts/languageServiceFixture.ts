/** Deterministic HTTP/LSP peer; the real ScriptEditor and host client own the UI and transport. */
export class LanguageServiceFixture {
  private sequence = 0
  private sessions = new Map<string, { uri: string; text: string; version: number }>()

  respond(path: string, method: string | undefined, body: any): unknown {
    if (path.endsWith('/sessions') && body?.context) {
      const sessionId = `lang-${++this.sequence}`
      const session = { uri: `file:///fixture/${sessionId}.groovy`, text: body.text, version: body.context.documentVersion }
      this.sessions.set(sessionId, session)
      return { sessionId, uri: session.uri, messages: [
        { jsonrpc: '2.0', id: 'initialize', result: { capabilities: { completionProvider: { triggerCharacters: ['.'] }, hoverProvider: true } } },
        this.diagnostics(session),
      ] }
    }
    const match = path.match(/\/sessions\/(lang-\d+)(?:\/(.*))?$/)
    if (!match || !this.sessions.has(match[1])) return undefined
    const session = this.sessions.get(match[1])!
    if (method === 'DELETE') { this.sessions.delete(match[1]); return {} }
    const messages: unknown[] = []
    if (match[2] === 'messages') for (const message of body) {
      if (message.method === 'textDocument/didChange') {
        session.text = message.params.contentChanges[0].text
        session.version = message.params.textDocument.version
      }
      if (message.id) messages.push({ jsonrpc: '2.0', id: message.id, result:
        message.method === 'textDocument/completion' ? [{ label: 'fixtureResult', kind: 6, insertText: 'fixtureResult', detail: 'Fixture backend completion' }] :
          { contents: 'Fixture backend hover' },
      })
    }
    messages.push(this.diagnostics(session))
    return { messages }
  }

  private diagnostics(session: { uri: string; text: string; version: number }) {
    return { jsonrpc: '2.0', method: 'textDocument/publishDiagnostics', params: {
      uri: session.uri, version: session.version,
      diagnostics: session.text.includes('BROKEN') ? [{ severity: 1, source: 'fixture-language-service', message: 'Unknown symbol BROKEN',
        range: { start: { line: 0, character: 0 }, end: { line: 0, character: 6 } } }] : [],
    } }
  }
}
