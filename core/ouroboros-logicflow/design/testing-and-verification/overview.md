# LogicFlow 测试与验证体系（Core/Dev/Editor/跨栈）深化说明

更新日期：`2026-03-18`

> 回到总览：[`DESIGN.md`](../../DESIGN.md)

这份文档是对 [`DESIGN.md`](../../DESIGN.md) 中“开发验证”“工具链契约”“跨栈集成测试治理”几部分的深化说明。它的目标不是罗列测试命令，而是回答三个更上位的问题：

- 为什么 LogicFlow 的测试必须分层，而不能只靠一个大而全的 E2E。
- 每一层测试分别在守什么设计原则，彼此的边界在哪里。
- 哪些测试资产和验证方式如果处理不当，会直接把分支带回“本地看起来没问题、合并后却断裂”的状态。

## 1. 为什么测试体系本身也是设计的一部分

LogicFlow 不是单一模块，而是一条跨层链路：

- `core` 定义语义权威
- `dev` 把权威校验与节点定义暴露给平台
- `editor` 把建模体验落到前端
- `testing/**` 里的夹具、scenario、runner 则把“跨前后端承诺”固定下来

因此测试体系的职责不是“补充信心”，而是把这条链路的边界固定成可重复验证的合同。

如果没有分层测试，常见退化会是：

- core 语义已经漂移，但 editor 单测仍然通过
- editor 体验看起来正常，但 dev 返回的诊断结构已经不能消费
- 场景手工点通了，但真正依赖的 fixture、harness 资产没有纳入 git，合并后回归保护消失

LogicFlow 的验证设计因此遵循两个原则：

1. **语义在最低层证明**
   - 图是否合法、终点是否互斥、端口契约是否成立，应尽量在 `core` 层用小而快的回归测试固定。
2. **跨层承诺在最接近用户的位置证明**
   - “保存但保留后端错误”“查看校验错误并定位到节点”这类承诺，只有在 dev/editor/壳应用联动时才算真正成立。

## 2. 规范分层：每一层到底守什么

当前 LogicFlow 的验证层次可以稳定划分为四层：

### 2.1 Core 语义层

位置：

- `core/ouroboros-logicflow/ouroboros-logicflow-core/src/test/java/com/ouroboros/logicflow/model/`
- `core/ouroboros-logicflow/ouroboros-logicflow-builtin-nodes/src/test/java/com/ouroboros/logicflow/model/`（内置节点执行回归）

职责：

- 守住流程构造、静态分析、执行语义、fail-closed 运行时兜底
- 防止“为了兼容工具链或测试”而破坏核心设计原则

它回答的问题是：

- 这个图是否合法
- 这条路径是否应该执行/跳过
- 成功终点如何收口
- selective-output 或 error flow 契约是否被破坏

### 2.2 Dev 作品化契约层

位置：

- `core/ouroboros-logicflow/ouroboros-logicflow-dev/src/test/java/com/ouroboros/logicflow/`

职责：

- 守住保存时的 validation snapshot 生成、NodeDefine 输出、schema/UI 契约
- 确保 dev 对外暴露的是 core 真实语义，而不是另一套平行规则

它回答的问题是：

- 保存时是否生成了结构化后端诊断
- 列表页/编辑页消费所需的字段是否稳定
- 节点定义与编辑器约定是否仍自洽

### 2.3 Editor 前端层

位置：

- `frontends/web/ouroboros-logicflow-editor/tests/`

职责：

- 守住前端自身的变换逻辑、警告策略、桥接行为、属性面板回写与序列化契约
- 在真实挂载下验证编辑器确实能消费 dev 侧输出的数据结构

它回答的问题是：

- 端口规范化、validation navigation、保存阻塞策略是否符合当前约定
- 真实 amis 渲染环境下的属性编辑是否会正确回写 `flowData`
- editor 暴露给测试 harness 的窄桥是否足以覆盖关键用户操作

### 2.4 Cross-stack 场景层

位置：

- `testing/integration/feature/lowcode/logicflow-validation-diagnostics/`
- `testing/common/fixtures/**`

职责：

- 守住“用户真正感知到的跨前后端承诺”
- 把运行时、前端壳、静态微前端、数据 fixture、身份 fixture 组织成可重放场景

它回答的问题是：

- 一个后端 `INVALID` 的模型是否真的能在平台上被看见、被解释、被定位
- 当前测试链路是坏在模型逻辑、壳应用注入、fixture 数据、还是运行时环境

## 3. Core 测试：先证明语义，再谈体验

### 3.1 测试族谱应按“设计规则”拆，而不是按实现文件拆

当前 core 测试已经逐步从“大而泛的 smoke”整理为按行为分组的回归族谱，例如：

- 静态分析与结构校验：
  - `FlowModelCycleValidationTest`
  - `FlowModelLeafValidationTest`
  - `FlowModelGraphReachabilityValidationTest`
  - `FlowModelInputConflictValidationTest`
  - `FlowModelTypeCompatibilityValidationTest`
- 成功终点与结果契约：
  - `FlowModelSuccessfulTerminalPresenceValidationTest`
  - `FlowModelSuccessfulTerminalExclusivityValidationTest`
  - `FlowModelReturnExecutionTest`
  - `FlowModelExecutionResultSelectionTest`
- selective-output 与运行时兜底：
  - `FlowModelOutputPortMetadataValidationTest`
  - `FlowModelSelectiveOutputRuntimeContractTest`
  - `FlowModelSuccessfulTerminalRuntimeGuardTest`
- error flow 与异常恢复：
  - `FlowModelErrorScopeValidationTest`
  - `FlowModelErrorFlowExecutionTest`
  - `FlowModelExecutionRuntimeFailureTest`

这个拆法的好处是：未来如果规则演进，新增测试可以直接挂到“哪条设计规则被保护”下面，而不是围着某个类名继续堆。

### 3.2 Core 层既要测 design-time，也要测 runtime fail-closed

LogicFlow 的设计不是“静态分析通过就万事大吉”。很多关键底线都采取“双保险”：

- design-time 尽量拒绝坏图
- runtime 对静态分析漏网之鱼继续 fail-closed

因此 core 层至少要同时有两类测试：

1. **静态分析回归**
   - 证明坏图会在 `DefaultFlowModel` 构造阶段被标 invalid。
2. **运行时保护回归**
   - 证明即使静态分析漏检或兼容层喂入了异常输入，执行期也不会悄悄成功。

像 `FlowModelImplicitEntryRuntimeGuardTest`、`FlowModelSelectiveOutputRuntimeContractTest` 这类测试，价值就在于守住第二类边界。

### 3.3 Core fixture 的职责是“表达语义样本”，不是承载跨栈环境

当前 core 模块内的测试资源位于：

- `ouroboros-logicflow-core/src/test/resources/fixtures/**`
- `ouroboros-logicflow-core/src/test/resources/flows/**`

这里的 fixture 适合承载：

- 线性流程、错误流、selective-output 等最小语义样本
- 某个静态分析规则的最小坏图/好图

这里不应承载：

- develop 平台 API 准备数据
- 前端壳注入参数
- 浏览器证据与场景脚本

这些职责一旦混进 core fixture，测试会失去“定位到底是语义坏了还是环境坏了”的能力。

## 4. Dev 测试：权威校验如何稳定变成作品化合同

`logicflow-dev` 的测试不是在重复 core，而是在防止“权威语义进入平台前被重新解释”。

当前值得长期保留的测试重点包括：

- validation snapshot 生成与字段映射
  - `LogicFlowValidationSnapshotSupportTest`
  - `LogicModelDevelopServiceImplValidationTest`
- NodeDefine / UI schema 契约
  - `LogicflowNodeDefineControllerReturnNodeTest`
  - `LogicFlowModelValidationDiagnosticSchemaTest`
  - `LogicFlowModelAddSchemaValidationFeedbackTest`

这一层要固定三个事实：

1. `validationState` 是诊断快照，不是自动 enable/disable 开关。
2. 保存阻塞规则与诊断展示规则要解耦。
3. editor 所消费的字段与行为提示，必须能从 dev 返回结果中稳定获得。

换句话说，dev 测试保护的是“core 真实语义如何被平台化”，而不是“另造一套语义”。

## 5. Editor 测试：前端不创造语义，但必须证明自己能消费它

### 5.1 包内测试层次：历史 node test + unit + integration

当前 `frontends/web/ouroboros-logicflow-editor/tests/` 已形成三种互补层次：

- `tests/*.test.mjs`
  - 历史 node 测试，覆盖大量前端逻辑与桥接行为
- `tests/unit/**`
  - 按 `frontends/web/TESTING.md` 对齐后的包级守卫与源代码测试
- `tests/integration/**`
  - 真实挂载编辑器、属性面板、序列化与平台注入的前端集成测试

这三层不是重复关系，而是速度与真实度的梯度。

### 5.2 为什么当前 harness 保持 package-local

当前 editor 集成测试依赖：

- 真实 amis 渲染能力
- `X6` 在 `jsdom` 下所需的 SVG polyfill
- 面向编辑器的窄测试桥

因此相关 helper 目前保留在：

- `frontends/web/ouroboros-logicflow-editor/tests/integration/logicflow/harness/`
- `frontends/web/ouroboros-logicflow-editor/tests/integration/logicflow/harness/platform/`

这是一个有意识的选择：

- **共享层只抽真正共享的东西**
- **带明显产品语义与依赖的注入层，先留在包内**

原因不是“将来不能抽”，而是当前这套注入能力仍然高度依赖 logicflow editor 的宿主方式和 amis 渲染约束。过早抽到 `frontends/web/tests/` 级别，往往会把产品特定假设伪装成公共平台能力。

未来只有在第二个以上前端包确实需要同一种注入能力时，才值得把它继续上提。

### 5.3 Editor integration 的底线：尽量测真实消费链，不要写自欺欺人的假壳

当前 editor integration 的设计有一个明确底线：

- 需要验证 amis schema 回写时，应走真实 amis 渲染链
- 需要验证节点/连线序列化时，应通过 harness 驱动编辑器窄桥，而不是直接伪造内部状态

这能减少一种常见自欺：测试通过的其实是 mock 过的内部 helper，而不是用户真正走到的编辑器消费链。

## 6. Cross-stack：场景目录、夹具目录、证据目录各司其职

### 6.1 场景目录是“合同”，不是脚本堆放处

当前跨前后端逻辑流场景采用 feature 级目录：

- `testing/integration/feature/lowcode/logicflow-validation-diagnostics/`

其中各文件职责清晰分离：

- `contract.md`
  - 写清场景目标、范围、通过标准、失败分类、证据要求
- `cases.md`
  - 用例表，列出 case id、前置条件、通过标准与证据
- `scenario.yaml`
  - 机器可消费的 fixture 组合、入口环境变量、报告位置
- `harness.playbook.md`
  - 面向执行者的稳定步骤与观察点

这套结构的意义是：

- 场景约束可以先稳定下来
- 后续无论接 Playwright、手工执行还是其他 runner，都接同一份合同

### 6.2 Fixture 目录是“能力供应”，不是场景私货

当前逻辑流跨栈验证依赖的共享 fixture 主要位于：

- `testing/common/fixtures/frontends/logicflow-editor-static/`
- `testing/common/fixtures/data/logicflow-validation-diagnostics/`

fixture 应只负责自己那一层的供应：

- 静态前端 fixture 负责把 editor 产物以最小静态服务暴露出来
- 数据 fixture 应准备无效模型，按[场景合同](../../../../testing/integration/feature/lowcode/logicflow-validation-diagnostics/contract.md#当前合同与执行差距)请求派生诊断信封；后续浏览器场景独立验证列表、诊断定位及正式文件。

它们不应该顺手再承担：

- 浏览器点击步骤
- 场景级断言
- 其他模块私有环境逻辑

### 6.3 证据目录必须从场景设计时就定好

当前场景已把证据根目录固定为：

- `tmp/testing-evidence/lowcode-logicflow-validation-diagnostics`

这不是附属细节，而是为了保证失败时能回答：

- 是 fixture 准备失败
- 还是微前端未注入
- 还是编辑页展示/定位失败

如果没有证据目录与证据要求，跨栈失败最终通常只会退化成一句“页面打不开”。

## 7. 稳定验证入口：命令只是表层，入口策略更重要

### 7.1 模块级稳定入口

当前建议固定使用以下入口：

- core
  - 从仓库根执行 `mvn test -pl :ouroboros-logicflow-core,:ouroboros-logicflow-builtin-nodes -am`，覆盖语义内核及内置节点执行回归
- dev
  - `mvn test -pl :ouroboros-logicflow-dev -am`
- editor unit
  - `cd frontends/web/ouroboros-logicflow-editor && npm run test:unit`
- editor integration
  - `cd frontends/web/ouroboros-logicflow-editor && npm run test:integration`
- editor aggregate
  - `cd frontends/web/ouroboros-logicflow-editor && npm test`

其中 dev 必须优先用 reactor 方式，而不是直接 `--file` 指向 `ouroboros-logicflow-dev/pom.xml`。原因很简单：

- 这个模块依赖 sibling artifacts
- 直接 `--file` 更容易命中本地 Maven 仓库旧 Jar
- 结果会把“构建入口选错了”误判成代码回归

### 7.2 场景级稳定入口

跨栈场景不建议一上来就点页面，稳定顺序应是：

1. 先读 `scenario.yaml`、`contract.md`、`cases.md`
2. 先跑 fixture harness 的 `plan`
3. 再跑 fixture harness 的 `run`
4. 再确认 develop-shell 是否注入了 `--logicflow_editor`
5. 最后才进页面执行交互

对当前逻辑流场景，稳定入口是：

- 计划模式：
  - `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action up --mode plan`
- 执行模式：
  - `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action up --mode run`

## 8. 常见失败模式：不要把环境问题误判成语义回归

### 8.1 启动完成不等于运行就绪

对借助 develop runtime 的联调场景，后端打印 `Started ...Application` 只说明 Web 容器基本起来了，不等于业务链路已经可用。

至少还应继续确认：

- 启动保护期已经结束，不再持续返回 `503`
- 未认证探针已经进入预期鉴权面
- 初始化/迁移/元数据装载已完成

否则页面与 API 的失败结论很容易被“启动后半段未完成”污染。

### 8.2 `401/403/503` 不是一个桶

对跨栈场景，HTTP 状态码要分开诊断：

- `503`
  - 先看是否仍处于启动保护期或依赖初始化阶段
- `401/403`
  - 优先排查认证、RBAC、组织过滤、登录态与壳应用鉴权链
- `500 text/html`
  - 优先怀疑 runtime/filter/SPI/装配问题，而不是直接判定业务模型回归

### 8.3 借用 runtime 可以帮助排查链路，但不能直接当 merge gate

如果本轮回归借用的不是当前分支 freshly-built 产物，而是本地已有 runtime：

- 它可以帮助验证 harness、fixture、壳应用路径是否成立
- 但它不能直接证明当前分支模块健康

只有当故障与本分支改动存在明确因果关系时，才应将其上升为 merge blocker。

## 9. 反模式与合并前阻塞项

以下情况对 LogicFlow 分支应视为高风险，很多时候就是合并前阻塞项：

1. **关键测试、fixture、scenario 资产仍是 untracked**
   - 本地跑通不代表回归保护已经进入仓库。
2. **为让测试通过而修改生产语义**
   - 例如为了绕开 selective-output、终点契约或 dev 诊断逻辑而加入测试特判。
3. **用过度 mock 的前端测试替代真实消费链**
   - 当问题与 amis 渲染、壳应用注入、微前端加载有关时，mock 测试不能充当结论。
4. **把产品特定 harness 误抽成共享平台能力**
   - 这会导致共享层承载错误假设，后续更难演进。
5. **fixture 含个人路径、个人仓库地址或其他环境私货**
   - 这既不利于复现，也会带来不必要的泄露风险。

## 10. 后续演进规则：如何继续补强而不把体系做乱

未来继续增强 LogicFlow 测试体系时，建议遵循以下顺序：

1. 先判断问题属于哪一层语义边界。
2. 优先在最低可证明层补回归测试。
3. 只有当问题跨层暴露给用户时，再补 editor integration 或 cross-stack scenario。
4. 抽共享 harness 之前，先证明至少有两个消费方需要同一种能力。
5. 任何新场景都要同时交付：
   - 场景合同
   - fixture 组合
   - 证据要求
   - 可纳入 git 的资产

这样做的目的不是“让测试越来越多”，而是让 LogicFlow 的设计原则在每一层都各有守门人，避免再次回到“概念上理解一致，但代码和测试落点各说各话”的状态。
