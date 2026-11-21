import React from 'react'
import { renderAmisSchema, Stomp } from 'ouroboros-sdk'

class Log extends React.Component {
  constructor(props) {
    super(props)
    this.logBtnsRef = React.createRef()
    this.subscribedTopic = null
    this.logs = []
    this.lastLine = ''
    this.maxLength = 10000
    this.unsubscribeFunc = function () {}
  }
  componentDidMount() {
    this.renderContent()
    this.subscribeStomp()
  }

  subscribeStomp() {
    const { appName } = this.props
    const stompTopic = `/user/queue/app/${appName}/logs`
    this.unsubscribeFunc = Stomp.subscribe(stompTopic, this.stompListener, {
      initialBody: {},
    })
    this.subscribedTopic = stompTopic
  }

  stompListener = (message) => {
    const lines = message.split('\n')
    this.addLines(lines)
  }

  componentWillUnmount() {
    this.unsubscribeStomp()
  }

  unsubscribeStomp() {
    if (!this.subscribedTopic) return
    this.unsubscribeFunc?.()
    this.subscribedTopic = null
  }

  addLines = (lines) => {
    const { maxLength = this.maxLength } = this.props
    lines = lines.concat()
    let lastLine = this.lastLine || ''
    let logs = (this.logs || []).concat()
    // 如果没有换行符就只更新最后一行
    if (lines.length === 1) {
      lastLine += lines[0]
      this.lastLine = lastLine
    } else {
      // 将之前的数据补上
      lines[0] = lastLine + (lines[0] || '')
      // 最后一个要么是空，要么是下一行的数据
      lastLine = lines.pop() || ''
      logs = logs.concat(lines)
      this.lastLine = lastLine
      this.logs = logs
      // 控制日志数据长度不超过maxLength
      if (this.logs.length > maxLength) {
        this.logs = this.logs.slice(this.logs.length - maxLength)
      }
    }
  }

  async renderContent() {
    const schema = {
      type: 'button-group',
      className: 'ml-4 log-wrapper',
      buttons: [
        {
          type: 'button',
          label: '日志',
          icon: 'fa-solid fa-terminal',
          level: 'default',
          className: 'border-gray-400',
          actionType: 'dialog',
          dialog: {
            title: '实时日志',
            size: 'full',
            data: {
              appName: this.props.appName,
              logs: this.logs,
            },
            body: [
              {
                type: 'log',
                className: 'flex-grow h-full',
                stomp: '/user/queue/app/${appName}/logs',
                source: '${logs}',
                autoScroll: true,
                autoHeight: true,
                maxLength: 10000,
                keepSingleListener: false,
                operation: ['clear', 'showLineNumber', 'filter', 'scrollToBottom'],
              },
            ],
            actions: [],
          },
        },
      ],
    }
    await renderAmisSchema(this.logBtnsRef.current, schema, {
      localeNamespace: 'debug-console',
    })
  }
  render() {
    return (
      <div>
        <div ref={this.logBtnsRef}></div>
      </div>
    )
  }
}
export default Log
