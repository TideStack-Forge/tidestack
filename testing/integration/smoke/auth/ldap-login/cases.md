# LDAP Login Integration Cases

| Case ID | Objective | Preconditions / Input | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- |
| LDAP-SVC-001 | 验证 LDAP 服务可达 | `services/openldap` 已拉起 | harness | LDAP 依赖状态被记录，服务不可用时阻断后续登录结论 | fixture harness report, service logs |
| LDAP-PROVIDER-001 | 验证 runtime 侧 provider readiness | `develop` 或 `mothership` 已配置 LDAP provider | harness | provider 初始化无阻断错误，关键探针/日志通过 | backend logs, fixture harness report |
| LDAP-UI-001 | 验证登录入口可打开 | 前端壳入口地址已注入 | harness | 登录页可打开且无明显代理/壳加载错误 | browser screenshot, console/network log |
| LDAP-SUCCESS-001 | 验证已映射 LDAP 用户登录成功 | `identities/ldap-user` 凭据已注入，provider 正常 | e2e | 完成登录并进入预期首页或壳入口 | browser trace, response log |
| LDAP-BIND-001 | 验证首次登录绑定路径 | `identities/first-login-binding-user` 未预映射 | harness | 首次登录进入绑定路径，而不是被误判为登录失败 | browser trace, backend logs |
| LDAP-PERM-001 | 验证权限不足路径单独分类 | `identities/permission-boundary-user` 已准备 | harness | 夹具预期无权限时明确记录拒绝；应获准身份遭拒绝时按配置或授权回归继续诊断，不一概排除代码问题 | report summary, backend logs |
| LDAP-APP-001 | 验证运行基座登录入口 | `runtime-shell` 与 `mothership` 可访问 | harness | 运行态登录链路能复用同一 LDAP 接入配置并被独立观察 | browser evidence, runtime logs |
