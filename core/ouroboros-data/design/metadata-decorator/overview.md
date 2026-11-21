# 元数据装饰器机制

元数据装饰器在创建数据模型时补充字段、约束与插件描述符。正式接口为 [DataModelMetaDecorator](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelMetaDecorator.java)，装配入口为 [EnhancedDataModelProxy](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/EnhancedDataModelProxy.java)。本页说明当前生命周期及扩展约束，不复制实现全文。

## 1. 生命周期与责任边界

[AbstractDataStation.setModelMetas](../../ouroboros-data-core/src/main/java/com/ouroboros/data/station/AbstractDataStation.java) 把原始元数据和模型工厂交给代理，工厂创建顶层类 [DefaultDataModel](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DefaultDataModel.java)。执行次序为：

```text
原始 DataModelMeta
  → EnhancedDataModelProxy 的元数据构造入口
  → DataModelMetaDecorator.applyDecorators(originalMeta)
  → ImmutableDataModelMeta
  → 收集元数据中的插件描述符与调用方额外描述符
  → dataModelCreator.apply(decoratedMeta)
  → DataModelValidators.validate(coreDataModel)
  → 保存核心模型并初始化插件链
```

装饰后的元数据先封装为不可变模型，再交给核心模型工厂。装饰器抛错、模型创建失败或校验失败都会中止该构造过程；不会把失败的装饰步骤跳过后继续创建模型。

[DataModelMetaCenter](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelMetaCenter.java) 负责从 `DataModelMetaProvider` 查找、聚合定义，不是元数据装饰的装配点。新增模型能力应走上述代理构造链，不在 provider、station 或核心模型构造中重复调用装饰器。

`EnhancedDataModelProxy(DataModel rawDataModel, Collection<PluginDescriptor>)` 是给已有模型追加插件的另一构造入口，保留原模型，不重新装饰元数据。不要把它与接收原始元数据的构造入口混为一谈。

## 2. SPI 与执行顺序

装饰器实现两个方法：

| 方法 | 输入与约束 |
| --- | --- |
| `supports(DataModelMeta originalMeta)` | 判断是否适用；框架对所有装饰器都传入最初的原始元数据 |
| `decorate(DataModelMeta meta)` | 消费上一装饰器返回的累积结果，返回供下一步使用的元数据 |

`applyDecorators()` 通过 [DataServices](../../ouroboros-data-core/src/main/java/com/ouroboros/data/util/DataServices.java) 获取缓存的逆优先级服务流。底层使用 `ServiceLoader`，按 `jakarta.annotation.Priority` 数值从高到低执行，未标注者按 0；不能把 SPI 文件中的文本排列当作执行顺序保证。

因为 `supports()` 始终检查原始输入，不能依赖前一装饰器新增的字段或标记来决定本装饰器是否启用。`decorate()` 则必须保留前序步骤的合法修改，避免从旧副本重新构造而丢失字段或插件。

模块资源文件 `META-INF/services/com.ouroboros.data.model.DataModelMetaDecorator` 注册实现全限定名。现有 [plugins 模块注册文件](../../ouroboros-data-plugins/src/main/resources/META-INF/services/com.ouroboros.data.model.DataModelMetaDecorator) 包括 `PrimaryKeyDecorator`、`AuditFieldsDecorator`、`Scd2HistoryDecorator`、`LogicalDeleteDecorator`；实际装配还取决于 classpath 上其他模块的同名服务资源。

## 3. 元数据修改与不可变边界

需要修改字段、约束或配置时，装饰器应使用 [DataModelMeta.deepCopy()](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelMeta.java) 或遵守其隔离约定的副本，避免污染 provider 共享的原始定义。

当前深拷贝涉及 `extraProps`、主键列表、唯一约束、字段和插件描述符；字段附加属性由 [DataModelFieldMeta](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/DataModelFieldMeta.java) 拷贝，插件配置由 [PluginDescriptor](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/PluginDescriptor.java) 拷贝。新增可变属性时，应同时核对拷贝和不可变封装，不能只增加字段声明。

代理以 [ImmutableDataModelMeta](../../ouroboros-data-core/src/main/java/com/ouroboros/data/model/ImmutableDataModelMeta.java) 封装装饰结果，额外插件描述符也在进入插件链前封装。运行期能力通过 `DataModelPluginBuilder` 等正式插件机制装配，不应反向修改已封装的模型定义。

## 4. 扩展步骤与设计约束

1. 确认能力属于创建时元数据增强；运行期操作拦截应复用数据模型插件机制。
2. 实现 `DataModelMetaDecorator`，仅依赖原始元数据判断 `supports()`，在 `decorate()` 中接续累积结果。
3. 用副本补充字段、约束或插件描述符；明确同名字段、重复调用及已有配置的处理，不假设所有存量装饰器天然幂等。
4. 在提供该能力的模块中注册 SPI；确有先后依赖时声明并验证优先级，避免依赖发现顺序。
5. 通过正式代理链验证最终元数据、核心模型校验和插件行为，同时验证不适用模型及原定义未被污染。

[实现示例](examples.md) 展示简化的装饰模式，不是内置类名、完整可编译实现或默认注册清单。实际扩展优先参考 [AuditFieldsDecorator](../../ouroboros-data-plugins/src/main/java/com/ouroboros/data/model/decorators/AuditFieldsDecorator.java) 等已注册实现。

## 5. 行为验证入口

- [DataModelMetaDecoratorTest](../../ouroboros-data-plugins/src/test/java/com/ouroboros/data/model/decorators/DataModelMetaDecoratorTest.java)：审计字段、逻辑删除以及不满足条件时的结果。
- [Scd2HistoryDecoratorTest](../../ouroboros-data-plugins/src/test/java/com/ouroboros/data/model/decorators/Scd2HistoryDecoratorTest.java)：SCD2 元数据增强。
- [TypedPluginAnnotationDecoratorTest](../../ouroboros-data-plugins/src/test/java/com/ouroboros/data/model/annotations/TypedPluginAnnotationDecoratorTest.java)：注解到插件描述符的转换。

执行方式沿用 [项目测试规范](../../../../docs/rules/testing.md)。测试文件存在只说明已有验证资产；新增装饰器仍需验证其实际行为、组合顺序和失败分支。本页不承载单次测试运行记录。
