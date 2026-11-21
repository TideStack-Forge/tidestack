# 安全与访问控制的协作边界

安全机制正文在[安全模块](../../../security/DESIGN.md)。本页只解释跨层归属，避免账号、协议和业务消费者各自维护一套安全语义。

| 提供者/消费者 | 职责与不能越过的边界 |
| --- | --- |
| security | 聚合 User/Subject/claims，维护凭据身份及 RBAC，统一输出 Permit/Deny 和字段/行约束 |
| 组织能力 | 经 UserSubject 提供员工等业务主体；员工档案不替代账号资料，不隐式同步联系方式 |
| 统一配置适配 | 管理外部认证、客户端和账号规则的权威值/revision；配置值与账号资料值分开 |
| OIDC 能力 | 复用原平台登录/会话与 SAS 端点；身份凭据和业务委托用途分别校验 |
| 应用运行时与业务模型 | 在原数据插件、Action 和事件入口消费当前有效权限，不从真实角色重算以绕过凭据边界 |
| 脚本、逻辑流、前端 | 使用原上下文及有效权限投影；前端菜单只作展示，服务端仍执行决策 |

委托权限的角色 OR、边界 AND 以及数据操作落实只在[委托设计](../../../security/delegated-authorization-design.md)维护。data 内核通过原插件接收约束，不反向依赖 security；跨线程作用域必须保留完整身份并恢复原上下文。

装配复用[扩展机制](extension-mechanisms.md)，配置沿[配置系统](configuration-system.md)，AppModule 权限投影见[桥接边界](../layers/appmodule-authority-bridge.md)。不增加隐藏注册表、第二份账号库或协议专用授权算法。
