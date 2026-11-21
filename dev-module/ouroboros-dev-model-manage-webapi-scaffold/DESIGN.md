# ouroboros-dev-model-manage-webapi-scaffold 设计文档

## 1. 概述

`ouroboros-dev-model-manage-webapi-scaffold` 是开发平台中的模型管理 WebAPI 脚手架模块，负责为各类开发态模型提供统一的 CRUD、VCS 锁定/提交/回滚、事件发布和前端片段支持。它是多个 `*-dev` 模块复用的基础框架，而不是某一个具体模型的管理实现。

## 2. 模块结构

当前模块是单一 jar 库，不再继续拆分子模块，但内部职责可以分成四块：

- 开发服务抽象：`DevelopService<M>`、`BaseDevelopService<M>`
- Web 层控制器：`ModelDevelopController`、`AbstractSingleFileController`
- VCS 与身份适配：`DefaultVCSUserNameResolver`、`DefaultVCSUserProvider`
- 开发片段资源：`vcs-buttons.json.ftl` 与相关前端片段输出

该模块直接依赖版本控制系统、安全、组织结构和模板能力。Web 层还包括 `DevFragmentSchemaController`，VCS 适配还包括开发应用名过滤器。

## 3. 核心模型与机制

### 3.1 DevelopService

`DevelopService<M>` 是开发态模型管理的统一接口，定义创建、更新、删除、回滚、提交、锁定与查询等标准能力。它让具体模型管理服务可以用统一方式暴露给开发平台。

### 3.2 BaseDevelopService

`BaseDevelopService<M>` 是最关键的抽象基类，负责整合：

- 文件路径和模型持久化
- VCS 锁定、提交和回滚
- 关联文件处理
- 模型生命周期事件发布

它使具体 `*-dev` 模块通常只需要补足少量模型相关细节。

### 3.3 发布准备与草稿保存

[DevelopService](src/main/java/com/ouroboros/modelmanage/DevelopService.java) 的 `validateForPublication` 与 `prepareCreate` 用于复合发布前的模型校验和创建准备。通用 `BaseDevelopService.prepareCreate` 计算主文件、关联文件和碰撞路径并返回 `PreparedModelCreate`；准备阶段不写入文件。默认接口不支持创建准备时返回明确失败，具体模型需要显式接入。

草稿 create/update 的校验与发布校验职责分开。`BaseDevelopService.create` 与 `prepareCreate` 共用内部资源准备过程；草稿创建不调用可被子类加强为发布校验的 `prepareCreate`。例如 LogicFlow 草稿可以保存诊断，发布准备则要求模型有效。消费方应走正式服务边界，不能复制序列化规则或绕过具体模型的发布校验。

### 3.4 ModelDevelopController

`ModelDevelopController` 通过收集所有 `DevelopService` 并根据 `@DevelopModule` 做动态路由，形成统一的 `/api/dev/{appName}/{moduleName}` 开发管理入口。这是该模块区别于普通工具库的核心设计。

### 3.5 AbstractSingleFileController

`AbstractSingleFileController<T, F>` 为单文件类型资源提供更轻量的管理控制器抽象，适合模块树、单页配置等场景。它是很多开发态单文件管理页面的基础骨架。

## 4. VCS 与事件机制

该模块的关键价值不只是 CRUD，而是把开发平台里的模型管理能力统一绑定到：

- VCS 锁定 / 提交 / 回滚
- 用户显示名解析
- 模型创建、更新、删除事件

这意味着维护时要把“管理 API”与“版本治理”和“生命周期事件”一起看，而不能只把它理解为一组 REST 控制器。

## 5. 维护重点

维护该模块时，应优先关注：

- `DevelopService`、`BaseDevelopService`、`ModelDevelopController` 的清晰分工。
- 新开发模块优先复用 `BaseDevelopService` 或 `AbstractSingleFileController`。
- 具体模型规则不要反向污染通用脚手架。
- VCS 集成、事件发布和前端片段模板应保持为跨模块复用能力，而不是为单个模块定制。
