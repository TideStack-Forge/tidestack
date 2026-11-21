# Orchestration 策略边界

策略由[共同设计](overview.md)定义，本页集中说明选择条件、成本与扩展限制；输入示例见[查询示例](examples.md)，验证维度见[场景矩阵](scenarios.md)。

## Populate 选择

DefaultPopulateStrategySelector.select(PopulateField) 按下列顺序选择：

1. sourceModel 或 relationType 缺失，回退 Separate。
2. JoinCapabilities 判定不可 JOIN，使用 Separate。默认按 DataStation 判断，自定义 JoinCapability 可扩展此判断。
3. 有 children 或 nestedPopulate，使用 Separate。
4. ToMany 使用 Separate，避免主记录因多行 JOIN 重复。
5. 可 JOIN 的 ToOne、无子级，使用 JOIN；其他情况回退 Separate。

JOIN 通过 PopulateJoinRewriter 增加 LEFT JOIN 和投影别名，再通过转换器恢复嵌套结果。Separate 从主结果取关联键，批量查询后登记转换器；多层填充由关联模型递归处理。转换器最终由 ApplyTransformersStep 统一应用。

JOIN 减少查询往返，但实际性能依赖投影宽度、索引和执行计划；Separate 需额外查询和内存合并。两次查询只是单个简单填充的量级示意，多字段、递归和分批会增加次数，不能宣称所有场景恒为两次。

## 关联条件

| 条件 | 编排处理 | 边界 |
| --- | --- | --- |
| 同源 ToOne | JoinStatementRewriter | INNER/LEFT 由 NULL 保留语义决定；不能一律 INNER |
| 同源 ToMany | ExistsStatementRewriter 与协调器 | ANY/NONE/ALL 对应满足/反例集合及 EXISTS/NOT EXISTS |
| 跨源关系 | CrossSourceRewriteStep + CrossSourceConditionRewriter | 先查关联键，再改写主条件；NONE/ALL 不能按正向 ANY 简化 |

IN 结果会按 maxInListSize 拆分表达式，不能据此推断预查询流式执行或限制全部内存。空集合与单值改写依具体关系语义处理；尤其否定条件的空集合不等价于始终无结果。

## JOIN 去重

JoinDeduplicator 按目标表和规范化 ON 条件分组，等价组优先 LEFT JOIN，再重写 WHERE/SELECT 别名并记录映射。它保留子查询 JOIN，不处理 ORDER BY 别名。不同关联键或不同 ON 条件的同表 JOIN 不能合并。不要将“同表”作为充分的等价条件。

## 执行与扩展

SequentialPlan 顺序执行步骤，首个异常转为失败结果，最终读取指定 finalStepName。延迟登记改写器和转换器为未来调度提供空间；当前默认流程没有并发执行或预查询结果缓存。缓存、并发和批量阈值优化需要场景测量与独立设计，不能视为现有能力。

PopulateStrategySelector 接口方法是 `PopulateStrategy select(PopulateField populate)`，不是旧示例的枚举返回或 `(sourceModel,targetModel,...)` 签名。DefaultQueryOrchestrator 创建时通过既有 DataServices/ServiceLoader 选择实现，使用 `jakarta.annotation.Priority` 数值最高的已注册实现；未注册时使用 DefaultPopulateStrategySelector。选择器在该编排器实例内复用，不按查询重新发现。接入沿 `META-INF/services` 与原 DataServices 机制完成。

JoinCapability 等正式可消费 SPI 仍按各自机制注册。自定义选择器实现须在 META-INF/services/com.ouroboros.data.orchestration.strategy.PopulateStrategySelector 注册并配套行为测试，不能依赖文档中的伪代码构造另一套查询执行器。
