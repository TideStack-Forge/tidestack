# 集成管理

只组合管理页面：上方“外部连接”，下方“应用接入”。配置值、凭据、客户端和业务规则仍归各领域，通用 UI Model 不增加集成语义。

## 装配与扩展

`core` 定义 `IntegrationManagementContributor` 和 `IntegrationManagementPage`。领域模块依赖 core，用自己的 AutoConfiguration 贡献 Bean，声明页面标识、分区、标题、原 Schema 路径、排序及管理权限；沿原 META-INF 机制注册自动配置。`runtime` 不依赖 OIDC、外部认证或供应商模块，安装后重启即可显示贡献。重复标识或编辑路径导致启动失败。

宿主独立返回两个分区的 Schema，每个分区只包含当前平台身份有权使用的页面，并按需加载。页面结构不包含配置值；真实数据 API 和配置管理策略分别执行领域授权，不能用页面可见性代替授权。没有权限或未安装贡献时显示空态。

已接入 LDAP、OAuth2/OIDC、CAS、外部登录公共设置；应用列表、授权服务器设置及 DCR。三家企业连接、目录、通知及工作台管理页面由对应模块贡献。通知模板继续由原通知管理拥有，连接页只读展示相关模板。企业 RPC 模块贡献“企业开放 API”说明页，导航至原 RPC 管理，不增加第二个接口编辑器或测试执行器。

企业 API 说明页使用独立权限 `systemSetting.integrationManagement.connections.rpc.catalog`，只开放说明及工作区导航。查看接口和执行测试仍要求原 RPC 管理权限及企业 API 调用权限；说明页权限不隐含这两项授权。

## 权限和旧入口迁移

根菜单、模块及路由为 `systemSetting.integrationManagement`、`/system-setting/integration-management`。外部登录配置和公共设置分别使用 `systemSetting.integrationManagement.connections.login.configure`、`.settings`；原泛配置权限不会自动获得这些新增权限，由管理员分配。

OIDC 的三项原权限通过等价改名迁移：

| 原权限 | 新权限 |
|---|---|
| `systemSetting.authClients.security` | `systemSetting.integrationManagement.applications.security` |
| `systemSetting.authClients.manage` | `systemSetting.integrationManagement.applications.manage` |
| `systemSetting.authServer.settings` | `systemSetting.integrationManagement.applications.authServer.settings` |

旧应用路由仅显示前往新入口的链接，不保留第二个编辑器。配置 key、客户端 ID、DCR 来源/一次性凭证状态不迁移，仍由原管理服务维护。

维护发布时停止旧节点并备份权限与配置数据；在安装新元数据的维护实例上显式设置 `ouroboros.integration-management.migration.enabled=true`。OIDC 的迁移监听器沿原 RBAC 服务在同一事务中改名角色授权及可授予权限过滤器，保留其余字段/范围/Permit-Deny 参数；失败或未知旧命名空间阻止切换，不默认扩大范围。已改名记录不再写入。完成差异核对后关闭维护开关并重启全部节点，重建权限缓存/会话的运行上下文。回滚须停止全部新节点，恢复对应备份与旧构件，不依赖永久旧权限别名。真实数据库迁移和多节点切换尚待部署演练。

## 本地验证边界

场景归属见 [management-workspace](../../testing/integration/feature/enterprise-integration/management-workspace/contract.md)。组件测试验证分区授权、最小装配、局部配置/CAS 及等价权限改名，`SystemSettingsMenuIntegrationTest` 使用正式元数据检查导航与权限树。浏览器使用生产 Schema 和 Amis、网络夹具，验证操作和错误状态；不等于真实企业或完整数据库 E2E。一次性证据位于 worktree 的 `tmp/testing-evidence/enterprise-*.log`。
