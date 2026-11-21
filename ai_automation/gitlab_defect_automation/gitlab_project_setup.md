# GitLab 项目配置执行清单

这个文件用于收敛当前方案里所有 GitLab 项目侧配置要求。

它的目标不是做一份泛泛而谈的说明，而是作为一份可直接交给 AI agent 执行的配置标准。Phase 1 默认面向：

- 自建 GitLab Free
- 当前 monorepo 的单一 GitLab project
- GitLab 作为唯一缺陷事实源
- 腾讯文档只作为 intake 与 writeback 界面

如果后续范围扩展到多个 project、多个 group、或 defect 之外的 work item，再在此文件基础上扩展，不要把新要求散落回其他文档。

## 使用方式

推荐把这个文件和一份参数 JSON 一起交给 AI agent。

- 本文件定义“要配什么、按什么顺序配、做到什么程度算完成”
- 参数 JSON 负责提供环境值，不把项目地址、标签值、secret 存储位置写死在文档里

推荐最小参数集：

```json
{
  "gitlab": {
    "base_url": "https://gitlab.example.com",
    "project": "group/monorepo",
    "project_id": 123,
    "default_branch": "dev",
    "token_strategy": "project_access_token",
    "label_scope": "project"
  },
  "labels": {
    "domain_values": ["domain::platform", "domain::workflow"],
    "source_values": ["source::tencent-sheet-internal", "source::tencent-sheet-customer"],
    "severity_values": ["sev::low", "sev::medium", "sev::high"],
    "priority_values": ["prio::low", "prio::medium", "prio::high"]
  },
  "board": {
    "enabled": true,
    "columns": [
      "state::incoming",
      "state::needs-info",
      "state::confirmed",
      "state::in-progress",
      "state::in-qa",
      "state::done"
    ]
  },
  "repo": {
    "path": "/abs/path/to/repo"
  },
  "secrets": {
    "runtime_secret_ref": "secret://gitlab/intake-runtime",
    "coding_secret_ref": "secret://gitlab/coding-runtime",
    "webhook_secret_ref": "secret://gitlab/webhook"
  },
  "webhook": {
    "enabled": false,
    "url": "",
    "events": ["issues", "merge_requests"]
  }
}
```

## 执行原则

- 只依赖 GitLab Free 的基础 project 能力，不把 Premium / Ultimate 功能当成前置条件。
- GitLab 是唯一事实源，任何配置都不能让腾讯文档变成第二套状态系统。
- 缺陷配置优先使用 project 级能力；只有多项目共用场景明确出现后，再上收为 group 级。
- Secret 只进入你的密钥存储，不进入 agent 对话、issue 评论、commit、MR 描述、截图或日志。
- 碰到权限不足、实例策略限制、或功能未启用时，agent 应停止在明确 blocker，而不是静默跳过。
- 没有被明确允许的高风险操作不要自动开启，例如自动合并、自动关 issue、放开默认分支保护。

## AI agent 的任务边界

权限提醒：

- labels、issue board、protected branch、webhook 往往需要至少 Maintainer 级别项目权限
- project access token 的创建能力还可能受实例版本和管理员策略影响
- 如果当前身份做不到，不应伪造“已配置成功”，而应返回明确 blocker

可以自动做：

- 检查 project 基线信息
- 创建或校验 labels
- 创建或校验 issue board
- 配置默认分支保护
- 通过仓库提交补齐 issue / MR 模板
- 在权限允许时创建 project access token 或使用既有机器人账号
- 配置 webhook
- 输出配置结果、证据、阻塞项

不要自动做：

- 直接把生成的 token 明文回显到对话里
- 绕过受保护分支直接推送主干
- 在没有确认的情况下修改多个 project 或 group 级共享配置
- 自动开启合并策略，导致 agent 结果直接进主干
- 自行发明标签、状态或模板结构

## 配置步骤

### 1. 基线核查

目标：

- 确认目标 project 就是当前 monorepo 的唯一缺陷事实源
- 确认当前默认分支
- 确认 agent 当前权限是否足够完成项目侧配置
- 确认实例是否支持 project access token；若不支持，立即切换回退方案

建议输出：

- project URL
- project path / id
- default branch
- 当前执行身份
- 权限缺口与 blocker

### 2. 标签体系

至少要创建或校验下面几组标签。

固定标签：

- `type::bug`
- `review::required`
- `agent::ready`
- `agent::claimed`
- `agent::blocked`
- `agent::mr-opened`
- `agent::done`
- `state::incoming`
- `state::needs-info`
- `state::confirmed`
- `state::in-progress`
- `state::in-qa`
- `state::done`
- `state::closed-invalid`
- `state::closed-duplicate`

参数化标签：

- `source::*`
- `domain::*`
- `sev::*`
- `prio::*`

执行要求：

- 先校验是否已存在，不重复创建同名标签
- Phase 1 默认使用 project label
- 只有在参数明确要求且场景确实是多项目共享时，才改成 group label

### 3. 项目看板

如果 `board.enabled = true`，则创建或校验一个面向缺陷流转的 project issue board。

建议列：

- `state::incoming`
- `state::needs-info`
- `state::confirmed`
- `state::in-progress`
- `state::in-qa`
- `state::done`

执行要求：

- 先检查是否已有同目的看板，避免重复创建
- 看板以 `state::*` 为主，不把 `agent::*` 当成主看板列
- 如果实例暂时没有 board，agent 可以创建一个最小化 board，不需要一次做复杂泳道

### 4. 默认分支保护

至少要满足：

- 默认分支是明确的，例如 `dev`
- 默认分支处于 protected 状态
- agent 产出应通过分支 + MR 进入，而不是直接改主干

执行要求：

- 如果当前保护策略已经满足要求，记录现状即可
- 如果需要修改，优先收紧为“人工合并、禁止 agent 直接落主干”
- 如果实例策略要求更细粒度审批，保留原策略，不做放松

### 5. Issue / MR 模板

需要在仓库中校验或补齐：

- `.gitlab/issue_templates/Default.md`
- `.gitlab/merge_request_templates/Default.md`

执行要求：

- 通过新分支和 MR 提交，不要直推默认分支
- 模板的目标是固定结构，而不是预填具体项目值
- 如果仓库里已有等价模板，优先复用，不重复造一套

建议模板结构至少包含：

- issue：问题摘要、来源、复现、期望、实际、影响范围、附件/链接、初步分类
- MR：修复摘要、根因、改动点、测试结果、风险、关联 issue

### 6. 自动化身份与令牌

优先顺序：

1. project access token
2. 专用机器人账号 + PAT
3. 已存在且被批准复用的 automation token

最小拆分建议：

- intake / writeback / coding result reporter：
  一个偏 API 写入的 runtime token
- coding worker：
  一个用于 issue/MR API 与必要仓库写入的 token

执行要求：

- token 必须设置过期时间
- 必须记录 token owner、用途、轮换责任人
- token 明文只会出现一次时，agent 必须立刻写入指定 secret 存储位置
- 对话里只返回 `secret_ref`，不要返回 token 本身
- 如果当前权限不足以创建 token，agent 必须停在 blocker，并说明需要谁来补权或代建

### 7. 可选 webhook

只有在消费端已经具备幂等处理能力时才启用。

建议最小范围：

- issue 相关事件
- merge request 相关事件

执行要求：

- 没有 webhook 消费端时，保持关闭，继续使用轮询
- webhook secret 进入密钥存储，不写进文档和对话
- 如果同时存在多个 webhook 来源，消费端必须按来源和事件做幂等控制

## 完成标准

AI agent 完成后，至少应返回下面这些结果：

- 项目基线信息
- 已创建或已确认的 labels 列表
- issue board URL 或说明复用的是哪个已有 board
- 默认分支保护结果
- 模板对应的 MR 或 commit 链接
- 新建或复用的 token 对应 `secret_ref`
- webhook 状态
- 未完成项与 blocker

## 最短路径配置包

如果你只想尽快把 Phase 1 跑起来，最少需要完成：

- 明确唯一 defect project
- 建好 `state::*`、`agent::*`、`review::required`、`type::bug`
- 建一个最小化 project issue board
- 保护默认分支
- 补齐 issue / MR 模板
- 准备至少一个 runtime token，并把它放进 secret 存储

Webhook、group label、复杂 board 编排都可以后置。

## 交给 AI agent 的最小任务指令

可以直接把下面这段连同参数 JSON 一起给 agent：

```text
请按 ai_automation/gitlab_defect_automation/gitlab_project_setup.md 执行 GitLab 项目配置。

要求：
1. 严格按文档中的执行顺序做。
2. 优先复用现有配置，不要重复创建等价对象。
3. 所有 secret 只写入我提供的 secret 存储位置，只向我返回 secret_ref。
4. 如果权限不足、实例能力不足、或需要人工确认，停在 blocker 并明确说明。
5. 最终输出：已完成项、证据链接、secret_ref、未完成项、下一步建议。
```

## 参考

本文件依赖的 GitLab 能力主要来自官方文档中的这些主题：

- project access tokens
- issue boards
- description templates
- protected branches
- webhook events
