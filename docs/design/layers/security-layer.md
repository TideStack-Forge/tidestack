# 安全层

安全层是与 Core 并列的统一安全边界，向 Ability、App/Dev 和 Platform 提供认证、凭据身份、权限决策及访问控制契约。它不拥有组织、通知、客户端配置等业务能力的第二份实现。

| 边界 | 维护入口 |
| --- | --- |
| 七个子模块、平台过滤链、RBAC、会话、SPI | [安全模块设计](../../../security/DESIGN.md) |
| 字段/行状态、委托边界和原消费者 | [委托授权](../../../security/delegated-authorization-design.md) |
| 账号资料与联系方式 | [账号资料](../../../security/user-profile-design.md) |
| 外部身份源、热配置和回调 | [外部认证](../../../security/external-auth-design.md) |
| OAuth2/OIDC 协议端点链 | [能力组件](../../../ability/ouroboros-oidc-server/DESIGN.md) |

外部认证按配置 revision 懒加载/更新目录，失败不能回退为旧静态身份源。Spring 默认实现的 ConditionalOnMissingBean 用于装配互斥，不是运行时错误兜底。平台无状态链与可选 OIDC 协议链分别承担自己的路径和生命周期。

依赖方向、历史例外及机械检查见[架构](../../../ARCHITECTURE.md)与[架构约束指南](../../architecture-constraint-guide.md)。模块存在不等于已赋层，当前治理缺口保持在[技术债](../../tech-debt-tracker.md)；本页不复制扫描数字、固定源码行号或单次测试结果。
