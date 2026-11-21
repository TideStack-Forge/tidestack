import { normalizeEditorCapabilities } from './editorCapabilities.js'

function normalizeRule(rule) {
  if (!rule) return {}
  if (rule.ruleType === 'auto') {
    if (typeof rule.autoFunction === 'function') {
      return rule
    }

    if (typeof rule.autoFunction === 'string' && rule.autoFunction.trim()) {
      return {
        ...rule,
        autoFunction: new Function('node', rule.autoFunction),
      }
    }

    return {
      ...rule,
      autoFunction: () => [],
    }
  }
  return rule
}

function buildNodeDefineFromJson(nodeDefine) {
  return {
    ...nodeDefine,
    editorCapabilities: normalizeEditorCapabilities(nodeDefine),
    portRules: {
      input: normalizeRule(nodeDefine.portRules?.input),
      output: normalizeRule(nodeDefine.portRules?.output),
    },
  }
}

export async function fetchNodeDefines(fetcher, api, data) {
  if (!fetcher || !api) return []
  const res = await fetcher(api, data)
  const { data: resData, status, msg, message } = res
  if (status !== 0) {
    throw Error(message || msg || '未返回错误信息')
  }
  const items = resData.items || resData
  if (!Array.isArray(items)) {
    throw Error('返回数据格式错误')
  }
  return items.filter((n) => n).map(buildNodeDefineFromJson)
}

export * from './editorCapabilities.js'
