export function getSaveBlockingValidationMessage(flowModel) {
  if (!flowModel?.name || !flowModel.name.trim()) {
    return '模型名称不能为空'
  }
}
