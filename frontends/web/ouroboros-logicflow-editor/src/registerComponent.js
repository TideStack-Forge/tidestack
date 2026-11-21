import React from 'react'
import { FormItem } from 'ouroboros-sdk'
import { loadLogicFlowRenderer } from './editorModuleLoader.mjs'
import './logicFlowEditorLoading.css'

/** The only renderer registration; stores and graphs belong to the asynchronously mounted child. */
export class LogicFlowEditorRenderer extends React.Component {
  state = { Renderer: null, error: null, attempts: 0 }
  renderer = React.createRef()
  mounted = false

  componentDidMount() {
    this.mounted = true
    this.disposeFlush = this.props.addHook?.(() => this.renderer.current?.flushPendingChanges?.(), 'flush')
    this.load()
  }

  componentWillUnmount() {
    this.mounted = false
    this.disposeFlush?.()
  }

  load = () => {
    this.setState(({ attempts }) => ({ attempts: attempts + 1, error: null }))
    loadLogicFlowRenderer().then(
      (Renderer) => { if (this.mounted) this.setState({ Renderer }) },
      (error) => { if (this.mounted) this.setState({ error }) }
    )
  }

  validate() {
    if (!this.renderer.current) {
      return this.state.error ? '流程图加载失败，请重试后再保存' : '流程图正在加载，请稍后再保存'
    }
    return this.renderer.current.validate()
  }

  doAction(...args) {
    if (!this.renderer.current) throw new Error('流程图尚未就绪')
    return this.renderer.current.doAction?.(...args)
  }

  render() {
    const { Renderer, error, attempts } = this.state
    if (Renderer) return React.createElement(Renderer, { ...this.props, ref: this.renderer })
    return React.createElement('div', {
      className: 'logic-flow-editor__loading',
      role: error ? 'alert' : 'status',
      'aria-busy': !error,
    }, error
      ? React.createElement(React.Fragment, null,
          React.createElement('p', null, '流程图加载失败，编辑内容已保留。'),
          attempts < 3
            ? React.createElement('button', { type: 'button', onClick: this.load }, '重新加载')
            : React.createElement('p', null, '请保存草稿后重新打开页面。'))
      : '正在加载流程图…')
  }
}

FormItem({ type: 'logic-flow-editor', autoVar: true })(LogicFlowEditorRenderer)
