import { initAmisComponentLib } from 'ouroboros-sdk'
import { initPlatformUtils } from './utils/index.js'
import './registerComponent.js'
// --------- sdk导出生命周期------------ //
const { bootstrap, mount, update, unmount } = initAmisComponentLib({
  beforeMount(props) {
    // 初始化平台工具
    initPlatformUtils(props.__utils__)
  },
})

if (!window.__POWERED_BY_QIANKUN__) {
  const container = document.getElementById('logicflow-container')
  if (container) {
    container.style.height = '100vh'
    import(/* webpackChunkName: "logicflow-preview" */ './devPreview.js')
      .then(({ mountDevPreview, createPreviewMicroAppProps }) =>
        mount(createPreviewMicroAppProps()).then(() => mountDevPreview(container)))
      .catch((error) => {
        container.textContent = '编辑器预览加载失败，请重新打开页面。'
        console.error('LogicFlow preview failed', error)
      })
  }
}

export { bootstrap, mount, update, unmount }
