# ouroboros-notification 模块设计摘要

## 模块定位

`ouroboros-notification` 是能力层中的统一通知系统，负责把多种通知渠道抽象成一致的发送接口，并与模板、逻辑流和组织结构能力协同工作。它的核心价值在于“统一通知模型”，而不是某个单一渠道实现。

## 责任边界

该模块负责通知模板、渠道适配、消息分发和逻辑流集成，不负责具体业务事件定义，也不负责把用户组织关系或运行时配置重新定义一遍。渠道实现可以很多，但统一通知抽象必须保持集中。

## 依赖关系

模块拆分为 `core`、`channel`、`dev`、`runtime`、`logicflow-node` 五层：`core` 定义统一通知抽象，`channel` 承接各个渠道实现，`runtime` 与 `logicflow-node` 负责运行时和流程集成，`dev` 负责模板管理，天然把“渠道抽象”和“渠道实现”分开。

## 个人消息入口

公共运行时通过 `notification` 应用模块提供“消息通知”主菜单，打开已有 `/notification` 收件箱页面。为用户角色授予 `notification` 权限后，菜单和页面进入正常授权链路；收件箱接口仍按当前员工查询及标记消息。该入口与开发平台的通知模板编辑分属不同用途。

## 深入文档索引

- `推荐阅读`：[ability/ouroboros-notification/DESIGN.md](../../../../ability/ouroboros-notification/DESIGN.md)
- `运行时配置管理（已实施）`：[后端设计](../../../../ability/ouroboros-notification/docs/runtime-management-design.md)、[列表/查看/编辑页面设计](../../../../ability/ouroboros-notification/docs/runtime-management-ui.md)。运行验收归关联任务与目标部署环境。
- `暂无独立深度设计文档`：当前仓库中尚无按单渠道拆分的维护者专题页。
- `补充深度资料`：[跨模块扩展机制](../../cross-cutting/extension-mechanisms.md)

## 演进状态

`ouroboros-notification` 的总体结构已经比较清晰，真正容易失控的是渠道增多之后的治理成本。维护者需要持续把“渠道实现的变化”限制在 `channel` 层，不让它反向侵入统一通知抽象和模板模型。
