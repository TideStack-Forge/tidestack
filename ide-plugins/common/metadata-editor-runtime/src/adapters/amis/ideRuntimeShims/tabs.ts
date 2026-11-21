import { unsupported } from './unsupported'
const tabs = {
  openTab: async () => unsupported('host tab navigation'),
  openOrSwitchTab: async () => unsupported('host tab navigation'),
  closeCurrentTab: async () => unsupported('host tab navigation'),
  closeTab: async () => unsupported('host tab navigation'),
  closeOtherTabs: async () => unsupported('host tab navigation'),
  sendToOpenerTab: async () => unsupported('host tab navigation'),
  updateTabTitle: async () => unsupported('host tab navigation'),
}

export default tabs
