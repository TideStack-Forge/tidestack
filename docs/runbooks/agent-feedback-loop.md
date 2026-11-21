# Agent 反馈循环运行手册

## 适用范围

本手册为仓库内的 Agent 提供统一的“观察 → 验证 → 记录 → 恢复”闭环入口。

当前首选目标是本地 `platform/ouroboros-develop` 调试运行时，因为仓库已经存在：

- 本地浏览器 bootstrap 脚本：`scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh`
- 平台侧 E2E 合同：`platform/ouroboros-develop/e2e/README.md`
- 长期测试资产：`docs/rules/testing.md` → `testing/`
- 任务进度与证据：当前任务/PR/Issue；长期风险与债务独立跟踪

## 一、现有入口

| 能力 | 用途与条件 | 正式入口 |
|------|----------|------|
| Agent 能查看应用日志 | 查询当前运行日志 | `scripts/agent-feedback/query-logs.sh`，默认读取 `tmp/playwright-it/backend/logs/backend-live.log` 和前端日志 |
| Agent 能运行测试并查看结果 | 执行选定测试与结果查询 | `scripts/agent-feedback/run-tests.sh`、`scripts/agent-feedback/run-key-journey-e2e.sh` |
| Agent 能查看应用指标（metrics） | develop debug 本地模式提供 Actuator/Prometheus | `platform/ouroboros-develop/pom.xml` 引入 Actuator + Prometheus，`scripts/agent-feedback/query-metrics.sh` |
| 进度与决定 | 保存在当前任务；已采纳结论回写唯一正文 | [文档治理](../rules/documentation.md) |
| 支持会话恢复 | 恢复当前任务上下文 | `scripts/agent-feedback/resume-session.sh`、`docs/runbooks/agent-session-recovery.md` |

## 二、日志闭环

### 结构化日志

`platform/ouroboros-develop` 已增加 `logback-spring.xml`，后端日志默认输出为稳定的 `key=value` 结构，便于 Agent 检索：

- `ts`
- `level`
- `app`
- `pid`
- `thread`
- `logger`
- `traceId`
- `spanId`
- `msg`
- `exception`

这类日志既适合人眼浏览，也适合脚本二次转换为 JSON 行。

### 日志查询命令

```bash
# 默认读取后端 bootstrap 日志，并输出 JSONL
bash scripts/agent-feedback/query-logs.sh

# 查询前端日志
bash scripts/agent-feedback/query-logs.sh --preset frontend --lines 100

# 查询任意日志文件
bash scripts/agent-feedback/query-logs.sh --file /abs/path/to/app.log --lines 200
```

## 三、测试闭环

### 通用测试命令

```bash
# 架构测试（默认推荐给 Agent）
bash scripts/agent-feedback/run-tests.sh --suite architecture

# 根 Maven reactor 测试（不包含前端 npm/Playwright 测试）
bash scripts/agent-feedback/run-tests.sh --suite all

# 单模块测试
bash scripts/agent-feedback/run-tests.sh --suite module --file platform/ouroboros-develop/pom.xml

# 单模块 + 指定测试类
bash scripts/agent-feedback/run-tests.sh --suite module --file platform/ouroboros-develop/pom.xml --selector PlatformDevelopKubernetesRuntimeSmokeTest
```

从仓库根目录执行；`module` 实际只调用 `mvn test --file ...`，不会自动把依赖加入 reactor。需要当前源码依赖时按测试规范使用根 reactor 的 `-pl ... -am`，不能把本地旧 JAR 当成本分支证据。`architecture` 先 test-compile，成功后执行独立 ArchUnit 工程；预编译失败立即停止，脚本退出码与 `result.json` 保留该失败。原始输出见 `command.log`。

所有测试执行都会把原始输出写入 `tmp/agent-feedback/tests/**/command.log`，并写出 `result.json`。

### 关键用户旅程 E2E

```bash
# 低代码全链路：仅规划，不执行浏览器动作
bash scripts/agent-feedback/run-key-journey-e2e.sh --scenario lowcode-full-app-lifecycle --mode plan

# LDAP 登录旅程
bash scripts/agent-feedback/run-key-journey-e2e.sh --scenario auth-ldap-login --mode plan

# 统计报表 runtime 查询/BI 旅程
bash scripts/agent-feedback/run-key-journey-e2e.sh --scenario statistics-reporting-runtime-query-and-bi --mode plan

# 运行某个脚本步骤
bash scripts/agent-feedback/run-key-journey-e2e.sh --scenario lowcode-full-app-lifecycle --mode run --script-id develop-browser-bootstrap-contract
```

### Shell / Topbar 回归验证规则

- Shell、topbar、tabs 这类壳层状态问题，必须沿用户真实路径验证，不能只验证目标页面的预加载 harness。
- 至少覆盖两类路径：进入路径，例如 public shell → app list → app-card open → workspace topbar 切换；绕路返回路径，例如 open app → switch tab/menu → close app → public shell 状态恢复。
- 如果只有“直接加载最终路由”的绿色结果，没有同一会话里的真实 route transition 与 detour/return 证据，就不能把该回归判定为已关闭。

## 四、健康检查与指标

### 启动成功判定标准

对本地 `ouroboros-develop`，Agent 应按以下标准判定“启动成功”：

1. 目标端口可访问
2. `GET /actuator/health` 返回 `status=UP`
3. 响应延迟不超过配置阈值（默认 `3000ms`）
4. 在重试窗口（默认 `120s`）内达成；每次 curl 的最长等待和重试间隔受剩余窗口限制。进程调度和结果写入仍有少量开销，不将该参数视为严格实时截止。

执行命令：

```bash
bash scripts/agent-feedback/check-health.sh --base-url http://127.0.0.1:18080
```

该地址对应浏览器 bootstrap 的默认后端端口；脚本自身默认是 8888。覆盖 bootstrap 端口时同时调整 `--base-url`。

### 指标查询命令

```bash
# 列出所有指标名（按实际后端地址）
bash scripts/agent-feedback/query-metrics.sh --base-url http://127.0.0.1:18080

# 查询单个指标
bash scripts/agent-feedback/query-metrics.sh --base-url http://127.0.0.1:18080 --metric jvm.memory.used

# 查询 Prometheus 暴露文本
bash scripts/agent-feedback/query-metrics.sh --base-url http://127.0.0.1:18080 --endpoint prometheus
```

## 五、任务记录与恢复

计划、进度、决策依据和单次验证归当前任务/PR/Issue；中断前保存固定 HEAD、worktree、未提交差异、已完成检查及待办。长期风险和技术债在对应台账原条目更新，不再新建 exec-plans 目录。

恢复脚本输出近期提交、Git状态和恢复建议，不再探测旧计划目录；任务语义仍从当前任务/PR/Issue及关联证据恢复。

## 六、推荐 Agent 执行顺序

1. `bash scripts/agent-feedback/resume-session.sh`
2. 查看当前任务保存的基线、进度与未决项
3. 如需运行时验证，先 `bash scripts/agent-feedback/check-health.sh`
4. 如需日志诊断，执行 `bash scripts/agent-feedback/query-logs.sh`
5. 如需指标诊断，执行 `bash scripts/agent-feedback/query-metrics.sh`
6. 如需验证回归，执行 `bash scripts/agent-feedback/run-tests.sh` 或 `run-key-journey-e2e.sh`

## 七、注意事项

- 当前 metrics/health 的第一优先级支持对象是 `platform/ouroboros-develop` 的 debug / 本地模式。
- 关键执行证据统一落到 `tmp/agent-feedback/` 或既有 `tmp/testing-evidence/`。
- `AGENTS.md` 保留直接执行纪律和规则导航；本手册维护操作细节。
- 如果任务声明了 worktree 范围，开工前先执行 `git status` 和 `pwd`，并确认待编辑路径确实位于目标 `.worktrees/` 目录内，再开始任何文件修改。
- 按目标依赖环境运行上述入口，执行结果与证据归当前任务或 CI。
