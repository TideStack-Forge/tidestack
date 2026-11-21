# 人员档案表单方案（PersonnelProfileFormScheme）模块设计摘要

本页是导航指针：人员档案表单方案的有效设计事实已归位到模块 `DESIGN.md`，这里只保留定位、边界与阅读入口。

## 模块定位

人员档案表单方案是 `ouroboros-organization-structure` 运行时的人员档案表单配置模型：把原本散落在 `template` / `runtime-override` / `edit-layout` / `display-layout` 四个配置键里的底层拼装件，重新建模为一个用户可见、可 CRUD 的场景化方案对象。

## 责任边界

在本模块内，它负责人员档案新增/编辑/详情三类 schema 的持久化与管理接口，并在运行时为 `EmployeePersonnelProfileUISchemaProvider` 提供已解析的表单 schema；不负责员工档案值的写入与存储，也不改变人员档案字段定义机制。旧配置只作为“没有可用持久化方案时”的兼容兜底，不再是一等模型。

## 依赖关系

方案实体沿用本模块的 `@Model` + `TypedDataModel` 持久化风格，管理接口挂在开发管理的 `/api/dev/{appName}/organization-management` 前缀下；旧配置读取依赖 `ouroboros-configuration` 的运行时配置服务，表单 schema 复用 `ui-schema-management` 的 AMIS schema 与 `personnel-profile-layout` 展开机制，字段编辑复用主前端的 amis editor plugin 基础设施。

## 深入文档索引

- `推荐阅读`：[DESIGN.md §3.4 设计动机与边界](../../../../ability/ouroboros-organization-structure/DESIGN.md#34-人员档案表单方案的设计动机与边界)、[§3.5 持久化模型](../../../../ability/ouroboros-organization-structure/DESIGN.md#35-人员档案表单方案持久化模型)、[§3.6 结构化字段约定](../../../../ability/ouroboros-organization-structure/DESIGN.md#36-结构化字段约定)、[§3.7 运行时解析结果](../../../../ability/ouroboros-organization-structure/DESIGN.md#37-运行时解析结果)
- `推荐阅读`：[DESIGN.md §4.3 服务边界](../../../../ability/ouroboros-organization-structure/DESIGN.md#43-人员档案表单方案服务边界)、[§4.4 管理接口](../../../../ability/ouroboros-organization-structure/DESIGN.md#44-人员档案表单方案管理接口)、[§4.5 校验规则](../../../../ability/ouroboros-organization-structure/DESIGN.md#45-人员档案表单方案校验规则)、[§4.6 运行时解析策略](../../../../ability/ouroboros-organization-structure/DESIGN.md#46-人员档案运行时解析策略)
- `推荐阅读`：[DESIGN.md §6 兼容与迁移](../../../../ability/ouroboros-organization-structure/DESIGN.md#6-兼容与迁移)、[§7 前端编辑器契约](../../../../ability/ouroboros-organization-structure/DESIGN.md#7-前端编辑器契约)、[§10 已知限制与未落地项](../../../../ability/ouroboros-organization-structure/DESIGN.md#10-已知限制与未落地项)
- `补充深度资料`：[ouroboros-organization-structure 模块设计摘要](ouroboros-organization-structure.md)、[ui-schema-management 模块设计摘要](../app-module/ui-schema-management.md)

## 演进状态

部分落地：方案实体、持久化服务、管理接口（5 个 CRUD 端点）、兼容适配器与 dev 管理页已可用；场景匹配、`resolve-preview`/`legacy-preview`/`migrate-from-legacy`、amis-editor 主编辑器与落库迁移未落地。状态日期 2026-09-12。
