# platform 模块设计

## 分类说明

`platform` 是应用交付层中的可运行平台应用集合，负责把下层能力与开发模块组合成可启动、可部署的产品入口。聚合模块以 [platform/pom.xml](../../../../platform/pom.xml) 为准。

## 当前覆盖状态

当前聚合包含 `ouroboros-develop` 与 `ouroboros-mothership`：前者是开发平台，后者是统一运行时基座。

## 已落地文档

- [ouroboros-develop 模块设计摘要](ouroboros-develop.md)
- [ouroboros-mothership 模块设计摘要](ouroboros-mothership.md)

平台装配与部署边界见各模块摘要；新增平台应用时同步聚合 POM 与本索引。
