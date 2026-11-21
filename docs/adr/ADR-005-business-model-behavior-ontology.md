# ADR-005: Business Model Behavior Ontology

**状态**: Accepted

**日期**: 2026-10-10

**决策者**: Epic Owner（最终交付接受）

**相关模块**: `ouroboros-business-model`, `ouroboros-data`, `ouroboros-logicflow`, `ouroboros-approval-workflow`

## 背景

业务模型原先集中描述 `fields` 与 `relations`，CRUD、自定义功能、LogicFlow、高代码处理器和审批 Spring 事件分散在不同入口。低代码定义、代码注解和事件框架若分别拥有注册表，会造成身份、发布和执行语义分叉。平台需要先提供动态业务契约，再由注解和类型化客户端复用同一模型路径。

## 决策

1. 业务模型以动态契约作为业务身份事实来源：`fields` 表达属性，`relations` 表达关系，`actions` 表达可请求的行为，`events` 表达已发生的业务事实，`eventSubscriptions` 表达消费方反应。Action、Event 与订阅的身份由模型全名及成员名决定。
2. 动态定义、注解 / SPI 贡献、校验、投影、发布和刷新沿现有业务模型机制完成。注解贡献高代码能力，不创建第二份注册表；Java / TypeScript 类型由动态契约派生。
3. CRUD、自定义功能、LogicFlow 和 `BusinessActionHandler` 进入统一 Action 运行时。业务 EventOccurrence 是独立于 Spring 的不可变事实；Outbox 按订阅管理至少一次交付与确认，Spring 仅作为可选适配渠道。
4. Action 对象参数和关系目标由服务端按已发布模型解析并授权。首期对象身份要求恰好一个名为 `id` 的主键；关系定义只归属 `relations`，不复制到 `fields`。
5. Action 写入、Event 事实和本地订阅效果复用已配置的 `PlatformTransactionManager`。当前已验证范围为 DataSource / JDBC 本地事务使用的同一实际 JDBC Connection；跨数据源原子性只在平台完成并验证相应全局事务参与后才可声明。
6. 订阅使用单一必填 `executionMode`：JOIN_SOURCE_TRANSACTION 同步加入源事务并共同回滚；AFTER_COMMIT_TRANSACTIONAL 在源提交后将本地消费效果与确认原子提交；AFTER_COMMIT_INDEPENDENT 将外部副作用的持久回执与幂等恢复交给声明独立能力的监听器。模式随投递责任固定，不能把既有责任改入已提交源事务。
7. 订阅定义以 sourceEvent、target 组织来源和目标，target.inputMapping 统一动态输入；执行身份由平台按消费模型配置启用且已授权的既有 User，所有模式重验权限，不从原单据或 actor 继承执行权限。

## 理由

动态契约让低代码模型可在不新增业务 Java 类型或重编译应用的情况下定义行为和事实，同时仍允许高代码处理器承接复杂实现。高低代码贡献到同一模型发布快照，避免分别注册后出现两个业务身份。

Action 表示意图，Event 表示事实。保留这一区分，可以让 HTTP、内部调用和订阅执行共享 Action 治理，也让 Spring 或未来传输渠道不决定事件语义与交付责任。

## 后果

### 正面影响

- 行为、事件和消费方订阅可以动态建模、校验、查询和统一治理。
- LogicFlow、高代码实现、CRUD、自定义功能、注解和类型化 SDK 共用身份与运行时边界。
- 审批事件可保留既有事实身份和待投递记录，同时复用租约与退避能力。

### 负面影响

- 新 Action / Event 的语义变更必须经过模型发布验证；注解不能单独绕过动态契约。
- 事件交付采用至少一次语义，外部副作用仍需消费侧幂等或补偿。
- 首期事务资源边界有限，跨资源能力必须单独验证。

### 风险

- 部署环境中的旧脚本若依赖 HTTP `request` 上下文，需要迁移为显式业务输入与执行上下文。
- 消费者权限变化可能暂停已入队交付，恢复后需授权运维者显式重驱。
- 当前实现不涵盖完整 ObjectSet、任意双向关系遍历、跨模型 Interfaces / Shared Properties 或 JTA/XA 资源参与证明。

## 替代方案

### 方案 A：以 Spring Event 和 Java 类型作为业务契约

- **优点**：直接使用 Spring 事件发布与监听机制。
- **缺点**：低代码模型需要与 Java 类型、Bean 生命周期绑定；渠道变更会影响业务身份，且无法自然提供动态逐订阅确认。
- **为何未选择**：Spring 只应是投递适配层，业务契约必须独立于框架。

### 方案 B：分别建设动态注册表和代码注解注册表

- **优点**：低代码与高代码可独立管理。
- **缺点**：同一业务行为会有重复身份、冲突规则和刷新语义。
- **为何未选择**：沿用现有模型 Provider、Validator、投影和刷新主路径即可完成贡献合并，无须平行注册中心。

### 方案 C：先生成静态类型，再以生成类型建立业务契约

- **优点**：调用端编译期类型较强。
- **缺点**：每次动态业务建模都要求代码生成、编译和部署，倒置低代码平台的动态契约。
- **为何未选择**：先完成动态契约和运行时，类型化能力从发布模型派生。

## 参考资料

- [业务行为契约](../../core/ouroboros-business-model/docs/business-model-behavior-contracts.md)
- [业务模型 DESIGN §8](../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)
- [业务行为开发指南](../guides/business-model-behaviors.md)
- [业务行为运行手册](../runbooks/business-model-behaviors.md)
