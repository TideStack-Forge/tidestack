# 开发指南

先读[AGENTS](../AGENTS.md)和目标模块正文。版本/合并规则由[版本管理](rules/version-control.md)维护，代码边界由[编码约定](rules/coding.md)维护；本页只保留本地上手入口。

## 环境准备

Java 21、Maven 3.6.3+、UTF-8；Java 兼容范围见[运行时基线](design/java-runtime-baseline.md)。导入根 POM，并将 [ouroboros-code-style.xml](../ouroboros-code-style.xml)导入 IDEA。私有依赖需本机 Maven settings 配置对应仓库凭证，不能写入源码。

前端按[前端说明](../frontends/web/README.md)和根 [.nvmrc](../.nvmrc)准备 Node；各包锁文件与正式安装命令不可混用。平台运行需 Docker、数据库和基础服务，见[安装](handbook/docs/getting-started/index.md)与[部署](deployment-guide.md)。

从本次选定 checkout 根目录执行：

~~~bash
pwd
git status --short --branch
java -version
mvn -version
bash build.sh --help
~~~

确认路径、已有变更和工具基线后再修改。新功能/修复从 dev 建分支；明确要求 worktree 时，按[并行协作](rules/parallel-work.md)隔离，不能改回主 checkout。

## 构建与测试

| 目标 | 在仓库根执行 |
| --- | --- |
| 完整后端构建并运行构建测试 | `bash build.sh build -c true` |
| 定向构建及所需依赖 | `mvn clean install -pl :<artifactId> -am`，artifactId 取目标 POM |
| 已装好依赖的单模块测试 | `mvn -f <module>/pom.xml test` |
| 架构测试 | 先 `mvn -DskipTests test-compile`，再 `mvn -pl testing/architecture-tests -am test -Dsurefire.failIfNoSpecifiedTests=false`（两阶段使用相同 profile） |
| 包结构检查 | `bash scripts/architecture/check-package-structure.sh` |
| 文档链接/锚点 | `bash scripts/docs/check-links.sh` |

测试分类、覆盖门槛、环境与报告见[测试规范](rules/testing.md)，复杂系统场景使用[正式测试任务](../.agents/skills/run-tests/SKILL.md)。上表列入口，不表示当前环境已运行。Maven test 不保证每个模块生成 JaCoCo 报告；以目标插件/profile 的真实产物为准。

## 本地运行与排障

平台安装/升级按[部署指南](deployment-guide.md)，已有后端的前端联调按[现有 Skill](../skills/tidestack-local-debugging/SKILL.md)。后端日志、健康、metrics 与恢复见[运维任务](../.agents/skills/operate-ouroboros/SKILL.md)。先核对目标环境与资源归属，再启动或清理资源。

依赖错误先对目标模块执行 `mvn dependency:tree`，核对 BOM、scope 和私有仓库；类找不到先检查模块是否装配及扩展注册，再决定是否重建。架构失败按检查报告回到正式扩展点，不靠 allowlist 掩盖新增违规。测试失败保留日志，修复原因后重跑受影响路径，不能以跳过测试视为通过。

## 交付前

回读 diff，只暂存本任务相关文件；提交保持逻辑完整，通过 MR 合入 dev。同步功能、需求、设计及合同，记录已执行检查与未验证项。发布入口、标签和指令统一见[CI/CD](rules/ci-cd.md)；本地构建不授权推送发布。
