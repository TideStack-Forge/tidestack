# Ouroboros 架构文档

> **版本**: 2.0  
> 当前机制与历史理由结合阅读，版本事实以源码配置为准。
> **目标读者**: 平台开发者、架构决策者、新加入团队成员

---

## 1. 架构愿景与设计哲学

### 1.1 平台定位

潮汐栈（TideStack，开发代号 Ouroboros） 是一个**企业级模型驱动低代码开发平台**，核心理念是"**元数据即应用**"——应用开发者无需编写传统 Java/JavaScript 代码，而是通过声明式的 JSON 元数据来定义数据模型、UI 界面、API 接口、业务流程、菜单导航和权限控制。平台在运行时动态解释这些元数据，生成完整可用的企业应用。

**技术栈**: Java 21 + Spring Boot 3.5.11 + React 18 + qiankun 微前端 + Amis 低代码 UI

**架构模式**: Maven 多模块 monorepo；默认反应堆边界由根 POM 聚合，独立工程见 README 布局；六层逻辑依赖结构

### 1.2 产品形态与职责域

潮汐栈低代码平台在产品形态上主要包括开发平台、统一运行时基座以及规划中的运维平台。开发平台面向维护者与应用构建者，负责模型定义、元数据管理、版本治理与开发辅助；统一运行时基座面向运行环境，负责应用元数据的加载、解释执行、动态渲染、API 暴露与业务运行；运维平台面向部署、发布、配置、监控、观测与运行治理场景。

在架构职责上，平台内部不直接以“平台/基座”划分责任，而是区分为设计时域、运行时域和运维治理域。开发平台主要承载设计时域能力，统一运行时基座主要承载运行时域能力，运维平台主要承载运维治理域能力，但三者与具体模块边界并非严格一一对应。

本文档中，“开发平台”“统一运行时基座”“运维平台”用于描述产品入口与交付形态；“设计时域”“运行时域”“运维治理域”用于描述系统内部职责划分；“层”用于描述代码依赖结构。不同术语不得混用。具体口径见 [术语与用语规范](GLOSSARY.md)。

### 1.3 核心设计理念

#### 1.3.1 配置优于编码 (Configuration over Code)

**理念**: 业务逻辑通过声明式配置表达，而非硬编码。

**体现**:
- 数据模型通过 JSON 定义，而非 Java Entity 类
- UI 页面通过 Amis Schema 配置，而非 React 组件代码
- API 通过 WebAPI Model 声明，而非 Spring Controller
- 工作流通过 JSON 流程定义，而非 Java 代码

**优势**:
- 降低开发门槛（配置比编码简单）
- 提高变更速度（修改配置无需重新编译）
- 支持可视化编辑（配置可映射为表单）
- 便于版本管理（JSON 文件易于 diff 和 merge）

**权威来源**: [Martin Fowler - Infrastructure As Code](https://martinfowler.com/bliki/InfrastructureAsCode.html)

#### 1.3.2 开发时与运行时分离 (Design-time vs Runtime Separation)

**理念**: 开发平台主要承载设计时能力，统一运行时基座主要承载运行时能力。

**体现**:
- **开发平台** (ouroboros-develop): 提供可视化建模工具、元数据管理、版本控制、运行时编排
- **统一运行时基座** (ouroboros-mothership): 加载应用元数据、解析并执行、提供业务服务

**优势**:
- 职责清晰（建模与执行分离）
- 部署灵活（开发平台与统一运行时基座可独立部署）
- 安全隔离（统一运行时基座不暴露建模工具）

**权威来源**: [Martin Fowler - Language Workbenches](https://martinfowler.com/articles/languageWorkbench.html)

#### 1.3.3 平台即框架 (Platform as Framework)

**理念**: 平台提供扩展点和约定，应用通过扩展而非修改平台代码来实现业务。

**体现**:
- **SPI 扩展点**: 逻辑流节点、通知渠道、字段类型、异常映射器等 40+ 扩展接口
- **资源约定**: `META-INF/ouroboros/` 目录结构约定
- **Auto-Configuration**: `META-INF/spring.factories` 自动装配
- **生命周期钩子**: 元数据刷新事件、应用启动事件

**优势**:
- 可扩展性强（第三方可贡献插件）
- 平台稳定性高（核心代码不被应用修改）
- 升级成本低（应用只依赖扩展接口）

#### 1.3.4 约定优于配置 (Convention over Configuration)

**理念**: 通过约定减少配置量，只在需要时才显式配置。

**体现**:
- 模块发现约定: `META-INF/spring.factories` 自动加载
- 资源路径约定: `META-INF/ouroboros/{type}/*.json`
- 命名约定: `{domain}-core`、`{domain}-runtime`、`{domain}-dev`
- SPI 注册约定: `META-INF/services/{interface-fqn}`

#### 1.3.5 元数据版本演进 (Metadata Versioning)

**理念**: 元数据格式支持版本化，新版本向后兼容旧版本。

**体现**:
- 工作流定义支持 v1/v2 格式（自动检测版本）
- JSON 反序列化使用 `@JsonIgnoreProperties(ignoreUnknown = true)`
- 元数据迁移工具（如工作流 v1 → v2 迁移）

### 1.4 架构目标与非目标

#### 架构目标

1. **元数据驱动**: 通过元数据定义应用，实现"配置即应用"
2. **高可扩展性**: 通过 SPI 和插件机制支持第三方扩展
3. **开发运行分离**: 开发平台与统一运行时基座职责清晰、可独立部署
4. **共享底层基座**: develop 和 mothership 共享核心引擎，确保语义一致
5. **严格分层**: 六层架构，严格单向依赖，防止循环依赖
6. **架构可验证**: 通过 ArchUnit 结构测试和 CI 门禁保护架构约束

#### 架构非目标

1. **不追求微服务**: 采用 monorepo 单体架构，通过模块化实现关注点分离
2. **不追求多语言**: 后端统一 Java 21，前端统一 React + TypeScript
3. **不追求完全无代码**: 复杂业务逻辑仍需通过逻辑流或脚本实现
4. **不追求零配置**: 保留必要的配置灵活性，避免过度约定

---

## 2. 核心概念模型

### 2.1 模型驱动的开发范式

Ouroboros 的核心是**模型驱动开发（Model-Driven Development, MDD）**。应用开发者通过定义元数据来描述应用的各个方面，平台在运行时解释这些元数据并生成可执行的应用。

**类比**: 如果传统开发是"用代码搭建房子"，那么 Ouroboros 是"用图纸（元数据）描述房子，由平台自动建造"。

**核心流程**:

```
[开发者] 定义元数据 (JSON)
    ↓
[开发平台] 管理元数据 (版本控制、验证)
    ↓
[统一运行时基座] 加载元数据 (扫描、解析)
    ↓
[运行时引擎] 执行元数据 (动态渲染、API 生成、流程执行)
    ↓
[最终用户] 使用应用
```

### 2.2 元数据类型体系

Ouroboros 支持 **18 种元数据类型**，按职责分为三类：**平台主模型**（7种）、**能力元数据**（8种）、**开发工具元数据**（3种）。它们协同工作定义一个完整的应用。

#### 2.2.1 平台主模型（7种）

平台主模型定义应用的核心结构，包括数据、业务逻辑、UI、API、菜单、权限和模块组织。

##### 2.2.1.1 数据模型 (Data Model)

**定义**: 运行时数据访问层，描述数据结构、字段定义、主键和插件链。Data Model 是运行时 CRUD 操作的执行单元。

**元数据来源**:
- Business Model 投影生成
- Java 注解扫描（`@Model`）

**核心职责**:
- 提供类型安全的 CRUD 操作
- 支持 QueryDSL 查询
- 支持插件链扩展（审计、软删除、多租户等）
BusinessModel (业务语义层)
    ↓ buildModelBundle()
ModelBundle (投影容器)
    ↓ BasicMPS / PluginFeaturesMPS
DataModelMeta (数据结构元数据)
    ↓ DataModelMetaCenter → SqlDataStationBuilder
DataModel (运行时数据访问实例)
```

**运行时行为**: 
- 通过 `DataModelMetaCenter` 聚合所有 `DataModelMetaProvider`
- `SqlDataStationBuilder` 将 `DataModelMeta` 装配为 `DataModel` 实例
- 提供类型安全的 CRUD 操作和 QueryDSL 查询
- 支持插件链扩展（审计、软删除、多租户等）

**示例（注解方式）**:
```java
@Model(name = "CodingSequence", namespace = "pkgen")
public class CodingSequence {
    @PrimaryKey
    @Field(name = "id")
    private String id;
    
    @Field(name = "currentValue")
    private Long currentValue;
}
```

##### 2.2.1.2 业务模型 (Business Model)

**定义**: 业务语义层，描述业务实体的完整定义，包括字段结构、类型约束、关系映射、工作流配置、API 生成规则和特性开关。Business Model 是多目标投影的源头。

**存储位置**: 
- 模板文件：`META-INF/business-model-meta-templates/default.json`
- 运行时存储：通过开发平台管理

**核心职责**:
- 投影生成 Data Model、UI Schema、WebAPI、Workflow 配置
- 定义业务实体的完整语义
- 配置特性开关（软删除、审计、多租户等）

##### 2.2.1.3 UI 模型 (UI Model)

**定义**: 页面元信息，描述页面路由、类型、标题、权限控制等。UI Model 是前端路由注册的基础。

**存储位置**: `META-INF/ouroboros/ui-model/**/*.json`

**核心职责**:
- 定义页面路由和访问路径
- 配置页面权限和可见性
- 关联 UI Schema 渲染定义

##### 2.2.1.4 UI Schema

**定义**: Amis 渲染定义，描述页面组件树、布局、交互逻辑。UI Schema 是前端页面的完整渲染配置。

**存储位置**: `META-INF/ouroboros/ui-schema/**/*.json`

**核心职责**:
- 定义页面组件结构和布局
- 配置表单验证和交互逻辑
- 支持动态表单和列表渲染

**运行时行为**: 
- 通过 `JarUIModelProvider` 加载
- 注册到 `UIRouteServiceImpl`
- 提供页面发现和路由注册

##### 2.2.1.4 UI Schema (UI 渲染定义)

**定义**: 描述页面的具体渲染内容，包括布局、组件树、交互逻辑和数据绑定（基于 Amis 低代码协议）。

**存储位置**: `META-INF/ouroboros/ui-schema/**/*.json`

**示例**:
```json
{
  "type": "page",
  "body": {
    "type": "crud",
    "api": "/api/employee",
    "columns": [
      {"name": "name", "label": "姓名"},
      {"name": "department.name", "label": "部门"}
    ]
  }
}
```

**运行时行为**: 
- 通过 `JarUISchemaProvider` 加载
- 前端请求 `/api/ui-schema/{path}`
- 后端返回 Amis Schema JSON
- 前端 Amis 引擎动态渲染页面

##### 2.2.1.5 菜单模型 (Menu Model)

**定义**: 应用菜单结构，描述菜单层级、图标、路由关联、权限控制。

**存储位置**: `META-INF/ouroboros/menu-model/**/*.json`

**核心职责**:
- 定义应用导航结构
- 配置菜单权限和可见性
- 关联页面路由

##### 2.2.1.6 权限模型 (Authority Model)

**定义**: 权限资源定义，描述权限点、角色、操作类型。

**存储位置**: `META-INF/ouroboros/authority-model/**/*.json`

**核心职责**:
- 定义权限资源和操作
- 配置角色和权限关系
- 支持细粒度权限控制

##### 2.2.1.7 应用模块模型 (App Module Model)

**定义**: 应用模块注册信息，描述模块标识、版本、依赖关系。

**存储位置**: `META-INF/ouroboros/app-module/**/*.json`

**核心职责**:
- 注册应用模块
- 管理模块依赖关系
- 支持模块热插拔

#### 2.2.2 能力元数据（8种）

能力元数据为平台提供扩展能力，包括 API 生成、配置管理、业务流程、字段类型、组件定义、微前端、错误码和国际化。

##### 2.2.2.1 WebAPI 模型 (WebAPI Model)

**定义**: REST API 自动生成规则，描述端点路由、参数、权限。

**存储位置**: `META-INF/ouroboros/webapi/**/*.json`

**核心职责**:
- 动态注册 REST 端点
- 自动处理参数验证和类型转换
- 自动应用权限控制

##### 2.2.2.2 配置元数据 (Configuration Metadata)

**定义**: 运行时配置项定义，描述配置类型、验证规则、默认值。

**存储位置**: `META-INF/ouroboros/configuration/**/*.json`

**核心职责**:
- 生成配置管理界面
- 提供配置项的读写 API
- 支持配置热更新

##### 2.2.2.3 逻辑流模型 (LogicFlow Model)

**定义**: 节点式业务流程编排，描述节点类型、连接关系、执行逻辑。

**存储位置**: `META-INF/ouroboros/logic-flow-node-define/*.json`

**核心职责**:
- 构建节点图（DAG）
- 编译为异步执行链
- 执行业务流程编排

##### 2.2.2.4 字段类型定义 (Field Types)

**定义**: 业务模型字段类型，描述类型约束、验证规则、UI 渲染。

**存储位置**: `META-INF/ouroboros/field-types/**/*.json`

**核心职责**:
- 提供字段类型的元信息
- 支持自定义字段类型扩展
- 用于表单生成和数据验证

##### 2.2.2.5 组件定义 (Widget Definition)

**定义**: UI 组件配置规范，描述组件属性、事件、渲染规则。

**存储位置**: `META-INF/ouroboros/widgets/**/*.json`

**核心职责**:
- 提供组件的配置 Schema
- 支持自定义组件注册
- 用于可视化编辑器

##### 2.2.2.6 微前端配置 (Micro Web)

**定义**: 微前端应用注册，描述应用入口、路由规则、通信协议。

**存储位置**: `META-INF/ouroboros/micro-web/**/*.json`

**核心职责**:
- 注册微前端应用
- 配置应用间通信
- 管理应用生命周期

##### 2.2.2.7 错误码定义 (Error Code)

**定义**: 业务错误码，描述错误码、消息模板、国际化。

**存储位置**: `META-INF/ouroboros/error-codes/**/*.json`

**核心职责**:
- 提供错误码查询
- 支持错误消息国际化
- 用于统一异常处理

##### 2.2.2.8 国际化资源 (i18n)

**定义**: 多语言翻译资源，描述语言代码、翻译键值对。

**存储位置**: `META-INF/ouroboros/i18n/**/*.json`

**核心职责**:
- 提供多语言翻译
- 支持动态语言切换
- 用于 UI 和消息国际化

#### 2.2.3 开发工具元数据（3种）

开发工具元数据支持开发平台的特定功能，包括开发菜单、通知模板和 RPC 模型。

##### 2.2.3.1 开发菜单模型 (Dev Menu Model)

**定义**: 开发平台菜单结构，描述开发工具导航。

**存储位置**: `META-INF/ouroboros/dev-menu/**/*.json`

**核心职责**:
- 仅在 ouroboros-develop 中加载
- 生成开发平台导航

##### 2.2.3.2 通知模板 (Notification Template)

**定义**: 通知消息模板，描述模板内容、渠道配置。

**存储位置**: `META-INF/ouroboros/notification-templates/**/*.json`

**核心职责**:
- 提供通知模板渲染
- 支持多渠道通知（邮件、短信、站内信）
- 用于业务事件通知

##### 2.2.3.3 RPC 模型 (RPC Model)

**定义**: RPC 接口定义，描述接口签名、调用配置。

**存储位置**: `META-INF/ouroboros/rpc-model/**/*.rpc.json`

**核心职责**:
- 仅在 ouroboros-develop 中加载
- 提供 RPC 接口的元信息
- 支持 RPC 调用的配置和管理

### 2.3 元数据生命周期

元数据从定义到执行经历完整的生命周期，理解这个流程是掌握平台运作机制的关键。

#### 完整生命周期流程

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 定义 (Definition)                                         │
│    开发者编写 JSON 元数据文件                                  │
│    位置: src/main/resources/META-INF/ouroboros/**/*.json     │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. 存储 (Storage) - 双路径元数据源                            │
│    ├─ 打包元数据: META-INF/ouroboros/**/*.json (不可变)       │
│    └─ 运行时元数据: $APP_HOME/**/*.json (可变)                │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 发现 (Discovery)                                          │
│    ├─ SPI 机制: META-INF/services/{Provider接口}             │
│    └─ Auto-Configuration: META-INF/spring.factories          │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. 加载 (Loading)                                            │
│    ├─ 从 JAR 包加载打包元数据                                 │
│    ├─ 从文件系统加载运行时元数据                              │
│    └─ 监听元数据刷新事件                                      │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. 解析 (Parsing)                                            │
│    ├─ JSON 反序列化                                          │
│    ├─ 版本检测和兼容性处理                                    │
│    └─ Schema 验证                                            │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. 聚合 (Aggregation) - Provider + Center 模式               │
│    ├─ 通过 SPI 发现所有 Provider                             │
│    ├─ Center 聚合所有 Provider                               │
│    └─ 提供统一访问接口                                        │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 7. 执行 (Execution)                                          │
│    ├─ UI: 动态渲染页面                                        │
│    ├─ API: 动态注册端点                                       │
│    ├─ LogicFlow: 执行业务流程                                │
│    ├─ Workflow: 部署工作流                                    │
│    └─ Notification: 分发通知                                 │
└─────────────────────────────────────────────────────────────┘
```

#### 关键设计模式

**Provider + Center 聚合模式**:
- **Provider**: 通过 SPI 注册，负责发现元数据来源（Jar 包、文件系统等）
- **Center**: 聚合所有 Provider，提供统一访问接口，支持运行时刷新

### 2.4 开发平台与统一运行时基座

Ouroboros 采用"双入口架构"，将开发时和运行时职责清晰分离。

#### 2.4.1 开发平台 (ouroboros-develop)

**定位**: 设计时平台，提供建模工具和运行时管理。

**核心职责**:
- 元数据管理（创建、编辑、删除、版本控制）
- 可视化建模工具（业务模型设计器、UI Schema 编辑器、逻辑流编辑器）
- 运行时环境管理（容器、数据源、网关、K8s 集成）
- 开发辅助工具（VCS 集成、后端 API 隧道会话）

**专属依赖**:
- `dev-module-all`: 开发管理模块聚合包
- `ouroboros-dev-app-management`: 应用管理服务
- `ouroboros-dev-container-docker`: 容器管理
- `ouroboros-dev-runtime-kubernetes`: K8s 运行时
- `ouroboros-dev-version-control-system`: VCS 集成
- `ouroboros-dev-datasource`: 数据源管理

**配置特征**: `application-debug.yml` 包含 `app-root/web-root/workspace/docker-api` 等开发运行参数

**独有功能**:
- 运行时管理页 `/container-management`
- 镜像管理、创建/生命周期操作
- 后端 API 隧道会话（用于调试）

#### 2.4.2 统一运行时基座 (ouroboros-mothership)

**定位**: 统一运行时基座，加载应用元数据并执行业务逻辑。

**核心职责**:
- 加载应用元数据（从 `$APP_HOME/` 读取）
- 解析和执行元数据（UI 渲染、API 路由、逻辑流执行）
- 提供业务服务（不包含建模工具）
- 对外服务（面向最终用户）

**专属依赖**:
- `websocket`: WebSocket 支持
- `H2`: 内嵌数据库（可选）
- `janino`: 运行时动态编译

**配置特征**: `application.yml` 几乎清空，依赖外部配置（强调运行时外部化配置）

**独有功能**:
- 优先验证 `app.*` 路由
- 生成应用真实运行态
- metadata/extend/runtime 权限装配

#### 2.4.3 "薄入口、厚装配"实现方式

两个平台的入口类都非常薄（仅 `@SpringBootApplication + SpringApplication.run`），差异来自"装配到 classpath 的模块 + 自动配置"：

```java
// ouroboros-develop 和 ouroboros-mothership 的入口类几乎相同
@SpringBootApplication
public class OuroborosDevelopApplication {
    public static void main(String[] args) {
        SpringApplication.run(OuroborosDevelopApplication.class, args);
    }
}
```

**差异化装配**:
- develop: 通过 Maven 依赖引入 `dev-module-all`，触发开发态 Auto-Configuration
- mothership: 不引入 `dev-module-all`，只引入运行时必需模块

### 2.5 共享底层基座

develop 和 mothership 共享以下层级：

**共享层**:
- **libs**: 工具类和公共库（ouroboros-util、common-*）
- **core**: 低代码核心引擎（ouroboros-data、ouroboros-ui-model、ouroboros-logicflow 等）
- **security**: 安全框架（ouroboros-web-security）
- **ability**: 业务能力服务（ouroboros-configuration、ouroboros-notification 等）

**设计意图**:
1. **代码复用**: 避免重复实现相同的核心逻辑
2. **语义一致性**: 确保"开发时定义的模型"和"运行时执行的模型"使用完全相同的核心引擎，消除模型语义偏差
3. **维护效率**: 核心引擎的 bug 修复和功能增强自动惠及两个平台

**约束**:
- 共享层不能依赖上层（develop/mothership 特有模块）
- 共享层的 API 变更需要同时考虑两个平台的影响

---

## 3. 分层架构

### 3.1 分层模型

Ouroboros 采用严格的六层分层架构，每层有明确的职责边界和依赖方向：

```
┌─────────────────────────────────────────────────────────────┐
│  Platform Layer (platform)                                   │
│  可运行应用: ouroboros-develop / ouroboros-mothership        │
│  职责: 聚合下层模块，提供完整的开发平台或统一运行时基座        │
├─────────────────────────────────────────────────────────────┤
│  App/Dev Layer (app-module, dev-module)                     │
│  应用模块和开发工具                                           │
│  职责: 面向最终用户的业务应用 / 面向开发者的管理工具           │
├─────────────────────────────────────────────────────────────┤
│  Ability Layer (ability)                                     │
│  业务能力服务                                                 │
│  职责: 通知、WebSocket、消息队列、调度、文件上传、组织架构等   │
├─────────────────────────────────────────────────────────────┤
│  Security Layer (security)                                   │
│  安全和访问控制                                               │
│  职责: 认证、授权、RBAC、安全注解                             │
├─────────────────────────────────────────────────────────────┤
│  Core Layer (core)                                           │
│  低代码核心引擎                                               │
│  职责: 数据模型、UI 模型、WebAPI 模型、逻辑流、工作流、字段类型 │
├─────────────────────────────────────────────────────────────┤
│  Foundation Layer (libs, bom)                                │
│  基础设施和工具                                               │
│  职责: 纯工具类、异常体系、Web 响应封装、缓存、模板、脚本执行  │
└─────────────────────────────────────────────────────────────┘
```

**设计意图**:
- **Foundation**: 基础设施和跨层共享契约，无具体业务实现，可被任何层使用
- **Core**: 低代码核心引擎，定义元数据模型和运行时抽象
- **Security**: 安全框架，提供认证授权能力
- **Ability**: 业务能力服务，基于 Core 层实现具体功能
- **App/Dev**: 应用模块和开发工具，消费 Ability 层能力
- **Platform**: 可运行的平台应用，聚合下层模块

### 3.2 层间依赖规则

**严格单向依赖**:
```
libs ← core ← security ← ability ← app-module/dev-module ← platform
```

**规则**:
1. 每层只能依赖其下层
2. 同层内模块可以相互依赖（但应避免循环）
3. 禁止向上依赖（例如 core 不能依赖 ability）；允许使用更底层的公共合同，包族拆分与登记例外见[现行约束](docs/architecture-constraints.md)
4. 禁止向上依赖

**架构守护**：两套 ArchUnit 入口共用模块角色模型，覆盖所有 reactor 生产类型；Foundation 禁止向外依赖，ADR-004 的 Bridge 投影带已纳入约束。物理目录与语义角色的精确映射及 allowlist 见[架构约束](docs/architecture-constraints.md#12-依赖方向规则-p0-3)。CI 是否执行取决于触发路径和开关，见[CI/CD](docs/rules/ci-cd.md)。

### 3.3 各层职责详解

#### 3.3.1 Foundation Layer (libs, bom)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**关键模块**:
- `ouroboros-util`: 纯工具类（Functions, Maps, Lists, Json, Dates, Paths, SPI）
- `ouroboros-util-excel`: Excel 读写
- `ouroboros-util-zip`: ZIP 文件操作
- `common-exception`: 异常体系
- `common-web`: Web API 响应封装
- `common-cache`: 缓存抽象
- `common-template`: FreeMarker 模板引擎
- `common-script`: JavaScript/Groovy 脚本执行
- `common-expression`: SpEL 表达式求值

**设计约束**: 不得依赖任何上层模块，保持纯工具性质

#### 3.3.2 Core Layer (core)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**关键模块**:
- `ouroboros-data`: 动态数据模型引擎（QueryDSL、多数据源、主键生成）
- `ouroboros-field-type`: 字段类型定义和扩展
- `ouroboros-ui-model`: UI 组件和 Schema 系统（MicroWeb、LowCode）
- `ouroboros-webapi-model`: REST API 动态生成
- `ouroboros-menu-model`: 菜单和导航结构
- `ouroboros-logicflow`: 节点式业务流程执行引擎
- `ouroboros-approval-workflow`: 以 Flowable 为当前主线的审批工作流；Activiti6 已移到 legacy-compat
- `ouroboros-resource`: 资源元数据管理
- `ouroboros-i18n`: 国际化支持

**设计约束**: 只能依赖 Foundation 层，不能依赖 Security/Ability 层

#### 3.3.3 Security Layer (security)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**关键模块**:
- `ouroboros-web-security-annotation`: 安全注解定义
- `ouroboros-web-security-core`: 核心安全抽象
- `ouroboros-web-security-spring-adapter`: Spring Security 适配
- `ouroboros-web-security-rbac-manage`: RBAC 管理
- `ouroboros-web-security-runtime`: 运行时实现

**设计约束**: 只能依赖 Core 和 Foundation 层

#### 3.3.4 Ability Layer (ability)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**关键模块**:
- `ouroboros-configuration`: 统一配置管理
- `ouroboros-notification`: 多渠道通知系统
- `ouroboros-websocket`: WebSocket 支持（可插拔 Broker）
- `ouroboros-scheduler`: 定时任务管理（Quartz）
- `ouroboros-message-queue`: 消息队列抽象（RabbitMQ）
- `ouroboros-file-upload`: 文件上传处理
- `ouroboros-organization-structure`: 组织架构和用户管理
- `ouroboros-data-dict`: 数据字典
- `ouroboros-ai`: AI 能力集成（OpenAI、Anthropic、RAG、MCP）

**设计约束**: 只能依赖 Security、Core 和 Foundation 层

#### 3.3.5 App/Dev Layer (app-module, dev-module)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**app-module 关键模块**:
- `dynamic-form`: 动态表单生成
- `ui-schema-management`: UI Schema 管理界面
- `document-management`: 文档管理系统

**dev-module 关键模块**:
- `ouroboros-dev-app-management`: 应用开发管理
- `ouroboros-dev-container`: 容器管理（Docker）
- `ouroboros-dev-runtime`: 运行时管理（Docker、Kubernetes、API Gateway）
- `ouroboros-dev-version-control-system`: VCS 集成
- `ouroboros-dev-model-manage-webapi-scaffold`: 模型管理 WebAPI 脚手架
- `ouroboros-dev-datasource`: 数据源管理
- `ouroboros-dev-environment`: 开发环境配置

**设计约束**: 只能依赖 Ability、Security、Core 和 Foundation 层

#### 3.3.6 Platform Layer (platform)

**模块边界**：以对应聚合 POM 的当前 modules 为准，不使用旧统计作为实时规模。

**关键模块**:
- `ouroboros-base`: 基础 Docker 镜像
- `ouroboros-develop`: 开发平台应用
- `ouroboros-mothership`: 统一运行时基座

**设计约束**: 可以依赖任何下层模块，但自身不应包含业务逻辑


---

## 4. 关键设计模式与机制

### 4.1 元数据驱动模式

**核心思想**: Schema 定义 → 运行时解释执行

**实现机制**:

1. **Schema 定义**: 开发者通过 JSON 定义元数据
2. **Schema 验证**: 加载时进行格式和语义验证
3. **Schema 编译**: 将声明式 Schema 编译为可执行结构（可选）
4. **运行时解释**: 根据 Schema 动态生成行为

**示例 - UI 渲染**:
```
[UI Schema JSON] 
    → UISchemaController 
    → 返回 Amis Schema 
    → 前端 Amis 引擎渲染
```

**示例 - 逻辑流执行**:
```
[LogicFlow JSON] 
    → NodeFactory 构建节点图 
    → FunctionalFlowCompiler 编译为 CompletableFuture 链 
    → DefaultFlowModel.execute() 执行
```

### 4.2 插件化扩展模式

Ouroboros 通过三种机制实现插件化扩展：

#### 4.2.1 Java SPI 扩展

**机制**: 通过 `META-INF/services/{接口全限定名}` 注册实现类

**扩展点分类**（40+ SPI 接口）:

| 类别 | 用途 |
|------|------|
| 逻辑流扩展 | 自定义逻辑流节点 |
| 通知渠道 | 自定义通知渠道（邮件、短信、微信等） |
| 异常映射 | 自定义异常到 HTTP 响应的映射 |
| 数据模型扩展 | 自定义查询转译、值类型、主键生成策略 |
| 业务模型特性 | 自定义业务模型特性（归档、SCD2 历史等） |
| RPC 模型 | 自定义 RPC 模型构建器 |

**扩展流程**:
1. 实现 SPI 接口
2. 在 `META-INF/services/` 注册实现类
3. 定义节点元数据（如果需要）

#### 4.2.2 Spring Boot Auto-Configuration

**机制**: 通过 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 发现自动配置，同时保留 `META-INF/spring.factories` 兼容声明；见[自动配置注册约定](docs/design/cross-cutting/spring-autoconfiguration.md)

**用途**: 自动装配模块的 Spring Bean 和配置

#### 4.2.3 资源约定

**机制**: 通过约定的目录结构自动发现资源

**约定目录**: `META-INF/ouroboros/{type}/*.json`

**支持的资源类型**:
- `business-model/`: 业务模型定义
- `ui-model/`: UI 元信息
- `ui-schema/`: UI Schema
- `webapi/`: WebAPI 模型
- `menu-model/`: 菜单模型
- `authority/`: 权限模型
- `configurations.json`: 配置元数据
- `logic-flow-node-define/`: 逻辑流节点定义
- `notification-template/`: 通知模板
- `field-types/`: 字段类型定义
- `app-modules.json`: 应用模块清单

### 4.3 开发时/运行时分离模式

每个核心模块严格划分为三层：

**命名模式**:
- `{domain}-core`: 纯抽象和接口，不依赖 Spring
- `{domain}-runtime`: 运行时实现，依赖 Spring，提供 Auto-Configuration
- `{domain}-dev`: 开发时支持，提供元数据管理界面和工具

**职责划分**:
- **core**: 定义领域模型和接口契约
- **runtime**: 实现运行时加载、解析、执行逻辑
- **dev**: 提供元数据管理、可视化编辑、调试工具
- **core**: 定义模型接口和核心逻辑，可被 runtime 和 dev 共享
- **runtime**: 实现运行时加载和执行，被 mothership 使用
- **dev**: 实现开发时管理和编辑，被 develop 使用

### 4.4 模块发现与加载机制

**Provider + Center 聚合模式**:

```
┌─────────────────────────────────────────────────────────────┐
│ Provider 层 (通过 SPI 注册)                                   │
│ ├─ JarUIModelProvider        (从 Jar 包加载)                 │
│ ├─ FileUIModelProvider       (从文件系统加载)                 │
│ └─ DatabaseUIModelProvider   (从数据库加载，可选)             │
└─────────────────────────────────────────────────────────────┘
                            ↓ SPI.getSortedServiceStream()
┌─────────────────────────────────────────────────────────────┐
│ Center 层 (聚合所有 Provider)                                 │
│ UIRouteServiceImpl                                           │
│ ├─ 聚合所有 Provider 的元数据                                 │
│ ├─ 提供统一访问接口                                           │
│ └─ 支持运行时刷新 (refresh())                                 │
└─────────────────────────────────────────────────────────────┘
                            ↓
┌─────────────────────────────────────────────────────────────┐
│ 消费层 (Controller / Service)                                │
│ UISchemaController                                           │
│ └─ 调用 Center 获取元数据并返回给前端                          │
└─────────────────────────────────────────────────────────────┘
```

**关键类**:
- `SPI.java`: 全局 SPI 加载器（优先级 + 缓存）
- `ResourceUtil.java`: 资源扫描工具（classLoader.getResources + 目录扫描）

### 4.5 横切关注点接入方式

#### 4.5.1 日志

**机制**: SLF4J + Logback

**约定**:
- 结构化日志格式（JSON）
- 敏感信息脱敏（密码、Token）
- 按模块分包配置日志级别

#### 4.5.2 监控和追踪

**机制**: Spring Boot Actuator + Micrometer

**暴露端点**:
- `/actuator/health`: 健康检查
- `/actuator/metrics`: 指标采集
- `/actuator/info`: 应用信息

#### 4.5.3 配置管理

**机制**: 统一通过 `ouroboros-configuration` 模块

**特性**:
- 支持多环境配置
- 配置热更新
- 配置元数据驱动（自动生成配置管理界面）

#### 4.5.4 安全

**机制**: 统一通过 `ouroboros-web-security` 模块

**特性**:
- 基于注解的权限控制
- RBAC 模型
- 与元数据权限模型集成

#### 4.5.5 异常处理

**机制**: 统一异常体系（`common-exception`）

**异常层次**:
- `BusinessException`: 业务异常（用户可理解）
- `ValidationException`: 验证异常
- `InternalException`: 内部异常（系统错误）
- `PlatformException`: 平台异常

**异常映射**: 通过 `WebApiResultExceptionMapper` SPI 自定义异常到 HTTP 响应的映射

---

## 5. 模块命名约定与治理

### 5.1 命名模式

#### 5.1.1 模块命名约定

每个功能域的模块应遵循以下命名模式：

- `{domain}-core`: 核心抽象和接口
- `{domain}-runtime`: 运行时实现
- `{domain}-dev`: 开发时支持
- `{domain}-test-support`: 测试支持
- `{domain}-{adapter}`: 特定技术适配器（如 `spring-adapter`）

**示例**:
- `ouroboros-data-core`
- `ouroboros-data-runtime`
- `ouroboros-data-typed-runtime`
- `ouroboros-data-sql`

#### 5.1.2 包命名约定

- 基础包: `com.ouroboros.{domain}`
- 核心包: `com.ouroboros.{domain}.core`
- 运行时包: `com.ouroboros.{domain}.runtime`
- 开发时包: `com.ouroboros.{domain}.dev`

#### 5.1.3 资源路径约定

- 元数据: `META-INF/ouroboros/{type}/*.json`
- SPI 注册: `META-INF/services/{interface-fqn}`
- Auto-Configuration: `META-INF/spring.factories`

### 5.2 架构守护规则

#### 5.2.1 结构测试 (ArchUnit)

**目的**: 通过自动化测试强制执行架构约束

**示例**:
```java
@Test
public void layerDependencyRulesShouldBeRespected() {
    var commonDevModule = resideInModule("libs/common/common-dev");
    layeredArchitecture()
        .layer("Foundation").definedBy(
            resideInAnyPackage("com.ouroboros.util..", "com.ouroboros.common..")
                .or(commonDevModule))
        .layer("Core").definedBy("com.ouroboros.data..", "com.ouroboros.logicflow..")
        .layer("Security").definedBy("com.ouroboros.security..")
        .layer("Ability").definedBy("com.ouroboros.ability..")
        .layer("Dev").definedBy(
            resideInAnyPackage("com.ouroboros.dev..").and(not(commonDevModule)))
        .whereLayer("Core").mayOnlyAccessLayers("Foundation")
        .whereLayer("Security").mayOnlyAccessLayers("Core", "Foundation")
        .whereLayer("Ability").mayOnlyAccessLayers("Security", "Core", "Foundation");
}
```

**检查项**:
- 层间依赖方向
- 循环依赖检测
- 命名约定检查
- 包结构检查

#### 5.2.2 CI 门禁

**机制**: 在 CI 流水线中运行结构测试

**规则**:
- 新增违规导致构建失败
- 定期生成依赖关系报告

#### 5.2.3 Ratchet 策略

**目的**: 对存量架构违规采用"只减不增"策略

**机制**:
1. 扫描存量违规，记录到 allowlist
2. 新代码必须通过架构检查
3. allowlist 中的违规不报错
4. 渐进式修复：每两周尝试从 allowlist 移除一条
5. allowlist 只能减少，不能增加新的违规

**跟踪**: 详见 [技术债跟踪器](docs/tech-debt-tracker.md)

---

## 6. 架构决策摘要

以下是最关键的架构决策及其理由（详细 ADR 见 [docs/adr/](docs/adr)）：

### 设计理由 001： 为什么选择 JSON 元数据而非注解驱动？

**决策**: 使用 JSON 文件作为元数据格式，而非 Java 注解或 YAML。

**理由**:
1. **前后端通用**: JSON 可被前端直接解析，支持前端可视化编辑器
2. **可读性**: JSON 比注解更易读，比 YAML 更严格（无缩进歧义）
3. **版本控制友好**: JSON 文件易于 diff 和 merge
4. **工具支持**: JSON Schema 提供强大的验证和 IDE 支持
5. **动态性**: JSON 可在运行时修改，注解需要重新编译

**权衡**: JSON 缺少类型安全，需要运行时验证

### 设计理由 002： 为什么 develop 和 mothership 分离？

**决策**: 将开发平台和统一运行时基座拆分为两个独立的可运行应用。

**理由**:
1. **职责清晰**: 开发时工具与运行时执行完全分离
2. **部署灵活**: 开发平台可部署在内网，统一运行时基座可部署在生产环境
3. **安全隔离**: 统一运行时基座不暴露建模工具，减少攻击面
4. **资源优化**: 生产环境无需加载开发时依赖

**权衡**: 需要维护两个应用的配置和部署

### 设计理由 003： 为什么采用六层分层架构？

**决策**: 采用 Foundation → Core → Security → Ability → App/Dev → Platform 六层架构。

**理由**:
1. **关注点分离**: 每层有明确的职责边界
2. **依赖管理**: 严格单向依赖，防止循环依赖
3. **可测试性**: 下层可独立测试，不依赖上层
4. **可扩展性**: 新功能优先在 Ability 层扩展，不污染 Core 层

**权衡**: 层次较多，增加了模块数量

### 设计理由 004： 为什么选择 SPI 而非 Spring 原生扩展？

**决策**: 在某些场景下使用 Java SPI 而非 Spring @Bean 扩展。

**理由**:
1. **框架无关**: SPI 不依赖 Spring，可在非 Spring 环境使用
2. **优先级控制**: SPI 支持 `@Priority` 注解控制加载顺序
3. **延迟加载**: SPI 支持按需加载，避免启动时加载所有实现
4. **标准化**: Java SPI 是 JDK 标准机制

**权衡**: SPI 不支持依赖注入，需要手动获取 Spring Bean

### 设计理由 005： 为什么开发态采用文件驱动而非数据库？

**决策**: 开发态元数据存储在文件系统中，而非数据库。

**理由**:
1. **版本控制**: 文件系统配合 Git 天然支持版本控制
2. **可读性**: JSON 文件易于阅读和手动编辑
3. **可移植性**: 文件系统便于导入导出和迁移
4. **开发体验**: 文件变更可直接触发重新加载，无需数据库迁移
5. **协作友好**: Git 提供强大的协作、合并和冲突解决能力

**权衡**: 文件系统不支持复杂查询，需要加载到内存后查询

**详细说明**: 见 [docs/guides/development-module-guide.md](docs/guides/development-module-guide.md)

### 设计理由 006： Java 8 兼容性决策（已废弃）

**当前状态**: Spring Boot 3.5 主线已收口到 Java 21 编译基线，默认运行镜像为 JDK 21；Java 8/Jabel 兼容构建不再是当前主线事实。

**历史背景**: 旧主线曾使用 Java 8 作为编译目标，并通过 jabel 插件支持部分 Java 11+ 语法糖。

**迁移后约束**: 新代码按 Java 21 与 Spring Boot 3.5.11 编写；如需维护遗留兼容路径，必须显式说明范围和验证命令。

---

## 7. 参考资料

### 7.1 内部文档

- **设计文档**: [docs/design/](docs/design)
- **ADR 决策记录**: [docs/adr/](docs/adr)
- **开发模块模式**: [docs/guides/development-module-guide.md](docs/guides/development-module-guide.md)
- **测试规范**: [docs/testing.md](docs/rules/testing.md)
- **前端架构**: [frontends/web/AGENTS.md](frontends/web/AGENTS.md)
- **技术债跟踪器**: [docs/tech-debt-tracker.md](docs/tech-debt-tracker.md)
- **架构违规跟踪器**: [docs/tech-debt-tracker.md](docs/tech-debt-tracker.md)

### 7.2 外部参考

**架构模式**:
- [Martin Fowler - DSL Guide](https://martinfowler.com/dsl.html) - 低代码平台的 DSL 设计模式
- [Martin Fowler - Language Workbenches](https://martinfowler.com/articles/languageWorkbench.html) - 模型驱动开发的核心理念
- [Martin Fowler - Infrastructure As Code](https://martinfowler.com/bliki/InfrastructureAsCode.html) - 配置优于编码的哲学

**架构文档**:
- [arc42 - Architecture Documentation](https://arc42.org/) - 架构文档最佳实践
- [C4 Model](https://c4model.com/) - 架构可视化方法

**设计模式**:
- [Clean Architecture](https://blog.cleancoder.com/uncle-bob/2012/08/13/the-clean-architecture.html)
- [Hexagonal Architecture](https://alistair.cockburn.us/hexagonal-architecture/)

**工具**:
- [ArchUnit](https://www.archunit.org/) - 架构测试工具
- [Maven Multi-Module Best Practices](https://maven.apache.org/guides/mini/guide-multiple-modules.html)

### 7.3 技术栈文档

| 技术 | 版本 | 官方文档 |
|------|------|---------|
| Java | 21 | https://docs.oracle.com/en/java/javase/21/docs/ |
| Spring Boot | 3.5.11 | https://docs.spring.io/spring-boot/3.5/reference/ |
| QueryDSL | 5.1.0（以 bom/pom.xml 为准） | https://javadoc.io/doc/com.querydsl/querydsl-core/5.1.0 |
| Flowable | 6.8.1 | https://www.flowable.com/open-source/docs/bpmn/ch01-Introduction |
| Liquibase | 4.28.0 | https://docs.liquibase.com/ |
| React | 18.3.1 | https://react.dev/ |
| qiankun | 2.10.16 | https://qiankun.umijs.org/ |
| Amis | - | https://aisuda.bce.baidu.com/amis/ |

---

**文档版本**: 2.0  
**最后更新**: 2026-04-13  
**维护者**: Ouroboros Architecture Team  
**反馈**: 如发现文档问题或有改进建议，请提交 Issue 或 Pull Request
