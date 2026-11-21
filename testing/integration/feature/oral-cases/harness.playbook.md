# Oral Feature Verification Harness Playbook

## Preflight

1. 先读取 [scenario.yaml](scenario.yaml)、[contract.md](contract.md)、[cases.md](cases.md)
2. 把本轮用户口述或聊天给出的测试点先转写到 `cases.md`
3. 明确本轮依赖哪些 runtime、frontend、identity、data fixture；需要时再补到 `scenario.yaml`
4. 运行 fixture harness `check --mode plan`，确认本轮依赖没有失真

## Execution Order

1. 先确认 topology 和依赖
2. 再验证新功能主链路
3. 再做写操作、权限边界、回查验证
4. 记录哪些是功能缺陷，哪些是资产缺失或环境阻断

## Cleanup

1. 清理本轮临时进程、登录态和测试数据
2. 把新增经验沉淀回 [testing/unified-testing-playbook.md](../../../unified-testing-playbook.md)
3. 判断当前场景是否应晋升为 `smoke` 或 `regression`
