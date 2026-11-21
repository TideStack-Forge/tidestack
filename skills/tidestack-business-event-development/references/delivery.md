# 发布、订阅与失败恢复

## 常规业务接入

先查已有事件及订阅，再确定需要发布什么已发生事实、由谁执行后续业务。应用消费现有事件时定义订阅即可，无需实现投递基础设施。

Action 中用 BusinessEventPort 或模型正式 emitEvent 方法，沿当前 Action 的 emits 和权限检查；流程使用 emitBusinessEvent 节点。非 Action 生产者使用已提供的 BusinessEventProducer 注册契约，声明 kind 并获得事件 emit 权限，通过 port.inTransaction 包住正式 DataModel 写入与 emit。任一失败应回滚源事务，不吞异常后返回成功。普通 Controller 或直接 Handler 调用不等价于 Runtime 业务执行。

平台负责将事件与源写入按正式事务提交，并按订阅 executionMode 同步合并事务或在提交后可靠投递。应用不另建 Outbox、投递队列或后台扫描器；普通 `@TransactionalEventListener(AFTER_COMMIT)` 加 `@Async` 不能替代这项保证。

订阅使用 `name/title/description/isEnabled/sourceEvent/filter/executionMode/target`。`sourceEvent` 包含 `modelFullName/eventName`；`target.kind=action` 配置 `modelFullName/actionName/inputMapping`，`target.kind=listener` 配置 `listenerName/config/inputMapping`。config 是字面 JSON，inputMapping 使用平台 EL。filter 在创建责任时评估，修改过滤器不撤销已有责任。

| executionMode | 执行与失败边界 |
| --- | --- |
| JOIN_SOURCE_TRANSACTION | emit 返回前同步完成，源业务、消费效果和确认同事务；失败使源事务回滚，成功不由 Outbox 重放 |
| AFTER_COMMIT_TRANSACTIONAL | 源提交后独立消费，本地效果与确认原子提交；失败由 Outbox 恢复 |
| AFTER_COMMIT_INDEPENDENT | 源提交后无消费事务/行锁调用 listener；listener 持久化回执并负责幂等与部分成功恢复 |

Action 仅支持前两种。同步级联上限 32，兄弟订阅不保证顺序；有依赖的步骤放进同一 Action/flow。模式在创建投递责任时固定。

平台通过 `ouroboros.business-event.consumer.principals` 按消费模型配置真实、启用、已授权的已有 User；订阅不指定身份。所有模式及恢复都重验权限，原 actor 仅作来源记录；请求 ThreadLocal 不会自动跟随后台执行。仅向载荷写入业务需要的稳定标识和数据，不携带原始凭据或整个请求对象。

重试或回放可能重复。按业务幂等键及事件/交付标识避免重复扣减、重复通知或重复外部调用；不宣称 exactly-once。远程调用超时后可能已成功，先查询或对账，再决定恢复操作，不能把超时直接等同于未执行。

## 仅在新增 Java listener 时读取

可实现 BusinessEventListener 或使用 BusinessEventSubscription 绑定，按正式发现方式注册。注解监听应同步完成其工作，不能加异步代理后提前报告交付成功。

| listener 交付契约 | 业务实现需保证 |
| --- | --- |
| ATOMIC | 使用正式事务内的数据操作；该保证不包括任意远程副作用 |
| INDEPENDENT | 外部效果按业务提供幂等及恢复方法；本地成功标记不能原子提交远端写入 |

deliveryMode 来自 listener 的实现契约：ATOMIC 可选合并源事务或提交后事务，INDEPENDENT 只能选提交后独立消费。方法注解通过必填 executionMode 声明对应适配能力。复用现有 listener 时只遵守其公开输入、结果和恢复契约；无需阅读或重写平台投递状态机。

应用参照：业务模型 Action 的事件发布与订阅，以及审批结果、通知等已有事实消费者。目标版本的字段和边界以配套的业务模型行为指南与运行手册为准；源码可用时再定位 BusinessEventPort、BusinessEventListener 等公开接口。
