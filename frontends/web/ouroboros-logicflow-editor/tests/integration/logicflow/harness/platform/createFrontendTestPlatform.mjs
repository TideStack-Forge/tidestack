import { createAmisTestRenderer } from './createAmisTestRenderer.mjs'
import { I18nManager } from '../../../../../../main-app/src/common/i18n/I18nManager.ts'
import { EventBus } from '../../../../../../main-app/src/common/eventBus/index.ts'

function noopDecorator() {
  return () => {}
}

function createToastRecorder() {
  const messages = []
  const push = (type) => (message, options = null) => {
    messages.push({ type, message, options })
  }

  return {
    messages,
    toast: {
      success: push('success'),
      warning: push('warning'),
      info: push('info'),
      error: push('error'),
    },
  }
}

export function createFrontendTestPlatform(options = {}) {
  const amisRenderer = createAmisTestRenderer()
  const toastRecorder = createToastRecorder()
  const {
    confirmResult = true,
    dialogResult = null,
    requestResult = { status: 0, data: null },
    requestFn = async () => requestResult,
  } = options

  return {
    i18n: new I18nManager({ ...EventBus, eventsHandlers: {} }),
    toastMessages: toastRecorder.messages,
    __utils__: {
      amisUtils: amisRenderer.amisUtils,
      renderAmisSchema: amisRenderer.renderAmisSchema,
      registerIcon: amisRenderer.registerIcon,
      toast: toastRecorder.toast,
      confirm: async () => confirmResult,
      openDialog: async () => dialogResult,
      request: requestFn,
      FormItem: noopDecorator,
      Renderer: noopDecorator,
      OptionsControl: noopDecorator,
      registerAction: () => {},
      registerFormula: () => {},
      registerValidator: () => {},
      registerEditorPlugin: () => {},
      registerActionPanel: () => {},
      openDrawer: async () => null,
      openTab: () => {},
      closeTab: () => {},
      openMenu: () => {},
      openOrSwitchTab: () => {},
      sendToOpenerTab: () => {},
      updateTabTitle: () => {},
      hasAuth: () => true,
      alert: async () => {},
      prompt: async () => null,
      eventBus: {
        on() {},
        off() {},
        emit() {},
      },
      loginHandler: async () => ({}),
      logoutHandler: async () => {},
      getToken: async () => null,
      listExternalProviders: async () => [],
      isLocalPasswordLoginEnabled: () => true,
      startRedirectLogin: () => null,
      loginWithLdap: async () => ({}),
      exchangeLoginCode: async () => ({}),
      bindExternalAccount: async () => ({}),
      resolveReturnUrl: () => '/',
      mapAuthError: (code) => String(code || ''),
      stomp: {},
      getUserInfo: async () => ({}),
      getTheme: () => 'default',
      setTheme: () => {},
    },
  }
}
