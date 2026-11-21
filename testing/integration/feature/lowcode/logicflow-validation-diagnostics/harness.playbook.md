# Logicflow Validation Diagnostics Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)
2. 运行 fixture harness 计划模式：
   `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action up --mode plan`
3. 确认以下输入已准备：
   - `OB_TEST_BASE_URL`
   - `OB_TEST_FRONTEND_BASE_URL`
   - `SMOKE_PUBLIC_SHELL_URL`
   - `SMOKE_LOGICFLOW_EDITOR_HOST_BASE_URL`（可选，不提供时默认按 `127.0.0.1:19088` 计算）
4. 准备 develop-shell 时，确保注入：
   `DEV_E2E_FRONTEND_EXTRA_ARGS="--logicflow_editor ${SMOKE_LOGICFLOW_EDITOR_HOST_BASE_URL:-http://127.0.0.1:19088}"`

执行前先核对 [当前合同与执行差距](contract.md#当前合同与执行差距)。夹具已按诊断信封检查响应；启动或模型保存成功仍不能替代完整的诊断夹具、正式文件和 UI 验证。

## Execution Order

1. 启动或确认 `develop` runtime 可访问
2. 执行 fixture harness：
   `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action up --mode run`
3. 确认 develop-shell 已使用 `DEV_E2E_FRONTEND_EXTRA_ARGS` 拉起（旧的 `FRONTEND_EXTRA_ARGS` 仅作为兼容口径）
4. 登录 develop 平台，进入 `logicflow-model` 列表
5. 打开 `SMOKE_LOGICFLOW_MODEL_NAME` 对应模型的编辑页
6. 展开编辑页的 diagnostics 入口（当前通常表现为顶部工具条告警条或错误列表入口）
7. 点击首条指向 `SMOKE_EXPECTED_NODE_NAME` 的诊断项，触发“定位到节点”动作
8. 观察属性面板是否聚焦到目标节点

## Observation Points

- 如果 fixture harness 先失败，不要直接下编辑器回归结论
- 如果静态微前端资源 404，先检查 `logicflow-editor-static` fixture 是否启动，以及 develop-shell 是否注入了 `--logicflow_editor`
- 如果 diagnostics 入口展开后没有结构化诊断，先确认请求带 `includeValidationSnapshot=true`，再检查 `.data.validationSnapshot.validationDiagnostics`
- 如果点击定位后无响应，记录：
  - diagnostics 入口是否存在，以及当前以何种形态展示
  - 浏览器 console/network
  - 属性面板当前节点名

## Cleanup

1. 如需关闭静态微前端服务，执行：
   `python3 testing/common/runners/harness/fixture_harness.py --scenario testing/integration/feature/lowcode/logicflow-validation-diagnostics/scenario.yaml --action down --mode run`
2. 清理本轮浏览器会话与 `tmp/testing-evidence/lowcode-logicflow-validation-diagnostics`
3. 若本轮不希望保留 smoke app / model，再到 develop 平台手工删除对应数据
