# ouroboros-menu-model 设计文档

## 1. 概述

`ouroboros-menu-model` 是 Ouroboros 核心引擎层中的菜单与导航模型内核，负责统一菜单定义、菜单来源聚合、权限过滤和树形导航输出。它提供的是平台级导航模型，而不是某个具体应用页面的菜单配置。

## 2. 模块结构

聚合 `pom.xml` 与当前源码显示，该模块由两部分组成：

- `ouroboros-menu-model-core`
- `ouroboros-menu-model-runtime`

其中：

- `core` 定义 `MenuModel`、`MenuModelProvider`、`MenuModelCenter` 和显示模式等核心抽象。
- `runtime` 负责 `JarMenuModelProvider`、`MenuService`、`MenuController`、树形视图对象以及自动配置。

## 3. 核心模型与机制

### 3.1 MenuModel

`MenuModel` 是菜单对象的统一抽象，定义 URL、全名、图标、标题、排序和显示模式等基本语义。它承担的是“菜单资源模型”，而不是权限判断或树构建逻辑。

### 3.2 MenuModelProvider 与 MenuModelCenter

菜单来源通过 `MenuModelProvider` 扩展，`MenuModelCenter` 负责统一聚合所有 provider 的输出。这保证了菜单来源可以扩展，但读取入口仍然保持集中。

### 3.3 Runtime 服务

`MenuService` / `MenuServiceImpl` 负责把原始菜单模型转为前端可消费的结果，并结合当前认证信息做主菜单过滤、树构建和次级菜单加载。`MenuController` 则将这套运行时能力暴露为标准 Web API。运行时服务还会消费应用模块 integrator 和当前认证信息，说明它处在菜单资源与权限输出之间。

### 3.4 TreeMenuModelVo

`TreeMenuModelVo` 是面向前端输出的树形视图模型。它说明本模块不仅维护菜单定义，还负责把菜单定义整理为可直接消费的导航树。

## 4. 权限与数据流

当前实现中最重要的链路有两条：

- 启动时通过 `MenuModelProvider` 发现并聚合菜单定义。
- 请求时根据当前身份过滤授权菜单，并组织成树形结果返回前端。

主菜单先由 `AppModuleIntegrator` 按模块的 `parent` 重排，再过滤授权页面。菜单全名与权限标识保持稳定，结构父节点可以不同于全名的点分前缀。运行时沿重排后的实际父链保留已授权菜单的导航祖先；仅为导航补入的祖先不输出自身 URL，也不会使兄弟页面获得授权。父链遍历去重，且只在主菜单 scope 内补齐；没有可见页面的空目录会在构树时移除。次级菜单仍按原有授权和去重规则输出。

这意味着维护时要持续守住两层边界：

- 菜单来源聚合属于模型层。
- 权限过滤和树构建属于运行时服务层。

## 5. 维护重点

维护该模块时，应优先关注：

- `MenuModel`、`MenuModelProvider`、`MenuModelCenter`、`MenuService` 的职责分离。
- 新菜单来源优先通过 `MenuModelProvider` 接入，而不是直接修改 `MenuServiceImpl`。
- 权限过滤逻辑不要反向污染菜单模型抽象。
- 次级菜单、树构建和授权输出的变化，应尽量限制在 `runtime` 层。
