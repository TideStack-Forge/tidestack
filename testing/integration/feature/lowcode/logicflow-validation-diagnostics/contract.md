# Logicflow Validation Diagnostics Contract

## Objective

验证逻辑流编辑器在“允许保存但保留后端错误”的策略下，前后端能够共同维持一条完整、可诊断的闭环：

- develop 侧保存返回结构化后端校验错误
- 模型列表和编辑页能够显示这些诊断
- 编辑页里的“定位到节点”动作能回到对应节点，而不是只给出一串无法消费的文本错误

## Scope

- 在范围内：
  - develop runtime 与 develop-shell 可访问
  - logicflow editor 静态微前端可被壳应用加载
  - 场景数据 fixture 能稳定生成一个可返回 validationSnapshot 诊断快照的逻辑流模型
  - 编辑页存在可展开的 diagnostics 入口，并能展示后端结构化诊断
  - 对带 `nodeNames` 的诊断显示“定位到节点”动作
  - 点击定位后，属性面板聚焦到预期节点
- 不在范围内：
  - 逻辑流模型运行时执行结果
  - 真实拖拽、连线、缩放等浏览器手势回归
  - 非 logicflow 模型编辑页的通用错误展示
  - 全自动 Playwright 执行器本身

## 当前合同与执行差距

诊断属于读取/保存响应的 `validationSnapshot`，通过 `includeValidationSnapshot=true` 请求。正式 `flowModel` 和持久化 JSON 不保存 `validationState`、`validationSummary`、`validationErrors`、`validationDiagnostics`、`lastValidatedTime`；不能要求从已保存文件读出这些字段。

数据夹具 `prepare.sh` 请求并检查诊断信封。本地 HTTP 合同测试覆盖有效信封、旧响应拒绝和目标节点缺失。真实运行时的准备、正式文件和浏览器诊断/定位仍须按下列通过标准执行；实际结果与边界归对应任务或 CI，不以本地合同测试代替跨栈验收。

## Fixtures

- Runtimes:
  - `runtimes/develop`
- Frontends:
  - `frontends/develop-shell`
  - `frontends/logicflow-editor-static`
- Data:
  - `data/logicflow-validation-diagnostics`
- Identities:
  - `identities/admin`

## Pass Criteria

- 统一 fixture harness 能为本场景生成预检结果和生命周期计划
- 数据 fixture 每次都能准备出一个稳定可返回 validationSnapshot 诊断快照的逻辑流模型
- develop 壳能在注入 `--logicflow_editor` 后加载逻辑流编辑器微前端
- 编辑页存在可展开的 diagnostics 入口，并能显示结构化诊断
- 针对目标节点的诊断条目能触发“定位到节点”，且属性面板中的节点名与预期一致

## Failure Classification

- 环境 / 拓扑问题：
  - develop runtime 不可达
  - develop-shell 未注入 logicflow editor 静态入口
  - logicflow editor 静态服务未启动或包产物过期
- 数据 fixture 问题：
  - smoke app 或 logicflow model 创建失败
  - 请求诊断信封后未产生 validationSnapshot
  - 诊断中缺少目标节点信息
- 前端壳 / 微前端问题：
  - 编辑页无法打开
  - 微前端资源加载失败
  - diagnostics 入口或诊断列表未出现
- 编辑器交互问题：
  - 目标诊断项未渲染或未提供定位动作
  - 点击后未聚焦预期节点
  - 属性面板未展示对应节点

## Evidence Requirements

- `tmp/testing-evidence/lowcode-logicflow-validation-diagnostics/**`
- fixture harness 生成的 `fixture-preflight.json` 与 `fixture-harness-*.json`
- develop 后端日志 / 浏览器网络与 console 证据
- 编辑页、diagnostics 入口/列表、定位后属性面板的截图或文字记录
