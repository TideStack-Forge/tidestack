# Business Behavior Cases

| Case ID | 分支与前置 | 可观察通过标准 | 实现 / 证据归属 |
|---|---|---|---|
| BAE-001 | normal；动态模型及允许的主体 | MVC → Handler Action → Event → 新 worker → Subscription → Action；订单、预留和两个完成审计持久化，delivery 成功 | runtime `BusinessBehaviorJourneyIntegrationTest` |
| BAE-002 | normal；真实编译的低代码流程 | 同一 Action 改绑 LogicFlow 后仍产生持久事实及库存效果，生成客户端指纹有效 | runtime `BusinessBehaviorJourneyIntegrationTest` |
| BAE-003 | error / recovery；源或消费方在写入后失败 | 源失败零业务写入/事实/delivery；消费者失败效果回滚，源事实不变；重试只执行未确认订阅 | runtime journey 与 `BusinessEventOutboxIntegrationTest` |
| BAE-004 | permission / transition；撤销消费者授权 | 不执行监听/Action，不暴露部分载荷；AUTHORIZATION_BLOCKED 保留事实，恢复授权后显式 redrive 成功 | runtime journey / Outbox integration |
| BAE-005 | contract / publication；Schema 或绑定变化 | 过期生成契约在统一入口拒绝且无副作用；兼容重新绑定可执行；刷新失败不混用快照，禁用目标无法交付 | runtime Action / Subscription、default Export、common-app lifecycle tests |
| BAE-006 | normal / invalid / boundary；动态导出 | 生成 Java 和 TypeScript 可编译、可调用，错误输入类型被编译器拒绝；动态身份、对象引用、Schema 校验及错误语义一致 | default codegen、dev controller、SDK 单测与 generated-client 自动检查 |
| BAE-007 | dependency / recovery；Spring 渠道开关 | 不使用 Spring 适配仍由扫描恢复逐订阅投递；启用适配只同步调用已选监听器，不广播载荷，异步或未完成不能确认 | runtime journey / `BusinessEventSpringAdapterTest` / runtime configuration tests |
| BAE-008 | regression；已有审批与聚合模型 | 审批身份及待投递记录保持可恢复；submitTogether 正式生命周期和事务回归通过 | approval Outbox integration 与 data child collection / default projection tests |
| BAE-009 | normal / boundary；正式 feature 投影 | batchDelete 只删除指定对象；原生 multipart XLS/XLSX 经正式 import 流写入数据且事务审计提交；生成 Java Map 客户端保留文件资源 | runtime journey、WebAPI adapter、util Schema 与 default codegen tests |
| BAE-010 | invalid / publication；输入与载荷根形态 | 显式非对象根 Schema 发布拒绝；开放、nullable 与含对象分支的组合定义可发布，生成 TypeScript 输入仍拒绝标量 | default behavior contract、codegen 与 generated-client 自动检查 |
| BAE-011 | normal / invalid；HTTP / invoke × Java / Flow | 同一动态契约得到相同业务值与结果；Web Input 同一实例到 Handler，读取 params 及枚举分区键不解析完整 body，Entry 按需取值，Header 按名读取；body 和真实 request 只解析一次；缺失、null、多值、未知属性在实现前按契约处理 | core Input、default MethodBinding、WebAPI `BusinessActionHttpInputPlanTest`、runtime `BusinessActionInputJourneyTest` |
| BAE-012 | boundary / permission；多对象角色及上下文 | params/data 内的全部引用和数组项授权后只执行一次；状态以 JSON Pointer 放入 Context，独立 Event 不继承 HTTP，请求必需绑定缺失时拒绝 | runtime `DefaultBusinessActionRuntimeTest` |
| BAE-013 | regression / invalid；正式 add/update 脚本 | `$request.data.id` 可访问，beforeScript 改动只影响工作数据，原输入不变；路径和载荷身份冲突在写入前失败 | runtime `BusinessBehaviorJourneyIntegrationTest` |
| BAE-014 | normal / atomicity；默认 CRUD 事实 | add/update/delete/batchDelete/import 按自身实际写集登记，删除后可交付；分块独立失败/重驱，输出或事实失败整笔回滚 | runtime journey / mutation emitter tests |
| BAE-015 | permission / race；前置条件 | 授权锁读后重验，两个并发提交只成功一次；非 id 关系键改值/改绑拒绝，不可读字段不能按 null 绕过 | core precondition / runtime relation tests / MySQL journey |
| BAE-016 | upgrade / recovery；业务与审批存量 | 旧 occurrenceJson 无 DDL 保持原投递身份；审批 PENDING/过期 PROCESSING 可恢复，旧 token 拒绝且成功责任不再接管 | `BusinessEventOutboxIntegrationTest` / approval `FlowEventOutboxStorageIntegrationTest` |
| BAE-017 | performance；单次与批量、订阅数量 | 按需解析次数、逐样本分配/耗时/堆使用和真实事实/投递增长可复核 | `run.sh --performance` / opt-in runtime tests |
| BAE-018 | atomicity / boundary；同步合并与级联 | emit 返回前执行完成；监听器或 Action 失败即使被捕获仍全回滚；REQUIRES_NEW 业务写入拒绝；同步成功不重放，级联超 32 层全回滚；同一事实混合三种模式时保持各自提交边界，源事务在 emit 后失败会撤销同步效果与两种提交后责任 | runtime journey / `BusinessEventOutboxIntegrationTest` |
| BAE-019 | contract / identity；新订阅 DSL 与消费账号 | 单 executionMode 与实现能力匹配，责任创建后模式不可变；账号按消费模型隔离，缺省/禁用/撤权阻塞，不继承 actor；Action/Listener 统一 target.inputMapping | core mapping / default contract / runtime identity、configuration、subscription tests |
