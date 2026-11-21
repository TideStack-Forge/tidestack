import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import RuntimeDebugCanvas from './RuntimeDebugCanvas.jsx'
import RuntimeDebugInspector from './RuntimeDebugInspector.jsx'
import EditorPanel from '../components/panel/EditorPanel.jsx'
import Toolbar from '../components/toolbar/index.jsx'
import RuntimeDebugTarget from './RuntimeDebugTarget.jsx'
import { mergeReplayEvents, replayFrame, replayInvocations } from './runtimeDebugReplay.mjs'
import { subscribeToEditorRuntimeDebugCapture } from '../orchestration/editorRuntimeDebugOverlayBridge.mjs'
import './runtimeDebug.scss'

const STATE_LABELS = {
  STARTING: '正在开始记录', ACTIVE: '记录中', DRAINING: '等待执行结束', CLOSED: '已停止记录',
  EXPIRED: '记录已结束', SATURATED: '记录容量已满', INCOMPLETE: '记录不完整',
  COMPLETED: '执行成功', FAILED: '执行异常',
}
const TERMINAL = new Set(['CLOSED', 'EXPIRED'])
const statusLabel = (state) => STATE_LABELS[state] || state
const time = (value) => new Date(value).toLocaleTimeString('zh-CN', { hour12: false })

export default function RuntimeDebugWorkbench({ host, flowName, onClose, graph }) {
  const {
    api, subscribe, TargetControl = RuntimeDebugTarget,
    recordingHint = '记录运行目标中接下来发生的执行。',
    triggerHint = '触发相关业务请求，执行会自动出现在这里。',
  } = host
  const [targets, setTargets] = useState([])
  const [runtimeId, setRuntimeId] = useState('')
  const [history, setHistory] = useState([])
  const [capture, setCapture] = useState(null)
  const [selectedRun, setSelectedRun] = useState('')
  const [events, setEvents] = useState([])
  const [availability, setAvailability] = useState('')
  const [invocation, setInvocation] = useState('')
  const [snapshot, setSnapshot] = useState(null)
  const [node, setNode] = useState('')
  const [inspectorOpen, setInspectorOpen] = useState(true)
  const [position, setPosition] = useState(0)
  const [overview, setOverview] = useState(true)
  const [flowing, setFlowing] = useState(true)
  const [playing, setPlaying] = useState(false)
  const [speed, setSpeed] = useState(1)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  const mounted = useRef(true)
  const runRequest = useRef(0)
  const operation = useRef(null)
  const context = useMemo(() => ({ api, flowName }), [api, flowName])
  const currentContext = useRef(context)
  currentContext.current = context
  const currentCapture = useRef(null)
  currentCapture.current = capture
  const invocations = useMemo(() => replayInvocations(events), [events])
  const selectedInvocation = invocations.find((item) => item.key === invocation) || invocations[0]
  const frame = useMemo(() => replayFrame(events, overview ? events.length : position, selectedInvocation, { allTransfers: overview }), [events, position, selectedInvocation, overview])
  const detail = frame.nodes.get(node)
  const live = capture && !TERMINAL.has(capture.state)

  useEffect(() => {
    mounted.current = true
    return () => { mounted.current = false; runRequest.current++ }
  }, [])

  useEffect(() => {
    let cancelled = false
    setCapture(null); setSelectedRun(''); setEvents([]); setSnapshot(null); setError(''); setTargets([]); setHistory([]); setRuntimeId(''); setBusy(false); operation.current = null
    Promise.all([api.targets(), api.captures(flowName)]).then(([list, previous]) => {
      if (cancelled) return
      setTargets(list || [])
      setRuntimeId(list?.find((item) => item.supported)?.runtimeId || '')
      setHistory(previous || [])
      const active = previous?.find((item) => !TERMINAL.has(item.state))
      if (active) setCapture(active)
    }).catch((failure) => { if (!cancelled) setError(failure.message) })
    return () => { cancelled = true }
  }, [api, flowName])

  useEffect(() => {
    if (capture) setHistory((previous) => [capture, ...previous.filter((item) => item.captureId !== capture.captureId)]
      .sort((left, right) => right.createdAt - left.createdAt).slice(0, 32))
  }, [capture])

  useEffect(() => {
    const pauseHidden = () => { if (document.visibilityState === 'hidden') setPlaying(false) }
    document.addEventListener('visibilitychange', pauseHidden)
    return () => document.removeEventListener('visibilitychange', pauseHidden)
  }, [])

  const refresh = useCallback(async () => {
    const current = currentCapture.current
    if (!current) return
    try {
      const next = await api.read(current.captureId)
      if (mounted.current && currentContext.current === context && currentCapture.current?.captureId === current.captureId) {
        setCapture(next)
        setError('')
      }
    } catch (failure) {
      if (mounted.current && currentContext.current === context) setError(failure.message)
    }
  }, [api, context])

  useEffect(() => {
    if (!capture?.captureId) return undefined
    return subscribeToEditorRuntimeDebugCapture({ subscribe, captureId: capture.captureId, refresh })
  }, [capture?.captureId, refresh, subscribe])

  useEffect(() => {
    if (!capture?.captureId || TERMINAL.has(capture.state)) return undefined
    let stopped = false
    let timer
    const id = capture.captureId
    async function renew() {
      try {
        const next = await api.renew(id)
        if (!stopped && mounted.current && currentCapture.current?.captureId === id) setCapture(next)
      } catch (failure) {
        if (!stopped && mounted.current) setError(failure.message)
      } finally {
        if (!stopped) timer = setTimeout(renew, 5000)
      }
    }
    timer = setTimeout(renew, 1000)
    return () => { stopped = true; clearTimeout(timer) }
  }, [api, capture?.captureId, capture?.state])

  useEffect(() => {
    if (!selectedRun && capture?.runs?.length) setSelectedRun(capture.runs[0].runId)
  }, [capture?.runs, selectedRun])

  useEffect(() => {
    const request = ++runRequest.current
    setEvents([]); setPosition(0); setOverview(true); setFlowing(true); setPlaying(false); setNode(''); setInvocation(''); setSnapshot(null)
    if (!capture?.captureId || !selectedRun) return undefined
    let cancelled = false
    let timer
    let after = 0
    let accumulated = []
    let complete = false
    const id = capture.captureId
    async function read() {
      try {
        let page
        let pages = 0
        do {
          page = await api.events(id, selectedRun, after)
          if (cancelled || request !== runRequest.current) return
          accumulated = mergeReplayEvents(accumulated, page.items, { captureId: id, runId: selectedRun })
          after = page.afterSeq
          pages++
        } while (page.hasMore && pages < 10)
        if (cancelled) return
        setEvents(accumulated)
        after = 0
        for (const event of accumulated) {
          if (event.seq !== after + 1) break
          after = event.seq
        }
        complete = page.complete
        setAvailability(complete ? 'COMPLETE' : page.availability)
      } catch (failure) {
        if (!cancelled) setError(failure.message)
      } finally {
        if (!cancelled && !complete) timer = setTimeout(read, 2000)
      }
    }
    void read()
    return () => { cancelled = true; clearTimeout(timer) }
  }, [api, capture?.captureId, selectedRun])

  useEffect(() => {
    let cancelled = false
    setSnapshot(null)
    if (!capture?.captureId || !selectedInvocation?.graphId) return undefined
    api.graph(capture.captureId, selectedInvocation.graphId).then((value) => {
      if (!cancelled) setSnapshot(value)
    }).catch((failure) => { if (!cancelled) setError(failure.message) })
    return () => { cancelled = true }
  }, [api, capture?.captureId, selectedInvocation?.graphId])

  useEffect(() => {
    if (!playing || !snapshot || position >= events.length) {
      if (playing) setPlaying(false)
      return undefined
    }
    const timer = setTimeout(() => setPosition((value) => value + 1), 650 / speed)
    return () => clearTimeout(timer)
  }, [playing, position, events.length, speed, snapshot])

  async function action(work) {
    setBusy(true); setError('')
    try { await work() } catch (failure) { if (mounted.current && currentContext.current === context) setError(failure.message) }
    finally { if (mounted.current && currentContext.current === context) setBusy(false) }
  }

  function start() {
    void action(async () => {
      operation.current ||= globalThis.crypto.randomUUID()
      const next = await api.start({ runtimeId, flowName, operationId: operation.current })
      if (!mounted.current || currentContext.current !== context) return
      operation.current = null
      setCapture(next); setSelectedRun(''); setEvents([])
      const previous = await api.captures(flowName)
      if (mounted.current && currentContext.current === context) {
        setHistory([next, ...previous.filter((item) => item.captureId !== next.captureId)])
      }
    })
  }

  function chooseCapture(id) {
    const next = history.find((item) => item.captureId === id)
    if (next) {
      setCapture(next); setSelectedRun(''); setEvents([])
      void api.read(id).then((updated) => {
        if (mounted.current && currentContext.current === context && currentCapture.current?.captureId === id) setCapture(updated)
      }).catch((failure) => {
        if (mounted.current && currentContext.current === context && currentCapture.current?.captureId === id) setError(failure.message)
      })
    }
  }

  const runs = capture?.runs || []
  const successful = runs.filter((run) => run.status === 'COMPLETED').length
  const failed = runs.filter((run) => run.status === 'FAILED').length

  return (
    <section className="logicflow-debug-workbench" aria-label="逻辑流在线调试">
      <EditorPanel side="left" className="logicflow-debug-run-panel" aria-label="调试记录与执行列表" header={<>
        <strong className="logicflow-debug-panel-title"><i className="fa fa-bug" aria-hidden="true" />在线调试</strong>
        <button type="button" className="panel-close-button" aria-label="结束调试" title="返回编辑" onClick={onClose}><i className="fa fa-times" aria-hidden="true" /></button>
      </>}>
        <div className="logicflow-debug-capture-controls">
        <TargetControl targets={targets} runtimeId={runtimeId} disabled={busy || live} onChange={(value) => { setRuntimeId(value); operation.current = null }} />
        <div className="logicflow-debug-session-controls">
          <span role="status" className={`logicflow-debug-session-state is-${capture?.state.toLowerCase() || 'idle'}`}><i aria-hidden="true" />{capture ? statusLabel(capture.state) : '待记录'}</span>
          {live ? <button className="logicflow-debug-stop" type="button" title="停止记录新执行，已有结果仍可查看。" disabled={busy} onClick={() => { void action(async () => {
            const next = await api.stop(capture.captureId)
            if (mounted.current && currentContext.current === context && currentCapture.current?.captureId === next.captureId) setCapture(next)
          }) }}><i className="fa fa-stop" aria-hidden="true" />停止记录</button> : <button className="logicflow-debug-primary" type="button" title={recordingHint} onClick={start} disabled={!runtimeId || busy}><i className="fa fa-play" aria-hidden="true" />开始记录</button>}
        </div>
        </div>
          <div className="logicflow-debug-run-heading"><strong>执行记录</strong><span>{runs.length}</span></div>
          <div className="logicflow-debug-run-summary"><span><i className="is-completed" />成功 {successful}</span><span><i className="is-failed" />异常 {failed}</span></div>
          <div className="logicflow-debug-runs">
            {!runs.length && <div className="logicflow-debug-run-empty"><i className="fa fa-list-ul" aria-hidden="true" /><strong>还没有执行记录</strong><p>{live ? triggerHint : recordingHint}</p></div>}
            {runs.map((run, index) => <button type="button" key={run.runId} aria-pressed={selectedRun === run.runId}
              className={`logicflow-debug-run is-${run.incomplete ? 'incomplete' : run.status.toLowerCase()}`}
              onClick={() => { setSelectedRun(run.runId); setPosition(0); setInspectorOpen(true) }}>
              <span className="logicflow-debug-run-line"><strong>执行 #{String(index + 1).padStart(2, '0')}</strong><span className="logicflow-debug-run-outcome"><i className={`fa ${run.status === 'FAILED' ? 'fa-times-circle' : run.status === 'COMPLETED' ? 'fa-check-circle' : 'fa-circle-o'}`} aria-hidden="true" />{statusLabel(run.incomplete ? 'INCOMPLETE' : run.status)}</span></span>
              <span className="logicflow-debug-run-meta"><time>{time(run.startedAt)}</time><span>{run.highestSeq} 个事件</span></span>
            </button>)}
          </div>
          <label className="logicflow-debug-history"><span><i className="fa fa-history" aria-hidden="true" />历史记录</span><select value={capture?.captureId || ''} onChange={(event) => chooseCapture(event.target.value)} disabled={busy || live}>
            <option value="">选择历史记录</option>
            {history.map((item) => <option key={item.captureId} value={item.captureId}>{time(item.createdAt)} · {item.profile}</option>)}
          </select></label>
      </EditorPanel>
      <Toolbar>
        <div className="toolbar-command-group logicflow-debug-playback" role="group" aria-label="调试工具">
          <strong className="logicflow-debug-mode-label"><i className="fa fa-bug" aria-hidden="true" />调试 <span>只读</span></strong>
              <label className="logicflow-debug-invocation"><span className="logicflow-debug-sr-only">调用</span><select value={selectedInvocation?.key || ''} onChange={(event) => { setInvocation(event.target.value); setNode('') }} disabled={!invocations.length}>
                {!invocations.length && <option value="">主流程</option>}
                {invocations.map((item) => <option key={item.key} value={item.key}>{item.scope === 'error' ? '异常处理' : item.callerNodeName ? item.flowName + ' · ' + item.callerNodeName : '主流程'}</option>)}
              </select></label>
              <button type="button" aria-pressed={overview} disabled={!events.length || !snapshot} onClick={() => { setOverview(true); setPlaying(false) }}>完整路径</button>
              <button type="button" disabled={!events.length || !snapshot || !overview} onClick={() => setFlowing(!flowing)}><i className={`fa ${flowing ? 'fa-pause' : 'fa-play'}`} aria-hidden="true" />{flowing ? '暂停流动' : '继续流动'}</button>
              <details className="logicflow-debug-step-controls"><summary title="逐事件检查"><i className="fa fa-sliders" aria-hidden="true" /><span className="logicflow-debug-sr-only">逐事件检查</span></summary>
                <div className="logicflow-debug-step-popover"><strong>逐事件检查</strong><p>按事件顺序检查本次执行</p>
                  <div className="logicflow-debug-playback">
                    <button type="button" disabled={!events.length || !snapshot} onClick={() => { setOverview(false); if (position >= events.length) setPosition(0); setPlaying(!playing) }}>{playing ? '暂停' : '播放'}</button>
                    <button type="button" disabled={position >= events.length || !snapshot} onClick={() => { setOverview(false); setPlaying(false); setPosition((value) => value + 1) }}>单步</button>
                    <button type="button" onClick={() => { setOverview(false); setPlaying(false); setPosition(0) }} disabled={!events.length}>回到开始</button>
                    <label><span className="logicflow-debug-sr-only">倍速</span><select value={speed} onChange={(event) => setSpeed(Number(event.target.value))}>{[0.5, 1, 2, 4].map((value) => <option key={value} value={value}>{value}×</option>)}</select></label>
                  </div>
                  <input aria-label="回放进度" type="range" min="0" max={events.length || 1} value={position} onChange={(event) => { setOverview(false); setPlaying(false); setPosition(Number(event.target.value)) }} /><span>{position} / {events.length}</span>
                </div>
              </details>
          <button type="button" aria-label="显示节点详情" title="节点详情" disabled={!selectedRun} onClick={() => setInspectorOpen(!inspectorOpen)}><i className="fa fa-sliders" aria-hidden="true" /></button>
          <button type="button" onClick={onClose}><i className="fa fa-arrow-left" aria-hidden="true" />返回编辑</button>
        </div>
      </Toolbar>
      <div className="logicflow-debug-notices">
        {error && <div className="logicflow-debug-error" role="alert"><i className="fa fa-exclamation-circle" aria-hidden="true" />{error}</div>}
        {selectedRun && availability !== 'COMPLETE' && <p className="logicflow-debug-warning">记录尚未完整；已有事件可查看，缺失的执行不会补成成功。</p>}
        {frame.missing.length > 0 && <p className="logicflow-debug-warning">缺失事件序号：{frame.missing.join('、')}</p>}
      </div>
          <RuntimeDebugCanvas graph={graph} snapshot={snapshot} flowName={flowName} frame={frame} onSelectNode={(name) => { setNode(name); setInspectorOpen(true) }} selectedNode={node} flowing={!overview || flowing} />
          {selectedRun && inspectorOpen && <RuntimeDebugInspector onClose={() => setInspectorOpen(false)} node={node} detail={detail} events={events} onSelectEvent={(event, index) => { setOverview(false); setPlaying(false); setPosition(index + 1); setInvocation(JSON.stringify([event.flowInvocationId, event.scope])); if (event.nodeName) setNode(event.nodeName) }} />}
    </section>
  )
}
