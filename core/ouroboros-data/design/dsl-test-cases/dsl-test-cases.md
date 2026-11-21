# 查询 DSL 测试用例


本矩阵持续维护 DSL 的预期行为；机制见[模块设计](../../DESIGN.md)，执行方法见[测试指南](dsl-test-cases-design.md)。`behavior-case` 记录待验证行为；`design-only` 区分尚无正式入口的设计用例。它们不代替当前提交的测试或验收结果。

## 状态标记与实现证据

- `behavior-case`：应验证的行为用例，不表示所有输入、阶段与数据库都已实现或已运行通过。
- `design-only`：尚无对应正式入口的设计用例。
- `⚠️`：保留的预期与实际入口/边界还需定向验证；不能当作已支持语法。

| 当前机制 | 已核对的事实与限制 |
| --- | --- |
| Map 输入 | MapExpressionNormalizer 消费 `$` 前缀并移除后查别名；DollarPrefixExpressionBuilder 已不存在。聚合比较、REL_ANY/ALL/NONE 在当前 Map normalizer 内处理 |
| List 输入 | 单元素字符串 List 为 FIELD 简写；显式字段与常量使用 `field` / `constant`，不套用 Map 的通用 `$` 前缀策略 |
| 逻辑组合 | LogicCombinationBuilder 过滤空表达式、展平同类操作符、单元素退化并折叠 AND/OR 常量；空参数组返回 TRUE |
| 字符串 | Normalize 保留比较值；StringCompareTranspiler 对无 `%` 的 LIKE/NOT_LIKE 常量补两端 `%`，STARTS_WITH/ENDS_WITH 分别补尾/首 `%` |
| 聚合/CASE | 独立转译器、子句 normalizer 与 `forClause(...).normalizeExpression` 是不同入口。通用 List normalizer 不支持 SELECT；AggregationExpressionTest 对若干 SELECT 通用入口断言失败，不能反推所有 SELECT 聚合不可用 |
| 子查询 | Map normalizer 的 IN 右值与 List normalizer 的 EXISTS 有子查询 Map 转换；不是旧评审所述一概拒绝 Map |
| POPULATE/OMIT | PopulateOmitNormalizer 生成类型化子句；Orchestration 消费填充并由 StatementPreparer 应用 OMIT |
| 关联改写 | REL_* 与多参数 FIELD 由编排改写为普通 JOIN/子查询；默认编排含跨源预查询，不是跨站一律 PlanException |
| 未接入配置 | PLAIN_SQL/MODEL_BASED 场景配置、符号关联速记和 feature flag 不视为已实现入口 |

以下表格的“期望”保留应验证的语义；还未闭合的边界不因源码存在而自动验收。旧评审、修订流水与近似覆盖计数归任务产物，普通修订追溯 Git。

## 级别定义

| 级别 | 标记 | 含义 | 面向人群 |
|------|------|------|----------|
| 基础 | **L1** | 最基本的 DSL 用法，开发者必须掌握的核心特性 | 所有开发者 |
| 常用 | **L2** | 日常开发中常用的扩展语法、操作符变体和向后兼容写法 | 日常开发 |
| 进阶 | **L3** | 复杂组合、高级子句、优化行为、错误处理和边界场景 | 高级开发者 |
| 专家 | **L4** | 未文档化特性、设计阶段特性、深层集成和端到端综合用例 | 平台开发者 / 架构师 |

## 测试数据模型

```
User(id, name, age, email, status, departmentId, createdAt)
Department(id, name, code)
Order(id, userId, amount, status, createdAt)
OrderItem(id, orderId, productName, qty, price, amount, status)
Review(id, orderId, rating, score, comment)
```

关系定义：
- `Order.user` → User（多对一）
- `User.department` → Department（多对一）
- `Order.orderItems` → OrderItem（一对多）
- `Order.reviews` → Review（一对多）

## 断言层次

| 层次 | 输入 | 输出 | 关注点 |
|------|------|------|--------|
| Normalize | 原始 Map/List | QueryStatement (SExpression) | 语法转换正确性 |
| Analyze | QueryStatement + 元数据 | Try<QueryStatement> / Failure | 语义验证、类型检查 |
| Orchestrate | QueryStatement + 模型/上下文 | Try<RecordList> | JOIN 策略、POPULATE 计划 |
| Transpile | QueryStatement + TranspileContext | SQL 片段 / QueryMetadata | 最终 SQL 正确性 |

---

# Part I — 表达式层

## 0. 原始表达式形式识别（Normalizer 判定树） `behavior-case`


### 0.1 顶层类型分派


| ID | 级别 | 场景 | DSL 输入示例 | 期望行为 | 已有覆盖 |
|----|------|------|-------------|----------|----------|
| 0-01 | L1 | Map → MapExpressionNormalizer | `{"name": "张三"}` | 进入 Map 解析路径 | 1-01 |
| 0-02 | L1 | List → ListExpressionNormalizer | `["=", ["field","name"], "张三"]` | 进入 List 解析路径 | 6-01~6-13 |
| 0-03 | L3 | 其他类型（String/Number/Boolean） | `"raw string"` | 包装为常量或报错 | — |
| 0-04 | L3 | null 输入 | `null` | 空条件或报错 | — |

### 0.2 Map key 三路分类（buildEntryExpression）


| ID | 级别 | 场景 | DSL 输入 | 期望路由 | 已有覆盖 |
|----|------|------|----------|----------|----------|
| 0-05 | L1 | P1: `$and` 逻辑操作符 | `{"$and": [...]}` | → buildLogicCombination(AND) | 5-01 |
| 0-06 | L1 | P1: `$or` 逻辑操作符 | `{"$or": [...]}` | → buildLogicCombination(OR) | 5-02 |
| 0-07 | L1 | P1: `$not` 逻辑操作符 | `{"$not": {...}}` | → buildLogicCombination(NOT) | 5-03 |
| 0-08 | L2 | P1: 无前缀 `and` | `{"and": [...]}` | → buildLogicCombination(AND) | 5-07 |
| 0-09 | L2 | P1: 无前缀 `or` | `{"or": [...]}` | → buildLogicCombination(OR) | 5-08 |
| 0-10 | L3 | P2: `$constant` 顶层特殊操作符 | `{"$constant": true}` | → buildSpecialOperatorExpression → CONSTANT(true) | — |
| 0-11 | L1 | P3: 普通字段名 | `{"name": "张三"}` | → buildFieldExpression | 1-01 |
| 0-12 | L3 | P2: 未知 `$` 特殊 key | `{"$unknown": "x"}` | 报错：不支持的操作符 | 30-01（部分覆盖） |

### 0.3 buildFieldExpression 五路值分派


| ID | 级别 | 场景 | DSL 输入 | 期望分派 | 已有覆盖 |
|----|------|------|----------|----------|----------|
| 0-13 | L1 | value = null → IS_NULL | `{"name": null}` | `IS_NULL(FIELD("name"))` | 1-04, 4-01 |
| 0-14 | L1 | value = String → EQ | `{"name": "张三"}` | `EQ(FIELD("name"), "张三")` | 1-01 |
| 0-15 | L1 | value = Number → EQ | `{"age": 25}` | `EQ(FIELD("age"), 25)` | 1-02 |
| 0-16 | L1 | value = Boolean → EQ | `{"active": true}` | `EQ(FIELD("active"), true)` | 1-03 |
| 0-17 | L1 | value = List → IN | `{"status": ["a","b"]}` | `IN(FIELD("status"), ["a","b"])` | 1-05 |
| 0-18 | L3 | value = 空 List → IN(空) | `{"status": []}` | `IN(FIELD("status"), [])` | 1-06 |
| 0-19 | L1 | value = Map + isOperatorMap → 操作符 | `{"age": {"$gt": 18}}` | → buildComplexFieldExpression | 2-03 |
| 0-20 | L2 | value = Map + !isOperatorMap → 关联路径 | `{"user": {"name": "张三"}}` | → handleNestedConditionMap | 20-01 |
| 0-21 | L3 | value = 不可识别类型 | 非 null/String/Number/Boolean/List/Map | 报错 | — |

### 0.4 isOperatorMap 边界判定


| ID | 级别 | 场景 | 字段 value 部分 | 判定结果 | 已有覆盖 |
|----|------|------|----------------|----------|----------|
| 0-22 | L1 | 单个 `$` 操作符 key | `{"$gt": 18}` | true | 2-03 |
| 0-23 | L2 | 多个 `$` 操作符 key | `{"$gte": 18, "$lt": 60}` | true（全部解析成功） | 2-18 |
| 0-24 | L2 | 无前缀操作符 key | `{"gt": 18}` | true（`gt` → `gt` → GT） | 2-10 |
| 0-25 | L2 | 符号操作符 key | `{">=": 18}` | true（`>=` → `>=` → GTE） | 2-12 |
| 0-26 | L2 | 纯字段名 Map（非操作符） | `{"name": "张三"}` | false → 关联路径 | 20-01 |
| 0-27 | L3 | 混合操作符 + 非操作符 key | `{"$eq": "张三", "badKey": 1}` | false（`badKey` 不可解析） | 30-14（部分覆盖） |
| 0-28 | L3 | 空 Map `{}` | `{}` | 需确认（全 key 满足的空集） | — |

### 0.5 Map 输入的别名解析（去掉 `$` 前缀）


| ID | 级别 | 场景 | 输入 key | 期望解析结果 | 已有覆盖 |
|----|------|------|----------|-------------|----------|
| 0-29 | L1 | `$` 前缀标准形式 | `$gt` | GT | 2-03 |
| 0-30 | L2 | 无前缀小写 | `gt` | GT | 2-10 |
| 0-31 | L2 | 符号 `>=` | `>=` | GTE | 2-12 |
| 0-32 | L2 | 符号 `!=` | `!=` | NE | 2-17 |
| 0-33 | L3 | 长名 camelCase | `greaterThan` | GT | — |
| 0-34 | L3 | 长名 UPPER_CASE | `GREATER_THAN` | GT | — |
| 0-35 | L3 | 长名缩写 `GOE` | `GOE` | GTE | — |
| 0-36 | L3 | `~` 符号（LIKE 别名）⚠️ | `~` | LIKE（需确认 `$~` 能否查表） | 3-08 |
| 0-37 | L3 | 不存在的别名 | `$nonExist` | 解析失败 → 非操作符 key | 30-01 |

### 0.6 List 首元素分类器（determineOperator）


| ID | 级别 | 场景 | DSL 输入 | 期望行为 | 已有覆盖 |
|----|------|------|----------|----------|----------|
| 0-38 | L2 | 首元素 = 符号操作符字符串 | `["=", ...]` | resolveOperator("=") → EQ | 6-01 |
| 0-39 | L2 | 首元素 = 字母操作符字符串 | `["and", ...]` | resolveOperator("and") → AND | 6-03 |
| 0-40 | L3 | 首元素 = List → 隐式 AND | `[[cond1], [cond2]]` | 各元素 normalize 后 AND 组合 | — |
| 0-41 | L3 | 首元素 = Map → 隐式 AND | `[{cond1}, {cond2}]` | 各元素 normalize 后 AND 组合 | — |
| 0-42 | L4 | 首元素 = Operator 对象 | 编程接口直接传入 | 直接使用（非 DSL 文本层面） | — |
| 0-43 | L3 | 首元素 = 不可识别类型 | `[123, ...]`（Number 开头） | 报错 | — |

### 0.7 逻辑组合

| ID | 级别 | 场景 | 期望行为 |
| --- | --- | --- | --- |
| 0-44 | L3 | 同类 AND 嵌套 | 展平 |
| 0-45 | L3 | 同类 OR 嵌套 | 展平 |
| 0-46 | L3 | 空条件列表 | TRUE |
| 0-47 | L3 | 单个布尔条件 | 返回子条件 |
| 0-48 | L4 | OR 含 TRUE | TRUE |
| 0-49 | L4 | AND 含 FALSE | FALSE |
| 0-50 | L3 | AND 包含 OR | 保留不同操作符层级 |

### 0.8 Map 字段操作符入口

| ID | 级别 | 字段值 | 当前入口与验证重点 |
| --- | --- | --- | --- |
| 0-51 | L1 | `{"$gt":18}` | Map normalizer 去前缀并生成 GT |
| 0-52 | L2 | `{"$gte":18,"$lt":60}` | 多个比较组合 |
| 0-53 | L3 | `{"$notLike":"%x%"}` | NOT_LIKE 别名，无另一个 DollarPrefix 处理器 |
| 0-54 | L3 | `{"$sum":{"$gt":100}}` | 当前聚合比较入口；关系聚合还需编排语义，不能依据旧 v1.3 抛错判断 |
| 0-55 | L3 | 操作符与普通字段混合 | 混合 key 校验，不能悄悄丢弃条件 |

### 0.x 覆盖缺口汇总


| 缺口 ID | 子节 | 场景 | 建议优先级 |
|---------|------|------|-----------|
| 0-03, 0-04 | 0.1 | 非 Map/List 顶层输入 | L3 |
| 0-10 | 0.2 | `$constant` 顶层 Map 形式 | L3 |
| 0-21 | 0.3 | 不可识别的 value 类型 | L3 |
| 0-28 | 0.4 | 空 Map `{}` 作为 operatorMap 判定 | L3 |
| 0-33, 0-34, 0-35 | 0.5 | 长名别名（camelCase / UPPER_CASE / 缩写） | L3 |
| 0-40, 0-41 | 0.6 | List 首元素为 List/Map 的隐式 AND | L3 |
| 0-43 | 0.6 | List 首元素为不可识别类型 | L3 |
| 0-48, 0-49 | 0.7 | 逻辑短路优化（OR+TRUE / AND+FALSE） | L4 |
| 0-55 | 0.8 | 混合 `$` 与非 `$` key 的门控回退 | L3 |

---

## 1. 隐式等值与基础 Map 表达式 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 1-01 | L1 | 字符串隐式等值 | `{"name": "张三"}` | `EQ(FIELD("name"), "张三")` |
| 1-02 | L1 | 数值隐式等值 | `{"age": 25}` | `EQ(FIELD("age"), 25)` |
| 1-03 | L1 | 布尔隐式等值 | `{"active": true}` | `EQ(FIELD("active"), true)` |
| 1-04 | L1 | null → IS_NULL | `{"name": null}` | `IS_NULL(FIELD("name"))` |
| 1-05 | L1 | List → IN | `{"status": ["active", "pending"]}` | `IN(FIELD("status"), ["active", "pending"])` |
| 1-06 | L3 | 空 List → IN(空) | `{"status": []}` | `IN(FIELD("status"), [])` |
| 1-07 | L1 | 多字段隐式 AND | `{"name": "张三", "age": 25}` | `AND(EQ(FIELD("name"), "张三"), EQ(FIELD("age"), 25))` |
| 1-08 | L3 | 单字段不包 AND | `{"name": "张三"}` | `EQ(FIELD("name"), "张三")`（无多余 AND 包裹） |

---

## 2. 比较操作符（$前缀 + 向后兼容） `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 2-01 | L1 | $eq 显式等值 | `{"name": {"$eq": "张三"}}` | `EQ(FIELD("name"), "张三")` |
| 2-02 | L1 | $ne 不等于 | `{"status": {"$ne": "deleted"}}` | `NE(FIELD("status"), "deleted")` |
| 2-03 | L1 | $gt 大于 | `{"age": {"$gt": 18}}` | `GT(FIELD("age"), 18)` |
| 2-04 | L1 | $gte 大于等于 | `{"age": {"$gte": 18}}` | `GTE(FIELD("age"), 18)` |
| 2-05 | L1 | $lt 小于 | `{"age": {"$lt": 60}}` | `LT(FIELD("age"), 60)` |
| 2-06 | L1 | $lte 小于等于 | `{"age": {"$lte": 60}}` | `LTE(FIELD("age"), 60)` |
| 2-07 | L1 | $between 区间 | `{"age": {"$between": [18, 60]}}` | `BETWEEN(FIELD("age"), 18, 60)` |
| 2-08 | L1 | $in 包含 | `{"status": {"$in": ["active", "pending"]}}` | `IN(FIELD("status"), ["active", "pending"])` |
| 2-09 | L1 | $notIn 不包含 ⚠️ | `{"status": {"$notIn": ["deleted"]}}` | `NOT_IN(FIELD("status"), ["deleted"])` |
| 2-10 | L2 | 无前缀 gt（兼容） | `{"age": {"gt": 18}}` | `GT(FIELD("age"), 18)` |
| 2-11 | L2 | 无前缀 gte（兼容） | `{"age": {"gte": 18}}` | `GTE(FIELD("age"), 18)` |
| 2-12 | L2 | 符号形式 >= | `{"age": {">=": 18}}` | `GTE(FIELD("age"), 18)` |
| 2-13 | L2 | 符号形式 > | `{"age": {">": 18}}` | `GT(FIELD("age"), 18)` |
| 2-14 | L2 | 符号形式 < | `{"age": {"<": 60}}` | `LT(FIELD("age"), 60)` |
| 2-15 | L2 | 符号形式 <= | `{"age": {"<=": 60}}` | `LTE(FIELD("age"), 60)` |
| 2-16 | L2 | 符号形式 = | `{"age": {"=": 25}}` | `EQ(FIELD("age"), 25)` |
| 2-17 | L2 | 符号形式 != | `{"status": {"!=": "deleted"}}` | `NE(FIELD("status"), "deleted")` |
| 2-18 | L2 | 多操作符组合 | `{"age": {"$gte": 18, "$lt": 60}}` | `AND(GTE(FIELD("age"), 18), LT(FIELD("age"), 60))` |
| 2-19 | L3 | 三操作符组合 | `{"age": {"$gte": 18, "$lt": 60, "$ne": 30}}` | `AND(GTE(...), LT(...), NE(...))` |


---

## 3. 字符串操作符 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 3-01 | L1 | $contains 包含 | `{"name": {"$contains": "张"}}` | `LIKE(FIELD("name"), "张")` |
| 3-02 | L1 | $startsWith 前缀 | `{"name": {"$startsWith": "张"}}` | `STARTS_WITH(FIELD("name"), "张")` |
| 3-03 | L1 | $endsWith 后缀 ⚠️ | `{"email": {"$endsWith": "@test.com"}}` | `ENDS_WITH(FIELD("email"), "@test.com")` |
| 3-04 | L2 | $like 模糊匹配 | `{"name": {"$like": "%张%三"}}` | `LIKE(FIELD("name"), "%张%三")` |
| 3-05 | L2 | $notLike `behavior-case`(via alias) | `{"name": {"$notLike": "%test%"}}` | `NOT_LIKE(FIELD("name"), "%test%")` |
| 3-06 | L2 | $notContains 别名 ⚠️ `behavior-case`(via alias) | `{"name": {"$notContains": "test"}}` | `NOT_LIKE(FIELD("name"), "test")`（Normalize 保留值；Transpile 为无通配符常量补 `%`） |
| 3-07 | L2 | 无前缀 contains（兼容） | `{"name": {"contains": "张"}}` | `LIKE(FIELD("name"), "张")` |
| 3-08 | L3 | 符号形式 ~ (LIKE) ⚠️ `behavior-case` | `{"name": {"~": "%张%"}}` | `LIKE(FIELD("name"), "张")` |
| 3-09 | L4 | MATCHES/REGEXP ⚠️ `behavior-case` | `{"name": {"$regexp": "^张.*"}}` | 别名注册为 `MATCHES: REGEXP, REGEX`，无专用 Transpiler |


---

## 4. 空值操作符 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 4-01 | L1 | 隐式 null → IS_NULL | `{"name": null}` | `IS_NULL(FIELD("name"))` |
| 4-02 | L1 | $isNull 显式 | `{"name": {"$isNull": true}}` | `IS_NULL(FIELD("name"))` |
| 4-03 | L1 | $isNotNull | `{"name": {"$isNotNull": true}}` | `IS_NOT_NULL(FIELD("name"))` |
| 4-04 | L2 | ~~$exists true~~ ⚠️ **已废弃** | ~~`{"name": {"$exists": true}}`~~ | ~~`IS_NOT_NULL(FIELD("name"))`~~ |
| 4-05 | L2 | ~~$exists false~~ ⚠️ **已废弃** | ~~`{"name": {"$exists": false}}`~~ | ~~`IS_NULL(FIELD("name"))`~~ |


---

## 5. 逻辑操作符 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 5-01 | L1 | $and 显式 | `{"$and": [{"age": {"$gte": 18}}, {"status": "active"}]}` | `AND(GTE(FIELD("age"), 18), EQ(FIELD("status"), "active"))` |
| 5-02 | L1 | $or | `{"$or": [{"name": "张三"}, {"name": "李四"}]}` | `OR(EQ(FIELD("name"), "张三"), EQ(FIELD("name"), "李四"))` |
| 5-03 | L1 | $not | `{"$not": {"status": "deleted"}}` | `NOT(EQ(FIELD("status"), "deleted"))` |
| 5-04 | L2 | 嵌套 and+or | `{"$and": [{"$or": [{"a": 1}, {"b": 2}]}, {"c": 3}]}` | `AND(OR(EQ(a,1), EQ(b,2)), EQ(c,3))` |
| 5-05 | L2 | 嵌套 or+and | `{"$or": [{"$and": [{"a": 1}, {"b": 2}]}, {"c": 3}]}` | `OR(AND(EQ(a,1), EQ(b,2)), EQ(c,3))` |
| 5-06 | L3 | 三层嵌套 | `{"$and": [{"$or": [{"$not": {"a": 1}}, {"b": 2}]}, {"c": 3}]}` | `AND(OR(NOT(EQ(a,1)), EQ(b,2)), EQ(c,3))` |
| 5-07 | L2 | 无前缀 and（兼容） | `{"and": [{"age": {">=": 18}}, {"status": "active"}]}` | 同 5-01 |
| 5-08 | L2 | 无前缀 or（兼容） | `{"or": [{"name": "张三"}, {"name": "李四"}]}` | 同 5-02 |
| 5-09 | L3 | ⚠️ 嵌套 AND 展平 | `{"$and": [{"$and": [{"a": 1}, {"b": 2}]}, {"c": 3}]}` | `AND(EQ(a,1), EQ(b,2), EQ(c,3))` |
| 5-10 | L3 | ⚠️ 嵌套 OR 展平 | `{"$or": [{"$or": [{"a": 1}, {"b": 2}]}, {"c": 3}]}` | `OR(EQ(a,1), EQ(b,2), EQ(c,3))` |
| 5-11 | L3 | 单元素 AND ⚠️ | `{"$and": [{"a": 1}]}` | `EQ(FIELD("a"), 1)` |
| 5-12 | L3 | 空 AND | `{"$and": []}` | TRUE |
| 5-13 | L4 | XOR `behavior-case` | `["xor", cond1, cond2]` 或 `{"$xor": [cond1, cond2]}` | `XOR(cond1, cond2)` — `Operators.java:51`，`LogicCombinationTranspiler` 处理 |
| 5-14 | L4 | XNOR `behavior-case` | `["xnor", cond1, cond2]` 或 `{"$xnor": [cond1, cond2]}` | `XNOR(cond1, cond2)` — `Operators.java:52` |


---

## 6. S 表达式语法 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 6-01 | L2 | 等值比较 ⚠️ | `["=", ["departmentId"], 1001]` | `EQ(FIELD("departmentId"), 1001)` |
| 6-02 | L2 | 大于等于 ⚠️ | `[">=", ["age"], 18]` | `GTE(FIELD("age"), 18)` |
| 6-03 | L2 | AND 组合 ⚠️ | `["and", [">=", ["age"], 18], ["=", ["status"], "active"]]` | `AND(GTE(FIELD("age"), 18), EQ(FIELD("status"), "active"))` |
| 6-04 | L2 | OR 组合 ⚠️ | `["or", ["=", ["name"], "张三"], ["=", ["name"], "李四"]]` | `OR(EQ(...), EQ(...))` |
| 6-05 | L2 | NOT ⚠️ | `["not", ["=", ["status"], "deleted"]]` | `NOT(EQ(FIELD("status"), "deleted"))` |
| 6-06 | L2 | BETWEEN ⚠️ | `["between", ["age"], 18, 60]` | `BETWEEN(FIELD("age"), 18, 60)` |
| 6-07 | L2 | IN ⚠️ | `["in", ["status"], ["active", "pending"]]` | `IN(FIELD("status"), ["active", "pending"])` |
| 6-08 | L2 | LIKE ⚠️ | `["like", ["name"], "%张%"]` | `LIKE(FIELD("name"), "张")` |
| 6-09 | L2 | IS_NULL ⚠️ | `["isNull", ["name"]]` | `IS_NULL(FIELD("name"))` |
| 6-10 | L3 | 嵌套布尔 ⚠️ | `["and", ["or", ["=", ["a"], 1], ["=", ["b"], 2]], ["=", ["c"], 3]]` | `AND(OR(EQ(a,1), EQ(b,2)), EQ(c,3))` |
| 6-11 | L3 | field 显式字段 | `["=", ["field", "name"], "张三"]` | `EQ(FIELD("name"), "张三")` |
| 6-12 | L3 | constant 常量 `behavior-case` | `["=", ["field", "status"], ["constant", "active"]]` | `EQ(FIELD("status"), CONSTANT("active"))` |
| 6-13 | L3 | 函数调用 ⚠️ | `["=", ["year", ["createdAt"]], ["year", ["now"]]]` | 函数节点保留并正确转译 |


---

## 7. 聚合函数 `behavior-case`(COUNT) / `behavior-case`(SUM/AVG/MAX/MIN Transpiler 已实现)


| ID | 级别 | 场景 | DSL 输入（SELECT 子句内） | 期望 Normalize 输出 |
|----|------|------|--------------------------|-------------------|
| 7-01 | L2 | COUNT(*) ⚠️ | `["count", ["*"]]` | `COUNT(COLUMNS)` |
| 7-02 | L2 | COUNT(field) ⚠️ | `["count", ["id"]]` | `COUNT(FIELD("id"))` |
| 7-03 | L2 | SUM ⚠️ | `["sum", ["amount"]]` | `SUM(FIELD("amount"))` |
| 7-04 | L2 | AVG ⚠️ | `["avg", ["amount"]]` | `AVG(FIELD("amount"))` |
| 7-05 | L2 | MAX ⚠️ | `["max", ["age"]]` | `MAX(FIELD("age"))` |
| 7-06 | L2 | MIN ⚠️ | `["min", ["age"]]` | `MIN(FIELD("age"))` |
| 7-07 | L2 | 聚合+别名 ⚠️ | `{"totalAmount": ["sum", ["amount"]]}` | `ALIAS(SUM(FIELD("amount")), "totalAmount")` |
| 7-08 | L3 | ⚠️ ~~HAVING Map 形式~~ **已修正** | `having: [">", ["count", ["*"]], 5]` | HAVING 子句含 `GT(COUNT(COLUMNS), 5)` |
| 7-09 | L3 | ❌ WHERE 中误用聚合 | `where: ["sum", ["amount"]]` | Analyze 阶段报错：聚合不允许在 WHERE |


---

## 8. CASE/WHEN 表达式 ⚠️ `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 |
|----|------|------|----------|-------------------|
| 8-01 | L3 | 搜索式 CASE ⚠️ | `["case", ["when", [">", ["age"], 60], "老年"], ["when", [">", ["age"], 18], "成年"], ["else", "未成年"]]` | `CASE(WHEN(GT(age,60),"老年"), WHEN(GT(age,18),"成年"), ELSE("未成年"))` |
| 8-02 | L3 | 简单式 CASE_EQ ⚠️ | `["caseEq", ["age"], ["when", 1, "一"], ["when", 2, "二"], ["else", "其他"]]` | `CASE_EQ(FIELD("age"), CASE_EQ_WHEN(1,"一"), CASE_EQ_WHEN(2,"二"), CASE_EQ_ELSE("其他"))` |
| 8-03 | L3 | CASE 无 ELSE ⚠️ | `["case", ["when", [">", ["age"], 18], "成年"]]` | `CASE(WHEN(GT(age,18),"成年"))` — 无 ELSE 分支 |
| 8-04 | L3 | CASE 在 SELECT 中 ⚠️ | `select: [{"label": ["case", ...]}]` | SELECT 子句含 ALIAS(CASE(...), "label") |


---

# Part II — 子句层

## 9. SELECT 子句 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 9-01 | L1 | 字段列表（List） | `{"select": ["id", "name", "age"]}` | SELECT 含三个 FIELD |
| 9-02 | L1 | 别名映射（Map 元素） | `{"select": ["id", {"userName": "name"}]}` | `FIELD("id"), ALIAS(FIELD("name"), "userName")` |
| 9-03 | L1 | 全部字段（省略 select） | `{"from": "User", "where": {...}}` | 默认 SELECT * |
| 9-04 | L2 | 聚合在 SELECT | `{"select": [{"total": ["count", ["*"]]}]}` | `ALIAS(COUNT(COLUMNS), "total")` |
| 9-05 | L2 | 混合字段+聚合 | `{"select": ["name", {"cnt": ["count", ["*"]]}]}` | FIELD + ALIAS(COUNT) |
| 9-06 | L3 | ❌ 关联字段点号路径 | `{"select": ["id", {"userName": "user.name"}]}` | **不支持**：SELECT 不支持 `关联.字段` 点号路径，关联数据通过 POPULATE 加载 |
| 9-07 | L3 | 字符串形式 `behavior-case` | `{"select": "id, name, age"}` | 逗号分隔解析为三个 FIELD |
| 9-08 | L3 | 字符串 + AS 别名 `behavior-case` | `{"select": "id, name as userName, age"}` | `FIELD("id"), ALIAS(FIELD("name"), "userName"), FIELD("age")` |
| 9-09 | L3 | Map 形式 | `{"select": {"userName": "name", "userAge": "age"}}` | `ALIAS(FIELD("name"), "userName"), ALIAS(FIELD("age"), "userAge")` |

---

## 10. FROM 子句 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 10-01 | L1 | 单表 | `{"from": "User"}` | FROM User |
| 10-02 | L1 | DataModel 注入 | 由 `DataModel.query()` 自动注入 from | FROM 自动填充模型名 |

---

## 11. ORDER 子句 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 11-01 | L1 | Map 形式单字段 | `{"order": [{"name": "asc"}]}` | ORDER BY name ASC |
| 11-02 | L1 | Map 形式多字段 | `{"order": [{"createdAt": "desc"}, {"name": "asc"}]}` | ORDER BY createdAt DESC, name ASC |
| 11-03 | L2 | 字符串形式 `behavior-case` | `{"order": "name asc, createdAt desc"}` | ORDER BY name ASC, createdAt DESC |
| 11-04 | L2 | List 混合形式 `behavior-case` | `{"order": [{"name": "asc"}, "createdAt desc"]}` | ORDER BY name ASC, createdAt DESC |
| 11-05 | L3 | Map 整体形式 `behavior-case` | `{"order": {"name": "asc", "createdAt": "desc"}}` | ORDER BY name ASC, createdAt DESC |
| 11-06 | L1 | 默认排序方向 | `{"order": ["name"]}` | ORDER BY name ASC（默认升序） |

---

## 12. 分页 `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 12-01 | L1 | limit + offset（标准） | `{"limit": 10, "offset": 20}` | LIMIT 10 OFFSET 20 |
| 12-02 | L1 | 仅 limit | `{"limit": 10}` | LIMIT 10 |
| 12-03 | L2 | skip + limit ⚠️ `behavior-case` | `{"skip": 20, "limit": 10}` | LIMIT 10 OFFSET 20（skip 等价 offset） |
| 12-04 | L2 | page + pageSize `behavior-case` | `{"page": 2, "pageSize": 20}` | LIMIT 20 OFFSET 20（页码从 1 开始，自动转换） |
| 12-05 | L2 | pageNum + perPage `behavior-case` | `{"pageNum": 3, "perPage": 10}` | LIMIT 10 OFFSET 20 |
| 12-06 | L1 | 无分页 | 不含 limit/offset | 无 LIMIT/OFFSET 子句 |


---

## 13. GROUP BY / HAVING `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 13-01 | L2 | 单字段分组（List） | `{"select": ["status", {"cnt": ["count", ["*"]]}], "from": "User", "group": ["status"]}` | GROUP BY status |
| 13-02 | L2 | 多字段分组 | `{"group": ["status", "departmentId"]}` | GROUP BY status, departmentId |
| 13-03 | L3 | 字符串形式 `behavior-case` | `{"group": "status, departmentId"}` | GROUP BY status, departmentId |
| 13-04 | L3 | groupBy 别名 `behavior-case` | `{"groupBy": ["status"]}` | 同 13-01 |
| 13-05 | L3 | group_by 别名 `behavior-case` | `{"group_by": ["status"]}` | 同 13-01 |
| 13-06 | L2 | ⚠️ HAVING 条件 | `{"group": ["status"], "having": [">", ["count", ["*"]], 5]}` | HAVING COUNT(*) > 5 |
| 13-07 | L3 | ⚠️ HAVING 多条件 | `{"having": ["and", [">", ["count", ["*"]], 5], [">", ["sum", ["amount"]], 1000]]}` | HAVING COUNT(*) > 5 AND SUM(amount) > 1000 |
| 13-08 | L3 | ❌ HAVING 无 GROUP ⚠️ | `{"having": [">", ["count", ["*"]], 5]}` | 期望 Analyze/Transpile 拒绝，但当前可能无该强校验 |


---

## 14. DISTINCT `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 14-01 | L2 | Boolean true | `{"select": ["name"], "from": "User", "distinct": true}` | SELECT DISTINCT name |
| 14-02 | L2 | Boolean false | `{"distinct": false}` | 无 DISTINCT |
| 14-03 | L3 | Number 非零 `behavior-case` | `{"distinct": 1}` | SELECT DISTINCT（非 0 视为 true） |
| 14-04 | L3 | String "true" `behavior-case` | `{"distinct": "true"}` | SELECT DISTINCT |
| 14-05 | L3 | String "1" `behavior-case` | `{"distinct": "1"}` | SELECT DISTINCT |


---

## 15. JOIN 子句 ⚠️ `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 15-01 | L3 | LEFT JOIN ⚠️ | `{"leftJoin": {"Department": {"on": {"departmentId": "id"}}}}` | LEFT JOIN Department ON ... |
| 15-02 | L3 | INNER JOIN ⚠️ | `{"innerJoin": {"Department": {"on": {"departmentId": "id"}}}}` | INNER JOIN Department ON ... |
| 15-03 | L3 | RIGHT JOIN ⚠️ | `{"rightJoin": {"Department": {"on": {"departmentId": "id"}}}}` | RIGHT JOIN Department ON ... |
| 15-04 | L3 | FULL JOIN ⚠️ | `{"fullJoin": {"Department": {"on": {"departmentId": "id"}}}}` | FULL JOIN Department ON ... |
| 15-05 | L3 | CROSS JOIN ⚠️ | `{"crossJoin": {"Department": {}}}` | CROSS JOIN Department（无 ON） |
| 15-06 | L3 | 多表 JOIN | 两个 join 条目 | 生成两个 JOIN 子句 |
| 15-07 | L3 | JOIN + WHERE | join + where 组合 | JOIN 和 WHERE 同时存在 |
| 15-08 | L3 | ON 条件 Map 形式 | `{"on": {"userId": "id"}}` | ON 条件为 EQ(leftField, rightField) |
| 15-09 | L3 | ON 条件 List 形式 | `{"on": [{"userId": "id"}, {"status": "active"}]}` | 多条件 AND |


---

## 16. UNION / UNION ALL ⚠️ `behavior-case`

| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 16-01 | L3 | UNION ⚠️ | `{"union": [{"select": ["name"], "from": "User"}, {"select": ["name"], "from": "Department"}]}` | 两个 SELECT 通过 UNION 连接（去重） |
| 16-02 | L3 | UNION ALL ⚠️ | `{"unionAll": [query1, query2]}` | UNION ALL（保留重复） |
| 16-03 | L3 | UNION + ORDER | union 结果集上加 order | ORDER BY 作用于 UNION 结果 |


---

## 17. WITH / CTE `behavior-case`

| ID | 级别 | 场景 | 状态 | DSL 输入 | 期望 |
|----|------|------|------|----------|------|
| 17-01 | L3 | 基础 CTE | behavior-case | `{"with": {"activeUsers": {"select": ["id","name"], "from": "User", "where": {"status": "active"}}}, "select": ["name"], "from": "activeUsers"}` | WITH activeUsers AS (...) SELECT ... |
| 17-02 | L3 | 多个 CTE | behavior-case | with 中定义两个命名查询 | WITH a AS (...), b AS (...) SELECT ... |
| 17-03 | L4 | 递归 CTE ⚠️ | behavior-case | `{"withRecursive": {...}}` | WITH RECURSIVE ... — `DefaultWithNormalizer:37` + `WithBuilder:25` 已有处理链路 |


---

## 18. POPULATE（关联数据加载） `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 18-01 | L1 | 单关联加载 | `{"from": "Order", "populate": ["user"]}` | Orchestration 生成 user 的 Populate 计划 |
| 18-02 | L1 | 多关联加载 | `{"populate": ["user", "orderItems"]}` | 两个 Populate 计划 |
| 18-03 | L2 | 嵌套 populate | `{"populate": {"user": {"populate": "department"}}}` | 嵌套 Populate：Order→User→Department |
| 18-04 | L2 | populate + where | `{"where": {"status": "active"}, "populate": ["user"]}` | WHERE 过滤主表，POPULATE 独立加载 |
| 18-05 | L2 | populate + select | `{"select": ["id", "amount"], "populate": ["user"]}` | SELECT 限定主表字段，POPULATE 独立加载 |
| 18-06 | L2 | 字符串形式 | `{"populate": "user, orderItems"}` | 逗号分隔解析为两个 Populate |
| 18-07 | L3 | Map 带子查询配置 | `{"populate": {"user": {"select": ["id", "name"], "where": {"status": "active"}, "limit": 10}}}` | Populate 带 SELECT/WHERE/LIMIT 子查询条件 |
| 18-08 | L3 | Map 带 OMIT | `{"populate": {"user": {"omit": ["password", "email"]}}}` | Populate 排除敏感字段 |
| 18-09 | L4 | 嵌套 + 子查询组合 | `{"populate": {"user": {"select": ["id", "name"], "populate": {"department": {"where": {"code": "TECH"}}}}}}` | 多层嵌套 + 每层独立子查询条件 |

---

## 19. OMIT（字段排除） `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 19-01 | L1 | 排除单字段 | `{"from": "User", "omit": ["password"]}` | SELECT 展开时排除 password |
| 19-02 | L1 | 排除多字段 | `{"omit": ["password", "secret"]}` | 排除多个字段 |
| 19-03 | L2 | omit + select 冲突 | `{"select": ["id", "password"], "omit": ["password"]}` | OMIT 优先，password 被排除 |
| 19-04 | L2 | 字符串形式 | `{"omit": "password, secret"}` | 逗号分隔解析 |

---

# Part III — 关联层

## 20. 多参数 FIELD — 多对一关联路径 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize 输出 | 期望 Execute 行为 |
|----|------|------|----------|-------------------|-----------------|
| 20-01 | L2 | 单层关联隐式等值 | `{"user": {"name": "张三"}}` | `EQ(FIELD("user","name"), "张三")` | 添加 user JOIN |
| 20-02 | L2 | 单层关联+操作符 | `{"user": {"age": {"$gt": 18}}}` | `GT(FIELD("user","age"), 18)` | 添加 user JOIN |
| 20-03 | L2 | 单层关联多字段 | `{"user": {"name": "张三", "age": {"$gte": 18}}}` | `AND(EQ(FIELD("user","name"),"张三"), GTE(FIELD("user","age"),18))` | 添加 user JOIN |
| 20-04 | L3 | 两层嵌套路径 | `{"user": {"department": {"name": "技术部"}}}` | `EQ(FIELD("user","department","name"), "技术部")` | 添加 user + department JOIN |
| 20-05 | L2 | 关联字段+$contains | `{"user": {"name": {"$contains": "张"}}}` | `LIKE(FIELD("user","name"), "%张%")` | 添加 user JOIN |
| 20-06 | L2 | 关联字段+$in | `{"user": {"status": {"$in": ["active","vip"]}}}` | `IN(FIELD("user","status"), [...])` | 添加 user JOIN |
| 20-07 | L2 | 关联字段+null | `{"user": {"name": null}}` | `IS_NULL(FIELD("user","name"))` | 添加 user JOIN |
| 20-08 | L3 | 多个关联同时出现 | `{"user": {"name": "张三"}, "department": {"code": "IT"}}` | 两个多参数 FIELD 表达式 AND | 添加两个 JOIN |
| 20-09 | L3 | 关联+本表混合 | `{"status": "active", "user": {"name": "张三"}}` | `AND(EQ(FIELD("status"),...), EQ(FIELD("user","name"),...))` | 本表条件+JOIN |
| 20-10 | L3 | ❌ 不存在的关联路径 | `{"unknownRel": {"name": "x"}}` | Analyze 报错：关联不存在 | — |
| 20-11 | L3 | ❌ 关联字段类型不匹配 | `{"user": {"age": "非数字"}}` | Analyze 报错：类型不匹配 | — |

---

## 21. 一对多关联操作符（$any / $all / $none） ⚠️ `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 Normalize | 期望 Transpile SQL 片段 |
|----|------|------|----------|---------------|----------------------|
| 21-01 | L2 | $any 基础 | `{"orderItems": {"$any": {"productName": "iPhone"}}}` | `REL_ANY("orderItems", EQ(FIELD("productName"),"iPhone"))` | `EXISTS (SELECT 1 FROM order_item WHERE ... AND product_name = 'iPhone')` |
| 21-02 | L2 | $any + 操作符 | `{"orderItems": {"$any": {"price": {"$gt": 100}}}}` | `REL_ANY("orderItems", GT(FIELD("price"),100))` | `EXISTS (... AND price > 100)` |
| 21-03 | L3 | $any + 多条件 | `{"orderItems": {"$any": {"productName": {"$contains": "iPhone"}, "qty": {"$gte": 2}}}}` | `REL_ANY("orderItems", AND(LIKE(...), GTE(...)))` | `EXISTS (... AND ...)` |
| 21-04 | L3 | $all | `{"reviews": {"$all": {"score": {"$gte": 4}}}}` | `REL_ALL("reviews", GTE(FIELD("score"),4))` | `NOT EXISTS (SELECT 1 FROM review WHERE ... AND NOT (score >= 4))` |
| 21-05 | L3 | $none | `{"reviews": {"$none": {"score": {"$lt": 3}}}}` | `REL_NONE("reviews", LT(FIELD("score"),3))` | `NOT EXISTS (SELECT 1 FROM review WHERE ... AND score < 3)` |
| 21-06 | L3 | 无前缀 any（兼容） | `{"orderItems": {"any": {"price": {"$gt": 100}}}}` | 同 21-02 | |
| 21-07 | L3 | 无前缀 all（兼容） | `{"reviews": {"all": {"score": {">=": 4}}}}` | 同 21-04 | |
| 21-08 | L3 | 无前缀 none（兼容） | `{"reviews": {"none": {"score": {"<": 3}}}}` | 同 21-05 | |
| 21-09 | L3 | ❌ $any 条件为非 Map | `{"orderItems": {"$any": 123}}` | 校验错误：关联条件必须是 Map | |
| 21-10 | L3 | $any + $none 同时 | `{"orderItems": {"$any": {...}}, "reviews": {"$none": {...}}}` | 两个 REL_* 表达式 AND | 两个 EXISTS/NOT EXISTS |
| 21-11 | L3 | 关联+本表混合 | `{"status": "active", "orderItems": {"$any": {"price": {"$gt": 100}}}}` | `AND(EQ(FIELD("status"),...), REL_ANY(...))` | WHERE status='active' AND EXISTS(...) |

---

## 22. 关联聚合语义与边界


| ID | 级别 | 场景 | 状态 | DSL 输入 | 期望 |
|----|------|------|------|----------|------|
| 22-01 | L3 | $count 关联聚合 ⚠️ | behavior-case | `{"orderItems": {"$count": {"$gt": 2}}}` | 关联记录数 > 2 |
| 22-02 | L4 | $sum 关联聚合 | design-only | `{"orderItems": {"$sum": {"amount": {"$gt": 1000}}}}` | 预期关系字段聚合比较；当前 Map 聚合入口与关联编排是否满足此嵌套写法须定向验证 |
| 22-03 | L4 | $avg 关联聚合 | design-only | `{"reviews": {"$avg": {"score": {"$gte": 4}}}}` | 同上 |
| 22-04 | L4 | $max 关联聚合 | design-only | `{"orderItems": {"$max": {"price": {"$gt": 500}}}}` | 同上 |
| 22-05 | L4 | $min 关联聚合 | design-only | `{"orderItems": {"$min": {"price": {"$lt": 10}}}}` | 同上 |

---

## 23. 子查询（SUB_QUERY） `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 23-01 | L4 | WHERE IN 子查询 ⚠️ | `{"where": {"userId": {"$in": {"select": ["id"], "from": "User", "where": {"status": "active"}}}}}` | `IN(FIELD("userId"), SUB_QUERY(...))` |
| 23-02 | L4 | EXISTS 子查询 ⚠️ `behavior-case` | `["exists", {"select": ["1"], "from": "Order", "where": {...}}]` | `EXISTS(SUB_QUERY(...))` — `ExistsTranspiler` 已实现 |


---

## 24. EXISTS 独立操作符 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 24-01 | L4 | S 表达式中 EXISTS ⚠️ | `["exists", subQuery]` | `EXISTS(SUB_QUERY(...))` |


---

# Part IV — 执行层

## 25. Scenario 规则（PLAIN_SQL vs MODEL_BASED） ⚠️ `design-only`


| ID | 级别 | 规则 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 25-01 | L3 | PLAIN_SQL 拒绝关联查询 | 关联过滤条件 + PLAIN_SQL 场景 | 报错：场景限制不允许关联操作 |
| 25-02 | L3 | MODEL_BASED 允许关联查询 | 同样的关联过滤条件 + MODEL_BASED 场景 | 正常通过 Analyze/Execute |
| 25-03 | L3 | ❌ 聚合操作符位置限制 | 聚合操作符出现在 WHERE 中 | 报错：位置校验失败 |
| 25-04 | L3 | ❌ ORDER 操作符误用 | ORDER 操作符出现在非 ORDER 子句 | 报错：位置校验失败 |


---

## 26. Parser Guardrail `behavior-case`


| ID | 级别 | 规则 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 26-01 | L3 | 未知操作符 | `{"name": {"$unknown": "x"}}` | 报错：不支持的操作符 |
| 26-02 | L3 | 关联条件格式错误 | `{"orderItems": {"any": 123}}` | 报错：关联条件必须是 Map/Object |
| 26-03 | L3 | 值形态不匹配 | 值不符合 parser rule 1/2/3 | 解析/Normalize 失败并给出明确错误信息 |

---

## 27. JOIN 能力与策略决策 `behavior-case`


| ID | 级别 | 场景 | 前置条件 | DSL 输入 | 期望行为 |
|----|------|------|----------|----------|---------|
| 27-01 | L3 | 同数据站 NATIVE | 两模型在同一 DataStation | `{"user": {"name": "张三"}}` | JoinCapability=joinable → NATIVE 策略，生成 SQL JOIN |
| 27-02 | L3 | 跨数据站 SEPARATE | 两模型在不同 DataStation | `{"user": {"name": "张三"}}` | JoinCapability=notJoinable → 跨源预查询后改写主条件 |
| 27-03 | L3 | 一对多 NATIVE | 同数据站 + $any | `{"orderItems": {"$any": {"price": {"$gt": 100}}}}` | 可 JOIN → EXISTS 子查询 |
| 27-04 | L3 | 一对多不可 JOIN | 跨数据站 + $any | 同上 | 跨源关系条件预查询与主条件改写 |
| 27-05 | L3 | Populate JOIN 策略 | 同数据站 + populate | `{"populate": ["user"]}` | `JoinPopulateStrategy` 选中 |
| 27-06 | L3 | Populate SEPARATE 策略 | 跨数据站 + populate | `{"populate": ["user"]}` | `SeparatePopulateStrategy` 选中 |

---

# Part V — 组合与集成

## 28. Pairwise 组合矩阵 `behavior-case`


维度：
- 表达式形式：`Map`, `S-expression`
- 操作符形式：`$prefixed`, `legacy`, `implicitEqual`
- 关联形式：`m2o FIELD`, `o2m relation op`
- 场景：`MODEL_BASED`, `PLAIN_SQL`
- JOIN 能力：`joinable`, `notJoinable`
- 子句复杂度：`simple`, `withOrderLimit`, `populateOmit`

| ID | 级别 | 表达式 | 操作符 | 关联 | 场景 | JOIN | 子句 | 期望 |
|----|------|--------|--------|------|------|------|------|------|
| X-01 | L3 | Map | $prefixed | m2o | MODEL_BASED | joinable | simple | 正常 JOIN 查询 |
| X-02 | L3 | Map | legacy | o2m(any) ⚠️ | MODEL_BASED | joinable | withOrderLimit | EXISTS + ORDER + LIMIT |
| X-03 | L3 | Map | implicitEqual | m2o | MODEL_BASED | notJoinable | populateOmit | 跨源预查询 + Separate 填充（场景标签本身未接入） |
| X-04 | L4 | S-expr | $prefixed | o2m(all) ⚠️ | MODEL_BASED | joinable | simple | NOT EXISTS 子查询 |
| X-05 | L4 | Map | $prefixed | o2m(none) ⚠️ | PLAIN_SQL ⚠️ | joinable | simple | ❌ 场景限制拒绝关联 |
| X-06 | L4 | S-expr | legacy | m2o | MODEL_BASED | joinable | withOrderLimit | JOIN + ORDER + LIMIT |
| X-07 | L4 | Map | $prefixed | m2o 多跳 | MODEL_BASED | joinable | simple | 多层 JOIN |
| X-08 | L3 | Map | implicitEqual | 无关联 | PLAIN_SQL ⚠️ | — | simple | 纯本表查询正常 |

---

## 29. 复合查询组合 `behavior-case`


| ID | 级别 | 场景 | DSL 输入 | 期望 |
|----|------|------|----------|------|
| 29-01 | L2 | select+where+order+limit | `{"select":["id","name"], "from":"User", "where":{"age":{"$gte":18}}, "order":[{"name":"asc"}], "limit":20}` | 完整 SQL |
| 29-02 | L3 | ⚠️ where+group+having+order | `{"select":["status",{"cnt":["count",["*"]]}], "from":"User", "group":["status"], "having":[">", ["count", ["*"]], 5], "order":[{"cnt":"desc"}]}` | GROUP BY + HAVING + ORDER BY |
| 29-03 | L3 | 关联条件+本表条件+分页 | `{"from":"Order", "where":{"status":"active", "user":{"name":{"$contains":"张"}}}, "order":[{"createdAt":"desc"}], "limit":10, "offset":0}` | WHERE + JOIN(user) + ORDER + LIMIT OFFSET |
| 29-04 | L3 | ⚠️ 多对一+一对多混合 | `{"from":"Order", "where":{"user":{"name":"张三"}, "orderItems":{"$any":{"price":{"$gt":100}}}}}` | JOIN(user) + EXISTS(orderItems) |
| 29-05 | L3 | distinct+order+limit | `{"select":["name"], "from":"User", "distinct":true, "order":[{"name":"asc"}], "limit":10}` | SELECT DISTINCT ... ORDER BY ... LIMIT |
| 29-06 | L3 | ⚠️ union+order+limit | `{"union":[q1, q2], "order":[{"name":"asc"}], "limit":10}` | (q1 UNION q2) ORDER BY name LIMIT 10 |
| 29-07 | L3 | with+主查询+分页 | `{"with":{...}, "select":[...], "from":"cte_name", "limit":10}` | WITH ... SELECT ... LIMIT |
| 29-08 | L3 | populate+omit 组合 | `{"from":"Order", "select":["id","amount"], "omit":["status"], "populate":["user","orderItems"]}` | SELECT 排除 status + 两个 Populate 计划 |


---

## 30. 边界与错误用例

| ID | 级别 | 场景 | DSL 输入 | 期望行为 |
|----|------|------|----------|---------|
| 30-01 | L3 | 未知 $ 操作符 | `{"name": {"$unknown": "x"}}` | 报错：不支持的操作符 |
| 30-02 | L3 | 空 where | `{"from": "User", "where": {}}` | 无 WHERE 子句或忽略 |
| 30-03 | L3 | where 为 null | `{"from": "User", "where": null}` | 无 WHERE 子句 |
| 30-04 | L3 | select 为空数组 | `{"select": [], "from": "User"}` | 默认 SELECT * 或报错 |
| 30-05 | L3 | limit 为负数 | `{"limit": -1}` | 报错或忽略 |
| 30-06 | L3 | limit 为 0 | `{"limit": 0}` | LIMIT 0（返回空结果） |
| 30-07 | L3 | offset 无 limit | `{"offset": 10}` | 报错或忽略 offset |
| 30-08 | L3 | 不存在的字段名 | `{"where": {"nonExistField": 1}}` | Analyze 报错：字段不存在 |
| 30-09 | L3 | $between 参数不足 | `{"age": {"$between": [18]}}` | 报错：BETWEEN 需要两个参数 |
| 30-10 | L3 | $between 参数过多 | `{"age": {"$between": [1,2,3]}}` | 报错：BETWEEN 参数过多 |
| 30-11 | L3 | $in 值非数组 | `{"status": {"$in": "active"}}` | 报错：IN 需要数组参数 |
| 30-12 | L3 | 关联条件非 Map | `{"orderItems": {"$any": 123}}` | 报错：关联条件必须是 Map |
| 30-13 | L3 | 嵌套层级过深 | 10 层嵌套 AND/OR | 正常处理或报错提示层级过深 |
| 30-14 | L3 | Map 中混合操作符和字段 | `{"name": {"$eq": "张三", "unknownKey": 1}}` | 报错或明确处理规则 |

---

## 31. 端到端集成用例


### G-01 复杂订单查询 `behavior-case` — L4

```json
{
  "select": ["id", "amount", "createdAt", {"orderStatus": "status"}],
  "from": "Order",
  "where": {
    "$and": [
      {"status": "paid"},
      {"amount": {"$gte": 100, "$lte": 10000}},
      {"createdAt": {"$gte": "2025-01-01"}},
      {"user": {"name": {"$contains": "张"}}},
      {"user": {"department": {"name": "技术部"}}},
      {
        "$or": [
          {"orderItems": {"$any": {"price": {"$gt": 500}}}},
          {"reviews": {"$all": {"rating": {"$gte": 4}}}}
        ]
      }
    ]
  },
  "order": [{"createdAt": "desc"}, {"amount": "asc"}],
  "limit": 20,
  "offset": 0,
  "populate": ["user", "orderItems"]
}
```

断言：
- Normalize：AND 树含 EQ + 多操作符组合 + FIELD("user","name") + FIELD("user","department","name") + REL_ANY + REL_ALL（⚠️ REL_* 归一化需确认）
- Analyze：验证所有字段路径和关联路径
- Orchestrate：user/department 走 JOIN，orderItems/reviews 走 EXISTS，生成 Populate 计划
- Transpile：SQL 含 JOIN + EXISTS/NOT EXISTS + ORDER BY + LIMIT OFFSET

### G-02 分组统计 + HAVING `behavior-case` — L3

```json
{
  "select": ["status", {"cnt": ["count", ["*"]]}, {"totalAmount": ["sum", ["amount"]]}],
  "from": "Order",
  "where": {"createdAt": {"$gte": "2025-01-01"}},
  "group": ["status"],
  "having": ["and", [">", ["count", ["*"]], 10], [">", ["sum", ["amount"]], 5000]],
  "order": [{"cnt": "desc"}]
}
```

断言：
- SELECT 含聚合函数 + 别名
- WHERE 过滤日期
- GROUP BY status
- HAVING 含两个聚合条件（⚠️ 已修正为 S 表达式形式）
- ORDER BY 别名字段

### G-03 多对一 + 一对多 + 逻辑嵌套 `behavior-case` — L4

```json
{
  "from": "Order",
  "where": {
    "$or": [
      {"$and": [
        {"user": {"department": {"name": "技术部"}}},
        {"amount": {"$gte": 1000}}
      ]},
      {"orderItems": {"$any": {"productName": {"$contains": "Pro"}}}}
    ]
  },
  "order": [{"createdAt": "desc"}],
  "limit": 50,
  "populate": ["user", "orderItems"]
}
```

断言：
- Normalize：OR(AND(EQ(FIELD("user","department","name"),...), GTE(...)), REL_ANY(...))（⚠️ REL_ANY 归一化需确认）
- 两层嵌套关联路径 user→department
- 逻辑组合正确保留
- Populate 独立于 WHERE 条件

### G-04 Design-only Golden Case `design-only` — L4

包含未实现特性的综合用例：
- 符号关联速记（`@`, `*`, `#`）
- 尚未确认的关联聚合嵌套输入（不能把独立 SUM/AVG 转译器与此输入等同）

期望：
- 当前无统一 feature flag 入口；不支持的语法应按实际解析错误断言，不虚构开关
- 若未来实现 → 语义等价于标准形式

---

# Appendix

## A. 操作符别名速查表


| 类别 | 标准操作符 | $前缀 | 无前缀 | 符号 | 长名别名 `behavior-case` |
|------|-----------|-------|--------|------|----------------------|
| 等值 | EQ | `$eq` | `eq` | `=`, `==` | `equal`, `EQUAL` |
| 不等 | NE | `$ne` | `ne` | `!=`, `<>` | `notEqual`, `NOT_EQUAL` |
| 大于 | GT | `$gt` | `gt` | `>` | `greaterThan`, `GREATER_THAN` |
| 大于等于 | GTE | `$gte` | `gte` | `>=` | `greaterThanOrEqual`, `GREATER_THAN_OR_EQUAL`, `GOE` |
| 小于 | LT | `$lt` | `lt` | `<` | `lessThan`, `LESS_THAN` |
| 小于等于 | LTE | `$lte` | `lte` | `<=` | `lessThanOrEqual`, `LESS_THAN_OR_EQUAL`, `LOE` |
| 区间 | BETWEEN | `$between` | `between` | — | — |
| 包含(集合) | IN | `$in` | `in` | — | — |
| 不包含(集合) | NOT_IN | `$notIn` | `notIn` | — | `NOT_IN` |
| 模糊匹配 | LIKE | `$like` | `like` | `~` | `CONTAINS` |
| 反模糊 | NOT_LIKE | `$notLike` | `notLike` | `!~` | `NOT_CONTAINS`, `notContains` |
| 前缀 | STARTS_WITH | `$startsWith` | `startsWith` | — | `LEFT_LIKE`, `leftLike` |
| 后缀 | ENDS_WITH | `$endsWith` | `endsWith` | — | `RIGHT_LIKE`, `rightLike` |
| 为空 | IS_NULL | `$isNull` | `isNull` | — | `ISNULL` |
| 非空 | IS_NOT_NULL | `$isNotNull` | `isNotNull` | — | `ISNOTNULL` |
| 常量 | CONSTANT | `$constant` | — | — | `CONST` |
| 逻辑与 | AND | `$and` | `and` | — | — |
| 逻辑或 | OR | `$or` | `or` | — | — |
| 逻辑非 | NOT | `$not` | `not` | — | — |
| 异或 | XOR | `$xor` | `xor` | — | — |
| 同或 | XNOR | `$xnor` | `xnor` | — | — |
| 任一子记录 | REL_ANY | `$any` | `any` | — | — |
| 全部子记录 | REL_ALL | `$all` | `all` | — | — |
| 无子记录 | REL_NONE | `$none` | `none` | — | — |
| 计数聚合 | COUNT | `$count` | `count` | — | — |
| 求和聚合 | SUM | `$sum` | `sum` | — | — |
| 平均聚合 | AVG | `$avg` | `avg` | — | `AVERAGE` |
| 最大聚合 | MAX | `$max` | `max` | — | — |
| 最小聚合 | MIN | `$min` | `min` | — | — |
| 正则 | MATCHES | — | — | — | `REGEXP`, `REGEX`（无专用 Transpiler） |

---

## B. 设计输入与特殊入口边界


| 特性 | 来源 | 状态说明 |
|------|------|---------|
| 符号关联速记 `@ * ! # + / ^ v` | overview.md 映射表 | 代码未实现 |
| 后缀语法糖 `?/*/#` | overview.md 替代语法提案 | 代码未实现 |
| 包裹语法糖 `[]/{}` | overview.md 替代语法提案 | 代码未实现 |
| 关联聚合 $sum/$avg/$max/$min | overview.md 操作符映射 | Map 聚合比较有实现；关系路径和输入形态须按正式编排合同验证 |
| WITH_RECURSIVE | DefaultWithNormalizer / WithBuilder | 解析构造入口存在；具体数据库转译与执行仍按用例验证 |
| INSERT / UPDATE / DELETE | `DMLStatements` + `SqlDataAdapter` | **已实现**（走独立的 DML 链路，非查询 Normalizer 体系） |
| MATCHES / REGEXP | OperatorAlias 注册 | 别名存在，无专用 Transpiler |
| $raw 原始表达式 | Codex design-only | 代码未确认 |
| ~~$exists → IS_NOT_NULL~~ | ~~Codex design-only~~ | ⚠️ **已废弃**（`$exists` 解析为 `EXISTS` 子查询操作符，不是空值判断） |
| PLAIN_SQL / MODEL_BASED 场景规则 | Codex Section 5 | ⚠️ 代码无对应场景配置入口，属文档先行 |

---

## C. 可执行测试归属

Normalize 行为测试位于 data-core 的 `src/test/groovy/com/ouroboros/data/dsl/normalize`，包括 NormalizerDecisionTreeTest、MapExpressionNormalizeTest、ListExpressionNormalizeTest、AggregationExpressionTest、ClauseNormalizeTest、JoinClauseNormalizeTest、UnionCteNormalizeTest、SubQueryNormalizeTest 和 DslParserGuardrailTest。

关联与组合测试位于 data-test 的 `src/test/groovy/com/ouroboros/data/dsl`，包括 RelationFieldIntegrationTest、RelationOperatorIntegrationTest、JoinCapabilityStrategyTest、PopulateOmitIntegrationTest、UnionCteIntegrationTest、SubQueryIntegrationTest、PairwiseCombinationIntegrationTest、CompositeQueryIntegrationTest、DslEdgeCaseIntegrationTest 和 DslGoldenCaseIntegrationTest。实际覆盖以测试方法的输入、断言与目标提交运行结果为准，不再用旧矩阵近似计数代表测试覆盖率。
