# Ouroboros 模型体系

## 目标

本页用于统一解释 Ouroboros 中几类核心模型是如何分工、投影和协作的，帮助维护者区分“模型定义发生在哪里”和“模型被消费或投影到哪里”。它不替代单模块摘要，而是提供跨模型视角的共识。

## 核心模型族

当前维护者需要优先把握以下模型族：

- `ouroboros-data`：定义动态数据模型、查询 DSL 和数据访问运行时，是多数上层模型最终落地到存储与查询的基础。
- `ouroboros-business-model`：定义业务模型及其投影过程，是跨数据、UI、UI Schema、Web API、逻辑流、菜单和权限等模型族的编排中枢。
- `ouroboros-ui-model`：定义 UI 元数据、Schema 和 MicroWeb 相关承载方式，回答“页面是什么、如何被组织”。
- `ouroboros-webapi-model`：定义 Web API 元数据、处理类型和路由注册边界，回答“接口是什么、如何暴露”。
- 邻接模型族：`ouroboros-menu-model` 负责导航与菜单表达，`ouroboros-app-module-model` 负责应用模块组织与运行时归属，它们更偏“组织和装配模型”，而不是底层业务数据模型。

## 模型之间的投影与协作

这些模型族不是并列孤岛，而是存在明显的源模型与投影关系：

- `ouroboros-data` 更像底层源模型之一，定义结构化数据和查询能力，为其他模型提供持久化与访问基础。
- `ouroboros-business-model` 位于中间层，把业务语义投影到数据、UI、UI Schema、Web API、逻辑流、菜单和权限等不同维度，因此它更像“模型编排器”而不是最终落点。
- `ouroboros-ui-model` 和 `ouroboros-webapi-model` 分别把业务与数据能力投影到用户界面和接口边界，前者面向 UI 元数据，后者面向 REST/API 暴露。
- `ouroboros-menu-model` 和 `ouroboros-app-module-model` 则进一步组织页面、入口和应用装配关系，让模型不仅能定义能力，还能被平台按导航和应用维度消费。

换句话说，`business-model` 负责把业务语义桥接到多个目标模型；`data`、`ui-model`、`webapi-model` 提供较明确的运行时落点；菜单和应用模块模型负责把这些结果组织成最终交付结构。

## 运行时落点

模型体系在运行时主要落到三类位置：

- 核心运行时：如 `ouroboros-data-runtime`、`ouroboros-ui-model-runtime`、`ouroboros-ui-model-schema-runtime`、`ouroboros-webapi-runtime`，负责把模型定义转成可执行能力。
- 开发管理侧：如 `*-dev` 模块，负责让维护者或开发者管理模型定义、查看元数据和进行低代码配置。
- 平台与前端交付侧：`platform/*` 和 `frontends/web` 消费这些模型，把模型能力转成可启动平台、可访问 API、可见页面和微前端入口。

因此，模型并不直接等于“页面”或“接口实现”；模型先在 `core` 内定义，再通过 runtime/dev/platform/frontend 等不同落点被执行和消费。

## 维护边界

维护模型体系时，应遵循以下边界：

- 不要在多个模型族重复定义同一份业务语义；优先明确谁是源模型，谁是投影结果。
- `business-model` 可以协调多个模型族，但不应取代 `data`、`ui-model`、`webapi-model` 各自的核心职责。
- `menu-model` 和 `app-module-model` 更适合组织导航、归属和装配关系，不应承载底层数据或权限细节。
- 平台和前端模块应消费模型结果，而不是把交付侧约束反向写回核心模型抽象。

## 深入阅读入口

- [../modules/core/ouroboros-data.md](../modules/core/ouroboros-data.md)
- [../modules/core/ouroboros-business-model.md](../modules/core/ouroboros-business-model.md)
- [../modules/core/ouroboros-ui-model.md](../modules/core/ouroboros-ui-model.md)
- [../../../core/ouroboros-webapi-model/DESIGN.md](../../../core/ouroboros-webapi-model/DESIGN.md)
- 如需回到整体层次边界，再看 [../layers/core-layer.md](../layers/core-layer.md)
