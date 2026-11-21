# Low-Code Full App Lifecycle Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)
2. 运行 fixture harness `check --mode plan`，先确认 develop/mothership/frontend/app-root 这些夹具都进入同一证据目录
3. 确认 `OB_TEST_BASE_URL`、`OB_TEST_APP_BASE_URL`、`OB_TEST_FRONTEND_BASE_URL`、`OB_TEST_RUNTIME_FRONTEND_BASE_URL` 已注入
4. 确认 app-root 指向真实元数据仓，而不是过期快照
5. 明确本轮要验证的是：
   - 纯 topology 诊断
   - 稳定 happy path
   - 生成 CRUD/查询验证

## Execution Order

1. 先验证 develop runtime 和 develop-shell
2. 在开发平台创建应用
3. 创建业务模型与必要页面/菜单入口
4. 启动或刷新 runtime / mothership
5. 打开 runtime-shell 并登录
6. 验证生成应用的基础 CRUD 与查询
7. 如果生成 UI 是 runtime-only，再结合业务模型与运行时结果补充观察证据

## Observation Points

- 看 fixture harness 报告是否先确认 topology
- 看 develop 侧应用创建和模型创建接口是否成功
- 看 mothership 日志是否存在运行时装配错误
- 看生成应用页面是否能真实提交，而不是只打开列表
- 看查询条件是否来自业务模型/过滤器，而不是人工猜参数

## Cleanup

1. 清理本轮拉起的不必要后台进程
2. 清理运行时临时目录、token、浏览器会话
3. 归档证据并记录本轮低开链路经验教训
