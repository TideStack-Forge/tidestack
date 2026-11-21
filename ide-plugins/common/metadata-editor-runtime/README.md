# Metadata Editor Runtime

VS Code 与 IntelliJ 元数据编辑器的共用会话、宿主消息和 Amis 编辑适配器。schema 文本保存、视觉修改、dirty state 与外部刷新由 `usecases/editorSession.ts` 负责，IDE 产品通过现有 host 接口接入。

Amis preset、renderer 与样式来自 [ouroboros-amis-runtime](../../../frontends/web/ouroboros-amis-runtime/README.md)。Vite 通过其 `createHostBindings` 提供显式能力，不能向 Web 主应用私有目录回退。HTTP、平台 dialog/drawer、导航和微前端挂载等能力在此宿主未实现时抛出明确错误；本地编辑、Amis 交互、Blob 导出和事件订阅按共享能力契约执行。

`npm run build` 构建 schema、TypeScript 包、浏览器宿主及 Amis IIFE，更新 IDE 使用的 `runtime` 资源。首次检出、rebase 或 Schema 变更后先执行 `npm test`，由 `prepare:schema` 构建并同步 Schema 资源，再运行会话、schema roundtrip 和宿主能力测试；资源已同步时可直接执行 `npm run test:unit`。浏览器入口同时打包 Monaco 的编辑、JSON、CSS、HTML 与 TypeScript worker，通过正式 `MonacoEnvironment.getWorker` 创建内联 worker，不依赖 Web 主应用的 `/pkg` 资源地址。浏览器加载使用 `OuroborosAmisEditorRuntime.mount`；返回对象提供 `applyState`、`setTheme`、`dispose`，关闭编辑器必须释放 React root 与拖拽监听。

构建后执行 `npm run test:integration`，验证真实独立 bundle 的可视编辑回传、状态更新和卸载；测试归属见 [tests/README.md](tests/README.md)。
