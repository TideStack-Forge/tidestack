# CI/CD 与交付

本规则描述仓库中实际声明的发布路径。集成主线及 MR 约定见[版本管理](version-control.md)。[后端 Jenkinsfile](../../Jenkinsfile)、[前端 Jenkinsfile](../../frontends/web/Jenkinsfile)及其调用脚本是执行入口；推送发布分支或标签会产生远端发布副作用，应在对应发布任务授权后操作。

## 合并前的检查

按变更范围运行[测试规范](testing.md)中的行为测试和架构检查，文档变更运行 `bash scripts/docs/check-links.sh`；涉及前端 Agent 入口时，在 `frontends/web` 运行 `npm run validate:agent-entrypoints`。前端还应运行所属包已有的 lint、unit、integration 命令，不能用 build 成功替代这些结果。

仓库未提供可核实的统一 MR 必须检查配置或分支保护导出，不能声称上述检查都由服务器强制。发布路径的门控也不等同于 MR 门控。当前前端 Web 发布脚本主要执行安装与构建，显式 lint/test 门控缺口见 [CI-001](../tech-debt-tracker.md#ci-001-前端发布质量门控)。后端 snapshot 默认跳过门控和测试，不能据镜像发布成功声明测试通过。

## 后端发布流程

### 1) 快照版本

- 推送分支：`snapshot/v<版本号>`
- 产物版本：`<版本号>-SNAPSHOT`
- 默认行为：发布 Docker 镜像，并 deploy Maven snapshot（可通过指令关闭）
- 门控规则：默认跳过 `verify-*` 门控检查，可通过发版指令手动开启或关闭
- 测试规则：默认跳过构建阶段测试用例，可通过发版指令手动开启或关闭

### 2) 发版准备版本（prerelease）

- 推送分支：`release/v<版本号>`
- 产物版本：`<版本号>-prerelease`
- 默认行为：只发布 Docker 镜像，不 deploy Maven

### 3) 正式发版

- 推送 tag：`v<版本号>`（示例：`v1.0.0`）
- 默认行为：发布 Docker 镜像并 deploy Maven release
- 说明：`alpha`/`beta`/`rc` 等先行版本同样走正式发版通道

### 4) 单独发布基座

- 推送 tag：`mothership/v<版本号>`
- 默认行为：发布基座镜像，并可 deploy Maven（受指令控制）

### 5) 单独发布开发平台

- 推送 tag：`develop/v<版本号>`
- 默认行为：只发布开发平台镜像，不 deploy Maven

### 6) 热修复

- 热修复分支 `hotfix/*` 不自动触发构建
- 通过 tag `v<版本号>-hotfix.<n>` 触发，流程同正式发版

## 前端 Web 发布流程

### 1) 快照版本

- 推送分支：`frontend-web/snapshot/v<版本号>`
- 产物版本：`<版本号>-snapshot`

### 2) 发版准备版本（prerelease）

- 推送分支：`frontend-web/release/v<版本号>`
- 产物版本：`<版本号>-prerelease`

### 3) 发布前端基座版本

- 推送 tag：`frontend-web-v<版本号>`
- 产物版本：`<版本号>`
- 若 tag annotation 包含 `[publish-sdk]` 或 `[publish sdk]`，会同时发布 SDK

### 4) 单独发布 SDK

- 推送 tag：`frontend-web-sdk-v<版本号>` 或 `frontend-web-sdk/v<版本号>`
- 只发布 `ouroboros-sdk` 到 npm 仓库

## 发布指令

可在以下位置写入指令：

1. 发布 tag 的 annotation（适用于 tag 触发）
2. 触发分支上的最新提交 message（适用于分支触发）

| 指令 | 说明 |
| --- | --- |
| `[ci-skip]` | 跳过 CI 构建（Jenkins GitLab 插件内置） |
| `[skip-deploy]` | 跳过 deploy 到 Maven 仓库（后端） |
| `[skip-publish]` | 跳过推送 Docker 镜像（后端） |
| `[gate-checks:on|off]` | 手动开启或关闭后端 `verify-*` 门控检查；`snapshot/*` 分支默认 `off`，其他发版路径默认 `on` |
| `[build-tests:on|off]` | 手动开启或关闭后端构建阶段测试用例；`snapshot/*` 分支默认 `off`，其他发版路径默认 `on` |
| `[base-image:<full image name>]` | 构建基座时指定基础镜像（后端） |
| `[web-image-tag:<tag>]` | 构建基座时指定 web 镜像 tag（后端） |
| `[mothership-image-tag:<tag>]` | 构建开发平台时指定基座镜像 tag（后端） |
| `[tags:<tag1>,<tag2>,...]` | 指定发布镜像 tag 列表（后端） |


## 本地入口与独立工程

公共存储的 `verify-content-storage` 阶段沿用后端 `GATE_CHECKS_ENABLED` 开关，调用[正式存储场景](../../testing/integration/feature/storage/independent-content-stores/harness.playbook.md)，本地后端 preflight 复用同一入口。该阶段需要 Python3、Java21、Maven 与隔离 MinIO（可通过 Docker 自动准备），检查完整编译、存储及消费者回归、架构、覆盖率和制品分包。发布入口以 HEAD 记录基线；本地分支验收显式传入目标维护线。声明接入不代表远端 Jenkins 已实际执行，也不改变快照默认关闭门禁的既有规则。

- 后端构建：仓库根目录 `bash build.sh build -c true`；完整参数先读 `bash build.sh --help`。`publish`/`deploy` 有发布副作用，复用[发布操作 Skill](../../.agents/skills/publish-ouroboros/SKILL.md)。
- 后端预检：`bash scripts/ci/preflight-backend-release.sh`，先检查参数与环境，按其实际覆盖范围解释结果。
- 前端各包：以 `frontends/web` 和目标包的 package.json 为准；安装策略见[前端说明](../../frontends/web/README.md)。
- Maven 插件使用独立版本，不参与根默认发布；维护时从根执行 `mvn -f maven-plugins/pom.xml clean deploy`，只在已授权的插件发布任务运行。见[插件 README](../../maven-plugins/ouroboros-dependency-maven-plugin/README.md)。
- IDE、Schema、手册及模板有独立构建/同步边界，按[源码同步](../guides/repository-sync.md)关联源提交和导出提交，不推定根流水线已验证全部产品。

## 证据与失败处理

每次交付关联源 HEAD、维护线、触发 ref、命令/门控开关、目标环境、版本、结果、CI job 和制品地址；列明跳过项及原因。可变分支名不能代替固定提交，历史报告不能代替本次运行。

本仓库未声明统一的 CI 日志/制品保留期，仓库配置本身也不能证明任意 HEAD 已远端运行。交付任务需确认访问权限和保留策略，并将必要证据保存到任务或 CI 制品，见 [EVIDENCE-001](../risk-tracker.md#evidence-001-交付证据可访问性)。

失败时保留首个失败命令、日志和实际产物，停止依赖该结果的发布；复现与回退复用[运维手册](../runbooks/README.md)。已发布 tag 和 release 产物不可覆盖，修复后用新版本；不可通过关闭检查把失败改写为通过。
