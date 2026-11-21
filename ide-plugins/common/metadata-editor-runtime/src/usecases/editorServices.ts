import type { JsonObject } from './developConfig'

export interface EditorConnection {
  url: string
  appName: string
  accessToken: string
  devKey?: string
}
export interface EditorServiceRequest { url: string; method?: string; data?: unknown }

/** Credentials remain in the IDE host; development keys use the platform authentication extension. */
export function editorConnection(project: JsonObject, local: JsonObject): EditorConnection | undefined {
  const string = (value: unknown): string => typeof value === 'string' ? value.trim() : ''
  const localServiceUrl = string(local.editorServiceUrl)
  const url = localServiceUrl || string(project.editorServiceUrl) || string(project.developmentServerUrl) || string(project.platformUrl)
  if (!url) return undefined
  const appName = string(project.appName)
  if (!/^[A-Za-z0-9_.-]+$/.test(appName)) throw new Error('Configure appName in .tidestack/develop.json')
  const parsed = new URL(url)
  if (!['http:', 'https:'].includes(parsed.protocol) || parsed.username || parsed.password || parsed.search || parsed.hash) throw new Error('Invalid editor service URL')
  const accessToken = string(local.editorAccessToken)
  const devKey = string(local.devKey) || string(local.debugKey)
  return {
    url: parsed.toString().replace(/\/$/, ''),
    appName,
    accessToken,
    devKey,
  }
}

/** Allow only editor APIs, within the configured application. Never proxy arbitrary webview URLs. */
export function editorServicePath(appName: string, request: EditorServiceRequest): { path: string; method: string } {
  if (!request || typeof request.url !== 'string' || !request.url.startsWith('/') || /[\\#]|%2e|%2f|%5c/i.test(request.url)
      || request.url.split(/[/?]/).some(part => part === '.' || part === '..')) throw new Error('Invalid editor API path')
  const parsed = new URL(request.url, 'http://editor.invalid')
  if (parsed.origin !== 'http://editor.invalid') throw new Error('External editor API URL is forbidden')
  const method = (request.method || 'GET').toUpperCase()
  const dev = `/api/dev/${encodeURIComponent(appName)}/`
  const app = `/api/app/${encodeURIComponent(appName)}/`
  const path = parsed.pathname
  let allowed = false
  if (path.startsWith(dev)) {
    const route = path.slice(dev.length)
    allowed = method === 'POST' && /^code-intelligence\/(context|sessions|sessions\/[A-Za-z0-9-]+\/(messages|context))$/.test(route)
      || method === 'GET' && (/^code-intelligence\/sessions\/[A-Za-z0-9-]+\/notifications$/.test(route)
        || /^(code-intelligence\/environment|logicflow\/node-define|approval-workflow\/(candidate(?:\/[^/]+\/(?:items|category))?|form-fields))$/.test(route))
      || method === 'DELETE' && /^code-intelligence\/sessions\/[A-Za-z0-9-]+$/.test(route)
  }
  if (path.startsWith(app)) allowed = method === 'GET' && /^field-types\/(primary|normal|[^/]+\/ui-schema)$/.test(path.slice(app.length))
  if (!allowed) throw new Error('Unsupported editor API or application')
  if (method === 'GET' && request.data && typeof request.data === 'object') {
    const append = (name: string, value: unknown): void => {
      if (value === undefined || value === null) return
      if (Array.isArray(value)) value.forEach(item => append(name.endsWith('[]') ? name : name + '[]', item))
      else if (typeof value === 'object') Object.entries(value).forEach(([key, item]) => append(`${name}[${key}]`, item))
      else parsed.searchParams.append(name, String(value))
    }
    Object.entries(request.data).forEach(([name, value]) => append(name, value))
  }
  return { path: parsed.pathname + parsed.search, method }
}

/** One client per editor: owns its HTTP session, requests and language sessions. */
export class EditorServiceClient {
  private cookie = ''
  private readonly pending = new Set<AbortController>()
  private readonly sessions = new Set<string>()
  private disposed = false
  // Session creation is serialized so concurrent requests cannot create different HTTP owners.
  private initialize: Promise<void> = Promise.resolve()
  constructor(readonly connection: EditorConnection, private readonly fetcher: typeof fetch = fetch) {}

  async request(request: EditorServiceRequest): Promise<unknown> {
    if (this.disposed) throw new Error('Editor service client is closed')
    const route = editorServicePath(this.connection.appName, request)
    const previous = this.initialize
    let initialized!: () => void
    if (!this.cookie) this.initialize = new Promise(resolve => { initialized = resolve })
    await previous
    try {
      if (this.disposed) throw new Error('Editor service client is closed')
      return await this.send(route.path, route.method, request.data)
    }
    finally { initialized?.() }
  }
  private async send(path: string, method: string, data?: unknown): Promise<unknown> {
    const controller = new AbortController()
    this.pending.add(controller)
    const timeout = setTimeout(() => controller.abort(), method === 'DELETE' ? 5000 : 125000)
    try {
      const headers: Record<string, string> = { Accept: 'application/json' }
      if (this.connection.accessToken) headers.Authorization = `Bearer ${this.connection.accessToken}`
      else if (this.connection.devKey) headers['X-TideStack-Dev-Key'] = this.connection.devKey
      if (this.cookie) headers.Cookie = this.cookie
      if (data !== undefined && method !== 'GET') headers['Content-Type'] = 'application/json'
      const response = await this.fetcher(this.connection.url + path, {
        method, headers, redirect: 'manual', signal: controller.signal,
        body: data !== undefined && method !== 'GET' ? JSON.stringify(data) : undefined,
      })
      const cookie = response.headers.get('set-cookie')?.match(/(?:^|,\s*)(JSESSIONID=[^;,]+)/)?.[1]
      if (cookie) this.cookie = cookie
      if (!response.ok) throw Object.assign(new Error(`Editor service HTTP ${response.status}${response.status === 401 ? ': development key or editor token is invalid or expired' : ''}`), { code: `http-${response.status}` })
      const payload = await response.json()
      if (!payload || typeof payload !== 'object' || typeof (payload.status ?? payload.code) !== 'number')
        throw new Error('Invalid editor service response')
      payload.status = payload.status ?? payload.code
      if (method === 'POST' && /\/sessions$/.test(path) && payload?.status === 0 && typeof payload.data?.sessionId === 'string') {
        const id = payload.data.sessionId
        if (this.disposed) {
          await this.send(path + '/' + encodeURIComponent(id), 'DELETE')
          throw new Error('Editor closed while opening a language session')
        }
        this.sessions.add(path + '/' + encodeURIComponent(id))
      }
      if (method === 'DELETE') this.sessions.delete(path)
      return payload
    } finally { clearTimeout(timeout); this.pending.delete(controller) }
  }
  async dispose(): Promise<void> {
    this.disposed = true
    this.pending.forEach(controller => controller.abort())
    const results = await Promise.allSettled([...this.sessions].map(path => this.send(path, 'DELETE')))
    results.forEach(result => { if (result.status === 'rejected') console.warn('Could not close editor language session', result.reason) })
    this.sessions.clear()
  }
}
