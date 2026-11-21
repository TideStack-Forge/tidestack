import React from 'react'
import { renderAmisSchema } from 'ouroboros-sdk'

class CloseAppButton extends React.Component {
  constructor(props) {
    super(props)
    this.closeAppButtonRef = React.createRef()
  }

  componentDidMount() {
    this.renderContent()
  }

  async renderContent() {
    const schema = {
      type: 'button-group',
      className: 'ml-4 log-wrapper functional-button',
      buttons: [
        {
          type: 'button',
          label: '关闭',
          icon: 'fa-solid fa-times close-icon',
          id: 'closeAppButton',
          wrapperCustomStyle:  {
            root: {
              background: 'transparent'
            },
            'root:not(:disabled):not(.is-disabled):hover': {
              'color': 'red',
              'border-color': 'red',
              'background': '#ffffffbb',
              '.close-icon': {
                color: 'red'
              }
            }
          },
          tooltip: '关闭正在编辑的应用，回到开发平台首页',
          size: 'sm',
          onEvent: {
            click: {
              actions: [
                {
                  actionType: 'confirmDialog',
                  dialog: {
                    title: '确认',
                    msg: '确定要关闭当前应用吗？'
                  }
                },
                {
                  actionType: 'ajax',
                  api: '/api/sys/web-app-info',
                  outputVar: 'appInfo'
                },
                {
                  actionType: 'ajax',
                  api: '/api/authored-menu-tree',
                  outputVar: 'menuTree'
                },
                {
                  actionType: 'alert',
                  dialog: {
                    title: '错误',
                    msg: '初始化平台界面时发生错误，请刷新页面后重试'
                  },
                  expression: '${appInfo.responseStatus !== 0 || menuTree.responseStatus !== 0}'
                },
                {
                  actionType: 'stopPropagation',
                  expression: '${appInfo.responseStatus !== 0 || menuTree.responseStatus !== 0}'
                },
                {
                  actionType: 'publish',
                  args: {
                    eventName: 'updateMenu',
                    eventData: '${menuTree.responseData}',
                    options: {
                      replayStage: 'SYS_INIT_USER',
                      replayScope: 'SESSION'
                    }
                  }
                },
                {
                  actionType: 'publish',
                  args: {
                    eventName: 'updateAppName',
                    eventData: '${appInfo.appName}',
                    options: {
                      replayStage: 'SYS_INIT_USER',
                      replayScope: 'SESSION'
                    }
                  }
                },
                {
                  actionType: 'closeOtherTabs'
                },
                {
                  actionType: 'closeCurrentTab'
                }
              ]
            }
          }
        },
      ],
    }
    await renderAmisSchema(this.closeAppButtonRef.current, schema, {
      localeNamespace: 'debug-console',
    })
  }

  render() {
    return (
      <div>
        <div ref={this.closeAppButtonRef}></div>
      </div>
    )
  }
}
export default CloseAppButton
