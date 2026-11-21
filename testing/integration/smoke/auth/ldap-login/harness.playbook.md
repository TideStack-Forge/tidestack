# LDAP Login Integration Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)
2. 运行 fixture harness `check --mode plan`，确认 LDAP 服务、runtime、frontend、identity 夹具都已进入同一证据目录
3. 确认 `OB_TEST_BASE_URL`、`OB_TEST_APP_BASE_URL`、`OB_TEST_FRONTEND_BASE_URL`、`OB_TEST_RUNTIME_FRONTEND_BASE_URL` 已注入
4. 如果要拉起本地 LDAP，先检查 Docker 可用，再执行 `services/openldap` 的 `up` 生命周期
5. 确认本轮测试的身份路径：
   - 已映射成功用户
   - 首次绑定用户
   - 权限边界用户

## Execution Order

1. 先拉起或检查 LDAP 服务 fixture
2. 再检查 backend runtime 和 provider readiness
3. 打开 develop-shell 登录入口，验证登录页和 provider 状态
4. 执行 LDAP 登录成功路径
5. 执行首次绑定路径
6. 执行权限边界路径
7. 如需验证运行态，再切到 runtime-shell / mothership

## Observation Points

- 看 fixture harness 报告是否先把依赖和入口状态落证据
- 看 backend 日志里 LDAP provider 初始化是否报错
- 看登录页 console/network 是否存在代理或壳加载错误
- 看首次绑定路径是否真的进入绑定 UI / API 分支
- 看权限不足是否被单独归类

## Cleanup

1. 停掉 LDAP 服务 fixture 或回收测试容器
2. 清理登录态、临时 token、浏览器会话
3. 清理不再需要的后台进程
4. 归档证据并把新增经验教训回填到统一 playbook
