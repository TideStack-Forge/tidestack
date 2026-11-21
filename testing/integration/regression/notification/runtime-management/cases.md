# 通知运行时配置管理回归

| 场景 | 验证结果 | 主要资产 |
| --- | --- | --- |
| 来源解析 | 数据库 -20、文件 -10、JAR 0；默认读取共享原算法；故障不伪装缺省 | NotificationTemplateCenterTest、DatabaseNotificationTemplateProviderTest |
| 保存和撤销 | 完整覆盖、停用/空渠道/空值、无修改不写、删除后回退当前默认 | NotificationTemplateRuntimeManagementServiceTest |
| 并发与迁移 | 真实唯一约束、首次保存竞争、过期更新/撤销、事务回滚、历史回填、重复名称阻止迁移 | NotificationTemplatePersistenceIntegrationTest |
| 校验 | 字段来自 Java 类型、未知字段、必填/范围、保护配置引用、候选深复制；检查无写入/发送 | NotificationTemplateDefinitionValidatorTest、ClassesManagementSchemaTest |
| 正式渠道组合 | 九个真实 Supplier；企业渠道通过纯描述校验，其他渠道沿原装配；可选对象、计算 getter、空选项、范围；已注册凭据策略及钉钉旧凭据清除策略拒绝匿名读取 | ChannelManagementScenario.java、run-channel-scenario.py |
| 渠道参数撤销 | 条件删除原配置行、空默认、旧版本冲突、删除重建防 ABA、旧组件恢复语义 | ConfigurationManagementSecurityTest、ConfigurationValueServiceImplTest、ConfigurationPersistenceIntegrationTest |
| 多实例 | 两个真实 Spring context 预热共享 Redis 后保存/撤销均读取新 revision | ConfigurationPersistenceIntegrationTest |
| 凭据与权限 | 管理脱敏、空值保留、独立配置权限、匿名/未授权拒绝、原通知权限放行 | NotificationChannelSecretManagementPolicyTest、NotificationManagementAuthorizationIntegrationTest |
| 原发送链 | 一次发送观察一次模板、可靠计划冻结内容、执行时可用性、邮件主机变化 | NotificationDispatcherImplTest、ReliableNotificationDispatchIntegrationTest、WorkflowNotificationDeliveryIntegrationTest、EmailServiceImplTest |
| 页面闭环 | 生产 schema 查看/编辑、动态渠道、纯检查、保存、409 保留输入、显式核对、撤销、提交待核对 | main-app/tests/integration/notification-runtime-management/management.spec.js |
| 页面边界 | 同组件换模板重新读取；非法 JSON、不变配置和提交待核对不能写；页签拒绝离开不修改目标状态 | notification-template-management.test.js、tab-navigation-runtime.test.js、management.spec.js |
| 原配置工作台 | 替换凭据不回显、留空保留、勾选撤销后无存储覆盖并返回默认 token | management.spec.js |

以上是组件行为合同；完整应用的七个稳定 Case ID、成功标准见 [contract.md](contract.md)，各阶段前置条件、动作和证据见 [harness.playbook.md](harness.playbook.md)。其中 SHELL 验证正常与检查无副作用，SECRET 验证敏感信息边界，RBAC 验证权限分支，UPGRADE/CLUSTER/DEFAULT 验证持久化、跨实例状态及部署版本迁移。执行结果见 `tmp/testing-evidence/notification-runtime-management/checkpoint.md`，不得仅凭资产存在判为已通过。

完成 Maven 通知/配置测试后，在仓库根执行 `python3 testing/integration/regression/notification/runtime-management/run-channel-scenario.py`。runner 从 Surefire 的实际 classpath 装配已编译模块，移除测试专用 SPI，使用正式供应商类型与管理校验，不启动外部发送。

浏览器 fixture 类为 `NotificationRuntimeManagementBrowserFixture`，仅监听 `127.0.0.1:43262`，前端 Playwright 配置使用 `43261`。fixture 使用 `OUROBOROS_BEHAVIOR_MYSQL_URL/USER/PASSWORD` 指定的一次性数据库；不设时使用 H2。生产历史数据库不可用于该 fixture。Java 类须使用通知 runtime 的测试 classpath 启动，并保持进程存活，再在 main-app 执行：

```sh
./node_modules/.bin/playwright test --config=playwright.notification-runtime.config.js
```

Java 真实集成测试使用相同 MySQL 环境变量；共享 Redis 测试另传 `-Dnotification.redis.port=<一次性Redis端口>`。Redis 区域和数据库表使用随机测试前缀。完整应用层另用正式 shell、真实用户名密码登录和双 JVM 验证；实际供应商外发送达由用户排除，不调用发送接口。
