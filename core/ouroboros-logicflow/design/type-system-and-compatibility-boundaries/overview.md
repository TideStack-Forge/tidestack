# LogicFlow 最小类型系统与类型兼容边界（Type System / Compatibility Boundaries）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 端口与数据契约总论：[`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
>
> 静态分析：[`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
>
> 成功终点边界：[`DESIGN-SUCCESSFUL-TERMINALS-AND-EARLY-EXIT-BOUNDARIES.md`](../successful-terminals-and-early-exit-boundaries/overview.md)

这份文档单独拆的是 LogicFlow 里另一条很容易被“看起来只是几个字符串枚举”低估掉的边界：

- `DataTypeEnum`
- `FlowModelMeta.inputDataType/outputDataType`
- 端口 `dataType`
- `LogicFlowUtil.validateInputTypeCompatibility(...)`

如果说 [`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md) 更像“数据契约总论”，这份文档更像：

- **最小类型系统、类型兼容规则、以及这些规则怎样在 entry / errorEntry / 成功终点 / 普通连线四个边界上生效的专题说明**

## 1. 先给结论：当前是“最小兼容系统”，不是完整类型推断系统

当前类型系统最重要的事实可以先压缩成六句话：

1. LogicFlow 当前只有一套很小的内建类型集合，由 `DataTypeEnum` 表达。
2. 它的主要职责是“兼容性检查”，不是“完整类型推断”。
3. `ANY` 是明确存在的宽松兜底类型，而且未知类型字符串会回退到 `ANY`。
4. 当前类型兼容主要在四个位置检查：
   - 流程输入 vs main scope 所有根节点输入端口
   - error scope 所有根节点输入合同
   - 成功终点 vs 流程输出
   - 图内连线两端端口
5. `ReturnNode` 与显式 `exitNode` 已经按同一套流程输出合同收口，但看的端口不一样：
   - `ReturnNode` 看 input port 0
   - `exitNode` 看 output port 0
6. 当前没有结构化 schema 推断、条件分支类型收窄、集合元素类型分析等更强能力。

如果只记一句口径，应该记这个版本：

- **LogicFlow 当前有类型兼容检查，但没有完整类型推断系统。**

## 2. 当前类型合同至少分成四层

### 2.1 流程外部合同：`inputDataType / outputDataType`

流程级类型合同入口在：

- [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)

它表达的是：

- 外部调用者向这张图喂什么类型的数据
- 这张图对外应产出什么类型的结果

这里最重要的边界是：

- 这是一份**流程对外合同**
- 不是单个节点的内部实现细节

### 2.2 端口局部合同：`FlowNodePortMeta.dataType`

节点端口的 `dataType` 当前表达的是：

- 这个槽位期望接收或产出什么类型

它是图内数据流的局部合同。

### 2.3 连线兼容合同：source port -> target port

连线不会自己携带类型，但它把两个端口的类型合同接在了一起：

- 上游输出端口类型
- 下游输入端口类型

这就是图内最基础的“边契约”。

### 2.4 成功终点结果合同：终点如何对齐 `outputDataType`

流程对外的 `outputDataType` 最终不会自己落地，而是通过成功终点收口：

- 显式 `exitNode`
- `ReturnNode`

这一层当前已经是成功终点语义和类型合同的交叉边界。

## 3. `DataTypeEnum` 当前到底定义了什么

### 3.1 类型集合很小，而且是故意的

当前内建类型定义在：

- [`DataTypeEnum`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DataTypeEnum.java)

它包括：

- `string`
- `integer`
- `long`
- `float`
- `double`
- `boolean`
- `date`
- `datetime`
- `time`
- `object`
- `array`
- `map`
- `any`

这说明当前系统的目标不是表达一个丰富到接近 schema 的类型宇宙，而是：

- **提供一套足够小、足够稳定的兼容性基线**

### 3.2 当前兼容规则其实非常克制

`DataTypeEnum.isCompatible(...)` 当前只有三条核心规则：

1. 同类型兼容
2. 任一侧是 `ANY` 时兼容
3. 数值类型之间互相兼容

除此之外，其他类型默认都按“不兼容”处理。

这意味着当前并不存在：

- `object` 自动兼容 `map`
- `array` 自动兼容 `object`
- 日期/时间家族的隐式兼容
- 任何更复杂的父子类型体系

### 3.3 未知类型字符串会退回 `ANY`

`DataTypeEnum.fromValue(...)` 对未知字符串当前会回退到：

- `ANY`

这是一条很重要、也很容易被忽略的兼容性边界。

它的好处是：

- 降低存量模型因为陌生字符串直接 fail-closed 的概率

它的代价是：

- 类型拼写错误可能被悄悄变宽
- authoring 侧如果把未知字符串当成“严格类型”，会和 core 的真实语义发生偏差

所以当前正确理解应当是：

- **未知类型不是被拒绝，而是被放宽成 `ANY`。**

## 4. 类型兼容当前在哪些边界上真正生效

### 4.1 流程输入 vs main scope 根节点输入端口

`LogicFlowUtil.validateInputTypeCompatibility(...)` 当前首先会检查：

- `FlowModelMeta.inputDataType`
- 是否兼容 main scope 中每个无有效输入连线根节点的所有输入端口 `dataType`

这条规则表达的是：

- 流程输入类型是对外合同
- 这些根节点按位置接收流程输入，包括显式 entry 之外的根节点，是外部输入进入图内部的类型边界。

### 4.2 error scope 根节点的输入类型合同

error scope 中每个无有效输入连线的根节点（包括 `errorEntryNode`）都遵循以下合同：

- 最多只能有 1 个输入端口
- 且该端口必须兼容 `MAP`

原因并不复杂：

- 当前 error scope 接收的是标准化 `errorContext`
- `errorContext` 当前本质上是 `Map<String, Object>`

这里要特别注意一点：

- 这条规则看的也是节点**实际 runtime 输入端口面**
- 不是只看 authoring 侧“打算给它几个输入端口”

`FlowModelTypeCompatibilityValidationTest` 当前已经锁住了：

- `error-entry-type-mismatch.json`
- `error-entry-actual-multi-input-node.json`

这两类场景。

### 4.3 成功终点 vs 流程输出类型合同

当前成功终点输出兼容检查已经统一成同一条规则：

- 最终都必须兼容 `FlowModelMeta.outputDataType`

但具体看的端口不同：

- 对显式 `exitNode`
  - 看 output port 0
- 对 `ReturnNode`
  - 看 input port 0

这一点非常关键，因为它不是“实现细节”，而是当前成功结果合同的一部分。

`FlowModelTypeCompatibilityValidationTest` 已经覆盖了：

- `return-only-type-mismatch.json`
- `disabled-exit-type-mismatch-ignored.json`
- 多输出显式 `exitNode` 只看返回槽位这一类构造场景

### 4.4 图内连线两端的端口类型兼容

对图内普通连线，当前检查的是：

- source output port `dataType`
- 是否兼容 target input port `dataType`

这一步是图内部最基础的类型自洽检查。

如果这一步不成立，说明问题不是流程外部合同，而是图本身已经不自洽。

`type-mismatch-basic.json` 就锁住了这一类最基本的失败场景。

## 5. 这些检查不是对 raw metadata 做的，而是对 runtime 端口面做的

这是当前最容易被忽略、但必须和上一轮“端口物化边界”一起理解的点。

### 5.1 检查依赖的是 `FlowNode.getInputPorts()/getOutputPorts()`

无论是：

- main scope 根节点输入合同
- error scope 根节点输入合同
- 成功终点输出合同
- 图内连线两端合同

当前最终都是通过：

- `FlowNode.getInputPorts()`
- `FlowNode.getOutputPorts()`

拿到端口类型。

这意味着类型兼容检查最终依赖的是：

- **runtime 物化后的端口面**

而不是：

- raw metadata 列表长度

### 5.2 为什么这很重要

因为这直接影响两个判断：

1. 某个端口是否存在
2. 某个端口的最终 `dataType` 是从 metadata 继承，还是从补齐逻辑落成 `ANY`

所以当前类型系统不能脱离：

- `AbstractNode` 的端口物化规则
- `DefaultFlowModel` 的装配顺序

单独理解。

## 6. 类型兼容检查为什么会刻意忽略“坏连线”的级联噪音

### 6.1 当前只消费 well-formed active connections

`LogicFlowUtil` 当前在图内类型兼容检查时，只会消费：

- active
- well-formed
- 端口索引仍合法

的连线。

这意味着：

- 如果连线本身已经越界或结构不完整
- 当前会先把它判成连接非法
- 而不是再进一步制造一串派生的类型错误

### 6.2 这是一种故意的“先修结构，再谈类型”的策略

`FlowModelConnectionValidationTest` 已经锁住：

- `invalid-connection-should-not-cascade-input-conflict.json`

这一类“无效连接不应继续制造级联噪音”的行为。

虽然这个测试文件名主要提到 input conflict，但同一个测试也明确要求：

- 类型兼容检查不要对坏连线继续报噪音

所以当前更准确的理解应当是：

- **结构错误优先，类型错误不做级联放大。**

## 7. 当前类型系统不是什么

这条边界同样重要，因为很多后续设计歧义都是把“已有兼容检查”误读成“已经有强类型系统”。

当前它还不是：

- 完整 schema 推断系统
- 结构化对象字段级类型系统
- 集合元素类型系统
- 条件分支后的类型收窄系统
- 基于表达式分析的 refined typing 系统
- 未知类型字符串的严格拒绝系统

这也是为什么当前关于类型的正确表述只能是：

- **最小兼容系统**

而不是：

- **强类型低代码图系统**

## 8. 当前最容易犯的几类误读

1. **把 `ANY` 当成严格类型**
   - 错，`ANY` 当前是宽松兜底与兼容放宽。
2. **认为未知类型字符串会被后端直接拒绝**
   - 错，当前会回退到 `ANY`。
3. **认为 `ReturnNode` 和显式 `exitNode` 看的都是输出端口**
   - 错，`ReturnNode` 当前看 input port 0。
4. **认为类型兼容只看 raw metadata**
   - 错，当前看的是 runtime 端口面。
5. **认为 editor 写了某个类型名，就等于 core 一定按这个严格类型执行**
   - 错，core 仍可能把未知字符串放宽成 `ANY`。

## 9. 对 authoring / editor 的直接含义

当前这条边界对 authoring 侧至少有三条直接约束：

1. 不要把未知类型字符串当成“已经建立了严格合同”
2. 不要把 authoring 侧端口列表直接当成 runtime 类型检查的最终端口面
3. 不要把前端的类型显示或编辑控件写成比 core 更强的语义承诺

换句话说，当前 editor 最合理的角色仍然是：

- 展示类型合同
- 帮助作者尽量写对
- 让错误更早被看见

而不是：

- 重新发明一套独立于 core 的强类型解释器

## 10. 如果未来要增强这条线，最合理的顺序是什么

如果未来确实要继续增强类型系统，当前更合理的顺序应当是：

1. 先定义哪些更强类型能力真有稳定价值
2. 再决定这些能力是落在流程级合同、端口级合同，还是表达式/条件层
3. 最后才考虑 editor/dev 是否要同步升级 authoring contract

而不是反过来：

1. 先在 editor 里增加很多类型 UI
2. 再要求 core 去追前端假设

这条顺序的原因很简单：

- 当前 core 才是类型兼容语义的权威

## 11. 实际读代码时，推荐的观察顺序

如果你是第一次从这条线进入代码，建议按这个顺序看：

1. [`DataTypeEnum`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DataTypeEnum.java)
2. [`FlowModelMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowModelMeta.java)
3. [`FlowNodePortMeta`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/FlowNodePortMeta.java)
4. [`LogicFlowUtil.validateInputTypeCompatibility`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/util/LogicFlowUtil.java)
5. [`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)
6. [`FlowModelTypeCompatibilityValidationTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelTypeCompatibilityValidationTest.java)
7. [`FlowModelConnectionValidationTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelConnectionValidationTest.java)

配套阅读建议：

- 端口与数据合同总论：[`DESIGN-PORTS-AND-DATA-CONTRACTS.md`](../ports-and-data-contracts/overview.md)
- 端口物化边界：[`DESIGN-AUTHORING-PORTS-AND-RUNTIME-PORT-MATERIALIZATION-BOUNDARIES.md`](../authoring-ports-and-runtime-port-materialization-boundaries/overview.md)
- 成功终点输出合同：[`DESIGN-SUCCESSFUL-TERMINALS-AND-EARLY-EXIT-BOUNDARIES.md`](../successful-terminals-and-early-exit-boundaries/overview.md)
- 静态分析总览：[`DESIGN-STATIC-ANALYSIS.md`](../static-analysis/overview.md)
