import { describe, expect, it, vi } from 'vitest'
import { createServer } from 'node:http'
import { once } from 'node:events'
import { EditorServiceClient, editorConnection, editorServicePath } from '../../src/usecases/editorServices'
import { Client } from '../../src/adapters/amis/ideRuntimeShims/http'

describe('IDE editor services', () => {
  it('resolves private overrides and uses the configured development key without another credential', () => {
    expect(editorConnection({ appName: 'app', developmentServerUrl: 'http://platform/base' },
      { editorServiceUrl: 'http://localhost:19762', editorAccessToken: 'token', devKey: 'deployment-key' }))
      .toEqual({ appName: 'app', url: 'http://localhost:19762', accessToken: 'token', devKey: 'deployment-key' })
    expect(editorConnection({ appName: 'app', developmentServerUrl: 'http://platform' }, { devKey: 'key' }))
      .toEqual({ appName: 'app', url: 'http://platform', accessToken: '', devKey: 'key' })
    expect(editorConnection({}, {})).toBeUndefined()
    expect(() => editorConnection({ appName: 'app', developmentServerUrl: 'http://user:secret@host' }, {})).toThrow()
  })
  it('authenticates requests and session cleanup using only devKey, including after HTTP session expiry', async () => {
    const credentials: Array<{ key: string | undefined; bearer: string | undefined; cookie: string | undefined }> = []
    let owner = 0
    const server = createServer((request, response) => {
      credentials.push({ key: request.headers['x-tidestack-dev-key'] as string | undefined,
        bearer: request.headers.authorization, cookie: request.headers.cookie })
      response.setHeader('Content-Type', 'application/json')
      if (request.method === 'POST') {
        response.setHeader('Set-Cookie', `JSESSIONID=owner-${++owner}; HttpOnly; Path=/`)
        response.end(JSON.stringify({ code: 0, data: { sessionId: `session-${owner}` } }))
      } else response.end(JSON.stringify({ code: 0 }))
    })
    server.listen(0, '127.0.0.1'); await once(server, 'listening')
    const connection = editorConnection({ appName: 'app', developmentServerUrl: `http://127.0.0.1:${(server.address() as { port: number }).port}` }, { devKey: 'private-key' })!
    const client = new EditorServiceClient(connection)
    try {
      const open = { url: '/api/dev/app/code-intelligence/sessions', method: 'POST' }
      await client.request(open)
      await client.request(open)
      await client.request({ url: '/api/dev/app/logicflow/node-define' })
      await client.dispose()
      expect(credentials).toHaveLength(5)
      expect(credentials.every(request => request.key === 'private-key' && request.bearer === undefined)).toBe(true)
      expect(credentials[1].cookie).toBe('JSESSIONID=owner-1')
      expect(credentials[2].cookie).toBe('JSESSIONID=owner-2')
    } finally { await client.dispose(); server.closeAllConnections(); server.close() }
  })
  it('prefers an explicit service token and never combines it with a development key', async () => {
    const connection = editorConnection({ appName: 'app', developmentServerUrl: 'http://platform' },
      { editorServiceUrl: 'http://localhost', editorAccessToken: 'service-token', devKey: 'private-key' })!
    const client = new EditorServiceClient(connection, async (_url, options) => {
      const headers = options!.headers as Record<string, string>
      expect(headers.Authorization).toBe('Bearer service-token')
      expect(headers['X-TideStack-Dev-Key']).toBeUndefined()
      return new Response(JSON.stringify({ code: 0 }))
    })
    await client.request({ url: '/api/dev/app/logicflow/node-define' })
    await client.dispose()
  })
  it('only forwards editor operations for the configured application', () => {
    for (const url of ['https://evil/steal', '//evil/steal', '/api/dev/other/code-intelligence/context',
      '/api/dev/app/../other/code-intelligence/context', '/api/dev/app/%2e%2e/other', '/api/dev/app/deploy']) {
      expect(() => editorServicePath('app', { url, method: 'POST' })).toThrow()
    }
    expect(editorServicePath('app', { url: '/api/dev/app/code-intelligence/sessions/a-1/messages', method: 'post' }).method).toBe('POST')
    expect(() => editorServicePath('app', { url: '/api/dev/app/logicflow/node-define', method: 'DELETE' })).toThrow()
  })
  it('retains HTTP session ownership, authentication and context paths, then releases language sessions', async () => {
    const requests: Array<{ path: string; method?: string; cookie?: string; authorization?: string }> = []
    const server = createServer((request, response) => {
      requests.push({ path: request.url!, method: request.method, cookie: request.headers.cookie, authorization: request.headers.authorization })
      response.setHeader('Content-Type', 'application/json')
      if (request.url === '/base/api/dev/app/code-intelligence/sessions' && request.method === 'POST') {
        response.setHeader('Set-Cookie', 'JSESSIONID=owner; HttpOnly; Path=/base')
        response.end(JSON.stringify({ code: 0, data: { sessionId: 'session-1' } }))
      } else response.end(JSON.stringify({ code: 0, data: { messages: [] } }))
    })
    server.listen(0, '127.0.0.1'); await once(server, 'listening')
    const address = server.address() as { port: number }
    const client = new EditorServiceClient({ appName: 'app', url: `http://127.0.0.1:${address.port}/base`, accessToken: 'private-token' })
    try {
      await client.request({ url: '/api/dev/app/code-intelligence/sessions', method: 'POST', data: { draft: 'unsaved' } })
      await client.request({ url: '/api/dev/app/code-intelligence/sessions/session-1/notifications' })
      await client.dispose()
      expect(requests.map(item => item.authorization)).toEqual(['Bearer private-token', 'Bearer private-token', 'Bearer private-token'])
      expect(requests[1].cookie).toBe('JSESSIONID=owner')
      expect(requests[2].path).toBe('/base/api/dev/app/code-intelligence/sessions/session-1')
      expect(requests[2].method).toBe('DELETE')
      await expect(client.request({ url: '/api/dev/app/code-intelligence/environment' })).rejects.toThrow('closed')
    } finally { server.closeAllConnections(); server.close() }
  })
  it('rejects redirects and authentication errors without following another host', async () => {
    for (const status of [302, 401, 503]) {
      const client = new EditorServiceClient({ url: 'http://localhost', appName: 'app', accessToken: 'secret' },
        async (_url, options) => { expect(options?.redirect).toBe('manual'); return new Response('', { status }) })
      await expect(client.request({ url: '/api/dev/app/code-intelligence/environment' })).rejects.toThrow(`HTTP ${status}`)
      await client.dispose()
    }
  })
  it('preserves GET search parameters and selected-value arrays without sending a request body', async () => {
    const client = new EditorServiceClient({ appName: 'app', url: 'http://localhost/base', accessToken: 'token' },
      async (url, options) => {
        const query = new URL(String(url)).searchParams
        expect(query.get('existing')).toBe('kept')
        expect(query.get('keyword')).toBe('Alice & Bob')
        expect(query.getAll('value[]')).toEqual(['user-1', 'user-2'])
        expect(query.get('category')).toBe('部门')
        expect(options?.body).toBeUndefined()
        return new Response(JSON.stringify({ code: 0, data: { items: [] } }))
      })
    vi.stubGlobal('window', { OuroborosMetadataEditorHost: { requestService: client.request.bind(client) } })
    try {
      await Client.get('/api/dev/app/approval-workflow/candidate/User/items?existing=kept',
        { keyword: 'Alice & Bob', value: ['user-1', 'user-2'], category: '部门' })
    } finally { vi.unstubAllGlobals(); await client.dispose() }
  })
  it('keeps parallel language sessions under one HTTP owner and isolates different editors', async () => {
    let nextOwner = 0
    let nextSession = 0
    const owners = new Map<string, string>()
    const deleted = new Set<string>()
    const server = createServer((request, response) => {
      response.setHeader('Content-Type', 'application/json')
      if (request.method === 'POST') {
        const owner = request.headers.cookie || `JSESSIONID=owner-${++nextOwner}`
        const sessionId = `session-${++nextSession}`
        owners.set(sessionId, owner)
        response.setHeader('Set-Cookie', `${owner}; Path=/; HttpOnly`)
        response.end(JSON.stringify({ code: 0, data: { sessionId } }))
      } else {
        const id = request.url!.split('/').pop()!
        if (owners.get(id) !== request.headers.cookie) response.statusCode = 403
        else deleted.add(id)
        response.end(JSON.stringify({ code: 0 }))
      }
    })
    server.listen(0, '127.0.0.1'); await once(server, 'listening')
    const url = `http://127.0.0.1:${(server.address() as { port: number }).port}`
    const first = new EditorServiceClient({ appName: 'app', url, accessToken: 'token' })
    const second = new EditorServiceClient({ appName: 'app', url, accessToken: 'token' })
    const open = { url: '/api/dev/app/code-intelligence/sessions', method: 'POST' }
    try {
      await Promise.all([first.request(open), first.request(open), second.request(open)])
      expect(nextOwner).toBe(2)
      expect(['JSESSIONID=owner-1', 'JSESSIONID=owner-2'].map(owner =>
        [...owners.values()].filter(value => value === owner).length).sort()).toEqual([1, 2])
      await Promise.all([first.dispose(), second.dispose()])
      expect(deleted.size).toBe(3)
    } finally { await first.dispose(); await second.dispose(); server.closeAllConnections(); server.close() }
  })

  it('releases a session returned while its editor is closing', async () => {
    let release!: () => void
    const created = new Promise<void>(resolve => { release = resolve })
    let started!: () => void
    const opening = new Promise<void>(resolve => { started = resolve })
    const methods: string[] = []
    const client = new EditorServiceClient({ appName: 'app', url: 'http://localhost', accessToken: 'token' },
      async (_url, options) => {
        methods.push(options!.method!)
        if (options!.method === 'POST') { started(); await created }
        return new Response(JSON.stringify({ code: 0, data: { sessionId: 'late-session' } }), {
          headers: { 'Set-Cookie': 'JSESSIONID=owner; Path=/' },
        })
      })
    const request = client.request({ url: '/api/dev/app/code-intelligence/sessions', method: 'POST' })
    await opening
    await client.dispose()
    release()
    await expect(request).rejects.toThrow('Editor closed')
    expect(methods).toEqual(['POST', 'DELETE'])
  })
})
