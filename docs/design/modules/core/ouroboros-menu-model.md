# ouroboros-menu-model 模块设计摘要

## 模块定位

`ouroboros-menu-model` 是核心引擎层中的菜单与导航模型模块，负责把菜单定义、菜单来源聚合、权限过滤和树形导航输出组织成统一菜单运行时。它是平台导航模型的基础层，而不是开发平台或具体业务页面的菜单编排器。

## 责任边界

该模块负责菜单模型、菜单提供者、菜单中心、菜单运行时服务和权限关联过滤，不负责应用模块重组，也不负责前端最终的路由实现细节。菜单来源聚合属于模型层，权限过滤和树构建属于运行时服务层。

## 依赖关系

模块只有 `core` 与 `runtime` 两层：`core` 定义 `MenuModel`、`MenuModelProvider` 和 `MenuModelCenter`，`runtime` 提供 `JarMenuModelProvider`、`MenuService`、`MenuController` 与树形视图对象；运行时服务消费应用模块 integrator 与当前认证信息。新菜单来源应优先通过 `MenuModelProvider` 接入。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-menu-model/DESIGN.md](../../../../core/ouroboros-menu-model/DESIGN.md)
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

源码审计表明模块结构稳定，当前已经收敛为 `DESIGN.md` 单文件权威入口。后续若再出现更细的运行时或权限专题，再按补充资料方式追加。
