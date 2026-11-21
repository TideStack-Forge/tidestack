# TideStack 潮汐栈

TideStack 是一个基于 Java 和 React 的高低代码融合应用开发平台。通过数据模型、业务行为、流程编排和页面配置构建业务应用，也支持用 Java、脚本和微前端扩展能力。

本仓库开放平台整体源码，采用 monorepo 统一维护。代码中的 `Ouroboros`、`com.ouroboros` 和 `ouroboros-*` 是现有工程标识，继续用于 Java 包名、Maven 坐标、SDK 和运行时配置。

[产品手册](https://github.com/TideStack-Forge/tidestack-docs) · [功能地图](docs/features/README.md) · [开发指南](docs/development-guide.md) · [架构](ARCHITECTURE.md) · [Apache-2.0](LICENSE)

## 功能地图

| 能力 | 可以做什么 | 了解更多 |
| --- | --- | --- |
| 数据与业务建模 | 定义数据、字段、关系、业务 Action/Event 和动态表单 | [模型与应用](docs/features/modeling.md) |
| 逻辑编排 | 配置逻辑节点、执行流程并进行调试 | [LogicFlow](docs/features/logicflow.md) |
| 审批流程 | 设计审批图、配置审批任务并接入流程引擎 | [审批](docs/features/approval.md) |
| 页面与接口 | 使用 UI Schema、React 微前端和 SDK 交付页面与接口 | [页面与接口](docs/features/presentation.md) |
| 统计分析 | 构建数据集、图表、仪表板和复合视图 | [统计分析](docs/features/analytics.md) |
| 平台与开发工具 | 组织与权限、配置、文件、通知、RPC、AI 接入，以及应用开发与 IDE 工具 | [运行服务](docs/features/runtime-services.md)、[开发平台](docs/features/development.md) |

各功能的启用条件、扩展方式和当前边界见对应文档。

## 快速开始

### 获取源码

```bash
git clone --branch master https://github.com/TideStack-Forge/tidestack.git
cd tidestack
```

### 技术栈

| 工具 | 版本 |
| --- | --- |
| Java | 21 |
| Spring Boot | 3.5.11 |
| Maven | 3.6.3 或更新版本 |
| Node.js | 22.23.1，见 [.nvmrc](.nvmrc) |
| React／qiankun | 18.3.1／2.10.16 |
| 配套服务 | 按部署方式配置数据库、Redis、RabbitMQ 和容器环境 |

后端版本与依赖以 [pom.xml](pom.xml) 和 [bom/pom.xml](bom/pom.xml) 为准；Web、IDE、Schema 和插件分别维护自身版本。

### 构建前端基座

在仓库根目录执行：

```bash
npm ci --prefix frontends/web/ouroboros-sdk
npm ci --legacy-peer-deps --prefix frontends/web/main-app
npm run build --prefix frontends/web/main-app
```

前端业务需要兼容的平台后端。开发服务器、后端代理和微前端接入见[前端说明](frontends/web/README.md)；也可以使用独立的 [tidestack-web](https://github.com/TideStack-Forge/tidestack-web) 仓库。

### 后端与平台运行

完整后端的公共 Maven 依赖链尚未发布完备，当前构建仍需现有依赖源与配置。公共父 POM、BOM、共享模块及 Snapshot 的发布正在接入；源码公开不代表这些制品已经可以从 Maven Central 下载。发布边界见[构建与发布设计](docs/design/architecture/build-and-release.md#公共-maven-制品托管)。

按[开发指南](docs/development-guide.md)配置依赖与环境，再按[部署指南](docs/deployment-guide.md)或[平台安装手册](docs/handbook/docs/getting-started/index.md)启动所需服务。构建脚本的参数入口是：

```bash
bash build.sh --help
```

## 独立开源模块

需要单独使用某个引擎、编辑器或工具时，可以从相应仓库开始。这些分发仓按正式清单从本总仓同步，构建和宿主依赖以各仓 README 为准。

| 仓库 | 用途 |
| --- | --- |
| [ouroboros-data-model](https://github.com/TideStack-Forge/ouroboros-data-model) | 数据模型引擎与查询、类型化模型支持 |
| [ouroboros-logicflow-model](https://github.com/TideStack-Forge/ouroboros-logicflow-model) | LogicFlow 逻辑编排引擎 |
| [tidestack-script](https://github.com/TideStack-Forge/tidestack-script) | 脚本、表达式与文本模板 |
| [tidestack-project-templates](https://github.com/TideStack-Forge/tidestack-project-templates) | 官方项目模板 |
| [tidestack-web](https://github.com/TideStack-Forge/tidestack-web) | React／qiankun 前端基座 |
| [tidestack-approval-workflow](https://github.com/TideStack-Forge/tidestack-approval-workflow) | 审批流引擎 |
| [tidestack-approval-workflow-designer](https://github.com/TideStack-Forge/tidestack-approval-workflow-designer) | 审批编辑器与只读流程图 |
| [tidestack-develop-plugin-common](https://github.com/TideStack-Forge/tidestack-develop-plugin-common) | IDE 共用的元数据编辑器与资源 |
| [tidestack-develop-vsc](https://github.com/TideStack-Forge/tidestack-develop-vsc) | VS Code 插件 |
| [tidestack-develop-intellij](https://github.com/TideStack-Forge/tidestack-develop-intellij) | IntelliJ IDEA 插件 |
| [tidestack-metadata-schema](https://github.com/TideStack-Forge/tidestack-metadata-schema) | 元数据 JSON Schema |
| [tidestack-skills](https://github.com/TideStack-Forge/tidestack-skills) | 面向平台开发者的 Agent Skills |

产品手册在 [tidestack-docs](https://github.com/TideStack-Forge/tidestack-docs) 独立维护；源码导出机制见[开源模块同步说明](scripts/open-source/README.md)。

## 仓库布局

| 目录 | 内容 |
| --- | --- |
| `libs/`、`bom/` | 公共工具、通用能力与依赖管理 |
| `core/`、`security/` | 数据、流程、统计等核心引擎与安全体系 |
| `ability/` | 配置、组织、文件、通知、RPC、AI 等能力 |
| `app-module/`、`dev-module/`、`platform/` | 应用模块、开发平台与运行基座 |
| `frontends/web/` | 前端基座、SDK 与微前端编辑器 |
| `ide-plugins/`、`metadata-schema/`、`skills/` | 开发者工具与元数据契约 |
| `project-templates/`、`maven-archetypes/`、`maven-plugins/` | 项目模板与工程工具 |
| `docs/`、`testing/`、`scripts/` | 技术文档、测试资产与工程脚本 |

## 参与贡献

欢迎通过 [Issues](https://github.com/TideStack-Forge/tidestack/issues) 报告问题或讨论需求，通过 [Pull Requests](https://github.com/TideStack-Forge/tidestack/pulls) 提交改进。问题报告请附上使用的提交或版本、复现步骤及相关日志，并移除凭据和业务敏感数据。

平台与共享实现优先在本仓库修改，再同步到分发仓。开发前阅读[开发指南](docs/development-guide.md)、[编码约定](docs/rules/coding.md)和[测试规范](docs/rules/testing.md)，为行为变更补充对应测试。

## 许可证与署名

本项目采用 [Apache License 2.0](LICENSE)。项目版权与归属声明见 [NOTICE](NOTICE)，再分发时请按协议保留适用的版权、许可证与归属声明，并标明修改过的文件。

另行声明许可证的 SDK、第三方代码和资源继续遵守各自协议。
