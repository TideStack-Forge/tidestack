export function clearStatisticsDatasetEditorDirty(_path?: string) {
  return undefined
}

export function markStatisticsDatasetEditorDirty(_path?: string) {
  return undefined
}

export function isStatisticsDatasetEditorPath(path: string) {
  return /^\/dev\/[^/]+\/dataset\/[^/]+$/.test(path || '')
}

export default {
  isStatisticsDatasetEditorPath,
  clearStatisticsDatasetEditorDirty,
  markStatisticsDatasetEditorDirty,
}
