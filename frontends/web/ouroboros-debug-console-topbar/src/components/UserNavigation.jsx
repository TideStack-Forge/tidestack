import { t } from '../i18n.js'
import React from 'react'
import { renderAmisSchema } from 'ouroboros-sdk'
import { toast, logout } from '@/utils'

class UserNavigation extends React.Component {
  constructor(props) {
    super(props)
    this.userRef = React.createRef()
  }
  async componentDidMount() {
    this.renderContent()
  }
  async renderContent() {
    const { userInfo } = this.props

    const userDisplayName =
      (userInfo?.employee && userInfo?.employee?.name) ||
      userInfo?.username ||
      userInfo?.nickName ||
      userInfo?.userIdentity ||
      '未知'
    const schema = {
      type: 'dropdown-button',
      label: userDisplayName,
      trigger: 'click',
      icon: 'fa-solid fa-user',
      btnClassName: 'border-0 user-wrapper',
      className: 'user-container',
      menuClassName: 'menu-ul',
      closeOnClick: true,
      buttons: [
        {
          type: 'button',
          label: '修改密码',
          actionType: 'dialog',
          dialog: {
            title: '修改密码',
            body: [
              {
                type: 'form',
                name: 'passwordForm',
                body: [
                  {
                    type: 'input-password',
                    name: 'passwordForm.credentials',
                    label: '旧密码',
                    required: true,
                  },
                  {
                    type: 'input-password',
                    name: 'passwordForm.newPassword',
                    label: '新密码',
                    required: true,
                    validations: {
                      minLength: 6,
                    },
                    validationErrors: {
                      minLength: '密码必须大于六位',
                    },
                  },
                ],
              },
            ],
            actions: [
              {
                type: 'submit',
                label: '修改密码',
                level: 'primary',
                onClick: async (event, props) => {
                  const { data, env } = props
                  const { passwordForm } = data
                  if (passwordForm.credentials == passwordForm.newPassword) {
                    toast(t('新旧密码不可设置一样'), 'error')
                    return false
                  }
                  const api = {
                    method: 'patch',
                    url: '/api/sys/user/modify-password',
                    data: {
                      ...passwordForm,
                      verificationMode: 'password',
                    },
                  }
                  const result = await env?.fetcher(api)
                  if (result.status !== 0 || !result?.ok) {
                    toast(result?.msg || '操作失败', 'error')
                    return false
                  }
                  toast(result?.msg || '操作成功', 'success')
                  logout()
                },
              },
            ],
          },
        },
        {
          type: 'button',
          label: '切换主题',
          onClick: () => {
            const { theme, themeList, onThemeChanged } = this.props
            if (!theme) return
            const currentIndex = themeList.indexOf(theme)
            const nextIndex = (currentIndex + 1) % themeList.length
            const nextThemeName = themeList[nextIndex]
            onThemeChanged(nextThemeName)
          },
        },
        {
          type: 'button',
          label: '退出',
          onClick: () => {
            logout()
          },
        },
      ],
    }
    await renderAmisSchema(this.userRef.current, schema, {
      localeNamespace: 'debug-console',
    })
  }
  render() {
    return <div ref={this.userRef}></div>
  }
}
export default UserNavigation
