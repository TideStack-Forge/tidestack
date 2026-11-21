# 验收用例

每项均为 strict 断言，执行前满足 contract 中的独立实例前提；证据目录为 scenario 中的 evidenceRoot。

| Case ID | 分支 | 行为与通过标准 | 证据 |
| --- | --- | --- | --- |
| SSO-01 | normal/state | 外部 CAS 配置新增、变更、删除立即可见；变更前回调拒绝 | 请求记录 |
| SSO-02 | normal/security | discovery 广告与支持一致，JWKS 只有公钥；码+PKCE 真实兑换、ID token 验签和 UserInfo 一致 | 浏览器截图、请求记录、验签断言 |
| SSO-03 | state/concurrency | 两客户端共存，密钥不回显，陈旧配置版本拒绝；变更一客户端不使其他客户端失效 | 管理截图、请求记录 |
| SSO-04 | state/security | 确认身份与业务分组，原请求/会话/CSRF 关联，多标签和重放拒绝 | 确认截图、协议行为测试 |
| SSO-05 | state/dependency | 热暂停拒绝新授权/兑换/刷新，保留 discovery/UserInfo/清理；恢复无需重启 | 请求记录 |
| SSO-06 | permission | ID token、纯身份 access token 拒绝业务 API；有界委托复用统一权限与数据限制 | 请求记录、数据消费者结果 |
| SSO-07 | state/security | refresh 轮换，旧 refresh 和码重放拒绝；标准 RP 退出撤销对应平台会话 | 请求记录 |
| SSO-08 | normal/concurrency | 本人/管理员共用资料规则，版本冲突拒绝，扩展值保留，protected 字段不可伪造 | 资料截图、请求记录、组件测试 |
| SSO-09 | state/security | SMTP 真实投递，验证前旧邮箱不变、他会话/错票/重放拒绝，通过后写入 verified | 脱敏邮件验收记录 |
| SSO-10 | dependency/recovery | 存量/逻辑删除账号分批补齐，重复运行稳定，数据库非空+唯一收口，未就绪拒绝签发 | 迁移日志、物理约束、门禁测试 |
| SSO-11 | dependency/recovery | 共享缓存序列化、并发兑换、重启、缓存故障均保持拒绝与恢复语义 | Redis/API/重启记录 |
| SSO-12 | permission/error | 通用配置、工作台、恢复默认和专用管理均应用权限、投影、CAS；失败不降级基础配置 | 配置行为测试、请求记录 |
| DCR-01 | state/permission | 默认关闭、无持久策略、关闭后真实注册拒绝；两份发现动态隐藏；管理接口要求平台权限；协议不支持 GET/PUT/DELETE | 脱敏请求、过滤链回归 |
| DCR-02 | normal/security | OPEN、PUBLIC+PKCE、机密客户端+basic；新注册无业务权限，未知扩展忽略、已知不支持拒绝；来源可筛选 | 请求、来源列表截图 |
| DCR-03 | concurrency/security | 同一 IAT 多请求及跨节点最多一个成功；平台 token 不作 IAT；吊销只作用未认领凭证 | 请求、原子状态组件测试 |
| DCR-04 | concurrency/state | 仅余一名额时并发注册只成功一个；禁用仍占配额，客户端原值保留，策略收紧/关闭与写入有明确锁顺序 | 真实数据库并发、请求、组件测试 |
| DCR-05 | error/dependency | 失败请求也限流；仅明确回滚可同次重试，提交/可见性失败不能恢复资格或再建客户端 | 请求、故障组件测试 |
| DCR-06 | normal/permission | 原多客户端页面增加服务器级注册页签；策略保存生效，凭证仅一次在密码框显示；普通管理者不可管理注册 | 页面截图、接口记录 |

| DCR-07 | normal/security/state | 动态注册出的公共和机密客户端实际完成 PKCE、确认、JWKS 验签与 UserInfo；身份令牌拒绝内部 API，机密客户端刷新轮换/重放拒绝及 RP 退出撤销授权 | 浏览器确认截图、脱敏协议记录 |
