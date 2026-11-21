# 模型实时协同

业务模型关系画布作为首个接入点，使用 Yjs 13.6.33 合并并发修改，通过基座已有 STOMP 连接同步，使用应用 VCS 保存协同文档。全局总览和每个切面分别拥有一份文件。

## 职责与接入位置

| 职责 | 实现 |
| --- | --- |
| CRDT 文档、事务、待同步队列与恢复 | shared AMIS runtime 的 `collaboration/YjsWorkspace.ts` |
| 基座实时连接、订阅补偿与 HTTP 操作回执 | `collaboration/platformTransport.ts` |
| 画布对象与协同数据的映射 | `renderers/business-model-collaboration.ts` |
| 文档持久化与检查点压缩 | dev-app-management 的 `CollaborativeDocumentService` |
| 查看和编辑权限、实时广播及订阅生命周期 | `CollaborationController`、`CollaborationGateway` |
| 模型模块的存储位置和初始数据校验 | common-dev 的 `CollaborativeResource`，通过 Spring bean 接入 |

`BusinessModelCanvasService` 注册 `business-model-canvas` 文档类型，沿用 `business-model/canvases/{id}.canvas.json`。后续类型通过同一资源接口和前端文档映射接入。

## 操作边界

一次节点拖拽在松手时成为一个协同操作。拖拽期间显示本地预览。整组移动中涉及的模块框、子模块框和节点坐标在同一 Yjs transaction 中修改。取消拖拽和模块框重叠回退只取消本地预览。

节点的 x、y 作为一个值存入 Y.Map，避免并发修改产生不同操作的坐标组合。模型选择按模型标识分别存储，布局按模式和实体标识分别存储。提交差异基于操作开始时的对象计算，能够保留其他开发者对无关实体的更新。

多人同时调整同一个实体时，由 Yjs 的冲突规则决定最终值，所有客户端收敛。成员增减和树变化后的分组归一化继续使用既有 `syncModelCanvas`。分组显示开关、当前切面、搜索、选中项、缩放和平移属于当前终端的浏览状态。

画布操作自动提交。界面展示待同步操作数和同步错误，失败操作保留在当前会话中，可重新同步。关闭浏览器或通过基座关闭页签时，待同步操作会触发离开确认。浏览器关闭后的离线草稿恢复尚未接入。

## 实时通信与访问控制

实时订阅复用 `subscribeWithInitial`，沿用首次补偿、重连新周期和订阅清理机制。每个应用及文档类型的 live destination 为 `/user/queue/app/{appName}/collaboration/{type}`。首次补偿返回当前文档与编辑权限，后续广播返回已持久化的文档状态。

操作通过 HTTP 提交并取得持久化回执。提交成功后广播给当前订阅且具有查看权限的用户。发送失败保留已经持久化的结果，客户端通过重连补偿或定期重新同步追平。

WebSocket 的正式入站拦截链增加 `StompDestinationAuthorizer` 扩展点。协同模块在订阅和补偿请求进入 broker 前校验应用查看权限，阻止客户端直接向协同广播目的地写入。HTTP 修改入口校验应用编辑权限。广播时再次校验查看权限，退订或会话断开时移除订阅记录。

## 持久化协议

每份画布文件首次接入时，从原有画布 JSON 迁移为 `ouroboros-yjs-v1` 格式，保存原始 seed、Yjs checkpoint、后续 updates 和单调递增 revision。seed 用于初始化与迁移，当前状态由 Yjs checkpoint 和 updates 恢复。

客户端同时提交增量和完整检查点。每次检查点由已确认的服务端状态与当前队首操作生成，后续排队操作在各自提交时持久化。请求的 baseRevision 与当前已保存 revision 相等时，检查点覆盖的历史前缀可以压缩。并发请求基于旧版本时，服务端保留当前检查点并追加增量，两个操作均可成功。重复增量由 Yjs 幂等合并。

应用 VCS 批量事务负责写入与提交失败回滚。写入前再次检查文件内容，避免其他存储实例写入后被覆盖。画布编辑期间不持有用户编辑锁，短时存储事务沿用 VCS 的原子写入机制。

| HTTP 操作 | 用途 |
| --- | --- |
| `GET /api/dev/{app}/collaboration/{type}` | 获取文档快照与当前编辑权限 |
| `POST /api/dev/{app}/collaboration/{type}/{id}/initialize` | 首次初始化，已有协同状态时返回已有状态 |
| `POST /api/dev/{app}/collaboration/{type}/{id}/updates` | 提交原子增量并返回已保存的检查点和日志 |

原画布整文件保存接口由以上协同入口接替，防止整文件覆盖和协同更新同时成为写入来源。

## 后续业务模型编辑接入

前端映射需要按稳定标识组织属性、关系、视图、动作、事件和访问权限，文本内容可以使用 Y.Text。编辑动作通过事务表达，避免把整个模型 JSON 作为一个冲突值。

后端当前保存不透明的 Yjs 二进制数据，只校验初始数据、操作标识、版本和编码。业务模型接入时，还需要将协同草稿物化为模型定义，再通过现有校验、投射和版本提交机制发布。现有业务模型编辑器的锁和提交流程在其协同适配完成后演进。

## 当前边界与演进

当前协同试点接入业务模型关系画布。Yjs 在前端运行，Java 后端负责权限、持久化和广播。模型定义编辑器仍通过自身的校验、投射及发布机制维护定义，接入协同需要完成前述文档映射和草稿物化。

当前试点以单开发工作区和 SimpleBroker 为验证环境，订阅登记保存在服务实例内。Redis、RabbitMQ 的跨实例订阅广播与共享 VCS 存储竞争还需接入和部署验证。Java 端尚未运行 Yjs 解码器，复杂业务模型的服务端语义校验需在后续接入时补齐。

浏览器关闭后的未同步操作恢复尚未接入，当前只保留会话内待同步队列。页面离开确认、重试和重连补偿已存在。真实部署下的多人交互、权限变化与页面生命周期还需完整环境验收。这些差距由 [COLLAB-001](../../tech-debt-tracker.md#collab-001-模型画布协同的交付边界)持续维护。

## 验证方式与回退

| 机制 | 可重复执行的验证入口 |
| --- | --- |
| 并发、乱序、重复消息、坐标原子性及整组事务 | [前端协同行为测试](../../../frontends/web/main-app/tests/unit/src/components/amis/renderers/business-model-collaboration.test.js) |
| 首次补偿、重连订阅与清理 | [前端传输测试](../../../frontends/web/main-app/tests/unit/src/components/amis/renderers/platform-collaboration-transport.test.js) |
| 旧画布迁移、并发提交、幂等及 VCS 恢复 | [文档持久化测试](../../../dev-module/ouroboros-dev-app-management/src/test/java/com/ouroboros/dev/app/collaboration/CollaborativeDocumentServiceTest.java) |
| 订阅权限、广播、断开及真实 STOMP 补偿 | [网关测试](../../../dev-module/ouroboros-dev-app-management/src/test/java/com/ouroboros/dev/app/collaboration/CollaborationGatewayTest.java)、[实时集成测试](../../../dev-module/ouroboros-dev-app-management/src/test/java/com/ouroboros/dev/app/collaboration/CollaborationRealtimeTest.java) |
| 完整用户流程 | 在真实部署中使用至少两个用户验证不同节点并发拖动、同节点冲突收敛、切面成员同步、只读拒绝、断线恢复及关闭后重开。具体运行结果归任务或 CI 附件。 |

回退需要同时恢复实现和画布文件。原始画布 JSON 可以从应用 VCS 的迁移前版本恢复，这会放弃迁移后的协同修改，应在回退前保留需要的协同状态。

参考 [Yjs 文档更新接口](https://docs.yjs.dev/api/document-updates) 和 [STOMP 补偿订阅设计](stomp-bootstrap-compensation-subscriptions.md)。
