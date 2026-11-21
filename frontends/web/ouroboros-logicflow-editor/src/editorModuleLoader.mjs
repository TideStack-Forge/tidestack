let pending

/** Share downloads, reset rejected loads so an explicit retry can recover. */
export function loadLogicFlowRenderer() {
  if (!pending) {
    pending = import(/* webpackChunkName: "logicflow-canvas" */ './LogicFlowEditorRenderer.js')
      .then((module) => module.LogicFlowEditorRenderer)
      .catch((error) => {
        pending = undefined
        throw error
      })
  }
  return pending
}
