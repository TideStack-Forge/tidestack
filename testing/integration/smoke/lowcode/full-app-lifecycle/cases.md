# Low-Code Full App Lifecycle Cases

| Case ID | Objective | Preconditions / Input | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- |
| LC-TOPO-001 | 验证双运行时拓扑与夹具状态 | `develop`、`mothership`、`develop-shell`、`runtime-shell` 已声明 | harness | fixture harness 报告能先落 topology/preflight 证据 | fixture harness report |
| LC-APP-001 | 创建低代码应用 | 管理员身份可登录 develop 平台 | e2e | 应用创建成功并可在平台中查看 | browser trace, backend logs |
| LC-MODEL-001 | 创建业务模型 | 已创建目标应用 | e2e | 业务模型保存成功，必要元数据落库/落仓 | browser trace, metadata repo evidence |
| LC-BOOT-001 | 启动或刷新运行时 | 应用与模型已准备 | harness | 运行时启动成功，必要探针通过 | runtime logs, harness report |
| LC-LOGIN-001 | 打开生成应用并登录 | runtime-shell 可访问 | e2e | 应用入口可打开，登录成功 | browser trace, network log |
| LC-CRUD-001 | 验证生成应用基础 CRUD | 至少一个基础模型已生成页面/API | e2e | 新增、编辑、删除链路能被执行并留证据 | browser trace, API log |
| LC-QUERY-001 | 验证生成应用查询条件 | 业务模型已定义过滤器/查询字段 | e2e | 基础查询与复杂过滤形态均可被触发 | browser trace, API log |
| LC-OBS-001 | 验证生成 UI 与业务模型关系可观察 | 生成 UI 可能部分 runtime-only | harness | 能从业务模型、UI schema、运行时生成结果反推出测试入口 | scenario evidence, analysis note |
