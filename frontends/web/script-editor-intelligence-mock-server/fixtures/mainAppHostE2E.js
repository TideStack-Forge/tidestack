const MAIN_APP_HOST_ROUTE_DEFINITIONS = [
  {
    key: 'orders-dashboard',
    title: '订单看板',
    path: '/mock/orders-dashboard',
    schemaId: '/mock/orders-dashboard',
    authority: 'main-app-host.orders.view',
  },
  {
    key: 'approval-center',
    title: '审批中心',
    path: '/mock/approval-center',
    schemaId: '/mock/approval-center',
    authority: 'main-app-host.approvals.view',
  },
  {
    key: 'change-password',
    title: '修改密码',
    path: '/system-setting/user-management/change-password',
    schemaId: '/system-setting/user-management/change-password',
    authority: 'shell.view',
    includeInMenu: false,
  },
]

const MAIN_APP_HOST_AUTH_PROVIDERS = [
  {
    key: 'mock-ldap',
    type: 'ldap',
    displayName: 'Mock LDAP',
    loginUrl: null,
    availability: 'healthy',
  },
]

const MAIN_APP_HOST_UI_SCHEMAS = {
  '/mock/orders-dashboard': {
    type: 'page',
    title: '订单看板',
    body: [
      {
        type: 'alert',
        level: 'success',
        showIcon: true,
        title: 'Main App Host E2E Happy Path',
        body: '订单看板页面已由独立 mock server 成功提供。',
      },
      {
        type: 'property',
        title: '今日概览',
        column: 2,
        items: [
          {
            label: '待处理订单',
            content: '3',
          },
          {
            label: '权限标识',
            content: 'main-app-host.orders.view',
          },
        ],
      },
    ],
  },
  '/mock/approval-center': {
    type: 'page',
    title: '审批中心',
    body: [
      {
        type: 'alert',
        level: 'info',
        showIcon: true,
        title: 'Main App Host E2E Happy Path',
        body: '审批中心页面已由独立 mock server 成功提供。',
      },
      {
        type: 'property',
        title: '审批队列',
        column: 2,
        items: [
          {
            label: '待审批单据',
            content: '2',
          },
          {
            label: '权限标识',
            content: 'main-app-host.approvals.view',
          },
        ],
      },
    ],
  },
  '/system-setting/user-management/change-password': {
    type: 'form',
    title: false,
    mode: 'horizontal',
    horizontal: {
      left: 3,
      right: 9,
    },
    api: {
      method: 'patch',
      url: '/api/sys/user/modify-password',
      data: {
        credentials: '${credentials}',
        newPassword: '${newPassword}',
        verificationMode: 'password',
      },
    },
    body: [
      {
        type: 'input-password',
        localeKeys: {
          label: 'changePassword.oldPassword',
          'validationErrors.minLength':
            'changePassword.validation.oldPasswordRequired',
        },
        name: 'credentials',
        label: '旧密码',
        required: true,
        validations: {
          minLength: 1,
        },
        validationErrors: {
          minLength: '请输入旧密码',
        },
      },
      {
        type: 'input-password',
        localeKeys: {
          label: 'changePassword.newPassword',
          description: 'changePassword.newPassword.description',
          'validationErrors.minLength':
            'changePassword.validation.newPasswordMinLength',
        },
        name: 'newPassword',
        label: '新密码',
        required: true,
        validations: {
          minLength: 6,
        },
        description: '至少 6 位，建议不要与近期使用过的密码重复。',
        validationErrors: {
          minLength: '密码必须大于六位',
        },
      },
      {
        type: 'input-password',
        localeKeys: {
          label: 'changePassword.confirmPassword',
          'validationErrors.equalsField':
            'changePassword.validation.confirmPasswordMismatch',
        },
        name: 'confirmPassword',
        label: '确认密码',
        required: true,
        validations: {
          equalsField: 'newPassword',
        },
        validationErrors: {
          equalsField: '两次输入的新密码不一致',
        },
      },
      {
        type: 'alert',
        localeKeys: {
          body: 'changePassword.notice.passwordUpdated',
        },
        level: 'info',
        showIcon: true,
        body: '密码更新后，请使用新密码重新登录。',
      },
    ],
    actions: [
      {
        type: 'button',
        localeKeys: {
          label: 'cancel',
        },
        label: '取消',
        level: 'secondary',
        actionType: 'cancel',
      },
      {
        type: 'submit',
        localeKeys: {
          label: 'userMenu.changePassword',
        },
        label: '修改密码',
        level: 'primary',
      },
    ],
  },
}

function cloneJson(value) {
  return JSON.parse(JSON.stringify(value))
}

function buildMainAppHostAuthProviders() {
  return cloneJson(MAIN_APP_HOST_AUTH_PROVIDERS)
}

function buildMainAppHostRoutes() {
  return MAIN_APP_HOST_ROUTE_DEFINITIONS.map((route) => ({
    path: route.path,
    title: route.title,
    type: 'amis',
    config: {
      schemaId: route.schemaId,
      authority: route.authority,
    },
  }))
}

function buildMainAppHostMenuTree() {
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
      {
        title: '主应用 Host E2E',
        name: 'main-app-host-e2e',
        path: '',
        url: '',
        type: 'catalog',
        children: MAIN_APP_HOST_ROUTE_DEFINITIONS.filter(
          (route) => route.includeInMenu !== false
        ).map((route) => ({
          title: route.title,
          name: route.key,
          path: route.path,
          url: route.path,
          type: 'amis',
          config: {
            schemaId: route.schemaId,
            authority: route.authority,
          },
          children: [],
        })),
      },
    ],
    secondary: [],
  }
}

function buildMainAppHostAuthorities() {
  return {
    'shell.view': true,
    'shell.mock': true,
    'main-app-host.orders.view': true,
    'main-app-host.approvals.view': true,
  }
}

function buildMainAppHostUiSchema(path) {
  if (!path) {
    return null
  }
  return cloneJson(MAIN_APP_HOST_UI_SCHEMAS[path] || null)
}

module.exports = {
  buildMainAppHostAuthProviders,
  buildMainAppHostAuthorities,
  buildMainAppHostMenuTree,
  buildMainAppHostRoutes,
  buildMainAppHostUiSchema,
}
