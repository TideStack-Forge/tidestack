# 钉钉与企业微信登录桥接协议参考

本文维护登录桥接所依赖的官方参数、身份作用域及来源。当前平台接入与实现差距见[外部认证设计](../../security/external-auth-design.md)；接口参考不等同于真实租户可用性证明。

## 适用范围

范围为 Connector 登录桥接所需的官方接口合同，不包括审批 API。下列来源的核对日期为 2026-09-09；供应商协议变化时应同步本参考与相应适配器。

- 钉钉企业内部应用与第三方企业应用都支持新版用户 OAuth。内部应用用 `AppKey/AppSecret`，第三方企业应用用 `SuiteKey/SuiteSecret`；`suiteTicket` 不参与用户授权码换 token。
- 企业微信现行第三方应用网页授权应以 `suite_id` 作为授权 URL 的 `appid`，不带 `agentid`。`appid=corpId&agentid=...` 是官方单列的“旧套件应用”企业 OAuth 链接，不应和现行第三方应用链路混用。
- 企业微信现行身份接口是 `/cgi-bin/service/auth/getuserinfo3rd`，响应字段为小写 `corpid`、`userid`、`open_userid`，没有 `user_info` 对象。
- 两个平台都可以通过安全框架的 provider bridge 接入：桥接负责按官方协议验证授权码并返回外部身份；平台已有的 state、身份绑定和 session 流程仍是唯一主链路。

## 钉钉

### 授权地址

官方登录页为：

```text
https://login.dingtalk.com/oauth2/auth
  ?redirect_uri=REDIRECT_URI
  &response_type=code
  &client_id=CLIENT_ID
  &scope=openid%20corpid
  &prompt=consent
  &state=STATE
```

核心参数合同：

| 参数 | 官方合同 | 桥接含义 |
|---|---|---|
| `redirect_uri` | 必填；须与应用登记域名一致 | 固定回安全模块的 connection-scoped callback |
| `response_type` | 必填，固定为 `code` | 不变 |
| `client_id` | 必填；内部应用为 `AppKey`，第三方企业应用为 `SuiteKey` | 凭据形态可直接决定应用类型 |
| `scope` | 必填；只支持 `openid` 或 `openid corpid` | 连接绑定具体企业时应使用 `openid corpid` |
| `prompt` | 必填；`consent` 进入授权确认页 | 授权 URL 必须显式携带 |
| `state` | 可选；随 `authCode` 原样返回 | 对 Ouroboros 是必需的 CSRF/请求关联参数 |
| `corpId` | 可选；`scope` 包含 `corpid` 时可指定用户选择的组织 | Connector 已绑定企业时应传连接的 `corpId` |

官方将回调临时授权码命名为 `authCode`；换 token 请求体中的字段名则是 `code`。桥接入口应接受钉钉回调参数 `authCode`，再映射为 token 请求体的 `code`，不能假定授权服务器会回调通用参数名 `code`。

来源：

- [获取登录用户的访问凭证](https://open.dingtalk.com/document/orgapp-server/obtain-identity-credentials)
- [实现网页方式登录应用（登录第三方网站）](https://open.dingtalk.com/document/orgapp-server/tutorial-obtaining-user-personal-information)

### 授权码换用户 token

```http
POST /v1.0/oauth2/userAccessToken HTTP/1.1
Host: api.dingtalk.com
Content-Type: application/json

{
  "clientId": "...",
  "clientSecret": "...",
  "code": "...",
  "grantType": "authorization_code"
}
```

凭据合同：

| 应用类型 | `clientId` | `clientSecret` |
|---|---|---|
| 企业内部应用 | `AppKey` | `AppSecret` |
| 第三方企业应用 | `SuiteKey` | `SuiteSecret` |

第三方企业应用在官方能力表中是“支持、默认开通”。因此 `SuiteKey/SuiteSecret` 可以直接用于这个用户 OAuth 接口；它们不是只能换审批/应用 token。`suiteTicket` 既不在该请求中，也不能代替用户授权码。第三方企业应用还需按官方指引接入统一授权并取得 `authCode`。

授权码模式必填 `code`，`grantType=authorization_code`；刷新模式改用 `refreshToken` 和 `grantType=refresh_token`。成功响应字段为 `accessToken`、`refreshToken`、`expireIn`，以及选择企业时的 `corpId`。用户 access token 有效期为 7200 秒，refresh token 有效期为 30 天。

来源：[获取用户 token](https://open.dingtalk.com/document/orgapp-server/obtain-user-token)

### 查询当前用户

```http
GET /v1.0/contact/users/me HTTP/1.1
Host: api.dingtalk.com
x-acs-dingtalk-access-token: USER_ACCESS_TOKEN
```

完整路径形式是 `/v1.0/contact/users/{unionId}`；官方允许把 `{unionId}` 传为 `me` 查询当前授权人。企业内部应用和第三方企业应用均支持，但需要“通讯录个人信息读权限”。响应包含 `nick`、`avatarUrl`、`mobile`、`openId`、`unionId`、`email`、`stateCode`。该响应合同不包含 `corpId`；连接企业应使用 token 响应的 `corpId` 校验。

来源：[获取用户通讯录个人信息](https://open.dingtalk.com/document/orgapp-server/dingtalk-retrieve-user-information)

### 标识作用域

- `openId`：用户在当前开放应用内的唯一标识。
- `unionId`：用户在当前钉钉开放平台账号范围内的唯一标识；官方 FAQ 同时表述为“同一个应用服务内全局唯一”，同一第三方企业应用安装到多个企业时，同一用户的 `unionId` 相同。
- `userId`：用户在同一企业内唯一，是组织成员标识，不是跨企业用户主键。

因此第三方登录首选 `unionId` 作为外部身份；`openId` 只适合作为同一应用作用域内的降级标识。两者都必须继续受 provider/connection 作用域约束，不能跨无关开发者账号直接合并身份。

来源：

- [获取用户授权的持久授权码](https://open.dingtalk.com/document/development/persistent-authorization-code)
- [常见问题：userId 和 unionId 的区别](https://open.dingtalk.com/document/orgapp/common-errors)

## 企业微信第三方应用

### 授权地址：现行第三方应用与旧套件必须分开

现行第三方应用合同：

```text
https://open.weixin.qq.com/connect/oauth2/authorize
  ?appid=SUITE_ID
  &redirect_uri=REDIRECT_URI
  &response_type=code
  &scope=snsapi_base
  &state=STATE
#wechat_redirect
```

| 参数 | 官方合同 |
|---|---|
| `appid` | 第三方应用 ID，即 `ww` 或 `wx` 开头的 `suite_id` |
| `redirect_uri` | 必填，URL 编码；域名须为第三方应用可信域名 |
| `response_type` | 固定 `code` |
| `scope` | `snsapi_base` 获取基础身份；`snsapi_privateinfo` 经手动授权获取敏感信息 |
| `state` | 可选，仅 `a-zA-Z0-9`，最多 128 字节 |
| `#wechat_redirect` | 固定片段，必需 |

该合同没有 `agentid`。官方另列的旧套件应用企业 OAuth 链接才是 `appid=CORPID`，并带 `agentid=AGENTID`。Connector 当前持有现行 `suiteId/suiteSecret/suiteTicket` 凭据时，应采用第一种合同；`corpId` 用于回调后校验租户，不用于替代授权 URL 的 `appid`。

来源：[构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91120)

### 获取 `suite_access_token`

```http
POST /cgi-bin/service/get_suite_token HTTP/1.1
Host: qyapi.weixin.qq.com
Content-Type: application/json

{
  "suite_id": "...",
  "suite_secret": "...",
  "suite_ticket": "..."
}
```

成功响应为 `suite_access_token` 和 `expires_in`。官方要求始终使用最新收到的 `suite_ticket`：ticket 每 10 分钟推送一次，实际有效期 30 分钟。`suite_access_token` 有效期 2 小时，必须缓存且不得频繁获取。失败时才保证返回 `errcode`，因此不能以“没有 `errcode` 字段”为失败。

来源：[获取第三方应用凭证](https://developer.work.weixin.qq.com/document/path/90600)

### 用 code 获取访问用户身份

现行正式合同为：

```http
GET /cgi-bin/service/auth/getuserinfo3rd?suite_access_token=SUITE_ACCESS_TOKEN&code=CODE HTTP/1.1
Host: qyapi.weixin.qq.com
```

`code` 最大 512 字节，只能使用一次，5 分钟过期；回跳域名必须完全匹配该第三方应用的可信域名。该接口不允许代开发自建应用调用，代开发应用须走官方单列的身份接口。

用户属于企业时响应示例：

```json
{
  "errcode": 0,
  "errmsg": "ok",
  "corpid": "CORPID",
  "userid": "USERID",
  "user_ticket": "USER_TICKET",
  "expires_in": 7200,
  "open_userid": "wwxxxx"
}
```

正式字段是小写 `corpid`、`userid`、`open_userid`，不是 `CorpId`、`UserId`、`OpenId`；响应中也没有 `user_info`。如果 scope 为 `snsapi_privateinfo` 且用户在应用可见范围，才会返回 `user_ticket`，详细/敏感用户资料须再调用官方的 user-ticket 接口。

`userid` 是企业内成员标识，且会受企业授权关系与升级后 ID 策略影响，可能返回明文或密文；`open_userid` 对同一个服务商全局唯一，同一服务商不同应用取得同一成员时值相同。因此第三方应用登录宜优先用 `open_userid` 作为外部身份，同时保存并校验 `corpid`，把 `userid` 作为连接企业内的成员映射字段。若产品明确将身份严格限定在单一 connection，也可用 `userid`，但不得脱离 `corpid`/connection 作用域。

用户不属于任何企业时只返回服务商作用域的 `openid`。企业协作 Connector 应拒绝这一分支，不能把非企业微信用户绑定为企业成员。

来源：[获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91121)

## 实现边界

1. 钉钉 bridge 同时支持内部应用和第三方企业应用；凭据选择保持 `AppKey/AppSecret` 与 `SuiteKey/SuiteSecret` 两种官方形态。
2. 企业微信这个 bridge 明确实现“第三方应用网页授权”，不要在同一 profile 中兼容旧套件或代开发自建应用；后两者若需要接入，应以独立 provider type 表达其不同合同。
3. 两个 bridge 只负责授权 URL、回调参数归一化、服务端 code 校验和 `ExternalIdentity` 构造；state、连接 revision、身份绑定、登录码和 session 继续复用安全模块。
4. 租户绑定必须 fail-closed：钉钉校验 token 响应 `corpId`，企业微信校验身份响应 `corpid`，都要与 Connector 发布快照中的企业 ID 一致。
