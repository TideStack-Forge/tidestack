# Orchestration 场景矩阵

本页维护应覆盖的查询行为与实现入口。旧的单次设计评估、评分和实施顺序不作为当前覆盖证据；机制在[共同设计](overview.md)，具体输入见[查询示例](examples.md)。存在实现或测试类不代表目标提交已经验收通过。

## 基础场景

| 场景 | 默认路径 | 需要验证的结果 |
| --- | --- | --- |
| 同源 ToOne Populate，无子级 | JoinPopulateStrategy | LEFT JOIN 后恢复嵌套对象，关联缺失与别名冲突 |
| 同源 ToMany Populate | SeparatePopulateStrategy | 批量查询、按关联键分组、空列表 |
| 跨源 ToOne Populate | SeparatePopulateStrategy | 对应站点查询、单对象合并、缺失关联 |
| 跨源 ToMany Populate | SeparatePopulateStrategy | 多记录分组与主记录边界 |
| 同源 ToOne 条件 | JoinStatementRewriter | 普通条件 INNER JOIN；需保留 NULL 时 LEFT JOIN |
| 同源 ToMany ANY/NONE/ALL | ExistsStatementRewriter 与关系改写协调器 | EXISTS/NOT EXISTS、空集合、NULL 及否定组合 |
| 跨源 ToOne 条件 | CrossSourceRewriteStep + CrossSourceConditionRewriter | 预查询结果改写、零/单/多值与批次边界 |
| 跨源 ToMany ANY/NONE/ALL | 关系条件规划与跨源改写 | 正向/反例集合选择、IN/NOT IN 及空集合 |

## 组合与边界

| 组合 | 核对重点 |
| --- | --- |
| 同字段条件 + Populate | 条件筛选与填充分离，等价 JOIN 合并不改结果 |
| 多个跨源条件 + 多个 Populate | 主查询前预查询与主查询后填充的顺序、失败传播 |
| 嵌套 Populate | 默认 Separate，关联模型继续处理子级，不丢选列/过滤 |
| 多层关联条件 | 递归改写、各层键与别名、OR/NOT 和 NULL 语义 |
| 用户显式 JOIN + 自动 JOIN | 按表及规范化 ON 分组；同表不同关系保留 |
| 自关联 | 别名隔离，不错误合并两种角色 |
| 多对多 | 结合实际关系元数据与中间表路径验证，不能由八类入口推断全部覆盖 |
| 循环引用与深层关系 | 不据递归支持推断已有统一深度限制或循环防护 |
| 跨源大结果 | maxInListSize 分批表达式、内存与参数规模；不是游标流式查询 |
| ORDER BY 使用待合并 JOIN 别名 | JoinDeduplicator 当前不重写 ORDER BY，须明确核对 |

## 验证入口与限制

源路径为 data-core 的 `orchestration` 包；行为测试分布在 data-core 的 `src/test` 与 data-test 的 DSL 集成测试，例如 RelationOperatorIntegrationTest、JoinCapabilityStrategyTest、PopulateOmitIntegrationTest、CompositeQueryIntegrationTest、DslGoldenCaseIntegrationTest。选择方法见[DSL 测试指南](../dsl-test-cases/dsl-test-cases-design.md)。

当前默认使用 SequentialPlan，首个失败终止后续步骤；不承诺并发预查询、自动结果缓存、跨数据源事务或所有组合的性能。编排器创建时通过既有 DataServices 选择最高 Priority 的 PopulateStrategySelector 注册实现，无注册时使用默认实现；选择器在该实例内复用。
