import React from 'react'
import * as ReactDOM from 'react-dom'
import {createRoot} from 'react-dom/client'
import {render as renderAmis, FormItem, Renderer, OptionsControl, registerIcon} from 'amis'
import {utils as amisUtils} from 'amis-core'
import {I18nManager} from '../../../../../main-app/src/common/i18n/I18nManager.ts'
import {EventBus} from '../../../../../main-app/src/common/eventBus/index.ts'
import 'amis/sdk/cxd.css'
import 'amis/sdk/helper.css'

// LF-RM-UI-001. Product schemas and delivered UMD assets; only shell and identity are fixtures.
window.React = React
window.ReactDOM = ReactDOM
window.__POWERED_BY_QIANKUN__ = true
const roots = new WeakMap()
const request = async ({url, method = 'get', data}) => {
  const target = new URL(url, location.origin)
  const verb = method.toUpperCase()
  if (verb === 'GET' && data && typeof data === 'object') {
    Object.entries(data).forEach(([key, value]) => { if (value !== undefined && value !== null) target.searchParams.set(key, value) })
  }
  const response = await fetch(target, {method: verb, headers:{'Content-Type':'application/json','X-Fixture-Role':new URLSearchParams(location.search).get('role') || 'editor'}, body: verb === 'GET' ? undefined : JSON.stringify(data)})
  const body = await response.json()
  return {...body, status: body.code, __httpStatus:response.status}
}
const env = {
  fetcher: async api => ({data: await request(api)}),
  confirm: async message => window.confirm(message),
  alert: async message => window.alert(message),
  notify: (_type, message) => { document.getElementById('notification').textContent = message },
  jumpTo: url => { location.href = url },
  updateLocation: () => {},
}
async function renderAmisSchema(container, schema, props = {}, suppliedEnv = {}) {
  const root = roots.get(container) || createRoot(container)
  roots.set(container, root)
  let scope
  root.render(renderAmis(schema, {...props, scopeRef: value => {scope=value}}, {...env,...suppliedEnv}))
  await new Promise(resolve => requestAnimationFrame(resolve))
  return {getScopeRef:()=>scope,destroy:async()=>{root.unmount();roots.delete(container)}}
}
const noop = () => {}
async function start() {
  const manifest = await fetch('/editor-manifest.json').then(r=>r.json())
  window.__INJECTED_PUBLIC_PATH_BY_QIANKUN__ = manifest.publicPath
  for (const href of manifest.css) {
    const link = document.createElement('link');link.rel='stylesheet';link.href=href;document.head.appendChild(link)
  }
  await new Promise((resolve,reject)=>{
    const script=document.createElement('script');script.src=manifest.entry;script.onload=resolve;script.onerror=reject;document.head.appendChild(script)
  })
  await window['logicflow-app'].mount({appInfo:{name:'runtime-manage-acceptance'},i18n:new I18nManager({...EventBus,eventsHandlers:{}}),__utils__:{
    FormItem,Renderer,OptionsControl,amisUtils,registerIcon,renderAmisSchema,request,
    registerAction:noop,registerFormula:noop,registerValidator:noop,registerEditorPlugin:noop,registerActionPanel:noop,
    confirm:env.confirm,alert:env.alert,toast:{success:noop,error:noop,warning:noop,info:noop},hasAuth:()=>true,
    eventBus:{on:noop,off:noop,emit:noop},openDialog:noop,openDrawer:noop,openTab:noop,closeTab:noop,
    getTheme:()=> 'default',setTheme:noop,
  }})
  const detail = location.pathname.includes('/detail')
  const schema = await fetch(detail ? '/product-schema/detail.json' : '/product-schema/list.json').then(r=>r.json())
  const params = Object.fromEntries(new URLSearchParams(location.search))
  const root = createRoot(document.getElementById('root'))
  root.render(renderAmis(schema.schema,{data:params},env))
  document.getElementById('fixture-status').textContent='本地隔离验收 · 两个真实 JVM / 共享 H2 · 产品 AMIS 页面与生产编辑器资产'
}
start().catch(error=>{document.getElementById('fixture-status').textContent=error.stack;console.error(error)})
