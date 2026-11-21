import React, { useState } from 'react'
import { createRoot } from 'react-dom/client'
import Canvas from '../../../../src/logicFlowEditor/components/canvas/index.jsx'
import RuntimeDebugWorkbench from '../../../../src/logicFlowEditor/debug/RuntimeDebugWorkbench.jsx'
import { createDevRuntimeDebugHost } from '../../../../src/logicFlowEditor/debug/devRuntimeDebugHost.jsx'
import '../../../../src/logicFlowEditor/styles.scss'

// The isolated transport fixture uses the production Canvas, with its design supplied up front.
// A selected execution only annotates these cells, exactly as in the full editor.
const data = {
  name: 'flow', entryNode: 'entry', exitNode: '', errorEntryNode: '', isEnabled: true,
  nodes: ['Entry', 'Script'].map((type, index) => ({
    name: index ? 'script' : 'entry', type, isEnabled: true, isAsync: false,
    inputPorts: [{ name: 'input', dataType: 'any' }], outputPorts: [{ name: 'output', dataType: 'any' }],
  })),
  connections: [{ sourceNode: 'entry', sourceOutputPort: 0, targetNode: 'script', targetInputPort: 0 }],
}
const nodeDefines = data.nodes.map((node) => ({
  type: node.type, label: node.type, icon: 'unknown', properties: [],
  editorCapabilities: { canBeEntry: node.type === 'Entry' },
  portRules: { input: { ruleType: 'limit', min: 1, max: 1 }, output: { ruleType: 'limit', min: 1, max: 1 } },
}))

// LF-DBG-UI-001: same workbench and HTTP contract; identity comes from the isolated Java fixture.
const fetcher = async ({ url, method, data }) => {
  const response = await fetch(url, {
    method: method.toUpperCase(),
    headers: { 'Content-Type': 'application/json' },
    body: data === undefined ? undefined : JSON.stringify(data),
  })
  const result = await response.json()
  return { ...result, status: result.code }
}

const debugHost = createDevRuntimeDebugHost({ appName: 'debug-container-it', fetcher })

function RuntimeDebugBrowserHarness() {
  const [graph, setGraph] = useState(null)
  const [busy, setBusy] = useState(false)
  const [stimulus, setStimulus] = useState('等待触发；以下按钮仅属于测试夹具')

  async function execute(kind) {
    setBusy(true)
    try {
      const response = await fetch('/fixture/execute/' + kind, { method: 'POST' })
      if (!response.ok) throw new Error('测试入口请求失败：' + response.status)
      setStimulus('已提交' + (kind === 'ok' ? '正常' : '异常') + '执行；等待真实助手事件入库')
    } catch (failure) {
      setStimulus(failure.message)
    } finally {
      setBusy(false)
    }
  }

  return <div style={{ display: 'flex', flexDirection: 'column', height: '100vh', fontFamily: 'system-ui, sans-serif' }}>
    <aside aria-label="联调测试入口" style={{ padding: '12px 18px', background: '#0f172a', color: '#fff' }}>
      <strong>逻辑流在线调试 · 真实 Docker / MQ / Redis 联调</strong>
      <div style={{ marginTop: 8, display: 'flex', gap: 12, alignItems: 'center' }}>
        <button type="button" disabled={busy} onClick={() => { void execute('ok') }}>触发正常执行（测试入口）</button>
        <button type="button" disabled={busy} onClick={() => { void execute('fail') }}>触发异常执行（测试入口）</button>
        <output aria-label="测试执行结果">{stimulus}</output>
      </div>
      <p id="fixture-step" style={{ margin: '10px 0 0', color: '#bfdbfe' }}>测试身份 alice；实际产品工作台在下方，当前为隔离联调环境。</p>
    </aside>
    <div className="wrapper-editor-container is-debugging" style={{ flex: 1, minHeight: 0 }}>
      <div className="logicflow-editor-canvas-slot" aria-label="执行运行图">
        <Canvas data={data} nodeDefines={nodeDefines} isReadOnly onGraphInited={setGraph} />
      </div>
      {graph && <RuntimeDebugWorkbench host={debugHost} flowName="flow" graph={graph} onClose={() => {}} />}
    </div>
  </div>
}

createRoot(document.getElementById('root')).render(<RuntimeDebugBrowserHarness />)
