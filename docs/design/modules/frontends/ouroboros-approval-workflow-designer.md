# ouroboros-approval-workflow-designer 模块设计摘要

## 模块定位

`frontends/web/ouroboros-approval-workflow-designer` 是审批工作流设计器微前端，提供审批流定义的可视化编辑体验，并以 Amis 组件库形式接入平台。它对应"审批流编辑器"这一独立前端资产。

## 责任边界

负责审批流设计器 UI、节点编辑、流程定义转换与条件字段支撑；不负责审批运行时执行、主应用壳层、SDK 公共契约，也不负责后端审批模型本身。编辑器对外读写的定义形状必须对齐审批流模块内核的正式元数据 contract，不自行发明第二套 persisted schema。

## 依赖关系

主实现依赖 React 18、React Flow 与 `ouroboros-sdk`，通过 `initAmisComponentLib()` 接入平台；G6 兼容代码只用于历史测试与适配层，**默认设计器与只读流程图都以 React Flow 为唯一渲染路径**。与 `core/ouroboros-approval-workflow`、后端审批接口和主应用的集成边界紧密。

## 深入文档索引

- `推荐阅读`：[frontends/web/ouroboros-approval-workflow-designer/DESIGN.md](../../../../frontends/web/ouroboros-approval-workflow-designer/DESIGN.md)（模块权威设计，含 Toolbar Boundary、State And Capabilities 与 **Interaction Contracts (as built)**）
- `推荐阅读`：[frontends/web/ouroboros-approval-workflow-designer/README.md](../../../../frontends/web/ouroboros-approval-workflow-designer/README.md)
- `补充深度资料`：[审批流定义元数据与图契约](../../../../core/ouroboros-approval-workflow/design/definition-metadata-and-graph-contracts/overview.md)、[集成边界](../../cross-cutting/integration-boundaries.md)
- `补充深度资料`：[React Flow rollout](../../../../frontends/web/ouroboros-approval-workflow-designer/docs/react-flow-rollout.md)、[workflow integration baseline](../../../../frontends/web/ouroboros-approval-workflow-designer/tests/integration/workflow/README.md)

## 演进状态

持续演进，骨架已清晰（设计器 UI + 转换层 + 节点体系）。当前治理重点是把 V1/G6 兼容严格限制在格式适配与少量兼容测试里，避免新入口、只读视图、harness 或运行时配置再次默认回退到旧设计器。当前机制以模块 `DESIGN.md` 和后端定义元数据与图契约为共同正文；需求与验收见 [审批工作流需求](../../../requirements/workflow/README.md)。历史 rollout、任务验收与 `.codestable` 记录只证明各自时点，不能替代当前设计和实现。
