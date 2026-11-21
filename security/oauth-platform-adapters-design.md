# OAuth 平台协议适配器设计

## 状态

- 日期：2026-09-12
- 状态：已获用户确认，尚未实现共享宿主/适配器；保留为采纳待实现设计，不能列入当前功能
- 适用范围：标准 OAuth2/OIDC、钉钉用户 OAuth、企业微信第三方应用网页授权

## 当前落点与实现差距

现有代码仍以 OAuth2Provider 和 Connector 各平台 provider/builder 承接协议。仓库未提供本文拟定的 OAuth2AuthorizationCodeFlow / OAuth2ProtocolAdapter；迁移、测试和上线条件均为待实施要求。差距在[SEC-001](../docs/tech-debt-tracker.md#sec-001-oauth-共享协议适配器待实现)跟踪。现有主路径见[外部认证](external-auth-design.md)。

## 背景与判断

当前安全模块已经提供 `RedirectAuthProvider`、`OAuth2Provider`、
`OAuth2ProviderSpec` 和统一 OAuth controller。Connector runtime 也已经能够从
`enterprise.connections` 派生 connection-scoped provider profile，并由
`ExternalAuthProviderFactory` 按连接解析 provider。

现有实现的问题不是缺少平台开关，而是标准 OAuth provider 与钉钉、企业微信
provider 各自重复了授权 URL、回调读取和授权码交换的编排。钉钉和企业微信的
协议差异不能安全地压缩成任意 URL、HTTP 方法、请求头、请求体或 JSON Path 配置：
这会把安全模块变成可编程 HTTP 认证引擎，扩大 SSRF、身份字段误配和企业边界
校验缺失的风险。

本设计采用“共享 OAuth 主流程 + 受控平台协议适配器”：安全模块集中拥有认证
编排和安全校验；平台适配器只拥有平台协议的固定差异。连接配置只选择平台、
凭据引用和企业边界，不承载协议实现。

## 目标

1. 复用现有 OAuth 的 state、nonce、PKCE、callback state 消费、连接 revision、
   `ExternalIdentity`、SSO binding 和 session 处理。
2. 用同一套授权码主流程承载标准 OAuth、钉钉和企业微信。
3. 保留平台协议的类型安全和 fail-closed 校验，避免任意 HTTP 配置。
4. 保持现有 controller、`RedirectAuthProvider` 和 connection-scoped 路由的
   外部行为不变。
5. 让标准 OAuth 的 endpoint、scope、身份字段等现有配置继续有效。

## 非目标

- 不增加租户可编辑的任意 `authorizationUri`、`tokenUri`、userinfo URL、HTTP
  header/body 模板或 JSON Path。
- 不把审批 API token、suite ticket、tenant access token 当作用户登录凭证。
- 不新建 session、SSO binding 或审批身份表。
- 不把业务 webhook/callback 送入 OAuth callback controller。
- 不在本次工作中实现企业微信 JS-SDK 免登、旧套件授权或审批 JS-SDK 发起链路。

## 方案

### 1. 共享主流程模块

在 `ouroboros-web-security-runtime` 增加一个共享的 OAuth 授权码流程宿主，暂定
名称为 `OAuth2AuthorizationCodeFlow`。它实现 `RedirectAuthProvider`，对调用者
隐藏以下复杂度：

- 生成一次性 state、按协议要求生成 nonce 和 PKCE verifier/challenge；
- 生成 connection-scoped redirect URI，并将 return URL、连接 ID、profile revision
  和安全状态写入短期缓存；
- 从 `CallbackParams` 读取平台声明的授权码参数名，并拒绝重复参数；
- 在授权回调中消费 state、校验连接和 profile revision，再调用协议适配器；
- 将适配器返回的 `ExternalIdentity` 交给现有 `ExternalAuthService`；
- 保持现有 provider key、连接作用域和 fail-closed 行为。

宿主不暴露任意 URL 或字段映射能力。授权 URL 的基础参数由宿主生成，平台额外
参数由受控适配器生成；适配器只能返回已注册协议允许的请求结构。

### 2. 受控协议适配器

在安全运行时定义小接口 `OAuth2ProtocolAdapter`，只描述主流程需要的变化点：

```java
interface OAuth2ProtocolAdapter {
  String callbackCodeParameter();

  AuthorizationRequest buildAuthorizationRequest(AuthorizationContext context);

  Either<AuthError, ExternalIdentity> exchangeCode(CodeExchangeContext context);
}
```

该接口是安全运行时与平台实现之间的 seam，不允许实现任意网络目的地。实现由
已注册的 `ExternalAuthProviderBuilder` 创建，按 provider type 选择，重复 type
启动失败。

### 3. 标准 OAuth2 适配器

将现有 `OAuth2Provider` 的标准逻辑迁移到 `StandardOAuth2ProtocolAdapter`：

- 使用 `OAuth2ProviderSpec` 中已有的 authorization/token/userinfo/JWKS 配置；
- 继续使用标准 form-urlencoded token 请求和 Bearer userinfo 请求；
- 继续执行 issuer、audience、签名、nonce、过期时间和稳定外部 ID 校验；
- 继续支持 `externalIdField`、`userInfoDataField`、`memberIdField` 和 PKCE。

`OAuth2Provider` 保留为兼容入口，但内部只负责组装 spec、客户端和标准适配器，
不再重复承载授权码流程编排。

### 4. 钉钉适配器

钉钉 provider 迁移为共享宿主 + `DingtalkOAuthProtocolAdapter`：

- 回调参数固定读取 `authCode`，再映射为 token 请求体的 `code`；
- 使用固定官方 token endpoint、JSON 请求体和
  `x-acs-dingtalk-access-token` userinfo header；
- 用 token 响应中的 `corpId` 与 connection 的 `corpId` 做严格比较；
- 外部登录 ID 优先 `unionId`，缺失时才使用 `openId`；
- 通过既有成员解析器取得企业内 `userId`，缺失时拒绝建立登录绑定；
- 内部应用和第三方企业应用继续由 typed credential 的字段组决定，不增加
  可编辑的 `applicationType`。

### 5. 企业微信适配器

企业微信 provider 迁移为共享宿主 + `WecomOAuthProtocolAdapter`：

- 授权 URL 固定使用 credential 中的 `suiteId`、`snsapi_base` 和安全 state；
- 先用 `suite_id/suite_secret/suite_ticket` 换取并缓存 `suite_access_token`；
- 使用固定的 `getuserinfo3rd` 接口，将 token 和 code 作为 query 参数；
- 严格校验小写 `corpid` 与 connection 的 `corpId`；
- 以 `open_userid` 作为稳定外部登录 ID，以 `userid` 作为成员标识；
- 缺少企业归属或成员标识时拒绝登录，不降级到 `openid`。

## 配置契约

连接仍使用现行结构，不增加平台总开关或公共协议字段：

```json
{
  "connectionId": "dingtalk-main",
  "provider": "dingtalk",
  "credentialRef": "env:DINGTALK_CREDENTIAL",
  "corpId": "dingxxx",
  "auth": {
    "enabled": true
  }
}
```

规则如下：

1. `provider` 是协议适配器的唯一选择器，由已发布连接派生，浏览器不能覆盖。
2. 标准 OAuth 的 endpoint 和身份字段只对标准 OAuth profile 生效；钉钉和企业微信
   的 endpoint、参数名、header 和稳定身份字段由适配器固定。
3. `auth.enabled=false` 只移除该连接的认证 profile；审批、通知、目录、业务回调
   和服务端 API 凭据不受影响。
4. `ouroboros.security.external-auth.dingtalk.enabled` 和
   `ouroboros.security.external-auth.wecom.enabled` 不再作为认证能力开关；在
   实现阶段删除其 metadata 和误导性配置测试。若以后需要全局熔断，应由运维入口
   实现，而不是加入 OAuth 配置模型。
5. 未知 provider、凭据解析失败、profile revision 不一致和适配器校验失败都必须
   fail-closed，不得回退到静态同名 provider。

## 数据流

```text
GET /api/auth/oauth2/{connectionId}/authorize
  -> Factory 按 connectionId 解析 profile
  -> OAuth2AuthorizationCodeFlow 生成 state/nonce/PKCE
  -> OAuth2ProtocolAdapter 生成平台授权 URL
  -> state cache
  -> 外部平台授权
  -> GET /api/auth/oauth2/{connectionId}/callback
  -> 消费并校验 state、connection revision
  -> 适配器按平台协议换 token / 取身份
  -> ExternalIdentity(connectionId, externalId, memberId)
  -> ExternalAuthService
  -> UserSso binding / Ouroboros session
```

业务 webhook 仍进入 Connector callback ingress；OAuth callback 只进入安全模块。
用户 token、suite token、授权 code 和 secret 不进入日志、SSO 持久化或业务 fact。

## 迁移策略

1. 先新增共享宿主、适配器接口和标准适配器，不改变现有 public interface。
2. 将 `OAuth2Provider` 改为共享宿主的标准实现，保持已有静态 provider 和
   connection-scoped Feishu 行为。
3. 将钉钉、企业微信 provider 的重复编排迁移到各自适配器，保留 builder、profile
   provider 和 connection resolver 的现有注册路径。
4. 保留现有 provider 类名作为薄组装入口，待所有调用和测试迁移后再判断是否删除。
5. 删除平台级开关的配置 metadata；更新设计文档，明确连接级 `auth.enabled` 是
   唯一业务启用控制。

## 测试契约

测试通过 public seam 验证行为，不测试私有实现：

- 共享宿主：state 单次消费、重复/过期 state、连接 revision 变化、PKCE/nonce、
  return URL 规范化和 provider key 路由；
- 标准 OAuth：form token 请求、Bearer userinfo、JWKS/id_token、身份字段配置；
- 钉钉：`authCode` 回调、JSON token 请求、自定义 header、corpId 不匹配、unionId
  缺失降级和成员 userId 缺失；
- 企业微信：suite token 缓存、query 参数、corpid 不匹配、open_userid/userid
  缺失和错误响应；
- Factory/resolver：connection profile 派生、auth.enabled、revision 校验、未知
  provider 和静态同名冲突；
- 配置契约：平台级 enabled 属性不影响适配器注册，连接级 auth.enabled 才控制
  profile 发布；任意 endpoint/header/body/path 字段不能进入连接公共 schema。

所有新增代码按模块补充单元或 contract test，覆盖率满足项目门禁；真实租户的
redirect URI、权限、撤销和 token 失效验证仍是平台上线前置条件。

## 风险与回滚

- 风险：适配器迁移期间可能改变错误分类或请求细节。通过保留现有 provider builder、
  逐平台 contract test 和分阶段迁移降低风险。
- 风险：旧配置仍携带平台级 enabled 属性。实现时先停止其运行时语义，再删除
  metadata 和测试，避免出现“配置看似生效但实际不生效”的误导。
- 回滚：共享宿主可由现有 provider 实现继续提供，平台适配器迁移按 provider 独立
  回退；不修改 UserSso 数据结构和 connection 持久化格式。

## 验收标准

1. 标准 OAuth、钉钉、企业微信共用同一个 `RedirectAuthProvider` 主流程宿主。
2. 钉钉和企业微信的协议差异只能通过已注册适配器提供，连接配置不能指定任意
   endpoint、header、body 或 JSON Path。
3. 现有 OAuth controller、connection-scoped state/revision 和 `ExternalAuthService`
   行为保持不变。
4. 三个平台的负向 contract test 均 fail-closed，且 token/secret 不泄漏。
5. 平台级开关不再承担业务认证启用语义，连接级 `auth.enabled` 是唯一连接控制。
