# libs 模块设计

## 分类说明

`libs` 对应支撑层，收纳平台的通用工具与公共基础组件，是 `core`、`security`、`ability` 等上层模块共同依赖的底座。`bom` 虽然同样服务于工程基础，但它属于整体构建治理话题，因此仍放在 architecture 维度说明，而不并入本分类。

## 当前覆盖状态

本分类提供 `ouroboros-util`、专门化文件工具库及 `common-*` 组件族的设计摘要；整体依赖和职责见[支撑层架构说明](../../layers/support-layer-architecture.md)。

## 已落地文档

- [ouroboros-util 模块设计摘要](ouroboros-util.md)
- [ouroboros-util-excel 模块设计摘要](ouroboros-util-excel.md)
- [ouroboros-util-zip 模块设计摘要](ouroboros-util-zip.md)
- [common-modules 组件族边界（跨模块专题）](../../cross-cutting/common-modules.md)
- [common-web 模块设计摘要](common-web.md)
- [common-cache 模块设计摘要](common-cache.md)
- [common-model 模块设计摘要](common-model.md)
- [common-service 模块设计摘要](common-service.md)
- [common-app 模块设计摘要](common-app.md)
- [common-template 模块设计摘要](common-template.md)
- [common-script 模块设计摘要](common-script.md)
- [common-expression 模块设计摘要](common-expression.md)
- [common-dev 模块设计摘要](common-dev.md)
- [common-exception 模块设计摘要](common-exception.md)
- [common-exception-mapper 模块设计摘要](common-exception-mapper.md)
- [common-error-code 模块设计摘要](common-error-code.md)
- [common-metadata 模块设计摘要](common-metadata.md)
- [common-all 模块设计摘要](common-all.md)
- [common-lock 模块设计摘要](common-lock.md)
- [支撑层架构说明](../../layers/support-layer-architecture.md)

## 后续补齐范围

- 是否需要为高频 `common-*` 模块继续拆专题页，例如异常流转、缓存接入和服务基类使用约束
- 若后续需要，也可补充 `libs` 内聚合模块与文档治理之间的关系说明
