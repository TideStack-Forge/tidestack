# ouroboros-ui-model 设计文档

## 1. 概述

`ouroboros-ui-model` 是平台 UI 元数据与 Schema 建模内核，负责把“页面是什么、如何被组织、如何以微前端方式承载”建模为可复用能力。它是后端模型系统与前端交付体系之间的重要桥梁。

## 2. 模块结构

聚合 `pom.xml` 显示该模块由以下主要子模块组成：

- `ouroboros-ui-model-core`
- `ouroboros-ui-model-runtime`
- `ouroboros-ui-model-lowcode`
- `ouroboros-ui-model-dev`
- `ouroboros-ui-model-schema`
- `ouroboros-ui-model-schema-runtime`
- `ouroboros-ui-model-schema-dev`
- `ouroboros-ui-model-microweb`
- `ouroboros-ui-model-microweb-lowcode`
- `ouroboros-ui-model-microweb-dev`
- `ouroboros-ui-model-microweb-nginx`

这说明它内部同时覆盖核心 UI 抽象、Schema 体系、低代码承载、开发支持与 MicroWeb 交付，是一个明确的模块族而非单一运行时包。

## 3. 核心模型与机制

### 3.1 UIModel

`UIModel` 是该模块的核心契约，描述页面标题、路径、渲染类型和扩展属性。它把“页面元数据”与“具体前端渲染实现”分开，使平台可以先建模、后交付。

`UIModelProvider` 是 UI 模型的加载与提供接口。

### 3.2 UI Schema 体系

UI Schema 负责组织页面结构、表单和组件表达，是低代码页面构建的重要基础。它与 `UIModel` 的关系是：前者更偏结构描述，后者更偏页面元信息与运行时入口。

### 3.3 MicroWeb 与路由

`UIRouteServiceImpl` 聚合 `UIModelProvider` 输出路由；`MicroWebModelProvider` 提供微前端元数据，`microweb-nginx` 通过 `MicroWebDeployService` 落地静态资源。模型、路由与资源部署分别承载页面入口、挂载路径和交付内容。

## 4. 运行时落点

该模块的运行时价值主要体现在三条链路：

- UI 模型加载流程
- Schema 渲染流程
- 微前端部署流程

因此它在维护上的关键不是单个类，而是保持元数据、Schema 与 MicroWeb 三条链路之间的边界清晰。

## 5. 维护重点

维护该模块时，应长期关注：

- `core / runtime / schema / microweb / dev` 的分工不能混乱。
- UI 元数据抽象不能退化成具体前端实现细节集合。
- Schema 与 MicroWeb 都是重要扩展面，但不应反向替代 `frontends` 的交付职责。
- 当低代码需求扩展时，优先复用既有模型体系，而不是临时新增平行抽象。
