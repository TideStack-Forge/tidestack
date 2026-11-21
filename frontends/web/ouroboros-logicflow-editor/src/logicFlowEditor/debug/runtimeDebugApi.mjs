export function createRuntimeDebugApi(fetcher, appName) {
  const base = `/api/dev/${encodeURIComponent(appName)}/logicflow/debug`
  async function request(path, method = 'get', data) {
    if (typeof fetcher !== 'function') throw new Error('平台请求能力尚未就绪')
    const result = await fetcher({ url: base + path, method, data }, data)
    if (result?.status !== 0) throw new Error(result?.msg || result?.message || '调试请求失败')
    return result.data
  }
  const id = encodeURIComponent
  async function list(path) {
    const data = await request(path)
    // AMIS normalizes array responses to its items contract.
    const items = data?.items ?? data
    if (!Array.isArray(items)) throw new Error('调试列表返回数据格式错误')
    return items
  }
  return {
    targets: () => list('/targets'),
    captures: (flowName) => list(`/captures?flowName=${id(flowName)}`),
    start: (data) => request('/captures', 'post', data),
    read: (captureId) => request(`/captures/${id(captureId)}`),
    renew: (captureId) => request(`/captures/${id(captureId)}/renew`, 'post'),
    stop: (captureId) => request(`/captures/${id(captureId)}/stop`, 'post'),
    events: (captureId, runId, afterSeq) => request(
      `/captures/${id(captureId)}/runs/${id(runId)}/events?afterSeq=${afterSeq}&limit=200`
    ),
    graph: (captureId, graphId) => request(`/captures/${id(captureId)}/graphs/${id(graphId)}`),
  }
}
