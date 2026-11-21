# Harness Runners

这里放 AI agent / 人工辅助执行所需的 runner 和编排逻辑。

## 当前能力

当前提供通用 fixture harness runner：

- 入口：`testing/common/runners/harness/fixture_harness.py`
- 支持动作：`up`、`check`、`reset`、`down`
- 支持模式：`plan`、`run`

它的职责不是替代具体场景 playbook，而是把这些共性能力统一起来：

- 读取场景引用的 fixture
- 先跑 fixture 预检
- 生成生命周期动作计划
- 只在 fixture 明确声明可执行 lifecycle command 时顺序执行
- 产出标准化 harness 证据

## Lifecycle 语义

fixture 的 lifecycle 字段需要区分“描述”和“命令”：

- 纯文本字符串：表示 harness/manual 描述，不会被自动执行
- `shell: <command>`：表示可执行 shell 命令
- `{ "shell": "...", "description": "..." }`：表示显式命令对象

这条规则是为了兼容过渡期 fixture。很多现有 fixture 还只有检查语义描述，runner 不能把这些描述误当成 shell 去执行。

## 证据

每次执行会生成：

- `fixture-harness-<action>.json`

其中至少包含：

- fixture preflight 结果
- 动作计划
- 实际执行结果
- 汇总统计

## 关系边界

- `testing/integration/**/harness.playbook.md` 仍然定义“这个场景怎么测”
- `fixture_harness.py` 只负责“如何统一编排 fixture 动作”
- 具体业务断言、页面操作、接口判定仍属于场景 runner 或场景 playbook
