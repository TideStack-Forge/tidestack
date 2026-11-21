# 逻辑编排

为低代码业务逻辑提供节点式定义、编译与执行，并供业务模型和能力模块复用。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 定义与执行 | core 负责流程语义，authoring 负责编排合同；runtime/web-adapter 承接运行时和 Web 接入。 |
| 节点与数据 | builtin/extra nodes、数据模型节点和能力层节点通过正式扩展点接入，保留统一执行上下文。 |
| 运行时管理 | 观察部署默认、持久覆盖和当前实例；条件保存、撤销及恢复操作前态，异步独立刷新父子流程，分实例核对代次。默认来源不完整或执行来源非受管时只读。 |
| 编辑与调试 | logicflow-editor 提供节点编辑；在线调试已实现容器选择、租约采集、可替换共享仓储及默认缓存实现、原画布只读调试与退出恢复、完整路径同时流动、节点输入输出与异常堆栈；本地正式主应用与现有编辑器验收通过，尚未生产部署。 |

## 实现情况

节点式执行和编辑路径已有实现；通用逻辑编排与审批状态机分别维护。脚本智能 mock-server 只服务开发/测试，不是生产执行后端；不把历史评审报告当作当前基线。

本页依据当前源码和配置描述能力。在线调试已执行模块行为测试、真实 Docker/MQ/Redis/HTTP 联调、正式平台浏览器验收及容器重启恢复，具体结果及边界见[在线调试设计](../../core/ouroboros-logicflow/design/online-debugging/overview.md#验收与发布状态)；这些结果不代表全部逻辑编排功能或线上发布已经验收。

编辑器支持[宿主接入合同](../../frontends/web/ouroboros-logicflow-editor/docs/runtime-debug-host.md)，可替换调试接口、通知和目标控件；节点定义可提供属性数据源与脚本智能接口。应用端克隆、草稿保存、测试运行环境和发布流程尚未实现。

运行时管理已实现，模块行为测试、共享 H2 的双 JVM 恢复场景以及生产编辑器资产的隔离浏览器验收覆盖保存与应用链。浏览器宿主替代了平台登录和壳层，尚未在完整目标平台或生产环境验收；目标数据库迁移、实际权限配置、静态代理及多版本保留仍须按[运行手册](../runbooks/logicflow-runtime-manage.md)核对。

运维详情已按版本状态、实例、监控诊断、变更和来源组织。运行监控与详细诊断按单个流程独立开关，默认关闭，分别控制统计与限时节点时序采集。采集设置走统一配置 CAS，后台持久化采用 typed data，保留期、容量及故障行为见运行手册。模型校验、来源和应用失败继续独立展示。

## 启用条件

装配相应 runtime/dev/节点模块和脚本执行环境；浏览器编辑器需接入平台既有微前端链。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [core/ouroboros-logicflow](../../core/ouroboros-logicflow) · [设计/指南](../design/modules/core/ouroboros-logicflow.md) | [测试入口](../../core/ouroboros-logicflow/ouroboros-logicflow-authoring/src/test/java/com/ouroboros/logicflow/authoring/provider/JarLogicFlowNodeDefineProviderTest.java) |
| [ability/ouroboros-logicflow-runtime-manage](../../ability/ouroboros-logicflow-runtime-manage) · [设计](../design/cross-cutting/logicflow-runtime-management.md) | [行为测试](../../ability/ouroboros-logicflow-runtime-manage/src/test/java/com/ouroboros/logicflow/manage/RuntimeFlowManagementTest.java)、[集成场景](../../testing/integration/feature/lowcode/logicflow-runtime-manage/contract.md) |
| [frontends/web/ouroboros-logicflow-editor](../../frontends/web/ouroboros-logicflow-editor) | [测试入口](../../frontends/web/ouroboros-logicflow-editor/tests/canvasController.test.mjs) |
| [frontends/web/script-editor-intelligence-mock-server](../../frontends/web/script-editor-intelligence-mock-server) | [测试入口](../../frontends/web/script-editor-intelligence-mock-server/tests/unit/config/testing-layout-standard.test.js) |

## 关联文档

- [core/ouroboros-logicflow/DESIGN.md](../../core/ouroboros-logicflow/DESIGN.md)
- [core/ouroboros-logicflow/design/testing-and-verification/overview.md](../../core/ouroboros-logicflow/design/testing-and-verification/overview.md)

- [在线调试机制与验收状态](../../core/ouroboros-logicflow/design/online-debugging/overview.md)
- [在线调试运行手册](../runbooks/logicflow-online-debugging.md)
