# Orchestration 设计文档

查询编排把语句准备、关系条件分析、查询改写、主查询与结果转换组织为计划。查询整体阶段见[模块设计](../../DESIGN.md)，本页维护编排内部的职责、策略与取舍。

## 当前入口与阅读边界

[DefaultQueryOrchestrator](../../ouroboros-data-core/src/main/java/com/ouroboros/data/orchestration/DefaultQueryOrchestrator.java)依次使用 StatementPreparer、RelationConditionPlanner、StatementRewriteCoordinator 与 PopulatePlanner 构建 QueryPlan；SequentialPlan 顺序执行各 QueryStep，MainQueryExecutor 承接主查询。

选择器在编排器创建时通过 DataServices 发现，优先使用 `jakarta.annotation.Priority` 最高的注册实现，未注册时使用默认实现；JOIN 去重仅针对相同目标表及规范化 ON 条件，且不重写 ORDER BY 别名。

下文代码片段用于解释机制，具体签名和内部拆分以 [orchestration 包](../../ouroboros-data-core/src/main/java/com/ouroboros/data/orchestration)为准。并发计划在“暂不实现”一节说明设计取舍，不能从扩展设想推断已有 ParallelPlan。

- [查询示例](examples.md)：独立场景与执行过程。
- [策略详解](strategies.md)：策略选择与边界。
- [场景覆盖](scenarios.md)：设计需要覆盖的行为，不是当前测试通过报告。

## 1. 背景与目标

### 1.1 历史问题与替代背景

旧 `plan` 包的设计是过程式的，逻辑硬编码在 `DefaultRelationConditionProcessor` 中：

- 每增加一种场景就要修改代码
- 跨数据源关联条件无法处理（直接抛异常）
- 缺乏灵活性和可扩展性

### 1.2 目标场景

**跨数据源关联条件查询**：

```
查询: Employee WHERE department.name = '开发部'

Employee 在 DataStation A
Department 在 DataStation B

期望流程:
1. 先查询 B: SELECT id FROM Department WHERE name = '开发部' → [101, 102]
2. 改写条件: Employee WHERE department IN (101, 102)
3. 查询 A
```

### 1.3 设计理念

> "以编排为纲，以执行为手段，最终汇聚为结果"

---

## 2. 包结构

```
com.ouroboros.data.orchestration/
├── QueryOrchestrator              (编排器接口)
├── DefaultQueryOrchestrator       (默认实现)
├── OrchestrationContext           (编排上下文)
├── QueryPlan                      (执行计划)
│
├── step/
│   ├── QueryStep                  (步骤接口)
│   ├── CrossSourceRewriteStep     (跨源条件改写，查询 + 创建改写器)
│   ├── StatementRewriteStep       (同源条件改写，创建改写器)
│   ├── MainQueryStep              (应用改写器 + 主查询)
│   ├── PopulateStep               (关联填充，查询 + 创建转换器)
│   ├── ResultTransformStep        (结果变换，创建转换器)
│   └── ApplyTransformersStep      (应用所有转换器)
│
├── rewriter/
│   ├── StatementRewriter          (语句改写器接口)
│   ├── CrossSourceConditionRewriter    (跨源条件改写逻辑)
│   ├── JoinStatementRewriter      (同源 ToOne 条件改写)
│   ├── ExistsStatementRewriter    (同源 ToMany 条件改写)
│   ├── PopulateJoinRewriter       (Populate JOIN 策略改写)
│   └── JoinDeduplicator           (JOIN 去重优化)
│
├── transformer/
│   └── RecordListTransformer      (记录列表转换器接口)
│
└── strategy/
    ├── PopulateField              (填充字段信息)
    ├── PopulateStrategy           (填充策略)
    └── PopulateStrategySelector   (策略选择器，SPI 扩展)
```

---

## 3. 核心抽象

### 3.1 两阶段分离

```
阶段 1: 分析（静态）
QueryStatement → QueryPlan

阶段 2: 执行（按计划）
QueryPlan → Try<RecordList>
```

### 3.2 QueryOrchestrator

编排器是统一入口，负责生成执行计划并执行。

```java
interface MainQueryExecutor {
  Try<RecordList> execute(QueryStatement statement);
}

interface QueryOrchestrator {
  Try<RecordList> orchestrate(
      QueryStatement statement,
      DataModel rootModel,
      MainQueryExecutor mainExecutor,
      OrchestrationContext context
  );
}
```

**接入方式**：DataModel 提供 `MainQueryExecutor`（lambda），可访问私有方法（transpile + execute）。

### 3.3 QueryStep

单个执行步骤。**所有操作都是 Step**，包括语句改写。

```java
interface QueryStep {
  String getName();

  void execute(OrchestrationContext context);  // 无返回值，结果存入 Context
}
```

**设计要点**：

- Step 不返回结果，而是将结果存入 `OrchestrationContext`
- 后续 Step 通过 `context.getResult("step_name")` 获取前置结果
- 语句改写也是 Step（`StatementRewriteStep`），包装 `StatementRewriter`
- **Step 名称必须唯一**：通常使用 `"类型_字段名"` 格式（如 `"rewrite_cross_department"`, `"pop_manager"`）

### 3.4 OrchestrationContext

编排上下文，管理执行过程中的所有状态。

```java
class OrchestrationContext {
  // 关注的信息：
  // - 主查询语句（可变）
  // - 各 Step 的执行结果
  // - JOIN 别名映射
  // - Populate 别名映射
  // - 语句改写器队列（并发安全）
  // - 转换器队列（并发安全）

  // 主查询语句管理
  void setMainStatement(QueryStatement stmt);

  QueryStatement getMainStatement();

  // 结果管理
  void setResult(String stepName, RecordList result);

  RecordList getResult(String stepName);

  // 别名映射管理
  void setAliasMapping(Map<String, String> mapping);

  Map<String, String> getAliasMapping();

  void addPopulateAlias(String field, String alias);

  Map<String, String> getPopulateAliases();

  // 语句改写器管理
  void addStatementRewriter(StatementRewriter rewriter);

  Queue<StatementRewriter> getStatementRewriters();

  void clearStatementRewriters();

  // 转换器管理
  void addTransformer(RecordListTransformer transformer);

  Queue<RecordListTransformer> getTransformers();

  void clearTransformers();
}
```

**职责**：纯粹的状态容器，不包含业务逻辑。

**RecordListTransformer 接口**：

```java

@FunctionalInterface
interface RecordListTransformer {
  RecordList transform(RecordList input);
}
```

### 3.5 Step 类型

| Step                       | 用途            | 输入                              | 输出                         |
|----------------------------|---------------|---------------------------------|----------------------------|
| **CrossSourceRewriteStep** | 跨源条件改写        | 跨源条件表达式                         | 创建改写器 + 执行预查询，结果存入 Context |
| **StatementRewriteStep**   | 同源条件改写        | 无                               | 创建改写器存入 Context            |
| **MainQueryStep**          | 应用改写器 + 执行主查询 | Context 中的改写器队列 + mainStatement | 结果存入 Context               |
| **PopulateStep**           | 关联数据填充        | Context 中的 "main" 结果            | 创建转换器 + 执行关联查询             |
| **ResultTransformStep**    | 结果结构变换        | 无                               | 创建转换器存入 Context            |
| **ApplyTransformersStep**  | 应用所有转换器       | Context 中的转换器队列 + 源结果           | 转换后结果存入 Context            |

**典型执行流程**：

```
CrossSourceRewriteStep("rewrite_cross_dept")
  → 创建改写器存入 context.statementRewriters
  → 查询 Department，结果存入 context.results["rewrite_cross_dept"]

CrossSourceRewriteStep("rewrite_cross_mgr")
  → 创建改写器存入 context.statementRewriters
  → 查询 Manager，结果存入 context.results["rewrite_cross_mgr"]

StatementRewriteStep("rewrite_join")
  → 创建改写器存入 context.statementRewriters

StatementRewriteStep("rewrite_dedupe")
  → 创建改写器存入 context.statementRewriters

MainQueryStep("main")
  → 从 context 获取 mainStatement
  → 依次应用 context.statementRewriters 中的所有改写器
  → 清空 context.statementRewriters
  → 执行改写后的查询
  → 结果存入 context.results["main"]

PopulateStep("pop_dept")
  → 从 context 获取 results["main"]，提取外键
  → 查询关联数据
  → 创建转换器存入 context.transformers

PopulateStep("pop_mgr")
  → 从 context 获取 results["main"]，提取外键
  → 查询关联数据
  → 创建转换器存入 context.transformers

ApplyTransformersStep("apply_all", sourceStepName="main")
  → 从 context 获取 results["main"]
  → 依次应用 context.transformers 中的所有转换器
  → 清空 context.transformers
  → 结果存入 context.results["apply_all"]
```

### 3.6 CrossSourceRewriteStep

处理跨源关联条件的 Step，合并了预查询和改写器创建。

**职责**：

1. 创建语句改写器，添加到 Context 的改写器队列
2. 执行预查询，获取关联数据的 ID 列表
3. 存储预查询结果，供改写器使用

**执行顺序**：

- 先创建改写器（快速，不阻塞）
- 再执行预查询（慢速，为将来并发预留空间）

**设计要点**：

- 合并了 PreQueryStep 和 StatementRewriteStep 的职责
- 减少中间状态，不需要单独存储预查询结果的 Step
- 改写器在 MainQueryStep 中应用时，预查询结果已准备好

### 3.7 StatementRewriteStep

包装 `StatementRewriter` 的 Step，用于同源条件改写。

**职责**：

- 创建语句改写器，添加到 Context 的改写器队列

**设计要点**：

- Rewriter 仍然是独立的接口，负责改写逻辑
- StatementRewriteStep 是 Rewriter 的适配器，将其转换为 Step
- 每个 Rewriter 对应一个 Step，粒度细、可观测

### 3.8 MainQueryStep

应用所有语句改写器并执行主查询。

**职责**：

1. 从 Context 获取原始 mainStatement
2. 依次应用 Context 中的所有语句改写器
3. 清空改写器队列
4. 执行改写后的查询
5. 存储查询结果

**设计要点**：

- 统一应用所有改写器，保证顺序
- 执行后清空队列，避免重复应用
- 合并了 ApplyStatementRewritersStep 的职责

### 3.9 ApplyTransformersStep

应用所有 RecordListTransformer 的 Step。

**职责**：

1. 从 Context 获取源数据
2. 依次应用 Context 中的所有转换器
3. 清空转换器队列
4. 存储转换后的结果

**设计要点**：

- 统一应用所有转换器，保证顺序
- 执行后清空队列，避免重复应用
- PopulateStep 和 ResultTransformStep 延迟登记转换器；当前计划仍顺序执行

### 3.10 QueryPlan

```java
interface QueryPlan {
  Try<RecordList> execute(OrchestrationContext context);
}

class SequentialPlan implements QueryPlan {
  // 关注：Step 列表
  // 职责：按顺序执行所有步骤，任何一步失败则整体失败
}
```

**注**：当前只实现 SequentialPlan，但 Step 设计已为将来的并发优化预留空间（详见第 11 节）。

---

## 4. 概念关系与职责

### 4.1 概念关系图

```
┌─────────────────────────────────────────────────────────────────┐
│                         DataModel                                │
│  - 对外提供 query(QueryStatement) 接口                           │
│  - 创建 MainQueryExecutor (lambda) 访问私有方法                   │
│  - 调用 QueryOrchestrator 完成编排                               │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │ 调用
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                      QueryOrchestrator                           │
│  - 分析 QueryStatement，识别跨源条件和填充需求                    │
│  - 选择填充策略 (通过 PopulateStrategySelector)                   │
│  - 构建 QueryPlan (包含多个 QueryStep)                           │
│  - 执行 QueryPlan                                                │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │ 构建
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         QueryPlan                                │
│  - 持有 QueryStep 列表                                           │
│  - 按顺序执行每个 Step                                           │
│  - 将每个 Step 的结果存入 OrchestrationContext                   │
│  - 返回最后一个 Step 的结果                                      │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │ 包含
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                         QueryStep                                │
│  - 单个执行单元，有唯一名称                                       │
│  - 从 OrchestrationContext 获取前置步骤结果                       │
│  - 执行后将结果存入 OrchestrationContext                         │
└─────────────────────────────────────────────────────────────────┘
                                │
                                │ 使用
                                ▼
┌─────────────────────────────────────────────────────────────────┐
│                    OrchestrationContext                          │
│  - 持有 mainStatement（可变）                                    │
│  - 存储各 Step 的执行结果 (Map<String, RecordList>)              │
│  - 存储别名映射 (aliasMapping, populateAliases)                  │
│  - 存储语句改写器队列 (Queue<StatementRewriter>)                 │
│  - 存储转换器队列 (Queue<RecordListTransformer>)                 │
│  - 提供 getResult(stepName) 方法供后续 Step 使用                 │
└─────────────────────────────────────────────────────────────────┘
```

### 4.2 职责划分

| 组件                           | 职责                                           | 不负责       |
|------------------------------|----------------------------------------------|-----------|
| **DataModel**                | 对外接口、创建 Executor                             | 编排逻辑      |
| **QueryOrchestrator**        | 分析、构建 Plan、选择策略、创建 Rewriter                  | 具体执行、语句改写 |
| **QueryPlan**                | 协调 Step 执行顺序、管理结果                            | 分析逻辑      |
| **QueryStep**                | 单步执行逻辑                                       | 执行顺序      |
| **OrchestrationContext**     | 状态管理、结果传递                                    | 业务逻辑      |
| **StatementRewriter**        | 改写 QueryStatement（修改 SELECT/FROM/WHERE/JOIN） | 执行查询、决策逻辑 |
| **PopulateStrategySelector** | 选择填充策略                                       | 执行填充      |
| **PopulateStrategy**         | 生成填充相关的 Step                                 | 选择逻辑      |

---

## 5. QueryOrchestrator 核心逻辑

### 5.1 orchestrate() 方法流程

```java
class DefaultQueryOrchestrator implements QueryOrchestrator {

  @Override
  public Try<RecordList> orchestrate(
      QueryStatement statement,
      DataModel rootModel,
      MainQueryExecutor mainExecutor,
      OrchestrationContext context
  ) {
    // 1. 初始化 Context
    context.setMainStatement(statement);

    // 2. 分析 QueryStatement
    AnalysisResult analysis = analyzeStatement(statement, rootModel);

    // 3. 构建 QueryPlan
    QueryPlan plan = buildPlan(statement, analysis, mainExecutor, context);

    // 4. 执行 QueryPlan
    return plan.execute(context);
  }
}
```

### 5.2 分析阶段（analyzeStatement）

**职责**：

- 分析 WHERE 子句中的关联条件，识别跨源/同源、ToOne/ToMany
- **分析条件的 NULL 语义**，确定是否需要 LEFT JOIN
- 分析 POPULATE 子句，提取填充字段信息

**输出**：`AnalysisResult` 包含：

- 跨源条件列表
- 同源 ToOne 条件列表（包含 JOIN 类型信息）
- 同源 ToMany 条件列表
- Populate 字段列表

**NULL 语义分析**：

某些条件需要保留"关联字段为 NULL"的记录，这要求使用 LEFT JOIN 而非 INNER JOIN：

```sql
-- 需要 LEFT JOIN 的模式
WHERE (department.name = '开发部' OR department IS NULL)
WHERE department.name IN ('开发部', NULL)
WHERE NOT (department.name = '开发部')

-- 不需要 LEFT JOIN 的模式（使用 INNER JOIN）
WHERE department.name = '开发部'
WHERE department.name IN ('开发部', '测试部')
```

分析器通过检测以下模式来识别 NULL 语义：

1. 显式 NULL 检查：`field IS NULL`
2. OR NULL 模式：`condition OR field IS NULL`
3. IN (..., NULL) 模式
4. NOT 条件（隐含 NULL 语义）

识别结果存储在 `SameSourceCondition.requiresLeftJoin` 字段中，供后续改写阶段使用。

### 5.3 构建阶段（buildPlan）

**职责**：根据分析结果构建 QueryPlan

**构建顺序**：

1. 创建语句改写 Step（跨源 + 同源）
2. 创建 MainQueryStep（应用改写器 + 执行查询）
3. 为每个 Populate 字段创建 Step（通过策略选择器）
4. 创建 ApplyTransformersStep（统一应用所有转换器）

**返回**：`SequentialPlan` 包含所有 Step

### 5.4 构建改写 Step 列表（buildRewriteSteps）

**职责**：根据分析结果创建所有语句改写 Step

**构建顺序**：

1. 为每个跨源条件创建 `CrossSourceRewriteStep`
2. 为每个同源 ToOne 条件创建 `StatementRewriteStep` + `JoinStatementRewriter`
3. 为每个同源 ToMany 条件创建 `StatementRewriteStep` + `ExistsStatementRewriter`
4. 为使用 JOIN 策略的 Populate 创建 `StatementRewriteStep` + `PopulateJoinRewriter`
5. 最后添加 `StatementRewriteStep` + `JoinDeduplicator`（去重优化）

**返回**：Step 列表

### 5.5 完整示例：Step 列表构建

**输入查询**：

```
Employee WHERE department.name = '开发部' AND project.status = 'active'
POPULATE department, manager
```

**分析结果**：

- `department.name = '开发部'`：跨源条件
- `project.status = 'active'`：同源 ToOne 条件
- `POPULATE department`：跨源填充
- `POPULATE manager`：跨源填充

**构建的 Step 列表**：

```java
steps =[
    // 1. 语句改写（创建改写器）
    // CrossSourceRewriteStep 关注：步骤名称、关联字段、关联模型、条件表达式、本地字段
    new

CrossSourceRewriteStep(...),  // 预查询 + 创建改写器

// StatementRewriteStep 关注：步骤名称、改写器实例
// JoinStatementRewriter 关注：关联字段
    new

StatementRewriteStep(...),    // 创建改写器

// JoinDeduplicator 关注：无（自动分析）
    new

StatementRewriteStep(...),    // 创建改写器

// 2. 主查询（应用改写器 + 执行查询）
// MainQueryStep 关注：步骤名称、主查询执行器
    new

MainQueryStep(...),

// 3. 填充（查询 + 创建转换器）
// PopulateStep 关注：步骤名称、填充字段信息、源数据步骤名称
    new

PopulateStep(...),  // 查询 Department，创建转换器
    new

PopulateStep(...),  // 查询 Manager，创建转换器

// 4. 统一应用所有转换器
// ApplyTransformersStep 关注：步骤名称、源数据步骤名称
    new

ApplyTransformersStep(...)
]
```

**执行流程**：

1. `CrossSourceRewriteStep` 执行
    - 创建改写器存入 `context.statementRewriters`
    - 查询 Department，结果存入 `context.results["rewrite_cross_department"]`
2. `StatementRewriteStep` (JOIN) 创建改写器存入 `context.statementRewriters`
3. `StatementRewriteStep` (去重) 创建改写器存入 `context.statementRewriters`
4. `MainQueryStep` 执行
    - 依次应用 `context.statementRewriters` 中的所有改写器
    - 清空 `context.statementRewriters`
    - 执行改写后的查询，结果存入 `context.results["main"]`
5. `PopulateStep` (department) 执行
    - 查询 Department 关联数据
    - 创建转换器存入 `context.transformers`
6. `PopulateStep` (manager) 执行
    - 查询 Manager 关联数据
    - 创建转换器存入 `context.transformers`
7. `ApplyTransformersStep` 执行
    - 从 `context.results["main"]` 读取
    - 依次应用 `context.transformers` 中的所有转换器
    - 清空 `context.transformers`
    - 结果存入 `context.results["apply_all"]`

**延迟转换的优势**：

- 设计上为将来并发优化预留空间
- 改写器和转换器按添加顺序执行，保证结果正确性
- 最终结果包含所有填充字段
- 简化了 Step 之间的依赖关系

### 5.6 DataModel Integration

`DefaultDataModel.query()` 在准备语句后进入 `orchestrateQuery()`，创建 `OrchestrationContext`，并从 `ModelQueryStatement` 传入 populate/omit 子句。主查询通过 `MainQueryExecutor` 回调执行 `DataAdapter.query(query, transpileContext)`，再转换记录值。

`DefaultQueryOrchestrator` 负责准备、关系条件规划、语句改写和 populate 计划；执行计划按步骤将改写结果交给主查询回调。这里不再通过旧 `DefaultQueryExecutor` 或 `QueryExecutionContext` 包装主查询。

正式入口见 [DefaultDataModel](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DefaultDataModel.java) 和 [DefaultQueryOrchestrator](../../ouroboros-data-core/src/main/java/com/ouroboros/data/orchestration/DefaultQueryOrchestrator.java)。内部方法与参数以源类型为准，文档维护职责和执行顺序。

---

## 6. StatementRewriter 设计

### 6.1 接口定义

```java
interface StatementRewriter {
  QueryStatement rewrite(QueryStatement statement, OrchestrationContext context);
}
```

### 6.2 典型实现

| Rewriter                       | 用途               | 关键参数                                   |
|--------------------------------|------------------|----------------------------------------|
| `CrossSourceConditionRewriter` | 跨源关联条件改写         | preStepName, localField, maxInListSize |
| `JoinStatementRewriter`        | 同源 ToOne 关联条件    | relationField, joinType                |
| `ExistsStatementRewriter`      | 同源 ToMany 关联条件   | relationField                          |
| `PopulateJoinRewriter`         | Populate JOIN 策略 | relationField, selectFields            |
| `JoinDeduplicator`             | JOIN 去重优化        | 无（自动分析）                                |

**关键设计点**：

1. **JoinStatementRewriter 支持 JOIN 类型选择**
    - 根据分析阶段识别的 NULL 语义，选择 INNER JOIN 或 LEFT JOIN
    - 保留 NULL 记录的条件使用 LEFT JOIN
    - 普通条件使用 INNER JOIN

2. **CrossSourceConditionRewriter 支持大结果集优化**
    - 当预查询结果 > maxInListSize（默认 1000）时，自动分批处理
    - 使用 UNION ALL 合并多个批次的查询

### 6.3 CrossSourceConditionRewriter 逻辑

**职责**：将跨源关联条件改写为本地字段的 IN/EQ 条件

**关注信息**：

- 预查询步骤名（从 Context 获取预查询结果）
- 本地关联字段（改写目标）
- IN 列表大小阈值（默认 1000）

**改写策略**：

**基本策略**（结果集 ≤ 1000）：

- 预查询结果为空 → `1 = 0`
- 预查询结果为单个值 → `field = value`
- 预查询结果为多个值 → `field IN (...)`

**分批策略**（结果集 > 1000）：

当预查询返回大量 ID 时（如 10000 个），直接使用 IN 条件会导致性能问题。此时自动分批处理：

```sql
-- 原始（10000 个 ID）
SELECT *
FROM Employee
WHERE department IN (1, 2, . . ., 10000)

-- 分批（每批 1000）
SELECT *
FROM Employee
WHERE department IN (1, 2, . . ., 1000)
UNION ALL
SELECT *
FROM Employee
WHERE department IN (1001, . . ., 2000)
UNION ALL
.
.
.
```

**分批优势**：

- 避免 SQL 语句过长
- 避免数据库 IN 列表性能下降
- 可配置阈值，适应不同数据库

### 6.4 JoinStatementRewriter 逻辑

**职责**：将同源 ToOne 关联条件改写为 JOIN

**关注信息**：

- 关联字段名
- JOIN 类型（INNER JOIN 或 LEFT JOIN）

**JOIN 类型选择**：

根据条件的 NULL 语义选择合适的 JOIN 类型：

**使用 INNER JOIN**（默认）：

```sql
-- 条件：department.name = '开发部'
-- 语义：必须有 department 且满足条件
SELECT e.*
FROM Employee e
         INNER JOIN Department d ON e.department = d.id
WHERE d.name = '开发部'
```

**使用 LEFT JOIN**（保留 NULL）：

```sql
-- 条件：(department.name = '开发部' OR department IS NULL)
-- 语义：department 是开发部，或者没有 department
SELECT e.*
FROM Employee e
         LEFT JOIN Department d ON e.department = d.id
WHERE d.name = '开发部'
   OR e.department IS NULL
```

**关键点**：

- JOIN 类型由分析阶段确定（`SameSourceCondition.requiresLeftJoin`）
- LEFT JOIN + WHERE 条件会过滤掉不满足条件的 NULL 记录
- 如果条件不包含 OR NULL，LEFT JOIN 等价于 INNER JOIN

### 6.5 JoinDeduplicator 逻辑

**职责**：识别并合并重复的 JOIN，统一别名引用

**关注信息**：无（自动分析语句中的所有 JOIN）

**优化策略**：

1. 按目标表及规范化 ON 条件签名分组 JOIN；不同连接条件保持分开
2. 识别重复 JOIN，选择"主 JOIN"（LEFT JOIN 优先）
3. 删除重复的 JOIN
4. 替换 WHERE、SELECT 中的旧别名；当前不重写 ORDER BY
5. 在 Context 中记录别名映射（供 ResultTransformStep 使用）

**LEFT JOIN 优先原则**：

当同一去重组内有多个 JOIN 时，当前实现优先保留 LEFT JOIN：

- LEFT JOIN 保留更多记录（包括 NULL）
- 如果条件需要 INNER JOIN 语义，WHERE 子句会自动过滤 NULL
- 如果条件需要 LEFT JOIN 语义，必须使用 LEFT JOIN
- 不能据此合并同表但 ON 条件不同的 JOIN；NULL、过滤和去重后的行为仍须按具体查询验证

**示例**：

```sql
-- 条件添加 INNER JOIN
INNER JOIN Department d1 ON e.department = d1.id
WHERE d1.name = '开发部'

-- Populate 添加 LEFT JOIN
LEFT JOIN Department d2 ON e.department = d2.id

-- 合并为 LEFT JOIN
LEFT JOIN Department d ON e.department = d.id
WHERE d.name = '开发部'

-- 结果：WHERE 子句过滤掉 NULL，等价于 INNER JOIN
```

### 6.6 Rewriter 与 Step 的关系

```
StatementRewriter (接口)
    ↓ 实现
CrossSourceConditionRewriter (具体改写逻辑)
    ↓ 包装
StatementRewriteStep("rewrite_cross_department", rewriter)
    ↓ 添加到
QueryPlan.steps
```

**设计优势**：

- **职责分离**：Rewriter 只管改写逻辑，Step 管执行流程
- **可复用**：同一个 Rewriter 可以被多个 Step 使用
- **可测试**：Rewriter 可以独立测试，不依赖 Step 框架
- **可观测**：每个 Rewriter 作为独立 Step，便于日志和调试

---

## 7. Populate 填充策略

### 7.1 两种策略

| 策略           | 适用场景             | 实现方式                |
|--------------|------------------|---------------------|
| **Separate** | 跨源、有子级填充         | 主查询后批量查询关联数据        |
| **JOIN**     | 同源、无子级填充、支持 JOIN | 主查询中 LEFT JOIN，结果变换 |

### 7.2 策略选择

编排器创建时选择 SPI 实现，未注册时使用 DefaultPopulateStrategySelector。默认实现按下列顺序选择策略：

1. **跨源** → Separate
2. **有子级填充** → Separate
3. **同源可 JOIN** → JOIN
4. **默认** → Separate

### 7.3 PopulateStrategy 接口

```java
interface PopulateStrategy {
  /**
   * 创建填充相关的 Step 列表
   *
   * @param populate 填充字段信息（包含字段名、select 列表、子级 populate 等）
   * @param sourceStepName 数据源 Step 名称（通常是 "main"）
   * @return Step 列表
   */
  List<QueryStep> createSteps(PopulateField populate, String sourceStepName);
}
```

### 7.4 Separate 策略实现

**职责**：创建 PopulateStep，通过独立查询填充关联数据

**PopulateStep 关注信息**：

- 源数据步骤名称（固定为 "main"）
- 关联模型（用于执行填充查询）
- 本地外键字段、关联表主键字段
- 填充字段名、选择字段列表
- 子级填充配置（递归）

**执行流程**：

1. 从 main 获取数据，提取外键
2. 查询关联数据（当前随 SequentialPlan 顺序执行）
3. 创建转换器 Lambda，捕获查询结果
4. 存入转换器队列

### 7.5 JOIN 策略实现

**职责**：创建 ResultTransformStep，将扁平化的 JOIN 结果转换为嵌套结构

**注意**：PopulateJoinRewriter 已在 buildRewriteSteps() 中添加，这里只需添加 ResultTransformStep

**ResultTransformStep 关注信息**：

- 源数据步骤名称（固定为 "main"）
- 目标字段名（如 "department"）
- 扁平化字段前缀（如 "dept__"，对应 `dept__id`, `dept__name`）
- 选择字段列表（用于构建嵌套对象）

**执行流程**：

1. 创建转换器 Lambda
2. 转换器将扁平字段（如 `dept__id`, `dept__name`）提取为嵌套对象
3. 存入转换器队列

### 7.6 子级填充

子级填充通过递归实现：`PopulateStep` 调用 `relatedModel.query()` 时传递子级 populate 配置，由关联模型的 QueryOrchestrator
处理。

---

## 8. JOIN 去重优化

### 8.1 设计原则

1. **原有 Rewriter 保持独立**：
    - JoinStatementRewriter 和 PopulateJoinRewriter 完全不知道优化器存在
    - 它们正常添加需要的 JOIN，不做任何特殊处理

2. **优化器作为后置处理**：
    - 在所有 Rewriter 执行完后运行
    - 分析整个 QueryStatement，识别重复 JOIN
    - 合并重复 JOIN，统一别名引用

3. **通用性**：
    - 可以处理 Rewriter 添加的重复 JOIN
    - 可以处理用户原始查询中的 JOIN
    - 可以处理两者混合的情况

### 8.2 优化场景

**场景 1**：同源关联条件和 POPULATE 指向同一个关联字段

```
Employee WHERE department.name = '开发部'
POPULATE department
```

→ 合并 INNER JOIN 和 LEFT JOIN 为一个 LEFT JOIN

**场景 2**：用户原始查询已包含 JOIN，又需要 POPULATE

```
用户查询: Employee JOIN Department AS dept ...
添加: POPULATE department
```

→ 合并用户的 JOIN 和 Populate 的 JOIN

**场景 3**：多个字段的重复 JOIN

```
Employee WHERE department.name = '开发部' AND manager.level > 5
POPULATE department, manager
```

→ 同时优化 Department 和 Manager 的重复 JOIN

**详细示例**: 参见 [examples.md#6-join-去重优化示例](./examples.md#6-join-去重优化示例)

---

## 9. DataModel 接入

**职责**：

- DataModel 持有 QueryOrchestrator 实例
- 在 `query()` 方法中调用 `orchestrator.orchestrate()`
- 提供 MainQueryExecutor（lambda）访问私有的 transpile + execute 方法

**关键点**：

- MainQueryExecutor 是 lambda，可以访问 DataModel 的私有方法
- 通过这种方式，Step 无需知道 DataModel 的内部实现细节

---

## 10. 设计原则

1. **所有操作都是 Step**：
    - 跨源条件改写是 Step（CrossSourceRewriteStep）
    - 同源条件改写是 Step（StatementRewriteStep 包装 Rewriter）
    - 主查询是 Step（MainQueryStep）
    - 填充是 Step（PopulateStep）
    - 结果变换是 Step（ResultTransformStep）
    - 统一的抽象，便于扩展和观测

2. **Step 无返回值，结果存入 Context**：
    - Step 执行后将结果存入 OrchestrationContext
    - 后续 Step 从 Context 获取前置结果
    - Context 是唯一的状态容器

3. **编排与执行分离**：
    - 先分析 QueryStatement，生成 QueryPlan
    - 再按 Plan 顺序执行所有 Step
    - 分析阶段是静态的，执行阶段是动态的
    - **分析阶段识别 NULL 语义**，为改写阶段提供 JOIN 类型信息

4. **Rewriter 与 Step 分离**：
    - Rewriter 是纯粹的改写逻辑，可独立测试
    - StatementRewriteStep 是 Rewriter 的适配器
    - 职责清晰，易于维护
    - **Rewriter 支持参数化**（如 JOIN 类型、IN 列表阈值）

5. **延迟转换与统一应用**：
    - PopulateStep 和 ResultTransformStep 只创建转换器，不修改结果
    - 转换器存入 Context 的队列中
    - ApplyTransformersStep 统一应用所有转换器
    - PopulateStep 当前顺序执行；并发属于待设计能力

6. **自动优化**：
    - **JOIN 去重**：JoinDeduplicator 自动合并重复 JOIN
    - **大结果集分批**：CrossSourceConditionRewriter 自动检测并分批处理
    - **LEFT JOIN 优先**：保证语义正确性

7. **快速失败**：
    - 任何 Step 失败，整个查询失败
    - 返回 Try.failure，包含异常信息

8. **SPI 扩展**：
    - PopulateStrategySelector 通过 META-INF/services 注册；默认编排器使用 DataServices 按 Priority 选取实现
    - 支持自定义填充策略

---

## 11. 暂不实现：并发执行

### 11.1 场景分析

理论上可并发的场景：

- **多个 CrossSourceRewriteStep 之间**（预查询可并发，创建改写器不阻塞）
- **多个 PopulateStep 之间**（查询关联数据可并发，创建转换器不阻塞）
- **多个 ResultTransformStep 之间**（只创建转换器，可并发）

**当前设计已为并发预留空间**：

- CrossSourceRewriteStep：先创建改写器（快），再执行预查询（慢）
- PopulateStep：先创建转换器（快），再执行关联查询（慢）
- 改写器和转换器都存入队列，不立即应用

### 11.2 实现方案

考虑过两种方案：

1. **QueryStep 返回 CompletableFuture**：统一异步模型，但接口复杂化
2. **并发 QueryPlan 实现**：Step 保持简单，由 ParallelPlan 协调执行

方案 2 更优，Step 增加 `getDependencies()` 声明依赖，ParallelPlan 构建 DAG 并发执行。

### 11.3 暂不实现的原因

**ThreadLocal 上下文丢失问题**：

并发执行时线程切换会导致：

- 数据权限插件无法获取当前用户信息
- 事务上下文丢失
- 其他 ThreadLocal 依赖的组件失效

**解决方案对比**：

| 方案                        | 说明            | 问题                    |
|---------------------------|---------------|-----------------------|
| 手动传递上下文                   | 提交任务前捕获，执行时恢复 | 需要知道所有 ThreadLocal 类型 |
| DelegatingSecurityContext | Spring 官方包装器  | 只解决 Security，其他不管     |
| TransmittableThreadLocal  | 阿里 TTL，自动传递   | 引入新依赖，侵入性强            |

**收益有限**：

- **CrossSourceRewriteStep 可以并发**（预查询是主要开销）
- **PopulateStep 可以并发**（关联查询是主要开销）
- **但 MainQueryStep 和 ApplyTransformersStep 必须等待前置 Step 完成**
- 实际并发收益：多个预查询 + 多个关联查询可并发，但需要解决 ThreadLocal 问题，复杂性显著增加

**当前方案**：

- 采用 SequentialPlan，顺序执行，简单可靠
- Step 设计已为并发预留空间（延迟转换机制）
- 将来可实现 ParallelPlan，Step 代码无需修改

### 11.4 结论

当前只实现 `SequentialPlan`，按顺序执行。未来如需并发：

1. 通过 Java default 方法添加 `getDependencies()`，不破坏已有实现
2. 新增 `ParallelPlan` 实现
3. 解决 ThreadLocal 传递问题

---
