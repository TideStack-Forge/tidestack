# 执行流程

1. 确认 worktree、分支和未提交变更；记录 Java/Maven/Node/浏览器版本与构建日志。
2. 使用仅绑定回环地址的独立 MySQL、Redis、SMTP sink；生成测试签名 JWK、验证 HMAC 密钥和账号密码，存放 tmp 下受限 fixture，禁止进入交付证据或版本库。
3. 按当前源码 reactor 打包 Mothership 与依赖并生成 runtime.classpath；主应用使用 npm run build。按正式模型生命周期启动专用实例，检查迁移和公开标识就绪日志。
4. 运行场景自动脚本，顺序验证浏览器协议/资料、业务委托、SMTP 联系方式、配置隔离和动态客户端注册；查看每条断言及失败截图。先修产品根因，再重新运行。不以删除断言使测试通过。`system-batch.json` 记录各脚本退出状态，JSONL 记录具体断言。
5. 验证配置迁移、重启、共享缓存和故障恢复；根据实际结果填写 case 状态。使用同一 schema/账号服务检查管理员和本人路径。
6. 汇总模块回归及覆盖率；检查证据脱敏。生成 tmp/testing-evidence/sso-dynamic-config-oidc/verification-report.md，给出可上线结论及实际限制。
7. 只清理本场景创建的进程/容器；保留长期场景、脚本及临时证据，不改变用户原开发实例。

组件测试的 H2/JUnit 与浏览器/MySQL/Redis 验证互补，报告必须区分两者。任何集群声明必须指明使用共享 HTTP 会话还是会话亲和，并提供跨节点请求证据。

## 自动执行入口

以下命令从仓库根运行。所有依赖端口只绑定回环地址；数据库必须是独立的 `ouroboros_sso` 验收库。复用已存在的 fixture 时保留其签名密钥，避免重跑变成另一个发行者。

```bash
export SSO_ISOLATED_FIXTURE=1
export SSO_FIXTURE_ROOT="$PWD/tmp/sso-local"
export SSO_BASE_URL=http://127.0.0.1:18372
export SSO_ADMIN_PASSWORD='<专用验收账号密码>'
export SSO_DB_PASSWORD='<专用验收库密码>'
node testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/init-local.cjs
```

`init-local.cjs` 创建原业务模型与通知模板 fixture、签名私钥和联系方式验证密钥，以及只限测试的 local.yml；拒绝覆盖已有 local.yml。私钥、原始邮件和 retained grant 只保存在 fixture/secrets 下，权限 0600，不进入证据目录或 Git。

依赖服务使用 MySQL 8.0.36、Redis 7；示例映射分别为 13371→3306 和 16371→6379，执行时先确认端口无占用并记录实际映射。MySQL 设置库名 ouroboros_sso、用户 sso_fixture 和上述专用数据库密码。不得指向原开发实例。容器身份、探针结果和服务版本落入证据。

后端打包与前端构建：

```bash
mvn -B -ntp -pl :ouroboros-mothership -am \
 '-Dtest=Dcr*Test,CodeContextServiceTest,EditorLanguageSessionsTest,BusinessModelDelegationProjectionTest,Oidc*,*Authorization*,*AccessConstraint*,*ConfigurationManagement*,*RuntimeConfigurationService*,*ExternalAuthProvider*,*ConfigPropertiesProviderSpecSource*,*Profile*,*Contact*,*AccountIdentity*,DataAuthorizationEnforcementTest,SchemaCacheSerializationTest' \
 -Dsurefire.failIfNoSpecifiedTests=false -DincludeScope=runtime \
 -Dmdep.outputFile=target/runtime.classpath package dependency:build-classpath
npm --prefix frontends/web/main-app run build
```

在独立进程启动以下入口，记录 PID；不要用全局进程名批量终止：

```bash
bash testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/start-backend.sh
node testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/gateway.cjs
node testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/smtp-sink.cjs
```

预检和全流程使用正式入口；E2E manifest 位于 `testing/e2e/regression/auth/sso-dynamic-config-oidc/e2e.yaml`。脚本使用真实产品页面和 API，API 数据在落盘前脱敏，失败保留截图。

```bash
node testing/integration/feature/auth/sso-dynamic-config-oidc/scripts/run.cjs --preflight
node testing/integration/feature/auth/sso-dynamic-config-oidc/scripts/run.cjs
```

## 重启、缓存故障与维护迁移

1. 用 `SSO_PHASE=prepare-resilience` 执行 browser-acceptance.cjs，在受限 secrets 目录保留当前授权；用 resilience-probe.cjs before-restart 核验。
2. 只重启专用 Java PID，等待 Context Refresh Finished，执行 resilience-probe.cjs after-restart。公开 subject、UserInfo 和刷新须保持一致。
3. 停止专用 Redis，执行 resilience-probe.cjs cache-down。任何成功 UserInfo 都失败；超时同样不能视为通过。测试配置把连接与命令超时设为 2s。
4. 启动同一 Redis，执行 resilience-probe.cjs after-cache-recovery；记录客户端重连期间的失败，并要求 15 秒内用同一授权恢复 UserInfo，随后验证刷新成功。缓存存储未丢失时原授权应继续有效。若主动清空缓存，应要求重新授权，不能恢复未知授权。
5. 存量迁移仅操作本场景 fixture。通过原用户 API 建立正常与逻辑删除样本，模拟旧列 nullable/空值，保留已签发账号的公开标识备份。维护启动执行分批回填；结构未收紧时 token 入口须 503。再次原模型迁移后检查非空和全表唯一约束，验证已生成标识不变；重复运行补齐数量为 0。BooleanValue 会把 SQL null 投影为 false，必须额外检查物理 NULL，而不能仅断言 Java getter。

既有授权与兑换流程按单节点/会话亲和验证，DCR 另有下节的双节点注册验证。无会话亲和的多节点授权上线仍须配置平台已有分布式锁实现、共享缓存和共享 HTTP 会话，并执行跨节点兑换；DCR 双节点结果不能替代该项验证。

## DCR 多节点与事务屏障验证

第二个后端复用同一隔离数据库、缓存、issuer 和签名材料。`start-backend.sh --server.port=18373` 通过原 Spring 参数选择端口；浏览器和 issuer 仍使用网关地址。运行以下命令前，确认容器是本场景自有数据库。注册请求交替发往两个节点，验证同一凭证和最后一个配额各仅一个成功；浏览器授权保留原单节点/会话亲和方式，不据此声称无亲和的集群授权也已验收。

```bash
export SSO_SECONDARY_URL=http://127.0.0.1:18373
node testing/integration/feature/auth/sso-dynamic-config-oidc/scripts/dcr-acceptance.cjs
export SSO_MYSQL_CONTAINER='<本场景数据库容器名>'
node testing/integration/feature/auth/sso-dynamic-config-oidc/scripts/dcr-concurrency.cjs
```

`dcr-concurrency.cjs` 先通过原配置行锁证明关闭、切换模式和收紧范围先提交时在途请求拒绝，再以临时 `sso_dcr_test_barrier` 触发器让注册停在真实 UPDATE，观察管理员关闭等待同一策略锁；释放后注册提交、关闭完成、后续注册拒绝。触发器操作使用该隔离容器的 root 环境凭据，只用于测试故障注入，产品代码不增加 JDBC 访问。脚本 finally 删除触发器、测试客户端并恢复关闭状态；异常强制终止后必须人工确认触发器已移除。

证据包括 `dcr-requests.jsonl`、`dcr-policy-races.jsonl`、`dcr-settings-credential.png`、`dcr-issued-credential-masked.png`、`dcr-registered-applications.png` 和两类客户端确认截图。旧 fixture 可能保留昵称/正式名称调整前的资料规则；预检读取本人资料失败时先检查规则，只能在独立测试库经正式配置 API 恢复当前默认规则，不改生产校验以兼容过期测试数据。

若设置 `SSO_MYSQL_CONTAINER`，统一 `run.cjs` 在 DCR 验收后纳入上述事务屏障；设置 `SSO_REDIS_CONTAINER` 时继续纳入 `dcr-resilience.cjs`。后者只停止/恢复本场景自有 Redis，验证真实缓存不可用时注册 503、不写客户端、恢复后已消费凭证仍拒绝、未认领凭证可继续使用。完整 E2E manifest 要求两个容器变量均明确设置；不设置时日常 run.cjs 只运行不控制容器生命周期的用例，不可据此宣称故障演练已通过。
