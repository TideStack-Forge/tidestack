# ability 模块设计

## 分类说明

`ability` 对应能力层，负责把核心引擎和安全边界组织成可复用的运行时与业务能力。本页提供模块摘要和深度设计入口。摘要中的边界说明不能代替模块内的正式合同。

## 当前覆盖状态

各能力模块的当前行为见[功能地图](../../../features/README.md)。以下摘要按主题链接模块内设计；WebSocket 的机制正文位于中央设计页，不假定每个模块都有独立 `DESIGN.md`。

## 已落地文档

- [ouroboros-configuration 模块设计摘要](ouroboros-configuration.md)
- [外部认证统一配置适配组件设计（模块内权威设计）](../../../../ability/ouroboros-external-auth-configuration/DESIGN.md)
- [OAuth2 授权服务器与 OIDC 身份提供方组件设计（模块内权威设计）](../../../../ability/ouroboros-oidc-server/DESIGN.md)
- [ouroboros-app-runtime 模块设计摘要](ouroboros-app-runtime.md)
- [ouroboros-cache-management 模块设计摘要](ouroboros-cache-management.md)
- [ouroboros-notification 模块设计摘要](ouroboros-notification.md)
- [ouroboros-organization-structure 模块设计摘要](ouroboros-organization-structure.md)
- [ouroboros-data-dict 模块设计摘要](ouroboros-data-dict.md)
- [ouroboros-file-upload 模块设计摘要](ouroboros-file-upload.md)
- [ouroboros-scheduler 模块设计摘要](ouroboros-scheduler.md)
- [ouroboros-business-log 模块设计摘要](ouroboros-business-log.md)
- [ouroboros-message-queue 模块设计摘要](ouroboros-message-queue.md)
- [ouroboros-rpc-model 模块设计摘要](ouroboros-rpc-model.md)
- [ouroboros-websocket 模块设计摘要](ouroboros-websocket.md)
- [ouroboros-ai 模块设计摘要](ouroboros-ai.md)
- [ouroboros-widget 模块设计摘要](ouroboros-widget.md)
- [ouroboros-user-setting 模块设计摘要](ouroboros-user-setting.md)
- [ouroboros-platform-component-lib 模块设计摘要](ouroboros-platform-component-lib.md)
- [审批流外部流程引擎对接设计（模块内权威设计；当前能力与缺口见正文）](../../../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)

## 后续补齐范围

仍缺：`ouroboros-widget` 的深度设计（当前仅有模块摘要）、`ouroboros-ai` 的 starter 装配矩阵（README 覆盖，尚无独立设计专题）。
