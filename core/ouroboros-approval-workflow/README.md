目录结构
[ouroboros-approval-workflow-core](ouroboros-approval-workflow-core)：核心模块，包含审批流程的核心功能，如审批流数据结构定义、转译器架构、抽象接口定义等。

[ouroboros-approval-workflow-manage](ouroboros-approval-workflow-manage)：审批流管理模块，包含审批流程的管理功能，包括增删改查和部署等。

[ouroboros-approval-workflow-engine-testkit](ouroboros-approval-workflow-engine-testkit)：跨引擎共享测试基础设施，以 `pom` 作为主工件元数据并附加发布 `tests.jar`，不生成主业务 jar，用于复用契约用例和场景数据。

[ouroboros-approval-workflow-flowable](ouroboros-approval-workflow-flowable)：默认审批流执行引擎实现，承接转译器、流程部署、任务执行、流程实例查询与事件语义。

[ouroboros-approval-workflow-activiti6](../../legacy-compat/ouroboros-approval-workflow-activiti6)：保留的 Activiti6 兼容实现，用于迁移验证和显式兼容场景，不再是默认 runtime / starter 装配路径。

[ouroboros-approval-workflow-web](ouroboros-approval-workflow-web)：向外提供Web服务，包括restfulAPI、UI Model、UI Schema等。

[ouroboros-approval-workflow-qb-spring-boot-starter](ouroboros-approval-workflow-qb-spring-boot-starter)：用于 Quick 框架的
Spring boot starter，默认装配 Flowable 引擎路径。

[ouroboros-approval-workflow-runtime](ouroboros-approval-workflow-runtime)：审批流运行时集成模块，默认装配 Flowable，并提供候选人解析、管理和 Web 集成所需的运行时能力。

引擎选择约定

- 未显式配置 `ouroboros.workflow.approval.engine` 时，默认装配 `flowable`
- 主线 runtime / starter 仅装配 Flowable；将该属性改为 `activiti6` 不会切换到兼容实现。Activiti6 位于 `legacy-compat`，需要独立兼容集成与验证，不能作为主线运行方式。


## 高代码业务的自定义表单

应用自行实现业务页面时，使用 `custom` 表单类型提供设计时字段，无需逐表单实现 Provider。声明与完整流程示例见[审批接入示例](../../skills/tidestack-approval-integration/examples/)，完整资源契约见[自定义表单设计](design/custom-form.md)。

1. 在应用元数据仓库的 `approval-form/` 中新增 `purchase.approval-form.json`；模块 JAR 使用 `src/main/metadata/approval-form/`。文件的 `name` 与文件基名一致，`formatVersion` 为 `1`。
2. 字段使用平台现有字段类型及 `params`；字段名为稳定的顶层业务键。字段描述不包含某次审批的实际值。
3. 流程定义用 `form: {"formType":"custom","formId":"purchase"}` 绑定，也可在审批流新建、编辑页选择“自定义表单”和关联表单。
4. 在“流程信息”查看字段目录，在节点配置可见、可写、必填，在连线配置数值或选项条件；保存后显式部署。选项条件保存实际值。
5. 部署后的在线修改、接管保护、重新部署与恢复初始配置复用现有流程管理。字段删除或类型变化可能使草稿引用失效；草稿可保存，新部署会拒绝无效引用。在途实例继续使用原部署定义。

字段目录只提供配置能力。业务代码仍负责提供实例值、读写业务数据并执行权限；未接入运行时表单服务时，通用审批页面提示通过业务页面操作，不显示必然失败的提交按钮。
