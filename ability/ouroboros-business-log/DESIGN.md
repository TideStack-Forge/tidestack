# ouroboros-business-log 设计文档

## 1. 概述

`ouroboros-business-log` 是 Ouroboros 能力层中的业务操作审计模块，负责把 HTTP 侧的业务动作抽取成统一日志模型，并沉淀为平台级审计记录与管理入口。它解决的是“如何记录业务操作轨迹”的共性问题，而不是替代具体业务模块自己的事件建模。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由三部分组成：

- `ouroboros-business-log-core`
- `ouroboros-business-log-runtime`
- `ouroboros-business-log-dev`

其中：

- `core` 定义 `BusinessLog`、`BusinessLogStorage`、`BusinessOperationTemplate`、`BusinessOperationTemplateSource`、`BusinessOperationTemplateProvider` 等核心抽象。
- `runtime` 提供 `BusinessLogFilter`、`BusinessLogExtractor`、数据库存储、模板提供者、控制器与管理界面资源。

`dev` 通过 `@DevelopModule("business-log-template")` 和 `BusinessOperationTemplateDevService` 管理 `.business-log.json` 源文件，沿 BaseDevelopService/VCS 完成校验和维护，并提供开发侧模板页面。`runtime` 的数据库模板管理和日志查看仍属运行侧入口；两者不能合并为同一职责。

## 3. 核心模型与机制

### 3.1 BusinessLog

`BusinessLog` 是业务日志的统一数据抽象，定义模块、操作、级别、操作人、业务键、摘要、请求载荷和响应数据等核心字段。它是整个审计链路的中心对象。

### 3.2 BusinessOperationTemplate

`BusinessOperationTemplate` 用于描述日志记录规则，包括 URL/HTTP 方法匹配、摘要模板、敏感字段和响应体保存策略。它让日志规则保持配置化，而不是直接写死在过滤器中。

### 3.3 BusinessLogStorage 与 Template Provider

`BusinessLogStorage` 是日志持久化扩展点，`BusinessOperationTemplateProvider` 是模板来源扩展点。Provider 必须通过 `BusinessOperationTemplateSource` 显式声明 DATABASE、FILE 或 JAR 来源，不能由实现类名推断。

`BusinessOperationTemplateCenter` 先聚合同来源的全部 Provider，再按 DATABASE > FILE > JAR 形成只读视图。同来源不同 key 全部保留；同来源同 key 按 Provider 顺序后者覆盖前者；跨来源同 key 按上述优先级覆盖。

### 3.4 BusinessLogFilter 与 BusinessLogExtractor

`BusinessLogFilter` 是运行时记录链路的入口，负责请求匹配、包装和触发日志提取；`BusinessLogExtractor` 负责从请求、响应和上下文中抽取结构化日志并执行脱敏与摘要渲染。

多个模板同时匹配请求时，Filter 按路径具体度、精确 HTTP 方法优先于 `*`、DATABASE > FILE > JAR 来源、稳定模板 key 的顺序选择；路径具体度优先于其他维度。

## 4. 请求到审计记录的数据流

当前实现中的关键链路是：

- 运行时由 `BusinessOperationTemplateCenter` 聚合模板 provider，并形成带来源与覆盖信息的统一视图。
- `BusinessLogFilter` 包装请求与响应，并驱动提取过程。
- `BusinessLogExtractor` 根据模板、上下文和脱敏规则生成日志对象。
- `BusinessLogStorage` 完成持久化，管理界面资源再提供查询与查看入口。

这说明本模块真正维护的是“日志抽取与审计记录”能力，而不是通用监控或链路追踪平台。

## 5. 维护重点

维护该模块时，应优先关注：

- `BusinessLog`、模板体系和存储接口之间的职责边界。
- 新日志规则优先通过显式声明来源的 `BusinessOperationTemplateProvider` 扩展，而不是直接改 `BusinessLogFilter`。
- 运行时匹配和管理查询必须共同消费 `BusinessOperationTemplateCenter`，不得另建 SPI 遍历旁路。
- 脱敏、摘要模板和响应体保存策略要保持配置驱动。
- 管理界面资源属于运行时外围能力，不应反向污染核心抽象。
