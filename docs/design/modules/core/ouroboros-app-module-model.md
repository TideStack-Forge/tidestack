# ouroboros-app-module-model 模块设计摘要

## 模块定位

`ouroboros-app-module-model` 是核心引擎层中的应用模块组织模型，负责把应用模块的层次结构、菜单关联、权限关联和重组差异统一为可管理的模块模型。它解决的是“应用如何被组织成平台内的模块树”，而不是直接承担菜单系统或权限系统本身。

## 责任边界

该模块负责应用模块抽象、模块来源聚合、菜单/权限到模块的投影，以及运行时与开发态的模块重组差异管理。它不负责重新定义菜单模型和权限模型，也不负责开发平台最终的应用管理页面交付。维护时要守住“统一模块组织模型”与“下层菜单/权限引擎”的边界。

## 依赖关系

从聚合结构和源码看，模块分为 `core`、`procode`、`dev`、`runtime` 四层：`core` 定义 `AppModule` 及 diff / integrator 抽象，`procode` 负责从菜单和权限提供者投影出模块，`runtime` 负责差异提供和最终集成，`dev` 负责模块树重组和 VCS 文件检查器接入。新增模块来源应优先通过 `AppModuleProvider` 或 diff/integrator 体系接入。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-app-module-model/DESIGN.md](../../../../core/ouroboros-app-module-model/DESIGN.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

源码审计表明旧概要与当前四层结构仍然可以对上；在 `menu-model` 与 `field-type` 已完成升格后，维护者更需要一个清晰的模块组织入口，因此当前已经整理出 `DESIGN.md` 作为权威入口。
