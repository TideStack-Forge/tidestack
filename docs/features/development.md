# 开发平台

为平台开发者管理应用源码、开发环境、运行后端和扩展交付，沿现有开发模块机制工作。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 应用与版本控制 | 应用管理和 VCS 组织应用工程、分支及源码操作；不替代 monorepo 自身的 MR 规则。 |
| 应用开发看板 | 在同一页面切换能力关系与功能矩阵。能力关系按界面导航、业务服务、数据权限分层，以印刷电路走线和可关闭的流动动画表示方向。业务模型展示属性、关系、视图、动作、事件和访问权限。矩阵读取正式开发菜单的分类、名称、图标、顺序及导航方式。 |
| 模型画布实时协同 | 前端使用 Yjs 处理原子操作与并发合并，复用基座 STOMP 和应用 VCS 持久化，支持重连补偿、当前会话失败重试和只读访问。已接入业务模型关系画布，当前支持边界为单开发工作区与 SimpleBroker。 |
| 运行管理 | container/runtime/environment 管理运行资源和环境；平台 develop 与 mothership 分别负责开发侧与运行基座装配。 |
| 管理支撑 | dev-helper 和模型管理 WebAPI 脚手架复用元数据、开发模块及查询机制；datasource/cache 模块负责对应管理。 |
| 组件库与生成 | 开发侧组件库治理包的检查/导入和应用引用；项目生成只生成源码 ZIP，不编译、不写应用 VCS，也不安装发布物。 |

## 实现情况

应用开发、运行管理及项目生成已有代码入口。Docker/Kubernetes 是运行后端选项，不表示当前机器或集群已配置。平台组件库的开发治理与 ability 层消费是不同职责；独立输出仍需对应发布链。

看板及业务模型画布已有正式 renderer、页面 Schema 和行为测试资产。完整部署下的多人联调与权限生命周期仍待验收。跨实例协同广播、共享 VCS 竞争处理和浏览器关闭后的未同步草稿恢复尚未接入。业务模型定义编辑器的协同需要后续文档映射与发布适配，当前画布协同只更新画布数据。

本页按源码与配置说明能力。测试入口说明可复跑资产，实际运行与线上发布状态由对应版本的交付证据确认。

## 启用条件

需要平台账户/权限、应用工作空间及明确运行后端；操作前核对端口、namespace、工作目录和资源所有权。

画布协同还需要可用的基座实时连接、应用 VCS 及查看或编辑权限。Yjs 在浏览器中运行，后端沿用 Java 服务。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [dev-module/ouroboros-dev-app-management](../../dev-module/ouroboros-dev-app-management) · [设计/指南](../design/modules/dev-module/ouroboros-dev-app-management.md) | [测试入口](../../dev-module/ouroboros-dev-app-management/src/test/java/com/ouroboros/dev/app/DevAppAutoConfigurationTest.java) |
| [应用开发看板](../../frontends/web/ouroboros-amis-runtime/src/renderers/DevAppDashboard.tsx) · [看板设计](../design/modules/dev-module/application-development-dashboard.md) | [看板行为测试](../../frontends/web/main-app/tests/unit/src/components/amis/renderers/dev-app-dashboard.test.js) |
| [前端协同框架](../../frontends/web/ouroboros-amis-runtime/src/collaboration/YjsWorkspace.ts) · [协同设计](../design/cross-cutting/model-collaboration.md) | [前端协同测试](../../frontends/web/main-app/tests/unit/src/components/amis/renderers/business-model-collaboration.test.js)、[服务端持久化与实时测试](../../dev-module/ouroboros-dev-app-management/src/test/java/com/ouroboros/dev/app/collaboration/) |
| [dev-module/ouroboros-dev-version-control-system](../../dev-module/ouroboros-dev-version-control-system) · [设计/指南](../design/modules/dev-module/ouroboros-dev-version-control-system.md) | [测试入口](../../dev-module/ouroboros-dev-version-control-system/src/test/java/com/ouroboros/vcs/GitBranchManagementIntegrationTest.java) |
| [dev-module/ouroboros-dev-project-generation](../../dev-module/ouroboros-dev-project-generation) · [设计/指南](../design/modules/dev-module/ouroboros-dev-project-generation.md) | [测试入口](../../dev-module/ouroboros-dev-project-generation/src/test/java/com/ouroboros/dev/projectgeneration/CanonicalSignedJsonCorpusTest.java) |
| [dev-module/ouroboros-dev-container](../../dev-module/ouroboros-dev-container) · [设计/指南](../design/modules/dev-module/ouroboros-dev-container.md) | [测试入口](../../dev-module/ouroboros-dev-container/ouroboros-dev-container-docker/src/test/java/com/ouroboros/container/service/impl/DockerContainerServiceImplBaseImageTest.java) |
| [dev-module/ouroboros-dev-runtime](../../dev-module/ouroboros-dev-runtime) · [设计/指南](../design/modules/dev-module/ouroboros-dev-runtime.md) | [测试入口](../../dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/test/java/com/ouroboros/dev/runtime/broker/BrokerPortAllocatorTest.java) |
| [dev-module/ouroboros-dev-module-all](../../dev-module/ouroboros-dev-module-all) | 未检出独立行为测试；按上层/交付场景判断 |
| [dev-module/ouroboros-dev-helper](../../dev-module/ouroboros-dev-helper) · [设计/指南](../design/modules/dev-module/ouroboros-dev-helper.md) | [测试入口](../../dev-module/ouroboros-dev-helper/ouroboros-dev-helper-core/src/test/java/com/ouroboros/devhelper/command/AbstractAsyncCommandTest.java) |
| [dev-module/ouroboros-dev-environment](../../dev-module/ouroboros-dev-environment) · [设计/指南](../design/modules/dev-module/ouroboros-dev-environment.md) | 未检出独立行为测试；按上层/交付场景判断 |
| [dev-module/ouroboros-dev-datasource](../../dev-module/ouroboros-dev-datasource) · [设计/指南](../design/modules/dev-module/ouroboros-dev-datasource.md) | [测试入口](../../dev-module/ouroboros-dev-datasource/src/test/java/com/ouroboros/dev/datasource/resource/PlatformDataSourceSchemaTest.java) |
| [dev-module/ouroboros-dev-platform-component-lib](../../dev-module/ouroboros-dev-platform-component-lib) · [设计/指南](../design/modules/dev-module/ouroboros-dev-platform-component-lib.md) | 未检出独立行为测试；按上层/交付场景判断 |
| [dev-module/ouroboros-dev-model-manage-webapi-scaffold](../../dev-module/ouroboros-dev-model-manage-webapi-scaffold) · [设计/指南](../design/modules/dev-module/ouroboros-dev-model-manage-webapi-scaffold.md) | [测试入口](../../dev-module/ouroboros-dev-model-manage-webapi-scaffold/src/test/java/com/ouroboros/modelmanage/DefaultVCSUserProviderCurrentSecurityContextTest.java) |
| [platform/ouroboros-develop](../../platform/ouroboros-develop) · [设计/指南](../design/modules/platform/ouroboros-develop.md) | [测试入口](../../platform/ouroboros-develop/src/test/java/com/ouroboros/develop/PlatformDevelopKubernetesRealClusterSmokeTest.java) |
| [platform/ouroboros-mothership](../../platform/ouroboros-mothership) · [设计/指南](../design/modules/platform/ouroboros-mothership.md) | 未检出独立行为测试；按上层/交付场景判断 |
| [project-templates](../../project-templates) | [测试入口](../../project-templates/templates/frontend.component-library.react-webpack/1.0.0/tree/tests/component-library-contract.test.cjs.ftl) |

## 关联文档

- [dev-module/GUIDE.md](../../dev-module/GUIDE.md)
- [docs/design/cross-cutting/project-generation-platform.md](../design/cross-cutting/project-generation-platform.md)
- [docs/handbook/docs/fusion-development/index.md](../handbook/docs/fusion-development/index.md)

## 后端代码提示

[脚本与表达式代码提示](../design/cross-cutting/script-expression-intelligence.md)复用目标应用开发助手环境，为逻辑流、业务模型、RPC、配置、字段类型和通知中的后端代码提供统一上下文。Java 签名反射与可插拔节点分析分别负责静态类型和流程变换；无依据的动态形状保持未知。前端执行的表达式/脚本不在此能力范围。完整入口及相邻语言边界见[清单](../design/cross-cutting/script-expression-intelligence-inventory.md)。

`script-editor` 默认将当前代码的 Error 诊断接入 AMIS 表单验证并阻止提交，Warning 放行；字段可通过 `validateCode: false` 关闭代码校验阻断，`required` 等规则继续生效。Groovy 和后端 JavaScript 提供语法/语义诊断；SpEL、混合模板、配置/通知占位符、JSONPath 和 FreeMarker 提供基本语法检查。AMIS 的 `json-data-editor` 配置 `codeContext` 后同样等待字符串内的诊断。语法检查不执行代码或读取应用配置值，不保证运行结果。机制和插件合同见[前端接入](../design/cross-cutting/script-expression-intelligence.md#8-前端接入与编辑隔离)。
