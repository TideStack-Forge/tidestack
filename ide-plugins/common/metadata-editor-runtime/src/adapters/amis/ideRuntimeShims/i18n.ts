const manager = {
  getLocale: () => (window as any).OuroborosMetadataEditorHost?.locale || 'zh-CN',
  onLocaleChange: (_listener: () => void) => () => undefined,
  tCommon: (key: string, fallback?: string) => fallback || key,
  tn: (_schemaId: string, _key: string, fallback: string) => fallback,
}

export const i18nService = {
  getManager: () => manager,
}

export default i18nService

export function useI18n() { return manager }
