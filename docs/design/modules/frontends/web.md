# web 模块设计摘要

## 模块定位

`frontends/web` 是 Ouroboros 当前前端交付体系的主入口，负责前端基座、微前端模块和 SDK 的统一发布与集成。它是应用交付层中浏览器侧的主壳层，与后端平台入口共同构成完整运行时体验。

## 责任边界

该模块负责前端主应用、微前端挂载、SDK 发布和前端交付链路，不负责后端模型定义或平台运行时装配。它可以消费 `ui-model`、`webapi-model` 和平台接口，但不应反向定义这些后端模型的核心语义。

## 依赖关系

`frontends/web` 内部至少包含 `main-app`、若干微前端模块和 `ouroboros-sdk`。它通过发布规则与后端平台分流：后端由 `platform/*` 和 Jenkins/镜像链路承接，前端则通过 `frontend-web/*` 分支和 tag 规则独立发布基座镜像与 SDK。

## 关键抽象

- `main-app`：前端基座主应用。
- 微前端模块：作为独立前端单元挂载到主应用或开发平台。
- `ouroboros-sdk`：向微前端提供平台能力集成的工具包。
- 发布规则：分支、tag、镜像和 SDK 发布链路。

## 关键子模块

- `main-app`：前端主壳层。
- `ouroboros-sdk`：微前端平台能力 SDK。
- 微前端模块：如逻辑流编辑器、调试工具栏、审批工作流设计器等。
- 发布链路：`frontend-web/snapshot/*`、`frontend-web/release/*`、`frontend-web-v*`、`frontend-web-sdk-v*`

## 扩展机制

前端基座的扩展主要体现为微前端挂载和 SDK 能力暴露，但这些机制与后端的模型和运行时边界是协作关系，不是替代关系。涉及共享机制和边界时，应同时参考 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md) 与 [../../cross-cutting/integration-boundaries.md](../../cross-cutting/integration-boundaries.md)。

## 深入文档索引

- `推荐阅读`：[frontends/web/README.md](../../../../frontends/web/README.md)
- `推荐阅读`：[main-app 模块设计摘要](main-app.md)
- `推荐阅读`：[ouroboros-sdk 模块设计摘要](ouroboros-sdk.md)
- `补充深度资料`：[ouroboros-logicflow-editor 模块设计摘要](ouroboros-logicflow-editor.md)
- `补充深度资料`：[ouroboros-approval-workflow-designer 模块设计摘要](ouroboros-approval-workflow-designer.md)
- `补充深度资料`：[ouroboros-debug-console-topbar 模块设计摘要](ouroboros-debug-console-topbar.md)

## 演进状态

`frontends/web` 已经具备清晰的前端基座和发布链路，而且分类下的第一批子模块摘要已经落地。后续重点不再是“有没有入口页”，而是继续把前后端边界和微前端接入约束说透。
