# 企业业务回调协议核对

核对日期：2026-11-16。此页记录协议依据及本地验证边界，真实企业后台、授权范围与消息投递仍需联调。

## 钉钉 HTTP 事件订阅

依据官方[开发 HTTP 推送服务器](https://open.dingtalk.com/document/isvapp/develop-synchttp-push-server)、[回调消息加解密](https://open.dingtalk.com/document/isvapp/callback-event-message-body-encryption-and-decryption)及[应用事件推送说明](https://open.dingtalk.com/document/development/develop-stream-mode-push-server)。

| 项目 | 实现合同 |
|---|---|
| 签名 | 对 Token、时间戳、nonce、密文排序拼接后计算 SHA-1，常量时间比较；查询参数接受官方 `msg_signature` 及旧 `signature`，同时出现但不同则拒绝 |
| 加密 | 43 字符 EncodingAESKey 解码为 32 字节，AES/CBC/NoPadding；协议自行执行块长 32 的 PKCS7 填充，解密完整验证填充及消息长度 |
| framing | 随机 16 字节、4 字节网络序消息长度、消息、receive key；企业级旧回调 receive key 为 CorpID，应用后台事件订阅为 AppKey，第三方为 SuiteKey |
| 模式 | 缺省沿旧企业级回调；应用后台 HTTP 事件订阅在连接的供应商字段中明确选择 `APPLICATION`，不能默默改变旧配置 |
| ACK | 验证并可靠受理后，在 2500 ms 内返回加密 `success` 的 JSON；字段为 `msg_signature`、`encrypt`、`timeStamp`、`nonce`。不能返回明文 `success` |
| 探测与批量 | GET 解密 echo；POST `check_url` 不进入业务订阅；批量消息逐事件识别，全部需要的订阅受理后才确认 |

本地测试使用独立 JCE framing/填充夹具验证解密，并独立解码和检查 ACK 的填充、接收方、签名与明文。覆盖 AppKey/CorpID 区别、时间戳秒/毫秒、签名冲突、长度越界、错误企业及凭据缺失。测试通过不证明企业后台已配置正确的事件权限与 URL。

## 企业微信与飞书边界

企业微信沿[自建应用协议核对](wecom-self-built-auth-contract.md)区分 CorpID framing 与原第三方 SuiteID。XML 禁用 DTD 和外部实体；存在企业接收方、AgentID 时须与连接一致。GET 验证不进入业务订阅。无 MsgId 的事件使用确定性载荷摘要作为传输事件身份，审批信封继续保留原领域事件算法。

飞书沿原事件订阅协议验证 Verification Token；配置 Encrypt Key 时解密并校验事件请求头签名与时间窗，URL challenge 按原协议验证 token 后响应。存在 App ID 时核对连接归属；普通目录事件无需审批字段。本期没有同步命令 handler，`card.action.*` 明确失败，不能当作未知异步消息成功确认。没有 Encrypt Key 的原 token 模式不额外假定具备签名时间窗，重投幂等由领域 receipt/inbox 保证。

首期三家只接通已实现的审批异步订阅。公共层不保存新的入站表、交付事实或处理状态；未验证的第三方交互、真实企业事件权限和重投窗口不在本地测试的证明范围内。
