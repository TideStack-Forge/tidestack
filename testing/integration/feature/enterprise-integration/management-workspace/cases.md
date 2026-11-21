# 验收用例

| ID | 分支 | 前提与可观察结果 | 实现与证据 |
|---|---|---|---|
| EIM-USER-001 | normal / state | 用户有绑定；显示渠道、外部标识、展示名和时间，确认解绑仅删除目标且刷新 | admin-bindings.spec.js；AuthBindAdminControllerTest |
| EIM-USER-002 | invalid / error | 无记录或删除失败；显示失败，不能报告成功或移除可见记录 | 同上 |
| EIM-USER-003 | empty / boundary | 空列表显示说明且无解绑操作；取消确认无写入 | 同上 |
| EIM-CONFIG-001 | permission | 登录配置独立可用，授权服务器设置独立可用，不请求应用列表 | workspace.spec.js；IntegrationWorkspaceTest |
| EIM-CONFIG-002 | state / recovery | 保存携带原 revision，版本冲突保留当前输入 | workspace.spec.js |
| EIM-CONFIG-003 | normal / error | 受控局部修改保全其他协议及未知字段；密码留空保留，引用替换退出旧明文；通用替换被拒绝 | ExternalAuthConfigurationManagementPolicyTest |
| EIM-CONFIG-004 | permission | 读取按组投影，已有密码及默认值不外泄；无权不读写 | ExternalAuthConfigurationManagementPolicyTest；ExternalAuthManagementControllerTest |
| EIM-ASSEMBLY-001 | dependency | 宿主不带 OIDC 和供应商 JAR 也能启动；重复贡献失败，已安装贡献可显示 | IntegrationWorkspaceTest |
| EIM-MIGRATION-001 | transition / dependency | 权限改名保留字段/范围/Deny，已迁移不重复写，存储失败终止 | IntegrationAuthorityMigrationTest |
| EIM-DIRECTORY-001 | normal / state | 选择企业连接读取目录，关联已有成员只提交组织关联，不建立第三方登录绑定 | directory.spec.js；DirectoryBindingCommandsTest |
| EIM-DIRECTORY-002 | permission | 仅查看权限可读取目录，不能出现关联和主来源修改操作；后端仍独立校验权限 | directory.spec.js |
| EIM-DIRECTORY-003 | error | 读取失败显示错误，无可绑定的伪成功结果 | directory.spec.js；三家 DirectorySourceTest |
| EIM-DIRECTORY-004 | state / recovery | 同步明确选择主部门，预览失败保留输入，不擅自采用第一个部门 | directory.spec.js；DirectorySyncCommandsTest |
| EIM-DIRECTORY-005 | invalid / boundary | 不完整的预览不能应用 | 同上 |
| EIM-DIRECTORY-006 | state / concurrency | 解绑确认使用当前关系版本，取消不写入 | directory.spec.js；DirectoryBindingCommandsTest |
| EIM-ENTERPRISE-001 | normal / state | 换密钥使用连接及凭据双版本，保存后清空密码输入，不请求接入应用列表 | enterprise-connection.spec.js；EnterpriseConnectionManagementPolicyTest |
| EIM-ENTERPRISE-002 | error / recovery | 未替换凭据不提交密钥；CAS 冲突保留当前输入 | 同上 |
| EIM-ENTERPRISE-003 | state / permission | 停用须确认影响，未确认无写入 | 同上 |
| EIM-ENTERPRISE-004 | normal / isolation | 调用预算使用独立版本保存，不提交连接草稿，后续连接保存仍携带原双版本 | enterprise-connection.spec.js；EnterpriseConnectionManagementPolicyTest |
| EIM-ENTERPRISE-005 | state / recovery | 孤立凭据清除可取消；确认使用双版本，成功后重新读取候选及版本；后端拒绝有引用的密文 | 同上 |
| EIM-ENTERPRISE-006 | permission / isolation / error | 独立权限检查已保存连接；不发送草稿/密钥，失败保留输入；服务端不改缓存/启停，轮换时不报当前配置成功 | enterprise-connection.spec.js；EnterpriseConnectionVerificationControllerTest；三家 ApplicationTokensTest |
| EIM-RPC-001 | permission / navigation | 企业 API 说明不发起 RPC；按钮跳转原管理路由，说明页权限不授予原管理或调用权限 | workspace.spec.js；EnterpriseRpcExecutionTest；SystemSettingsMenuIntegrationTest |
| EIM-HTTP-001 | permission | 真实登录/RBAC 下匿名和普通配置管理员不能读受保护连接、登录配置或他人绑定；管理员可查绑定 | http-acceptance.cjs |
| EIM-HTTP-002 | normal / isolation | 首次保存停用连接及托管密钥；只读返回 no-store 且不含原密钥，Schema 无修改和诊断操作 | 同上 |
| EIM-HTTP-003 | permission / state | 查看、配置和换密钥互相隔离；拒绝不推进双配置版本，通用配置入口不能代写 | 同上 |
| EIM-HTTP-004 | concurrency / recovery | 同版本并发换密钥恰一成功；返回版本与当前一致，越权修改应用身份后两行版本保全 | 同上；ConfigurationPersistenceIntegrationTest 的加锁完整版本回归 |
| EIM-HTTP-005 | concurrency / isolation | 预算独立保存/CAS，不修改连接或凭据版本；只读者不能保存 | http-acceptance.cjs |
| EIM-HTTP-006 | permission / error | 配置者不能诊断；诊断者检查停用连接在外呼前失败，双版本不变 | 同上 |
| EIM-HTTP-007 | permission / boundary | 企业 API 说明不授予 RPC/连接管理；应用范围管理员只见范围内应用，不能读连接、登录配置和 DCR | 同上 |
| EIM-HTTP-008 | permission / recovery | 独立登录配置角色可修改 LDAP、拒绝旧版本并恢复；独立授权服务器设置角色可读设置，均不扩大其他权限 | 同上 |
| EIM-LIVE-001 | normal / dependency | 正式前端登录后加载上下分区及三家插件，目录/通知/工作台页沿原 Schema 提供并通过真实 GET 读取 | browser-acceptance.cjs；EnterpriseManagementSchemaAssemblyTest |
| EIM-LIVE-002 | state / recovery | 停用测试连接在同一页面连续两次换密钥均成功，使用最新双版本，保存后密码输入消失 | 同上 |
| EIM-LIVE-003 | permission / boundary | 真实只读角色登录后可看连接；无保存、换密钥、诊断操作，不请求应用接入列表 | 同上 |
| EIM-LIVE-004 | permission / state | 仅登录配置角色在正式页面编辑停用 LDAP 身份源并保存；不加载应用管理或企业连接，保全其他身份源 | configuration-acceptance.cjs |
| EIM-LIVE-005 | permission / recovery | 仅授权服务器设置角色可在下区修改有效期并连续保存，保持原启停；不请求应用列表或外部连接 | 同上 |
| EIM-LIVE-006 | normal / recovery | 原安全管理角色在下区 DCR 页连续保存数量限制，注册保持关闭，更新后使用最新版本，不请求授权服务器设置或外部连接 | 同上 |
| EIM-LIVE-007 | permission / boundary | 两个已存在接入应用中，范围管理员只见获准应用，能改授权字段；无创建入口，范围外读取为 404，越权改安全字段为 403 且版本/数据不变，保存不改另一应用 | 同上 |

前端组件实现位于 `frontends/web/main-app/tests/integration/enterprise-integration/`，真实 HTTP 及正式前后端脚本位于本场景 `scripts/`。每个用例的运行日志归 `tmp/testing-evidence/enterprise-*.log`；状态及场景结论归指定证据目录的 `enterprise-management-http.jsonl`、`enterprise-management-browser.jsonl` 或 `enterprise-configuration-browser.jsonl`；组件 Playwright 的失败跟踪位于前端包 `test-results/`。这些本地产物不纳入正式版本库。真实企业凭据及完整环境联调尚待执行。
