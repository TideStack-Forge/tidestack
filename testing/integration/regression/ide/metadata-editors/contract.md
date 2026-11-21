# IDE 元数据编辑器回归契约

正式 Webview HTML、打包资源、宿主脚本、Host/Session 和磁盘文件往返为共同验证边界。VS Code 使用正式消息脚本，IntelliJ 使用 Kotlin 正式生成器，夹具仅实现两者公开的宿主通信端点，不复制编辑器逻辑。

共享样本位于 `testing/common/fixtures/data/ide-metadata-workspace/documents.json`。每例独占临时目录、文档修订号和在线服务客户端；不读取用户工程、凭据或 IDE 配置。文件创建语义继续使用 `metadataCreationCases.json`，此处样本只定义编辑行为。

浏览器层严格断言原始字节、保存结果、未知字段和引用；宿主层使用实际 Document/WorkspaceEdit 或 Document/WriteCommandAction 验证 dirty、undo/redo、冲突和生命周期；原生 smoke 必须收到真实 Webview/JCEF 执行结果，文本 fallback 不算通过。

在线服务使用可控 HTTP 服务验证正式客户端与桥接协议。真实 MQ、TCP IPC、Spring 服务的契约由后端既有测试负责，不将协议夹具通过解释为所有部署拓扑验收。

业务模型脚本必须通过完整业务模型页面展开，不能用单独挂载 ScriptEditor 替代。五种 CRUD 的执行前后脚本在在线、离线时均使用可见 Monaco，保持语言、独立字段值与保存重开行为；在线请求包含各字段的位置和完整草稿，离线服务失败不得产生未处理异常。真实 JCEF 单独验证两个脚本的挂载和保存回传。

逻辑流目录使用正式构建资源，流程样本的类型必须存在于目录中。在线测试通过 HTTP 返回完整生产目录，离线测试通过正式宿主配置关闭服务并直接消费随包目录；两个模式都验证节点列表、图标解析和拖入保存，避免只用几个模拟类型掩盖目录问题。
