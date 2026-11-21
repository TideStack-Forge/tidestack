# Business Action and Event Journey Contract

动态 BusinessModel 经正式投影得到 Action、Event、Subscription 与数据模型。Spring MVC 调用统一 Action，源数据、事实和逐订阅记录提交到同一个实际 JDBC Connection；新 Outbox worker 在没有提交后唤醒的情况下恢复消费，并经同一 Action runtime 完成库存预留。高代码与真实编译的 LogicFlow 均须走这些入口。

## 夹具与准入

- Java 21、Maven reactor 使用当前工作树源码；SDK 使用当前源码构建，执行前先完成 `npm ci`。
- 默认每个用例独立 H2 数据库；显式设置 `OUROBOROS_BEHAVIOR_MYSQL_URL` / `OUROBOROS_BEHAVIOR_MYSQL_USER` / `OUROBOROS_BEHAVIOR_MYSQL_PASSWORD` 时，Journey / BusinessEvent Outbox 与审批 Outbox 升级场景使用真实 MySQL、隔离表名。均经 SqlDataStation 与正式 TypedModelMetaHelper 建表；业务定义通过 DefaultBusinessModel / ModelBundle 投影。未设置环境时，MySQL 专用用例明确跳过，不计入 MySQL 验收。
- 模型目录和 UserCenter 由测试提供可控夹具，执行器、端口、事务管理器、QueryDSL、持久化事实、worker 和 MVC 路由使用正式实现。权限仍由当前身份与正式 Authority 检查。
- Java 生成源码通过 javac 编译后调用；TypeScript 生成产物通过 TypeScript compiler 的正反类型检查并调用构建的 SDK。

## 判定

以数据库业务状态、不可变事实和 delivery 状态、执行审计、API 结果及消费者调用次数为准。源失败须整体回滚；JOIN_SOURCE_TRANSACTION 消费者在 emit 返回前完成，失败即使被捕获也使源写入、事实及所有同步效果一起回滚，成功不由 Outbox 重放；提交后消费者失败不回滚已提交事实，未确认效果须回滚，重试成功后不得重跑已成功 delivery。模式在责任创建时固定，级联上限 32，内部 REQUIRES_NEW 业务写入拒绝。执行账号按消费模型显式配置并重验，不从 actor 取得权限。权限撤销及契约不匹配须暂停，事实身份不变，修复后显式重驱。

同一事实可同时匹配三种模式：同步订阅先在源事务中完成，两种提交后订阅保留待交付责任；根事务提交后才能交付，且成功责任不重跑。根事务在 emit 返回后失败时，同步效果、事实及全部交付责任一起回滚，不唤醒提交后消费。

正式 batchDelete / import feature 也通过同一 Action 入口验证：params.records 映射为流程 ids，data.file 保留原生 XLS/XLSX，经无 JSON 约束的文件字段和生成 Java Input 客户端进入正式导入流程。Action input 根仅有 params/data，data 可以是 Schema 允许的对象、数组或标量；Event payload 保持对象根。发布拒绝显式非对象根定义，开放和组合生成客户端保留实际根对象约束。

输入修订还验证 HTTP / invoke × 注解 Java / 实际编译 LogicFlow 四组合、HTTP Input 身份复用、按键读取与枚举键不求值、未映射 body 不读取、Header 按名读取、body 及 Context.request 共用一次解析、严格校验与全对象角色授权先于实现、独立交付无请求继承。正式 add/update 脚本保留 `$request.data.id` 的业务端口语义，工作数据可变且不污染调用者，身份冲突不得写入。Java 参数计划拒绝不明确签名、丢失业务属性和可缺失 primitive。

本场景证明单实际 JDBC 资源与进程内 HTTP 管线。MySQL 方言仅由显式 MySQL 运行证明。已部署环境、真实浏览器须使用独立部署模式另行取证；跨数据源 XA 或远程副作用原子性不计入本场景通过结论。

## 执行与证据

自动化入口：[e2e.yaml](../../../../e2e/regression/lowcode/business-model-actions-events/e2e.yaml)。用例实现归属 business-model Maven 模块和 SDK；场景不复制生产执行器。实际运行须保留 Surefire、JaCoCo 及 runner 日志作为对应执行的原始证据；稳定用例索引见 [cases.md](cases.md)。

## 独立部署与浏览器取证

`run.sh --deployment <Spring 启动参数>` 编译并启动真实 mothership。先将当前模块安装到本地 Maven 仓库，准备仅含合成数据的独立元数据目录和 MySQL 8.3 数据库；通过环境变量传入数据源凭据，使用 `--spring.config.additional-location=file:<隔离配置文件>` 指定运行配置。必填 `--acceptance.frontend-origin=<本地前端 origin>` 与 `--acceptance.evidence-dir=<本地证据目录>`；监听地址限定 127.0.0.1。

此模式仅增加指定 origin 的 CORS Filter 和只读证据观察器，不增加业务端点；认证、发布、Runtime、DataModel、迁移、事实与 Outbox 均为正式实现。观察器经系统身份的正式 DataModel 查询合成 BehaviorOrder 的事实/交付摘要，不导出令牌或租约所有权。浏览器在原编辑器中保存提交后，将已提交元数据放入上述隔离部署目录；运行表单填写目标 API 和该应用登录得到的临时访问令牌，验证 availability/invoke 与诊断/redrive。令牌不写入元数据或取证文件。

部署证据与进程内证据分别记录；只有实际执行并保留请求结果、截图及持久状态后，才能认定相应部署/浏览器分支通过。

## 性能与存储升级

`run.sh --performance` 运行正式输入路径的 opt-in CPU/分配测量，以及同一事务/审计配置下的默认批删事实成本测量，输出原始逐样本 CSV。第一组覆盖 HTTP→Handler、HTTP→Flow、invoke→Flow、模型→Handler 及直接 invoke→Handler 对照，断言 body 需要时一次、未使用时零次解析。第二组比较无自动事件计划、只存事实、1/8 个订阅；每份最多 32 个 ID，断言实际事实和投递增长与分块一致。只有配置上述 MySQL 环境才是 MySQL 存储成本，H2 结果不得冒充。

CPU 测量排除网络和数据库，存储测量使用 DriverManagerDataSource，包含连接创建成本，均不能作为生产延迟 SLA。allocatedBytes 是当前线程分配量；heapUsedBytes 是样本结束时 JVM 已用堆。heapPoolPeakUsedBytes 在每次调用前 resetPeakUsage，再汇总堆内存池报告的峰值，是整个 JVM 各池峰值之和的上界观察，不能当成精确保留内存或单次 Action 的独占峰值。默认输入量为 1/128；可通过 ouroboros.behavior.performance.sizes 指定大批量测量。比较前后版本时使用相同负载、事务和审计配置，保存基线版本与适配差异。

旧事实升级用例删除 occurrenceJson 中本轮新增的披露标志，验证无 DDL、原始 JSON/事件/投递身份不变且只确认一次。审批存量用例独立验证原 PENDING 和过期 PROCESSING 的身份及 token 归属，不将业务事件的恢复测试替代审批回归。
