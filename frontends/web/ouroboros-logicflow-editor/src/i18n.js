import { i18n } from 'ouroboros-sdk'

export function t(text, params) {
  if (typeof text !== 'string' || !text) return text
  return i18n.tn('logicflow-editor', text, text, params)
}
