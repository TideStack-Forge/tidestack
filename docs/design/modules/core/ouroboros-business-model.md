---
doc_type: module-design
module: ouroboros-business-model
status: current
updated: 2026-09-05
roadmap: statistics-analysis-evolution
---

# ouroboros-business-model 模块设计摘要

## 模块定位

`ouroboros-business-model` 是业务模型定义与投影管线的承载模块：把数据模型之上的业务语义（插件扩展、模型类型、编辑器 schema、运行时投影）组织成一套可复用的定义链路。对维护者来说，它是理解"业务模型如何从定义走到运行时"的入口。

## 责任边界

该模块负责业务模型的元数据定义、插件与投影机制、模型类型词表与编辑器 schema 贡献，不负责统计分析、仪表板或复合视图的运行时执行本身（那些在 `core/ouroboros-statistics-reporting` 与各自 runtime 中）。业务模型的类型扩展应收敛到统一的 `modelType` 词表，而不是另立平行体系。

## 依赖关系

从聚合 `pom.xml` 看，`ouroboros-business-model` 直接依赖 `ouroboros-data-core`、`ouroboros-web-security-core`、`ouroboros-logicflow-core`、`ouroboros-ui-model-schema`、`ouroboros-webapi-model-core`、`ouroboros-menu-model-core` 与 `common-all`。这意味着它本身不是孤立引擎，而是一个把多个核心协议组合为统一业务模型投影面的协调层。

## 关键抽象

- `BusinessModel`：业务模型核心接口，是维护者理解整个模块的第一抽象。
- `ModelBundle` 与 `ModelBundleProjector`：把业务模型投影到不同目标模型的核心容器与投影器。
- `ModelProcessStep`：按步骤组织投影和处理链路的扩展点。
- 字段与元数据体系：如 `BusinessModelFieldMeta` 等概念，用于承接业务模型字段和约束信息。

## 关键子模块

- `ouroboros-business-model-core`：业务模型核心协议与抽象。
- `ouroboros-business-model-default`：默认实现与内置投影逻辑。
- `ouroboros-business-model-runtime`：运行时集成。
- `ouroboros-business-model-dev`：开发管理侧支持。
- `ouroboros-business-model-util`：围绕业务模型处理的辅助能力。

## 扩展机制

本模块的扩展点主要落在投影步骤、SPI 构建器和默认实现替换上，但这些机制同时也出现在其他核心模块。共享机制的统一规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 插件系统与模型类型过滤

业务模型插件系统由 `BusinessModelFeaturePlugin`、`BusinessModelFeaturePluginRegistry`、`PluginProjector` 与 `PluginEditorSchemaContributor` 共同组成。当前主干实现的关键事实如下：

| 位置 | 当前行为 | 演进要求 |
|---|---|---|
| `PluginFeaturesMPS` | 从 `BusinessModelFeaturePluginRegistry.getInstance().getAllPlugins()` 取全量插件，再逐个调用 `PluginProjector.project(...)`；插件内部依赖 `meta.extraProps` 判断自身是否应生效。 | 不再把“所有插件都被调度”作为长期契约；新增类型适用性过滤入口后，投影链路只调度适用于当前业务模型类型的插件。 |
| `BusinessModelEditorUISchemaProvider` | 先读取独立 SPI `PluginEditorSchemaContributor`，再从 `getAllPlugins()` 收集 `BusinessModelFeaturePlugin.getEditorContributor()`；两路 contributor 去重后统一合并。 | UI schema contributor 也必须进入同一模型类型过滤语义，避免编辑器显示与投影结果不一致。 |
| `BusinessModelFeaturePluginRegistry.getEnabledPlugins(meta)` | 根据 `enabledPlugins` 或 `plugins.enabled` 解析启用插件，并补齐 required plugins；当前无生产调用方承担主链过滤。 | 去留、委托或删除延后到 roadmap item `business-model-plugin-projector-modeltype`，本阶段只记录事实和迁移边界。 |

### PluginProjector v2 契约

统计分析链路需要让 dashboard、composite-view 等业务模型类型复用业务模型扩展机制，而不是复制一套平行插件系统。后续 `PluginProjector` v2 应保留“插件负责投影逻辑”的主职责，同时把模型类型适用性声明提升为 Registry 可过滤的显式契约：

- 新增模型类型常量入口 `BusinessModelTypes`，至少包含 `DEFAULT`、`DASHBOARD`、`COMPOSITE_VIEW` 与 `ALL`；字符串值必须与 `BusinessModelMeta.modelType` 的正式值一致。
- `BusinessModelFeaturePlugin` 或其 metadata 增加“适用模型类型”声明，默认值不应无条件扩大到全部类型；内置插件必须显式声明自己支持的业务模型类型。
- `BusinessModelFeaturePluginRegistry` 增加 `getPluginsForModelType(String modelType)`，按模型类型返回适用插件，保持 SPI 加载顺序与现有去重规则。
- `PluginProjector` v2 签名替换为 `void project(BusinessModelMeta meta, ModelProjectionPipelineContext<?> context)`，删除当前三参签名中的 `DataModelMeta` 参数，让 projector 通过投影上下文按目标类型和 `meta.getFullName()` 取得需要修改的模型。
- `PluginProjector` 移除 `@FunctionalInterface`，为后续按模型类型、目标 bundle 或多投影目标扩展方法留下正式接口空间。
- `PluginFeaturesMPS` 与内置 `Scd2HistoryFeaturePlugin`、`ArchiveDeleteFeaturePlugin` 必须随接口迁移一次同步修改；不能保留旧三参 projector 的 bridge、default method 或双签名兼容层。
- `PluginProjector` 的声明范围不得宽于插件声明范围；如果插件只声明适用于 `DEFAULT`，其 projector 不得在 `DASHBOARD` 或 `COMPOSITE_VIEW` 链路被调度。

### 两层过滤语义

模型类型过滤和 feature 开关过滤是两层不同语义，不能合并：

- L1 类型适用性：Registry 根据 `BusinessModelMeta.modelType` 或编辑器上下文 `modelType` 选择可参与当前模型类型的插件；这是“插件有没有资格被调度”的结构性过滤。
- L2 feature enablement：插件被调度后，仍可读取业务模型 `extraProps` 中的配置开关、字段配置或插件私有参数，决定是否实际投影内容；这是“本模型是否启用该能力”的实例级过滤。
- 存量插件当前依赖 `extraProps` 自门控。迁移到 `getPluginsForModelType` 时，不能把未配置 `enabledPlugins` 的模型直接视为禁用所有插件，否则已有插件会静默停止投影。
- `getEnabledPlugins(meta)` 的语义与 L2 开关相关，但它目前没有生产调用方；后续实现应先引入 L1，再单独评估它是删除、委托到 L1+依赖补齐，还是继续保留为显式启用列表工具。

### 迁移约束

- 不提供 bridge/default compatibility method；正式接口迁移一次完成，调用方同步改到 `getPluginsForModelType(String modelType)`。
- `DataModelMeta` 只能作为特定投影目标在 projector 内部按上下文读取，不能继续作为 `PluginProjector` 接口参数暴露；否则 dashboard / composite-view 会被默认数据模型形态绑死。
- 模型 bundle 与投影上下文查找 key 必须继续使用 `getFullName()`，不能降级为 `getName()`；`getName()` 只适合展示或局部短名，不足以区分模块命名空间。
- `PluginEditorSchemaContributor` 目前既可独立 SPI 注册，也可由 `BusinessModelFeaturePlugin.getEditorContributor()` 暴露。后续必须收敛到同一类型过滤策略：要么独立 SPI contributor 也声明支持的模型类型，要么只允许通过 feature plugin 进入编辑器合并链路。
- UI 与投影链路的过滤输入必须同源：投影链路使用 `BusinessModelMeta.modelType`，编辑器链路使用上下文 `modelType`，两者都要落到同一 `BusinessModelTypes` 词表。
- `ALL` 只用于真正跨模型类型的基础插件，不得作为未声明插件的隐式默认值。


## Composite View 业务模型类型

`composite-view` 是统计分析与业务明细查询之间的复合业务视图类型，定义真相源同样放在业务模型定义管线中。它通过标准业务模型扩展点落地：

- `CompositeViewBusinessModelMeta` 描述 source、columns、filters、actions 和 layout。
- `CompositeViewBusinessModel` 负责校验数据源声明、列配置、过滤器和操作按钮绑定。
- `BusinessModelEditorUISchemaProvider` 为 `modelType=composite-view` 生成编辑 schema，覆盖数据集选择、列配置、过滤器配置与操作按钮绑定。
- 运行时执行入口是 `POST /api/runtime/composite-view/{viewName}/execute`，只读查询必须继续走统计分析 Dataset 执行流水线，不得绕过权限注入直接调用 SQL executor。

composite-view 与 dashboard 一样，依赖 `PluginProjector` v2 的模型类型过滤语义；适用插件必须通过 `BusinessModelTypes.COMPOSITE_VIEW` 或明确的跨类型声明进入调度。

## Dashboard 业务模型类型

`dashboard` 是仪表板的正式业务模型类型，定义真相源放在业务模型定义管线中。它通过标准业务模型扩展点落地：

- `DashboardBusinessModelMeta` 描述 layout 和 widgets。
- `DashboardBusinessModel` 负责构建、校验和对外暴露 dashboard meta。
- `DashboardModelValidator` 只允许 widget 使用 `chart`，并校验位置、尺寸、画布边界和 widget 重叠。
- `DashboardEditorSchemaProvider` 通过 `EditorSchemaProvider` SPI 暴露 Dashboard.tsx 编辑面，`chartName` 使用 select，选项来自 `/api/dev/${appName}/chart/options`。

仪表板保存为 `modelType=dashboard` 的业务模型定义，随应用统一发布。运行时通过
`BusinessModelCenter` 直接读取 `DashboardBusinessModel`，再执行其中引用的图表；统计分析
模块不维护第二套仪表板定义、迁移入口或独立发布状态。

全局筛选不是当前 Dashboard 元数据或运行时合同。后续如实现响应式数据域和 widget 字段映射，应先补齐请求域、参数绑定、重新执行和运行时验证，再作为独立设计变更引入。

## 深入文档索引

- `推荐阅读`：[core/ouroboros-business-model/DESIGN.md](../../../../core/ouroboros-business-model/DESIGN.md)（含插件两层过滤、dashboard 与 composite-view 类型契约）
- `业务行为实现`：[DESIGN.md §8](../../../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)；[开发指南](../../../guides/business-model-behaviors.md)；[运行手册](../../../runbooks/business-model-behaviors.md)
- `设计依据`：[业务模型 Action / Event：面向本体的动态行为建模](../../../../core/ouroboros-business-model/docs/business-model-actions-and-events.md)（动态定义优先，完整本体能力仍按后续路线演进）
- `补充深度资料`：[core/ouroboros-statistics-reporting 模块内权威设计](../../../../core/ouroboros-statistics-reporting/docs/module-design.md)（dashboard / composite-view 的运行时事实）
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

业务行为 Action / Event 的动态定义、输入与执行机制见 [DESIGN.md §8](../../../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)，稳定约束与验收要求见[业务行为契约](../../../../core/ouroboros-business-model/docs/business-model-behavior-contracts.md)。插件模型类型过滤（`BusinessModelTypes` + `getPluginsForModelType`）与 `PluginProjector` 两参契约已落地（见 DESIGN.md §6），`getEnabledPlugins` 目前无生产调用方，其去留待评估；dashboard 与 composite-view 已作为正式 `modelType` 落地（DESIGN.md §7）。完整 ObjectSet、任意双向关系遍历等本体能力仍按后续业务路线评估。

## 开发态业务模型关系画布

业务模型页面以 AMIS Tab 承载列表和 `business-model-graph` 渲染器。关系图从 `BusinessModelMeta.relationDefinitions()` 读取关系，包含模型锁的持有人信息。单击节点显示详情，进入编辑时先获取既有模型锁，再通过基座动作打开编辑页签。模块筛选沿用列表的精确 namespace 语义，其他节点淡化保留关联上下文。

### 界面与探索

页面延续基座侧栏、应用页签、白色工作区、字体、AMIS 线形 Tab 和主题按钮。模型列表与可视化建模位于同一业务模型页。看板的可视化建模入口携带 `view=visual-modeling`，页面 Schema 通过 `activeKey` 选择对应 Tab。

| 区域 | 展示与交互 |
| --- | --- |
| 模块树 | 沿用已有模块筛选栏，可通过图内开关收起。显示状态由 AMIS 页面事件更新，保留当前协同会话。 |
| 工具区 | 选择画布、新建切面、打开模型库、搜索、切换模块分组、查看直接关联和适应画布，同时展示同步状态及模型与关系数量。 |
| 画布 | 浅色细点网格，节点显示图标、名称、标识、属性数和当前画布关联数。模块框显示模块名称、模型数及整体拖动入口。 |
| 关系 | 子表关系采用主题色实线和 1∶N 标签，引用或主表关系采用灰色虚线。标签与详情保留关系名称、来源、目标和类型。 |
| 模型库与详情 | 模型库位于画布左侧，支持搜索、拖入和加号添加，重复模型显示已添加。选择节点或关系后可查看详情和关联上下文。 |
| 视角 | 空白处拖动平移，支持缩放与适应画布。方向键按 8 个画布单位微调，Shift 加方向键按 24 个单位微调。 |

节点基准尺寸为 180×82 个画布单位，工具控件高度为 32px，间距为 8px。主色、表面、边框及文字读取主题变量，避免图面与基座形成不同风格。适应画布按可见视口和有效节点、分组边界计算，模块框已预留的标题与自关联空间只计算一次。模型库、详情或模块栏改变可用空间后，保留本地浏览视角。

### 布局与切面

每张画布包含模型引用、自由布局和模块分组布局。全局总览纳入应用内全部模型，切面通过模型 fullName 选择成员，自动呈现成员之间已有的关系。移除切面成员更新画布引用。模型被删除或改名后，原引用显示为不可用，开发者可以显式移除。分组显示、缩放和平移属于各终端的浏览状态。

模块框按 namespace 和现有模块树组织嵌套层级。分组布局中的节点保持在所属框内，拖动模块标题带动其内部节点及子模块，单次拖动期间框尺寸固定。同级模块框重叠时恢复原位。自由布局与分组布局分别保存位置，切换后恢复各自布局。成员或模块层级变化时重新计算有效框，保存位置失去包含关系或产生框重叠时采用重新布局。

画布以应用 VCS 文件 `business-model/canvases/{id}.canvas.json` 保存。全局总览使用 `overview.canvas.json`，每个切面使用自己的 ID 和文件。同一画布同时保存自由与分组布局，通过模型标识引用业务模型定义。切面增减模型只改变当前画布的成员和连线展示。

### 持久化与协同

画布通过 Yjs 和基座 STOMP 实时协同。一次拖拽、模型增删和整组移动按原子操作自动同步。`BusinessModelCanvasService` 提供资源位置和初始数据校验，通用协同服务负责检查点、增量和持久化。并发操作由 Yjs 合并，待同步操作保留在当前会话中。画布操作自动提交，原型阶段的本机手动保存已由该链路接替。协议、权限、迁移和当前边界见[模型实时协同](../../cross-cutting/model-collaboration.md)。

| HTTP 入口 | 行为 |
| --- | --- |
| `GET /api/dev/{appName}/business-model/graph` | 返回节点、锁持有人和模型关系。 |
| `GET /api/dev/{appName}/collaboration/business-model-canvas` | 返回协同文档快照及当前编辑权限。 |
| `POST /api/dev/{appName}/collaboration/business-model-canvas/{canvasId}/initialize` | 首次初始化画布或返回已有协同状态。 |
| `POST /api/dev/{appName}/collaboration/business-model-canvas/{canvasId}/updates` | 保存并广播一次原子操作。 |

前端实现入口为 [BusinessModelGraph](../../../../frontends/web/ouroboros-amis-runtime/src/renderers/BusinessModelGraph.tsx)，布局规则位于 [business-model-graph](../../../../frontends/web/ouroboros-amis-runtime/src/renderers/business-model-graph.ts)。画布初始校验由 [BusinessModelCanvasServiceTest](../../../../core/ouroboros-business-model/ouroboros-business-model-dev/src/test/java/com/ouroboros/businessmodel/dev/service/impl/BusinessModelCanvasServiceTest.java) 覆盖。通用持久化及实时链路测试位于 dev-app-management 的 `collaboration` 测试包，前端协同与画布行为测试位于 main-app 的 renderer 测试目录。
