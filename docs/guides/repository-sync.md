# 源码聚合与独立交付

monorepo 是当前源码维护入口，历史子项目曾以 git subtree 聚合。现有目录不保证仍有活跃独立仓库；跨仓修改/发布需记录各自固定提交和依赖版本，不能推定“同名目录 = 已同步”。分支和维护线见[版本管理](../rules/version-control.md)。

## 现行声明式导出

IDE 使用 [sync.manifest.json](../../ide-plugins/sync.manifest.json)和[同步脚本](../../scripts/open-source/sync-ide-plugins.mjs)。从仓库根执行：

~~~bash
node scripts/open-source/sync-ide-plugins.mjs --check --all
~~~

该命令检查配置的目标 checkout；先读[IDE 说明](../../ide-plugins/README.md)确认目标和所需目录。check 不写目标，真实 sync 会覆盖受管路径；commit/push 需要对应交付授权。同步生成的 `.tidestack-sync.json` 关联来源提交、受管路径和哈希；独立 README、LICENSE 等包装由 manifest 保留规则处理。Schema、Skills 等产品同样使用各自正式同步入口和合同，不另建复制清单。

## 历史 subtree 来源表

下表从原 README 保留，用于定位来源，不证明远端当前可用或已导出；分支必须由对应交付任务核实。

| 子模块路径                                                 | 子模块远程仓库地址                                                                                           |
|-------------------------------------------------------|-----------------------------------------------------------------------------------------------------|
| core/ouroboros-data                                   | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-data.git                                   |
| core/ouroboros-logicflow                              | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-logicflow.git                              |
| core/ouroboros-approval-workflow                      | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-approval-workflow.git                      |
| core/ouroboros-field-type                             | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-field-type.git                             |
| core/ouroboros-business-model                         | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-business-model.git                         |
| core/ouroboros-ui-model                               | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-ui-model.git                               |
| core/ouroboros-webapi-model                           | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-webapi-model.git                           |
| core/ouroboros-menu-model                             | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-menu-model.git                             |
| core/ouroboros-app-module-model                       | git@gitlab.whxinpai.com:ouroboros-lowcode/core/ouroboros-app-module.git                             |
| libs/util/ouroboros-util                              | git@gitlab.whxinpai.com:ouroboros-lowcode/support/ouroboros-util.git                                |
| libs/common                                           | git@gitlab.whxinpai.com:ouroboros-lowcode/support/ouroboros-common.git                              |
| security/ouroboros-web-security                       | git@gitlab.whxinpai.com:ouroboros-lowcode/security/ouroboros-web-security.git                       |
| ability/ouroboros-ability-all                         | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-ability-all.git                         |
| ability/ouroboros-app-runtime                         | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-app-runtime.git                         |
| ability/ouroboros-configuration                       | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-configuration.git                       |
| ability/ouroboros-data-dict                           | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-data-dict.git                           |
| ability/ouroboros-file-upload                         | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-file-upload.git                         |
| ability/ouroboros-notification                        | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-notification.git                        |
| ability/ouroboros-rpc-model                           | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-rpc-model.git                           |
| ability/ouroboros-organization-structure              | git@gitlab.whxinpai.com:ouroboros-lowcode/ability/ouroboros-organizational-structure.git            |
| app-module/dynamic-form                               | git@gitlab.whxinpai.com:ouroboros-lowcode/app-module/dynamic-form.git                               |
| app-module/ui-schema-management                       | git@gitlab.whxinpai.com:ouroboros-lowcode/app-module/ui-schema-management.git                       |
| dev-module/ouroboros-dev-app-management               | git@gitlab.whxinpai.com:ouroboros-lowcode/dev-module/ouroboros-dev-app-management.git               |
| dev-module/ouroboros-dev-container                    | git@gitlab.whxinpai.com:ouroboros-lowcode/dev-module/ouroboros-dev-container.git                    |
| dev-module/ouroboros-dev-model-manage-webapi-scaffold | git@gitlab.whxinpai.com:ouroboros-lowcode/dev-module/ouroboros-dev-model-manage-webapi-scaffold.git |
| dev-module/ouroboros-dev-version-control-system       | git@gitlab.whxinpai.com:ouroboros-lowcode/dev-module/ouroboros-dev-version-control-system.git       |
| dev-module/ouroboros-dev-module-all                   | 无                                                                                                   |
| platform/ouroboros-base                               | git@gitlab.whxinpai.com:ouroboros-lowcode/platform/ouroboros-base.git                               |
| platform/ouroboros-develop                            | git@gitlab.whxinpai.com:ouroboros-lowcode/platform/ouroboros-develop.git                            |
| platform/ouroboros-mothership                         | git@gitlab.whxinpai.com:ouroboros-lowcode/platform/ouroboros-mothership.git                         |
| legacy-compat/quickbuilder                            | git@gitlab.whxinpai.com:ouroboros-lowcode/compatible/quickbuilder.git                               |
| micro-app/spreadsheet                                 | git@gitlab.whxinpai.com:ouroboros-lowcode/micro-app/spreadsheet.git                                 |
| scripts                                               | git@gitlab.whxinpai.com:ouroboros-lowcode/automation-scripts.git                                    |

原表中的 `platform/ouroboros-base` 已不代表当前物理目录；保留为历史来源，不据此创建空目录。dev 聚合入口以[当前 POM](../../dev-module/ouroboros-dev-module-all/pom.xml)为准。

## subtree 操作边界

仅在目标仍由 subtree 管理且已确认路径/远端/分支时，从仓库根使用 git subtree 的 pull/push/add 正式机制。先检查所有相关 worktree 状态，保存未提交工作，确认 source/target HEAD、受管范围与权限，再在隔离 checkout 预览差异。不要先删除模块再尝试同步；迁移位置需同时更新 Maven、资源、脚本和文档，并验证后再移除旧路径。

子仓引入使用 `git subtree pull --prefix=<path> <remote> <branch> --squash`，导出使用 `git subtree push --prefix=<path> <remote> <branch>`。这些命令会修改历史或推送，只有任务明确需要时执行；参数取真实对象，不把此处占位符直接运行。

## 导出验收

每次记录源提交、目标提交、受管路径、manifest、版本与检查结果；在导出镜像/独立 checkout 检查本地链接、Agent 入口、README 的独立构建命令及依赖合同。跨仓变更按依赖顺序交付，任何未同步对象明确列出。当前未取得实际独立仓库和远端交付证据，风险见 [SYNC-001](../risk-tracker.md#sync-001-独立仓库的交付基线)。
