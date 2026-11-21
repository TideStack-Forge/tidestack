# Runners

`testing/common/runners/` 存放统一 runner 和桥接器，实现“如何执行”，但不承载业务场景真相。

## 职责

- 读取 `testing/integration/**` 中的集成场景定义
- 读取 `testing/e2e/**` 中的自动化 manifest
- 编排 `testing/common/fixtures` 中的夹具
- 在真正执行前产出标准化 fixture 预检结果
- 调用现有自动化实现或兼容旧脚本
- 生成标准化证据和报告出口

## 过渡期规则

现阶段允许 runner 做“桥接”：

- 调旧的 `testing/common/runners/api/data_api_regression`
- 调模块内 Playwright
- 调平台已有 e2e 合同或启动脚本

但这些旧位置不应继续成为长期事实来源。

## Fixture 预检

统一 runner 在委派旧脚本前，至少应完成一轮 fixture 预检：

- 校验 fixture manifest 结构
- 校验 data fixture 暴露的关键路径是否真实存在
- 校验 identity fixture 的内嵌凭据形态是否完整，或明确记录为外部注入
- 校验 runtime fixture 的 `baseUrlEnv`、探针路径和解析后的 base URL 形态

预检结果应作为标准化证据落到当前 run 的 `fixture-preflight.json`，这样后续分析时能先判断“场景输入是否失真”，再判断业务回归。

## 目标

长期目标是让一个场景能通过统一 runner 被发现、拉起、执行和报告，而不是靠操作者记忆到处找脚本。
