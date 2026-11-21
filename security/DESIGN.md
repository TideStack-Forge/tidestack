# 安全模块设计

本文说明当前主线的安全职责、正式扩展点与请求边界。模块版本随[根 POM](../pom.xml)维护，不在设计中复制版本号。当前实现、环境启用、验收及发行分别取证；单次运行记录不作为当前部署结论。

## 1. 范围与相邻模块

安全层维护账号与业务主体、RBAC、凭据身份、认证、权限决策、访问控制参数、平台令牌和会话。业务模型、应用运行时、脚本及逻辑流消费同一权限语义；数据内核不反向依赖安全模块。

| 机制 | 唯一详细正文 |
| --- | --- |
| 有效权限、字段/行代数、委托身份与数据消费者 | [委托授权](delegated-authorization-design.md) |
| 账号资料、本人/管理员维护、联系方式验证与资料 CAS | [账号资料](user-profile-design.md) |
| 外部身份源与企业登录 | [外部认证](external-auth-design.md) |
| 外部认证的统一配置适配 | [配置适配组件](../ability/ouroboros-external-auth-configuration/DESIGN.md) |
| OAuth2 授权服务器、OIDC 身份与标准端点 | [协议组件](../ability/ouroboros-oidc-server/DESIGN.md) |

组织结构维护员工和组织；通知能力只投递验证消息；配置能力维护规则和客户端配置。它们均不拥有另一份账号或权限存储。跨模块关系见[安全与访问控制](../docs/design/cross-cutting/security-and-access.md)。

## 2. 模块与装配

[聚合 POM](ouroboros-web-security/pom.xml)维护七个子模块：

| 子模块 | 职责 |
| --- | --- |
| annotation | `@Authority`、匿名与已认证接口声明 |
| core | User/Subject、SecurityIdentity、Authority、权限求值、字段/行契约和密钥解析组合规则 |
| spring-adapter | Servlet/Spring 认证适配，保留完整 SecurityIdentity |
| rbac-manage | 账号、角色、授权关系、资料与管理接口 |
| runtime | 平台过滤链、身份聚合、Token/会话和外部认证 |
| logicflow-node | 权限字段与过滤条件的逻辑流消费 |
| test-support | 测试身份与授权支持 |

`META-INF` 的 SPI 与自动配置登记遵循[扩展机制](../docs/design/cross-cutting/extension-mechanisms.md)。新增提供者必须进入已有发现链，不能另建注册表。runtime POM 仍存在历史跨层依赖，设计摘要不将它们解释为可新增的依赖许可；精确约束见[架构](../ARCHITECTURE.md)及[技术债](../docs/tech-debt-tracker.md)。

## 3. 身份、主体与声明

### 3.1 User 与 Subject

[User](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/rbac/User.java)表示账号视图；`userIdentity`是内部登录/查找标识，角色来自当前账号授权。昵称属于账号，正式名称来自所关联业务主体，显示名称按非空白昵称、正式名称、登录名回退。账号资料的存储和编辑约束由[资料设计](user-profile-design.md)维护。

[Subject](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/rbac/Subject.java)表示员工等业务主体；绑定由[UserSubjectCenter](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/rbac/UserSubjectCenter.java)维护。按主体找账号时先调用 `UserSubjectCenter.getBySubject(type, id)`，再经[UserCenter](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/rbac/UserCenter.java)取得账号；不能调用已不存在的 User 主体 ID 方法或 `UserCenter.getBySubject`。

OIDC 的公开 `sub` 与内部 userIdentity 分开；公开标识生成、存量补齐及发行门槛见[公开标识设计](../ability/ouroboros-oidc-server/identity-profile-design.md#2-公开用户标识)。平台原 Token 的内部 subject 语义不因 OIDC 接入自动替换。

### 3.2 当前凭据身份

[SecurityIdentity](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/identity/SecurityIdentity.java)把用户、角色、claims 与不可变 `AuthorizationBoundary`、`CredentialContext`关联。共享 User/Role 不随客户端改写。[DefaultAuthentication](ouroboros-web-security/ouroboros-web-security-spring-adapter/src/main/java/com/ouroboros/security/authentication/DefaultAuthentication.java)保存原身份，Spring 往返和 scoped 上下文传播不得从 User 重新构造并丢掉委托边界。

`CurrentSecurityContextHolder`的作用域结束必须恢复原上下文。线程池不隐式继承身份；异步委托不能长期捕获旧角色，在线重验证要求见[委托消费者](delegated-authorization-design.md#6-全链路消费者改造矩阵)。

### 3.3 Claims 聚合

ClaimsProvider、用户/主体提供者沿已有 SPI 聚合。声明只说明信息来源，不能把任意 claim 当作权限授予或已验证联系方式。OIDC 通过明确 scope 映射输出资料，不导出完整员工、角色或 profileValues。必需的公开标识在签发边界再次校验，不能因可选 Provider 失败而使用登录名兜底。

## 4. 授权与请求执行

### 4.1 权限定义与角色授予

Authority 定义权限全名、关联 API 及访问控制能力；角色授予提供参数。同名权限的多个角色先 OR 合并，超级角色也经过同一求值器。定义存在、接口归属某权限与账号实际获准是不同事实。

`@Authority`单值/数组值的接口归属和验收见[多权限接口需求](../docs/requirements/security/multi-authority-api.md)。平台接口入口允许命中任一 Permit；需要请求级数据参数或接纳委托时，还必须满足同目标及约束兼容性，不能把多权限歧义变成空参数放行。

### 4.2 明确决策与访问控制

[AuthorizationEvaluator](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/authorize/AuthorizationEvaluator.java)是无状态 final class，`standard()`提供标准入口；[AuthorizationDecision](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/authorize/AuthorizationDecision.java)只返回 Permit 或 Deny。`requirePermit()`在拒绝时抛授权异常。无当前请求、无目标、无权限或不兼容的多个目标均不能返回“无限制参数”。

[AccessControlParams](ouroboros-web-security/ouroboros-web-security-core/src/main/java/com/ouroboros/security/authority/accesscontrol/AccessControlParams.java)的字段状态为 ALL/ONLY/NONE，行状态为 ALL/FILTER/NONE；真值表、默认未限制语义与兼容投影只在[详细设计](delegated-authorization-design.md#3-权限与参数的确定语义)维护。业务代码不得重新按角色计算一份权限。

### 4.3 认证与授权过滤链

[OuroborosSecurityAutoConfiguration](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/OuroborosSecurityAutoConfiguration.java)建立平台无状态过滤链，装配原 AuthenticationFilterSupplier 与 AuthorizeFilterSupplier。OIDC 启用后另有协议端点链，不能把平台链称为全系统唯一链。

受保护请求的单个 Bearer 凭据经 TokenService 验证、UserCenter 核对账号，再由已有解析器补入委托身份。明确无效的 Bearer 不能换用完整平台身份；内部存储/配置故障必须向外传播，不能吞掉后继续匿名或回退为凭证错误。

匿名契约由原 AnonymousApiProvider 与方法/路径匹配决定，表示允许未认证访问。匿名接口携带有效平台 Token 时建立当前用户及平台会话；有效委托 Token 保留原委托身份和授权边界，不转换成完整平台身份。无 Token 或凭据无效、过期、格式错误时继续匿名访问，内部存储/配置故障仍向外传播。仅要求已认证的管理入口仍拒绝 DELEGATED 凭据，业务 scope 不等于平台管理会话。

Servlet `ERROR` 分派跳过会重复读取 RBAC 的认证/授权过滤器，交给宿主错误出口；普通 REQUEST `/error`仍接受正常安全检查。未认证与未授权分别经原处理器映射为 401、403；内部故障保持故障语义。

### 4.4 数据、脚本与逻辑流

数据消费者沿 `AccessControlDataModelPlugin`、QueryDSL 和原插件链落实 Permit 的字段及行约束；查询在分页前限制，写入同时控制提交字段与写后行范围。业务动作可信作用域仍保留原身份边界。三种逻辑流节点及旧方法投影采用同一明确状态，NONE 不得序列化为空过滤条件而放行；详见[执行契约](delegated-authorization-design.md#7-原数据插件的执行契约)。

### 4.5 RBAC 读取、缓存与前端初始化

账号、角色、用户角色及角色权限服务使用原 Spring `@Cacheable(sync = true)`。成功空列表可以缓存；数据库读取失败必须保留 cause 抛出，不能返回空集合/Optional 冒充不存在。失败 miss 不发布缓存条目，恢复后由原入口重读；不拿旧授权结果兜底。

宿主 `user/init`在用户、菜单、权限三项读取全部成功后提交登录状态。500、超时和网络错误保留可再次验证的 Token，并显示可重试失败；明确凭据无效或主动退出才清理会话。重试不重复一次性登录/绑定/兑换操作。缓存恢复行为由[RbacCacheRecoveryTest](ouroboros-web-security/ouroboros-web-security-rbac-manage/src/test/java/com/ouroboros/security/rbac/service/impl/RbacCacheRecoveryTest.java)描述；多节点失效时效仍需部署实测。

### 4.6 委托授权边界

内部平台身份使用 UNRESTRICTED，它仍受真实角色权限约束。DELEGATED 使用 RESTRICTED，不允许空边界回退为平台身份。业务权限满足 `E = U当前 ∩ C当前 ∩ G ∩ T`；身份登录无需 C/G，ID Token 或纯身份 Access Token 不能调用平台业务 API。

权限快照、资源一致性、isDelegable 默认关闭、撤销/收紧语义与全部消费者要求统一见[委托授权](delegated-authorization-design.md)，协议组件只提供可信边界。

## 5. 平台令牌与会话

[AccessRefreshTokenSessionManager](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/session/AccessRefreshTokenSessionManager.java)通过原 TokenService 创建双令牌，刷新时重新聚合 claims、撤销旧令牌并更新原会话；销毁撤销关联令牌并删除会话。会话数量、客户端与存储按[SessionConfig](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/config/SessionConfig.java)及原 UserSessionStorage 处理。

[JwtTokenService](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/token/jwt/JwtTokenService.java)使用既有签名及撤销缓存。平台 JWT 与 OIDC 发行者的 JWT/JWKS 是不同凭据体系，不能只因都是 Bearer 就互换。

[TokenConfig](ouroboros-web-security/ouroboros-web-security-runtime/src/main/java/com/ouroboros/security/config/TokenConfig.java)以**秒**接收时长：

```yaml
ouroboros:
  security:
    token:
      access-token-expire: 10800
      refresh-token-expire: 604800
```

签名键由 `token.extra-properties`中的 `accessTokenJWTSigningKey`、`refreshTokenJWTSigningKey`读取；源码存在开发默认值，生产必须经受控部署配置覆盖。TokenPair 的到期时间戳为毫秒，不等于配置时长单位。OIDC 的 issuer/JWK 引用及生命周期另见[协议配置](../ability/ouroboros-oidc-server/DESIGN.md#3-部署配置与动态启停)。

## 6. 扩展与验证边界

提供用户、主体、claims、Authority、行约束、认证方式时，先定位既有 Provider/Builder/Supplier，再按正式 SPI 或自动配置机制注册。`ExternalAuthProviderBuilder`承接受控外部协议扩展；业务代码不能新增隐藏授权目录或另存管理员角色。

| 关键行为 | 可复用验证入口 |
| --- | --- |
| 权限交集、显式拒绝与多目标 | [安全核心测试](ouroboros-web-security/ouroboros-web-security-core/src/test/java/com/ouroboros/security/) |
| RBAC 故障恢复、资料 CAS 和写保护 | [账号模块测试](ouroboros-web-security/ouroboros-web-security-rbac-manage/src/test/java/com/ouroboros/security/) |
| 公开接口、Token 与外部认证边界 | [运行时测试](ouroboros-web-security/ouroboros-web-security-runtime/src/test/java/com/ouroboros/security/) |
| 统一认证跨模块验收 | [SSO 契约与用例](../testing/integration/feature/auth/sso-dynamic-config-oidc/contract.md) |

本页维护方法和边界，不记录单次通过数字。独立实例、持久密钥、迁移、集群一致性、真实短信及上游供应商网络均须相应运行证据；源码和测试存在不能证明这些环境已经验收。已知契约导出差距见[API-001](../docs/tech-debt-tracker.md#api-001-版本化接口契约与漂移校验)。
