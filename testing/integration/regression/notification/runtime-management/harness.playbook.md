# 通知运行时管理复验

按 [contract.md](contract.md) 和 [cases.md](cases.md) 判定结果。运行前检查 checkout、HEAD、未提交差异、Java 21、Node、Docker、端口和资源归属；全部资源须独立且仅绑定回环地址。下面使用的账号、密码、Token 和渠道凭据只放在忽略的 `tmp/` 中，不写进命令日志或提交。

## 组件与浏览器组合

先完成通知及统一配置模块的 Maven 测试，保留 Surefire 报告和测试 classpath。运行正式渠道组合：

```sh
python3 testing/integration/regression/notification/runtime-management/run-channel-scenario.py
```

`NotificationRuntimeManagementBrowserFixture` 使用通知 runtime 的测试 classpath 启动，参数为 `43262`。传入 `OUROBOROS_BEHAVIOR_MYSQL_URL`、`OUROBOROS_BEHAVIOR_MYSQL_USER` 和 `OUROBOROS_BEHAVIOR_MYSQL_PASSWORD` 指向一次性 MySQL；不传时使用隔离 H2。夹具创建随机测试表，不得连接生产数据源。等待日志出现 `browser fixture ready` 后，在 `frontends/web/main-app` 执行：

```sh
./node_modules/.bin/playwright test --config=playwright.notification-runtime.config.js
```

该层使用生产 AMIS schema、真实 Controller、用例和事务；认证身份由夹具提供。五个场景应全部通过，不能用该结果替代完整应用认证验收。

## 完整应用准备

1. 创建专用 MySQL 8.3 和 Redis 7，记录容器名、端口、PID 及任务归属。数据库从空库开始；Redis 只供本场景使用。账号文件权限为 `0600`。
2. 从当前 checkout 构建正式应用及依赖 classpath：

   ```sh
   mvn -B -ntp -pl :ouroboros-mothership -am -DskipTests \
     -DincludeScope=runtime -Dmdep.outputFile=target/runtime.classpath \
     package dependency:build-classpath
   ```

3. 在 `frontends/web/main-app` 执行 `npm run build`。启动主应用静态网关，API 转发至节点 A，不拦截或伪造产品响应。可复用 `testing/integration/feature/auth/sso-dynamic-config-oidc/fixtures/gateway.cjs` 的静态文件/API 代理；其 `SSO_ISOLATED_FIXTURE`、`SSO_ADMIN_PASSWORD`、`SSO_FRONTEND_PORT`、`SSO_BACKEND_PORT`、`SSO_FIXTURE_ROOT` 和 `SSO_EVIDENCE_DIR` 只作为代理的隔离启动参数，本场景不运行 SSO 或外发逻辑。
4. 在临时 app home 的 `notification-template/runtime-acceptance.notification.json` 写入下面的 V1 默认。文件扩展名必须是 `.notification.json`。

   ```json
   {
     "name": "runtime-acceptance",
     "title": "运行时验收默认 V1",
     "isEnabled": false,
     "payloadSchema": {
       "type": "object",
       "properties": {"name": {"type": "string"}},
       "required": ["name"]
     },
     "channel": {
       "email": {
         "schema": {"titleTemplate": "验收 ${name}", "contentTemplate": "默认 V1 ${name}"},
         "options": {}
       }
     }
   }
   ```

5. 使用正式 `com.ouroboros.mothership.OuroborosApplication` 和 `target/runtime.classpath` 启动节点 A。附加原 `application-base.yml` 和临时配置：独立 MySQL、`spring.cache.type=redis`、专用 Redis、`ouroboros.metadata.path` 指向临时 app home、文件存储目录指向临时目录。自动创建管理员的密码和缓存 token secret 使用本场景生成值。等待正式模型刷新完成及 `/api/sys/web-app-info` 成功，不仅检查端口可连接。
6. 给断言脚本准备 JSON 账号文件，包含 `adminPassword` 和独立生成的 `cacheSecret`。后者只作为可替换的测试凭据值，不填真实渠道密钥。设置：

   ```sh
   export NOTIFICATION_ISOLATED_FIXTURE=1
   export NOTIFICATION_ACCEPTANCE_BASE_URL=http://127.0.0.1:43271
   export NOTIFICATION_ACCEPTANCE_SECOND_URL=http://127.0.0.1:43272
   export NOTIFICATION_ACCEPTANCE_SECRETS="$PWD/tmp/notification-runtime-full-app/secrets.json"
   export NOTIFICATION_ACCEPTANCE_EVIDENCE="$PWD/tmp/testing-evidence/notification-runtime-management/full-app"
   ```

   账号文件、数据库密码和进程环境不得打印到证据。所有阶段从仓库根调用 `node frontends/web/main-app/tests/integration/notification-runtime-management/full-app-acceptance.cjs <phase>`。

## 分阶段验证

| 阶段 | 前置与动作 | 必须观察的结果 |
| --- | --- | --- |
| `initial` | A 使用 V1 默认；静态网关就绪 | 真实管理员登录、菜单、查看、编辑、检查和保存；独立角色权限、直达页拒绝及凭据脱敏通过。脚本保留本场景版本状态，不发送通知 |
| 旧表样本 | 停 A；备份 `t_notification_template`、`t_sys_configuration_value`；仅在本专用库移除本次新增的模板列及 name_key 唯一索引、配置 generation 列，插入 name=`historical-only`、停用且 JSON 字段为 `{}` 的旧模板；清空本专用 Redis；重启 A | 正式模型生命周期补齐列与唯一约束，名称键及 revision 回填；保存迁移前后 DDL 和行投影。该操作模拟旧表结构，不等于运行旧二进制 |
| `after-restart` | A 升级完成；启动同版独立 JVM B，共享同一数据库及 Redis | 已保存覆盖保留、历史模板只读及写入 HTTP 409；B 预热后读取 A 保存/撤销的新状态。两节点分别真实登录 |
| `upgrade` | 停 A、B；将模板 title 改为 `运行时验收默认 V2`，contentTemplate 同步为 V2；启动 A | 覆盖仍保留，baseChanged=true；V1 默认版本写入 HTTP 409；撤销后有效模板为 V2，overrideRevision=NONE |
| `follow-default` | 停 A；将模板改为 V3；在 app home 的 `configuration/notification-email-host.config.json` 定义 host 默认为 `deployment-v3.invalid`；启动 A | 模板和 host 均读取 V3 部署默认，configValue 不存在；数据库中无该模板及 host/授权码覆盖 |

host 文件沿原 `FileConfigurationMetadataProvider`，内容为 key=`notification.email.host`、label=`邮件通知主机`、groupPath=`notification.email`、valueType=`string`、modifiable=true、nullable=true、restoreDefaultRemovesOverride=true、defaultValue=`deployment-v3.invalid`。各阶段必须等前一阶段退出码为 0，再修改默认或重启。

HTTP 冲突必须同时断言状态 409 和业务响应，不能把 HTTP 200 的错误包计为通过。正式应用由原 `WebApiResultHttpStatusAdvice` 统一映射；组件夹具中的手工装配不能证明正式启动已注册。

## 证据与清理

保留构建及启动日志、固定源码基线、各阶段退出码、迁移前后结构、脱敏行投影、`assertions.jsonl` 和截图。断言按稳定 Case ID 去重；失败尝试的中途通过不能累加为额外成功用例。完整应用七项、组件浏览器五项分别报告。

完成后核对 PID 的命令及工作区，再停止本任务 JVM、浏览器夹具与代理；按任务标签核对后删除专用容器。保留证据和私有账号文件，勿操作其他任务资源。真实生产迁移和实际供应商外发送达均不在本场景中执行。
