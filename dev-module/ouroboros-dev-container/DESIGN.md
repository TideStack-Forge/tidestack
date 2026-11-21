# ouroboros-dev-container 设计文档

## 1. 概述

`ouroboros-dev-container` 是开发平台中的容器运行时支撑模块，负责为开发环境提供容器生命周期管理、镜像拉取和状态事件监听能力。它更像开发平台底层的容器适配层，而不是直接面向维护者的业务管理模块。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由两部分组成：

- `ouroboros-dev-container-core`
- `ouroboros-dev-container-docker`

其中：

- `core` 定义 `ContainerService`、`Container`、`ContainerStatus`、`ContainerEventType`、`DockerEventListener` 等抽象。
- `docker` 提供 `DockerContainerServiceImpl`、Docker 配置对象与自动配置。

该模块向上主要被 `ouroboros-dev-app-management` 这类开发平台装配模块消费；事件监听扩展点由 `DockerEventListener` 与 `EventObject` 组成。

## 3. 核心模型与机制

### 3.1 ContainerService

`ContainerService` 是容器生命周期管理的统一接口，定义创建、删除、启动、暂停、停止、重启、查询和镜像拉取等标准能力。它是整个模块的主协议。

### 3.2 Container

`Container` 是容器标准化数据模型，统一描述容器 ID、名称、镜像、端口映射、卷映射、环境变量、网络与状态。这使上层使用方不需要直接依赖 Docker 原生对象。

### 3.3 状态与事件模型

`ContainerStatus`、`ContainerEventType` 与 `DockerEventListener` 共同定义容器状态语义和事件监听机制，说明本模块不仅负责命令式操作，也负责状态变化通知。

### 3.4 DockerContainerServiceImpl

`DockerContainerServiceImpl` 是当前默认 Docker 实现，负责把通用容器抽象映射到 Docker Java API，并通过自动配置接入开发平台运行环境。

## 4. 运行时链路

当前实现中的关键链路是：

- 上层开发平台通过 `ContainerService` 发起容器生命周期操作。
- Docker 实现层把标准化模型和命令映射到具体 Docker 能力。
- 容器状态变化通过事件监听机制回传给上层。

这说明该模块真正维护的是“开发平台容器运行时适配层”，而不是完整的开发环境编排产品。

## 5. 维护重点

维护该模块时，应优先关注：

- `ContainerService`、容器模型和 Docker 具体实现之间的职责分离。
- 新运行时应优先新增新的 `ContainerService` 适配实现，而不是继续堆进 `DockerContainerServiceImpl`。
- Docker 特定配置和自动配置应限制在 `docker` 子模块。
- 事件监听语义要保持稳定，避免让上层直接依赖底层 Docker 细节。
