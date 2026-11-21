# Ouroboros 运行时拓扑

## 目标

本页描述的是 Ouroboros 在运行和部署层面的组成关系：哪些是可启动的后端运行单元，哪些是独立发布的前端交付物，以及它们如何在环境中拼接成完整平台。它不是依赖顺序图；模块依赖方向仍以 `architecture/dependency-map.md` 为准。

## 运行入口

从维护者视角看，当前仓库存在三类核心运行入口：

- 后端开发平台入口：`platform/ouroboros-develop`，由 `OuroborosDevelopApplication` 启动，偏向开发平台和低代码管理场景。
- 后端统一运行时基座入口：`platform/ouroboros-mothership`，由 `OuroborosApplication` 启动，偏向统一运行时基座、部署环境和微前端承载场景。
- 前端交付入口：`frontends/web`，承载前端基座、微前端模块和 SDK 的独立发布链路。

这三类入口共同构成运行时拓扑：后端应用负责能力装配与 API/资源暴露，前端基座负责用户侧交付与微前端加载。

## 后端运行单元

当前后端运行单元主要是两个 Spring Boot 应用：

- `ouroboros-develop`：依赖 `ouroboros-ability-all-runtime`、`ouroboros-dev-module-all` 和开发元数据提供器，更强调开发平台、模型管理和维护者工作台场景。它是维护者最常用的上层运行入口。
- `ouroboros-mothership`：同样依赖 `ouroboros-ability-all-runtime`，但在 `application-base.yml` 中显式携带 `metadata.path`、`microWeb.deployPath`、上传存储与 token 等运行时参数，更像面向部署环境的统一运行时基座。

两者都以 `jar` 形式打包，并各自提供 Docker 交付资产，因此“后端运行单元”不是单纯的 Maven 模块，而是具备启动入口、配置约束和镜像边界的应用形态。

## 前端运行单元

`frontends/web` 是当前前端运行单元的主入口，内部至少包含：

- `main-app`：前端主应用基座。
- 以微前端方式挂载的功能模块，例如逻辑流编辑器、调试工具栏等。
- `ouroboros-sdk`：单独发布的微前端 SDK。

前端发布链路与后端分离：[CI/CD 规则](../../rules/ci-cd.md)依据前端 Jenkinsfile 说明 `frontend-web/snapshot/*`、`frontend-web/release/*`、`frontend-web-v*` 和 `frontend-web-sdk-v*` 等分支与 tag 规则，说明前端运行单元既可以作为基座镜像发布，也可以把 SDK 单独发布给外部接入方。

## 部署与环境边界

运行时拓扑真正开始产生边界约束，是在平台模块和前端交付物进入部署环境之后：

- `platform/*` 模块负责把 `core`、`security`、`ability` 和上层交付模块装配成可启动应用。
- `frontends/web` 负责把 `core/ouroboros-ui-model` 等后端模型能力转化为浏览器侧可加载的页面与微前端壳层。
- Docker 文件、应用配置、上传存储根路径、元数据路径、微前端部署目录等，都属于运行时拓扑的一部分，而不是底层引擎抽象本身。

因此，当问题涉及镜像、环境变量、文件系统路径、前端基座版本或部署形态时，应优先把它视为“运行时拓扑问题”，而不是简单回退到模块依赖分析。

## 维护者排障阅读路径

建议按下面的顺序定位运行时问题：

1. 先确认问题属于 `ouroboros-develop`、`ouroboros-mothership` 还是 `frontends/web` 的运行单元。
2. 如果是应用装配或发布问题，回看 [build-and-release.md](build-and-release.md)。
3. 如果是模块边界或职责混淆，回看 [dependency-map.md](dependency-map.md) 和 [../layers/application-layer.md](../layers/application-layer.md)。
4. 如果是具体入口的运行时角色不清，继续阅读 [开发平台](../modules/platform/ouroboros-develop.md)、[运行时基座](../modules/platform/ouroboros-mothership.md)与[前端交付](../modules/frontends/web.md)模块摘要。
