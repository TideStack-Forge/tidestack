# 逻辑流在线调试运行手册

当前分支已实现，本地正式平台与容器重启后的验收通过，尚未生产部署。分段验证、正式平台验收、真实 Kubernetes 集群的边界，以及机制与容量见[在线调试设计](../../core/ouroboros-logicflow/design/online-debugging/overview.md)；以下为目标环境的部署和接手步骤。

## 装配与启用

开发平台装配 logicflow-dev、dev-app-management、现有开发助手 MQ 及 WebSocket 模块。TEST_APP 工作区沿正式构建路径把 debug-support 拷贝到 extend，由既有扩展加载机制加载助手；不使用运行时 attach Java agent。

开发支持包必须来自同一构建版本，包含 `ouroboros-logicflow-debug-helper.jar`、`ouroboros-dev-helper-core.jar`、`ouroboros-dev-helper-runtime.jar`、`ouroboros-common-dev.jar`。旧测试容器需重建，确认注入 `OUROBOROS_DEV_HELPER_TEST_APP=true`、新的 `OUROBOROS_APP_INCARNATION_ID` 和平台写入的 `OUROBOROS_DEBUG_OWNER`；用户传入 env 不能覆盖这三者的真实来源。

基座镜像须包含本分支的 `init.sh` 修正：指向 `/app/deploy/metadata` 的软链接在重启后仍属于部署元数据，不能被误判为内建元数据而禁用扩展包。首次启动和重启后均应在 JVM 的 loader.path 中看到 `/app/deploy/extend`，并能重新开始采集；显式 `ENABLE_DEPLOY_EXTEND=false` 仍会禁用部署扩展。

单机可用本机缓存。多活平台必须使用同一共享 CacheManager 后端、命名空间和序列化，并设置：

```yaml
ouroboros:
  logicflow:
    debug:
      cache-region: logicflow-debug
      require-shared-cache: true
```

该开关对默认缓存仓储生效，不自动把本机缓存变成共享缓存。不支持原子比较交换或未装配共享缓存时应拒绝采集。私有通知使用已配置的 Redis/Rabbit WebSocket broker；simple broker 不提供跨实例推送。各平台进程默认使用不同的实例 UUID，不能手工给多副本配置相同消息身份。

MQ 保持固定 command/response/debug 三个目的地。命令及响应为每进程广播，debug ingest 使用固定具名消费组。更换 MQ binder 必须验证对应广播、竞争消费和失败重试合同；业务代码不依赖 Rabbit 路由专用特性。

Kubernetes 必须能实时取得唯一可调试 Pod 的 UID，并通过 Downward API 给助手注入相同值；存在多个候选 Pod 时当前版本拒绝探测。不要以 Deployment 名称代替具体 Pod 身份。

## 使用与观察

1. 以具有应用编辑权限的用户打开逻辑流，进入“在线调试”。左侧节点库切换为采集与执行面板，顶部原位置的工具栏切换为调试操作，右侧用于节点详情；沿用原面板样式。原画布保持尺寸、位置、缩放和滚动，仅锁定编辑；退出时恢复编辑面板与交互。
2. 选择本人运行中的测试容器，点击“开始记录”（待记录 / 记录中 / 已停止记录）。按钮提示为“记录测试容器中接下来发生的执行”。通过真实测试应用入口触发两次正常和一次异常执行。
3. 确认左侧执行列表持续更新；选择一次执行后，原画布中所有实际流经连线同时流动、原节点直接显示最终状态。切换执行不会居中、缩放或重新布局；如需调整视野，使用原小地图的缩放或“适配画布”。点击节点在右侧查看输入/输出；异常节点默认显示根因摘要和可滚动堆栈，可切换数据或展开详情。“事件时间线”支持定位，工具栏的“逐事件检查”支持播放和单步。暂停流动只影响动画，不改变业务执行或路径高亮。
4. 点击“停止记录”，停止接纳新执行，已有结果仍可查看；等待排空确认。离开页面将停止续租，助手按租约到期停止。关闭或过期后未完成的执行标记为不完整。

采集时拓扑与当前设计不一致会暂停画布标注，执行数据仍可查看；其他流程的调用需在对应逻辑流查看。拓扑相同不代表未保存的脚本/配置与容器版本完全一致，应核对部署版本。

历史是临时缓存，当前默认从创建起最多保留 40 分钟。单轮 payload 预算 64 MiB，应用按整轮预留最多保留 4 轮、平台最多 16 轮；不因停止就立即释放历史预留。后端额外内存用于键、索引和对象开销，不把 1 GiB payload 上限等同实际内存峰值。

## 排查与恢复

| 现象 | 判断与恢复 |
| --- | --- |
| 没有容器/标注需更新助手 | 核对登录原始身份、profile、创建者、TEST_APP 状态和四个支持 JAR；旧实例重建后重新选择 |
| 助手未响应 | 核对固定通道连接、广播订阅和容器 incarnation；不要放宽身份校验或重用旧 UUID |
| 某副本能用、另一副本历史为空 | 核对共享缓存区域/后端；多活 require-shared-cache 必须开启，不能开本地兜底 |
| 实时提示延迟 | 工作台仍每 2 秒 REST 补偿；检查 WebSocket broker 的私有路由和进程实例 UUID |
| SATURATED | 达到采集或共享预算；停止采集并查看缺失标记，等待保留期释放或换用已评估容量的仓储实现 |
| INCOMPLETE / INCOMPLETE_OR_EXPIRED | 存在事件或运行图缺口，不推断业务失败；若窗口仍在，助手重传后可补齐，否则保留明确缺失 |
| 缓存清空/重启 | 旧控制失效，助手租约到期停止；重新开始新采集，不能根据迟到事件复活旧 capture |
| 容器或助手重建 | 当次助手身份改变，旧会话不续租；在新目标上显式开始 |
| MQ 中断 | 业务继续，有界重传；恢复后在原租约/排空窗口内补发。超过窗口可能不完整，不承诺无限离线零丢失 |

快照不执行任意业务 getter/toString。大对象会截断或显示不支持类型；键名脱敏不能识别所有任意字符串或异常消息中的秘密，应使用适合调试的测试数据。

## 验证与替换仓储

[正式场景](../../testing/integration/feature/lowcode/logicflow-online-debugging/scenario.yaml)及其用例表定义多活、故障、隔离、容器、HTTP、回放与性能验收。以实际执行日志和证据报告判断结果，不使用历史成功代替当前验证。

替换默认仓储时，提供唯一 `LogicFlowDebugEventStore` Bean；默认 Bean 会退让。先停止并排空采集，再统一切换全部副本。数据库实现将来按相同合同接入 ouroboros-data / QueryDSL；本期无数据库表、无双写、无缓存故障自动切数据库。旧缓存历史不承诺迁移。

回退版本前停止新采集并等待助手租约结束，统一回退平台、支持 JAR 和编辑器；旧容器重建。新旧 v2 控制记录不可作为可跨版本恢复的持久化合同。
