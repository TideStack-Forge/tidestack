---
doc_type: product-spec
status: active
created: 2026-07-04
---

# 复合业务视图产品规格

## 1. 文档状态

- 状态：当前功能规格，验收范围按第 12 节执行
- 适用范围：复合业务视图（`composite-view`）业务模型类型的产品定位、字段级契约、运行时执行入口、权限模型和操作链路
- 取代关系：本规格承接旧统计规格中“明细查询 / Query View / 综合查询页面”的产品诉求；旧稿由 Git 历史追溯，不作为当前契约正文

当前交付支持数据集引用和私有内嵌查询、过滤、列展示、默认排序、分页和既有 WebAPI 按钮。两种来源共用参数、账户上下文、数据范围和执行限制流水线。API 引用支持 operationId 或 `METHOD /path`。按钮由现有 WebAPI 执行自身授权与业务行为。

## 2. 产品定位

复合业务视图解决的是“查到一批跨实体记录并继续处理”的业务明细查询场景。它是业务模型类型 `composite-view`，不是统计分析图表，也不是仪表板组件。

用户使用复合业务视图时，核心任务是：

- 选择或内嵌一个受治理的数据查询来源
- 定义最终用户可以看到的列、筛选器、排序和分页
- 给查询结果配置行级或批量操作按钮
- 通过按钮进入既有 WebAPI → 逻辑流链路完成写操作或后续处理

## 3. 与统计分析和综合查询的边界

| 能力 | 本规格结论 |
|---|---|
| 数据集 | 提供受治理的数据来源和执行流水线；复合业务视图可通过 `datasetName` 引用 |
| 图表 | 用于指标、趋势、分布等只读分析展示；不承接明细处理流程 |
| 仪表板 | 用于组合图表和分析交付；不内置明细查询写操作 |
| 复合业务视图 | 用于明细查询、结果列表和后续处理入口 |
| 旧 Query View | 一次性转换到 composite-view 业务模型 |

复合业务视图不把写逻辑放入查询定义。查询定义只负责只读数据、展示列、过滤器和操作按钮声明；写操作必须由按钮指向的 `apiRef` 进入既有 WebAPI，再由 WebAPI 内部调用逻辑流或服务完成。

## 4. 用户故事

1. 作为业务开发者，我可以基于一个数据集或内嵌查询定义一个列表页，让业务用户按字段筛选、分页查看明细记录。
2. 作为业务开发者，我可以为结果行配置“查看、处理、批量提交”等按钮，把选中记录字段绑定到后端 API 参数。
3. 作为平台维护者，我可以从定义中判断数据来源、权限链路、按钮行为和运行时入口，不需要再追旧 Query View 产品语义。

## 5. 非目标

- 不建设完整 BI、自助分析或自由 SQL 控制台。
- 不恢复旧统计分析中的 Query View 一级产品入口。
- 不在 `actionButtons` 中表达写逻辑、流程节点或业务规则。
- 不绕过 Dataset 执行流水线直接调用底层 SQL 执行器。
- 不新建与业务模型类型、Dataset 执行或 WebAPI 并行的注册机制。

## 6. 定义模型

`CompositeViewBusinessModelMeta` 是业务模型定义，`modelType = "composite-view"`。定义字段如下：

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `datasetName` | `string` | 条件必填 | 引用已发布或可预览的 Dataset；与 `embeddedQuery` 二选一 |
| `embeddedQuery` | `EmbeddedQueryDefinition` | 条件必填 | 内嵌查询定义；与 `datasetName` 二选一 |
| `columns` | `ColumnDef[]` | 是 | 结果列定义；`field` 必须来自 Dataset schema 或 embeddedQuery schema 白名单 |
| `filterConfig` | `FilterConfig` | 否 | 对最终用户暴露的过滤器 UI 声明 |
| `actionButtons` | `ActionButtonDef[]` | 否 | 结果行或批量操作按钮声明 |
| `layoutConfig` | `LayoutConfig` | 是 | 分页、默认排序和列表布局配置 |

### 6.1 数据来源 XOR

`datasetName` 与 `embeddedQuery` 必须满足 XOR：

| `datasetName` | `embeddedQuery` | 结果 |
|---|---|---|
| 有 | 无 | 通过，走 Dataset 执行流水线 |
| 无 | 有 | 通过，走 embeddedQuery 权限模型和执行流水线 |
| 有 | 有 | 校验失败：`composite-view.source-conflict` |
| 无 | 无 | 校验失败：`composite-view.source-missing` |

### 6.2 embeddedQuery 权限模型

`embeddedQuery` 用于复合业务视图私有查询定义。它不成为统计分析主链的新产品入口，也不允许绕过权限治理。

- embeddedQuery 必须声明执行账户上下文、参数白名单和结果 schema。
- embeddedQuery 的执行仍进入 Dataset 级 normalize → authorize → compile → execute 语义；实现上可通过临时 Dataset execution context 承载，但对外不暴露为独立 Dataset。
- embeddedQuery 不能直接拼接运行时用户输入；所有最终用户输入都必须先经过 `DynamicFilterSpec` 或定义内参数白名单。
- embeddedQuery 查询结果只服务当前 composite-view，不被图表、仪表板或其它视图复用。
- embeddedQuery 发布时必须进入复合业务视图发布检查；非法字段、非法参数、权限上下文缺失均阻塞发布。

## 7. 字段级契约

### 7.1 `columns`

```text
columns: List<ColumnDef{
  field: string,
  title: string,
  width?: number,
  format?: "text" | "number" | "integer" | "decimal" | "percent" | "currency" | "date" | "datetime" | "boolean" | "enum"
}>
```

- `field` 必须命中数据源 schema 白名单。
- `title` 是用户可见列名，不改变底层字段名。
- `width` 是展示建议，不参与运行时查询。
- `format` 只影响渲染，不改变返回值类型。

### 7.2 `filterConfig`

```text
filterConfig: FilterConfig{
  items: List<FilterItemDef{
    field: string,
    ops: List<string>,
    widget: "input" | "number" | "date" | "datetime" | "select" | "multiSelect" | "checkbox" | "switch"
  }>
}
```

- `field` 必须命中数据源 schema 白名单。
- `ops` 必须兼容 DynamicFilterSpec 操作符集合；非法 op 在定义校验阶段阻塞。
- `widget` 是 UI 建议，运行时仍以 DynamicFilterSpec 为执行合同。
- 空 `filterConfig.items` 表示不向最终用户暴露过滤器，不等于禁用权限过滤。

### 7.3 `actionButtons`

```text
actionButtons: List<ActionButtonDef{
  label: string,
  icon?: string,
  scope: "row" | "batch",
  apiRef: string,
  contextBinding: Map<string,string>
}>
```

- `scope = "row"` 时，`contextBinding` 从当前行字段绑定到 API 参数。
- `scope = "batch"` 时，`contextBinding` 可绑定选中行集合、主键集合或批量上下文。
- `apiRef` 指向后端 WebAPI；WebAPI 内部负责调用逻辑流或业务服务。
- `actionButtons` 不包含写 SQL、逻辑流节点、脚本、条件表达式或权限绕过规则。

### 7.4 `layoutConfig`

```text
layoutConfig: LayoutConfig{
  pageSize: number,
  defaultSort?: List<SortDef{ field: string, direction: "asc" | "desc" }>
}
```

- `pageSize` 必填且必须为正整数。
- `defaultSort.field` 必须命中 schema 白名单。
- 排序与分页进入 Dataset 执行层，不在 controller 层截断。

## 8. 运行时执行入口

```text
POST /api/runtime/composite-view/{viewName}/execute
Request:  { runtimeParameters?: object, dynamicFilter?: DynamicFilterSpec, page?: PageSpec }
Response: WebApiResult<DatasetResult>
DatasetResult: { rows: [...], pageInfo: { pageNo, pageSize, total }, resolvedSchema, executionMeta, diagnostics, appliedParameters }
```

- 该入口是只读查询入口。
- `page` 缺失时使用 `layoutConfig.pageSize` 查询第一页。显式分页仍需满足正整数和有效执行限制。
- `dynamicFilter` 复用 Dataset 执行层合同，字段白名单和 op 校验与 Dataset 保持一致。
- `pageInfo.total` 由数据集执行层 COUNT 通道提供。
- `datasetName` 路径必须走 `DatasetExecutionService` 完整流水线，不得绕过 normalize、authorize、compile、execute。

## 9. 错误语义

| 场景 | 错误码 | 说明 |
|---|---|---|
| view 不存在 | `composite-view.not-found` | 未找到对应 `viewName` |
| 数据来源同缺 | `composite-view.source-missing` | `datasetName` 与 `embeddedQuery` 均为空 |
| 数据来源同在 | `composite-view.source-conflict` | `datasetName` 与 `embeddedQuery` 同时存在 |
| 字段非法 | `dataset.field-not-allowed` | 透传 Dataset / DynamicFilterSpec 字段白名单错误 |
| op 非法 | `dataset.filter-op-not-allowed` | 透传 DynamicFilterSpec op 校验错误 |
| page 非法 | `dataset.page-invalid` | 透传 Dataset 执行层分页错误 |
| API 引用非法 | `composite-view.action-api-invalid` | 定义校验阶段发现 `apiRef` 不存在或不可访问 |

## 10. 发布检查

发布 composite-view 前必须检查：

- `datasetName` / `embeddedQuery` XOR 成立。
- `columns.field`、`filterConfig.items.field`、`layoutConfig.defaultSort.field` 全部在 schema 白名单内。
- `filterConfig.items.ops` 全部在 DynamicFilterSpec 支持集合内。
- `actionButtons.apiRef` 指向可用 WebAPI。
- `actionButtons.contextBinding` 的源字段存在，目标参数与 WebAPI 合同兼容。
- embeddedQuery 权限上下文、参数白名单和结果 schema 完整。

## 11. 与业务模型插件系统的关系

复合业务视图必须作为业务模型类型落地：

- `modelType = "composite-view"`。
- 编辑器 schema 通过 `EditorSchemaProvider` SPI 贡献。
- 投影、权限和运行时模型通过业务模型正式扩展点进入。
- 插件过滤遵守 `BusinessModelTypes` 和 `getPluginsForModelType(String modelType)` 的类型适用性语义。
- 不新建 composite-view 专属平行 registry。

## 12. 验收标准

验收以第 6–11 节的产品行为与契约为准。以下 AC 标识用于关联测试和任务证据；文档可搜索、frontmatter 合法或旧稿已归档不能代替业务验收。

### 12.1 环境与数据前提

在隔离的开发或测试环境中，固定源提交、应用配置与数据快照，按平台正式发布/预览机制准备：

- 一个 schema 含 `id`、`amount`、`status` 的测试 Dataset，以及引用它的 composite-view；准备 5 条确定记录：`S1/10/open`、`S2/20/closed`、`S3/30/open`、`S4/40/closed`、`S5/50/open`。这些名称是验收夹具，不代表仓库已提供该数据。
- 一个结果 schema 与上述字段相同的 embeddedQuery 视图，按第 6.2 节准备执行上下文和参数白名单。
- 已注册的行操作及批量操作 WebAPI，合同中含可从选中记录绑定的参数，并进入既有逻辑流或服务；操作结果可通过业务状态或关联执行记录核对。
- 满足对应模型、Dataset 和 WebAPI 既有访问规则的测试账户。拒绝路径按第 6.2、10 节及已有权限规则准备，不另定义一套权限策略。

下面的分页、筛选和排序请求使用 Dataset 的正式 `PageSpec`、`DynamicFilterSpec` 与排序类型，操作符取现有支持集合。每个场景独立恢复数据，写操作场景不能污染后续查询的预期值。

### 12.2 行为验收表

| 标识 | 前置状态与触发 | 可观察结果 |
| --- | --- | --- |
| CV-AC-01 | 定义只含有效 `datasetName`，执行该视图 | 定义通过，进入该 Dataset 的 normalize → authorize → compile → execute 主链；返回夹具对应数据，不能被客户端传入的其他 datasetName 替换 |
| CV-AC-02 | 定义只含完整 `embeddedQuery`，执行该视图 | 定义通过，按第 6.2 节进入 Dataset 级治理语义，返回该私有查询结果；不存在绕过治理的直接 SQL 入口 |
| CV-AC-03 | `datasetName` 和 `embeddedQuery` 同时存在，提交定义/发布 | 校验失败，错误语义为 `composite-view.source-conflict`，不得成为可用发布版本 |
| CV-AC-04 | 两种数据来源均缺失，提交定义/发布 | 校验失败，错误语义为 `composite-view.source-missing` |
| CV-AC-05 | 依次把 columns、过滤字段、默认排序字段改为 schema 外的字段；另以非法 dynamicFilter 字段执行有效视图 | 定义/发布拒绝非法配置；运行时拒绝非法筛选字段，错误语义为 `dataset.field-not-allowed`，不返回未经允许的字段数据 |
| CV-AC-06 | 定义 filterConfig 时使用不支持的 op；另在运行时使用不支持的筛选 op | 定义校验阻塞；运行时返回 `dataset.filter-op-not-allowed`，不把非法操作符送入查询执行 |
| CV-AC-07 | 不传 page 后查询；另使用非法页码/页大小，或配置非正整数的 layoutConfig.pageSize | 缺省 page 使用布局默认分页；非法分页返回 `dataset.page-invalid` 或阻塞非法定义 |
| CV-AC-08 | 按 amount 升序、每页 2 条请求第 2 页 | 返回 `S3`、`S4`，COUNT 总数为 5；排序、分页和计数由 Dataset 执行层完成，不在 Controller 中截断全集 |
| CV-AC-09 | 筛选 status 等于 open，按 amount 升序、每页 2 条请求第 1 页 | 返回 `S1`、`S3`，COUNT 总数为 3；下一页仅 `S5`，总数不因页大小改变 |
| CV-AC-10 | 用有效请求执行不存在的 viewName | 返回 `composite-view.not-found`，不退回其他视图或直接执行请求内指定的数据源 |
| CV-AC-11 | 在 S3 行触发 row 按钮 | contextBinding 从当前行取值，WebAPI 收到合同兼容的参数；后续逻辑流/服务产生预期业务结果。只执行查询时不会触发该写操作 |
| CV-AC-12 | 选择 S1、S5 后触发 batch 按钮 | 选中记录集合、主键集合或批量上下文按已配置绑定传给 WebAPI；业务结果能对应到这两条选中记录，未选中的 S2 不被误当作输入 |
| CV-AC-13 | 分别配置不存在/不可用的 apiRef、缺失源字段和不兼容的目标参数后发布 | 发布被阻塞；非法 API 引用的错误语义为 `composite-view.action-api-invalid`，其他合同问题可定位到相应按钮和绑定项 |
| CV-AC-14 | embeddedQuery 缺少第 6.2 节要求的上下文、参数白名单或结果 schema；或含非法字段/参数后发布 | 发布被阻塞，缺失项可定位；运行时用户输入不能直接拼入查询，遵守第 6.2 节既有约束 |
| CV-AC-15 | 通过正式业务模型类型和编辑器入口创建、加载 composite-view | 类型为 `composite-view`，编辑器 schema 来自 `EditorSchemaProvider`；模型、投影和插件沿正式扩展点协作，类型过滤符合第 11 节，不要求另建专属 registry |

### 12.3 实现与验证边界

正式综合查询编辑器由业务模型的 `EditorSchemaProvider` 加载共享 `composite-view-editor`，使用默认业务模型工具栏。字段登记沿 AMIS 表单，保存完整元数据。开发合同、关联诊断与预览分别由 `/api/dev/{appName}/composite-view/contracts`、`/validate`、`/preview` 提供。诊断响应为 `{diagnostics: string[]}`，预览返回本节定义的 `DatasetResult`。

私有查询使用 `EmbeddedQueryDefinition`，包含 `queryDefinition`、`parameters`、`schema`、`requiredContext`，以及可选 `permissionDefinition` 和 `executionConstraints`。保存、投射检查定义与参数引用。执行时只接受声明的参数，从服务端取得账户上下文，结果仅保留视图配置的列。

运行和开发预览共用 `CompositeViewQueryService`。字段与操作符、默认排序和列投影规则由同一实现处理。分页计数、SQL 参数绑定、数据范围和执行限制继续经过原数据集流水线。平台配置键为 `statisticsReporting.executionPolicy`，初始值为 30 秒、1000 行、100000 行，数据集只可收紧。

模块行为测试覆盖字段/API 关联、无效草稿诊断、投射拒绝、真实 JDBC 分页和私有查询、限额、超时及恢复。浏览器验证保存、映射、诊断定位和预览。完整环境验收还须记录应用身份、数据库、实际发布路径及业务动作副作用。

每次验收在关联任务或 CI 中按 AC 标识记录提交、环境、夹具、触发步骤、实际结果和产物链接。部署与发布使用各自环境证据。


## 13. 编辑器交互设计

综合查询沿业务模型编辑入口承载，默认保持来源、列、筛选、操作、布局五个配置分区。正式工作台使用左侧分区导航与右侧列表预览。基本信息以顶部折叠区呈现，小屏幕纵向排列配置与预览。

数据来源页提供 Dataset 搜索选择器，显示名称、标题、启用状态和字段数量。选择后从该来源的输出 Schema 提供字段选项。切换来源时保留仍能匹配的字段，将失效的列、筛选、排序和动作绑定标记为待修复，交由用户确认处理。内嵌查询的 UI 复用 SQL、参数和输出 Schema 编辑概念，使用私有查询状态，从正式入口编辑并预览。

列配置逐行选择字段，自动带入显示名和类型，支持改名、宽度、格式和顺序，字段能力来自数据来源。筛选配置仅列出 filterable 字段，操作符按类型约束，控件按字段类型提供默认值。布局配置显示页大小与平台当前限制，默认排序仅列出 sortable 字段。

操作页显示行操作和批量操作。选择 WebAPI 后展示方法、路径、摘要与参数合同。参数映射表的列为目标参数、类型、必填、来源和验证状态。行操作来源为当前行字段，批量操作来源为选中字段集合或全部选中记录。参数类型不兼容、必填映射缺失和接口不存在时，错误显示在对应参数行。

顶部操作沿用默认业务模型公共编辑页，顺序为“取消、查看投射模型、保存、保存并提交”，按钮样式、未保存禁用状态、取消确认、保存快捷键和修改说明弹窗由同一宿主负责。“保存”保留带诊断的草稿并展示原 validationSnapshot，“保存并提交”沿现有业务模型提交机制处理。关联诊断显示在内容区，点击错误定位对应分区和行。右侧运行页面预览使用当前草稿，遇到无效关联定位错误。投射和发布沿平台正式生命周期阻断无效版本。

预览区呈现查询条件、总数、分页、列表列和行/批量按钮。设计稿中的写操作仅展示确认和参数结果。运行页面的操作按钮进入其引用的正式 WebAPI。

UI 验收覆盖加载、无 Dataset、来源禁用、失效引用、筛选空值、映射冲突、草稿保存、定位修复、窄屏以及局部查询失败。详细设计稿与交互证据归关联任务的 tmp/testing-evidence，长期行为由本节维护。
