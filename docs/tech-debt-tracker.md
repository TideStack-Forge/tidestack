# 技术债

本台账只保留未闭合问题、影响、偿还路径及退出条件。归档过程文档不会自动关闭代码债务；历史条目须按现行源码与目标环境复核，不能把旧观察当作当前复现。负责人由对应实施任务确定。存量架构债务仍以 `testing/architecture-tests/src/test/resources/architecture-allowlist.tsv` 和正式扫描为依据；未逐项复核的历史欠缺不因正文清理而视为关闭。

## COLLAB-001 模型画布协同的交付边界

- 当前范围为业务模型关系画布、单开发工作区与 SimpleBroker。Redis 或 RabbitMQ 下的跨实例订阅广播、共享 VCS 存储竞争处理，以及浏览器关闭后恢复未同步操作尚未接入。
- 影响是试点代码和测试资产尚不足以证明多实例部署及关闭浏览器后的离线恢复能力。完整部署中的多人操作、权限变化和页面生命周期仍待验收。
- 处置沿现有实时通信、VCS 事务和 Yjs 文档机制扩展，具体协议与资源职责维护在[模型实时协同](design/cross-cutting/model-collaboration.md#当前边界与演进)。业务模型定义编辑器的协同另需文档映射、草稿物化和原发布校验链适配。
- 退出条件为对应部署范围的跨实例广播与存储并发、失败恢复、权限拒绝及多人端到端场景通过，离线草稿恢复按已实现范围在原设计和功能地图中更新。执行证据归任务或 CI 附件。

## NAV-001 导航所有权（原记录 2026-05-09）

状态：架构差距已修复。原问题为 router 与 tabs 同时写 URL，并依赖全局重入标记协调，造成 route 与 activeTab 提交顺序不清。

当前正式路径为 `tabNavigationCommand → routerEngine.performNavigation`：所有守卫接受后，router 执行标签状态投影，随后提交 URL、route 并通知消费者。标签命令携带本次导航意图，路由同步携带 `isCurrent` 围栏；取消或被新导航替代的操作不投影。`installHostRouter` 组合拓扑、守卫和执行适配，router engine 不反向导入 tabs，原导入环已拆除。全局 `preventTabOperation` 状态、mutation 和兼容 action 已删除。

验证覆盖离开守卫取消、快速导航、前进后退、切换与关闭标签、查询参数更新、关闭最后一页的普通/全屏/外链首页，以及登录后跳转；Web 完整单测及正式主壳浏览器流程已执行。浏览器流程使用本地协议夹具；本条不据此承诺生产环境的性能指标或外部微前端服务可用性。


## DOC-001 外部审批适配能力与真实租户验证

- 差距：平台端口不等于供应商能力；撤销、记录、任务查询/动作、订阅生命周期与补偿能力须逐渠道核实，不能依据早期清单认定全部已支持或全部未实现。
- 依据与影响：以[外部引擎规格](requirements/workflow/external-engine.md#⑦-已知限制与未实施项)和[模块能力矩阵](../ability/ouroboros-approval-workflow-external-integration/docs/module-design.md)维护当前合同；没有真实租户证据的范围不能作为生产可用性承诺。
- 退出：分别补齐已采纳的能力与注册，执行本地合同及必需的真实租户场景，按渠道关闭；与[EXT-001](risk-tracker.md#ext-001-外部审批渠道的真实企业可用性)共同维护环境边界。

## DOC-002 企业协作后置能力

- 差距：企业微信 JS-SDK 审批启动桥接、通用外部待办投影尚未交付；Activiti6 位于 `legacy-compat/`，不属于主线引擎支持。
- 约束：审批 MIRROR 路径已删除，不能用旧镜像端口补出第二条待办路径；待办中心尚无生产模块，也未因旧提案存在而获得实现承诺。
- 退出：有明确需求并采纳设计后，沿[企业协作集成边界](design/cross-cutting/enterprise-collaboration-integration.md#14-当前实现边界)实施和验证；未立项部分继续标为能力边界。

## DOC-006 SuppressWarnings 基线与门控

- 问题：`scripts/quality/check-suppresswarnings-ratchet.sh` 的 allowlist 无登记项时回退硬编码 379；Jenkinsfile 未接入对应独立门控。
- 影响：检查通过不等于所有存量豁免已登记或 CI 已阻止新增。
- 退出：核定并登记有效基线、消除回退依赖，把现有检查接入实际 CI 并验证新增违规能阻断交付。

## DOC-007 未启用的质量机制

- 边界：SpotBugs、质量评分看板、逐域评分等不属于当前已启用的门禁；具体状态由[自动化检查设计](design/automation-checks.md#6-未启用的机制)维护。
- 影响：不能用旧规划中的评分或看板宣称存在持续质量反馈。
- 处置：如确认需要这些机制，应沿现有质量检查与 CI 扩展；采纳前不将旧提案视为默认实施任务。当前有效质量约束不因此取消。

## DOC-008 AppModule authority bridge 建模

- 状态：已处理。AppModule authority bridge/projection band 已纳入共享的 `ArchitectureLayerSupport`；[ADR-004](adr/ADR-004-appmodule-authority-bridge-band.md) 与两套架构入口采用同一语义模型。
- 维护要求：新增投影类型须补精确分类和合法/非法依赖夹具，未知模块根目录应使门禁失败；不扩大存量 allowlist。

## DOC-009 连接变更约束

- 问题：集成设计要求 provider 创建后不可变、连接被引用时禁止复用/删除，但强制校验点尚不完整。
- 影响：连接标识可保持相同而实际协议或引用语义发生变化。
- 退出：在正式连接服务与引用生命周期补齐校验，覆盖创建、修改、删除和引用冲突；不由各领域另建连接目录。

## DOC-010 恢复扫描的非唯一组合索引

- 问题：数据模型缺少非唯一组合索引的声明能力；审批事件 outbox、外部 action/start fact 与 event inbox 的 `(status, claimUntil)`、`(status, nextAttemptAt)` 等恢复扫描索引没有相应正式声明。
- 影响：批次上限可限制 JVM 成本，但不能消除大表数据库扫描成本。
- 待决定：补数据模型索引声明；或运维 DDL 与模型同步；或以真实数据量证明可接受并记录理由。
- 退出：形成唯一索引管理机制，验证升级与目标查询计划，不静默依赖人工建库状态。

## API-001 版本化接口契约与漂移校验

- 当前状态：已有统一 WebAPIModel 导出，缺少全平台版本合同、生成一致性、破坏性比较与消费者兼容门控。
- 影响：路由变化可能未反映到对外合同，鉴权和全局错误响应导出也有覆盖限制。
- 退出：沿[契约生命周期](api/contract-lifecycle.md)建立可重复生成、按版本追溯的合同；非法引用和破坏性变更能阻断交付，受影响消费者行为通过。不手写第二份合同。

## CI-001 前端发布质量门控

- 当前状态：Web 发布入口可见安装/build，同链路缺少明确 lint/test 阶段；远端 MR job 设置仍需核实。
- 影响：发布成功无法证明前端规范和行为回归通过。
- 退出：核对实际 job，复用各包正式 lint/test 接入必要门控；故意失败能阻断交付，允许例外和恢复条件可追溯，见[CI/CD 规则](rules/ci-cd.md)。

## TEST-001 前端历史 runner 和目录例外

- 当前状态：logicflow-editor 的 `test:unit` 聚合历史 node:test 与 Vitest，部分存量测试目录仍有漂移。
- 影响：只运行新目录可能漏掉原有行为。
- 退出：按[前端测试规范](../frontends/web/TESTING.md)迁移等价行为，标准入口完整执行后删除旧 runner 分支；迁移期间保留原聚合入口。

## TEST-002 JUnit 兼容台账与源码归属

- 问题是[依赖策略](../scripts/ci/dependency-policy.yaml)记录的 JUnit 4 来源计数、有效覆盖与 Vintage 继承归属和当前源码扫描不一致。
- 影响是依赖策略门禁及其仓库正例测试持续报告漂移，后端发布预检无法凭该检查获得通过状态。
- 退出条件为按真实测试来源和 POM 继承关系修正必要源码或台账，正式依赖策略检查及其行为测试通过。非法坐标、例外范围和到期约束继续由原验证器执行。

## DOC-013 WebSocket 工程样例配置

- 剩余问题：RabbitMQ relay 样例中的 username/password 与 system/client login/passcode 属性差异仍须在工程输入中修正。
- 影响：按旧样例启动不等于正确绑定凭据；使用前以当前配置类及正式指南为准。
- 退出：统一样例与属性合同，完成凭据绑定和实际 broker 行为验证。非凭据属性的修正不代替该验证。

## DOC-014 应用开发工具的验证缺口

- 项目生成：API 回退、下载后工程构建及正式 E2E manifest 登记尚未完成；须按生成入口分别验证，不能把浏览器下载成功等同于项目可构建。
- 国际化联动：AMIS Schema memo 的语言版本依赖已修复，实际控件、菜单/页签与微前端联动仍需完整浏览器场景，见 [i18n 设计](../frontends/web/main-app/docs/i18n-design.md)。
- 待复核观察：手册验收曾发现统计分析 appName/env 绑定与发布链、权限树会话绑定、项目生成表单 500 和 UI Schema 代码弹窗缺少保存入口；均为旧环境观察，须先在当前部署复现，不能据旧报告直接判断当前仍失败。
- 退出：在对应工具的正式测试资产登记并执行场景，将可复现缺陷交由正式实现任务修正；任务/CI 保存实际结果，台账不堆积逐次测试流水。

## DOC-020 Spreadsheet multipart 导入失败

- 剩余问题：Excel 导入经过 `OpenApiFilter` / `MyHttpServletRequestWrapper` 时，二进制 multipart 被作为字符流解码，曾抛出 `MalformedInputException` 并返回 500；须在当前运行基线复现并修复认证过滤器根因。
- 边界：既有构建坐标、Druid/Redis 属性与 JSON 导出绑定已修正，不代表 multipart 导入可用。历史 beta/Boot 2.7 环境也不能代表当前平台部署验收。
- 退出：沿原过滤器入口完成含数据导入/导出回归，覆盖二进制请求和失败传播；生产、多实例和其他数据库验证分别取证。

## SEC-001 OAuth 共享协议适配器待实现

- 差距：已采纳的 OAuth2AuthorizationCodeFlow / OAuth2ProtocolAdapter 共享宿主尚未实现；现有 OAuth2Provider 与 Connector 平台实现继续工作。
- 影响：协议编排、平台差异与安全校验仍分散维护。
- 退出：按[适配器设计](../security/oauth-platform-adapters-design.md)在原 provider/builder 注册链迁移，保留租户、state/revision 和身份映射边界，完成各平台行为及真实租户验收。

## SEC-002 外部绑定的多节点并发边界

- 问题：ExternalAuthService.decrementBindingToken 使用 intern 后的 synchronized，只能保证本进程互斥。
- 影响：共享缓存下的全局次数上限和一次性消费不能据本地锁保证。
- 退出：在原 CacheManager/DistributedLockManager 与绑定服务内完成跨实例并发验证，覆盖总次数、单次消费、配置切换与恢复；不另建绑定仓库。

## SEC-003 OIDC state 的空类型查找兼容缺口

- 问题：[确认流程契约](../ability/ouroboros-oidc-server/consent-flow-design.md)要求空 tokenType 也匹配 state；[CacheOAuth2AuthorizationService.findByToken](../ability/ouroboros-oidc-server/src/main/java/com/ouroboros/ability/auth/oidc/service/CacheOAuth2AuthorizationService.java) 仅在显式 state 类型时使用 state 索引，null 走普通令牌索引并校验 OAuth2Token。
- 影响：空类型查询 pending state 的消费者不能获得契约要求的记录；不据此推定标准 consent provider 的显式 state 路径也受影响。
- 退出：在原授权服务补齐/确认兼容行为，覆盖显式 state、null、无效、到期和已消费场景；保留 state/令牌隔离与生命周期校验。
