import { expect, type Page } from '@playwright/test'

export type WebApiResult<T> = {
  code?: number
  data?: T | null
  message?: string
  result?: T
}

export type LoginCredential = {
  account: string
  password: string
}

export function runtimeFrontendBaseUrl(): string {
  const baseUrl = process.env.OB_TEST_RUNTIME_FRONTEND_BASE_URL
  if (!baseUrl) {
    throw new Error('OB_TEST_RUNTIME_FRONTEND_BASE_URL is required')
  }
  return baseUrl.replace(/\/$/, '')
}

export function businessModelPath(fullName: string): string {
  const path = fullName
    .split('.')
    .map((part) => part.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase())
    .join('/')
  return `/${path}`
}

export function businessModelApiPath(fullName: string): string {
  return `/api${businessModelPath(fullName)}`
}

export function businessModelRuntimeRoute(fullName: string): string {
  return `${runtimeFrontendBaseUrl()}${businessModelPath(fullName)}`
}

export async function expectNotFoundShellAbsent(page: Page): Promise<void> {
  await expect(page.getByText('页面未找到')).toHaveCount(0)
}

export function runtimeAdminCredential(): LoginCredential {
  return {
    account: process.env.OB_TEST_ADMIN_ACCOUNT ?? 'admin',
    password: process.env.OB_TEST_ADMIN_PASSWORD ?? '666666',
  }
}

export function runtimeRestrictedCredential(): LoginCredential | null {
  const account = process.env.OB_TEST_RESTRICTED_ACCOUNT
  const password = process.env.OB_TEST_RESTRICTED_PASSWORD
  if (!account && !password) {
    return null
  }
  if (!account || !password) {
    throw new Error('OB_TEST_RESTRICTED_ACCOUNT and OB_TEST_RESTRICTED_PASSWORD are required together')
  }
  return {
    account,
    password,
  }
}

export function runtimeScopedReaderCredential(): LoginCredential {
  return {
    account: 'srt-scoped-reader',
    password: process.env.OB_TEST_SRT_SCOPED_READER_PASSWORD ?? 'SrtScopedReader@2026!',
  }
}

export async function login(page: Page, baseUrl: string, credential: LoginCredential): Promise<void> {
  await page.goto(baseUrl)
  const loginResult = await page.evaluate(
    async ({ account, password }: LoginCredential) => {
      const apiHost = window.sessionStorage.getItem('apiHost') ?? window.location.origin
      const response = await fetch(new URL('/api/auth/login', apiHost).toString(), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({ account, password }),
        credentials: 'include',
      })
      const text = await response.text()
      let json: unknown = null
      try {
        json = JSON.parse(text)
      } catch (error) {
        json = { parseError: String(error) }
      }
      if (!response.ok) {
        throw new Error(`login failed with HTTP ${response.status}: ${text}`)
      }
      const jsonRecord = typeof json === 'object' && json !== null && !Array.isArray(json)
        ? json as Record<string, unknown>
        : null
      if (jsonRecord && 'code' in jsonRecord && jsonRecord.code !== 0) {
        throw new Error(`login failed with code=${String(jsonRecord.code)}: ${text}`)
      }
      const data = jsonRecord?.data ?? jsonRecord?.result ?? json
      window.localStorage.setItem('Ouroboros_token', JSON.stringify(data))
      return { text }
    },
    credential,
  )
  expect(loginResult.text).toContain('accessToken')
  await page.goto(baseUrl)
  await page.waitForLoadState('networkidle')
  await expect(page).not.toHaveURL(/\/login(?:$|[?#/])/)
}

export async function browserFetchJson<T>(page: Page, apiPath: string, init: { method?: string; body?: unknown } = {}): Promise<T> {
  return page.evaluate(
    async ({ fetchPath, fetchInit }: { fetchPath: string; fetchInit: { method?: string; body?: unknown } }) => {
      const parseStoredToken = (value: string | null): unknown => {
        if (!value) {
          return null
        }
        try {
          return JSON.parse(value)
        } catch (error) {
          throw new Error(`Failed to parse stored Ouroboros token: ${String(error)}`)
        }
      }

      const asObjectRecord = (value: unknown): Record<string, unknown> | null => {
        if (typeof value !== 'object' || value === null || Array.isArray(value)) {
          return null
        }
        return value as Record<string, unknown>
      }

      const extractTokenValue = (value: unknown): string | null => {
        if (typeof value === 'string') {
          return value
        }
        const record = asObjectRecord(value)
        if (!record) {
          return null
        }
        const candidate = record.token ?? record.access_token ?? record.value
        return typeof candidate === 'string' ? candidate : null
      }

      const tokenPayload = parseStoredToken(
        window.localStorage.getItem('Ouroboros_token') ?? window.sessionStorage.getItem('Ouroboros_token'),
      )
      const tokenRecord = asObjectRecord(tokenPayload)
      const accessToken = tokenRecord
        ? extractTokenValue(tokenRecord.accessToken ?? tokenRecord.access_token)
        : null
      const apiHost = window.sessionStorage.getItem('apiHost') ?? window.location.origin
      const requestUrl = fetchPath.startsWith('http') ? fetchPath : new URL(fetchPath, apiHost).toString()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      }
      if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`
      }
      const response = await fetch(requestUrl, {
        method: fetchInit.method ?? 'GET',
        headers,
        body: fetchInit.body === undefined ? undefined : JSON.stringify(fetchInit.body),
        credentials: 'include',
      })
      const text = await response.text()
      let json: unknown = null
      try {
        json = JSON.parse(text)
      } catch {
        json = null
      }
      if (!response.ok) {
        throw new Error(`request failed with HTTP ${response.status}: ${text}`)
      }
      const jsonRecord = asObjectRecord(json)
      if (jsonRecord && 'code' in jsonRecord && jsonRecord.code !== 0) {
        throw new Error(`request failed with code=${String(jsonRecord.code)}: ${text}`)
      }
      return (jsonRecord?.data ?? jsonRecord?.result ?? json ?? null) as T
    },
    { fetchPath: apiPath, fetchInit: init },
  )
}

export async function browserFetchRawResult<T>(page: Page, apiPath: string, init: { method?: string; body?: unknown } = {}): Promise<WebApiResult<T>> {
  const rawResult = await page.evaluate(
    async ({ fetchPath, fetchInit }: { fetchPath: string; fetchInit: { method?: string; body?: unknown } }) => {
      const parseStoredToken = (value: string | null): unknown => {
        if (!value) {
          return null
        }
        try {
          return JSON.parse(value)
        } catch (error) {
          throw new Error(`Failed to parse stored Ouroboros token: ${String(error)}`)
        }
      }

      const asObjectRecord = (value: unknown): Record<string, unknown> | null => {
        if (typeof value !== 'object' || value === null || Array.isArray(value)) {
          return null
        }
        return value as Record<string, unknown>
      }

      const extractTokenValue = (value: unknown): string | null => {
        if (typeof value === 'string') {
          return value
        }
        const record = asObjectRecord(value)
        if (!record) {
          return null
        }
        const candidate = record.token ?? record.access_token ?? record.value
        return typeof candidate === 'string' ? candidate : null
      }

      const tokenPayload = parseStoredToken(
        window.localStorage.getItem('Ouroboros_token') ?? window.sessionStorage.getItem('Ouroboros_token'),
      )
      const tokenRecord = asObjectRecord(tokenPayload)
      const accessToken = tokenRecord
        ? extractTokenValue(tokenRecord.accessToken ?? tokenRecord.access_token)
        : null
      const apiHost = window.sessionStorage.getItem('apiHost') ?? window.location.origin
      const requestUrl = fetchPath.startsWith('http') ? fetchPath : new URL(fetchPath, apiHost).toString()
      const headers: Record<string, string> = {
        'Content-Type': 'application/json',
      }
      if (accessToken) {
        headers.Authorization = `Bearer ${accessToken}`
      }
      const response = await fetch(requestUrl, {
        method: fetchInit.method ?? 'GET',
        headers,
        body: fetchInit.body === undefined ? undefined : JSON.stringify(fetchInit.body),
        credentials: 'include',
      })
      const text = await response.text()
      let json: unknown = null
      try {
        json = JSON.parse(text)
      } catch {
        json = null
      }
      const jsonRecord = asObjectRecord(json)
      const rawResult: WebApiResult<unknown> = jsonRecord
        ? {
            code: typeof jsonRecord.code === 'number' ? jsonRecord.code : response.ok ? undefined : -response.status,
            data: jsonRecord.data,
            message: typeof jsonRecord.message === 'string'
              ? jsonRecord.message
              : typeof jsonRecord.msg === 'string' ? jsonRecord.msg : undefined,
            result: jsonRecord.result,
          }
        : { code: response.ok ? undefined : -response.status, message: text }
      return rawResult
    },
    { fetchPath: apiPath, fetchInit: init },
  )
  return {
    code: rawResult.code,
    data: rawResult.data as T | null | undefined,
    message: rawResult.message,
    result: rawResult.result as T | undefined,
  }
}

type JsonResponse = {
  json(): Promise<unknown>
}

export async function readWebApiData<T>(response: JsonResponse): Promise<T> {
  const payload = await response.json()
  const webApiResult = payload as WebApiResult<T>
  return (webApiResult.data ?? webApiResult.result ?? payload) as T
}
