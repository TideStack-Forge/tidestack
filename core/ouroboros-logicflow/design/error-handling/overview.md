# LogicFlow 错误处理与恢复语义（DesignError/RuntimeError/Error Flow）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)
>
> 错误流专题：[`DESIGN-ERROR-FLOW-RECOVERY-AND-FAIL-CLOSED-BOUNDARIES.md`](../error-flow-recovery-and-fail-closed-boundaries/overview.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“错误流、异常、终点语义”的深化说明，重点把以下边界讲清楚：

- 设计期错误（invalid model）和运行期错误（runtime failure）如何区分
- 哪些失败会进入 `errorEntryNode` 对应的错误流（error flow）
- 哪些节点是“异常终点”但不是“成功终点”
- error flow 在什么条件下能把失败恢复成成功
- editor 的异常终点视觉语义为什么必须与 runtime 语义一致

本文件描述的是“当前实现已落地的行为契约”，不是未来方案提案。

核心入口：

- 设计期模型收口：[`DefaultFlowModel`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/model/DefaultFlowModel.java)
- 运行期执行/错误流切入：[`FunctionalFlowCompiler`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/compiler/FunctionalFlowCompiler.java)
- 运行时错误：[`FlowRuntimeException`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/exception/FlowRuntimeException.java)
- 校验失败异常：[`ValidationFailedException`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/exception/ValidationFailedException.java)
- 异常终点节点：[`OuroborosExceptionsNode`](../../ouroboros-logicflow-extra-nodes/src/main/java/com/ouroboros/logicflow/nodes/OuroborosExceptionsNode.java)

## 1. 三层错误：先分层，再谈恢复

当前 LogicFlow 的错误大致分成三层：

1. **设计期错误（Design-time errors）**
   - 模型结构、端口、类型、scope、终点、可达性等不满足约束。
   - 表现为 `DesignException` 聚合到 `FlowModel.getErrors()`。
   - 这类错误不会进入 error flow，因为模型根本不应被执行。
2. **运行期错误（Runtime failures）**
   - 节点执行抛异常、端口契约违约、参数数量不正确等。
   - 表现为 `FlowRuntimeException` 或其 cause。
   - 只有 main scope 已开始执行的 failure 且存在 error scope 才尝试恢复；缺参导致 started=false 时直接失败。
3. **业务显式失败（Explicit business failure nodes）**
   - 某些节点本身就是“故意抛异常”的业务表达，例如 `OuroborosExceptionsNode`。
   - 它们在 runtime 上表现为 failure，不是 success terminal。
   - 是否能被恢复，取决于 error flow 是否存在且是否成功收口。

这三层一定要分开理解，否则很容易出现两个误判：

- 把 invalid model 当成“可运行但会被 error flow 兜住”
- 把异常终点节点误认为“另一种成功终点”

## 2. 设计期错误：invalid model 不会进入 error flow

### 2.1 为什么 invalid model 不能靠 error flow 挽救

`DefaultFlowModel` 在构造后会立即收集并固化模型错误：

- 节点构造错误
- 连线端口越界
- scope 错误
- 成功终点缺失/冲突
- 类型不兼容
- 等等

如果模型 invalid：

- `executeFunction` 会被替换成一个固定失败函数
- 返回的是 `FlowDesignException`

因此：

- invalid model 属于“执行前就失败”
- 这种失败不会进入 `FunctionalFlowCompiler` 的 main scope
- 自然也不会进入 error flow

这条边界非常重要，因为它保证了：

- error flow 只处理“运行中失败”
- 不会把模型设计错误误包装成业务异常

## 3. 运行期错误：什么会被当成 runtime failure

### 3.1 节点执行异常会被统一包装

编译器在调用 `node.execute(...)` 时会统一处理异常：

- 如果节点已经抛出 `FlowRuntimeException`，则直接沿用
- 否则包装成 `FlowRuntimeException("执行节点失败", cause, node, inputs)`

这样做的意义是：

- 每个 runtime failure 都尽量带上 `node + inputs`
- 后续 error flow / analyzer /日志 都能拿到统一定位信息

### 3.2 典型 runtime failure 来源

运行期失败来源很多，常见包括：

- 节点内部显式抛 `FlowRuntimeException`
  - 例如 `AbstractSimpleNode` 在输入为空时抛“节点没有输入值”
- 业务校验失败
  - 例如 `ValidateNode` 校验失败后抛 `ValidationFailedException`
- 业务显式抛错节点
  - 例如 `OuroborosExceptionsNode` 抛 `BusinessException/PlatformException/InternalException`
- 编译器运行时契约错误
  - 例如 selective-output 同一 `exclusiveGroupId` 同时命中多个输出

这几类错误都会被当成 main scope failure 处理。

## 4. ValidateNode：它不是“分支失败”，而是直接 runtime failure

`ValidateNode` 的当前语义很明确：

- schema 校验通过：原值透传
- schema 校验失败：抛 `ValidationFailedException`

代码：

- [`ValidateNode`](../../ouroboros-logicflow-builtin-nodes/src/main/java/com/ouroboros/logicflow/nodes/ValidateNode.java)
- [`ValidationFailedException`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/exception/ValidationFailedException.java)

这意味着：

- `ValidateNode` 当前不是“有 success/fail 两个输出端口”的分支节点
- 它的失败语义是“终止当前主路径并进入 runtime failure 流程”
- 如果模型声明了 `errorEntryNode`，它的失败可以由 error flow 接住

这与某些工作流产品常见的“校验失败走另一条边”不同，是当前设计刻意保持简单的一种收口方式。

## 5. 异常终点节点：是 terminal，但不是 successful terminal

### 5.1 OuroborosExceptionsNode 的真实语义

`OuroborosExceptionsNode` 继承自 `AbstractTerminalNode`，但它的 `doExecute(...)` 永远是：

- 抛 `BusinessException`
- 或抛 `PlatformException`
- 或抛 `InternalException`

代码：

- [`OuroborosExceptionsNode`](../../ouroboros-logicflow-extra-nodes/src/main/java/com/ouroboros/logicflow/nodes/OuroborosExceptionsNode.java)

因此：

- 它是“图上的终点样节点”（0 输出端口）
- 但它不是 success terminal
- 它进入终点家族候选索引，但执行抛错，不能形成成功 `FlowOutcome`

### 5.2 为什么 editor 要把它画成 exception terminal，而不是 return terminal

NodeDefine 已明确把它标成：

- `isTerminal = true`
- `terminalKind = exception`

资源：

- [`OuroborosExceptionNode.flow-node.json`](../../ouroboros-logicflow-extra-nodes/src/main/metadata/OuroborosExceptionNode.flow-node.json)

editor 侧也会据此显示为 `ERROR` 而不是 `RETURN`：

- [`displayState.mjs`](../../../../frontends/web/ouroboros-logicflow-editor/src/node-define/displayState.mjs)

这套视觉契约必须与 runtime 语义一致，否则建模者会误以为：

- 这个节点也是“正常结束流程”的一种方式

而实际上它表达的是：

- “显式制造一个失败”

## 6. Error Flow：什么时候触发，输入是什么

### 6.1 触发条件

error flow 只在以下条件同时满足时触发：

1. main scope 已经开始执行（started=true）
2. main scope 的最终 outcome 是 failure
3. 模型声明了 `errorEntryNode`

所以这些情况不会进 error flow：

- 模型 invalid（设计期错误）
- 输入参数数量不正确导致 scope 根本没开始
- 模型未启用

### 6.2 errorContext：错误流接收到的是“标准化上下文”，不是原始异常对象

进入 error flow 时，编译器会构造一个 `Map<String, Object>` 作为唯一输入：

- `exceptionType`
- `exceptionMessage`
- `exceptionCode`
- `flowName`
- `flowInputs`
- `nodeName`
- `nodeInputs`

这是一份“标准化错误上下文”，目的是：

- 让错误流节点用统一数据结构消费失败信息
- 避免把各种 Java 异常对象直接泄露为图上的数据契约

这也是为什么静态分析要求：

- `errorEntryNode` 的实际输入端口契约必须兼容 `Map<String, Object>`

## 7. Error Flow 如何“恢复”为成功

### 7.1 恢复不是自动的，必须命中成功终点

error flow 的目标不是“有了 errorEntryNode 就天然能兜底成功”，而是：

- 给失败一次“用另一条图重新收口”的机会

当前 error flow 想把失败恢复为成功，必须满足：

- error scope 自身存在成功终点
- 并且本次执行命中了那个成功终点

当前成功终点依然是同一套规则：

- `ReturnNode`
- 或该 scope 下的显式 success terminal 约定（当前 error scope 通常依赖 `ReturnNode`）

### 7.2 没命中成功终点：fail-closed

如果 error flow 虽然执行了，但最终没有命中成功终点：

- 会按 fail-closed 处理
- 不会默默把错误吞掉并返回 `null`

这条规则在回归测试里有明确覆盖：

- `main-failure-no-recovery-terminal.json`
- 对应测试：[`FlowModelErrorFlowExecutionTest`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelErrorFlowExecutionTest.java)

### 7.3 error flow 自己再失败：不会递归重入

如果 error flow 里的节点再次失败：

- 整个流程以 error flow 的最终 failure 结束
- 不会再次回到 `errorEntryNode` 形成递归错误处理

这也是当前设计里非常关键的一个“防爆炸”边界。

## 8. 主流程成功时：error flow 完全忽略

如果 main scope 正常命中成功终点：

- error flow 不会被执行
- 即使图里存在完整的 error scope，也只是“备用恢复路径”

这条规则保证了：

- error flow 是失败恢复机制，而不是主流程的一部分

## 9. Analyzer 与最终结果：关注“最终态”，不是中间异常

运行时分析器拿到的是流程最终态：

- 若 error flow 恢复成功：`onFlowCompleted` 收到的是**恢复后的成功结果**
- 若 error flow 失败：`onFlowFailed` 收到的是**error flow 的最终失败**

相关回归：

- [`FlowModelRuntimeAnalyzerCompatibilityTest`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelRuntimeAnalyzerCompatibilityTest.java)

这意味着 analyzer 的语义是：

- 面向“流程最终外部可见结果”
- 不是“任何中间节点一失败就把原始失败直接上报为最终失败”

## 10. Error Flow 与异常终点节点的组合

把 `OuroborosExceptionsNode` 放进图里时，有两种典型用法：

1. **主流程里显式抛错**
   - 作用：主动制造一个业务失败，转入 error flow
2. **error flow 里再次抛错**
   - 作用：明确声明“这里不恢复，继续失败”

第二种用法特别重要，因为它让 error flow 的“无法恢复”也能保持显式建模，而不是依赖隐式异常。

## 11. 测试视角：当前错误处理契约有哪些回归保护

核心回归用例大致覆盖了这些问题：

- 主流程失败后，error flow 能恢复成功
- 主流程失败后，error flow 自己也失败
- 主流程成功时，不触发 error flow
- error flow 能收到标准化错误上下文
- error flow 没命中成功终点时 fail-closed
- error flow 失败不会递归重入
- errorEntryNode 的 scope/类型契约正确

相关测试入口：

- [`FlowModelErrorFlowExecutionTest`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelErrorFlowExecutionTest.java)
- [`FlowModelErrorScopeValidationTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelErrorScopeValidationTest.java)
- [`FlowModelTypeCompatibilityValidationTest`](../../ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/FlowModelTypeCompatibilityValidationTest.java)

## 12. Deferred：当前未做的错误处理能力

以下能力当前仍然是 Deferred：

1. **节点级本地异常端口**
   - 现在的恢复机制是全局 `errorEntryNode`，不是每个节点都有 catch/else 异常边。
2. **保留原始异常对象作为图数据**
   - 当前只提供标准化 `errorContext`，避免把 Java 异常对象直接暴露为契约。
3. **error flow 的多级嵌套恢复**
   - 当前明确禁止递归重入。
4. **事务级错误恢复语义**
   - 当前事务仍主要在 adapter 层兜底，尚未与 error flow 做更细粒度组合。
