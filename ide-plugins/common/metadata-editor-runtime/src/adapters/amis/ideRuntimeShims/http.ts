interface ServiceHost {
  requestService?: (request: { url: string; method: string; data?: unknown }) => Promise<unknown>
}
async function request(method: string, url: string | undefined, data?: unknown) {
  const host = typeof window === 'undefined' ? undefined
    : (window as unknown as { OuroborosMetadataEditorHost?: ServiceHost }).OuroborosMetadataEditorHost
  if (!url || !host?.requestService) throw new Error('Editor service HTTP is not configured')
  return host.requestService({ url, method, data }).then(payload => ({ data: payload }))
}
export const Client = {
  get: (url?: string, data?: unknown) => request('GET', url, data),
  post: (url: string, data?: unknown) => request('POST', url, data),
  put: (url: string, data?: unknown) => request('PUT', url, data),
  patch: (url: string, data?: unknown) => request('PATCH', url, data),
  delete: (url: string) => request('DELETE', url),
}
export default Client
