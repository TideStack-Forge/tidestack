# 跨模块专题设计

## 定位

`cross-cutting/` 用来沉淀那些会同时影响多个模块、多个层次或多个维护团队的共享机制与边界规则。它回答的不是“某个模块怎么实现”，而是“多个模块之间应该遵守什么共同约定”。

## 何时阅读

出现以下问题时，建议优先进入本目录，而不是直接扎进某个模块摘要页：

- 需要判断一个能力应该落在 `core`、`ability`、`dev-module` 还是 `platform/frontends` 哪一层。
- 需要新增扩展点、装配方式、模板生成或脚本能力，但不确定应该复用哪套共享机制。
- 需要新增 `META-INF` 资源、SPI 声明或平台元数据，但不确定目录应该怎么选。
- 需要决定某类改动最小应该跑什么验证，而不是盲目全量构建。
- 需要解释两个以上模块为什么要遵守同一套边界、刷新时机或运行时约定。

## 阅读顺序

建议先读 [extension-mechanisms.md](extension-mechanisms.md)，建立共享机制的总分类，再按问题类型继续深入：

- 机制装配问题：先看 [spring-autoconfiguration.md](spring-autoconfiguration.md)，再看 [spi-governance.md](spi-governance.md) 与 [provider-patterns.md](provider-patterns.md)。
- 资源命名空间问题：看 [meta-inf-resource-conventions.md](meta-inf-resource-conventions.md)。
- 统一配置定义问题：先看 [configuration-system.md](configuration-system.md)，再看 [configuration-definition-conventions.md](configuration-definition-conventions.md)。
- 模板、表达式与脚本边界问题：看 [template-and-script-runtime.md](template-and-script-runtime.md)。
- 项目生成器、官方模板和模板发布治理问题：看 [project-generation-platform.md](project-generation-platform.md)。
- 测试与验证问题：看 [testing-and-verification.md](testing-and-verification.md)。
- 配置、模型、安全和集成边界问题：继续看 [configuration-system.md](configuration-system.md)、[model-system.md](model-system.md)、[DESIGN.md](../../../security/DESIGN.md)、[integration-boundaries.md](integration-boundaries.md)。
- handbook 与外部文档体系集成问题：看 [amis-handbook-integration.md](amis-handbook-integration.md)。

## 当前专题索引

- [公共内容存储](content-storage.md)：说明各消费者独立实例、公共内容 IO、后端可插拔以及业务协调边界。

- [扩展机制总览](extension-mechanisms.md)：给出 AutoConfiguration、SPI、模板与脚本等共享扩展机制的分类入口。
- [Spring AutoConfiguration 装配模式](spring-autoconfiguration.md)：说明启动期自动装配的放置位置、注册方式、生命周期接点与维护规则。
- [SPI 治理](spi-governance.md)：说明 Java SPI 的注册、排序、缓存、Spring 桥接与刷新边界。
- [Provider 模式与聚合链路](provider-patterns.md)：说明 provider、center、integrator、builder 等角色如何协同形成平台级聚合。
- [META-INF 资源约定](meta-inf-resource-conventions.md)：说明 `META-INF` 下各类框架入口、平台元数据和模板资源的命名空间规则。
- [模板、表达式与脚本运行时](template-and-script-runtime.md)：说明 FreeMarker、SpEL 和 JS/Groovy 在平台中的职责分工与边界。
- [后端脚本与表达式代码提示](script-expression-intelligence.md)：说明真实应用环境、位置作用域、可插拔逻辑流形状分析、统一编辑器接入与当前能力边界，入口范围见[代码提示入口清单](script-expression-intelligence-inventory.md)。
- [项目生成平台与模板发布治理](project-generation-platform.md)：说明开发平台项目生成器的模块边界、官方模板清单和 qualification/publication gate。
- [测试与验证策略](testing-and-verification.md)：说明共享测试形态、资源驱动测试和按改动类型选择验证的原则。
- [配置体系](configuration-system.md)：说明配置元数据、运行时配置和开发态配置管理的分工。
- [多租户能力边界](multi-tenancy.md)：说明尚未实现的隔离能力、既有通用扩展点和使用限制。
- [统一配置定义约定](configuration-definition-conventions.md)：说明 JAR 内和工作区内如何声明配置分组与配置项，以及资源路径和字段规则。
- [模型体系](model-system.md)：说明几类核心模型之间的源模型、投影关系和运行时落点。
- [安全与访问控制](../../../security/DESIGN.md)：说明权限、安全运行时和访问控制边界。
- [集成边界](integration-boundaries.md)：说明平台内协作、外部系统接入和基础设施适配的边界。
- [common-* 组件族边界](common-modules.md)：说明 `common-*` 这组平台公共部件的分层落点、依赖方向与模块清单。
- [企业协作平台集成架构](enterprise-collaboration-integration.md)：本期钉钉/企微/飞书各接一个企业，目录绑定/单向同步、通知、工作台入口与 RPC 包沿原领域扩展；集成管理上下分区，模块/权限/菜单同步迁移，模板管理沿用原入口；§6.2、§7.3、§7.5 补凭据、通知及同步建议，§18 记录剩余合同。
- [AMIS 与 Handbook 文档集成设计](amis-handbook-integration.md)：说明 AMIS 原生文档、可运行沙箱示例和 handbook 开发者入口的职责边界与落地路径。
- [STOMP 补偿订阅设计](stomp-bootstrap-compensation-subscriptions.md)：说明如何在保持 `/app`、`/topic`、`/queue`、`/user` 分工的前提下，为上层提供逻辑单订阅、物理多通道的 bootstrap + live 订阅能力。
- [模型实时协同](model-collaboration.md)说明 Yjs、基座 STOMP 和应用 VCS 的职责，以及业务模型关系画布的首个接入。
- [逻辑流运行时管理](logicflow-runtime-management.md)说明持久覆盖、候选检查、集合 CAS、实例应用、编辑器懒加载和逐流程观测。

## 与模块摘要页的关系

模块摘要页只需要说明“本模块依赖了哪类共享机制”，并把读者带回本目录；共享机制的统一规则、选型边界和演进约束，应集中保留在这里，避免各模块各写一套。
