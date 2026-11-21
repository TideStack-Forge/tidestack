/* 属性转译器列表 */
const transpilers = {}

/**
 * @typedef PropertyDefine
 * @property {string} type 属性类型
 * @property {string} name 属性名
 * @property {string} label 属性标签
 *
 * 转译单个属性定义
 * @param {PropertyDefine} property 属性定义
 * @param {object} editorProps 节点编辑器配置
 * @returns 转译后的schema
 */
export function transpile(property, editorProps) {
  const type = property?.type
  const transpiler = transpilers[type]
  if (transpiler) {
    return transpiler(property, editorProps)
  }
  return property
}

/**
 * 转译属性定义数组
 * @param {object[]} properties
 * @param {object} editorProps 节点编辑器配置
 * @returns 转译后的schema array
 */
export function transpileAll(properties, editorProps) {
  return properties.map((p) => transpile(p, editorProps))
}

export function withEditorProps(editorProps) {
  return {
    transpile: (property) => transpile(property, editorProps),
    transpileAll: (properties) => transpileAll(properties, editorProps),
  }
}

/**
 * 注册转译器
 * @param {string} type 属性类型名
 * @param {(property: object) => object} transpiler 转译器函数
 */
export function registerTranspiler(type, transpiler) {
  transpilers[type] = transpiler
}
