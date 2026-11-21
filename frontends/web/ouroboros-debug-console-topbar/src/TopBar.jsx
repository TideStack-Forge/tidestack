import React from 'react'
import './index.scss'
import ContainerControlButtons from './components/ContainerControlButtons'
import HelperControlButtons from './components/HelperControlButtons'
import Log from './components/Log'
import CloseAppButton from './components/CloseAppButton'
import DevKeyControlButton from './components/DevKeyControlButton'

class TopBar extends React.Component {
  getCurrentLocationKey() {
    if (typeof location === 'undefined') return ''
    return `${location.pathname}${location.search}${location.hash}`
  }

  render() {
    const { sidebarStatus, appName, userInfo, currentPath: propCurrentPath } = this.props
    const currentPath = propCurrentPath || this.getCurrentLocationKey()
    const pathname = currentPath.split('?')[0].split('#')[0]
    const customStyle = {
      paddingLeft: sidebarStatus?.sidebarWidth
        ? `${sidebarStatus.sidebarWidth + 20}px`
        : '20px',
    }
    const developmentRouteMatch = pathname.match(/^\/(dev|debug)\/(.+?)\/(.+)$/)
    // 判断是否为开发环境
    const isDevelopment = Boolean(developmentRouteMatch) && !pathname.startsWith('/dev/apps')

    const titleStyle = {
      display: sidebarStatus && sidebarStatus.sidebarExpanded ? 'none' : 'block',
    }

    const profile = typeof userInfo?.userIdentity === 'string' && userInfo.userIdentity.trim()
      ? userInfo.userIdentity.trim()
      : ''

    let appNameInUrl = ''
    if (isDevelopment) {
      appNameInUrl = developmentRouteMatch[2]
    }
    return (
      <div className="custom-top-navbar">
        <div className="left" style={customStyle}>
          {!isDevelopment ? (
            <div className="title" style={titleStyle}>
              {appName}
            </div>
          ) : (
            <div className="left-content" key={`dev-${profile}-${appNameInUrl}`}>
              <ContainerControlButtons profile={profile}></ContainerControlButtons>
              <Log profile={profile} appName={appNameInUrl}></Log>
            </div>
          )}
        </div>
        <div className="right">
          {isDevelopment ? (
            [
              <div className="dev-key-button" key="dev-key-button">
                <DevKeyControlButton
                  key={`dev-key-${appNameInUrl}`}
                  appName={appNameInUrl}
                ></DevKeyControlButton>
              </div>,
              <div className="helper-buttons" key="helper-buttons">
                <HelperControlButtons
                  key={`helper-${currentPath}`}
                  appName={appNameInUrl}
                ></HelperControlButtons>
              </div>,
              <CloseAppButton key="close-button" />,
            ]
          ) : (
            <div></div>
          )}
        </div>
      </div>
    )
  }
}
export default TopBar
