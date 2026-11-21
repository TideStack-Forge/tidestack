# ouroboros-resource 模块设计摘要

## 模块定位

`ouroboros-resource` 是核心引擎层中的应用级文件资源模块，负责把前端与后端两个消费域下的资源文件按“默认层 + 运行时覆盖层”组织起来，并对外提供统一的分层读取能力。它管理的是文件内容与元数据，不解释资源的业务用途。

## 责任边界

该模块负责资源范围与覆盖层级模型、资源提供者与读取服务、开发态默认资源管理、运行时覆盖资源的系统设置入口；不提供平台级共享资源，不把资源模块做成模板引擎或内容语义中心，也不在首版绑定统一租户架构。开发态与运行时覆盖面向不同角色：前者跟随开发平台应用编辑能力，后者面向系统管理员，普通业务用户没有管理入口。

## 依赖关系

模块分为 `core`、`runtime`、`dev` 与 `runtime-manage`：`core` 是纯模型与接口包，`runtime` 依赖 `core` 与 Spring Boot 自动配置；`ability/ouroboros-app-runtime` 引入资源读取链路，`ouroboros-ability-all-runtime` 装配运行时覆盖管理，`ouroboros-dev-module-all` 装配开发态资源编辑。管理员通过 `systemSetting.resourceManagement` 权限访问运行时管理入口。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-resource/DESIGN.md](../../../../core/ouroboros-resource/DESIGN.md)
- `推荐阅读`：[core/ouroboros-resource/README.md](../../../../core/ouroboros-resource/README.md)

## 演进状态

模块已经收敛为“两级 scope + 两层覆盖 + sidecar 元数据”的单一路线，`DESIGN.md` 与 `README.md` 表述一致。仍可见待收敛迹象（`spi` 包中的 legacy 默认实现、`index.json` 兼容路径），详见 `DESIGN.md` 的“待收敛迹象”一节。
