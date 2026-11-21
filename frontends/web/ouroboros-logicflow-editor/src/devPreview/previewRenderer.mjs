function escapeHtml(value) {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
}

export function createPreviewAmisRenderer() {
  return async (containerEl, schema, props) => {
    containerEl.innerHTML = `
      <div style="padding: 12px; color: #666; font-size: 12px; line-height: 1.6;">
        <div style="font-weight: 600; margin-bottom: 6px;">Preview Mode</div>
        <div>当前是本地预览模式，属性面板使用轻量占位渲染器。</div>
        <div style="margin-top: 6px; white-space: pre-wrap;">${escapeHtml(
          JSON.stringify(props?.data || {}, null, 2)
        )}</div>
      </div>
    `
    return {
      destroy() {
        containerEl.innerHTML = ''
      },
      schema,
    }
  }
}
