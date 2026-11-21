# Orchestration 查询示例

下列示例说明查询语义与编排结果，模型和数据是教学夹具，SQL 为逻辑示意。机制只在[共同设计](overview.md)维护，策略边界见[策略详解](strategies.md)。示例不提供另一份 QueryStatement JSON 序列化合同或可直接复制的 QueryStep 构造代码。

## 1. 完整示例：跨源条件 + 同源条件 + 跨源填充

Employee、Project 在站点 A，Department 在站点 B。Employee.department 指向 Department，Employee.project 指向 Project，均为 ToOne。

```groovy
def statement = [
    where: [department: [name: '开发部'], project: [status: 'active']],
    populate: ['department']
]
// 交给 Employee 的 DataModel.query(statement)
```

编排先由 CrossSourceRewriteStep 查询 B 中满足条件的 Department 主键并登记改写器。同源 project 条件登记 JoinStatementRewriter。主查询前执行 JOIN 去重、嵌套关系改写和语句准备；MainQueryStep 应用改写器后查询 A。department 填充走 Separate；PopulateStep 查询关联记录、登记转换器，ApplyTransformersStep 最后将其合并到主结果。

若 B 返回 101、102，则 A 的 department 条件语义为 IN (101,102)；具体空值、NONE/ALL 和分批规则见共同设计与实现，不能把所有跨源条件都简化为 IN。

## 2. 同源 Populate（JOIN 策略）

Employee 与 Department 可 JOIN，department 为 ToOne、无子级填充时：

```groovy
def statement = [populate: ['department']]
```

默认选择器使用 JoinPopulateStrategy。PopulateJoinRewriter 添加 LEFT JOIN 与带前缀投影，MainQueryStep 获取扁平结果；ResultTransformStep 登记嵌套转换，ApplyTransformersStep 统一应用。关联缺失时应检查 NULL 结果，不把 LEFT JOIN 视为关联必定存在。

## 3. 同源 ToOne 关联条件（JOIN 改写）

```groovy
def statement = [where: [department: [name: '开发部']]]
```

普通肯定条件可用 INNER JOIN；需要保留 NULL 记录的逻辑组合由 RelationConditionPlanner 分析 requiresLeftJoin 后选择 LEFT JOIN。不能对所有 ToOne 条件一律使用 INNER JOIN。JOIN 不重复主记录依赖 ToOne 关系键的真实唯一性。

## 4. 同源 ToMany 关联条件（EXISTS 改写）

Order.items 为 ToMany：

```groovy
def statement = [where: [items: ['$any': [quantity: ['$gt': 10]]]]]
```

Normalize 产生关系操作符，编排将其改写为带关联键条件的 EXISTS 子查询；`$none` 对应不存在满足条件的记录，`$all` 对应不存在违反条件的记录。改写发生在 Orchestration，Transpile 消费改写后的普通子查询表达式。NULL 和空集合行为应由场景测试覆盖。

## 5. 多个 Populate 的链式传递

```groovy
def statement = [populate: ['department', 'manager']]
```

多个填充步骤从主结果提取关联键，登记各自转换器；它们不依赖前一个填充步骤已经生成的嵌套记录。最终由 ApplyTransformersStep 按登记顺序累积结果，SequentialPlan 读取显式 finalStepName（有填充时为 apply_transformers）。

子级填充另用嵌套 populate 配置，由关联模型 query 继续编排；默认选择器对有子级的填充使用 Separate。当前计划顺序执行，延迟转换不等于已有并发调度。

## 6. JOIN 去重优化示例

当同一个 department 同时用于条件和 Populate 时，条件 JOIN 与填充 JOIN 可能等价。JoinDeduplicator 根据目标表及规范化 ON 条件分组，组内优先 LEFT JOIN，并替换 WHERE/SELECT 的旧别名。

- 条件和填充生成的等价 JOIN 可以合并；同表不同 ON 条件必须分别保留。
- 用户显式 JOIN 只有符合相同语义签名时才可合并。
- department、manager 都指向同表但关联键不同，不能只按表名合并。
- 子查询 JOIN 保留；此实现不重写 ORDER BY 别名，使用被替换别名的排序应单独核对。

这六类示例只说明预期与代码路径；实际运行范围见[场景矩阵](scenarios.md)及目标提交的测试结果。
