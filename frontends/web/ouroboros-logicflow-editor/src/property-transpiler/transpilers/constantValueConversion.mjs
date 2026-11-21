function normalizeDataType(dataType) {
  return typeof dataType === 'string' && dataType ? dataType : 'any'
}

export function stringifyConstantValue(value, dataType) {
  if (value == null) {
    return ''
  }
  switch (normalizeDataType(dataType)) {
    case 'array':
    case 'map':
    case 'object':
      return typeof value === 'string' ? value : JSON.stringify(value, null, 2)
    default:
      return String(value)
  }
}

function parseRequiredJsonValue(value) {
  if (typeof value !== 'string') {
    return { ok: true, value }
  }
  try {
    return { ok: true, value: JSON.parse(value) }
  } catch (_error) {
    return { ok: false, message: '常量值必须是有效 JSON' }
  }
}

function isPlainObject(value) {
  return value != null && typeof value === 'object' && !Array.isArray(value)
}

function parseNumericText(value, message) {
  if (typeof value === 'number') {
    return Number.isFinite(value) ? { ok: true, text: String(value) } : { ok: false, message }
  }
  if (typeof value !== 'string' || !value.trim()) {
    return { ok: false, message }
  }
  return { ok: true, text: value.trim() }
}

function parseDecimalNumberValue(value) {
  const normalized = parseNumericText(value, '常量值必须是有效数字')
  if (!normalized.ok) {
    return normalized
  }
  const numericValue = Number(normalized.text)
  if (!Number.isFinite(numericValue)) {
    return { ok: false, message: '常量值必须是有效数字' }
  }
  return { ok: true, value: numericValue }
}

function parseIntegerValue(value) {
  const normalized = parseNumericText(value, '常量值必须是整数')
  if (!normalized.ok) {
    return normalized
  }
  if (!/^-?\d+$/.test(normalized.text)) {
    return { ok: false, message: '常量值必须是整数' }
  }
  const numericValue = Number(normalized.text)
  if (
    !Number.isSafeInteger(numericValue) ||
    numericValue < -2147483648 ||
    numericValue > 2147483647
  ) {
    return { ok: false, message: '常量值必须是 32 位整数' }
  }
  return { ok: true, value: numericValue }
}

function parseLongValue(value) {
  const normalized = parseNumericText(value, '常量值必须是整数')
  if (!normalized.ok) {
    return normalized
  }
  if (!/^-?\d+$/.test(normalized.text)) {
    return { ok: false, message: '常量值必须是整数' }
  }
  const longValue = BigInt(normalized.text)
  const minLong = -9223372036854775808n
  const maxLong = 9223372036854775807n
  if (longValue < minLong || longValue > maxLong) {
    return { ok: false, message: '常量值必须是 64 位整数' }
  }
  if (longValue >= BigInt(Number.MIN_SAFE_INTEGER) && longValue <= BigInt(Number.MAX_SAFE_INTEGER)) {
    return { ok: true, value: Number(normalized.text) }
  }
  return { ok: true, value: normalized.text }
}

function parseBooleanValue(value) {
  if (typeof value === 'boolean') {
    return { ok: true, value }
  }
  if (typeof value !== 'string') {
    return { ok: false, message: '常量值必须是 true 或 false' }
  }
  const normalized = value.trim()
  if (normalized === 'true') {
    return { ok: true, value: true }
  }
  if (normalized === 'false') {
    return { ok: true, value: false }
  }
  return { ok: false, message: '常量值必须是 true 或 false' }
}

function parseArrayValue(value) {
  const parsed = parseRequiredJsonValue(value)
  if (!parsed.ok || !Array.isArray(parsed.value)) {
    return { ok: false, message: '常量值必须是 JSON 数组' }
  }
  return parsed
}

function parseObjectValue(value, message) {
  const parsed = parseRequiredJsonValue(value)
  if (!parsed.ok || !isPlainObject(parsed.value)) {
    return { ok: false, message }
  }
  return parsed
}

function isLeapYear(year) {
  return year % 400 === 0 || (year % 4 === 0 && year % 100 !== 0)
}

function getMonthDays(year, month) {
  const monthDays = [31, isLeapYear(year) ? 29 : 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31]
  return monthDays[month - 1] || 0
}

function parseDateParts(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(value)
  if (!match) {
    return null
  }
  return {
    year: Number(match[1]),
    month: Number(match[2]),
    day: Number(match[3]),
  }
}

function isValidDateParts(parts) {
  return (
    parts &&
    parts.month >= 1 &&
    parts.month <= 12 &&
    parts.day >= 1 &&
    parts.day <= getMonthDays(parts.year, parts.month)
  )
}

function parseDateValue(value) {
  if (typeof value !== 'string') {
    return { ok: false, message: '常量值必须是 YYYY-MM-DD 格式' }
  }
  const normalized = value.trim()
  const parts = parseDateParts(normalized)
  if (!isValidDateParts(parts)) {
    return { ok: false, message: '常量值必须是有效日期，格式为 YYYY-MM-DD' }
  }
  return { ok: true, value: normalized }
}

function parseTimeParts(value) {
  const match = /^(\d{2}):(\d{2})(?::(\d{2}))?$/.exec(value)
  if (!match) {
    return null
  }
  return {
    hour: Number(match[1]),
    minute: Number(match[2]),
    second: match[3] == null ? 0 : Number(match[3]),
  }
}

function isValidTimeParts(parts) {
  return (
    parts &&
    parts.hour >= 0 &&
    parts.hour <= 23 &&
    parts.minute >= 0 &&
    parts.minute <= 59 &&
    parts.second >= 0 &&
    parts.second <= 59
  )
}

function parseTimeValue(value) {
  if (typeof value !== 'string') {
    return { ok: false, message: '常量值必须是 HH:mm:ss 格式' }
  }
  const normalized = value.trim()
  const parts = parseTimeParts(normalized)
  if (!isValidTimeParts(parts)) {
    return { ok: false, message: '常量值必须是有效时间，格式为 HH:mm:ss' }
  }
  return { ok: true, value: normalized }
}

function parseDateTimeValue(value) {
  if (typeof value !== 'string') {
    return { ok: false, message: '常量值必须是 YYYY-MM-DD HH:mm:ss 或 ISO 日期时间格式' }
  }
  const normalized = value.trim()
  const match = /^(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2}(?::\d{2})?)$/.exec(normalized)
  if (!match || !isValidDateParts(parseDateParts(match[1])) || !isValidTimeParts(parseTimeParts(match[2]))) {
    return { ok: false, message: '常量值必须是有效日期时间，格式为 YYYY-MM-DD HH:mm:ss 或 ISO 日期时间格式' }
  }
  return { ok: true, value: normalized }
}

export function parseConstantValue(dataType, value) {
  switch (normalizeDataType(dataType)) {
    case 'string':
      return { ok: true, value: value == null ? '' : String(value) }
    case 'integer':
      return parseIntegerValue(value)
    case 'long':
      return parseLongValue(value)
    case 'float':
    case 'double':
      return parseDecimalNumberValue(value)
    case 'boolean':
      return parseBooleanValue(value)
    case 'array':
      return parseArrayValue(value)
    case 'map':
    case 'object':
      return parseObjectValue(value, '常量值必须是 JSON 对象')
    case 'date':
      return parseDateValue(value)
    case 'datetime':
      return parseDateTimeValue(value)
    case 'time':
      return parseTimeValue(value)
    case 'any':
    default:
      return { ok: true, value }
  }
}
