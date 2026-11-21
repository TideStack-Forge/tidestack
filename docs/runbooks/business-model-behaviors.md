# 业务 Action 与 Event 运行手册

运行时链路与建模入口见[模块设计](../../core/ouroboros-business-model/DESIGN.md#8-动态业务行为与事实)和[开发指南](../guides/business-model-behaviors.md)。

## 2.0.0-rc.1 应用升级兼容

平台版本由根 POM 维护，与业务模型的 `formatVersion` 是两套版本号。升级平台不要求重写存量模型；业务模型缺省、`0.1`、`1.0` 仍走旧格式解析，`2.0` 使用新的动作引用定义。

| 现象 | 平台处理 / 应用处理 |
|---|---|
| 非 entry 根节点声明了输入端口但没有输入连线，被新增校验拒绝 | 恢复旧行为：所在 scope 中无有效输入连线的根节点按端口序号接收 scope 输入；无需新增连线或修改应用元数据。部分连线的节点仍须接全；主流程执行前按所有根节点所需的最大参数数检查缺参，异常根节点接收标准 `errorContext` |
| 旧自定义功能采用 `isGenerateImplementation=true`，未写 `actionInputMapping` | 解析配置时缺省为 `params ← request.params`、`data ← request.body`，投影和显式格式升级共用此规则，不回写原始元数据。已有映射包括 `{}` 保持原意；显式错误值仍拒绝。新版动作引用和主动绑定的高代码接口仍须显式声明映射 |
| 包装为 Action 后旧脚本读取 `$request.body`、`$request.headers`、URI 或 params 中的 Header 失败 | LogicFlow 输入工作视图兼容 `RequestInfoMap` 读取形状；body 默认与已投影 data 同源，HTTP 信息及参数回退复用原请求的惰性读取，不要求修改脚本或应用元数据。已映射业务键及显式 null 优先；高代码 Input 仍使用显式业务分区 |
| 脚本型 CRUD / 自定义动作报 `binding-unavailable`，原因是数据模型节点被自动补出未连接的输入端口 | `DataModelNode` 声明允许零输入，复用引擎已有的源节点机制；`singleScript` 保持该节点没有输入端口或输入连线，仍向脚本第二个参数传递数据模型。已有显式输入连线继续有效。更新平台后删除应用里绕过此缺陷的同名模板覆盖，再验证脚本动作；不能用删除数据模型节点作为通用修复 |
| 普通查询关闭、软删除等查询能力开启后，页面菜单消失 | 查询权限可能只承载能力的数据授权，父权限按其是否实际提供页面入口决定是否保留菜单 / 页面授权，无需为此重写应用菜单定义 |
| 旧格式存在字段与关系同名，或关系引用键未通过新增结构检查 | 缺省、`0.1`、`1.0` 沿用旧版关系校验与投影规则，不删除关系、不替换引用键、不回写元数据；新增关系结构校验仅用于 `formatVersion=2.0`。原有关联目标可用性检查继续生效 |
| `/api/auth/authorities` 条目增加 | 接口仍按权限全名返回 Map；Action / Event 引入新的权限项。不要依赖固定条目数；如怀疑权限值内部结构改变，应对照同一账号的原始响应验证，不能仅凭 key 数量判为结构不兼容 |

智慧物流应用 `master-refactor-1024` 的 `nonStandard.productInfoManage` 同时声明字段和关系 `materielTypeInfo`；`nonStandard.waybillDetail.relations.waybill` 配置 `referenceKey=waybill`。这两份旧格式定义可以继续沿用，平台升级不再要求删除同名关系，也不要求将引用键与选择器值字段改为 `id`。仅显式升级为 `formatVersion=2.0` 时，才需按新结构规则审核关系定义；平台不会猜测或改写业务含义。

这些平台修正不增加数据库结构，也不要求迁移应用元数据。发布后应先删除临时模板覆盖，再验证启动、脚本型 CRUD、自定义动作和磅单菜单。旧格式自定义功能不再需要手补映射；已经补充的有效映射也可以保留。发布前的组件回归不能替代这一步实际应用验证。需要回退时恢复修正前的平台制品，并恢复旧制品仍需要的临时模板。

H2 小写 catalog/schema 的迁移支持沿现有 Liquibase SPI 提供；预览和迁移仍走原链路。回归时应同时覆盖默认和小写命名、重复预览/迁移、既有记录保留，以及审批待交付事实恢复。单独回退该方言时同步撤销其 SPI 注册；这些修正无需回退 DDL。

逻辑流 revision 在模型初始化时计算；初始化前读取明确拒绝。批次初始化失败后不能在同一工厂实例上重试。机制见[装配与初始化](../../core/ouroboros-logicflow/design/core-abstractions-and-patterns/overview.md#42-模式二装配器--校验管线)。

## Action 输入协议升级

契约升级时，先修改模型与手工 WebAPI，再完整发布并重新生成 Java / TypeScript 客户端；不要将旧路由、旧指纹和新 Action 混用。自动 CRUD 的正式重新投影会生成新配置，不应直接改生成产物。

| 原配置 / 用法 | 新配置 / 用法 |
|---|---|
| Invocation 的 target / targetId；Action 的 target | 从 inputSchema 声明 params/data 中的对象角色；对象节点用 x-business-object-ref，集合使用 items；可信状态从 Context.objectStates 的 JSON Pointer 读取 |
| handlerType=LogicFlow + businessActionModelFullName / businessActionName | handlerType=BusinessAction + businessModel / action；移除 logicFlow，增加 actionInputMapping；旧字段或相互冲突的配置会被拒绝 |
| 任意根 input Map | BusinessActionInput.of / paramsOnly / dataOnly / empty；外层固定 params/data，内容继续支持动态对象 |
| 新版自定义 api 仅声明 URL 和方法 | 新版动作引用必须显式声明 api.actionInputMapping；例如 `{ "params.notify":"${ request.params.notify }", "data":"${ request.body }" }`；无输入也显式写 `{}`。旧格式生成实现的缺省规则见上表 |
| Handler 固定 Context + Map | BusinessActionHandler.handle(Context, BusinessActionInput)；注解方法用 @BusinessActionParam / @BusinessActionData，或接收整体 BusinessActionInput<?, ?> |
| subscription.targetIdMapping 与根 inputMapping | 将对象引用及载荷显式映射到 target.inputMapping 的 params/data；重试仍按当前已发布契约执行 |
| Flow 的 input / target / targetState 封套 | 标准入口 params/data/context；特殊旧端口通过 binding.inputMapping 显式投影 |

原 CRUD 的 `$request.data` 表达业务输入，自动投影保持其正式端口契约。Flow 入口同时兼容 `RequestInfoMap`：`body` 默认别名到已投影 `data`，`headers/uri/remoteAddress` 等未映射属性读取原请求，`params` 保留已映射业务键并提供原参数回退；无需给旧脚本增加请求映射或修改访问路径。显式 Flow 映射（含 null）优先。强依赖 HTTP 的绑定声明 `binding.requiresHttpRequest=true`，缺少请求在执行前拒绝；普通后台 invoke 不伪造 HTTP 信息，独立 Event / Outbox 不继承请求。同步 Controller 内手动 invoke 可以从 RequestContextHolder 获取当前请求，显式业务输入优先于请求回退；高代码 Input 的参数来源保持显式。

update 的 data.id 若提供，必须与 params.record.id 一致。自动契约使用 `x-business-object-identity: "/params/record/id"` 表达这个约束；不同角色的 id 不自动互比。审计存储中的历史 target 字段保留以兼容已有记录，新执行不再填入单一 target；关联执行和实际写集仍由正式审计机制记录。

应用迁移应按源码路由、Owner 确认及实际场景逐项核对；未匹配到当前源码的声明不能仅凭扫描判为未实现。迁移验收按具体应用和部署基线取证，不能沿用其他应用的历史成绩。

## 事务与部署

配置一个明确供业务运行时使用的 PlatformTransactionManager，源业务数据、事实、逐订阅交付及本地消费效果必须经正式 DataModel 使用该事务的同一实际 Connection。当前资源证明支持 DataSourceTransactionManager（包括 JdbcTransactionManager）；自定义适配器必须提供有效的事务参与证明。不支持的管理器、另一个数据源或内部 REQUIRES_NEW 写入在修改数据前拒绝。首期未实现 JTA/XA 资源证明，不能把配置 JTA 管理器等同于已支持跨数据源原子提交。外部服务副作用仍须由应用保证幂等或补偿。


应用系统配置按消费模型指定服务账号，账号及权限沿现有 UserCenter/Authority 管理：

```yaml
ouroboros:
  business-event:
    consumer:
      principals:
        "[inventory.Stock]": stock-service
```

方括号保留 Map 键中的模型全名；查找模型名不区分大小写。不配置全局默认账号。同步和异步模式使用同一身份策略，原事件 actor 与交付尝试中的 `user` 分开保留；`user` 保存实际执行账号的 `User.userIdentity`，交付诊断返回同名字段。禁用账号或撤权会阻塞消费；JOIN 模式直接回滚源事务，提交后模式保留阻塞责任，修复后显式重驱。

开发阶段订阅只接受 `sourceEvent / executionMode / target` 新结构，不提供旧订阅字段兼容。交付模型 `executionMode` 在创建责任时必填并固定；已有未完成开发数据须由开发环境负责人重建或明确迁移，不以重新领取覆盖模式。

自动配置沿 META-INF 注册建立 `ouroboros.BusinessEventOccurrence`、`ouroboros.BusinessEventDelivery` 和 `ouroboros.BusinessActionExecutionAudit` 模型，使用既有 DataModel migration / data station 配置。升级前按部署环境的迁移流程审核实际生成 SQL 并备份；既有审批事实与待处理 Outbox 表保持原存储、身份和恢复渠道，无需搬迁到业务 Event 表，避免并行重发审批事实。

默认 CRUD 事实会在首次成功发布后产生，无订阅时也保存。升级前核对 recordsCreated/recordsUpdated/recordsDeleted 名称冲突和消费者完整模型读取权限；不扫描或回放历史 CRUD。事实披露限制保存在 occurrenceJson 内，旧事实缺省 false，无需新增事实表列；新的 PRECONDITION_BLOCKED 沿原 delivery status 存储。

`ouroboros.business-event.mutation` 配置 max-ids（默认 500）和 max-payload-bytes（65536），各份独立交付，源事务内全部原子提交。

`ouroboros.business-event.delivery` 配置 batch-size（默认 100）、lease-seconds（60）和 max-retry-count（6）。提交后唤醒只是优化，恢复扫描负责漏唤醒或进程重启后的交付。至少一次交付不能等同外部 exactly-once，消费者应使用稳定 eventId/deliveryId 实现外部去重。

## 检查与恢复

`GET /api/business-events/{eventId}/deliveries` 需要 `businessModel.events.inspect` 权限；返回事实元信息、每个 delivery 状态、attempt/retryCount、诊断码、匹配 revision、当前 attempt/目标 revision 和实现。响应在标准 WebApiResult.data 中，接口最多返回 100 个 delivery，不包含原始载荷或租约 token。需要更多记录时经正式 DataModel 的受控运维入口查询。

`POST /api/business-event-deliveries/{deliveryId}/redrive` 需要独立 `businessModel.events.redrive` 权限。WebApiResult.data 为是否恢复的布尔值。成功重驱只恢复交付责任，不修改原事实或 eventId/deliveryId。先修复权限或契约，再重驱；重驱权限不授予消费者读取事实的权限。

| 状态 | 处置 |
|---|---|
| PENDING / PROCESSING | 等待扫描；PROCESSING 在有效租约内不能重复领取，超时可恢复 |
| FAILED | 普通失败按退避自动重试，查看安全诊断并修复消费者 |
| AUTHORIZATION_BLOCKED | 当前消费身份停用、权限或完整载荷 / 对象访问不足；恢复后显式 redrive |
| CONTRACT_BLOCKED | 当前订阅、目标、绑定、Schema 或生成 SDK 不兼容；发布完整有效契约后显式 redrive |
| PRECONDITION_BLOCKED | 目标 Action 业务条件不满足；修复条件后显式 redrive，源事实不变 |
| DEAD | 重试预算耗尽；修复后显式 redrive |
| SUCCESS | 已确认；扫描或重驱不会重跑成功责任 |

待投递事实保留原始 Schema 与载荷。每次 attempt 解析当前完整实现，不恢复旧流程；不能通过修改原事实规避兼容或授权检查。某个消费者失败不会重跑已确认消费者。

## 发布失败与 Spring 渠道

整次刷新与同步 Action / 交付互斥。刷新失败后业务入口报告发布快照不可用，已有 Outbox 责任保留；修复模型成员诊断、绑定或投影并成功完整刷新后恢复。业务执行中不能同步触发刷新。Action availability 的 BLOCKED/CONTRACT 可用于展示服务状态，不能替代实际调用检查。

Spring dispatcher 可以停用，普通平台 listener 与 Outbox 扫描仍可工作。显式使用 BusinessEventSpringAdapter 的 listener 必须有同步 dispatcher；缺失、异步或延后 dispatch 不能确认，效果与确认失败一起回滚。Spring 广播只携带所选调用的 dispatch 信号与 delivery 身份，不携带业务载荷；目标仍受平台权限、Schema 与同步能力检查。

审计区分 STARTED、COMMITTED、ROLLED_BACK 等实际事务结果，可关联父 executionId、实际写集及 eventId。异步交付状态单独看 delivery，不能把源 Action 提交等同所有消费者已完成。

## 回归

执行 `python3 testing/common/runners/playwright/browser_asset_bridge.py --e2e-manifest testing/e2e/regression/lowcode/business-model-actions-events/e2e.yaml --mode run --script-id business-behavior-journey`。前置为 Java 21、Maven 及 SDK 的 `npm ci`。入口使用正式 H2/JDBC、MVC 与编译客户端，输出 Surefire、JaCoCo 和 runner 证据；不依赖运行中的应用，也不修改共享数据库。它不证明部署环境、浏览器或 MySQL/XA 行为。稳定用例及范围见[场景契约](../../testing/integration/feature/lowcode/business-model-actions-events/contract.md)。

显式配置 OUROBOROS_BEHAVIOR_MYSQL_URL、OUROBOROS_BEHAVIOR_MYSQL_USER、OUROBOROS_BEHAVIOR_MYSQL_PASSWORD 后，同一 runner 中的 Journey、业务 Outbox 和审批存量存储用例使用真实 MySQL。凭据只放环境变量。`run.sh --performance` 输出入口及存储成本原始 CSV；`--deployment` 启动独立 mothership 供浏览器操作，具体参数见场景契约。默认测试、真实数据库、独立进程和浏览器结论分别取证。
