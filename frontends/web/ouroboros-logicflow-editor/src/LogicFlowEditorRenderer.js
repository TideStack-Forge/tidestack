import React from 'react'
import { createRoot } from 'react-dom/client'
import createStoreFn from './store/index.js'
import LogicFlowEditorApp from './logicFlowEditor/index.jsx'
import { getSaveBlockingValidationMessage } from './editorValidation/saveBlockingValidation.mjs'
import {
  hasPendingValidationNavigationRequest,
  readValidationNavigationRequest,
  resolveValidationNavigationNodeName,
} from './logicFlowEditor/orchestration/validationNavigationRequest.mjs'
import {
  createValidationNavigationRequestPoller,
} from './logicFlowEditor/orchestration/validationNavigationRequestPoller.mjs'
import {
  performValidationNavigation,
} from './logicFlowEditor/orchestration/validationNavigationRuntime.mjs'
import './logicFlowEditorRenderer.css'

export class LogicFlowEditorRenderer extends React.Component {
  constructor(props) {
    super(props)
    this.reactContainer = React.createRef()
    this.editorRef = React.createRef()
    this.lastValidationNavigationRequest = {}
    this.lastObservedFormStoreUpdatedAt = null
    this.validationNavigationRequestPoller = null
    this.handleEditorChange = this.handleEditorChange.bind(this)
  }
  findAppName(obj) {
    for (const key in obj) {
      if (key === 'appName') {
        return obj[key]
      } else if (typeof obj[key] === 'object' && obj[key] !== null) {
        const result = this.findAppName(obj[key])
        if (result) {
          return result
        }
      }
    }
    return null
  }
  // 装载到DOM后立即调用
  componentDidMount() {
    const { value } = this.props
    this.store = createStoreFn(value)
    this.store.commit('changeIsReadOnly', !!(this.props.static || this.props.readonly))
    this.reactRoot = createRoot(this.reactContainer.current)
    this.renderReactEditor()
    this.store.commit('changeFlowData', value)
    this.lastObservedFormStoreUpdatedAt = this.readFormStoreUpdatedAt(this.props)
    this.triggerValidationNavigation(this.props)
    this.validationNavigationRequestPoller = createValidationNavigationRequestPoller({
      getProps: () => this.props,
      getLastRequest: () => this.lastValidationNavigationRequest,
      onPendingRequest: (nextProps) => {
        this.triggerValidationNavigation(nextProps)
      },
    })
    this.validationNavigationRequestPoller.start()
  }
  shouldComponentUpdate(nextProps) {
    if (
      nextProps.value !== this.props.value ||
      nextProps.logicFlowHost !== this.props.logicFlowHost ||
      nextProps.readonly !== this.props.readonly ||
      nextProps.static !== this.props.static ||
      nextProps.env?.logicFlowDebug !== this.props.env?.logicFlowDebug ||
      nextProps.env?.fetcher !== this.props.env?.fetcher ||
      nextProps.validationSnapshot !== this.props.validationSnapshot ||
      nextProps.navigationTargetNodeName !== this.props.navigationTargetNodeName ||
      nextProps.navigationTargetRequestKey !== this.props.navigationTargetRequestKey
    ) {
      return true
    }
    if (
      hasPendingValidationNavigationRequest(this.lastValidationNavigationRequest, nextProps)
    ) {
      return true
    }
    if (this.readFormStoreUpdatedAt(nextProps) !== this.lastObservedFormStoreUpdatedAt) {
      return true
    }
    return false
  }
  componentDidUpdate(_prevProps) {
    this.renderReactEditor()
    this.store.commit('changeIsReadOnly', !!(this.props.static || this.props.readonly))
    if (this.props.value !== _prevProps.value) {
      this.store.commit('changeFlowData', this.props.value)
    }
    this.lastObservedFormStoreUpdatedAt = this.readFormStoreUpdatedAt(this.props)
    this.triggerValidationNavigation(this.props)
  }

  componentWillUnmount() {
    this.validationNavigationRequestPoller?.stop()
    this.reactRoot?.unmount?.()
  }
  handleEditorChange(value) {
    this.store?.commit?.('changeFlowData', value)
    this.props.onChange?.(value)
  }
  renderReactEditor() {
    this.reactRoot?.render(
      React.createElement(LogicFlowEditorApp, {
        ref: this.editorRef,
        onChange: this.handleEditorChange,
        getProps: () => ({
          ...this.props,
          store: this.store,
        }),
        rootData: {
          appName: this.findAppName(this.props.data),
        },
      })
    )
  }
  render() {
    let propClassName = this.props.className || ''
    if (typeof propClassName === 'string') {
      propClassName += ' logic-flow-editor__wrapper'
    } else if (propClassName instanceof Array) {
      propClassName.push('logic-flow-editor__wrapper')
    } else {
      propClassName['logic-flow-editor__wrapper'] = true
    }
    return React.createElement('div', {
      ref: this.reactContainer,
      className: propClassName,
      style: this.props.style,
    })
  }
  async flushPendingChanges() {
    await this.editorRef.current?.flushPendingChanges?.()
  }
  async validate() {
    await this.flushPendingChanges()
    return getSaveBlockingValidationMessage(this.store?.state?.flowData || this.props.value)
  }
  readFormStoreUpdatedAt(props = {}) {
    return props?.formStore?.updatedAt ?? null
  }
  triggerValidationNavigation(nextProps) {
    const nextRequest = readValidationNavigationRequest(nextProps)
    const nodeName = resolveValidationNavigationNodeName(
      this.lastValidationNavigationRequest,
      nextRequest
    )
    if (!nodeName) return
    this.lastValidationNavigationRequest = nextRequest
    Promise.resolve().then(() => {
      performValidationNavigation({
        editorRoot: this.editorRef.current,
        rootElement: this.reactContainer.current,
        nodeName,
      })
    })
  }
}
