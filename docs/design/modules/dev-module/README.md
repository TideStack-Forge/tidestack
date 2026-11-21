# dev-module 模块设计

## 分类说明

`dev-module` 属于应用交付层中的开发支撑模块群，负责开发平台中的低代码管理、环境配置、版本控制与开发辅助能力。它与 `platform/ouroboros-develop` 一起构成维护者最常接触的上层交付面。

## 当前覆盖状态

本分类按应用管理、容器、版本控制、helper、模型管理、数据源、环境及平台组件库等职责提供设计入口。

## 已落地文档

- [ouroboros-dev-app-management 模块设计摘要](ouroboros-dev-app-management.md)
- [ouroboros-dev-datasource 模块设计摘要](ouroboros-dev-datasource.md)
- [ouroboros-dev-environment 模块设计摘要](ouroboros-dev-environment.md)
- [ouroboros-dev-version-control-system 模块设计摘要](ouroboros-dev-version-control-system.md)
- [ouroboros-dev-helper 模块设计摘要](ouroboros-dev-helper.md)
- [ouroboros-dev-container 模块设计摘要](ouroboros-dev-container.md)
- [ouroboros-dev-model-manage-webapi-scaffold 模块设计摘要](ouroboros-dev-model-manage-webapi-scaffold.md)
- [ouroboros-dev-platform-component-lib 模块设计摘要](ouroboros-dev-platform-component-lib.md)
- [ouroboros-dev-project-generation 模块设计摘要](ouroboros-dev-project-generation.md)
- [ouroboros-dev-runtime 模块设计摘要](ouroboros-dev-runtime.md)

## 后续补齐范围

- 若后续需要，可补 `ouroboros-dev-module-all` 这类聚合模块与装配关系说明
- 已落地模块可继续拆分出更细的专题页，例如组件库清单约束、数据源探查扩展和开发平台应用生命周期协作
