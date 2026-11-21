# LogicFlow 非目标与延后能力（Deferred Capabilities / Non-Goals）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 阅读导航：[`DESIGN-READING-PATHS-AND-MODULE-BOUNDARIES.md`](../reading-paths-and-module-boundaries/overview.md)
>
> 历史审查（2026-03-18）：[`reviews/2026-03-18-report-current-baseline.md`](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-18-report-current-baseline.md)
>
> 场景走查：[`DESIGN-CANONICAL-SCENARIOS-AND-END-TO-END-WALKTHROUGHS.md`](../canonical-scenarios-and-end-to-end-walkthroughs/overview.md)

这份文档的目标不是列 wishlist，而是把下面两类东西明确写死：

- **当前明确不做的事情**
- **当前还没做、但未来可能做的事情**

如果不把这两类内容拆开，后续最容易出现三种漂移：

- 文档里像“已经支持”，代码里其实没有
- 讨论里像“只是还没来得及做”，其实设计上根本不该这样做
- 某些增强项明明要单独立项，却在一次小改动里被偷偷塞进现有语义

## 1. 先分清两类边界：Non-Goal vs Deferred

在 LogicFlow 文档体系里，这两个词必须严格区分：

- **Non-Goal**
  - 当前设计明确不把它当作本模块目标，至少在当前范式下不应直接朝这个方向演进。
- **Deferred**
  - 当前没有实现，但未来可能成为正式能力；前提是先补语义、契约、静态分析和测试。

这条区分的意义非常实际：

- Non-Goal 不应被写进“当前能力只差一点点”
- Deferred 也不应被包装成“其实已经半支持”

## 2. 当前明确的 Non-Goals

### 2.1 不是审批流/BPMN 全量引擎

LogicFlow 当前的定位仍然是：

- 功能点级执行逻辑编排内核

而不是：

- 人参与审批流
- 长事务工作流引擎
- BPMN 语义的完整替代实现

这意味着后续即使继续增强，也不应为了“看起来像流程平台”就把大量状态机、任务待办、会签/转签等能力直接塞进 core。

### 2.2 不是“任何节点都能变成小型调度器”

当前不应把这些能力做成任意普通节点的通用属性：

- 通用 OR-Join / XOR-Join / Any-Join
- 任意节点输入互斥
- 任意节点先到先触发

原因不是“永远不需要 join”，而是：

- 这会把调度语义散落到所有普通节点上
- 会显著提高建模学习成本、运行时复杂度和静态分析难度

所以当前更符合设计原则的方向仍然是：

- **如果要支持，就优先做专用 Join 原语，而不是升级所有普通节点。**

### 2.3 不是“让 editor/dev 反向定义 core 语义”

下面这些都不应成为正式设计路线：

- 用 NodeDefine 替代 runtime truth
- 用 editor fallback 创造 core 尚不支持的语义
- 用前端 warning / 展示状态定义后端 gating

可以有作者态合同，也可以有兼容性 fallback，但它们的地位只能是：

- core 语义的消费者和适配层

而不是：

- 另一套并列的语义来源

### 2.4 不是“为了短期体验把诊断态混成执行态”

当前也明确不应这样做：

- 让 `validationState` 自动改写 `isEnabled`
- 让 invalid 诊断直接掩盖高优先级模型覆盖关系
- 让保存期 warning 冒充 runtime guarantee

原因很简单：

- 诊断态、启用态、执行态是三条不同的线

把它们混起来，短期像是“更省事”，长期一定会把覆盖策略和错误暴露机制搞乱。

## 3. 当前明确 Deferred、但未来可能推进的能力

### 3.1 真正意义上的 terminate/cancel 语义

当前已经有：

- 多成功终点结果语义
- `ReturnNode`
- `FlowOutcome`

当前还没有：

- 命中某个终点后取消其余已调度分支
- flow 级或 scope 级 terminate

所以未来如果要推进这一层，必须把它当作：

- **独立能力**

而不能把它包装成：

- `ReturnNode` 的隐含副作用

### 3.2 本地异常端口

当前错误处理路线是：

- runtime failure
- 标准化 errorContext
- 同图 error scope 恢复

当前未实现：

- 节点级异常输出端口
- 异常输出与普通输出并存时的统一编译语义

这条线未来不是不能做，但必须先回答：

- 它和 error scope 的关系是什么
- 它是否引入新的 join / merge / recovery 语义
- editor 如何表达而不把图复杂度抬得过高

### 3.3 条件层静态分析

当前静态分析以结构层为主，尚不证明：

- switch 条件互斥
- 条件穷尽
- 条件覆盖率

这条能力未来可能有价值，但它的前提是：

- 表达式语义边界足够清楚
- 可接受误报/漏报策略被明确
- editor 与后端诊断口径可对齐

否则它很容易把“辅助分析”做成“半可信解释器”。

### 3.4 专用 Join 家族

当前没有真正成熟的：

- `XorJoinNode`
- `AnyJoinNode`
- `ErrorJoinNode`

但这条线是可能成立的，只是它应当以：

- 专用控制流原语

而不是：

- 普通节点通用属性

的方式推进。

### 3.5 事务边界下放与更细粒度事务语义

当前事务大体仍由 web-adapter 在外层兜底。

未来如果要继续推进：

- flow 内事务边界
- 节点级事务控制
- 与 error scope / 异步节点的事务关系

那它必须先有单独契约，否则很容易和当前“编译型 DAG 执行”语义打架。

## 4. 当前最容易被误写成“好像已经支持”的几件事

### 4.1 “提前退出已经支持了”

不准确。

当前准确说法是：

- 已支持多成功终点结果语义
- 尚未支持真正的 terminate/cancel

### 4.2 “MergeNode 可以做 join”

不准确。

当前准确说法是：

- `MergeNode` 只做 payload merge，不做 control-flow join

### 4.3 “MutexNode 可以作为成熟控制流原语使用”

不准确。

当前准确说法是：

- `MutexNode` 仍是 legacy placeholder，不应成为新设计前提

### 4.4 “validationState 会决定模型是否生效”

不准确。

当前准确说法是：

- `validationState` 只是诊断快照
- 是否参与覆盖关系仍看 `isEnabled`

## 5. 如果未来要把 Deferred 项正式启动，最低要求是什么

任何 Deferred 项要进入正式实现，至少先补齐下面四层：

1. **语义合同**
   - 名字到底是什么，和现有能力怎么区分。
2. **静态分析与运行时边界**
   - 哪些结构在保存期拒绝，哪些留到执行期防御。
3. **authoring / editor / dev contract**
   - 前端怎么表达，后端怎么诊断，二者如何对齐。
4. **测试与夹具**
   - core 回归、authoring 契约测试、跨栈场景至少要有一层能稳住它。

只要这四层没补齐，就不应该在现有能力上做“半支持”的宣传。

## 6. 建议阅读顺序

如果你是为了继续推进未来能力而读这份文档，建议回到下面几份材料交叉阅读：

1. 想看当前到底已经做到哪一步
   - [`reviews/2026-03-18-report-current-baseline.md`](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/core/ouroboros-logicflow/reviews/2026-03-18-report-current-baseline.md)
2. 想看当前原则和总路线
   - [`DESIGN.md`](../../DESIGN.md)
3. 想看真实执行边界是否允许某个 deferred 方向成立
   - [`DESIGN-RUNTIME-EXECUTION.md`](../runtime-execution/overview.md)
4. 想看某个 deferred 项会如何影响节点体系
   - [`DESIGN-CONTROL-FLOW-NODE-CATALOG.md`](../control-flow-node-catalog/overview.md)
   - [`DESIGN-NODE-CAPABILITY-MATRIX.md`](../node-capability-matrix/overview.md)
5. 想看 authoring 与 runtime 如何一起收口
   - [`DESIGN-AUTHORING-CONTRACTS.md`](../authoring-contracts/overview.md)
   - [`DESIGN-NODE-DEFINES-AND-EDITOR-CAPABILITIES.md`](../node-defines-and-editor-capabilities/overview.md)
