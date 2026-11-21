import enUsMessages from '../schema-resources/i18n/en-US.json'
import zhCnMessages from '../schema-resources/i18n/zh-CN.json'
import type { MetadataFileTemplate, MetadataTypeContribution } from './types'

export type TideStackIdeLocale = 'zh-CN' | 'en-US'

type MessageCatalog = Record<string, string>

const catalogs: Record<TideStackIdeLocale, MessageCatalog> = {
  'zh-CN': zhCnMessages,
  'en-US': enUsMessages,
}

export function normalizeIdeLocale(locale?: string): TideStackIdeLocale {
  const normalized = (locale || '').toLowerCase()
  if (normalized.includes('en')) {
    return 'en-US'
  }
  return 'zh-CN'
}

export function tideStackText(
  key: string,
  locale?: string,
  data: Record<string, unknown> = {},
): string {
  const resolvedLocale = normalizeIdeLocale(locale)
  const template = catalogs[resolvedLocale][key] ?? catalogs['zh-CN'][key] ?? key
  return formatMessage(template, data)
}

export function localizeMetadataTypeContribution<TDocument = unknown>(
  contribution: MetadataTypeContribution<TDocument>,
  locale?: string,
): MetadataTypeContribution<TDocument> {
  const resolvedLocale = normalizeIdeLocale(locale)
  return {
    ...contribution,
    displayName: localizeText(contribution.displayName, resolvedLocale),
    fileTemplate: contribution.fileTemplate
      ? localizeMetadataFileTemplate(contribution.fileTemplate, resolvedLocale)
      : undefined,
    createEditorSchema: (context) => localizeValue(contribution.createEditorSchema(context), resolvedLocale) as Record<string, unknown>,
  }
}

function localizeMetadataFileTemplate(
  fileTemplate: MetadataFileTemplate,
  locale: TideStackIdeLocale,
): MetadataFileTemplate {
  return {
    ...fileTemplate,
    document: localizeValue(fileTemplate.document, locale),
    fields: fileTemplate.fields?.map((field) => ({
      ...field,
      label: localizeText(field.label, locale),
      labelRemark: field.labelRemark ? localizeText(field.labelRemark, locale) : undefined,
    })),
  }
}

function localizeValue(value: unknown, locale: TideStackIdeLocale): unknown {
  if (typeof value === 'string') {
    return localizeText(value, locale)
  }
  if (Array.isArray(value)) {
    return value.map((item) => localizeValue(item, locale))
  }
  if (!value || typeof value !== 'object') {
    return value
  }
  return Object.fromEntries(
    Object.entries(value).map(([key, child]) => [key, localizeValue(child, locale)]),
  )
}

function localizeText(value: string, locale: TideStackIdeLocale): string {
  return catalogs[locale][value] ?? value
}

function formatMessage(template: string, data: Record<string, unknown>): string {
  return template.replace(/\{\{([\w.-]+)}}/g, (_raw, key: string) => String(data[key] ?? ''))
}
