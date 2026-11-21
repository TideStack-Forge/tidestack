---
name: publish-ouroboros
description: Publish Ouroboros 项目发版分支操作流程。Use when the user asks to prepare, trigger, move, rebase, push, or resolve backend overall or frontend-web release/snapshot branches for Ouroboros, including “发版”, “预发版”, “快照发版”, “release 分支”, “snapshot 分支”, or “frontend-web 发版”.
---

# Publish Ouroboros

## 目标

准备并推送能触发 CI 发版的 Git 分支。复用现有发版分支与流水线触发机制，不直接运行发布流水线，不修改业务代码。共享历史按[版本管理](../../../docs/rules/version-control.md)保持不变；禁止改写共享历史。

## 词汇

- 发版分支：推送后触发发版流水线的分支。
- 基线分支：承载待发布提交的工作分支。
- 后端整体发版：使用 `release/*` 或 `snapshot/*`。
- 前端 Web 发版：使用 `frontend-web/release/*` 或 `frontend-web/snapshot/*`。
- `release`：预发版分支；后端 CI 会按 prerelease 语义处理。
- `snapshot`：快照发版分支；后端 CI 会按 SNAPSHOT 语义处理。

## 收集发版需求

1. 确认发版对象。
   - 用户明确说前端、Web、frontend-web、SDK 或前端流水线时，选择前端 Web 发版。
   - 用户明确说后端、整体、平台、mothership、develop 或未说明对象时，默认后端整体发版。
2. 确认发版类型。
   - 用户说 release、预发、预发版、prerelease 时，选择 `release`。
   - 用户说 snapshot、快照、SNAPSHOT 时，选择 `snapshot`。
   - 用户未说明时，先确定基线分支，再在同一发版对象下寻找离基线头部最近的 `release` 或 `snapshot` 分支，并沿用它的类型。
3. 确认版本号/分支后缀。
   - 用户直接给了完整发版分支名时，优先使用该分支名。
   - 用户给了版本号时，先用原样版本作为后缀；若没有精确匹配分支，再尝试 `v` 前缀和去掉 `v` 前缀的候选。
   - 用户未给版本号时，从离基线头部最近的发版分支中提取后缀，保留现有 `v` 前缀或其他限定词。
4. 确认基线分支。
   - 用户明确指定基线分支时，使用该分支。
   - 否则检查当前目录是否位于某个 worktree，若该 worktree 绑定了分支，使用该分支。
   - 否则执行 `git worktree list --porcelain`，检查每个 worktree 的 `git status --short --branch`；若只有一个正在工作的 worktree 有明确绑定分支，使用它。
   - 若多个 worktree 都像是正在工作且用户没有给线索，先询问用户选择基线。
   - 若没有任何线索，使用 `dev`。

## 分支命名

- 后端预发：`release/<后缀>`
- 后端快照：`snapshot/<后缀>`
- 前端预发：`frontend-web/release/<后缀>`
- 前端快照：`frontend-web/snapshot/<后缀>`

示例：`release/v1.0.0-beta.2`、`snapshot/v2.0.0-rc.1`、`frontend-web/release/v2.0.0`、`frontend-web/snapshot/v1.6.0`。

## 发现命令

先同步引用，再基于本地 refs 决策：

```bash
git fetch --prune origin
git worktree list --porcelain
git for-each-ref --format='%(refname:short) %(committerdate:iso8601)' refs/heads refs/remotes \
  | rg '(^|/)(frontend-web/)?(release|snapshot)/|^frontend-web/'
```

寻找“离基线头部最近”的发版分支时，只在目标发版对象范围内筛选：

- 后端整体：`release/*`、`snapshot/*`、`origin/release/*`、`origin/snapshot/*`
- 前端 Web：`frontend-web/release/*`、`frontend-web/snapshot/*`、`origin/frontend-web/release/*`、`origin/frontend-web/snapshot/*`

优先选择与基线同一路径上距离最近的分支：

```bash
git rev-list --count <candidate>..<baseline>
git merge-base --is-ancestor <candidate> <baseline>
```

如果没有候选分支是基线祖先，再比较共同祖先到基线头部的距离，并选择共同祖先最接近基线头部的候选；若多个候选距离相同，优先用户暗示的类型，否则优先最近提交时间更新的候选。

## 执行动作

目标 ref 有发布任务专门约束时，先核对该任务上下文。历史 snapshot/v2.0.0-rc.1 的约束见[历史版本参考](references/legacy-snapshot.md)，只在该具体发布任务仍适用时读取。

1. 安全检查。
   - 在任何分支移动、merge、push 前执行 `git status --short --branch`。
   - 更新发版分支前，先用 `git worktree list --porcelain` 检查目标发版分支是否已绑定 worktree。
   - 若目标发版分支已绑定 worktree，必须在该已有 worktree 中操作，且先确认它没有未提交变更。
   - 若目标发版分支没有绑定 worktree，必须为本次发版建立临时 worktree，在临时 worktree 中操作；操作和验证完成后删除该临时 worktree。
   - 不要丢弃用户未提交改动；如果目标 worktree 不干净，停止并说明 blocker。
2. 定位或创建目标发版分支。
   - 根据发版对象、类型、版本后缀拼出目标分支。
   - 若目标分支只存在于 `origin/<branch>`，先创建本地跟踪分支。
   - 若用户明确给了版本/分支而目标分支不存在，可从基线头部创建它。
   - 若版本/分支是推断出来但目标不存在，停止并重新检查推断。
3. 按关系更新分支。
   - 目标是基线祖先时，在目标已有/临时 worktree 中 `git merge --ff-only <baseline>`。
   - 目标已有额外提交且包含基线时，保留这些逻辑提交，按目标分支要求检查后交付。
   - 两者分叉时，普通共享发版分支通过 MR 将基线合入并保留逻辑提交；完成组合验证后才能发布。不能把分叉转化为 rebase 或强推。
   - 目标版本有专门发布约束时另行核对；与保留共享历史冲突就停止该分支更新，说明需要新的发布 ref 或约束决定。
4. 推送策略。
   - 用户授权发布且没有明确禁止推送时，普通 push 到目标分支；推送会触发已配置流水线。
   - 非快进拒绝时重新读取远端状态、核对他人提交并走 MR 集成，禁止 force/force-with-lease。
   - 推送后用 `git ls-remote --heads origin <branch>` 核对远端提交；另查实际流水线和制品，不能把推送成功当作发布成功。

## 命令模板

先定位目标发版分支绑定的 worktree；如果没有绑定，创建临时 worktree：

```bash
git fetch --prune origin

repo_root=$(git rev-parse --show-toplevel)
target_branch=<target-branch>
baseline=<baseline>
tmp_worktree="${repo_root}/.worktrees/.tmp-publish-${target_branch//\//-}-$(date +%Y%m%d%H%M%S)"
created_temp_worktree=false

target_worktree=$(git worktree list --porcelain \
  | awk -v branch="refs/heads/${target_branch}" '
      /^worktree / { path=$2 }
      /^branch / && $2 == branch { print path }
    ')

if [ -z "${target_worktree}" ]; then
  if git show-ref --verify --quiet "refs/heads/${target_branch}"; then
    git worktree add "${tmp_worktree}" "${target_branch}"
  elif git show-ref --verify --quiet "refs/remotes/origin/${target_branch}"; then
    git branch --track "${target_branch}" "origin/${target_branch}"
    git worktree add "${tmp_worktree}" "${target_branch}"
  else
    git worktree add -b "${target_branch}" "${tmp_worktree}" "${baseline}"
  fi
  target_worktree="${tmp_worktree}"
  created_temp_worktree=true
fi
```

在目标 worktree 中仅执行可快进更新；分叉或存在额外发版提交时，先按上面的集成规则处理，不直接运行此模板：

```bash
cd "${target_worktree}"
git status --short --branch

if ! git merge-base --is-ancestor HEAD "${baseline}"; then
  echo '目标不能快进；按 MR 集成规则处理并验证，禁止改写共享历史' >&2
  exit 1
fi
git merge --ff-only "${baseline}"
git push origin HEAD:"${target_branch}"
```

只删除本次创建的临时 worktree，不能删除已有 worktree：

```bash
if [ "${created_temp_worktree}" = true ]; then
  cd "${repo_root}"
  git -C "${target_worktree}" status --short --branch
  git worktree remove "${target_worktree}"
fi
```

## 完成前验证

- 本地目标分支包含基线头部：`git merge-base --is-ancestor <baseline> <target-branch>`。
- 若是快进，目标 worktree 的 `git rev-parse HEAD` 必须等于 `git rev-parse <baseline>`。
- 若已推送，`git ls-remote --heads origin <target-branch>` 的 SHA 必须等于本地目标分支 SHA。
- 若创建了临时 worktree，完成验证后必须删除；已有 worktree 必须保留。
- 最终回复必须说明：发版对象、类型、版本后缀、基线分支、目标发版分支、是否推送，以及实际集成方式和验证结果。
