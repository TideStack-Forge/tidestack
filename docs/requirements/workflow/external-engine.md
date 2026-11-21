# 审批流外部流程引擎对接规格（钉钉 / 企业微信 / 飞书）

> 状态：Accepted（2026-09-12 依据实现补写）
> 适用范围：审批流以 `authority=EXTERNAL_ENGINE` 接入第三方流程引擎的产品行为——支持渠道、默认能力档位与 `RESULT_ONLY` / `FULL_PROCESS` 派生规则、映射激活前的校验、以及"能力不支持"时的用户可见表现。
> 相关设计：[外部集成模块设计](../../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)（Accepted，**只引用其 §现行与历史分区标注为现行的小节**）；运维：[外部引擎迁移手册](../../runbooks/approval-workflow-external-engine-migration-runbook.md)、[发布前门禁清单](../../runbooks/approval-workflow-external-integration-release-checklist.md)；关联规格：[可发起流程接口契约](available-workflows.md)

## ① 背景与问题

1. 外部流程平台的定义 ID、用户标识、动作名、状态码、表单与回调协议都与内置引擎不同，不能把外部 API 塞进引擎实现或 `WorkflowModel.json`。
2. 多数外部引擎（尤其钉钉原生审批）不开放任务级操作，若强制要求"全过程接入"才能对接，等于把可对接范围压到零；因此需要"只关注结果"的一等模式。
3. 管理员配置映射时最容易犯的错是"选了一个适配器根本不具备的能力"（例如给没有任务端口的引擎配全过程）；这类不一致必须在**激活前**被拦住，而不是运行到用户发起时才失败。
4. 外部调用失败时若静默回退内置引擎，会出现同一次审批在两个引擎各跑一遍；因此回退是被禁止的行为。

## ② 目标

1. 支持钉钉、企业微信、飞书三种渠道以 `EXTERNAL_ENGINE` 接入，各自注册为一个只读适配器条目。
2. 接入档位由适配器**实际具备的端口**派生：能力不足即 `RESULT_ONLY`，`FULL_PROCESS` 需要同时具备实例查询与任务查询端口。
3. `RESULT_ONLY` 提供发起 + 终态结果获取（回调或轮询补偿），且不伪造本地任务。
4. 映射激活前完成能力、定义、动作/结果覆盖、连接健康四类校验，失败即拒绝激活并给出错误码。
5. 运行期遇到未实现能力时返回明确的"能力不支持"错误码，而不是静默降级或回退内置引擎。

## ③ 非目标

- 不替换内置引擎（Flowable）语义；未映射流程默认仍由内置引擎执行（`unmapped-policy=internal`）。
- 不提供外部流程定义的写/部署能力（`ExternalProcessDefinitionValidator` 只做校验与发现）。
- 不为编排型能力（实例暂停/恢复、发起人撤销）做对外承诺：`EXTERNAL_INSTANCE_ACTION` / `EXTERNAL_INSTANCE_CANCEL` 无 provider 声明，属未落地预留项（`Capability.java` 类注释）。
- 不做外部任务的本地改派与本地候选人设置。
- 不定义凭据的安全存储机制（`credentialRef` 仅作引用透传，见设计文档 §安全与权限）。

## ④ 用户故事

1. 作为平台管理员，我希望在适配器目录里看到三个渠道及其能力，以便知道每个渠道能配到什么程度。
2. 作为平台管理员，我希望在启用映射前就被拒绝，以便不会把用户导向一条走不通的路径。
3. 作为审批发起人，我希望发起走外部引擎的流程时失败原因明确，以便知道是连接问题还是能力问题。
4. 作为运维人员，我希望外部失败时系统不会偷偷改走内置引擎，以便两个引擎不会同时产生实例。
5. 作为最终用户，我希望 `RESULT_ONLY` 流程用业务记录与外部实例引用查询结果，以便不需要在本系统里看到外部任务。

## ⑤ 功能规格

**支持渠道与适配器（均由 Spring `@Bean` + `AutoConfiguration.imports` 注册，非 SPI）**

| 渠道 | provider | adapterId | 显示名 | 适配器实现的端口 | 派生档位 |
|------|----------|-----------|--------|------------------|----------|
| 钉钉 | `dingtalk` | `dingtalk-oa` | 钉钉 OA 审批 | 校验 / 表单 / 发起 / `ExternalInstanceReader` / 回调 | `RESULT_ONLY` |
| 企业微信 | `wecom` | `wecom-approval-third-party` | 企业微信第三方应用审批 | 校验 / 表单 / 发起 / `ExternalInstanceDetailReader` / 回调 | `RESULT_ONLY` |
| 飞书 | `feishu` | `feishu-approval-native` | 飞书原生审批 | 校验 / 表单 / 发起 / 发起恢复 / `ExternalInstanceDetailReader` / 回调 | `RESULT_ONLY` |

（除上述三者外，`fake-external-engine` 为演示/灰度适配器，经 SPI 注册，声明 `POLLING` 且实现任务查询与动作执行端口。）

**档位派生规则（不是管理员可填字段）**

- `EXTERNAL_ENGINE`：流程真相源在外部引擎；`OUROBOROS`：本地内置引擎执行，语义已收窄为"内部引擎执行"（镜像投影链路已删除，见 ⑦）。
- `integrationModeOf(adapterId)`：同时具备 `EXTERNAL_INSTANCE_QUERY`（端口 `ExternalInstanceDetailReader`）与 `EXTERNAL_TASK_QUERY`（端口 `ExternalTaskReader`）才派生出 `FULL_PROCESS`，否则一律 `RESULT_ONLY`（`WorkflowEngineAdapterRegistry.java:158-163`）。
- `executionModeOf(adapterId)`：实现动作执行端口才是 `PROXY`，否则为 `LINK`（平台只能跳转外部处理，`:169-172`）。
- 能力集按端口 `instanceof` 派生，descriptor 只能**追加**细粒度能力；声明了有专属端口的能力却没实现端口 → **启动期失败**（`EngineAdapterCapabilities.derive`），不留到运行时。

**映射激活前的校验（任一失败即拒绝激活，`WorkflowEngineMappingValidator.run`）**

- 适配器存在（`ENGINE_ADAPTER_NOT_FOUND`）、processKey 存在于 `WorkflowModel`、authority 合法、结果映射覆盖 `RUNNING/APPROVED/REJECTED/CANCELED/ERROR`、外部定义校验通过、连接健康检查通过。
- 能力校验（`EXTERNAL_CAPABILITY_UNSUPPORTED`）：`EXTERNAL_ENGINE` 必须 `EXTERNAL_START` + `DEFINITION_VALIDATION`；`RESULT_ONLY` 追加 `EXTERNAL_FINAL_RESULT`，且 `CALLBACK` / `POLLING` **至少具备其一**（两者都没有则要求两者都有，必然失败）；`FULL_PROCESS` 追加 `EXTERNAL_INSTANCE_QUERY` + `EXTERNAL_TASK_QUERY`。
- 自洽校验：`RESULT_ONLY` **不允许**配置任务动作映射；`authority=OUROBOROS` **不允许**任何动作映射；动作必须在平台规范动作集内且被适配器 descriptor 声明（`EXTERNAL_ACTION_NOT_MAPPED`）。
- 管理入口：`/api/approval-workflow/engine-mappings`（列表/创建/更新/`validate`/`activate`/`pause`/`retire`），权限点细分到 `VIEW/EDIT/VALIDATE/ACTIVATE/PAUSE/RETIRE`；适配器目录 `/api/approval-workflow/engine-adapters` 为只读、不返回凭据。

**"能力不支持"的用户可见表现**

- 未注册渠道 / 不存在的 adapterId：`ENGINE_ADAPTER_NOT_FOUND`；连接配置缺少必填字段（钉钉/企业微信 `corpId`）为 `ENGINE_CONNECTION_UNAVAILABLE`；两者都在**保存/激活阶段**可见。
- 运行期对未实现能力统一返回 `EXTERNAL_CAPABILITY_UNSUPPORTED`，且**不回退内置引擎**：实例动作、发起人撤销、外部实例耗时统计、外部任务本地改派/候选人设置均属此列。
- `RESULT_ONLY` 流程不支持实例级操作：调用即返回 `RESULT_ONLY 流程不支持实例<操作>操作`。
- 适配器级细化拒绝：钉钉发起时传抄送 / 覆盖审批人 / 自选审批人，或表单值为复合类型（Map/List）时返回 `EXTERNAL_CAPABILITY_UNSUPPORTED` 并点明字段。
- 用户标识缺少平台映射时 `EXTERNAL_USER_MAPPING_MISSING`（禁止把外部字符串或 `null` 当平台用户）。
- 未配置映射且 `unmapped-policy=reject` 时发起失败：`WORKFLOW_ROUTE_NOT_CONFIGURED`，消息为"流程未配置映射，且 unmapped-policy=reject 禁止发起"。
- 原生渠道回调：`POST /api/approval-workflow/external/dingtalk|wecom|feishu/{connectionId}/callback`；另有按 codec 端口接入的通用回调 `POST /api/approval-workflow/external/{adapterId}/callbacks/{connectionId}`，两者不可随意互换；轮询补偿为定时作业，仅在显式开启 `ouroboros.workflow.approval.external.scheduling.enabled=true` 后启用。

## ⑥ 验收口径

- [ ] 适配器目录同时列出 `dingtalk-oa` / `wecom-approval-third-party` / `feishu-approval-native`，三者能力集中均含 `EXTERNAL_START`、`DEFINITION_VALIDATION`、`EXTERNAL_FINAL_RESULT`、`CALLBACK`，且均**不含** `EXTERNAL_TASK_QUERY`。
- [ ] 验证上述渠道的注册表派生模式均为 `RESULT_ONLY`，映射没有可手填的 `integrationMode`；构造缺少模式所需端口的适配器，验证注册或能力校验拒绝不实声明。
- [ ] 给 `RESULT_ONLY` 映射配置任一条任务动作映射 → 拒绝激活，错误码 `WORKFLOW_MAPPING_NOT_ACTIVE`。
- [ ] 结果映射缺少 `CANCELED` 等任一必需终态 → 拒绝激活，错误码 `EXTERNAL_MAPPING_INVALID`。
- [ ] 连接不可用时激活失败，错误码 `ENGINE_CONNECTION_UNAVAILABLE`。
- [ ] 已激活映射的流程发起失败时**不产生**内置引擎实例（无回退）。
- [ ] `unmapped-policy=reject` 下未映射流程发起返回 `WORKFLOW_ROUTE_NOT_CONFIGURED`；回滚为 `internal` 后重新可发起。
- [ ] `bash scripts/docs/check-links.sh` 输出 `No broken links found`。

## ⑦ 已知限制与未实施项

- **三家渠道当前都只有回调、没有轮询补偿**：descriptor 只声明 `EXTERNAL_FINAL_RESULT`，均未声明 `POLLING`；轮询作业会跳过未声明 `POLLING` 的适配器（`ExternalPollingCompensationJob.java:84`），钉钉的 `queryChangedInstances` 更是直接返回"钉钉未实现增量 polling"。当前唯一声明 `POLLING` 的是 `fake-external-engine`。
- **`FULL_PROCESS` 对三家渠道当前不可达**：没有适配器实现 `ExternalTaskReader`，派生档位恒为 `RESULT_ONLY`。重新评估钉钉全过程接入时，须在关联任务按真实租户、应用类型和授权范围验证任务可见性、表单/详情读取、agree/refuse 角色权限、任务顺序、错误与回调闭环；未取得证据并采纳扩展设计前保持当前能力边界。
- 实例级动作（暂停/恢复）与发起人撤销无 provider 声明，入口恒定返回"能力不支持"。
- 未核实：三家渠道的真实企业联调状态与回调订阅的注册方式；`credentialRef` 的实际解析路径与密钥存储。
- 未核实：设计文档 §能力矩阵 是否逐格与三家适配器现状一致（本文只按各 provider 的 descriptor 与端口核实）。

三家渠道通过回调取得终态；通用轮询能力只适用于声明了 `POLLING` 的适配器。迁移手册已同步删除外部镜像承诺，`authority=OUROBOROS` 仅表示内置引擎执行。

## ⑧ 相关文档

- 设计：[外部集成模块设计](../../../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)
- 运维：[外部引擎迁移手册](../../runbooks/approval-workflow-external-engine-migration-runbook.md)、[发布前门禁清单](../../runbooks/approval-workflow-external-integration-release-checklist.md)
- 关键实现：[WorkflowEngineAdapterRegistry.java](../../../ability/ouroboros-approval-workflow-external-integration/ouroboros-approval-workflow-external-integration-runtime/src/main/java/com/ouroboros/workflow/approval/external/runtime/WorkflowEngineAdapterRegistry.java)、[WorkflowEngineMappingValidator.java](../../../ability/ouroboros-approval-workflow-external-integration/ouroboros-approval-workflow-external-integration-runtime/src/main/java/com/ouroboros/workflow/approval/external/runtime/mapping/WorkflowEngineMappingValidator.java)、[Capability.java](../../../ability/ouroboros-approval-workflow-external-integration/ouroboros-approval-workflow-external-integration-core/src/main/java/com/ouroboros/workflow/approval/external/Capability.java)
