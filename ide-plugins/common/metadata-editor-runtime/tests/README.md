# Metadata Editor Tests

`unit/` 验证会话、Schema roundtrip 和宿主能力，首次检出、rebase 或 Schema 变更后执行 `npm test`，先构建并同步 Schema 资源；资源已同步时可直接执行 `npm run test:unit`。

`integration/amis-editor/` 验证真实浏览器加载独立 bundle、可视编辑回传、宿主状态更新和卸载，执行 `npm run build` 后再执行 `npm run test:integration`。浏览器只提供 IDE 构建的 JS/CSS；额外请求 Web 主应用资源会失败，因此能覆盖错误的浏览器依赖入口和 Monaco worker 装配回归。

`integration/hosts/` 消费 VS Code 与 Kotlin 正式生成的页面，验证磁盘往返、冲突、弹窗、逻辑流节点属性与 ScriptEditor 提示/诊断。使用 [统一入口](../../../tests/README.md) 构建两个宿主的资源和模板后执行；完整场景见 [回归契约](../../../../testing/integration/regression/ide/metadata-editors/contract.md)。在线 HTTP/LSP 是协议夹具，编辑器和语言服务客户端使用生产实现。
