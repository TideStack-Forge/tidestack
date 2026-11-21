# Ouroboros 系统总体设计

Ouroboros 是以 Java Maven monorepo 组织的低代码平台。动态数据模型、业务建模、逻辑流和 UI 模型构成核心引擎；安全与通用业务能力支撑应用模块、开发模块、平台运行时和前端交付。

本页说明模块群职责、协作边界和阅读入口。依赖约束由 [依赖关系图](dependency-map.md) 维护，聚合、子树和发布边界由 [构建与发布设计](build-and-release.md) 维护，各模块的局部机制沿模块摘要进入唯一正文。

## 模块群与职责

| 模块群 | 职责 | 当前设计入口 |
| --- | --- | --- |
| `libs` | 通用工具、公共组件与基础抽象 | [基础支撑模块](../modules/libs/README.md) |
| `core` | 数据模型、业务模型、逻辑流、UI 模型及相关核心引擎 | [核心引擎模块](../modules/core/README.md) |
| `security` | 访问控制与安全能力 | [安全模块设计](../../../security/DESIGN.md) |
| `ability` | 配置、通知、组织、文件、应用运行时等可复用业务能力 | [业务能力模块](../modules/ability/README.md) |
| `app-module` | 面向业务场景组合核心与能力模块 | [应用模块](../modules/app-module/README.md) |
| `dev-module` | 模型、流程、项目生成等开发态管理能力 | [开发模块](../modules/dev-module/README.md) |
| `platform` | 装配模块形成可运行的开发平台与母舰平台 | [平台运行时](../modules/platform/README.md) |
| `frontends` | Web 基座、微前端编辑器和前端 SDK | [前端模块](../modules/frontends/README.md) |

`libs` 为上层提供支撑，`core` 和 `security` 承载核心抽象与安全边界，`ability` 复用这些基础能力，应用与开发模块再由平台运行时装配。前端通过正式 HTTP/WebAPI 和宿主公共属性消费后端及微前端能力；Java 依赖与前端交付边界分别沿上述入口核对。

这张职责表不授权跨层调用。模块依赖仍遵守既有分层和正式扩展点；例外及约束检查见依赖关系图，不能从目录并列关系推断彼此可以任意依赖。

## 工程目录与交付边界

| 路径 | 架构用途 |
| --- | --- |
| `bom` | Maven 依赖与版本管理 |
| `maven-archetypes` | 工程脚手架 |
| `scripts` | 构建、同步、检查和运行辅助 |
| `docs` | 共享规则、规格、设计、接口及持续知识入口 |
| `legacy-compat` | 存量兼容边界，结合依赖关系核对用途 |
| `micro-app` | 扩展交付内容，结合构建与前端入口核对装配 |

这些工程目录不因此成为独立的核心业务模块。模块与子树的实际维护范围以版本控制、聚合配置及对应模块入口为准；本地 worktree、依赖缓存、构建产物和单次任务输出不属于系统模块。

## 维护者阅读路径

1. 由本页确认模块群职责，再看 [依赖关系图](dependency-map.md) 和 [分层设计](../layers/README.md)，判断变更允许落在哪一层。
2. 查看 [构建与发布设计](build-and-release.md)，确认聚合、独立交付与版本边界。
3. 从 [模块设计索引](../modules/README.md) 或上表进入具体摘要，再按深入文档索引读取模块中的设计正文。
4. 涉及跨模块协作时读取 [横切设计](../cross-cutting/README.md)；需求与行为预期从 [需求索引](../../requirements/README.md) 定位。

模块摘要负责定位、边界与入口，详细机制在对应模块正文维护。评审、施工计划和单次验证留在关联任务，不能代替当前设计。
