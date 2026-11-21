# 外部认证与账号绑定

## 范围与正式入口

本主题维护平台作为外部身份源消费者时的流程：LDAP/AD、CAS、OAuth2/OIDC 验证外部身份，绑定到原平台账号，再创建平台会话。平台作为 OAuth2 授权服务器/OIDC IdP 的机制由[协议组件](../ability/ouroboros-oidc-server/DESIGN.md)维护，两种角色不得混为同一套客户端配置。

| 正式入口 | 职责 |
| --- | --- |
| [ExternalAuthProvider](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/externalauth/ExternalAuthProvider.java)、[RedirectAuthProvider](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/externalauth/RedirectAuthProvider.java) | 验证外部凭据/重定向协议并产出 ExternalIdentity |
| [ExternalAuthProviderFactory](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/externalauth/ExternalAuthProviderFactory.java) | 从权威配置构建 provider 目录，按 revision 发布一致快照 |
| [ExternalAuthService](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/externalauth/ExternalAuthService.java) | 校验绑定与账号，签发短期绑定凭证或进入原会话管理 |
| [控制器](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/controller/) | provider 列表、LDAP、CAS/OAuth 回调、绑定/解绑和一次性登录码兑换 |
| [配置适配组件](../ability/ouroboros-external-auth-configuration/DESIGN.md) | 将统一配置接入安全模块自有读取契约 |

## 配置、装配与失败

动态键 `security.auth.externalAuth`是平台的外部认证设置；Spring `ouroboros.security.external-auth`提供部署默认值。装配时默认实现与统一配置适配互斥，运行时只读一个权威来源。损坏/不可读配置明确失败，不能退回静态默认值。

工厂在首次使用及 revision 改变时构建目录，构建前后检查同一 revision，最多三次重试后明确报错。发布整份目录而非逐项替换，回调与发起时的 revision 绑定；配置改变后旧回调不能继续完成认证。密钥引用解析失败只使相应 provider 不可用，不能把空密钥作为合法配置。

`SecretResolver` 契约和 `CompositeSecretResolver` 组合规则由 security core 提供；组合器负责按 scheme 分派和 JSON 字段投影，多字段投影时只读取一次基础密钥，不读取环境或文件。env/config/file 渠道实现与 `ExternalAuthAutoConfiguration` 的 Bean 工厂位于 security runtime。审批对接通过 core 使用组合器；基座与开发平台通过 `ouroboros-ability-all-runtime` 引入 security runtime 完成装配。配置了密钥引用时，解析失败不能回退到内联值；仅在未配置引用时使用内联值。

自定义协议使用既有 ExternalAuthProviderBuilder，企业连接沿 EnterpriseAuthProfileResolver 派生连接级 profile，不新建租户认证渠道表或静态同名回退。当前受控平台协议实现与未来共享宿主的区别见[已采纳待实现适配器设计](oauth-platform-adapters-design.md)。

## 请求、重定向与一次性凭据

LDAP 在服务端验证目录身份；search 与 AD direct-bind 的精确配置以[正式配置类型](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/externalauth/config/)为准。目录连接/读取故障和账号密码不正确分别处理，不能把内部故障当作“未找到用户”。健康探测用于判断 provider 可用性，不替代真实登录与绑定验收。

CAS/OAuth2/OIDC 回调沿原控制器与 provider 的 state、nonce、PKCE、签名/发行者及目标校验执行；这些安全参数的适用条件由各 provider 配置和协议决定，不能宣称所有协议都使用同一参数。OAuth state 在原缓存中一次消费，默认五分钟；returnUrl 由服务端规范化，非法值回到 `/`。公开回调地址依赖受控 publicBaseUrl，不信任请求头任意拼接。

成功后的前端回跳使用短期 loginCode，默认六十秒，兑换时原子 getAndEvict；平台 Token 不放入回跳 URL。登录码、绑定凭证、state、验证码、用户 Token 和密钥均为敏感值，不应进入日志/截图/审计正文。

## 账号绑定与身份归属

外部身份由 channel/provider 与 externalId 定位既有 UserSso 绑定。已有绑定仍需确认原账号存在且启用，再通过原 UserSessionManager 创建平台会话；无绑定返回 bindingRequired，不自动按邮箱或同名登录名合并账号。

绑定凭证保存服务端已验证的 ExternalIdentity，默认五分钟、最多五次绑定验证；绑定成功后消费。绑定/解绑和管理权限由原 AuthBindController/AuthBindAdminController、UserSsoService 执行，不能从浏览器接收任意“已验证外部身份”。现有绑定重试计数使用进程内同步，集群原子性不能据此推定已满足。

企业登录的成员标识与用户 OAuth 标识可能不同，必须经正式企业身份映射契约关联。缺少企业成员标识或租户不符时不得退回登录名。供应商参数与身份作用域见[钉钉/企业微信协议参考](../docs/references/dingtalk-wecom-auth-bridge-contract.md)，来源核对日期在参考中维护；真实租户启用仍须验证对应权限和回调。

## 错误、缓存与恢复

协议错误由[AuthError](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/externalauth/AuthError.java)及控制器的 WebApiResult/重定向错误出口表达，不能只检查 HTTP 200 判断登录成功。内部 RBAC/配置/缓存故障继续保持故障语义，不能签发匿名或完整权限身份兜底。

state/loginCode/bindingToken 的缓存生命周期与平台会话分开。缓存清空使待完成登录失效，应重新发起；不能重用已消费 code，也不能用另一 provider 恢复旧 state。持久绑定不随短期缓存清除而改写。全局 RBAC 缓存与前端初始化规则回到[安全总设计](DESIGN.md#45-rbac-读取缓存与前端初始化)维护。

## 验证与限制

| 需要证明的行为 | 入口 |
| --- | --- |
| 工厂热更新、配置一致性、秘密解析失败 | [运行时外部认证测试](ouroboros-web-security/ouroboros-web-security-runtime/src/test/java/com/ouroboros/security/externalauth/) |
| LDAP 登录、首次绑定、权限边界 | [LDAP 场景契约](../testing/integration/smoke/auth/ldap-login/contract.md) |
| 动态配置与统一协议的系统协作 | [SSO 场景契约](../testing/integration/feature/auth/sso-dynamic-config-oidc/contract.md) |

当前源码不证明真实上游配置、生产 HTTPS、跨节点一次性消费或绑定计数已验收。共享 OAuth 宿主/适配器尚未实现，集群验证和工程差距分别由[SEC-001](../docs/tech-debt-tracker.md#sec-001-oauth-共享协议适配器待实现)、[SEC-002](../docs/tech-debt-tracker.md#sec-002-外部绑定的多节点并发边界)跟踪。
