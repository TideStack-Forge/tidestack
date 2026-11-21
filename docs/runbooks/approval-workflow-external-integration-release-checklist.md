# 审批流外部流程引擎对接发布检查清单

> 适用范围：`ability/ouroboros-approval-workflow-external-integration` 及其在 `platform/ouroboros-develop` 中的能力聚合装配。
> 本模块尚未上线，发布前只按当前最终契约验收；不保留旧路径、旧能力声明或兼容性降级逻辑。

审批继续拥有映射、实例和 receipt/inbox；连接、凭据、应用令牌及 HTTP 业务回调已迁入[企业连接基础](../../ability/ouroboros-enterprise-connector/README.md)。三家自建应用及集成管理的实际环境验收先按[企业集成验收](enterprise-integration-acceptance.md)准备。下列本地门禁不能替代真实企业授权、回调切换和存量数据演练。

## 1. 发布前硬门禁

- [ ] `platform/ouroboros-develop` 通过 `ouroboros-ability-all-runtime` 引入 external-integration runtime 与稳定预置适配器。
- [ ] `dingtalk-oa` 默认稳定契约只承诺 `RESULT_ONLY + LINK`（AppLink `externalUrl`）；`MIRROR_*` 能力与流程中心镜像投影已于 2026-09-11 随 `a7903ae09d` 删除，`authority=OUROBOROS` 现在只表示内置引擎执行。不声明稳定 `FULL_PROCESS`、任务查询、任务表单或任务动作能力。
- [ ] 调度作业默认不注册；只有显式设置 `ouroboros.workflow.approval.external.scheduling.enabled=true` 后才启用补偿和清理作业。
- [ ] 所有 ACTIVE mapping 都通过连接健康检查、定义校验、能力门禁、scope/RBAC 校验和结果映射覆盖校验。
- [ ] 本地回归通过：runtime reactor、DingTalk reactor、装配聚合 compile/check 至少各执行一次。
- [ ] DingTalk `RESULT_ONLY` 官方接口对齐 contract 在受控 profile 下通过；真实企业应用 smoke 作为灰度/运行证据，不作为本次未上线模块修订的自动门禁。
- [ ] DingTalk `START_UNKNOWN` 按本清单的运行处置执行：v1 不声明 `ExternalWorkflowStartRecovery`，不得自动重发、候选搜索绑定或人工强制绑定 external instance ID。

## 2. 本地验证矩阵

| 门禁 | 证明方式 |
|---|---|
| connection-aware routing | `ConnectionAwareAdapterResolverRoutingTest` + runtime reactor 验证领域路由隔离；部署级目录中同渠道只允许一个连接，由 `EnterpriseConnectionCatalogTest` 核对 |
| scheduling explicit switch | `ApprovalWorkflowExternalIntegrationSchedulingConfigurationTest`；默认无调度 bean，开关为 `true` 后注册 |
| DingTalk default capability | `DingtalkActivationInterceptionTest`、`DingtalkEngineAdapterContractTest`；第三方连接无法激活 `FULL_PROCESS` |
| unique facts and idempotency | `ExternalIntegrationPersistenceMetadataTest`、`ExternalBindingCasServiceTest`、mapping/binding service tests |
| shared callback ingress | `EnterpriseCallbackIngressTest`、三家 CallbackProtocolTest 与审批 CallbackCodecTest；统一入口验签/解密，全部匹配订阅可靠受理后 ACK，失败可重投 |
| event ordering and CAS | `ExternalEventProcessorTest`、`ExternalEventOrderTest`；旧 revision/eventTime 不覆盖新终态 |
| polling/reconcile boundary | `ExternalPollingCompensationJobTest`；只处理声明 `POLLING` 的 adapter，DingTalk 默认不声明 `POLLING`；单实例 detail reconcile 由 `ExternalStateReconcileJobTest` 覆盖 |
| reconcile / outbox lease | `ExternalStateReconcileJobTest`（镜像族测试 `TaskCenterMirrorHookTest`、`MirrorPublishServiceTest`、`ExternalMirrorOutboxServiceTest` 已随镜像投影一并删除） |
| scope/RBAC/tracking fail-closed | `EngineMappingPermissionGuardTest`、`FailClosedExternalBusinessRecordPermissionGuardTest`、tracking controller tests |
| pagination/statistics no write side effect | `RoutedFlowInstanceServiceTest`、`RoutedFlowTaskServiceTest`；offset>0 合并截断、count/query 不写 binding |
| platform/develop assembly | `mvn -pl platform/ouroboros-develop -am -DskipTests compile` 或等价基座启动 smoke，确认 external auto-configuration loaded |

## 3. DingTalk contract

### 3.1 RESULT_ONLY 自动发布门禁（按官方接口文档对齐）

`-Pcontract` 必须在无真实 DingTalk 凭据的普通 CI 环境通过，且不能通过 `assumeTrue`、条件跳过或明文 options shortcut 放行。它只 include `DingtalkResultOnlyReleaseContractTest`，并必须覆盖：

- 正式 `EnterpriseConnectionCatalog` + `CompositeSecretResolver` + `credentialRef` + typed `DingtalkCredential` 建连；连接保存非敏感的 provider/corpId 等字段，不含已删除的 `options/callbackUrl` 公共字段。
- mock HTTP 断言官方 token、start 请求的 URL、body 和 `x-acs-dingtalk-access-token` header 形状；其余 HTTP 操作另由原 client contract 测试覆盖，不将本用例写成全 API 验证。
- RESULT_ONLY start 不发送 `approvers`、`ccList`、`targetSelectActioners`；详情跳转返回 DingTalk AppLink，缺 `corpId` fail-closed。
- callback 按官方签名与加解密规则进入统一 `EnterpriseCallbackDispatcher` 及 `DingtalkApprovalCallbackSubscriber`，验证受理与加密 ACK。本用例的 receipt 是测试替身；真正的持久幂等、冲突与恢复由原 receipt/inbox 测试及数据库组件验证证明，不能据此宣称数据库已通过。
- `DingtalkFullProcessContractTest` 只在 `-Pfull-process-research` 下运行，不能计入 RESULT_ONLY release gate。

```bash
mvn -Pcontract test \
  -pl :ouroboros-approval-workflow-external-integration-dingtalk \
  -am \
  -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=DingtalkResultOnlyReleaseContractTest
```

### 3.2 灰度/运行期真实 RESULT_ONLY 证据

真实企业应用 smoke 用于灰度和运行期确认，普通 CI 的上述自动合同不要求真实凭据；开放相应生产能力前仍须完成真实企业验收。执行时凭据只能通过 `credentialRef` 指向安全来源，禁止写入仓库、测试资源、普通连接字段或日志。建议归档：

- commit SHA、profile、执行时间、DingTalk connectionId 和脱敏 processInstanceId。
- 连接的 provider、connectionId、corpId、应用形态及凭据代次等脱敏信息；不保存密钥、令牌或凭据文件内容。
- start 返回 externalInstanceId 和不含占位符的详情链接。
- 统一 `GET/POST /api/enterprise-callbacks/{connectionId}/callback` 的 challenge、签名验证、加密 ACK、审批 receipt 状态、eventId 数量、inbox 行、result binding route key、终态映射和 canonical result event 记录。
- 同一已验证业务事件重投可确认且不重复入箱；同一事件身份不同摘要被拒绝。审批订阅将稳定事件身份传给原 receipt，不能再把一次 HTTP 传输的 nonce 当作唯一业务去重依据。
- 测试结束后的清理记录或明确人工步骤；如果外部平台不支持自动清理，必须写明人工处理方式，不得吞掉失败。

### 3.3 `START_UNKNOWN` 运行处置

适用于 DingTalk start 请求在本地 overall deadline、网络中断或本地绑定持久化失败后无法证明远端结果的情况。它表示“远端结果未知”，不是“远端创建失败”。当前 DingTalk 官方公开契约没有请求级幂等键、按本地 request fingerprint 唯一查询，或“未找到即可安全重发”的保证，因此 v1 不支持 `ExternalWorkflowStartRecovery`。

1. 保留原始 `startFactId`、adapter/connection/mapping identity、业务记录 identity、request fingerprint、发生时间和脱敏错误摘要；不得删除或改写为成功/失败。
2. 不得重新发送 start，不得以时间、发起人或业务字段搜索候选实例后自动绑定，也不得接受任意 external instance ID 的人工强制绑定。
3. 优先等待已认证 callback 或原调用的已知迟到响应；两者可通过既有事实安全收敛。
4. 若运维独立取得 external instance ID，只能调用官方单实例详情接口调查。调查不能证明其属于本地 start fact，不得据此更新 binding。
5. 确认远端实例存在时，按 DingTalk 实际权限和平台政策取消或关闭该实例；保留原 start fact 与审计记录，再经正常业务流程发起新的提交。无法确认时保持 `START_UNKNOWN`，交由业务人工决策，不重放旧请求。
6. 只有官方公开资料将来明确提供稳定 idempotency key 或按本地 correlation identity 的唯一查询语义时，才允许提案实现 recovery port；该改动必须先通过 focused contract，证明同一事实不会产生第二个远端实例。

### 3.4 FULL_PROCESS research-only

`DingtalkFullProcessContractTest` 只用于未来重新评估原生任务查询/表单/动作，不是 release gate；默认构建和 `-Pcontract` 都排除它。需要实验时单独运行：

```bash
DINGTALK_CONTRACT_APP_KEY=... \
DINGTALK_CONTRACT_APP_SECRET=... \
DINGTALK_CONTRACT_CORP_ID=... \
DINGTALK_CONTRACT_PROCESS_CODE=... \
DINGTALK_CONTRACT_ORIGINATOR_USERID=... \
DINGTALK_CONTRACT_INSTANCE_ID=... \
DINGTALK_CONTRACT_TASK_ID=... \
mvn -Pfull-process-research test -pl :ouroboros-approval-workflow-external-integration-dingtalk -Dtest=DingtalkFullProcessContractTest
```

任务查询/任务动作探测结果只能写入 research 文档，不得改变默认 provider 能力，不得作为 `RESULT_ONLY` release contract 证据。

## 4. 灰度与切换

1. 灰度阶段保持 `ouroboros.workflow.approval.external.unmapped-policy=internal`，只为目标 flowKey 激活外部映射。
2. 对每个目标 mapping 执行 batch validate，失败项必须修复后重新验证，不允许带失败激活。
3. 先开启 `RESULT_ONLY` 小流量流程，观察 result binding、inbox、outbox、pending assignment、callback 延迟和未知状态指标。
4. 补偿/清理调度只在明确需要时开启，并记录启用窗口、负责人和期望作业。
5. 全量切换到未映射拒绝时，按 `docs/runbooks/approval-workflow-external-engine-migration-runbook.md` 执行。

## 5. 回滚策略

- 新实例路由回滚：暂停或 retire 对应 ACTIVE mapping；全量迁移场景再把 `unmapped-policy` 从 `reject` 改回 `internal`。
- 调度回滚：将 `ouroboros.workflow.approval.external.scheduling.enabled=false`，停止补偿/清理作业继续消费。
- 结果型外部实例回滚：不得自动回退 Flowable 或重启内置实例；由运维按外部平台能力取消外部实例，并基于业务记录重新发起或人工处理。
- ~~镜像投影回滚~~：**该步骤已失效**——镜像投影链路已于 2026-09-11 删除（`a7903ae09d`），没有 mirror outbox 或 mirror mapping 可停。若历史遗留的流程中心卡片需要清理，按外部平台后台人工处理。
- 事件处理回滚：保留 inbox/binding/outbox 事实；修复后按死信/重试入口重放，不删除事实表制造成功假象。

## 6. 禁止事项

- 禁止外部失败后静默回退 Flowable。
- 禁止把 DingTalk 流程中心待办更新当作 OA 审批同意/拒绝。
- 禁止为旧实验字段、旧 capability 或旧 callbackUrl 形态增加兼容分支。
- 禁止未通过 RESULT_ONLY 官方接口对齐 contract 就开放 DingTalk RESULT_ONLY 生产 mapping。
- 禁止未通过独立真实凭据 research/contract 就开放 DingTalk `FULL_PROCESS`。
