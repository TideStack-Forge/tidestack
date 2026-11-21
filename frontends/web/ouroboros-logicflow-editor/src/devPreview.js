import React from 'react'
import { createRoot } from 'react-dom/client'
import LogicFlowEditor from './logicFlowEditor/index.jsx'
import createStoreFn from './store/index.js'
import { createPreviewAmisRenderer } from './devPreview/previewRenderer.mjs'

function createPort(name, description = '') {
  return {
    name,
    description,
    dataType: 'any',
  }
}

const previewNodeDefines = [
  {
    type: 'start',
    label: '入口',
    group: '基础',
    icon: 'mark',
    editorCapabilities: {
      canBeEntry: true,
      canBeExit: false,
      canBeErrorEntry: false,
    },
    scaffold: {
      type: 'start',
      name: '入口',
      inputPorts: [],
      outputPorts: [createPort('next')],
    },
    portRules: {
      input: { ruleType: 'limit', min: 0, max: 0 },
      output: { ruleType: 'limit', min: 1, max: 1 },
    },
    properties: [],
  },
  {
    type: 'errorStart',
    label: '异常入口',
    group: '基础',
    icon: 'mark',
    editorCapabilities: {
      canBeEntry: false,
      canBeExit: false,
      canBeErrorEntry: true,
    },
    scaffold: {
      type: 'errorStart',
      name: '异常入口',
      inputPorts: [],
      outputPorts: [createPort('next')],
    },
    portRules: {
      input: { ruleType: 'limit', min: 0, max: 0 },
      output: { ruleType: 'limit', min: 1, max: 1 },
    },
    properties: [],
  },
  {
    type: 'action',
    label: '处理节点',
    group: '基础',
    icon: 'unknown',
    scaffold: {
      type: 'action',
      name: '处理节点',
      inputPorts: [createPort('input')],
      outputPorts: [createPort('next')],
    },
    portRules: {
      input: { ruleType: 'limit', min: 1, max: 1 },
      output: { ruleType: 'limit', min: 1, max: 1 },
    },
    properties: [],
  },
  {
    type: 'switch',
    label: '条件分支',
    group: '控制',
    icon: 'unknown',
    editorCapabilities: {
      hasExclusiveOutputs: true,
    },
    scaffold: {
      type: 'switch',
      name: '条件分支',
      inputPorts: [createPort('input')],
      outputPorts: [createPort('通过'), createPort('兜底')],
    },
    portRules: {
      input: { ruleType: 'limit', min: 1, max: 1 },
      output: {
        ruleType: 'fixed',
        fixedPorts: [createPort('通过'), createPort('兜底')],
      },
    },
    properties: [],
  },
  {
    type: 'return',
    label: '返回',
    group: '终止',
    icon: 'mark',
    editorCapabilities: {
      canBeEntry: false,
      canBeExit: false,
      canBeErrorEntry: false,
      isSuccessfulTerminal: true,
    },
    scaffold: {
      type: 'return',
      name: '成功返回',
      inputPorts: [createPort('input')],
      outputPorts: [],
    },
    portRules: {
      input: { ruleType: 'limit', min: 1, max: 1 },
      output: { ruleType: 'limit', min: 0, max: 0 },
    },
    properties: [],
  },
]

const previewFlowData = {
  name: 'logicflow-preview',
  description: '本地预览数据',
  entryNode: '入口',
  exitNode: '处理节点',
  errorEntryNode: '异常入口',
  isEnabled: true,
  inputDataType: 'any',
  outputDataType: 'any',
  nodes: [
    {
      type: 'start',
      name: '入口',
      inputPorts: [],
      outputPorts: [createPort('next')],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'switch',
      name: '条件分支',
      inputPorts: [createPort('input')],
      outputPorts: [createPort('通过'), createPort('兜底')],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'action',
      name: '处理节点',
      inputPorts: [createPort('input')],
      outputPorts: [createPort('next')],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'return',
      name: '成功返回',
      inputPorts: [createPort('input')],
      outputPorts: [],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'return',
      name: '兜底返回',
      inputPorts: [createPort('input')],
      outputPorts: [],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'errorStart',
      name: '异常入口',
      inputPorts: [],
      outputPorts: [createPort('next')],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'action',
      name: '异常补偿',
      inputPorts: [createPort('input')],
      outputPorts: [createPort('next')],
      isEnabled: true,
      isAsync: false,
    },
    {
      type: 'return',
      name: '异常返回',
      inputPorts: [createPort('input')],
      outputPorts: [],
      isEnabled: true,
      isAsync: false,
    },
  ],
  connections: [
    {
      sourceNode: '入口',
      sourceOutputPort: 0,
      targetNode: '条件分支',
      targetInputPort: 0,
    },
    {
      sourceNode: '条件分支',
      sourceOutputPort: 0,
      targetNode: '处理节点',
      targetInputPort: 0,
    },
    {
      sourceNode: '条件分支',
      sourceOutputPort: 1,
      targetNode: '兜底返回',
      targetInputPort: 0,
    },
    {
      sourceNode: '异常入口',
      sourceOutputPort: 0,
      targetNode: '异常补偿',
      targetInputPort: 0,
    },
    {
      sourceNode: '异常补偿',
      sourceOutputPort: 0,
      targetNode: '异常返回',
      targetInputPort: 0,
    },
  ],
}

export function createPreviewMicroAppProps() {
  return {
    __LOCAL_PREVIEW__: true,
    __utils__: {
      amisUtils: {
        createObject(superProps, props) {
          return {
            ...(superProps || {}),
            ...(props || {}),
          }
        },
      },
      toast: {
        success(message) {
          console.info(message)
        },
        error(message) {
          console.error(message)
        },
        info(message) {
          console.info(message)
        },
        warning(message) {
          console.warn(message)
        },
      },
      renderAmisSchema: createPreviewAmisRenderer(),
      confirm(message) {
        return Promise.resolve(window.confirm(message))
      },
      openDialog() {
        return Promise.resolve(false)
      },
      request() {
        return Promise.resolve({})
      },
    },
  }
}

export function mountDevPreview(container) {
  const store = createStoreFn(previewFlowData)
  const previewProps = {
    nodeDefineApi: 'mock://logicflow-preview/node-defines',
    env: {
      fetcher() {
        return Promise.resolve({
          status: 0,
          data: previewNodeDefines,
        })
      },
    },
  }

  const root = createRoot(container)
  root.render(
    <div style={{ height: '100%', width: '100%' }}>
      <LogicFlowEditor
        onChange={(value) => {
          store.commit('changeFlowData', value)
        }}
        getProps={() => ({
          ...previewProps,
          store,
        })}
        rootData={{
          appName: 'logicflow-preview',
        }}
      />
    </div>
  )
  return {
    app: {
      unmount() {
        root.unmount()
      },
    },
    store,
  }
}
