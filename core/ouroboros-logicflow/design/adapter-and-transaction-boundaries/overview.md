# LogicFlow Adapter 与事务边界（Runtime/Web Adapter）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“runtime 集成”“web-adapter 调用边界”“事务暂存于 adapter 层”几部分的深化说明。它专门回答以下问题：

- LogicFlow 在进入平台运行时后，是如何从模型元数据走到一次真实 HTTP 调用的。
- `runtime` 和 `web-adapter` 当前各自承担什么职责，哪些语义仍然属于 `core`。
- 为什么当前事务仍停留在 adapter 外层，以及这套做法的真实边界是什么。
- 哪些方向已经明确是 Deferred，不能再被文档误写成“现已支持”。

这份文档只描述“当前已实现事实”和“明确延后边界”，不把未来愿景包装成现状。

## 1. 为什么这条边界值得单独成章

LogicFlow 的核心语义已经不再只是“单入口、单出口、单错误”：

- `core` 内部已经有 `FlowOutcome`
- 主流程与错误流程已经可以分 scope 执行
- 成功终点已不仅只有显式 `exitNode`

但只要流程进入平台运行时，外层调用方看到的仍是一条更窄的合同：

- `FlowModel.execute(Object...): Try<Object>`
- `HandlerFunctionAdapter` 统一包事务
- 最终收口为 `WebApiResult`

因此 adapter/runtime 这一层的价值，不是“再实现一遍 core”，而是把更丰富的引擎语义安全地压缩成平台今天还能消费的外部合同。

这条边界如果不写清楚，后续最容易出现两类认知漂移：

1. 误以为 `core` 已经支持的语义，上层也已经能区分并消费。
2. 为了短期联调方便，直接在 adapter 层补特判，反过来污染核心设计原则。

## 2. 当前 Web 执行链

`HandlerFunctionAdapter.buildHandler(flowName, transactionManager, executionFacade)` 返回请求 handler。handler 构造 `RequestInfoMap`，开启 `PROPAGATION_REQUIRED` 事务，然后调用 `LogicFlowExecutionFacade.execute(flowName, List.of(requestInfo))`。

默认 [DefaultLogicFlowExecutionFacade](../../ouroboros-logicflow-runtime/src/main/java/com/ouroboros/logicflow/DefaultLogicFlowExecutionFacade.java) 统一检查名称、经 `FlowModelCenter` 选模、拒绝设计错误，再调用模型 `execute()`；adapter 将 `LogicFlowExecutionGateException` 映射为 HTTP 400。adapter 不再直接选模或维护 `checkFlowModel`。

## 3. Runtime 的模型来源与发布

模型来源、优先级、批次准备、刷新期完成态快照和按需缓存统一维护在[模型生命周期](../model-lifecycle/overview.md)。其中按名懒建仅在 metadata 代完成发布后允许；刷新期不能绕过候选初始化边界。

## 4. Web Adapter 侧：当前承担的四个职责

当前 `web-adapter` 模块非常小，核心只有两个类：

- [`HandlerFunctionAdapter`](../../ouroboros-logicflow-web-adapter/src/main/java/com/ouroboros/logicflow/adapter/HandlerFunctionAdapter.java)
- [`RequestInfoMap`](../../ouroboros-logicflow-web-adapter/src/main/java/com/ouroboros/logicflow/adapter/RequestInfoMap.java)

但它承担的是一条很重要的边界收口。

### 4.1 模型存在性与 design-time 显式拒绝

名称、存在性与设计错误检查由统一 Facade 承担，adapter 只映射 gate failure。高优先级 enabled 但 invalid 的模型仍然显式失败，不回退低优先级实现。

### 4.2 Web 请求到流程输入的适配

`RequestInfoMap` 的作用不是简单包一层 Map，而是把 `ServerRequest` 适配成流程能消费的层次化输入上下文。

它会懒加载并暴露一组稳定键，例如：

- `method`
- `uri`
- `path`
- `headers`
- `params`
- `body`
- `data`

这里的设计取向是：

- 让流程节点消费一个“面向业务脚本与表达式的请求上下文”
- 而不是直接把 Servlet / Spring Web 类型暴露给流程图

因此 adapter 当前不仅是“调用器”，也是一层输入语义降解器。

### 4.3 外层事务兜底

当前 adapter 会在整个 `flowModel.execute(...)` 外层包一层统一事务：

- 成功：`commit`
- 失败：`rollback`

代码中也明确写着：

- “先偷个懒，简单粗暴的加个事务管理，将来再考虑优化”

这说明当前事务模型的定位不是“设计已定型”，而是“在平台里先给一层整请求兜底”。

### 4.4 Web 响应映射与异常解包

执行结果当前仍统一收敛为：

- `Try<Object>` -> `Either`
- 再交给 `WebApiResult.of(...)`

同时 adapter 会做一层异常解包：

- 若失败是 `FlowRuntimeException` 且有 cause，则优先取 cause
- 若 error 或其 cause 已经是 `OuroborosException`，则直接保留

这说明 adapter 当前更关心的是：

- 上层最终应该看到哪种平台异常类型

而不是：

- 这次成功到底来自 `RETURN` 还是 `EXIT`
- 这次失败是主流程失败还是错误流程最终失败

## 5. 当前事务语义：足够可用，但有意保持收敛

### 5.1 已落地语义

今天可以明确确认的事务语义只有一套：

- 一次 Web 调用对应一层外部事务
- `flowModel.execute(...)` 成功则提交
- `flowModel.execute(...)` 失败则回滚

这套语义与 `core` 已落地能力结合后的真实效果包括：

- 主流程直接成功，提交
- 主流程失败但错误流程 `ReturnNode` 恢复成功，仍提交
- 主流程失败且错误流程也失败，回滚

也就是说，adapter 当前只看“最终结果是 success 还是 failure”，并不区分“如何成功”。

### 5.2 这套语义为什么现在还成立

它之所以还成立，是因为当前公共合同仍刻意维持在：

- `FlowModel.execute(): Try<Object>`

只要外层世界仍把流程执行看成“成功拿到一个值”或“失败抛出一个错误”，这层统一事务仍然自洽。

### 5.3 它已经开始变窄的地方

随着 `core` 内部语义变丰富，这套事务模型的局限已经非常明确：

- 看不到成功终点种类
- 看不到“恢复成功”和“正常成功”的差别
- 无法为不同终点语义配置不同提交策略
- 无法承载将来的节点级/局部事务设计

所以当前正确的说法不是“事务问题已经解决”，而是：

- **引擎内核已经先把结果语义做宽**
- **adapter 仍暂时用旧的单结果事务模型收口**

## 6. 当前结果契约：`FlowOutcome` 已存在，但外层故意看不见

`core` 内部当前已经使用：

- [`FlowOutcome`](../../ouroboros-logicflow-core/src/main/java/com/ouroboros/logicflow/results/FlowOutcome.java)

来统一收口成功终点。

但这层结果信封目前仍然没有穿透到 adapter/runtime 公共合同。对外保留的是：

- `FlowModel.execute(): Try<Object>`
- `FlowRuntimeAnalyzer.onFlowCompleted(..., Object)`

兼容性证据：

- [`FlowModelRuntimeAnalyzerCompatibilityTest`](../../ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/FlowModelRuntimeAnalyzerCompatibilityTest.java)

这意味着当前架构采取的是一种很克制的分层策略：

1. 先在 `core` 内部建立更准确的结果语义
2. 再由公共签名继续暴露旧的“解包后结果”
3. 等 adapter/runtime 真正准备好，再决定是否扩大外部合同

这能降低学习成本，但也要求文档明确写出：

- 现在上层确实还看不到 outcome kind

## 7. 错误边界：当前 adapter 如何理解“失败”

在当前实现里，adapter 对失败的理解仍然是“最终执行失败”：

- 如果 `execute()` 返回 failure，就回滚并映射为错误响应
- 如果 error flow 已把主流程失败恢复成 success，那么 adapter 只会看到 success

这与 [`DESIGN-ERROR-HANDLING.md`](../error-handling/overview.md) 的当前语义是对齐的：

- 错误流是流程级恢复机制
- 只有真正 surface 到外层的最终 failure 才会被 adapter 当成失败处理

因此当前不能把 adapter 描述成“知道错误流细节”。更准确的说法是：

- adapter 只消费 `core` 的最终 verdict
- 错误流细节仍主要留在 `core` 与 analyzer 侧

## 8. 当前非目标与 Deferred：哪些事情还不能说“已经支持”

以下能力在当前 baseline 下都应视为 Deferred：

1. **流程级事务边界下放**
   - 当前没有 `BeginTransaction` / `CommitTransaction` / `RollbackTransaction` 节点，也没有 scope 级事务控制。
2. **按成功终点种类区分事务/响应策略**
   - `RETURN` 与显式 `EXIT` 对 adapter 来说当前没有外部差异。
3. **局部异常端口与局部回滚语义**
   - 当前错误恢复主要依赖全局 `errorEntryNode`。
4. **把 `FlowOutcome` 直接暴露给 adapter/runtime 公共合同**
   - 当前仍保持旧签名兼容。
5. **目标环境中的完整事务验证**
   - 已有 runtime 和 Web adapter 单元测试；真实数据库、异步节点与恢复成功时的事务行为仍需目标环境验证。

这些点里，最容易误写过满的是第 1 和第 2 条。当前正确的表述只能是：

- 事务仍在 adapter 外层统一兜底
- 更细粒度事务语义是明确延后项

## 9. 当前验证口径：现有保护在哪里，缺口又在哪里

当前模块测试包括 runtime 的 `DefaultLogicFlowExecutionFacadeTest`、`DefaultLogicFlowProviderSnapshotTest`、App/Jar metadata provider 与 SPI 测试，以及 web-adapter 的 `HandlerFunctionAdapterTest`。相邻 core/builtin 测试保护最终结果和 analyzer 兼容性。测试存在不代表本轮已执行，也不替代真实数据库事务与跨栈验收。



这意味着如果后续要改：

- `RequestInfoMap` 输入契约
- adapter 事务边界
- `WebApiResult` 映射方式
- runtime provider 的刷新/缓存行为

就不应只靠跨栈回归或人工联调，最好同时补上模块级测试切片。

## 10. 演进建议：如何继续推进而不破坏当前分层

如果未来要推进这条边界，建议顺序是：

1. **先定义 outcome 对 adapter 的最小新增合同**
   - 例如只是增加“成功类别”可观测性，而不是立刻改公开返回类型。
2. **再决定事务策略是否需要感知成功类别**
   - 不要在事务模型未定义前引入事务节点。
3. **最后再决定 editor/dev 是否需要暴露新的运行时语义**
   - 避免反向从工具链倒逼 core/adapter 临时补洞。

这里最重要的设计原则仍然是：

- `core` 负责把语义做准
- `runtime` 负责把模型接进来
- `web-adapter` 负责把平台现有合同稳稳接住

在这三者的边界真正改清楚之前，任何“为了通过某个场景临时在 adapter 层多判断一点”的做法，都很容易变成未来的设计债。
