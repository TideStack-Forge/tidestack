# frontends 模块设计

## 分类说明

`frontends` 属于应用交付层中的前端基座与微前端交付集合，负责承接主应用壳层、微前端包和前端 SDK 的维护视角入口。

## 当前覆盖状态

当前已补齐 `frontends/web` 摘要页，并把最稳定的前端子项纳入第二层索引：主应用、SDK，以及几个独立发布的微前端包。当前前端分类已经具备“总览 + 子项”的层次化入口。

## 已落地文档

- [web 模块设计摘要](web.md)
- [main-app 模块设计摘要](main-app.md)
- [ouroboros-sdk 模块设计摘要](ouroboros-sdk.md)
- [ouroboros-logicflow-editor 模块设计摘要](ouroboros-logicflow-editor.md)
- [ouroboros-approval-workflow-designer 模块设计摘要](ouroboros-approval-workflow-designer.md)
- [ouroboros-debug-console-topbar 模块设计摘要](ouroboros-debug-console-topbar.md)
- [前端基座菜单扩展机制设计（已实施；正文在 main-app 模块内）](../../../../frontends/web/main-app/docs/menu-extension-design.md)

## 后续补齐范围

- 若后续需要，可继续补更细的接入专题，例如 qiankun 挂载约束、SDK 演进约束、Amis 组件库接入约定
- 与 `core/ouroboros-ui-model`、`platform` 的前后端边界协作说明仍可继续深化
