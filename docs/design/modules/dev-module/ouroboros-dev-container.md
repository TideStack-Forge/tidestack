# ouroboros-dev-container 模块设计摘要

## 模块定位

`ouroboros-dev-container` 是开发平台中的容器运行时支撑模块，负责为开发环境提供容器生命周期管理、镜像拉取和状态事件监听能力。它更像开发平台底层的容器适配层，而不是直接面向维护者的业务管理模块。

## 责任边界

该模块负责容器抽象、容器状态模型、Docker 适配实现和自动配置，不负责开发应用的上层编排流程，也不负责把容器 UI 管理能力做成完整产品。维护时应把“容器运行时能力”与“开发平台应用管理”分开。

## 依赖关系

模块由 `core` 和 `docker` 两部分组成：`core` 定义 `ContainerService`、容器模型和事件抽象，`docker` 提供 `DockerContainerServiceImpl`、配置对象和自动配置；向上主要被 `ouroboros-dev-app-management` 这类开发平台装配模块消费。新增 Podman 等运行时应优先增加新的适配实现，而不是把多运行时逻辑继续堆进 `DockerContainerServiceImpl`。

## 深入文档索引

- `推荐阅读`：[dev-module/ouroboros-dev-container/DESIGN.md](../../../../dev-module/ouroboros-dev-container/DESIGN.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

源码审计表明旧概要与当前双层结构仍然基本一致，模块边界清晰、单入口价值明确，因此当前已经整理出 `DESIGN.md` 作为权威入口，用来替代旧的 Docker 说明式概要。
