import { parseConstantValue, stringifyConstantValue } from '../transpilers/constantValueConversion.mjs'

const DEFAULT_JSON_EDITOR_OPTIONS = {
  lineNumbers: 'off',
  tabSize: 2,
  insertSpaces: true,
}

export const CONSTANT_VALUE_EDITOR_TYPE = 'logicflow-constant-value'

export function normalizeConstantValueDataType(dataType) {
  return typeof dataType === 'string' && dataType ? dataType : 'string'
}

export function getConstantValuePlaceholder(dataType, placeholder) {
  if (placeholder) {
    return placeholder
  }

  switch (normalizeConstantValueDataType(dataType)) {
    case 'integer':
      return '请输入整数'
    case 'long':
      return '请输入长整数'
    case 'float':
      return '请输入单精度数字'
    case 'double':
      return '请输入双精度数字'
    case 'date':
      return 'YYYY-MM-DD'
    case 'datetime':
      return 'YYYY-MM-DD HH:mm:ss'
    case 'time':
      return 'HH:mm:ss'
    case 'array':
      return '请输入 JSON 数组'
    case 'map':
      return '请输入 JSON 对象'
    case 'string':
    default:
      return '请输入字符串'
  }
}

export function formatConstantValueForEditor(value, dataType) {
  const normalizedDataType = normalizeConstantValueDataType(dataType)
  if (normalizedDataType === 'boolean') {
    if (typeof value === 'string') {
      return value.trim() === 'true'
    }
    return !!value
  }
  return stringifyConstantValue(value, normalizedDataType)
}

export function parseConstantValueForCommit(dataType, value) {
  const parsed = parseConstantValue(normalizeConstantValueDataType(dataType), value)
  return parsed.ok ? parsed.value : value
}

export function validateConstantValueDraft(dataType, value) {
  const parsed = parseConstantValue(normalizeConstantValueDataType(dataType), value)
  return parsed.ok ? undefined : parsed.message || '常量值不符合所选值类型'
}

export function buildConstantValueInnerSchema({
  dataType,
  draftValue,
  disabled = false,
  name = '__logicflowConstantValueDraft',
  onBlur,
  onChange,
  onFocus,
  options,
  placeholder,
} = {}) {
  const normalizedDataType = normalizeConstantValueDataType(dataType)
  const sharedSchema = {
    label: false,
    name,
    disabled,
    value: draftValue,
    onBlur,
    onChange,
    onFocus,
  }

  switch (normalizedDataType) {
    case 'boolean':
      return {
        ...sharedSchema,
        type: 'switch',
      }
    case 'date':
      return {
        ...sharedSchema,
        type: 'input-date',
        placeholder: getConstantValuePlaceholder(normalizedDataType, placeholder),
        format: 'YYYY-MM-DD',
        inputFormat: 'YYYY-MM-DD',
      }
    case 'datetime':
      return {
        ...sharedSchema,
        type: 'input-datetime',
        placeholder: getConstantValuePlaceholder(normalizedDataType, placeholder),
        format: 'YYYY-MM-DD HH:mm:ss',
        inputFormat: 'YYYY-MM-DD HH:mm:ss',
      }
    case 'time':
      return {
        ...sharedSchema,
        type: 'input-time',
        placeholder: getConstantValuePlaceholder(normalizedDataType, placeholder),
        format: 'HH:mm:ss',
        inputFormat: 'HH:mm:ss',
      }
    case 'array':
    case 'map':
      return {
        ...sharedSchema,
        type: 'json-editor',
        options: {
          ...DEFAULT_JSON_EDITOR_OPTIONS,
          ...(options || {}),
        },
        placeholder: getConstantValuePlaceholder(normalizedDataType, placeholder),
      }
    case 'string':
    case 'integer':
    case 'long':
    case 'float':
    case 'double':
    default:
      return {
        ...sharedSchema,
        type: 'input-text',
        placeholder: getConstantValuePlaceholder(normalizedDataType, placeholder),
      }
  }
}
