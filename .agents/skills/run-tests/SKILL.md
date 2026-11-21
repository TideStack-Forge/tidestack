---
name: run-tests
description: 选择并执行 Ouroboros 的模块、架构、前端或跨栈测试，保存当前基线的真实证据；复杂回归或多环境测试时使用。
---

# 执行测试

## 前提

从用户指定 checkout 根目录工作，检查 pwd、Git 状态和 HEAD；测试前读[测试规范](../../../docs/rules/testing.md)及目标模块说明。前端另读[前端规范](../../../frontends/web/TESTING.md)和包 tests/README，系统场景读[测试资产说明](../../../testing/test-asset-management.md)。

## 动作

- [ ] 根据变更和失败行为选择最小有效层级，核对 JDK/Node、数据源、容器、端口及资源归属；缺必要环境只暂停依赖项。
- [ ] 后端优先使用仓库的 `scripts/agent-feedback/run-tests.sh --suite module --file <module>/pom.xml`；架构路径用 `--suite architecture`，其内部包含两阶段编译/检查。先读脚本 help；不重复实现 runner。
- [ ] 前端在所属包运行既有标准命令；系统旅程复用[反馈循环手册](../../../docs/runbooks/agent-feedback-loop.md)和 testing 的正式 runner/场景。plan 模式仅说明计划，不算执行通过。
- [ ] 记录 HEAD、环境、命令、退出码、实际场景与日志/报告；核对真实测试数和前置阶段结果，不能只看聚合脚本最后一条状态。保存任务需要的证据后再清理自有资源。

## 成功与失败

成功需目标行为及分支覆盖通过、无隐藏跳过，相关架构/合同检查满足；测试资产存在不算成功。后端 runner 结果默认在 tmp/agent-feedback/tests，长期证据复制到本次任务或 CI 制品。

失败先保留首个错误和日志，按[故障排查](../../../docs/runbooks/troubleshooting-guide.md)定位；修复后只重跑受影响链。不能重置他人工作、删除共享数据或跳过失败测试来关闭任务。
