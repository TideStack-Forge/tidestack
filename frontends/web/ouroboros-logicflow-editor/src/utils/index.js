let __utils__ = {}

export function initPlatformUtils(utils) {
  __utils__ = utils
}

export function createObject(superProps, props, properties) {
  return __utils__.amisUtils.createObject(superProps, props, properties)
}

export function toast(message, type) {
  __utils__.toast[type](message)
}

export function renderAmisSchema(containerEl, schema, props = {}, env = {}) {
  return __utils__.renderAmisSchema(
    containerEl,
    schema,
    { localeNamespace: 'logicflow-editor', ...props },
    env
  )
}

export function loadSvgAsSymbol(svgs) {
  if (!document) return Promise.resolve()

  const svgSymbols = Object.entries(svgs).map(([key, svg]) => {
    if (!svg) return null
    return convertSvgToSymbol(svg, key)
  })
  .filter(s => s)

  if (!svgSymbols.length) {
    return
  }

  // 确保在 DOM 准备好后执行
  const insertSymbols = () => {
    try {
      let svgElement = document.getElementById('__SVG_SPRITE_NODE__')
      if (!svgElement) {
        console.error('__SVG_SPRITE_NODE__ not found')
      }
      svgSymbols.forEach(([symbolId, symbol]) => {
        if (svgElement.getElementById(symbolId)) return;
        svgElement.insertAdjacentHTML('beforeend', symbol)
      })
    } catch (error) {
      console.error('Error loading SVG symbols:', error)
    }
  }

  // 根据 DOM 状态选择执行时机
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', insertSymbols)
  } else {
    // DOM 已经准备好，但使用 setTimeout 确保在下一个事件循环中执行
    setTimeout(insertSymbols, 0)
  }
}


/**
 * 将 SVG 字符串转换为 symbol 格式
 * @param {string} svgString - SVG 字符串
 * @param {string} key - 用作 symbol id 的键名
 * @returns {string} - 转换后的 symbol 字符串
 */
function convertSvgToSymbol(svgString, key) {
  try {
    // 创建临时 DOM 元素来解析 SVG
    const parser = new DOMParser()
    const doc = parser.parseFromString(svgString, 'image/svg+xml')
    const svgElement = doc.querySelector('svg')
    
    if (!svgElement) {
      console.warn(`Invalid SVG for key: ${key}`)
      return null
    }

    // 提取重要属性
    const viewBox = svgElement.getAttribute('viewBox')
    const width = svgElement.getAttribute('width')
    const height = svgElement.getAttribute('height')
    
    // 获取 SVG 内部内容（排除 svg 标签本身）
    const innerHTML = svgElement.innerHTML
    
    // 构建 symbol 标签
    let symbolAttributes = `id="icon-${key}"`
    
    // 保留 viewBox 属性（最重要）
    if (viewBox) {
      symbolAttributes += ` viewBox="${viewBox}"`
    } else if (width && height) {
      // 如果没有 viewBox，尝试从 width/height 构建
      symbolAttributes += ` viewBox="0 0 ${width} ${height}"`
    }
    
    return [`icon-${key}`, `<symbol ${symbolAttributes}>${innerHTML}</symbol>`]
  } catch (error) {
    console.error(`Error converting SVG to symbol for key: ${key}`, error)
    return null
  }
}
