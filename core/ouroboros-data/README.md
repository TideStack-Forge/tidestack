# 动态数据模型

[toc]

## . 查询DSL

查询 DSL 是一种用于构建查询语句的语法，抹平不同数据库的方言差异（甚至不同结构的差异，如 SQL 与 NoSql），在保
持简便性的同时，也提供了一种可以更好地控制和描述查询的方式。

查询 DSL 基于 Map/List 结构，通过解析转换为 QueryDSL 的 QueryMetadata，然后借助 QueryDSL 的能力实现对
不同 SQL 数据库方言的操作。当前内置 SQL/JDBC 适配器；NoSQL 属于 SPI 可扩展方向。

查询 DSL 能够序列化存储（JSON、YAML等），以支持配置化

### 1.1. Map/List结构的优缺点

- 构建方便，可以将前端的参数直接映射为查询条件，Groovy也能够很方便的构建如：
  ```groovy
  def query = [
    select: ['id', 'name'],
    from: 'user',
    where: [ department: 1001]
  ]
  ```
- 结构化数据，可以更方便的编程控制，同时也能更好的兼顾配置化控制
- 存在大量的工具库，可以很好的降低开发成本

Map/List 结构的缺点是缺少静态类型检查（当然，这是任何一种想要达到灵活控制的方式的缺点）。

### 1.2 DataAdapter 与 DataModel

查询 DSL 有两个入口层级，管线处理能力不同：

| 能力                | DataAdapter           | DataModel                                       |
|-------------------|-----------------------|-------------------------------------------------|
| 管线路径              | Normalize → Transpile | Normalize → Transform → Analyze → Transform → Orchestration → Transpile |
| Plugin 链          | ✗                     | ✓                                               |
| 关联改写（JOIN/EXISTS） | ✗                     | ✓                                               |
| POPULATE 填充       | ✗                     | ✓                                               |
| OMIT 字段排除         | 仅 Transpile 层兜底       | Normalize 解析 + Orchestration 应用                 |
| 通配符展开（`*`）        | Transpile 层兜底         | Analyze 前的 Transform 展开                                     |
| 字段校验              | ✗                     | ✓（Analyze 层）                                    |

**选择建议**：

- `DataAdapter`：低级 SQL 操作、无需模型语义的场景（如原始表查询、迁移脚本）
- `DataModel`：业务查询入口，需要完整的模型语义处理（Plugin、关联、填充等）

### 1.3 唯一约束

通过 `@UniqueConstraint`、字段唯一属性或模型元数据声明业务唯一性。`ALL_RECORDS` 包含逻辑删除记录，`ACTIVE_RECORDS` 只检查未删除记录；同一模型可以同时使用两种作用域。单条插入、批量插入和部分更新按每条约束的作用域执行，部分更新合并当前完整键值后检查并提示冲突字段。

`nullsDistinct=true` 允许多条包含空值的完整键共存。数据库物理唯一键应与模型的字段、作用域和空值语义一致；更严格的业务唯一性应在模型声明中表达。

## 2. DSL定义

### 2.1. DML —— 增删改

- 插入

*单数据插入*

```groovy
def insertStatement = [
        insert: 'tableA',
        values: [
                field1: 'value1',
                field2: 'value2'
        ]
]

```

*批量插入*

```groovy
def batchInsertStatement = [
        insert: 'tableA',
        values: [
                [
                        field1: 'value11',
                        field2: 'value21'
                ],
                [
                        field1: 'value12',
                        field2: 'value22'
                ],
        ]
]
```

- 更新

```groovy
def updateStatement = [
        update: 'tableA',
        set   : [
                field1: 'value1',
                field2: 'value2'
        ],
        where : [ /* 条件定义 */]
]
```

- 删除

```groovy
def deleteStatement = [
        delete: "tableA",
        where : [ /* 条件定义 */]
]
```

### 2.2. DQL —— 查询

```groovy
def selectStatement = [
        // CTE 定义（支持 WITH / WITH RECURSIVE）
        with     : [ /* 关联定义 */],
        // 是否去重
        distinct : false,
        select   : [
                'id',
                [nameAlias: 'name'],
                /* 字段表 */
        ],
        omit     : [ /* 剔除字段列表 (模型上可用) */],
        from     : [ // 也可以直接设定表名字符串
                     'a': 'tableA'
                     /* 别名或子查询 */
        ],
        // INNER JOIN
        join     : [
                /* 连接表 */
                [
                        b : 'tableB',
                        on: ['b.id': 'a.id']
                ]
        ],
        leftJoin : [ /* 连接表 */],
        rightJoin: [ /* 连接表 */],
        fullJoin : [ /* 连接表 */],
        crossJoin: [ /* 连接表 */],
        populate : [ /* 填充关联字段（模型查询可用） */],
        where    : [ /* 条件定义 */],
        group    : [ /* 分组字段 */],
        having   : [ /* 聚合条件 */],
        order    : [ /* 排序定义 */],
        // 分页
        skip     : 0,
        limit    : 10,
        union    : [ /* UNION 语句 */]
]
```

#### 2.2.1 SELECT 子句

```groovy
// 简单SELECT
def select = ['id', 'name', 'gender']

// 字段别名 [别名: '字段名']
def select2 = ['id', [username: 'name'], [gender: 'gender']]

// 空格别名（简写语法）—— 字段名与别名之间用空格分隔
def select6 = ['id', 'name nickname', 'gender sex']
// 等价于 [别名: '字段名'] 形式：['id', [nickname: 'name'], [sex: 'gender']]

// CASE WHEN
def select3 = ['id', 'name', [gender: ['case', [[gender: 1], '男'], [[gender: 2], '女']]]]

// 聚合函数
def select4 = [[cnt: ['count', [1]]], [sum: ['sum', ['price']]]]

// 子查询结果作为返回字段
def select5 = ['id', 'name', 'gender',
               [
                       orderCount: [
                               select: ['count', 1],
                               from  : 'order',
                               where : ['=', ['order.user'], ['user.id']]
                       ]
               ]
]
// 字段列表中的第一个字段名不能为关键字，如and、or、not等，及标准SQL函数名等
// 可以通过加表别名前缀或者把第一个字段设为 'field' 的方式解决
// 在多表关联查询中，没有指定表名的字段默认都会指向主表
```

#### 2.2.2. FROM 子句

```groovy
// 简单FROM
def from1 = 'tableA'

// 指定表别名
def from2 = [a: 'tableA']

// 子查询
def from3 = [a: [ /* 查询语句 */]]
```

#### 2.2.3. 各 JOIN 子句

```groovy
// 简单JOIN
def join1 = [with: 'tableB', on: ['tableA.fk': 'tableB.id']]

// 指定别名
def join2 = [with: [b: 'tableB'], on: ['a.fk': 'b.id']]

// 复杂条件
def join3 = [with: [b: 'tableB'], on: [ /* S表达式 */]]
```

#### 2.2.4. GROUP 子句

```groovy
// 简单GROUP
def group1 = ['fieldA', 'fieldB']

// 复杂GROUP
def group2 = [[/* S表达式 */]]
```

#### 2.2.5. HAVING 子句

```groovy
// 基于select中的聚合字段
def havingA = [arragedFiledA: ['>': 1000]]

// 使用聚合函数
def havingB = ['>', ['count', ['fieldA']], 1000]
```

#### 2.2.6. ORDER 子句

```groovy
def orderA = ['fieldA', 'fieldB']
def orderB = ['fieldA ASC', 'fieldB DESC']
def orderC = [[fieldA: 'ASC'], [fieldB: 'ASC']]
def orderD = [fieldA: 'ASC', fieldB: 'ASC']
```

#### 2.2.7. Union/UnionAll 子句

```groovy

def union = [ /* SELECT 查询 */]

```

#### 2.2.8. POPULATE 子句（模型专用）

```groovy
def populateA = ['user']
def populateB = [user: [select: [], omit: [], where: []]]

```

#### 2.2.9. OMIT 子句（模型专用）

只能是字段列表，且不支持表达式（因为没有意义）

#### 2.2.10. 关联查询操作符（模型专用）

关联查询操作符用于在 Map DSL 中表达对关联字段（一对多/多对多）的条件查询和聚合统计。
这些操作符在 Plan 阶段被展开为 EXISTS/NOT EXISTS 子查询或相关子查询，Transpile 阶段无需感知关联语义。

##### `$any` — 存在匹配记录

语义：关联字段中存在至少一条满足条件的记录。

```groovy
// Map DSL：订单中存在已发货的订单项
def where = [orderItems: ['$any': [status: 'shipped']]]
```

等价 SQL：

```sql
WHERE EXISTS (
  SELECT 1 FROM order_items sq_orderItems
  WHERE sq_orderItems.order_id = orders.id
    AND sq_orderItems.status = 'shipped'
)
```

##### `$all` — 所有记录匹配

语义：关联字段中所有记录都满足条件（即不存在不满足条件的记录）。

```groovy
// Map DSL：订单中所有订单项都已发货
def where = [orderItems: ['$all': [shipped: true]]]
```

等价 SQL：

```sql
WHERE NOT EXISTS (
  SELECT 1 FROM order_items sq_orderItems
  WHERE sq_orderItems.order_id = orders.id
    AND NOT (sq_orderItems.shipped = true)
)
```

##### `$none` — 无匹配记录

语义：关联字段中没有满足条件的记录。

```groovy
// Map DSL：订单中没有已取消的订单项
def where = [orderItems: ['$none': [status: 'cancelled']]]
```

等价 SQL：

```sql
WHERE NOT EXISTS (
  SELECT 1 FROM order_items sq_orderItems
  WHERE sq_orderItems.order_id = orders.id
    AND sq_orderItems.status = 'cancelled'
)
```

##### `$count` — 关联记录计数

语义：统计关联字段的记录数量，用于 SELECT 聚合。

```groovy
// Map DSL：查询每个订单的订单项数量
def select = ['id', ['$count': 'orderItems']]
def group = ['id']
```

等价 SQL：

```sql
SELECT id, (
  SELECT COUNT(*) FROM order_items sq_orderItems
  WHERE sq_orderItems.order_id = orders.id
) FROM orders GROUP BY id
```

### 2.3. 条件及表达式定义

条件定义由表达式构成，而表达式最常用的是 **常量比较表达式** 和 **逻辑表达式**，此外还有 **复杂表达式**，
比如涉及到函数计算、字段间比较等。

表达式又分为 **Map表达式** 和 **S表达式**，**Map表达式** 主要用于提高效率，**S表达式** 主要用于提高
表达力。

#### Map表达式、S表达式分析

Map表达式的主要价值在于：在 WebAPI 接口中，最高频被定义和使用的接口类型是查询接口，查询接口的参数传递方式
都是键值对，而键值对可以很自然的转化为Map结构，我们还可以很方便的对Map结构进行控制，比如填充缺省参数和覆写
敏感参数。

但 **Map表达式** 也有局限性，即表达能力有限，比如这样的SQL条件：

```sql
select * from user where createdAt = updatedAt;
```

查询表达式要对比两个字段，这时候用Map表达式就很难表达了：

```groovy
def whereA = [createdAt: 'updatedAt']                     // 有歧义，'updatedAt'代表字符串常量还是字段无法通过规则区分
def whereM = [createdAt: [equal: [field: 'updatedAt']]]   // 可接受，但嵌套层次深，不易编写、不易读
```

再复杂一点的例子：

```sql
select * from news where YEAR(createdAt) = YEAR(current_timestamp());
```

```groovy
def whereA = [['year': [[field: 'createdAt']]]: [equal: [year: [[current_timestamp: []]]]]]  // 过于复杂的键，不可接受，并且函数参数依然存在歧义
def whereB = [RAW_EXP: 'YEAR(createdAt) = YEAR(CURRENT_TIMESTAMP())']                        // 如果需要拼接参数，则会留下注入漏洞，尽量还是不要这样做
def whereM = [equal: [[year: [[field: ['createdAt']]]], [year: [current_timestamp: []]]]]    // 可接受，但嵌套层次深，不易编写、不易读
```

由上面的例子可以看出，如果我们把操作符前移，并且把它的参数用列表表示，表达式就会变得稍微简洁一点，手写的时候就容易了一些。
_PS：其实这种语法形式就是M表达式（Meta-expression），第一个例子混合了 **Map表达式** 和 **M表达式** 两种写法_

如果采用 **S表达式**（S-expression）的形式，上面的表达式可以更简洁一些：

```groovy
def whereS = ['equal', ['year', ['field', 'createdAt']], ['year', ['current_timestamp']]] // 相比M表达式，S表达式的括号嵌套层次更浅
def whereS2 = ['equal', ['year', ['createdAt']], ['year', ['current_timestamp']]]
//                                     ^
//                   表达这种非关键词的字段时允许忽略 field 符号
```

通过上面的例子我们可以得出 **S表达式** 的基本形式：`['<符号>', ...参数列表]`。

> 实际上S表达式就是Lisp语言的的基本形式，Clojure 中著名的ORM库 HoneySQL[^HoneySQL] 就是基于S表达式的

因为 **S表达式** 的形式极度简洁，因此解析起来也是非常容易。

**S表达式** 也有缺点，相比于结构化的 **Map表达式**，**S表达式** 是半结构化数据，定位特定查询字段比较麻烦，不方便做查询字段覆写之类的操作。

考虑到使用场景：

- **Map表达式**
  擅长自动处理大量字段的查询需求，快速生成WebAPI[^Sailsjs] [^WixAPI] [^rql] [^rsql]满足绝大多数的 OLTP（在线交易）场景需求。
- **S表达式** 大多数 OLAP（在线分析）的场景，查询比较复杂但是参数较少且参数位置固定，这时候用 **S表达式** 比较合适。

为了不引入更多复杂性，暂 **不支持M表达式**

#### 为什么不直接用SQL表达式？

首先，动态数据模型会提供原生SQL的执行接口，如果要写SQL可以直接调用相关接口。

但是还是要避免直接手写SQL，一是因为手写SQL的时候无法杜绝拼接SQL的使用方式，也就是程序会存在SQL注入风险；二是因为手写的SQL难以兼容不同的数据库；三是对SQL字符串做运行时检查要远高于结构化的数据。

#### 常量比较条件

**比较条件**的基本表达方式：

```groovy
// Map表达式
def mapExpr = [fieldName: ['<操作符>': '常量值']]

// S表达式
def sExpr = ['<操作符>', ['fieldName'], '常量值']

// S表达式的优点是能够表表达复杂的逻辑
def sExprB = ['=', ['left', ['fieldNameA'], 5], ['left', ['fieldNameB'], 5]]
// 等价SQL表达式：WHERE LEFT(fieldNameA, 5) = LEFT(fieldNameB, 5)

```

比较操作符不区分大小写。

绝大多数的操作符都有兼容写法，如表示 `等于条件` 的 equal 兼容符号有"="、"=="等。

还有一些操作符存在便捷写法，如 `等于条件`：

```groovy
// 简便写法
def equalExprA = [fieldName: '比较值']

// 等同于
def equalExprB = [fieldName: [equal: "比较值"]]

```

| 比较操作符              | 兼容写法            | 说明         |
|--------------------|-----------------|------------|
| equal              | =、==、eq         | 等于         |
| notEqual           | !=、&lt;&gt;、ne、 | 不等于        |
| greaterThan        | &gt;、gt         | 大于         |
| greaterThanOrEqual | &gt;=、ge        | 大于等于       |
| lessThan           | &lt;、lt         | 小于         |
| lessThanOrEqual    | &lt;=、le        | 小于等于       |
| between            | between         | 在指定的值之间    |
| in                 | in              | 在指定的值中     |
| notIn              | notIn           | 不在指定的值中    |
| like               | like、contains、~ | 匹配指定的值     |
| notLike            | notContains、!~  | 不匹配指定的值    |
| leftLike           | startsWith      | 匹配指定的值     |
| rightLike          | endsWith        | 匹配指定的值     |
| regexp             | regex           | 匹配指定的正则表达式 |

*其中，多单词的操作符，均支持下划线分割的写法*

**比较条件的简便写法**

- 等于条件：

```groovy
def equalExpr = [fieldNameA: "值"]
```

- in条件：

```groovy
def inExpr = [fieldNameA: ["值1", "值2"]] // 也就是等于表达式
```

- notIn条件：

```groovy
def notInExpr = [fieldName: ['!=': ["值1", "值2"]]] // 也就是不等于表达式
```

#### 逻辑表达式

**逻辑表达式**的表达式方式：

```groovy
// Map表达式
def andExpr = [
        "<AND|OR|NOT|XOR|XNOR>": [
                [fieldA: ['=': '<搜索值>']],
                [fieldB: ['=': '<搜索值>']]
        ]
]
```

逻辑运算符也有简便写法，如下：

**AND/OR 运算符**

```groovy

// 简便写法A（即内部的条件表达式可以简写）：
def andExprA = [
        and: [
                fieldA: '<搜索值>',
                fieldB: '<搜索值>'
        ]
]

def orExprA = [
        or: [
                fieldA: '<搜索值>',
                fieldB: '<搜索值'
        ]
]

// AND操作符在不需要参与其他组合条件的情况下，可以直接使用更简便的写法B：
def andExprB = [
        fieldA: '<搜索值>',
        fieldB: '<搜索值>'
]
```

NOT 运算符支持多条件的写法，隐含了先将条件组合成一个 AND 条件，然后再使用 NOT 操作符：

```groovy
def notExprA = [
        not: [
                fieldA: "searchValue",
                fieldB: "searchValue"
        ]
]
```

| 逻辑运算符 | 兼容写法 | 说明 |
|-------|------|----|
| and   | &&   | 并且 |
| or    | &#124;&#124; | 或者 |
| not   | !    | 取反 |

#### 复杂条件（S表达式）

```groovy
// 1. （S表达式）字段间比较，比如：createdAt != updatedAt
def sExpr = ['!=', ['createdAt'], ['updatedAt']]

// 2. （S表达式）函数
def sExpr2 = ['=', ['year', ['createdAt']], ['year', ['now']]]

// 3. （S表达式）IN条件的多字段子查询
def sExpr3 = ['in', ['a', 'b'], [select: ['a', 'b'], from: 'tableC', where: [id: ['>': 1]]]]
//                     ^
//              同 SELECT 语法

// 4. CASE WHEN（S表达式）
def sExpr4 = ['case',
              ['when', ['=', ['gender'], 1], '男'],
              ['when', ['=', ['gender'], 2], '男'],
              ['when', [], '未知']
]
// CASE WHEN 简写
def sExpr5 = ['case',
              [[gender: 1], '男'],
              [[gender: 2], '女'],
              [[], '未知']
]


// S表达式、Map表达式混写
def sExprMapExpr1 = ['and', [department: 1001], ['>', ['age'], 30]]
def mapExprSExpr1 = ['and': [[department: 1001], ['>', ['age'], 30]]]
// 等价Map表达式：
def mapExpr1 = [department: 1001, age: ['>': 30]]
// 等价SQL表达式：
// department = 1001 AND age > 30


```

## 3. 当前能力与演进边界

单表 DML、GROUP/HAVING、JOIN、POPULATE、子查询、WITH/递归 CTE 与 UNION/UNION ALL 已有相应实现入口；SQL 可执行范围还受数据源方言与模型语义限制。不同子句的 Map/List 输入有独立解析路径，不能把操作符存在等同于任意位置均接受该写法。

CASE 等底层转译能力与原始字符串 DSL 入口分开核对；例如通用表达式 normalizer 不为 SELECT 提供同样的入口，CASE 字符串别名也不能由转译器存在推断。具体预期与已知差距见[DSL 用例矩阵](design/dsl-test-cases/dsl-test-cases.md)。窗口函数尚无统一 DSL 实现入口，属于演进方向。

## 4. 参考资料

[^HoneySQL]: [Honey SQL](https://github.com/seancorfield/honeysql)

[^WixAPI]: [Wix - API Query Language](https://dev.wix.com/api/rest/getting-started/api-query-language)

[^Sailsjs]: [Sails.js - find where](https://sailsjs.com/documentation/reference/blueprint-api/find-where)

[^rql]: [Resource Query Language for REST](https://github.com/a8m/rql)

[^rsql]: [REST Query Language with RSQL](https://www.baeldung.com/rest-api-search-language-rsql-fiql)
