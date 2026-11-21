# 自动化参数示例

这个目录保存的是“带有环境结构形态”的示例参数文件。它们可以直接和 prompt 模版一起交给 AI agent 使用。

推荐用法：

1. 选择一份最接近你目标自动化的 JSON 示例。
2. 替换其中与你环境相关的值，例如 GitLab 地址、项目路径、worktree 根目录、标签策略、表格标识和 secret reference。
3. 把这份 JSON 文件和相关的 prompt markdown 文件一起交给 AI agent。
4. 让 agent 按参数替换占位符，并返回最终 prompt bundle，但不要改动源模版。

这些 JSON 是实例参数片段，不是完整的运行配置：调度在自动化系统配置；运行前还需按共同 schema 补齐 dry_run、限额、审计、稳定记录键与指纹规则，并将 ./path/to/module/pom.xml 和 TargetedTest 替换为真实验证入口。

这些文件只是示例。真实密钥不要放进仓库，只保留 `vault://...` 这类 secret reference。

## 先从哪份示例开始

- `intake-customer-cn.json`
  - 适合“一张腾讯文档表格对应一个 intake + writeback 自动化实例”
- `coding-dispatcher-monorepo.json`
  - 适合“一个 GitLab issue 扫描与派发自动化实例”
- `coding-worker-default.json`
  - 适合“coding dispatcher 选中单个 issue 后，给 clean-context worker 使用的 prompt 参数”

## 怎样修改 JSON

只修改那些与你环境和策略相关的值：

- GitLab 主机与项目
- source identity 与 sheet locator
- 稳定的 `source_record_id` 生成规则
- `sync_fingerprint` 所依据的原始字段范围
- 标签策略与 dedupe 策略
- worktree 根目录与仓库路径
- 分支前缀
- secret reference

除非你连 prompt 契约本身也要改，否则尽量保持 key 结构稳定。特别是：

- 不要把“行号”当成唯一记录标识
- 不要频繁变更用于生成 `sync_fingerprint` 的字段集合

## Codex App 使用示例

在 Codex app 中，使用这些文件的简单方式是：

1. 以当前仓库作为 workspace 打开。
2. 复制一份最接近的示例 JSON，然后按你的环境修改。
3. 新开一个线程，请求里写明：
   - JSON 文件路径
   - prompt 模版文件路径
4. 让 Codex 返回渲染后的 prompt bundle，并列出所有缺失字段。

最小请求模式：

```text
使用 ai_automation/gitlab_defect_automation/examples/coding-dispatcher-monorepo.json
以及 ai_automation/gitlab_defect_automation/prompts/ 下的 coding dispatcher 模版文件。

请按 JSON 替换占位符，保留下游运行时段落，并以 markdown 返回最终 prompt bundle。
如果任何必需值缺失，请明确列出来。
```
