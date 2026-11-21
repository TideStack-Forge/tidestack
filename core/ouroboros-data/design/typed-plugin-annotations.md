# Typed data model 插件注解

## 1. 背景

typed data model 使用 Java `@Model` / `@Field` 等注解声明高代码数据模型，再由 `TypedModelMetaHelper` 扫描生成 `DataModelMeta`。基础模型元数据已经类型化，但审计字段、软删除、SCD2 历史、归档删除等数据模型能力过去主要依赖 `@Model.extraProps` 字符串数组或 low-code business model 投射。

插件注解提供一层高代码声明语法，把稳定能力翻译为现有数据模型元数据承载点。它不创建新的运行时插件体系，也不复制 low-code business model 的并列模型生成逻辑。

## 2. 设计原则

- Java 注解不支持继承另一个注解作为“插件注解基类”，也不能实现业务接口；统一行为由 `TypedModelAnnotationDecorator` / `TypedFieldAnnotationDecorator` SPI 承担。
- `@TypedModelPluginAnnotation` 是元注解标记，只说明某个注解属于 typed data model 插件注解；它不负责执行装饰逻辑。
- typed 编译阶段只写正式数据模型元数据，例如 `DataModelFieldMeta`、`DataModelMeta.uniqueConstraints`、`DataModelMeta.extraProps`、`DataModelMeta.pluginDescriptors` 或 `DataModelFieldMeta.extraProps`。
- 运行时仍由 `DataModelMetaDecorator.applyDecorators(...)` 和 `DataModelPluginBuilder` 构建插件链。
- 同一个 runtime plugin descriptor 只能有一个生成来源，避免 runtime decorator 根据 `extraProps` 追加 descriptor 时又被 typed annotation decorator 直接写入一次。

## 3. 扩展点

公共 SPI 位于 `ouroboros-data-typed-core`：

```java
public interface TypedModelAnnotationDecorator<A extends Annotation> {
  Class<A> annotationType();
  DataModelMeta decorate(
      Class<?> modelClass,
      A annotation,
      DataModelMeta modelMeta,
      TypedAnnotationDecoratorContext context
  );
  default int getOrder() { return 0; }
}

public interface TypedFieldAnnotationDecorator<A extends Annotation> {
  Class<A> annotationType();
  DataModelFieldMeta decorate(
      Class<?> modelClass,
      Field field,
      A annotation,
      DataModelFieldMeta fieldMeta,
      DataModelMeta modelMeta,
      TypedAnnotationDecoratorContext context
  );
  default int getOrder() { return 0; }
}
```

`ouroboros-data-typed-runtime` 在基础字段和模型元数据构建完成后执行 decorator pipeline：先执行字段 decorator，再执行模型 decorator。每个 decorator 接收当前 metadata 并返回装饰后的 metadata；`TypedAnnotationDecoratorContext` 只提供模型名解析、descriptor owner 声明和异常构造等环境能力，不收集或直接修改 metadata。decorator 通过 `META-INF/services/com.ouroboros.data.model.TypedModelAnnotationDecorator` 或 `META-INF/services/com.ouroboros.data.model.TypedFieldAnnotationDecorator` 注册。

`@DataModelScan` 同样位于 `ouroboros-data-typed-core` 的 `com.ouroboros.data.annotation` 包，但它只是声明扫描范围的纯注解，不 `@Import` 任何 Spring runtime 类。typed Spring runtime 的注册入口由 `TypedDataModelAutoConfiguration` 持有：auto configuration 显式提供 `com.ouroboros.data.typed.runtime.DataModelScanRegistry` bean，由它在 Spring bean definition 阶段发现所有 `@DataModelScan` 配置类并注册 `TypedDataModel` FactoryBean。auto configuration 自身不标注 `@DataModelScan`；框架内部 typed 模型通过 `TypedRuntimeModelScanConfiguration` 明确声明内部扫描包。这样高代码模型只依赖 typed-core 注解契约，不会因为使用 `@DataModelScan` 直接拉入 typed-runtime 注册机制，也不会把 runtime auto configuration 包误当作业务模型扫描范围。

## 4. 内置插件注解

内置 typed 注解位于 `ouroboros-data-typed-core` 的 `com.ouroboros.data.annotation` 包，作为高代码模型可直接依赖的声明契约。对应 decorator 由插件能力所属模块注册：普通 `DataModelMetaDecorator` 位于 `ouroboros-data-plugins` 的 `com.ouroboros.data.model.decorators` 包，typed annotation decorator 位于 `com.ouroboros.data.model.decorators.typed` 子包，并通过 typed decorator SPI 把注解翻译为正式 `DataModelMeta`。

```java
@Model(fullName = "demo.Order")
@AuditFields(createdByRawName = "created_by", createdAtRawName = "created_at")
@LogicalDelete(isDeletedRawName = "is_deleted")
class Order {
  @Field
  private String id;
}
```

`@AuditFields` 只写入 `enableAuditFields=true` 和 `basicAuditConfig`，由 `AuditFieldsDecorator` 追加 `BasicAudit` descriptor。逻辑删除 typed 注解统一使用 `@LogicalDelete`，它只写入 `enableSoftDelete=true` 和 `softDeleteConfig`，由 `LogicalDeleteDecorator` 追加 `LogicalDelete` descriptor。

## 5. 唯一性注解

typed data model 使用 `@Unique` 声明字段级独立唯一，使用可重复的 `@UniqueConstraint` 声明模型级联合唯一。字段级唯一仍是单字段约束；多个字段分别标注 `@Unique` 时，`DuplicateDataChecker` 按多个独立约束处理，语义是 OR。

```java
@Model(fullName = "demo.Node")
@UniqueConstraint(fields = {"projectId", "nodeCode"})
class Node {
  @Field
  private String id;

  @Field
  private String projectId;

  @Field
  private String nodeCode;

  @Field
  @Unique
  private String externalCode;
}
```

`@Unique` 写入字段元数据 `isUnique=true`，可选 `scope` 写入 `DataModelFieldMeta.uniquenessScope`；它不会复制成 `DataModelMeta.uniqueConstraints`，也没有字段级物理 constraint name。`@UniqueConstraint` 写入 `DataModelMeta.uniqueConstraints`，`fields` 使用模型字段名，约束内部按 AND 查重。

`scope=UniquenessScope.DEFAULT` 在元数据中落为空值，由模型默认唯一性范围解析；`@LogicalDelete` 模型默认解析为 `ACTIVE_RECORDS`。字段级 `scope=ALL_RECORDS` 可覆盖逻辑删除默认值并继续生成普通单列 unique DDL；字段级 `ACTIVE_RECORDS` 和所有模型级 `@UniqueConstraint` 本期只做运行期查重，不生成普通复合 unique、`is_deleted + fields` unique、生成列或函数索引。

运行期查重由既有 `DuplicateDataChecker` 消费统一解析后的有效唯一约束集合。模型级约束是 runtime-only 能力，不能当作数据库级并发保障；两个并发写入仍可能同时通过运行期查重，强数据库物化需要按 `UniquenessScope` 和数据库方言另行设计。

旧的 `@Model.extraProps = {"uniqueConstraints", ...}` 不是正式入口，新代码不要继续使用；已有模型应迁移到 `@UniqueConstraint`。

## 6. SCD2 与归档删除

`@Scd2History` 和 `@ArchiveDelete` 必须显式引用已有并列模型。typed 注解模式不会自动生成 history/archive model；自动生成仍属于 low-code business model feature plugin 的职责。

支持 class 和 string 两种形式，二者必须且只能指定一个：

```java
@Model(fullName = "demo.Order")
@Scd2History(historyModel = OrderHistory.class)
class Order {
  @Field
  private String id;
}

@Model(fullName = "demo.OrderHistory")
class OrderHistory {
  @Field
  private String id;
}
```

```java
@Model(fullName = "demo.Customer")
@ArchiveDelete(archiveModelName = "demo.CustomerArchive")
class Customer {
  @Field
  private String id;
}
```

class 形式使用 `Void.class` 作为未设置 sentinel，并要求目标类标注 `@Model` 且在当前 typed model 扫描结果中。模型名优先取目标类 `@Model.fullName()`，为空时退回目标类 simple name。string 形式按完整模型名原样写入，不做类存在性校验。

## 7. Descriptor 生成 owner

部分 `DataModelMetaDecorator` 会根据 `extraProps` 自动生成并追加 runtime plugin descriptor。为避免重复描述符，typed annotation decorator 必须遵守单一 owner 规则：

| 注解 | typed decorator 输出 | descriptor 生成来源 |
|---|---|---|
| `@AuditFields` | `enableAuditFields` / `basicAuditConfig` | `AuditFieldsDecorator` 生成 `BasicAudit` |
| `@LogicalDelete` | `enableSoftDelete` / `softDeleteConfig` | `LogicalDeleteDecorator` 生成 `LogicalDelete` |
| `@Scd2History` | `enableScd2History` / `scd2HistoryConfig` | `Scd2HistoryDecorator` 生成 `Scd2History` |
| `@ArchiveDelete` | `PluginDescriptor("ArchiveDelete", config)` | `ArchiveDeleteAnnotationDecorator` 直接写入 |
| `@DataPlugin` | `PluginDescriptor(name, config)` | `DataPluginAnnotationDecorator` 直接写入 |
| `@Unique` / `@UniqueConstraint` | 字段 `isUnique` / `DataModelMeta.uniqueConstraints` | 不生成 runtime descriptor，由 `DuplicateDataChecker` 读取有效唯一约束 |

专用注解和 `@DataPlugin` 声明同名 descriptor 时会在 metadata compile 阶段 fail-fast。不要同时使用 decorator-backed 注解和 direct descriptor 触发同一个插件，例如 `@AuditFields` + `@DataPlugin(name = "BasicAudit")`、`@LogicalDelete` + `@DataPlugin(name = "LogicalDelete")` 或 `@Scd2History` + `@DataPlugin(name = "Scd2History")`。

## 8. 边界

- typed-core / typed-runtime / data-plugins 不依赖 business-model，也不调用 `BusinessModelFeaturePlugin`。
- SCD2 / ArchiveDelete 的 low-code 自动并列模型生成不迁移到 typed helper。
- `@Model.extraProps` 仍是低频逃生口；已一等化的稳定能力应使用专用 typed 注解，例如唯一性使用 `@Unique` / `@UniqueConstraint`，不要再写 `extraProps.uniqueConstraints`。
- 新增插件注解时应优先新增注解类、decorator 和 SPI 注册，不在 `TypedModelMetaHelper` 中添加每个插件的硬编码分支。
