# DSL 测试指南

[用例矩阵](dsl-test-cases.md)定义预期行为。本页说明用例的测试归属、复用设施和验证方法；项目通用要求见[测试规则](../../../../docs/rules/testing.md)。

## 测试归属

| 测试层级 | 归属与现有入口 | 验证内容 |
| --- | --- | --- |
| Normalize 原子行为 | [core Groovy 测试](../../ouroboros-data-core/src/test/groovy/com/ouroboros/data/dsl/normalize) | Map/List DSL 的规范化结果、分支选择、错误输入 |
| 编排与转译 | [core Java 测试](../../ouroboros-data-core/src/test/java/com/ouroboros/data) | 查询计划、作用域、字段歧义及委托边界 |
| 数据执行集成 | [DSL 集成测试](../../ouroboros-data-test/src/test/groovy/com/ouroboros/data/dsl)、[Java 集成测试](../../ouroboros-data-test/src/test/java/com/ouroboros/data/integration) | 真实查询的结果、关联、聚合、子查询与失败行为 |
| 复用设施 | [test-support](../../ouroboros-data-test-support/src/main/java/com/ouroboros/data) | 模型、数据夹具和共享测试支持 |

没有数据库依赖的语义测试放 core；依赖适配器、数据库和模型装配的行为放 test。共享夹具保留在 test-support，避免每个场景创建第二套模型或基础设施。

## 用例与断言

每项测试关联矩阵中的行为或明确场景。正向测试断言可观察的结构化结果或记录内容；错误路径断言失败类型、原因及无副作用。不要以“调用成功”“结果非空”代替具体行为，也不要读取实现源码来判断通过。

Normalize 测试复用该包的 TestQueryNormalizer、TestRawExpressionNormalizer 等现有辅助类。结构化表达式结果本身是被测输出，可以断言其语义结构；这与检查实现源码结构不同。

集成测试复用 [AbstractDslIntegrationTest](../../ouroboros-data-test/src/test/groovy/com/ouroboros/data/AbstractDslIntegrationTest.groovy) 与其父类。该基类提供查询、结果数量和失败消息断言；实际场景还应断言关键字段、排序、去重、边界值和错误后的状态。

Groovy DSL 的 `$` 操作符键使用明确的字符串写法，避免插值改变被测输入。用例需要的模型、关联和数据分布应在既有夹具中表达，并验证零条、一条、多条、空值和重复值的差异。

## Golden 数据与语义变更

已有 Normalize golden 数据在 [core 测试资源](../../ouroboros-data-core/src/test/resources/golden/normalize)，集成 golden 用例由 [DslGoldenCaseIntegrationTest](../../ouroboros-data-test/src/test/groovy/com/ouroboros/data/dsl/DslGoldenCaseIntegrationTest.groovy)消费。先检查实际消费者和格式再增加数据，文件存在不等于已被执行。

预期结果改变时，先核对 DSL 语义、已确认需求与设计，再更新预期和对应测试。禁止直接用新实现的输出覆盖预期以掩盖回归。历史新旧实现对比的性能比例和时点成绩不作为当前阈值。

## 执行与结果

在仓库根按[测试规则](../../../../docs/rules/testing.md)及[正式测试操作入口](../../../../.agents/skills/run-tests/SKILL.md)选择模块、依赖和场景。core 与 test 分开定位失败；集成测试先确认当前数据库配置、迁移和夹具前提。

报告记录提交、环境、命令、实际发现并执行的测试数、失败与跳过；未实现或未运行的矩阵项保留差距。可复用方法留在本页，单次执行日志和覆盖统计归关联任务或 CI。
