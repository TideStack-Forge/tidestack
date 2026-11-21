# ouroboros-webapi-model 设计文档

## 1. 概述

`ouroboros-webapi-model` 是平台 Web API 建模内核，负责把 HTTP API 从控制器实现提升为可建模、可注册、可动态管理的统一接口元数据体系。它解决的是“接口如何被表达和治理”，而不是业务逻辑本身。

## 2. 模块结构

聚合 `pom.xml` 显示该模块由三个主要部分组成：

- `ouroboros-webapi-model-core`
- `ouroboros-webapi-model-dev`
- `ouroboros-webapi-runtime`

这三层分别对应模型定义、开发管理与运行时路由集成，体现出它既是核心模型层，也是运行时 API 暴露机制的一部分。

## 3. 核心模型与机制

### 3.1 WebAPIModel

`WebAPIModel` 是接口元数据核心抽象，用来描述接口的路径、方法、处理方式与附加属性。它让“接口定义”从控制器代码中被提取出来，成为平台可治理的模型对象。

### 3.2 HandlerType 与 WebAPIScope

`HandlerType` 描述接口处理方式，`WebAPIScope` 描述接口的开放范围与组织边界。这两个抽象决定了 API 不仅是路由，还包含处理策略和作用域语义。

### 3.3 路由集成体系

`RequestMappingIntegrationCenter` 捕获 Spring MVC 路由并提供模型来源，按当前模型集合重新注册路由；`RouterFunctionMappingIntegrationCenter` 负责函数式路由集成。模型装配与 HTTP 导出复用这些正式来源，导出细节见 [OpenAPI 指南](docs/openapi-guide.md)。

具体抽象上，`WebAPIModelProvider` 是 API 模型来源提供者，`WebAPIRouteRegistry.register(Collection<WebAPIModel>)` 是批量注册边界；旧路由清理由具体集成器在重新注册时处理。

## 4. 运行时链路

该模块维护时需要重点把握三条链路：

- Web API 模型加载流程
- 动态路由注册流程
- 请求处理流程

这三条链路共同决定了 API 从模型定义到实际对外暴露的闭环。

## 5. 维护重点

维护该模块时，应优先守住以下边界：

- 保持 `core / runtime / dev` 分工清晰。
- API 元数据与控制器实现不能重新耦合回去。
- 路由注册、处理类型和开放作用域的治理应统一落在该模块，而不是被外围框架细节主导。
- 新的开发态能力应建立在模型抽象之上，而不是绕过模型直接操作路由实现。
