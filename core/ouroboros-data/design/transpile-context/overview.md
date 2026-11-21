# TranspileContext 与字段作用域

TranspileContext 为转译阶段提供字段、表和表达式转译器解析。调用者通过 resolve API 读取作用域，场景由 Context 链表达，避免把 WHERE、ORDER BY 等位置分支散落到表达式转译器中。查询管线、分析与编排职责见[模块设计](../../DESIGN.md)和[编排设计](../orchestration/overview.md)。

## 正式接口与扩展点

[TranspileContext](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/TranspileContext.java)提供 resolve(field)、resolve(tableOrAlias, field)、resolveTable、getSExpressionTranspiler 和 getQueryTranspiler。找不到字段/表返回 Optional.empty；同优先级来源歧义使用 AmbiguousFieldException。

[DelegatingTranspileContext](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/DelegatingTranspileContext.java)委托未覆盖的方法，包括 QueryTranspiler。定制只覆盖存在差异的方法，不能让子查询意外回到另一套默认转译器。模型与适配器定制沿用 QueryTranspiler 和 Context 的正式入口。

## Context 分工与解析顺序

| Context | 当前解析规则 |
| --- | --- |
| BaseTranspileContext | 主表字段优先；支持 table.field；按接口方法回退父作用域及关联模型来源 |
| JoinTranspileContext | 裸字段先查 delegate，未命中再查 JOIN 来源；多个 JOIN 同时命中报歧义。带表名/别名时先查指定 JOIN 来源 |
| ColumnAliasTranspileContext | 裸字段先查列别名，再委托；带表限定的字段直接委托 |
| CTETranspileContext | 按名称解析 CTE 表及其投影字段；裸字段不遍历所有 CTE，继续委托 |
| SubqueryTranspileContext | 先查本地查询作用域，未命中回退父作用域，支持关联子查询 |
| DummyTranspileContext | 为不依赖完整模型的场景提供显式注册的字段来源 |

尤其注意：裸字段的 JOIN 解析保留主表/delegate 优先，不能把旧稿中的“JOIN 总是先于主表”当作当前规则。实际来源见 [transpile 包](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile)。

## 链的构建与生命周期

Context 随单次查询建立。各子句构建器通过包装或替换当前 Context 传递作用域；顺序决定可见性，不建立跨查询共享的隐藏注册表。

- [FromBuilder](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/clausebuilder/FromBuilder.java)建立主表或子查询来源及父作用域。
- [JoinBuilder](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/clausebuilder/JoinBuilder.java)建立 JOIN 来源与 ON 表达式上下文。
- [WithBuilder](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/clausebuilder/WithBuilder.java)注册 CTE 来源。
- [SelectBuilder](../../ouroboros-data-core/src/main/java/com/ouroboros/data/transpile/clausebuilder/SelectBuilder.java)收集列别名并包装后续上下文。

表达式转译依赖这些上下文解析字段，不承担跨源查询计划和执行调度。相关查询重写、Populate 和计划执行职责由编排专题解释，不在本页复制另一套 Plan 设计。

## 验证边界

[BaseTranspileContextTest](../../ouroboros-data-core/src/test/java/com/ouroboros/data/transpile/BaseTranspileContextTest.java)、[JoinTranspileContextTest](../../ouroboros-data-core/src/test/java/com/ouroboros/data/transpile/JoinTranspileContextTest.java)和[SubqueryTranspileContextTest](../../ouroboros-data-core/src/test/java/com/ouroboros/data/transpile/SubqueryTranspileContextTest.java)验证局部行为；[TranspileContextResolutionIntegrationTest](../../ouroboros-data-test/src/test/java/com/ouroboros/data/integration/TranspileContextResolutionIntegrationTest.java)覆盖执行集成。扩展需要包含同名字段、显式别名、外层引用和未命中场景。

这些入口说明测试归属，不表示当前环境已经运行通过。移类清单、实施步骤与单次修订记录通过 Git 历史和关联任务追溯。
