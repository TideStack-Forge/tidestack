# 模块设计

本目录存放 Ouroboros 各模块的**设计入口页**（模块定位、责任边界、依赖关系、深入文档索引）；模块的权威设计正文在模块目录下——优先 `DESIGN.md`，没有 `DESIGN.md` 的模块则在其 `docs/` 下。按分层组织。

## 模块组织原则

### 1. 按层分组

模块按所属层级组织：
- `core/` - 核心引擎层模块
- `libs/` - 基础设施层模块
- `ability/` - 业务能力层模块
- `security/` - 安全层模块
- `dev-module/` - 开发工具模块
- `app-module/` - 业务应用模块
- `platform/` - 平台运行时模块
- `frontends/` - 前端模块

### 2. 模块命名规范

模块命名遵循以下模式：
- `{domain}-core`: 核心抽象和接口
- `{domain}-runtime`: 运行时实现
- `{domain}-spring-starter`: Spring Boot 自动配置
- `{domain}-dev`: 开发时支持
- `{domain}-test-support`: 测试支持

### 3. 模块职责边界

每个模块应：
- 有单一、清晰的职责
- 通过接口暴露能力
- 最小化对外部模块的依赖
- 提供完整的文档和测试

## 模块设计文档索引

### Core Layer (核心引擎层)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-data | 动态数据模型引擎 | [ouroboros-data.md](core/ouroboros-data.md) |
| ouroboros-ui-model | UI 组件和 Schema 系统 | [ouroboros-ui-model.md](core/ouroboros-ui-model.md) |
| ouroboros-webapi-model | REST API 动态生成 | [ouroboros-webapi-model.md](core/ouroboros-webapi-model.md) |
| ouroboros-logicflow | 逻辑流引擎 | [ouroboros-logicflow.md](core/ouroboros-logicflow.md) |
| ouroboros-approval-workflow | 审批工作流 | [ouroboros-approval-workflow.md](core/ouroboros-approval-workflow.md) |
| ouroboros-field-type | 字段类型系统 | [ouroboros-field-type.md](core/ouroboros-field-type.md) |
| ouroboros-menu-model | 菜单和导航 | [ouroboros-menu-model.md](core/ouroboros-menu-model.md) |
| ouroboros-app-module-model | 应用模块模型 | [ouroboros-app-module-model.md](core/ouroboros-app-module-model.md) |
| ouroboros-business-model | 业务模型 | [ouroboros-business-model.md](core/ouroboros-business-model.md) |
| ouroboros-error-code | 错误码管理 | [ouroboros-error-code.md](core/ouroboros-error-code.md) |
| ouroboros-statistics-reporting | 综合查询与统计（数据集 / 图表 / 仪表板） | [core/ouroboros-statistics-reporting/docs/module-design.md](../../../core/ouroboros-statistics-reporting/docs/module-design.md) |
| ouroboros-i18n | 国际化（语言环境提取 / 同步 / 错误信息本地化） | [core/ouroboros-i18n.md](core/ouroboros-i18n.md) |
| ouroboros-resource | 应用级文件资源与运行时覆盖 | [core/ouroboros-resource.md](core/ouroboros-resource.md) |

更多模块见 [core/README.md](core/README.md)；跨模块专题 [审批流分支与汇聚图语义](../../../core/ouroboros-approval-workflow/design/gateway-join-semantics.md)（已归位到 `core/ouroboros-approval-workflow/design/`）

### Libs Layer (基础设施层)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-util | 纯工具类 | [libs/ouroboros-util.md](libs/ouroboros-util.md) |
| common-exception | 异常体系 | [libs/common-exception.md](libs/common-exception.md) |
| common-web | Web 响应封装 | [libs/common-web.md](libs/common-web.md) |
| common-cache | 缓存抽象 | [libs/common-cache.md](libs/common-cache.md) |
| common-template | 模板引擎 | [libs/common-template.md](libs/common-template.md) |
| common-script | 脚本执行 | [libs/common-script.md](libs/common-script.md) |
| common-all | 公共组件聚合入口 | [libs/common-all.md](libs/common-all.md) |
| common-app | 应用信息契约 | [libs/common-app.md](libs/common-app.md) |
| common-dev | 开发态共享契约 | [libs/common-dev.md](libs/common-dev.md) |
| common-expression | 表达式引擎契约 | [libs/common-expression.md](libs/common-expression.md) |
| common-exception-mapper | 异常到 Web 响应的映射 | [libs/common-exception-mapper.md](libs/common-exception-mapper.md) |
| common-lock | 分布式锁 | [libs/common-lock.md](libs/common-lock.md) |
| common-model | 通用模型契约 | [libs/common-model.md](libs/common-model.md) |
| common-modules | 组件族边界与清单 | [cross-cutting/common-modules.md](../cross-cutting/common-modules.md) |
| common-service | 通用服务契约 | [libs/common-service.md](libs/common-service.md) |
| ouroboros-util-excel | Excel 工具 | [libs/ouroboros-util-excel.md](libs/ouroboros-util-excel.md) |
| ouroboros-util-zip | 压缩包工具 | [libs/ouroboros-util-zip.md](libs/ouroboros-util-zip.md) |
| common-error-code | 错误码引用与异常描述契约 | [libs/common-error-code.md](libs/common-error-code.md) |
| common-metadata | 元数据加载底座（来源 / 规格 / 加载编排） | [libs/common-metadata.md](libs/common-metadata.md) |

更多模块见 [libs/README.md](libs/README.md)

### Ability Layer (业务能力层)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-configuration | 配置管理 | [ability/ouroboros-configuration.md](ability/ouroboros-configuration.md) |
| ouroboros-notification | 通知系统 | [ability/ouroboros-notification.md](ability/ouroboros-notification.md) |
| ouroboros-websocket | WebSocket 支持 | [ability/ouroboros-websocket.md](ability/ouroboros-websocket.md) |
| ouroboros-scheduler | 定时任务 | [ability/ouroboros-scheduler.md](ability/ouroboros-scheduler.md) |
| ouroboros-message-queue | 消息队列 | [ability/ouroboros-message-queue.md](ability/ouroboros-message-queue.md) |
| ouroboros-file-upload | 文件上传 | [ability/ouroboros-file-upload.md](ability/ouroboros-file-upload.md) |
| ouroboros-organization-structure | 组织架构 | [ability/ouroboros-organization-structure.md](ability/ouroboros-organization-structure.md) |
| ouroboros-approval-workflow-external-integration | 审批流外部流程引擎对接（当前能力与缺口见正文） | [ability/ouroboros-approval-workflow-external-integration/docs/module-design.md](../../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md) |
| ouroboros-cache-management | 公共平台缓存管理入口 | [ability/ouroboros-cache-management.md](ability/ouroboros-cache-management.md) |
| ouroboros-app-runtime | 应用运行时能力合成 | [ability/ouroboros-app-runtime.md](ability/ouroboros-app-runtime.md) |
| ouroboros-business-log | 业务日志与操作模板 | [ability/ouroboros-business-log.md](ability/ouroboros-business-log.md) |
| ouroboros-data-dict | 数据字典 | [ability/ouroboros-data-dict.md](ability/ouroboros-data-dict.md) |
| ouroboros-rpc-model | RPC 运行时治理 | [ability/ouroboros-rpc-model.md](ability/ouroboros-rpc-model.md) |
| ouroboros-organization-structure / personnel-profile-form-scheme | 人员档案表单方案（子专题入口；正文见模块 `DESIGN.md`） | [ability/ouroboros-organization-structure-personnel-profile-form-scheme.md](ability/ouroboros-organization-structure-personnel-profile-form-scheme.md) |
| ouroboros-ai | AI 能力集成（spring-ai 体系） | [ability/ouroboros-ai.md](ability/ouroboros-ai.md) |
| ouroboros-user-setting | 用户设置（分层加载 Provider） | [ability/ouroboros-user-setting.md](ability/ouroboros-user-setting.md) |
| ouroboros-widget | 组件定义与用户仪表盘 | [ability/ouroboros-widget.md](ability/ouroboros-widget.md) |
| ouroboros-platform-component-lib | 平台组件库运行时消费端 | [ability/ouroboros-platform-component-lib.md](ability/ouroboros-platform-component-lib.md) |

更多模块见 [ability/README.md](ability/README.md)。企业协作能力与未实施边界见[集成架构](../cross-cutting/enterprise-collaboration-integration.md#14-当前实现边界)。

### Security Layer (安全层)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-web-security | 安全框架 | [DESIGN.md](../../../security/DESIGN.md) |

更多模块见 [DESIGN.md](../../../security/DESIGN.md)

### Dev Module (开发工具)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-dev-app-management | 应用管理 | [dev-module/ouroboros-dev-app-management.md](dev-module/ouroboros-dev-app-management.md) |
| ouroboros-dev-datasource | 数据源管理 | [dev-module/ouroboros-dev-datasource.md](dev-module/ouroboros-dev-datasource.md) |
| ouroboros-dev-environment | 环境管理 | [dev-module/ouroboros-dev-environment.md](dev-module/ouroboros-dev-environment.md) |
| ouroboros-dev-helper | 开发辅助 | [dev-module/ouroboros-dev-helper.md](dev-module/ouroboros-dev-helper.md) |
| ouroboros-dev-platform-component-lib | 平台组件库 | [dev-module/ouroboros-dev-platform-component-lib.md](dev-module/ouroboros-dev-platform-component-lib.md) |
| ouroboros-dev-version-control-system | 版本控制 | [dev-module/ouroboros-dev-version-control-system.md](dev-module/ouroboros-dev-version-control-system.md) |
| ouroboros-dev-container | 容器运行时管理 | [dev-module/ouroboros-dev-container.md](dev-module/ouroboros-dev-container.md) |
| ouroboros-dev-model-manage-webapi-scaffold | 模型管理与 WebAPI 脚手架 | [dev-module/ouroboros-dev-model-manage-webapi-scaffold.md](dev-module/ouroboros-dev-model-manage-webapi-scaffold.md) |
| ouroboros-dev-runtime | 开发平台运行时编排（Docker/K8s 后端、调试隧道、网关路由同步） | [dev-module/ouroboros-dev-runtime.md](dev-module/ouroboros-dev-runtime.md) |
| ouroboros-dev-project-generation | 项目模板生成与源码 ZIP 打包 | [dev-module/ouroboros-dev-project-generation.md](dev-module/ouroboros-dev-project-generation.md) |

更多模块见 [dev-module/README.md](dev-module/README.md)

### App Module (业务应用)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| dynamic-form | 动态表单 | [app-module/dynamic-form.md](app-module/dynamic-form.md) |
| document-management | 文档管理 | [app-module/document-management.md](app-module/document-management.md) |
| ui-schema-management | UI Schema 管理 | [app-module/ui-schema-management.md](app-module/ui-schema-management.md) |

更多模块见 [app-module/README.md](app-module/README.md)

### Platform (平台运行时)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| ouroboros-develop | 开发平台运行时 | [platform/ouroboros-develop.md](platform/ouroboros-develop.md) |
| ouroboros-mothership | 母舰平台运行时 | [platform/ouroboros-mothership.md](platform/ouroboros-mothership.md) |

更多模块见 [platform/README.md](platform/README.md)

### Frontends (前端)

| 模块 | 职责 | 设计文档 |
|------|------|----------|
| main-app | 主应用（qiankun 主框架） | [frontends/main-app.md](frontends/main-app.md) |
| ouroboros-logicflow-editor | 逻辑流编辑器微前端 | [frontends/ouroboros-logicflow-editor.md](frontends/ouroboros-logicflow-editor.md) |
| ouroboros-approval-workflow-designer | 审批流设计器微前端 | [frontends/ouroboros-approval-workflow-designer.md](frontends/ouroboros-approval-workflow-designer.md) |
| ouroboros-debug-console-topbar | 调试控制台顶部栏微前端 | [frontends/ouroboros-debug-console-topbar.md](frontends/ouroboros-debug-console-topbar.md) |
| ouroboros-sdk | 微前端 SDK（含 menu / userMenu 能力） | [frontends/ouroboros-sdk.md](frontends/ouroboros-sdk.md) |

更多模块见 [frontends/README.md](frontends/README.md)；机制设计：[前端基座菜单扩展机制](../../../frontends/web/main-app/docs/menu-extension-design.md)（已实施，正文在 main-app 模块内）

## 写作辅助

[模块设计模板](../module-template.md)：按实际职责裁剪使用，不计入当前模块清单。

## 相关文档

- [分层设计](../layers) - 各层职责和依赖规则
- [架构设计](../architecture) - 系统架构概览
- [横切关注点](../cross-cutting) - 跨模块设计
