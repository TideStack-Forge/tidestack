# 用例

| ID | 分支与前提 | 通过标准 | 证据 |
| --- | --- | --- | --- |
| LF-DBG-STORE-001 | 并发/恢复；两个 Redis 客户端 | 同目标只有一个采集，乱序重试去重，副本重建后顺序可读 | SharedRepositoryIT |
| LF-DBG-STORE-002 | 依赖/丢失；缓存局部驱逐和清空 | 不完整可见，旧租约不可复活，loss 可跨永久缺口 | SharedRepositoryIT |
| LF-DBG-STORE-003 | 装配/正常；平台已装配 Redis CacheManager | 官方 CacheManager SPI 默认入口的占用、CAS、批读、ACK 释放可被另一个 Redis 客户端观察 | SpringBeanCacheManagerRedisIT |
| LF-DBG-MQ-001 | normal/permission；双平台双助手同应用不同 profile | 只采选中助手，两个平台均可控制与恢复，输入输出异常可读 | MultiActiveMqIT |
| LF-DBG-MQ-002 | dependency/recovery；隔离 RabbitMQ 重启 | 中断期间业务照常，重连后补发去重，不阻塞业务线程 | MultiActiveMqIT |
| LF-DBG-MQ-003 | state/transition；助手重启及缓存丢失 | 旧身份不可续租，新会话显式开始，过期停止采集 | MultiActiveMqIT |
| LF-DBG-PRIVATE-001 | permission；双 WebSocket 实例、不同用户 | 默认不同进程标识，私有跨实例通知恰好一次，其他用户和公共 topic 无数据 | RedisDefaultIdentityPrivateDeliveryIT |
| LF-DBG-UI-001 | normal/error；隔离挂载工作台 | 开始、选次、边动画、节点状态和异常堆栈，停止/卸载释放订阅定时器；组件联调录屏可复核三次执行和刷新后恢复 | runtime-debug-workbench integration；runtime-debug.browser 的视频、trace、HTTP 记录与章节；不证明正式主应用接入 |
| LF-DBG-UI-003 | normal/error/recovery；正式主应用、编辑器和测试容器 | 主应用正常导航、正式 AMIS 数组适配、真实私有订阅；进入/退出、选次、节点/异常详情和历史恢复时原画布尺寸、屏幕位置、SVG 变换、滚动及节点屏幕坐标全等；浮动面板沿用原位置与样式；禁用节点编辑，手动导航后退出保留当前视口，恢复编辑拖动，组件验证撤销历史不被污染；两次正常一次异常；完整路径同时流动；节点输入输出/堆栈、停止后历史恢复，无页面异常 | runtime-debug-platform.browser 的实机视频、截图、viewport-stability.json、HTTP 记录、私有提示计数和章节 |
| LF-DBG-CONTAINER-002 | state/recovery；首次启动后重启基座 | 部署元数据软链接仍加载部署扩展，能重新开始采集；内建元数据默认禁用、显式开关有效 | MothershipInitializationTest；正式候选镜像首次启动与重启后的采集证据 |
| LF-DBG-CONTAINER-001 | runtime/recovery；源构建助手注入 TEST_APP | 实际注入可装配与执行，重建身份变化，旧目标被拒绝 | 容器运行日志 |
| LF-DBG-HTTP-001 | invalid/permission；认证 HTTP | 越权拒绝、游标边界、序列化与公开 API 契约一致 | 控制器测试 |
| LF-DBG-PERF-001 | boundary；有界并发与大对象 | 默认关闭开销和开启开销有测量，队列/采集容量受限，采集清理无泄漏 | 性能报告 |
| LF-DBG-COMPAT-001 | normal/state；注入助手后保留原有元数据能力 | 逻辑流、WebAPI、UI、Schema、菜单、权限、模块和配置导出保留来源/优先级/详情，刷新移除已删除项 | MetadataCollectorCompatibilityTest |
| LF-DBG-COMPAT-002 | normal/error；原有命令与新身份隔离共存 | 旧式响应、监听器注销、同步响应和超时仍正确；坏消息和异主广播不执行命令 | DefaultDevHelperCommandServiceGenerationTest、HelperCommandDispatcherGenerationTest |
| LF-DBG-COMPAT-003 | normal/empty/state；应用运行时扩展装配 | 配置默认值、微前端入口刷新、样式合并顺序和空值行为不变 | AppRuntimeMetadataCompatibilityTest |
| LF-DBG-COMPAT-004 | normal/override；正式 WebAPI 导出 | 控制器默认错误/标签/安全约束与方法覆盖可共存，保留成功 Schema、权限 scope、弃用及数值边界 | RequestMappingOpenAPIExtractorTest |
| LF-DBG-COMPAT-005 | state/compatibility；Redis WebSocket 默认进程 UUID | 原有公共广播跨副本只投递一次；共享会话路由保留正确进程身份、健康状态及注销后的可见性 | RedisDefaultIdentityPrivateDeliveryIT |
