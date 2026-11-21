import React, { useEffect, useRef } from 'react'
import { createRuntimeDebugGraphOverlay, runtimeGraphMatchesEditor } from './runtimeDebugGraphOverlay.mjs'

export default function RuntimeDebugCanvas({ graph, snapshot, flowName, frame, onSelectNode, selectedNode, flowing = true }) {
  const overlay = useRef(null)
  const select = useRef(onSelectNode)
  select.current = onSelectNode
  const compatible = !!graph && runtimeGraphMatchesEditor(graph, snapshot, flowName)
  useEffect(() => {
    if (!graph) return undefined
    const plugin = graph.getPlugin('logicflow')
    const enabled = plugin?.isEnabled()
    if (enabled) plugin.disable()
    const scroller = graph.getPlugin('scroller')
    const autoResize = scroller?.options?.autoResize
    if (autoResize) scroller.disableAutoResize?.()
    const presentation = createRuntimeDebugGraphOverlay({ graph })
    overlay.current = presentation
    const selected = ({ node }) => { if (!node.getData()?.__virtual) select.current?.(node.getData().name) }
    graph.on('node:click', selected)
    return () => {
      graph.off('node:click', selected)
      presentation.restore()
      overlay.current = null
      if (enabled) plugin.enable()
      if (autoResize) scroller.enableAutoResize?.()
    }
  }, [graph])
  useEffect(() => {
    if (compatible) overlay.current?.applyFrame(frame, { animate: flowing, selectedNode })
    else overlay.current?.restore()
  }, [compatible, frame, flowing, selectedNode])

  return <div className="logicflow-debug-graph-stage">
    {snapshot && !compatible && <div className="logicflow-debug-graph-notice" role="alert">{snapshot.flowName !== flowName ? `该调用属于 ${snapshot.flowName}，请在对应逻辑流中查看。` : '当前设计与采集时的节点或连线不同，已暂停画布标注；执行数据仍可查看。'}</div>}
    {!snapshot && <div className="logicflow-debug-graph-notice">选择一次执行，在当前画布查看路径与节点数据</div>}
    {compatible && <div className="logicflow-debug-legend"><span><i className="is-completed" />正常</span><span><i className="is-failed" />异常</span><span><i className="is-idle" />未执行</span><span className="logicflow-debug-path-key">实际流经路径</span></div>}
  </div>
}
