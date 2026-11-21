/**
 * 默认端口数据
 * @param {object} override
 * @returns
 */
export function getDefalutPortData(override = {}) {
  return {
    name: '',
    description: '',
    dataType: 'any',
    ...override,
  }
}

/**
 * @typedef {Object} rules - 规则定义
 * @property {string} inputPort - 输入端口规则
 * @property {string} maxInputPort - 输入端口最大值
 * @property {string} minInputPort - 输入端口最小值
 * @property {string} outputPort - 输出端口规则
 * @property {string} maxOutputPort - 输出端口最大值
 * @property {string} minOutputPort - 输出端口最大值

 * 根据端口规则生成连接桩数组
 * @param {Object} rules
 * @returns 连接桩数组
 */
export function generatePortItemsByRules(rules = {}) {
  const inputPorts = []
  const outputPorts = []
  const portItems = []
  const {
    input: { ruleType: inputPort, min: minInputPort, max: _maxInputPort } = {},
    output: { ruleType: outputPort, min: minOutputPort, max: _maxOutputPort } = {},
  } = rules
  if (inputPort === 'limit' && minInputPort > 0) {
    for (let i = 0; i < minInputPort; i++) {
      portItems.push({
        group: 'in',
        attrs: {
          text: {
            text: '',
          },
        },
      })
      inputPorts.push(getDefalutPortData())
    }
  }
  if (outputPort === 'limit' && minOutputPort > 0) {
    for (let i = 0; i < minOutputPort; i++) {
      portItems.push({
        group: 'out',
        attrs: {
          text: {
            text: '',
          },
        },
      })
      outputPorts.push(getDefalutPortData())
    }
  }
  return { portItems, inputPorts, outputPorts }
}

/**
 * 获取节点的大小
 * @param {object} nodeData 节点数据
 * @param {number} fontSize 字体大小
 * @returns {height, width}
 */
export function calcNodeSize(nodeData, fontSize = 14) {
  const height = calcNodeHeight(nodeData)
  const width = calcNodeWidth(nodeData, fontSize)

  return {
    height,
    width,
  }
}

/**
 * 获取节点高度
 * @returns 节点高度
 */
export function calcNodeHeight(nodeData) {
  const inPorts = nodeData.inputPorts || [] // 输入端组的端口数组
  const outPorts = nodeData.outputPorts || [] // 输出端组的端口数组
  const maxLength = Math.max(inPorts.length, outPorts.length)
  let height = 40

  if (maxLength > 1) {
    height = 15 * maxLength + 40
  }

  return height
}

/**
 * 粗略计算节点宽度
 * @param {*} nodeData
 * @returns
 */
export function calcNodeWidth(
  nodeData,
  fontSize = 14,
  additionalSpace = 70,
  minWidth = 150
) {
  const label = nodeData.name
  let chineseCount = 0
  let englishCount = 0
  const chineseCharWidth = fontSize
  const englishCharWidth = fontSize * 0.5
  for (let i = 0; i < label.length; i++) {
    const char = label.charAt(i)
    if (/[\u4e00-\u9fa5]/.test(char)) {
      chineseCount++
    } else {
      englishCount++
    }
  }

  const width =
    chineseCharWidth * chineseCount + englishCharWidth * englishCount + additionalSpace

  return Math.max(width, minWidth)
}

export function getUniqueName(prefix, names, alwaysAddIndex = true) {
  let name = alwaysAddIndex ? `${prefix}0` : prefix
  let index = 1
  while (names.includes(name)) {
    name = `${prefix}${index}`
    index++
  }
  return name
}
