# Agent 会话恢复

恢复时以当前任务保存的基线、进度和 Git 事实为准。历史计划目录不再是当前任务入口。

## 确认工作区

从用户指定 checkout 执行：

~~~bash
pwd
git status --short --branch
git rev-parse HEAD
git log -5 --oneline --decorate
~~~

核对任务中的仓库、分支、HEAD、worktree 和已有变更；基线变化时重新检查受影响项。保留无关修改，不能为了恢复而重置工作区。

## 读取任务记录

恢复目标、范围、用户决定、已完成的实际检查及证据、未完成事项和阻塞；核对需要保留的 PID/端口/数据/忽略产物。信息不全时先检查相关 diff 和日志，不能将猜测写成已有决定。

`scripts/agent-feedback/resume-session.sh` 输出最近提交、Git状态和恢复建议；目标、决定与进度从当前任务/PR/Issue记录恢复。脚本不扫描旧 exec-plans，也不替代任务记录。

## 按需恢复运行上下文

只有涉及运行联调时才按[反馈循环](agent-feedback-loop.md)查询目标健康、日志和 metrics，先核对实际 URL、环境和进程归属。恢复会话不自动授权重启或部署。

确认断点后继续未完成项，结果写回当前任务。中断前保存基线、相关 diff、关键决定、真实验证、待办和资源交接；临时文件不作为长期共享证据。
