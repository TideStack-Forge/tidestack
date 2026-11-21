# Ouroboros Data 模块设计概要

## 1. 概述

`ouroboros-data` 是 Ouroboros 低代码平台的核心数据模块，提供动态数据模型系统，支持运行时定义数据结构、自动生成数据库架构、类型安全的动态查询语言以及多数据源适配。

### 核心特性

- **动态数据模型**：支持运行时定义和修改数据模型，无需重启应用
- **统一查询DSL**：基于 S 表达式的查询语言，屏蔽底层数据源差异
- **多数据源适配**：当前提供 SQL/JDBC 适配器；非 SQL 数据源可通过适配器 SPI 扩展
- **插件化架构**：基于 SPI 的可扩展设计，支持业务逻辑扩展
- **类型安全**：类型化 Java API 提供编译期约束；Map/List DSL 在运行期规范化与校验
- **Spring Boot 集成**：提供完整的自动配置和生命周期管理

## 2. 模块结构

| 子模块                                                | 功能描述                          |
|----------------------------------------------------|-------------------------------|
| **ouroboros-data-core**                            | 核心抽象、查询执行链与 `DataModel.query()` 链式查询     |
| **ouroboros-data-plugins**                         | data 域内置插件与元数据装饰器实现              |
| **ouroboros-data-sql**                             | SQL 数据库适配器实现                 |
| **ouroboros-data-sql-migration**                   | SQL schema migration / DDL / Liquibase 实现 |
| **ouroboros-data-builders**                        | 已弃用的查询与模型元数据构建器；新代码使用 core API          |
| **ouroboros-data-runtime**                         | 公共 Spring/JDBC 数据模型运行时          |
| **ouroboros-data-platform-adapter**                | Ouroboros 平台生命周期、安全、脚本适配       |
| **ouroboros-data-typed-core**                      | 类型化模型纯注解与平台无关 contract          |
| **ouroboros-data-typed-runtime**                   | 类型化模型 Spring 扫描与运行时             |
| **ouroboros-data-sql-druid-datasource**            | Druid 数据源集成                   |
| **ouroboros-data-pkgen-coding**                    | 编码主键接口与序列 contract             |
| **ouroboros-data-test**                            | 测试项目                          |
| **ouroboros-data-test-support**                    | 测试支持工具（内存数据站点等）               |
| **ouroboros-data-dev**                             | 开发工具模块                        |

### 2.1 子模块补充说明

- **ouroboros-data-plugins**：data 域公共内置插件与 metadata decorator 实现；当前承载 data-model 插件/装饰器和 ArchiveDelete 支撑契约，运行时通过依赖它保持默认 SPI 行为。
- **ouroboros-data-runtime**：公共 Spring/JDBC 运行时，承载 DataSource/DataStation 自动配置、编码主键 DB 实现、snowflake factory 和公共 expression adapter。
- **ouroboros-data-platform-adapter**：Ouroboros 平台适配层，承载 lifecycle/security/script/JS host access glue；需要完整平台行为的消费者应同时依赖它和 `ouroboros-data-runtime`。
- **ouroboros-data-typed-core / ouroboros-data-typed-runtime**：类型化模型 contract 与 Spring 扫描运行时；不保留旧 artifact 兼容壳。

## 3. 架构设计

### 3.1 分层架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        应用层 (Applications)                      │
│  ouroboros-develop, ouroboros-mothership, business modules      │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│                     数据模型层 (DataModel)                        │
│            DataModel, AbstractDataModel, plugins                │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│                     查询处理层 (Query Processing)                 │
│ normalize → transform → analyze → orchestration → transpile+execute │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│                    数据适配层 (DataAdapter)                       │
│              DataAdapter, SqlDataAdapter, adapter SPI          │
└─────────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────────┐
│                   数据源层 (Data Sources)                         │
│                JDBC DataSource（SQL 方言由适配器处理）                 │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心接口体系

#### DataAdapter - 数据适配器

```java
public interface DataAdapter {
    // DML / QueryStatement
    Try<String> insert(InsertStatement statement);
    Try<String> insert(InsertStatement statement, TranspileContext context);
    Try<Long> update(UpdateStatement statement);
    Try<Long> update(UpdateStatement statement, TranspileContext context);
    Try<Long> delete(DeleteStatement statement);
    Try<Long> delete(DeleteStatement statement, TranspileContext context);
    Try<RecordList> query(QueryMetadata queryMetadata);
    Try<RecordList> query(QueryStatement statement);
    Try<RecordList> query(QueryStatement statement, TranspileContext context);
    Try<Long> count(QueryMetadata queryMetadata);
    Try<Long> count(QueryStatement statement);
    Try<Long> count(QueryStatement statement, TranspileContext context);

    // Map / 便捷重载
    Try<String> insert(Map<String, ?> insertStatement);
    Try<String> insert(String entityName, Map<String, ?> data);
    Try<List<String>> batchInsert(BatchInsertStatement batchInsertStatement);
    Try<List<String>> batchInsert(Map<String, ?> batchInsertStatement);
    Try<List<String>> batchInsert(String entityName, List<Map<String, ?>> dataList);
    Try<Long> update(Map<String, ?> updateStatement);
    Try<Long> update(String entityName, Map<String, ?> where, Map<String, ?> data);
    Try<Long> delete(Map<String, ?> deleteStatement);
    Try<Long> delete(String entityName, Map<String, ?> where);
    Try<RecordList> query(Map<String, ?> queryStatement);
    Try<RecordList> query(String entityName, Map<String, ?> whereMap);
    Try<Long> count(Map<String, ?> whereMap);

    // 插件扩展
    DataAdapter withPlugin(DataAdapterPlugin plugin);
}
```

#### DataModel - 数据模型

```java
public interface DataModel {
    // 元数据访问（节选）
    String getFormatVersion();
    String getSource();
    String getName();
    String getFullName();
    String getNamespace();
    String getLabel();
    String getDescription();
    String getRawName();
    List<DataModelField> getFields();
    Optional<DataModelField> getField(String name);
    List<DataModelField> getPrimaryKeys();
    List<DataModelUniqueConstraintMeta> getUniqueConstraints();
    PrimaryKeyGenerator<?> getPrimaryKeyGenerator();
    MigrationStrategy getMigrationStrategy();
    Map<String, Object> getExtraProps();
    Optional<Object> getExtraProp(String name);

    // 数据访问
    DataAdapter getAdapter();
    DataStation<?> getDataStation();

    // CRUD / Query（节选）
    Try<Record> insert(Map<String, Object> data);
    Try<Record> insertOrUpdate(Map<String, Object> data);
    Try<RecordList> batchInsert(List<Map<String, Object>> dataList);
    Try<RecordList> batchInsertOrUpdate(List<Map<String, Object>> dataList);
    Try<Long> update(Object id, Map<String, Object> data);
    Try<Long> update(List<?> ids, Map<String, Object> data);
    Try<Long> update(Map<String, Object> where, Map<String, Object> data);
    Try<Long> delete(Object id);
    Try<Long> delete(List<?> ids);
    Try<Long> delete(Map<String, Object> where);
    Try<Long> count(Map<String, Object> where);
    Try<Record> get(Object id);
    Try<Record> get(Object id, Map<String, Object> statement);
    Try<RecordList> query(List<?> ids);
    Try<RecordList> query(QueryStatement statement);
    Try<RecordList> query(Map<String, Object> statement);
    Try<RecordList> query(List<String> select, Map<String, Object> where);

    // 插件管理
    DataModel withPlugins(Collection<PluginDescriptor> pluginDescriptors);
    DataModel withoutPlugins(Collection<String> pluginNames);
    DataModel withoutPlugins();
    boolean hasPlugin(String name);
}
```

批量插入合同：

- `batchInsert` 的返回记录与输入记录一一对应，顺序稳定；单主键模型在数据库生成主键时也要能把权威返回映射回提交项。

#### DataStation - 数据站点

```java
public interface DataStation<T> {
    String getName();
    DataAdapter getDataAdapter();
    Optional<DataModel> getDataModel(String modelName);
    List<DataModel> getDataModelList();
    T getDataSource();
}
```

#### Record / RecordList - 数据记录

```java
public interface Record extends Map<String, Object> {
    static Record of(Map<String, ?> map);
    default <T> T toBean(Class<T> clazz);
}

public interface RecordList extends List<Record> {
    static RecordList of(List<? extends Map<String, ?>> list);
    static RecordList empty();
    default <T> List<T> toBeanList(Class<T> clazz);
}
```

### 3.3 核心组件层次关系

DataStation、DataAdapter、DataModel 三者构成了数据访问的核心层次结构：

```
┌─────────────────────────────────────────┐
│       DataModel（业务抽象层）            │  ← 最高层：提供业务级CRUD操作
│   - 数据验证和类型转换                   │
│   - 插件链扩展                          │
│   - 元数据管理                          │
├─────────────────────────────────────────┤
│      DataStation（资源管理层）           │  ← 中间层：统一资源管理
│   - 管理 DataAdapter 实例               │
│   - 管理 DataModel 注册表               │
├─────────────────────────────────────────┤
│      DataAdapter（数据访问层）           │  ← 底层：统一数据访问接口
│   - 执行具体 CRUD 操作                  │
│   - 查询语句转译和执行                   │
│   - 插件机制支持                        │
└─────────────────────────────────────────┘
```

#### 依赖关系

```java
// 1. DataModel 通过构造函数接收 DataStation
public class DefaultDataModel extends AbstractDataModel {
    private final DataStation<?> dataStation;
    private final DataAdapter adapter;

    public DefaultDataModel(DataModelMeta meta, DataStation<?> dataStation) {
        super(meta);
        this.dataStation = dataStation;
        this.adapter = dataStation.getDataAdapter(); // 通过 DataStation 获取 DataAdapter
    }
}

// 2. DataStation 负责创建和管理 DataModel 实例
public abstract class AbstractDataStation<T> implements DataStation<T> {
    protected void setModelMetas(Collection<DataModelMeta> modelMetas) {
        var dataModels = modelMetas.stream()
            .map(meta -> new EnhancedDataModelProxy(meta,
                decoratedMeta -> new DefaultDataModel(decoratedMeta, this))) // 传入 this
            .collect(Collectors.toList());
        setDataModels(dataModels);
    }
}
```

#### 调用链路

典型的数据操作调用链路：

```
Application
    ↓
DataStation.getDataModel("User")     // 获取数据模型
    ↓
DataModel.insert(data)               // 业务操作
    ↓
DataModel.getAdapter().insert(...)   // 内部调用 DataAdapter
    ↓
DataAdapter → DataSource             // 执行实际数据库操作
```

#### 类层次结构

```
DataModel (interface)
├── AbstractDataModel (abstract)
│   └── DefaultDataModel (concrete)
└── EnhancedDataModelProxy (decorator)

DataStation<T> (interface)
└── AbstractDataStation<T> (abstract)
    └── SqlDataStation (concrete)

DataAdapter (interface)
└── AbstractDataAdapter (abstract)
    └── SqlDataAdapter (concrete)
```

#### 设计原则

- **单一职责**：DataModel 专注业务逻辑，DataStation 专注资源管理，DataAdapter 专注数据访问
- **依赖倒置**：DataModel 不直接依赖 DataAdapter，通过 DataStation 间接获取
- **松耦合**：各层通过接口交互，具体实现可替换

## 4. 查询 DSL 系统

### 4.1 表达式类型

**Map 表达式**（高效便捷）：

```java
Map<String, Object> query = Map.of(
    "select", List.of("id", "name", Map.of("userAge", "age")),
    "from", "user",
    "where", Map.of(
        "department", 1001,
        "age", Map.of(">=", 18)
    ),
    "order", List.of("name ASC"),
    "limit", 10
);
```

**空格别名简写**：

```java
// 空格别名简写 —— 字段名与别名之间用空格分隔
"select", List.of("id", "name nickname", "gender sex"),
// 等价于 Map 别名形式：List.of("id", Map.of("nickname", "name"), Map.of("sex", "gender"))
```

**S 表达式**（强表达力）：

```java
List<Object> sQuery = List.of("and",
    List.of("=", List.of("department"), 1001),
    List.of(">=", List.of("age"), 18),
    List.of("=", List.of("year", List.of("createdAt")),
            List.of("year", List.of("now")))
);
```

### 4.2 查询处理流程

DataModel 查询管线当前可概括为 5 个阶段：

```
原始查询Map → Normalize → Transform → Analyze → Orchestration → Transpile+Execute
```

1. **Normalize 阶段**：
    - **Normalize** (`QueryNormalizeContext`)：原始查询 Map → 标准化 `QueryStatement`
    - 负责 Clause / RawExpression / SExpression 三层规范化
    - 默认链由 `QueryNormalizeContext.builder().withDefaultNormalizers()` 组装，可扩展插入自定义 normalizer

2. **Transform 阶段**：
    - **Transform** (`QueryTransformer`)：对 `QueryStatement` 做 Analyze 前后的显式改写
    - 当前默认转换器包括 `WildcardExpandAnalyzer` 和 `OptimizationAnalyzer`
    - 当前实现中，`query(Map)` 路径会先做 wildcard expansion，再 Analyze，再做 optimization

3. **Analyze 阶段** (`QueryAnalyzer`)：
    - 对标准化后的 `QueryStatement` 做语义分析与验证
    - 当前默认分析器包括 `TypeCheckAnalyzer` 与 `RelationExpandAnalyzer`

4. **Orchestration 阶段** (`QueryOrchestrator`)：
    - 输入：QueryStatement + AnalysisResult + 模型元数据
    - 职责：SELECT 展开/OMIT 应用、POPULATE 填充编排、关联模型发现、JOIN 策略决策、查询计划生成
    - 输出：OrchestrationContext（含执行计划和 TranspileContext）

5. **Transpile+Execute 阶段** (`QueryTranspiler`)：
    - 输入：QueryStatement + TranspileContext
    - 输出：QueryDSL QueryMetadata → SQL 执行 → RecordList
    - 责任链：WithBuilder → UnionBuilder → ... → PaginationBuilder

### 4.3 核心组件

- `QueryStatement` - 标准化查询表示
- `SExpression` - S 表达式系统，支持复杂条件表达
- `RawExpressionNormalizer` - 原始表达式规范化
- `SExpressionNormalizer` - Normalize 阶段的表达式级规范化契约
- `QueryNormalizeContext` - Normalize 上下文，Builder 模式创建，固定 normalizer 链
- `QueryTransformer` - Analyze 前/后的语句变换契约
- `DefaultQueryOrchestrator` - 编排器，负责 SELECT 展开/OMIT 应用、POPULATE 填充编排、关联模型发现、查询计划生成
- `OrchestrationContext` - 编排上下文，承载查询计划和 TranspileContext
- `TranspileContext` - 转译上下文接口，提供路径解析和转译器访问
- `ClauseTranspiler` - Transpile 阶段的子句级转译契约
- `DelegatingTranspileContext` - 装饰器基类，统一委托模式（Join/ColumnAlias/CTE 均继承此类）

### 4.4 包结构

```
com.ouroboros.data
├── normalize/       # Normalize 阶段：原始表达式 → SExpression → QueryStatement
│   ├── normalizers/          # ClauseNormalizer（12 个，含 PopulateOmitNormalizer）
│   ├── expressionnormalizers/ # RawExpressionNormalizer（2 个：Map/List）
│   └── builders/             # SExpressionNormalizer 默认实现（8 个，兼容桥仍保留 SExpressionBuilder 命名）
├── analyze/         # Transform + Analyze：语义分析与语句变换
├── orchestration/   # Orchestration 阶段：查询编排、JOIN 策略、计划生成
│   ├── step/        # 编排步骤
│   ├── strategy/    # JOIN 策略
│   ├── rewriter/    # 查询重写器
│   └── ...          # StatementPreparer / RewriteCoordinator 等辅助组件
├── transpile/       # Transpile 阶段：QueryStatement → QueryDSL
│   ├── clausebuilder/ # ClauseTranspiler 默认实现（11 个）
│   └── transpilers/   # SExpressionTranspiler（20 个，SPI 注册）
├── dsl/             # 类型定义（SExpression、Keyword、Operators、Statement）
├── model/           # DataModel 实现
├── adapter/         # DataAdapter 实现
├── station/         # DataStation 实现
├── record/          # Record/RecordList
├── validation/      # 验证规则
└── util/            # 工具类
```

## 5. 类型系统

### 5.1 值类型定义

```java
public interface ValueType<T> {
    String getName();
    String getLabel();
    default Boolean canPopulate() { return false; }
    Class<T> getType();
    T convert(Object value);
    default Object toPersistentValue(Object value); // 持久化值转换
    default Boolean isPhysical() { return true; }
}
```

**内置数据类型**：

- 基础类型：`StringValue`、`IntegerValue`、`LongValue`、`ShortValue`、`BooleanValue`
- 日期时间：`DateTimeValue`、`DateValue`、`TimeValue`
- 数值类型：`DecimalValue`、`DoubleValue`、`FloatValue`
- 复合类型：`JsonValue`、`MapValue`、`ListValue`、`ModelValue`、`CollectionValue`

### 5.2 验证规则系统

```java
public interface Rule {
    ValidationResult validate(Object value, ValidationContext context);
}
```

**内置规则**：`NotNull`、`NotEmpty`、`RegexPattern`

## 6. 插件系统

### 6.1 数据模型插件

```java
public interface DataModelPlugin {
    default Try<Record> insert(Map<String, Object> data, DataModelPluginContext context) {
        return context.getNextPlugin().insert(data, context.getNextPluginContext());
    }
    // 以下为方法签名节选；默认实现同样通过 context 委托下一插件
    Try<RecordList> batchInsert(List<Map<String, Object>> dataList, DataModelPluginContext context);
    Try<Long> update(SExpression<Boolean> where, Map<String, Object> data, DataModelPluginContext context);
    Try<Long> delete(SExpression<Boolean> where, DataModelPluginContext context);
    Try<Long> count(QueryStatement statement, DataModelPluginContext context);
    Try<RecordList> query(QueryStatement statement, DataModelPluginContext context);
}
```

**内置插件**：

| 插件名                    | 类名                                    | 功能            |
|------------------------|---------------------------------------|---------------|
| `UpdateWhenDataChange` | `UpdateWhenDataChangeDataModelPlugin` | 数据变化时自动更新审计字段 |
| `NormalizeWhere`       | `NormalizeWhereDataModelPlugin`       | 规范化查询条件，移除空值  |
| `DuplicateDataChecker` | `DuplicateDataCheckerDataModelPlugin` | 唯一性约束检查       |
| `referenceChecker`     | `ReferenceCheckerDataModelPlugin`     | 外键引用完整性检查     |
| `LogicalDelete`        | `LogicalDeleteDataModelPlugin`        | 将删除改写为逻辑删除更新 |
| `ArchiveDelete`        | `ArchiveDeleteDataModelPlugin`        | 先复制到回收站再执行删除 |
| `Scd2History`          | `Scd2HistoryDataModelPlugin`          | SCD2 历史版本记录 |

#### `Scd2History` 插件

`Scd2History` 是面向数据模型的历史版本插件，用于把源模型的每次增删改同步写入独立历史模型，并在历史模型中维护 `validFrom` / `validTo` / `isCurrent` 版本链。

当前约定：

- 业务模型层只通过 `enableScd2History=true` 开启能力，并自动投影生成并列历史模型
- data-core 运行时仍消费规范化后的 `scd2HistoryConfig.historyModelFullName` 等配置，不负责创建模型或表结构
- 历史模型默认自己维护 `id`，源模型主键默认写入 `businessKey`
- 源模型主键不会再以同名快照字段复制到历史模型
- `changedFields` / `changeSet` 仅在 `storeDiff=true` 时落库
- 为兼容权限和删除策略语义，插件顺序应位于 `AccessControl` 之后、`LogicalDelete` / `ArchiveDelete` 之前

专题设计见：[`design/scd2-history-plugin.md`](design/scd2-history-plugin.md)

#### Typed data model 插件注解

高代码 typed data model 可以通过插件注解声明数据模型能力。注解只参与元数据编译，运行时仍消费正式 `DataModelMeta` / `DataModelFieldMeta`、`PluginDescriptor`、`DataModelMetaDecorator` 和 `DataModelPluginBuilder` 这条主链路；模型级唯一约束使用 `DataModelMeta.uniqueConstraints`，不再把联合唯一塞进 `extraProps.uniqueConstraints`。

专题设计见：[`design/typed-plugin-annotations.md`](design/typed-plugin-annotations.md)

#### 写入上下文与锁定查询

[EnhancedDataModelProxy](ouroboros-data-core/src/main/java/com/ouroboros/data/model/EnhancedDataModelProxy.java) 在原写入口调用 `DataModelExecutionContext.beforeWrite` 并检查只读上下文；插件和核心模型仍走同一条分发链。开启变更跟踪时，update/delete 先沿 `queryForUpdate` 取得锁定目标，要求单一、非空且唯一的主键，并核对实际写入行数后记录变更。相同值 UPDATE 的零变更不产生虚假写集；跟踪失败保持失败状态，不能作为成功事件依据。

[DataModelPlugin.queryForUpdate](ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelPlugin.java) 默认委托下一插件，代理与尾插件保留原查询来源/语句校验。新增权限或过滤插件需要同时考虑普通查询和锁定查询，不能让锁定读绕过原插件链。是否有有效事务与底层锁保证仍需具体数据站点/适配器证据；不能只因方法存在就推定任意数据源支持行锁。业务 Action 的事务参与及审计归属由[业务模型设计](../ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)维护。

### 6.2 数据适配器插件

```java
public interface DataAdapterPlugin {
    // 标记接口，用于扩展数据适配器功能
}
```

### 6.3 元数据装饰器

```java
public interface DataModelMetaDecorator {
    boolean supports(DataModelMeta originalMeta);
    DataModelMeta decorate(DataModelMeta meta);
}
```

**内置装饰器**：

- `PrimaryKeyDecorator`：主键字段装饰
- `AuditFieldsDecorator`：审计字段装饰（创建时间、更新时间等）
- `LogicalDeleteDecorator`：逻辑删除装饰（`enableSoftDelete=true` 时补充逻辑删除字段，并生成 `LogicalDelete` 插件描述符）

### 6.4 显式删除插件

删除能力以数据模型插件描述符作为运行时入口，并保持底层物理删除出口不变：

- `delete(id)` 和 `delete(ids)` 会先被规范化为 `where` 条件，再统一进入 `delete(where)` 插件链。
- 逻辑删除路径由业务模型投射写入 `enableSoftDelete` / `softDeleteConfig` 装饰输入，`LogicalDeleteDecorator` 补写 `isDeleted`、`deletedAt`、`deletedBy` 字段并生成 `LogicalDelete` 插件描述符；运行时 `LogicalDeleteDataModelPlugin` 将删除改写为 `update(where, patch)`。
- 归档删除路径由业务模型投射生成并列回收站模型，并直接写入 `ArchiveDelete` 插件描述符，配置键使用 `archiveModel` / `batchSize` / `sourceFieldMappings`；运行时 `ArchiveDeleteDataModelPlugin` 先查询待删记录、组装回收站记录、批量写入回收站模型，再按主键批次回到 `delete(where)` 物理删除出口。
- 数据模型层不再根据中间策略对象自动切换 `LogicalDelete` / `ArchiveDelete`；插件链只执行明确的插件描述符。

回收站模型契约：

- 回收站模型必须完整包含源模型的全部字段；当源字段名与回收站自有字段冲突时，通过 `sourceFieldMappings` 显式映射，例如源模型 `id` 可落为 `sourceId`，避免覆盖回收站行主键。
- 回收站模型必须额外包含 `sourceModel`、`sourceDataStation`、`sourcePrimaryKey`、`deletedAt`、`deletedBy`、`deleteOperationId` 六个字段。
- `sourcePrimaryKey` 存储源模型主键字段的 JSON 序列化结果，`deleteOperationId` 用于标识同一次删除操作产生的回收站记录。

事务协调：

- 删除写路径通过 `DeleteTransactionCoordinator` / `DeleteTransactionCoordinatorProvider` SPI 解耦；该 SPI 是 `ArchiveDelete` 内置插件的支撑契约，归 `ouroboros-data-plugins`，不放回 `data-core`。
- 同一数据源下使用 Spring JDBC 的嵌套事务 / savepoint 语义保护“写回收站 + 删除源数据”。
- 跨数据源场景使用两个本地事务做 best-effort 协调：先提交回收站事务，再提交源数据事务；该语义不是 XA 分布式事务。

## 7. SPI 扩展机制

### 7.1 核心 SPI 接口

| SPI 接口                       | 作用         | 位置                  |
|------------------------------|------------|---------------------|
| `DataAdapterBuilder`         | 数据适配器构建器   | ouroboros-data-core |
| `DataStationBuilder`         | 数据站点构建器（SQL 实现位于 data-sql） | ouroboros-data-core |
| `DataStationProvider`        | 数据站点提供者    | ouroboros-data-core |
| `DataModelPluginBuilder`     | 数据模型插件构建器  | ouroboros-data-core |
| `DeleteTransactionCoordinatorProvider` | ArchiveDelete 删除事务协调器提供者 | ouroboros-data-plugins |
| `DataModelMetaProvider`      | 数据模型元数据提供者 | ouroboros-data-core |
| `DataModelMetaDecorator`     | 数据模型元数据装饰器 | ouroboros-data-core |
| `ValueTypeBuilder`           | 值类型构建器     | ouroboros-data-core |
| `ValueTypeFactory`           | 值类型工厂      | ouroboros-data-core |
| `RuleBuilder`                | 验证规则构建器    | ouroboros-data-core |
| `PrimaryKeyGeneratorFactory` | 主键生成器工厂    | ouroboros-data-core |
| `JoinCapability`             | JOIN 能力判断  | ouroboros-data-core |

### 7.2 查询 DSL 相关扩展点

| 契约 / SPI | 作用 | 接入方式 | 当前数量 |
|------------|------|----------|----------|
| `QueryTranspiler` | 查询语句转译 | SPI | 1 |
| `SExpressionTranspiler` | S 表达式转译 | SPI | 20 |
| `ClauseNormalizer` | Clause 级规范化 | `QueryNormalizeContext` 默认注册 | 12 |
| `RawExpressionNormalizer` | 原始表达式规范化 | `QueryNormalizeContext` 默认注册 | 2 |
| `SExpressionNormalizer` | Normalize 阶段表达式级规范化 | `QueryNormalizeContext` 默认注册 | 8 |
| `QueryTransformer` | Analyze 前/后的语句改写 | `DefaultQueryTransformer` 组合 | 2 |
| `QueryAnalyzer` | 语义分析与验证 | `DefaultQueryAnalyzer` 组合 | 2 |
| `ClauseTranspiler` | Transpile 阶段子句级转译 | `DefaultQueryTranspiler` 链式组装 | 11 |
| `OperatorFactory` | 操作符工厂 | SPI | 1 |
| `OperatorAliasRegister` | 操作符别名注册 | SPI | 1 |

### 7.3 数据库迁移相关 SPI

| SPI 接口                | 作用                | 实现数量 |
|-----------------------|-------------------|------|
| `DataTypeBuilder`     | Liquibase 数据类型构建器 | 15 个 |
| `DataSourceBuilder`   | JDBC 数据源构建器       | 1    |
| `SqlTemplatesFactory` | QueryDSL SQL 模板工厂 | 1    |

### 7.4 扩展示例

适配器构建器消费已建立的 `DataStation`，匹配时返回适配器，不匹配返回 `Optional.empty()`；接口为：

```java
public interface DataAdapterBuilder<T> extends Function<DataStation<T>, Optional<DataAdapter>> {
    Optional<DataAdapter> apply(DataStation<?> dataStation);
}

public interface PrimaryKeyGeneratorFactory {
    Optional<PrimaryKeyGenerator<?>> get(String generatorName);
}
```

实现分别注册到 `META-INF/services/com.ouroboros.data.adapter.DataAdapterBuilder` 和 `META-INF/services/com.ouroboros.data.model.PrimaryKeyGeneratorFactory`。可参考 `SqlDataAdapterBuilder` 和 `SnowflakePrimaryKeyGeneratorFactory`；仓库没有内置 MongoDB 适配器。

### 7.5 JOIN 能力判断机制

用于 Plan 阶段判断两个模型是否可以在同一数据源内执行 JOIN 查询。

#### 核心接口

```java
// JOIN 策略
public enum JoinStrategy {
    NATIVE,    // 同一数据源，可合并查询（具体用 JOIN 还是 SUBQUERY 由 Transpile 决定）
    SEPARATE   // 跨数据源，需分开查询后内存合并
}

// JOIN 能力判断接口
public interface JoinCapability {
    JoinCapabilityResult canJoin(DataModel source, DataModel target);
    default int getPriority() { return 100; }
}

// 判断结果
public class JoinCapabilityResult {
    public static JoinCapabilityResult joinable();           // NATIVE 策略
    public static JoinCapabilityResult notJoinable(String reason);  // SEPARATE 策略
    public boolean isJoinable();
    public JoinStrategy getStrategy();
    public String getReason();
}
```

#### 使用方式

```java
// 判断两个模型是否可以 JOIN
var joinResult = JoinCapabilities.canJoin(sourceModel, targetModel);
if (joinResult.isJoinable()) {
    // NATIVE 策略：在同一数据源内执行
} else {
    // SEPARATE 策略：分开查询后内存合并
    logger.info("无法 JOIN: {}", joinResult.getReason());
}
```

#### 默认实现

`DefaultJoinCapability`：基于 DataStation 名称判断，同一 DataStation 返回 `NATIVE`，否则返回 `SEPARATE`。

#### 扩展方式

实现 `JoinCapability` 接口并注册 SPI：

```java
public class CrossSchemaJoinCapability implements JoinCapability {
    @Override
    public JoinCapabilityResult canJoin(DataModel source, DataModel target) {
        // 自定义判断逻辑，如支持同一数据库实例的跨 Schema JOIN
        if (isSameDatabaseInstance(source, target)) {
            return JoinCapabilityResult.joinable();
        }
        return JoinCapabilityResult.notJoinable("不同数据库实例");
    }

    @Override
    public int getPriority() { return 50; } // 优先级高于默认实现
}
```

## 8. 主键生成器

### 8.1 内置主键生成器

| 类型                | 实现类                            | 说明              |
|-------------------|--------------------------------|-----------------|
| `snowflake`       | `SnowflakePrimaryKeyGenerator` | 雪花算法，生成分布式唯一 ID |
| `fixLengthCoding` | `FixLengthCodingGenerator`     | 固定长度编码，支持自定义规则  |

### 8.2 雪花算法配置

```java
public interface SnowflakeNodeIdProvider {
    long getNodeId();
}
```

通过 `SnowflakeNodeIdProvider` SPI 自定义节点 ID 获取方式。

## 9. Spring Boot 集成

### 9.1 配置属性

默认站点来自 Spring 的 `DataSource`，例如：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/test
    username: user
    password: ${DB_PASSWORD}
    migration-strategy: disabled
```

`SpringDataStationDefineProvider` 使用 `spring.datasource.migration-strategy` 与 `spring.datasource.dialect`（默认 `auto`），方言还可由 `ouroboros.data.sql.dialect` 提供。附加数据源属性由 `ouroboros.additional-datasources` 绑定。类型化模型扫描通过 `@DataModelScan` 与 typed-runtime 接入；没有 `ouroboros.data.models.auto-discovery/scan-packages` 这组绑定。

### 9.2 中心化管理

```java
public class DataModelCenter {
    public static Optional<DataModel> getDataModel(String name);
    public static void refresh();
}

public class DataStationCenter {
    public static Optional<DataStation<?>> getDataStation(String name);
    public static void refresh();
}
```

## 10. 数据库迁移

### 10.1 迁移策略

```java
public enum MigrationStrategy {
    AUTO,      // 自动迁移
    DISABLED   // 禁用迁移
}
```

### 10.2 迁移流程

```
模型变更检测 → 差异分析 → 迁移脚本生成 → Liquibase执行 → 迁移历史记录
```

## 11. 设计模式应用

| 模式         | 应用场景                                        |
|------------|--------------------------------------------|
| **责任链**    | normalize/transpile 两阶段处理、插件系统               |
| **策略**     | 数据适配器、路径解析器、表达式规范化器                         |
| **建造者**    | QueryStatement.Builder、DataModelBuilder、QueryNormalizeContext.Builder |
| **适配器**    | DataAdapter 体系、TranspileContext 体系           |
| **模板方法**   | AbstractDataAdapter、AbstractDataModel        |
| **装饰器**    | DelegatingTranspileContext（Join/ColumnAlias/CTE）、EnhancedDataModelProxy |
| **插件**     | DataModelPlugin、DataAdapterPlugin            |
| **SPI 机制** | 组件自动发现、插件自动装配                               |

## 12. 关键流程

### 12.1 数据插入流程

```
1. DataModel.insert(data)
2. 构建InsertRecord（数据验证、类型转换、主键生成）
3. 插件链处理（重复检查、引用检查）
4. 构建InsertStatement
5. DataAdapter.insert(statement)
6. SQL生成和执行
7. 返回插入结果
```

### 12.2 查询执行流程（DataModel 查询管线）

```
1. DataModel.query(Map statement)
2. Normalize: `QueryNormalizeContext.normalizeQuery(Map → ModelQueryStatement)`
   - 各 `ClauseNormalizer` 按子句类型规范化
   - `PopulateOmitNormalizer` 在此阶段解析 `POPULATE/OMIT`
3. Transform（before analyze）: `DefaultQueryTransformer(WildcardExpandAnalyzer)`
4. Analyze: `DefaultQueryAnalyzer(TypeCheckAnalyzer + RelationExpandAnalyzer)`
5. Transform（after analyze）: `DefaultQueryTransformer(OptimizationAnalyzer)`
6. Orchestration: `QueryOrchestrator.orchestrate(...)`
   - `StatementPreparer` 处理 SELECT 展开 / OMIT 应用 / 主表别名前缀
   - populate planner / selector 处理填充编排
   - relation planner / rewrite coordinator 处理关联条件编排与语句改写
7. Transpile: `QueryTranspiler.applyWithContext(QueryStatement → QueryMetadata)`
8. Execute: `DataAdapter.query(QueryMetadata) → RecordList`
```

### 12.3 模型刷新流程

```
1. OuroborosModelRefreshingEvent事件触发
2. DataStationCenter.refresh()
3. 扫描和注册所有DataStationProvider
4. DataModelCenter.refresh()
5. 收集所有DataModel并建立索引
6. 执行数据迁移（如启用）
```

## 13. 最佳实践

### 13.1 数据模型定义

- 使用清晰的命名空间组织模型
- 合理设计字段类型和验证规则
- 选择合适的主键生成策略

### 13.2 查询性能优化

- 合理使用索引和查询条件
- 避免 N+1 查询问题
- 使用分页查询处理大结果集

### 13.3 插件开发

- 遵循单一职责原则
- 正确处理插件链的传递
- 提供详细的错误信息

### 13.4 扩展开发

- 实现相应的 SPI 接口
- 在 `META-INF/services` 目录注册服务
- 编写完整的单元测试

## 14. 维护与性能边界

性能判断应基于具体查询、数据量、执行计划与测量。多阶段查询管线、`Try` 返回值或子模块数量本身不是性能缺陷的证据，不据此确定合并模块、移除阶段或替换错误合同。

扩展沿现有 SPI、模型插件和编排机制实现。运行时与平台适配层、类型化 contract 与扫描运行时已分离；继续维护这些边界。未闭合问题在[技术债跟踪器](../../docs/tech-debt-tracker.md)记录，单次评审和优化计划归关联任务。

## 15. 演进方向

分库分表、读写分离、分布式事务与流式数据处理属于待论证方向，不是本模块已实现承诺。查询 DSL 的已实现入口、预期行为和限制分别见本设计、[DSL 用例矩阵](design/dsl-test-cases/dsl-test-cases.md)与对应可执行测试；是否通过以目标提交的实际运行结果为准。

## 16. 专题索引

| 专题 | 正文与职责 |
| --- | --- |
| Orchestration | [查询编排](design/orchestration/overview.md)：计划、步骤与执行边界 |
| TranspileContext | [字段作用域](design/transpile-context/overview.md)：上下文链与解析规则 |
| MetadataDecorator | [元数据装饰器](design/metadata-decorator/overview.md)：SPI 与装饰模式 |
| DSL 测试 | [用例与测试方法](design/dsl-test-cases/overview.md)：预期行为及验证归属 |

## 17. 总结

ouroboros-data 模块通过动态数据建模、统一查询 DSL、插件化扩展等核心特性，为低代码平台提供了坚实的数据底座。

**主要优势**：

- 动态性强，支持运行时模型定义
- 扩展性好，插件化架构支持业务定制
- 类型化 API 与动态 DSL 分别提供编译期约束和运行期校验
- 技术先进，采用函数式编程和现代设计模式

**改进空间**：

- 架构需要进一步简化和优化
- 性能需要针对性优化
- 可维护性和测试覆盖率需要提升

---

**文档版本**：1.3
**核对基线**：当前工作树源码与 POM（普通修订历史见 Git）
**适用版本**：ouroboros-data 2.0.0-rc.1-SNAPSHOT

## QueryDSL UNION 适配边界

`OuroborosSQLQuery` 针对当前锁定的 QueryDSL SQL 5.1.0，通过继承使用 `ProjectableSQLQuery` 的 `protected union/firstUnionSubQuery/unionAll` 状态；原审查将其称为 private 不准确。已移除字段反射，版本升级时这三个受保护成员的变化由 Java 编译直接暴露。适配仍限于 SQL adapter，不向业务层暴露第三方状态。

`OuroborosSQLQueryTest` 用真实 JDBC 验证 UNION/UNION ALL 混合、重复值、排序、分页和绑定值，以及 CTE 与 UNION/分页的组合。H2 执行覆盖 H2/MySQL/PostgreSQL 三套模板生成的通用 SQL，不代表真实 MySQL/PostgreSQL 服务端已验证。现有 data 测试另外覆盖 normalize、UNION builder 和 CTE 元数据。

QueryDSL 5.1.0 的 `SQLSerializer.serializeUnion` 不处理 QueryModifiers。分页 UNION 通过正式派生表表达式进入普通 query serializer，由实际 SQLTemplates 负责分页方言；不手写 LIMIT/OFFSET 或反射访问序列化内部字段。CTE 在 UNION 分支构建后统一加入外层，避免主分支重复 WITH。适配边界的升级回归须保留结果集、绑定值及方言测试。
