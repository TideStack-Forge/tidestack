# ouroboros-util 模块设计摘要

## 模块定位

`ouroboros-util` 是 Ouroboros 支撑层中最底部、最通用的工具库，负责提供与具体业务无关但被大量上层模块复用的基础工具能力。对维护者来说，它是判断“这个能力是否应该继续下沉为通用工具”的第一参考点。

## 责任边界

该模块负责函数式执行辅助、集合处理、JSON 处理、日期与路径工具、类型转换、树结构处理以及 SPI 加载等通用能力，不负责 Spring 集成、业务语义、平台运行时装配或任何特定场景的页面/接口逻辑。凡是已经依赖领域模型或交付场景才能成立的能力，都不应继续沉到这里。

## 依赖关系

`ouroboros-util` 位于 `libs` 中最靠底层的位置，被 `common-*`、`core`、`security`、`ability` 及更上层模块广泛消费。它的职责是降低重复实现和跨模块工具分裂，而不是向上定义业务抽象。

## 关键抽象

- `Functions`：统一异常捕获、安全执行和函数式风格辅助。
- `Maps`、`Lists`：安全访问、过滤、转换、分组与分区等集合操作。
- `Json`、`JsonBag`、`JsonSchemas`：JSON 序列化、灵活容器与 Schema 校验工具；后端运行时 JSON Schema 校验统一走 `JsonSchemas`，底层由 `networknt` 支撑。
- `Dates`、`Paths`：日期与路径处理工具，承接跨平台和格式转换问题。
- `SPI`：统一的服务发现与优先级加载工具，是很多扩展机制的基础工具层实现。

## 关键子模块

- 当前分类下主要是 `ouroboros-util` 本体，承载通用工具命名空间 `com.ouroboros.util`。
- 与它并列但更专门化的工具模块还包括 `ouroboros-util-excel`、`ouroboros-util-zip`，现在都已经在 `libs` 分类下补齐了对应摘要页。

## 扩展机制

`ouroboros-util` 本身不承载平台级扩展协议，但它提供 `SPI` 等共享机制所依赖的底层工具实现。维护共享扩展规则时，应回到 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)；本页只保留它作为工具底座的角色说明。

## 深入文档索引

- `推荐阅读`：[libs/util/ouroboros-util/GUIDE.md](../../../../libs/util/ouroboros-util/GUIDE.md)
- `使用入口`：[libs/util/ouroboros-util/README.md](../../../../libs/util/ouroboros-util/README.md)
- `暂无独立深度设计文档`：当前仓库中尚无比 GUIDE 更系统的 `ouroboros-util` 设计专题。

## 演进状态

`ouroboros-util` 已经形成较稳定的工具边界，但它也是最容易被“顺手塞进一点业务便利方法”的模块之一。维护者应优先保持其通用性和低耦合，避免把上层语义带回基础工具层。
