# ouroboros-logicflow-editor 模块设计摘要

## 模块定位

`frontends/web/ouroboros-logicflow-editor` 是 LogicFlow 的设计时图形化编辑器微前端，负责把逻辑流建模体验封装成可挂载到平台表单体系中的 `logic-flow-editor` 组件。它不是主壳层的一部分，而是一个围绕“受约束的逻辑流 authoring”组织起来的独立前端资产。

## 责任边界

该模块负责逻辑流画布编辑、节点定义消费、属性转译、后端校验结果导航，以及运行期调试事件在当前画布上的投影；它不负责 LogicFlow 运行时执行语义、不负责流程模型事实层，也不负责主应用路由壳层或 SDK 通用契约。它的核心边界是“消费后端 authoring contract，并把它变成可操作的设计时体验”，而不是自己创造第二套流程语义。

## 依赖关系

模块当前主链依赖 React 18、X6 及其 DnD / minimap / scroller / selection 等插件，并通过 `ouroboros-sdk` 的 `initAmisComponentLib()` 暴露给平台。宿主侧通过 `FormItem(type='logic-flow-editor')` 挂载编辑器；编辑器内部由 `useLogicFlowEditorState()` 统一读取 `nodeDefineApi`、流程数据和上下文，再把持久化校验快照、属性面板、validation navigation 与 runtime debug overlay 编排到同一条设计时工作链里。最近一轮 shell 收口还把 palette / inspector visible state、toolbar docking 和 navigator 避让统一交给 `logicFlowEditor/index.jsx + orchestration/` helper 持有，而不是分散在各个 surface 组件里。

## 壳层设计入口

palette / inspector 显隐、工具栏命令分组、画布导航、diagnostics 定位和只读行为，由 `logicFlowEditor/index.jsx` 与 `orchestration/` 协同实现。具体契约统一维护在 [宿主嵌入与生命周期](../../../../core/ouroboros-logicflow/design/editor-embedding-and-lifecycle-boundaries/overview.md) 及其专题中。本页保留模块边界与入口，避免重复维护会随交互演进的壳层细节。

## 关键抽象

- `initAmisComponentLib()`：微前端生命周期入口，把编辑器注册到平台。
- `FormItem(type='logic-flow-editor')`：宿主侧正式挂载点。
- `useLogicFlowEditorState()`：设计时状态归一层，统一消费流程数据、节点定义与宿主上下文。
- `fetchNodeDefines()`：editor 消费后端 `NodeDefine` 合同的入口。
- `validation navigation bridge`：把后端 diagnostics 导航请求转成画布选中与定位动作。
- `editorRuntimeDebugOverlayBridge`：把 runtime debug 事件投影到当前编辑画布。
- `createLogicFlowCanvasController()`：X6 画布、缩放、拖拽、布局等交互的控制器封装。
- `LogicFlowChromeVisibilityState`：editor shell 的 palette / inspector availability + visible contract。
- `LogicFlowNavigatorClusterContract`：minimap、zoom/fit/center 与 expand/collapse 的 canvas-local 壳层 contract。
- `LogicFlowDiagnosticsSurfaceContract`：基于 persisted validation entries 生成 diagnostics trigger + popover 的展示 contract。

## 关键子模块

- `src/main.js`：微前端生命周期入口。
- `src/registerComponent.js`：Amis/FormItem 注册层，负责宿主挂载、保存阻塞校验与 validation navigation 编排。
- `src/logicFlowEditor/index.jsx`：编辑器壳层，组织 canvas、toolbar、properties panel 与 debug/diagnostics 消费。
- `src/logicFlowEditor/orchestration/`：shell-derived visible state、toolbar surface、navigator docking、diagnostics surface 和 validation navigation 的局部 orchestration helper。
- `src/logicFlowEditor/state/`：编辑器状态归一层。
- `src/node-define/` 与 `src/node/`：节点定义消费、节点角色显示与图元层辅助逻辑。
- `src/property-transpiler/`：属性面板与流程定义之间的数据转换层。
- `tests/`：unit / integration 两层前端合同测试。

## 扩展机制

该模块的正式扩展面主要有三类：

- 通过后端 `NodeDefine` 合同扩展可建模节点能力，而不是在前端维护 built-in 节点真相。
- 通过属性转译器与面板配置扩展节点/连线的编辑体验。
- 通过宿主桥接面扩展 validation navigation、调试投影和局部 orchestration 行为。

维护时如果要新增节点或 editor 能力，优先沿 `NodeDefine -> editor state -> property/transpile -> canvas/panel bridge` 这条主链扩展。壳层对齐也只允许发生在 LogicFlow 自己的 shell / orchestration 层，不要直接 import 审批流组件、React Flow contract，或重写 X6 / property panel 主链。

## 深入文档索引

- `模块入口`：[core/ouroboros-logicflow/README.md](../../../../core/ouroboros-logicflow/README.md)
- `设计快照`：[core/ouroboros-logicflow/DESIGN.md](../../../../core/ouroboros-logicflow/DESIGN.md)
- `authoring contract`：[core/ouroboros-logicflow/design/authoring-contracts/overview.md](../../../../core/ouroboros-logicflow/design/authoring-contracts/overview.md)
- `字段级 diagnostics contract`：[core/ouroboros-logicflow/design/result-contracts-and-diagnostic-surfaces/overview.md](../../../../core/ouroboros-logicflow/design/result-contracts-and-diagnostic-surfaces/overview.md)
- `NodeDefine / editor capabilities`：[core/ouroboros-logicflow/design/node-defines-and-editor-capabilities/overview.md](../../../../core/ouroboros-logicflow/design/node-defines-and-editor-capabilities/overview.md)
- `宿主嵌入与生命周期`：[core/ouroboros-logicflow/design/editor-embedding-and-lifecycle-boundaries/overview.md](../../../../core/ouroboros-logicflow/design/editor-embedding-and-lifecycle-boundaries/overview.md)
- `测试与验证`：[core/ouroboros-logicflow/design/testing-and-verification/overview.md](../../../../core/ouroboros-logicflow/design/testing-and-verification/overview.md)
- `repo 级轻量索引`：[docs/logicflow/README.md](../../../logicflow/README.md)
- `前端测试入口`：[frontends/web/ouroboros-logicflow-editor/tests/README.md](../../../../frontends/web/ouroboros-logicflow-editor/tests/README.md)

## 演进状态

`ouroboros-logicflow-editor` 当前的深度设计说明已经集中收口到 `core/ouroboros-logicflow` 的 canonical 文档链中。当前更准确的状态是：模块边界、宿主嵌入、NodeDefine 合同、诊断/调试消费面都已有正式文档入口，但源码目录下仍缺少一份就近的本地 README / DESIGN 作为轻量跳转索引。

## 运行态复用与按需加载

编辑器继续通过原 SDK 与 `FormItem(type='logic-flow-editor')` 注册。启动时仅加载注册代理，首次打开流程图或编辑画布时动态加载编辑器本体及样式；列表、详情摘要和文本差异不触发画布下载。开发预览也改为动态入口，调试工作台单独拆包，避免其他静态引用把 X6、布局或调试代码带回首包。首次实现不新增所有组件库的通用延迟挂载协议。

代理保留原 value/onChange、只读、校验、导航和动作合同，使用最新宿主参数；未就绪时阻止保存，加载失败允许有界重试并保留草稿。共享代码加载与各编辑实例状态分离，卸载清理 Graph、事件、订阅及内部 React root。运行时宿主使用当前运行环境的节点和引用目录，不依赖 dev 接口。

编辑器元数据和 zip 从 dev 模块移到共享纯资源制品 `core/ouroboros-logicflow/ouroboros-logicflow-editor-assets`，供 dev 与运行时管理共同依赖；保留一份正式资产和微前端身份，不通过依赖 dev 取得画布。运行态宿主由 `ouroboros-logicflow-runtime-manage` 提供。

异步块随同一 zip 交付，以构建版本目录作为正式 entry，注入的 publicPath 和 HTML 资产引用均固定到该版本。现有 [Nginx 部署器](../../../../core/ouroboros-ui-model/ouroboros-ui-model-microweb-nginx/src/main/java/com/ouroboros/uimodel/microweb/NginxMicroWebDeployServiceImpl.java) 部署前删除目标目录，因此新版本只替换本版本目录，父目录及仍需使用的旧版本资产不得一并清空。版本保留和静态代理路径须纳入正式发布操作。

上述拆包、资产迁移和运行态宿主已有实现。画布通用布局样式随画布加载，调试样式随工作台加载；不能让普通编辑器依赖调试块样式。IDE 的 `publicPath=auto` 构建保留稳定入口，异步块仍有内容哈希。

单元与挂载测试覆盖延迟加载、最新 props、只读、失败重试、卸载与多实例隔离。本地生产资产浏览器验证和目标部署验证分别取证，不能从构建成功推定实际发布。升级时旧版本目录保留策略由发布方执行。
