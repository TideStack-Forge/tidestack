# 逻辑流在线调试通信

逻辑流在线调试的机制、身份、MQ 多活、共享仓储和状态机统一维护在[逻辑流在线调试设计](../../../../core/ouroboros-logicflow/design/online-debugging/overview.md)。实现入口、限制和目标环境验证要求以该正文为准，部署验收由具体交付任务取证。

本通信路径复用开发助手请求响应与 MQBridge，按 appName/profile 发现后绑定具体助手进程；事件经共享仓储接收，再由用户私有 WebSocket 提示和 REST 恢复供画布回放。旧公共 topic 推送完整 payload、本机 session FIFO 和常驻采集描述已被替换，不能作为当前部署依据。

执行前查看[运行手册](../../../runbooks/logicflow-online-debugging.md)和[正式测试场景](../../../../testing/integration/feature/lowcode/logicflow-online-debugging/scenario.yaml)。文件或测试存在不表示发布门禁已经通过。
