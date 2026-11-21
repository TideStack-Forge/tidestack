import React, { useEffect, useState } from 'react'
import { exceptionText, snapshotText } from './runtimeDebugReplay.mjs'
import EditorPanel from '../components/panel/EditorPanel.jsx'
import { RUNTIME_NODE_LABELS } from './runtimeDebugGraphOverlay.mjs'

function CodePane({ label, snapshot }) {
  return <section className="logicflow-debug-code-pane">
    <div className="logicflow-debug-code-heading"><strong>{label}</strong><span>JSON</span></div>
    <pre>{snapshotText(snapshot)}</pre>
  </section>
}

export default function RuntimeDebugInspector({ node, detail, events, onSelectEvent, onClose }) {
  const [view, setView] = useState('data')
  const [expanded, setExpanded] = useState(false)
  useEffect(() => { setView(detail?.exception ? 'exception' : 'data') }, [node, detail?.exception])
  let cause = detail?.exception?.value ?? detail?.exception
  while (cause?.cause) cause = cause.cause
  const state = detail?.state || 'idle'

  return <EditorPanel side="right" className={`logicflow-debug-detail${expanded ? ' is-expanded' : ''}`} aria-label="节点运行详情" header={<>
      <h3 className="panel-title">节点运行详情</h3>
      <div className="logicflow-debug-panel-actions">
        <button type="button" className="panel-close-button" aria-label={expanded ? '收起节点详情' : '展开节点详情'} title={expanded ? '收起节点详情' : '展开节点详情'} onClick={() => setExpanded(!expanded)}><i className={`fa ${expanded ? 'fa-compress' : 'fa-expand'}`} aria-hidden="true" /></button>
        <button type="button" className="panel-close-button" aria-label="隐藏节点详情" title="隐藏节点详情" onClick={onClose}><i className="fa fa-times" aria-hidden="true" /></button>
      </div>
    </>}>
    <div className="logicflow-debug-inspector-bar">
      <div className="logicflow-debug-node-heading">
        <span className="logicflow-debug-eyebrow">节点详情</span>
        <strong title={node}>{node || '选择节点'}</strong>
        {node && <span className={`logicflow-debug-node-status is-${state}`}><i aria-hidden="true" />{RUNTIME_NODE_LABELS[state]}</span>}
      </div>
      <div className="logicflow-debug-detail-views" role="group" aria-label="节点详情视图">
        <button type="button" aria-pressed={view === 'data'} onClick={() => setView('data')}>输入 / 输出</button>
        <button type="button" aria-pressed={view === 'exception'} onClick={() => setView('exception')}>异常堆栈{detail?.exception && <i className="logicflow-debug-error-dot" aria-hidden="true" />}</button>
        <button type="button" aria-label="事件时间线" aria-pressed={view === 'events'} onClick={() => setView('events')}>事件时间线 <small>{events.length}</small></button>
      </div>

    </div>
    {detail?.truncated && <p className="logicflow-debug-warning">该事件的数据已按容量限制截断</p>}
    {view === 'events' ? <ol className="logicflow-debug-event-list">
      {events.map((event, index) => <li key={event.seq}>
        <button type="button" onClick={() => onSelectEvent(event, index)}>{event.seq} · {event.nodeName || event.flowName} · {event.kind}</button>
      </li>)}
      {!events.length && <li className="logicflow-debug-detail-empty">选择一次执行后查看事件时间线</li>}
    </ol> : !node ? <div className="logicflow-debug-detail-empty"><i className="fa fa-mouse-pointer" aria-hidden="true" /><span>点击画布中的节点，查看本次执行的输入、输出和异常</span></div>
      : view === 'exception' ? detail?.exception ? <div className="logicflow-debug-exception">
        <div className="logicflow-debug-exception-summary"><i className="fa fa-exclamation-circle" aria-hidden="true" /><strong>{cause?.message || '节点执行时抛出异常'}</strong><code title={cause?.type}>{cause?.type}</code></div>
        <pre>{exceptionText(detail.exception)}</pre>
      </div> : <div className="logicflow-debug-detail-empty"><i className="fa fa-check-circle" aria-hidden="true" /><span>该节点未记录异常</span></div>
        : <div className="logicflow-debug-data-panes"><CodePane label="输入" snapshot={detail?.input} /><CodePane label="输出" snapshot={detail?.output} /></div>}
  </EditorPanel>
}
