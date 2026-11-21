# ouroboros-mothership 模块设计摘要

## 模块定位

`ouroboros-mothership` 是平台运行侧的重要应用入口之一，负责把能力层 runtime、Web/WebSocket、缓存、数据库与微前端部署配置组织成面向运行环境的基础平台应用。相比 `ouroboros-develop`，它更偏向“统一运行时基座”而不是“开发管理入口”。

## 责任边界

该模块负责运行时应用装配、环境配置收口、部署目录和上传/元数据路径等基础运行约束，不负责开发平台管理界面，也不负责重新定义下层能力模块本身。它的职责是承载和暴露统一运行时基座，而不是成为新的公共能力层。

## 依赖关系

从 `pom.xml` 和应用配置可见，`ouroboros-mothership` 依赖 `ouroboros-ability-all-runtime`、Web/WebSocket、Redis、Liquibase、数据库驱动等基础设施，并通过应用配置显式声明 metadata、microWeb 和 upload 路径。这说明它的核心不是领域逻辑，而是统一运行时基座装配和环境承载。

## 关键抽象

- `OuroborosApplication`：Spring Boot 启动入口。
- 统一运行时基座装配：通过聚合依赖把能力层 runtime 收口成单一运行应用。
- 环境配置边界：`metadata.path`、`microWeb.deployPath`、上传存储路径、token 配置等。
- 交付资产：模块自身的 Docker 文件与资源配置共同定义运行形态。

统一运行时基座只负责加载已提交的应用元数据版本并发布生命周期事件；具体能力模块在自己的 runtime 层消费这些事件。审批流定义的正式链路是基座通过 `AppContext.homePath` 提供应用元数据目录，`ouroboros-approval-workflow-runtime` 发现、校验并刷新元数据层 `WorkflowModel`，测试平台再显式部署到 Flowable。mothership 不应把应用元数据加入 classpath，也不应把元数据层刷新和 Flowable 部署合并。

## 关键子模块

- 应用启动入口：`src/main/java/com/ouroboros/mothership/OuroborosApplication.java`
- 聚合运行时依赖：`ouroboros-ability-all-runtime`
- 部署与配置资产：`docker/dockerfile`、`application-base.yml`

## 扩展机制

`ouroboros-mothership` 自身并不创造新的共享扩展协议，它主要消费下层能力模块已经提供的自动配置与运行时扩展。相关共享机制规则统一查看 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- [模块 README](../../../../platform/ouroboros-mothership/README.md)：启动方式与基座说明；本页只保留维护者摘要。

## 演进状态

`ouroboros-mothership` 当前更适合作为“统一运行时基座入口”来理解，而不是按单一业务模块理解。由于独立设计资料仍然偏少，后续若继续演进这一入口，建议补齐更明确的运行时与部署专题。
