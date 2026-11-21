# 通知运行时管理实施验收记录

日期：2026-11-15。工作分支 `feat/notification-define-runtime-manage`，工作区 `.worktrees/notification-define-runtime-manage`。原始功能基线为本地 `dev-next` 的 `7f9b5b381f9ba1632623299d5ce6172364c5578c`；随后按用户选择 rebase 到本地 `dev-next` 的 `0e6b96ce289fda3ad851efde6533c1c8469cb62b` 并完成下述组合复验。本地采用快进集成，实际提交与合并 HEAD 以 Git 记录为准；未向远端推送或发布。

本地功能及完整应用集成验收通过；全仓检查仍有下述基线失败。本记录覆盖正式 Mothership、生产构建主应用和真实用户名密码登录，不代表外部身份提供方、生产数据迁移或生产部署已经通过。用户明确排除实际供应商外发送达验证。

## 本地 dev-next 集成复验

新基线已扩展统一配置策略接口和批处理实现。通知凭据沿原接口返回 `ConfigurationPreparedWrite`，审计人来自真实平台身份，无跨键写入依赖；测试验证传入 actor 不能冒充真实操作人。撤销分支适配原 `for` 循环，继续处理后续 key，补充同批撤销空默认及非空默认两项的行为测试。保留已有个人消息模块，两个管理入口归入“系统设置 → 基础配置”。没有新增配置策略注册表、存储或租户机制。

| 范围 | rebase 后结果 |
| --- | --- |
| 通知与配置 Java | 306 项通过（通知 212、配置 94），0 failure/error/skip |
| app-runtime | 90 项通过，0 failure/error/skip |
| 统一配置相交组件 | OIDC/DCR 定向 26 项通过，含管理策略、受限注册、依赖与失败边界 |
| 前端相关单测 | 12 个文件、89 项通过，包含编辑状态、页签、路由与登录后导航；其中两个直接关联文件的 37 项为子集，不重复累加 |
| 生产构建 | 正式 Mothership 及依赖 package/classpath 构建成功；主应用生产构建成功，仅有 AMIS CSS 的 autoprefixer 警告 |
| 浏览器组合 | 5 个 Chromium 场景全部通过，生产 AMIS schema + 真实 Controller/用例/MySQL |
| 正式渠道组合 | 7 渠道、32 项行为检查和 6 个正式凭据策略匿名拒绝检查通过 |
| 完整应用 | 重新构建的正式 Mothership/主应用、独立 MySQL/Redis；四阶段均退出 0，7 个稳定 Case ID 全部通过 |
| 架构 | 全仓 test-compile 后，原架构检查 62 项通过；未修改门禁或排除项 |
| 文档 | 本次 10 个新增/修改 Markdown 文件无断链；全仓 55 处失败来自相对新基线未变的 7 个文档，包含缺失的本地临时证据链接；锚点目标亦未变 |

完整应用复验再次验证真实登录和角色隔离、原配置批量保存、旧表结构样本迁移、双 JVM 共享 Redis、V1→V2→V3 默认升级。最终只读 SQL 确认验收模板及 host/授权码覆盖均为 0。全仓文档失败不作为本功能阻塞，也不能称全仓链接检查通过。其他全量检查的历史失败见后文，未将原基线失败自动认定为新基线结果。

合并已有真实执行数据与新基线定向回归、正式渠道组合的 JaCoCo 数据后，对新基线四模块各自全部生产 class 重新执行原 BUNDLE / LINE / 0.70 门禁，均通过，无 class 数据不匹配警告。结果为 configuration-core 249/272（91.5%）、configuration-runtime 1123/1297（86.6%）、notification-core 172/200（86.0%）、notification-runtime 1510/1620（93.2%）。这是组合执行数据的覆盖率，不宣称本轮单独测试或全仓 verify 的覆盖率。

新增证据以 `rebase-*` 命名保存在原本地证据目录；完整应用七项见 `rebase-full-app/summary.json`，保留原始基线证据以区分两次结果。

## 实现边界

- 模板沿原 `NotificationTemplateCenter`、Provider SPI 和原模板表实现完整覆盖；数据库 -20、文件 -10、JAR 0。撤销条件删除覆盖，数据库故障向上传播。
- 新管理用例统一校验、默认/有效投影、事务和 CAS；原持久化服务只访问原模型。旧 HTTP 写路由收敛到同一用例，不接受无版本的旧 CRUD 请求。
- 渠道描述由原 Supplier 与适配器 Java 类型产生。原配置工作台保存连接参数；通知配置通过元数据选择撤销覆盖语义，其他组件的恢复规则保留。
- 正式 AMIS 列表、查看、编辑和渠道目录连同 menu、authority、app-modules 已注册。标识和载荷契约只读，历史数据库独有模板只读。

## 原始功能基线验证

| 范围 | 结果 | 证明内容 |
| --- | --- | --- |
| 通知与配置 Java 测试 | 298 项通过，0 failure/error/skip | 最终 Surefire 报告中通知 212 项、配置 86 项；修正后按受影响套件补跑，不累加重复执行次数 |
| 正式渠道组合 | 7 个渠道、32 项行为检查通过 | 真实 SPI 和适配器类型；合法定义、必填、空白、未知字段及语音范围；不调用供应商 |
| 凭据策略装配 | 6 个已注册策略通过 | 从正式 AutoConfiguration imports 找到策略 Bean 工厂，验证所属凭据 key 和匿名读取拒绝；管理脱敏和授权行为另有单测 |
| 数据库 | H2 与隔离 MySQL 8.3 通过 | 原 TypedDataModel/QueryDSL、物理唯一约束、首次插入竞争、版本条件写/删、回滚、历史回填及冲突中止 |
| 多实例配置 | 两个 Spring context、同一隔离 Redis 7 通过 | 两端预热缓存后，保存和撤销均可读到新 revision；未使用进程内广播替代共享缓存 |
| 前端相关单测 | 10 个文件、77 项通过 | 编辑状态、空标题、未知提交结果、冲突保留、页签离开保护及路由行为 |
| 浏览器组合 | 5 个 Chromium 场景通过 | 生产 AMIS schema + 真实 Controller/用例/MySQL：检查不写、保存、409、显式采用最新、撤销、待核对、列表筛选、渠道目录、换模板重载、原配置工作台凭据闭环 |
| 完整应用 | 7 个稳定 Case ID、四阶段全部通过 | 正式 Mothership + 构建主应用、真实密码登录与 RBAC、菜单与直达页、凭据替换脱敏、旧表样本迁移、双 JVM、V1→V2→V3；不拦截产品 HTTP |
| 应用运行时回归 | 90 项通过，0 failure/error/skip | 注册原 HTTP 状态映射 Advice 后运行完整 app-runtime 模块测试；完整应用中的拒绝与过期写入均实际返回 HTTP 409 |
| 页面复核 | 桌面与 390px 窄屏 | 内容复制到真实剪贴板；窄屏差异上下排列，正文作为文本显示；查看页说明区不悬浮遮挡内容 |
| 架构检查 | 62 项通过 | 全仓 test-compile 后运行原 architecture-tests；未修改门禁、allowlist 或排除规则 |
| 元数据与差异检查 | 通过 | 21 个变更 JSON 可解析；git diff --check 通过 |
| 定向 ESLint | 0 error | 新页面、状态模型和浏览器用例；原路由/页签文件仍有既有格式警告 |

## 完整应用补充验收

使用当前 worktree 的正式 Mothership 与 `main-app` 生产构建，独立 MySQL 8.3、Redis 7，节点 A/B 为独立 JVM，并分别真实登录。`initial`、`after-restart`、`upgrade`、`follow-default` 四阶段退出码均为 0；七个 Case ID 按标识去重，不累加失败尝试中的中途断言。详细合同及可复验步骤见[完整应用合同](../../../testing/integration/regression/notification/runtime-management/contract.md)和[运行手册](../../../testing/integration/regression/notification/runtime-management/harness.playbook.md)。

旧表样本在停机和备份后，移除本次新增列及唯一索引，再由正式模型生命周期升级。迁移实际补齐七项结构变更，并回填名称键及 revision；重启保留既有覆盖，数据库独有历史模板保持只读。该结果证明旧表结构样本的兼容性，没有运行旧二进制或处理生产数据。

双 JVM 共享同一专用数据库与 Redis。节点 B 预热配置缓存后能读到 A 的参数更新、模板覆盖及参数撤销。随后停止两个节点并修改文件默认：V2 保留完整覆盖、标示默认变化、拒绝 V1 版本写入；撤销后有效模板为 V2；再部署 V3 时，模板与渠道 host 均继续跟随文件默认，原表无相应覆盖。

完整应用验收发现并修正两处装配边界：四个页面通过原 `hasAuth` 公式阻止未授权渲染及数据请求；应用运行时通过 `@Import` 注册已有的 `WebApiResultHttpStatusAdvice`，使正式应用沿统一规则返回 HTTP 409/503。组件测试原本手工装配该 Advice，不能证明正式应用已注册。没有添加通知专用状态映射。修正后重跑完整应用四阶段、五个浏览器场景、90 项 app-runtime 测试和两阶段架构检查，均通过。

微信可选嵌套对象、极光 SDK 计算 getter、邮件空选项在正式渠道组合中发现问题后已修正。结构检查先按原 Java 类型绑定，再校验本次提交字段，既保留数值转换，也不把计算输出当作输入。可选对象、枚举允许 null，空选项类型可绑定为 `{}`。未知输入仍会拒绝。

原 Map 存储将空载荷契约读为 `{}`，原 Dispatcher 对 null/空 Map 都视为没有约束。管理投影对空契约采用 `{}`，避免只改标题就显示契约变化；原 Provider 对象不被修改，也没有补造载荷字段。页面仅提交实际改动的可编辑字段，后端仍保存完整快照。

## 原始功能基线覆盖率口径

使用 JaCoCo 0.8.15 合并通知/配置各测试进程与正式渠道组合的真实执行数据，再对各模块自己的全部生产 class 执行原 `jacoco:check@check`，保留 BUNDLE / LINE / 0.70 门槛。合并用于纳入运行时测试对 core 依赖的实际覆盖；不以某个高覆盖包代替整个模块。

| 模块 | 已覆盖/总行数 | 行覆盖率 | 原门禁 |
| --- | --- | --- | --- |
| notification-core | 171/198 | 86.4% | 通过 |
| notification-runtime | 1510/1620 | 93.2% | 通过 |
| configuration-core | 248/270 | 91.9% | 通过 |
| configuration-runtime | 1085/1248 | 86.9% | 通过 |

补充统计：本次修改/新增的 Java 可执行行 598/637，93.9%；其中渠道模块变更行 21/21。该统计不能替代模块门禁，也不能把渠道旧实现或全仓未达标的整包覆盖率称为已通过。

## 原始功能基线的全仓失败

以下结果已使用 `git archive HEAD` 的独立副本复现，副本位于本任务 worktree 的临时目录；没有通过删除断言、降低门槛、修改既有实现或增加排除项使其通过。

| 检查 | 基线问题 | 对本次结论的限制 |
| --- | --- | --- |
| Maven 全量 verify | common-app 未达原 70% 行覆盖率门槛 | 不能宣称全量 verify 通过 |
| Maven 全量 test | `DefaultBusinessModelEditorSchemaGeneratorTest.shouldExposeDynamicActionEventAndSubscriptionEditors:105` 失败 | 本次采用通知/配置实际测试类清单执行定向 reactor 回归 |
| 前端全量单测 | user-menu-password-dialog、modal-schema-filter-runtime 导入时缺少 document；css-variable-contract-parity 的既有清单断言失败 | 相关功能 77 项通过，不代表全量前端单测通过；并行运行的另外四项超时单 worker 重跑已通过 |
| 前端 tsc | 当前和未修改 HEAD 均有同样的 37 条 TS 诊断（忽略行号后逐项一致） | 本次页面/状态/页签无新增诊断，不能宣称前端全量类型检查通过 |
| 文档链接 | 全仓检查发现 6 处既有断链或锚点；逐项核对来源及目标与固定基线一致 | 本次新增/修改文档无新增断链，全仓文档检查仍未全绿 |

## 复验入口与证据

测试按[场景清单](../../../testing/integration/regression/notification/runtime-management/cases.md)组织。正式渠道组合在 Maven 测试建立 classpath 后执行：

```sh
python3 testing/integration/regression/notification/runtime-management/run-channel-scenario.py
```

原始日志、Surefire XML、合并前/后的 JaCoCo 数据、各模块覆盖率 XML、变更行统计和截图保存在 worktree 的 `tmp/testing-evidence/notification-runtime-management/`。该目录是本地证据，不作为生产资源发布；复验需要重新建立场景清单中的隔离数据库、Redis 和 loopback 浏览器 fixture。

## 部署前提与未覆盖范围

同一应用多实例必须使用原 Spring CacheManager 的共享区域，并部署一致的默认定义；默认本地缓存不具备集群可见性。独立应用使用隔离存储；本次未实现租户机制或通知专用失效通道。

升级前应备份原表、暂停旧管理写入，在维护窗口运行原模型迁移和历史冲突预检；发现重复记录时中止并人工决定，不能自动删除。新旧客户端不能混用管理写协议。步骤见[使用与升级指南](runtime-management-guide.md)。

组件浏览器 fixture 的身份由夹具提供；完整应用层另已验证正式 shell 与真实用户名密码认证。外部身份提供方、生产历史数据迁移及生产部署未执行；实际供应商外发送达由用户排除。本场景没有真实渠道凭据、通知发送或生产写入。启动时未提供 MQ 夹具，不能据此声明 MQ 通道已经验证。
