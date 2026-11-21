import React from 'react'
import { createRuntimeDebugApi } from './runtimeDebugApi.mjs'
import RuntimeDebugTarget from './RuntimeDebugTarget.jsx'

function TestContainerTarget(props) {
  return <RuntimeDebugTarget {...props} label="测试容器" placeholder="请选择运行中的测试容器" />
}

/** The development platform adapter owns its HTTP paths, private destination and target UI. */
export function createDevRuntimeDebugHost({ fetcher, appName, stomp }) {
  return {
    key: `dev:${appName}`,
    api: createRuntimeDebugApi(fetcher, appName),
    subscribe: typeof stomp?.subscribe === 'function'
      ? (captureId, notify) => stomp.subscribe(`/user/queue/dev/logicflow/debug/${captureId}`, notify, false)
      : undefined,
    TargetControl: TestContainerTarget,
    recordingHint: '记录测试容器中接下来发生的执行。',
    triggerHint: '触发测试应用的业务请求，执行会自动出现在这里。',
  }
}
