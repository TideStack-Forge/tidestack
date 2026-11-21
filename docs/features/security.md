# 账号、认证与访问控制

平台为管理员、应用与接入方提供统一账号、登录和业务访问边界。以下描述当前源码能力；环境启用、验收和发布需另取运行证据。

| 功能 | 当前行为与边界 |
| --- | --- |
| 账号与业务身份 | 原 RBAC 账号、角色、权限和 UserSubject 关联；账号昵称与主体正式名称分开，显示名称自动回退 |
| 账号资料 | 本人/管理员共用字段规则和版本 CAS；账号联系方式独立于员工档案，纯外部账号自助只读 |
| 联系方式 | NONE 更新后为未验证；REQUIRED 经挑战确认后生效，pending 不外发；真实短信依赖可用渠道和密钥 |
| 平台登录与会话 | 双令牌、刷新与撤销；受保护 API 由当前账号和有效权限决定，匿名 API 按既有匿名契约处理 |
| 外部身份登录 | LDAP/AD、CAS、OAuth2/OIDC、企业连接及首次绑定，动态配置不另建账号库；管理员在原用户详情查看和解绑第三方登录 |
| 集成管理 | 上方外部连接、下方应用接入；安装的模块贡献页面，分区独立授权/加载；外部登录配置、授权服务器与 DCR 复用原配置/CAS 和客户端管理范围 |
| OAuth2/OIDC 提供方 | 独立能力组件复用原登录/会话，授权码+PKCE、确认、UserInfo、刷新/退出；首版公共客户端不发刷新令牌 |
| 动态客户端注册 | 集成管理下方“应用接入”的 DCR 页提供服务器级开关和 OPEN / INITIAL_ACCESS_TOKEN 模式；一次性凭证、配额和限流；注册只开放身份能力 |
| 可选业务委托 | 客户端显式开放可委托权限才可申请；角色、当前客户端、批准快照与 Token scope 求交，身份令牌不获得业务 API 权限 |
| 字段与行访问 | 原数据插件、Action、脚本与逻辑流消费明确 Permit/Deny；NONE 不解释为无约束 |

## 启用条件与限制

平台安全运行时随应用装配；外部身份源需要有效配置/凭据和真实绑定。OIDC 还需模块、部署启用开关、稳定 issuer、持久 JWK 以及公开用户标识迁移门槛。运行时策略和客户端开关不能替代部署身份，详细条件见各设计。

源码不证明已在生产启用。首版协议覆盖、单节点/集群、真实上游和短信边界以[OIDC 设计](../../ability/ouroboros-oidc-server/DESIGN.md#7-首版范围)和场景证据为准；共享 OAuth 适配器仍是待实现设计。

## 源码、测试与正文

- [安全模块与归属测试](../../security/ouroboros-web-security/)：账号、权限、过滤器和会话。
- [外部认证配置组件](../../ability/ouroboros-external-auth-configuration/)：动态配置适配。
- [集成管理宿主](../../ability/ouroboros-integration-management/)：展示贡献、上下分区与迁移说明。
- [OIDC 组件](../../ability/ouroboros-oidc-server/)：SAS 扩展和共用客户端管理。
- [前端认证页面](../../frontends/web/main-app/src/features/auth/)、[账号初始化](../../frontends/web/main-app/src/store/modules/user.js)：登录、确认与状态读取。
- [统一认证场景](../../testing/integration/feature/auth/sso-dynamic-config-oidc/contract.md)：隔离环境、用例与证据要求。

机制总入口是[安全设计](../../security/DESIGN.md)，公共契约源入口见[接口索引](../api/README.md)。已确认的多权限接口行为见[需求](../requirements/security/multi-authority-api.md)。
