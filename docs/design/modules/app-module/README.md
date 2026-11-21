# app-module 模块设计

## 分类说明

`app-module` 属于应用交付层中的业务应用模块群，负责把下层能力装配成面向业务场景的具体应用。与 `ability` 或 `core` 相比，这一层更强调“把已有能力组合成交付成品”。

## 当前覆盖状态

本分类提供 `document-management`、`dynamic-form` 和 `ui-schema-management` 的职责与深入设计入口。

## 已落地文档

- [document-management 模块设计摘要](document-management.md)
- [dynamic-form 模块设计摘要](dynamic-form.md)
- [ui-schema-management 模块设计摘要](ui-schema-management.md)

## 后续补齐范围

- 围绕 `dynamic-form` 的部署生命周期、生成式 UI/API 与审批流协作专题
- 围绕 `ui-schema-management` 的 DB 覆盖策略与 `core/ouroboros-ui-model` 协作边界专题
