const SHELL_APP_NAME = 'Ouroboros Mock Shell'

function buildSuccessEnvelope(data) {
  return {
    code: 0,
    status: 0,
    msg: '',
    data,
  }
}

function buildShellToken() {
  return {
    accessToken: 'mock-access-token',
    refreshToken: 'mock-refresh-token',
    expiresIn: 60 * 60,
    refreshTokenExpiresIn: 24 * 60 * 60,
    issuedAt: Date.now(),
  }
}

function buildShellAppInfo() {
  return {
    appName: SHELL_APP_NAME,
    defaultTheme: 'dark',
    currentTheme: 'dark',
    quickEntry: '',
    logo: '',
    icon: '',
    homePage: '/welcome',
    headerEntry: '',
    sidebarEntry: '',
    loginEntry: '',
    amisLibrary: null,
    componentLibraries: [],
    platformComponentLibraries: [],
    isLockTheme: false,
    isSSOAnyWhere: false,
    ssoServices: [],
    layoutType: 'header-sidebar',
    customStyles: [],
    storagePrefix: 'Ouroboros',
    routes: [],
    config: {
      'security.auth.token.storageType': 'localStorage',
      'security.session.idleLogout': false,
    },
  }
}

function buildShellUserInfo() {
  return {
    id: 'mock-user-1',
    username: 'mock-admin',
    nickname: 'Mock Admin',
    roles: [
      {
        id: 'admin',
        name: '管理员',
      },
    ],
  }
}

function buildShellMenuTree() {
  return {
    main: [
      {
        title: '欢迎页',
        name: 'welcome',
        path: '/welcome',
        url: '/welcome',
        type: 'internal',
        children: [],
      },
    ],
    secondary: [],
  }
}

function buildShellAuthorities() {
  return {
    'shell.view': true,
    'shell.mock': true,
  }
}

function buildShellUserSetting() {
  return {}
}

module.exports = {
  SHELL_APP_NAME,
  buildShellAppInfo,
  buildShellAuthorities,
  buildShellMenuTree,
  buildShellToken,
  buildShellUserInfo,
  buildShellUserSetting,
  buildSuccessEnvelope,
}
