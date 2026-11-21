# GitLab 缺陷自动化 Prompt Bundle

这组文件用于参数化的 Codex 自动化。

设计原则：

- GitLab 是唯一缺陷事实源。
- 腾讯文档表格只承担输入和回写。
- intake 与 coding 是两套独立的自动化任务。
- dispatcher 与 worker 的职责明确拆分。
- 环境差异通过参数承载，而不是写死在模版里。
- prompt 模版必须能被多个自动化实例重复复用。

建议阅读顺序：

1. `00-common-parameter-schema.md`
2. `30-gitlab-label-state-machine.md`
3. `31-tencent-sheet-column-contract.md`
4. `10-12` intake 模版
5. `20-22` coding 模版
6. `40-automation-instance-examples.md`

无脚本使用这些模版的方式：

1. 选出你这次要创建的自动化实例所需的 prompt 文件。
2. 从 `../examples/` 中选一份 JSON 示例，并替换成你的环境参数。
3. 把这些 prompt 文件和 JSON 参数文件一起交给 AI agent。
4. 让 AI agent 按 JSON 中的值替换占位符，例如 `{{ gitlab.project }}`、`{{ source.identity }}`。
5. 让 AI agent 以纯 markdown 形式返回最终 prompt bundle，并保留那些本来就应该在后续阶段再提供的运行时段落。

## Codex App 使用方式

在 Codex app 里，生成出的 prompt 应只描述任务本身；workspace 和调度周期应通过 Codex app 的配置传入，而不是写进生成后的模版里。

推荐的 Codex app 使用流程：

1. 把 workspace 指向当前仓库。
2. 在 `../examples/` 中准备一份参数 JSON，或复制一份后再修改。
3. 在 Codex 的提示词中，明确写出 JSON 文件和模版文件的路径。
4. 让 Codex 在回答里直接渲染出最终 prompt bundle。
5. 人工审阅结果后，再把批准的 prompt 文本放进真正的 recurring automation。

在 Codex app 中配置 recurring automation 时：

- 一个 automation 只关注一张表，或一个 dispatcher 范围
- intake 与 coding 应拆成两套独立 automation
- worker prompt 应作为 dispatcher 流程的产物，而不是一个全局共享的常驻 recurring job
- 每个正式启用的 automation 都应记录一份 prompt release 信息，至少包含：
  - 当前仓库 commit
  - 使用的 template 文件列表
  - 对应的参数 JSON 路径
  - 生成时间
  - 生成人与审核人

推荐的 prompt 组合：

- Intake dispatcher 族：
  `00-common-parameter-schema.md`、`30-gitlab-label-state-machine.md`、
  `31-tencent-sheet-column-contract.md`、`10-intake-dispatcher-prompt.md`、
  `11-intake-writer-worker-prompt.md`、
  `12-intake-writeback-reporter-prompt.md`
- Coding dispatcher 族：
  `00-common-parameter-schema.md`、`30-gitlab-label-state-machine.md`、
  `20-coding-dispatcher-prompt.md`、
  `22-coding-result-reporter-prompt.md`
- Coding worker：
  `00-common-parameter-schema.md`、`21-coding-worker-prompt.md`

对 AI agent 的重要约束：

- 不要修改源模版文件。
- 不要硬编码密钥，只保留 `vault://...` 这类 secret reference。
- 不要静默臆造缺失参数，必须明确指出 JSON 中没有提供什么。

## Codex App 请求示例

以 intake dispatcher 实例为例：

```text
读取：
- ai_automation/gitlab_defect_automation/examples/intake-customer-cn.json
- ai_automation/gitlab_defect_automation/prompts/00-common-parameter-schema.md
- ai_automation/gitlab_defect_automation/prompts/30-gitlab-label-state-machine.md
- ai_automation/gitlab_defect_automation/prompts/31-tencent-sheet-column-contract.md
- ai_automation/gitlab_defect_automation/prompts/10-intake-dispatcher-prompt.md
- ai_automation/gitlab_defect_automation/prompts/11-intake-writer-worker-prompt.md
- ai_automation/gitlab_defect_automation/prompts/12-intake-writeback-reporter-prompt.md

输出：
- 每个目标 prompt 文件对应一段渲染后的 markdown
- 一份简短的缺失或含糊参数清单

规则：
- 只按 JSON 文件里的值替换占位符
- 保留那些本来就应由下游 agent 提供的运行时 payload 占位或说明
- 不要改写源模版文件
```
