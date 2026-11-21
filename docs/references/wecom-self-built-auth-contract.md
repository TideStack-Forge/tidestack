# 企业微信自建应用认证合同

核对日期：2026-11-16。用于企业连接基础的自建应用网页授权；真实企业、可信域名和应用可见范围仍需联调。

## 官方依据

- [构造网页授权链接](https://developer.work.weixin.qq.com/document/path/91022)：`appid=CorpID`、`agentid=AgentID`，首期固定 `snsapi_base`；`redirect_uri` 编码，原安全模块负责 state 及本地目标恢复。
- [获取访问用户身份](https://developer.work.weixin.qq.com/document/path/91023)：`GET /cgi-bin/auth/getuserinfo?access_token=...&code=...`；页面标示更新日期 2026-04-21，当前返回字段为小写 `userid`。code 一次有效，最长 512 字节，五分钟失效；可信域名必须匹配 token 对应应用。
- [获取 access_token](https://developer.work.weixin.qq.com/document/path/91039)：`GET /cgi-bin/gettoken?corpid=...&corpsecret=...`；`corpsecret` 来自该应用的 Secret，不能使用第三方套件 Secret。

原始页面快照保存在 `tmp/testing-evidence/wecom-selfbuilt-{authorize,userinfo,token}.md`，SDK 索引仅作为补充检索，不替代以上官方合同。

## 实现边界

自建凭据采用 `agentId/secret`，企业 `corpId` 仍归原非敏感连接。第三方套件保留 `suiteId/suiteSecret/suiteTicket/permanentCode`，两种形状必须互斥；同一连接不能以密钥轮换切换应用形态或 AgentID。

自建网页授权由独立的 `WecomSelfBuiltAuthProvider` 执行，原第三方 `WecomEnterpriseAuthProvider` 保持其 suite token 和 `/service/auth/getuserinfo3rd` 合同。Builder 根据经过校验的凭据形状选择实现，不增加公共 applicationType 或自定义 endpoint。应用 token 由 `WecomApplicationTokens` 统一获取、缓存并按权威连接版本隔离，登录用户授权不成为一份持久 token。

自建响应没有 `open_userid`，因此使用连接作用域内的 `userid` 作为外部登录标识，同时作为企业成员地址。第三方套件继续使用 `open_userid` 作为登录标识、`userid` 作为成员属性。只返回 `openid/external_userid` 的非企业成员被拒绝；`CorpId/userid` 形式的互联企业成员不在本期单企业范围，也被拒绝。不会把 `UserId` 等旧字段当作当前合同的隐式 fallback。

登录不强制业务回调材料。只有使用业务回调时才要求 callbackToken/encodingAesKey；自建应用的加密回调 receiveId 校验企业 CorpID，第三方套件仍校验 suiteId。飞书和钉钉同样在回调使用处验证对应材料，纯登录凭据可省略这些字段。

## 本地验收

新增真实协议形状的 HTTP mock、应用形态互斥、无回调材料的登录、非本企业成员拒绝、令牌复用以及加密回调 CorpID framing 测试。执行记录见 `tmp/testing-evidence/enterprise-selfbuilt-protocol-tests.log`；仅日志的最终成功才表示该轮通过，不将 mock 测试视为真实企业授权验证。
