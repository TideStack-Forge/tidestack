# ouroboros-app-module-model 设计文档

## 1. 概述

`ouroboros-app-module-model` 是 Ouroboros 核心引擎层中的应用模块组织模型，负责把应用模块的层次结构、菜单关联、权限关联和重组差异统一为可管理的模块模型。它解决的是“应用如何被组织成平台内的模块树”，而不是直接承担菜单系统或权限系统本身。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由四部分组成：

- `ouroboros-app-module-model-core`
- `ouroboros-app-module-model-procode`
- `ouroboros-app-module-model-runtime`
- `ouroboros-app-module-model-dev`

其中：

- `core` 定义 `AppModule`、`AppModuleProvider`、`AppModuleIntegrator`、`AppModuleDiff` 等核心抽象。
- `procode` 负责从菜单和权限提供者投影出模块，并提供 JAR 模块来源。
- `runtime` 负责差异提供、最终集成与自动配置。
- `dev` 负责模块树重组、开发平台控制器和 VCS 相关接入。

`core` 还提供模块关联装饰器与树节点模型。

## 3. 核心模型与机制

### 3.1 AppModule

`AppModule` 是应用模块核心抽象，定义模块名称、标题、图标、排序、菜单关联和权限关联等基础语义。它是模块树组织的第一抽象。

### 3.2 AppModuleProvider 与 AppModuleIntegrator

模块来源通过 `AppModuleProvider` 扩展，`AppModuleIntegrator` 负责统一聚合与输出最终模块结构。这保证了来源可以扩展，而读取入口仍然保持集中。

### 3.3 AppModuleDiff

`AppModuleDiff` 与 `AppModuleDiffUtil` 承担模块重组差异和 patch 机制，说明本模块不仅维护静态模块树，还负责维护“开发态如何重组模块结构”的差异语义。

### 3.4 ProCode 投影器

`ProCodeMenuAppModuleProjector` 与 `ProCodeAuthorityAppModuleProjector` 负责把菜单模型和权限模型投影为应用模块。它们是本模块与菜单/权限体系衔接的关键桥梁。

## 4. 菜单、权限到模块树的数据流

当前实现中的关键链路是：

- 菜单和权限来源先被投影为应用模块候选。
- `AppModuleProvider` 体系统一汇聚不同来源的模块定义。
- `runtime` 层通过 integrator 和 diff provider 生成最终模块结构。
- `dev` 层对模块树重组和差异编辑提供维护入口。

这意味着维护时要守住两层边界：

- 菜单模型、权限模型仍属于各自子系统。
- 应用模块模型只负责组织、投影和重组，不反向吞并菜单或权限内核。

## 5. 维护重点

维护该模块时，应优先关注：

- `AppModule`、`AppModuleProvider`、`AppModuleIntegrator`、`AppModuleDiff` 的清晰分工。
- 新模块来源优先通过 provider、integrator 或 diff provider 接入。
- 菜单/权限投影逻辑应限制在 `procode` 与运行时整合边界内。
- 开发态重组能力要保持为差异管理能力，而不是反向改写核心模块抽象。
