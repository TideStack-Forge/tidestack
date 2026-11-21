# Ouroboros 开发应用管理模块设计文档

## 1. 概述

`ouroboros-dev-app-management` 是开发平台中的应用管理核心模块，负责管理开发阶段应用的创建、分类、容器运行、开发菜单、调试支持与相关交付能力。它是维护者进入开发平台业务管理面的主入口之一。

## 2. 模块位置与依赖

从 `pom.xml` 可见，该模块直接依赖：

- `ouroboros-dev-version-control-system`
- `ouroboros-dev-container-core`
- `ouroboros-configuration-core`
- `ouroboros-message-queue-core`
- `ouroboros-websocket-core`
- `ouroboros-dev-helper-dev`
- `ouroboros-dev-model-manage-webapi-scaffold`
- `ouroboros-dev-datasource`
- `ouroboros-menu-model-core`
- `ouroboros-app-module-model-core`

这说明它并不是底层能力实现者，而是开发平台中的典型装配层模块。

## 3. 核心职责

### 3.1 应用管理

`AppManagementService` 负责应用创建、更新、分类和生命周期管理，是开发平台业务管理面的核心服务。

### 3.2 容器管理

`AppContainerService` 保留容器管理 API；其实现将创建、启动、停止、重启与工作区重载委托给 `AppLifecycleService`，后者通过 `DevRuntimeService` 调度运行时后端。端口分配、工作区/配置准备和数据源初始化由相应协作服务承担，不能把当前链路理解为直接调用 Docker 的单一路径。

### 3.3 应用分类与扩展管理

`AppCategoryService` 承接应用分类组织，扩展 JAR 管理则负责开发阶段的扩展能力装配。这说明模块不只是做 CRUD，也在承担开发平台的交付编排职责。

### 3.4 开发交付与调试支持

`DevMenuService` 与调试支持控制器一起，构成开发平台中的菜单交付面和调试辅助能力，使该模块不仅管理“应用”，也管理开发体验。开发交付面还包括自定义样式能力。

## 4. 运行时与平台集成

该模块设计上与以下能力高度耦合但边界应清晰：

- 版本控制能力提供开发资产治理
- 容器能力提供运行环境
- 配置和数据源能力提供开发环境准备
- 消息队列与 WebSocket 提供实时状态与异步协作
- 开发辅助模块提供运行时命令与元数据桥接

维护时应持续把它理解为“开发平台业务装配层”，而不是继续向下吞并这些基础能力。

## 5. 维护重点

维护该模块时，应重点关注：

- 避免把版本治理、容器编排、元数据采集等职责继续内聚进本模块。
- 新增功能优先判断能否下沉到 `dev-helper`、`dev-container`、`version-control-system` 等更专门模块。
- 保持管理服务、调试支持、开发菜单与容器运行职责之间的边界清晰。
