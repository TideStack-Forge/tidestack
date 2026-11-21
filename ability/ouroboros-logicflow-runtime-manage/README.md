# 逻辑流运行时管理

在运行环境观察已部署逻辑流、检查并保存持久覆盖、条件撤销与恢复操作前态，按实例观察应用结果。生产依赖只引用核心契约与共享编辑器资产；不引入 logicflow-dev。

入口 `/system-setting/logicflows`，API `/api/runtime/logicflows`。正式装配入口 `ouroboros-ability-all-runtime` 包含本模块；应用也可单独选择它。需要逻辑流 runtime、typed data、事务管理器、配置与权限实现，以及原平台 UI/微前端部署链。八个 `sys_runtime_flow_*` 数据模型由平台原迁移机制创建。

权限分别为 `systemSetting.logicflow.read`、`.edit`、`.reload`、`.observation.read`、`.observation.manage`。写入与重载同时要求 read，控制器独立检查可信认证决策；前端按钮不承担鉴权。编辑意味着能修改可执行逻辑，应由应用管理员分配。

默认采用最后成功完整 Context 的 App/投影/JAR 目录。数据库覆盖作为优先级 200 的正式 MetaProvider 参与原选择；未知、不完整、高优先级冲突或非受管执行来源只读。详情列出各来源优先级、禁用/遮挡情况、来源诊断与当前模型校验错误；仅禁用定义可查看但不能作为编辑默认。首期锁定名称、启用状态及流程对外类型/Schema。保存完整定义，最大 2 MB、2000 节点、8000 连线。

保存/撤销/恢复要求 editToken 和 UUID operationId；集合 epoch/revision 与每个名称的墓碑修订防止旧页覆盖。相同操作者和相同内容可重放；operationId 复用冲突返回 409。HTTP 202 仅表示已提交；按操作回执和实例目标/应用代次核对。无内容变化返回 200，但仍记录操作。

每实例独立重建全集候选，短写锁下验证 Context epoch 与原完成态，再一次切换父子流程。已有调用继续原对象。检查失败、过期或等待超时保留旧完成态；不触发完整 Context、不污染全局失败门控。轮询键 `logicflow.runtimeManagement.pollSeconds` 默认 5 秒，范围 1–300；实例状态以 bootId 区分重启，时间戳过期的记录不能当作在线成员。

编辑器由轻量 FormItem 代理按需加载，列表和详情摘要不挂载画布。runtime host 绑定当前运行态目录并关闭开发智能/调试。资产来自 `ouroboros-logicflow-editor-assets`，按构建版本目录部署；升级须保留旧目录，避免旧页稍后加载异步块失败。IDE `publicPath=auto` 构建保留稳定 app.js/app.css 入口，异步块仍有内容哈希。

逐流程运行监控与详细诊断默认关闭，策略通过统一配置的 CAS 与受管策略扩展修改。Analyzer SPI 在原调用入口选择流程，业务线程只更新有界内存，后台每 5 秒采用策略并写 typed data。开关修订与模型版本独立，详细诊断有时限和每 boot 每流程 100 次上限，节点记录仅含时序、结果与异常类型。

操作与故障边界见[运行手册](../../docs/runbooks/logicflow-runtime-manage.md)，核心机制见[生命周期](../../core/ouroboros-logicflow/design/model-lifecycle/overview.md)。行为测试在 `src/test`，跨 JVM 与浏览器验收合同在 [testing 场景](../../testing/integration/feature/lowcode/logicflow-runtime-manage/contract.md)。实现、验证与发布状态见功能地图；未对外部署或发布。

来源、并发、实例协调、界面和观测的长期设计见[运行时管理设计](../../docs/design/cross-cutting/logicflow-runtime-management.md)。
