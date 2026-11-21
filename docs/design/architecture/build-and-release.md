# Ouroboros 构建与发布设计

## Monorepo 与 subtree 关系

Ouroboros 以 Monorepo 作为统一协作入口。历史 subtree 来源与当前声明式导出机制见[仓库同步指南](../../guides/repository-sync.md)；来源表不证明独立仓库已同步，也不应直接据此删除目录或重放 subtree 操作。

## Maven 聚合结构

根 `pom.xml` 是后端构建入口，当前以 `pom` 打包聚合 `bom`、`libs`、`ability`、`core`、`security`、`app-module`、`dev-module`、`platform`、`maven-archetypes`。这说明：

- Maven 聚合结构覆盖后端主干模块和工程脚手架。
- `frontends` 不在根 Maven modules 中，而是走独立前端构建与发布链路。
- 统一版本号通过根项目 `version` 和 `${ouroboros.version}` 传递到各后端模块。

## 版本管理规则

根目录 `pom.xml` 维护后端根版本，`versions-maven-plugin` 提供统一版本调整入口。团队分支、MR 和维护政策以[版本管理规则](../../rules/version-control.md)为准；后端与前端的发布 ref、版本推导及触发边界以[CI/CD 规则](../../rules/ci-cd.md)和实际 Jenkinsfile 为准。

## 后端发布入口

后端发布入口由根目录 `Jenkinsfile` 与 `build.sh` 共同定义：

- `Jenkinsfile` 负责识别分支或 tag 类型、推导版本号、控制是否 deploy Maven，以及是否发布 `mothership` 与 `develop`。
- `build.sh` 提供 `build`、`deploy`、`publish`、`publish-mothership`、`publish-develop` 等入口，分别对应构建、部署 Maven、发布全部镜像或单独发布平台镜像；`preflight` 用于本地构建 Maven 和平台镜像，不 deploy、不 push。
- `Jenkinsfile` 当前把 `snapshot/*`、`release/*`、`refs/tags/*`、`refs/tags/mothership/*`、`refs/tags/develop/*` 作为后端发布判断入口，并明确区分 Maven release 与 snapshot 仓库地址。

这套设计说明后端发布是“Jenkinsfile 决策，build.sh 执行”的双层入口，而不是单一脚本或单一流水线规则。

## 前端发布入口

前端发布入口以 `frontends/web` 为主。根 `Jenkinsfile` 会识别 `frontend-web` 引用并跳过后端流水线，说明前后端发布被明确分流。[CI/CD 规则](../../rules/ci-cd.md)记录前后端触发边界，前端 README 说明包级操作。运行单元包括基座与独立 SDK：

- `frontend-web/snapshot/v<版本号>` 用于快照版本。
- `frontend-web/release/v<版本号>` 用于预发布版本。
- `frontend-web-v<版本号>` 用于发布前端基座版本。
- `frontend-web-sdk-v<版本号>` 或 `frontend-web-sdk/v<版本号>` 用于单独发布 SDK。

因此，维护者在讨论构建与发布边界时，需要把后端 Maven 聚合链路和 `frontend-web` 独立发布链路同时纳入设计视图。

## 公共 Maven 制品托管

公共 Java 制品采用 Sonatype：固定版本发布到 Maven Central，开发版本发布到官方 Snapshot 仓库 `https://central.sonatype.com/repository/maven-snapshots/`。共享模块、父 POM、BOM 和 Maven 插件由总仓统一发布，同一坐标和版本不由各分发仓重复上传。

namespace 验证、Snapshot 开通、发布凭据、固定版本签名及匿名消费验收尚未完成。当前完整后端构建仍依赖现有 Maven 仓库与配置；公开源码和分发仓同步不表示公共依赖链已可用，也不触发 Maven、npm、镜像或插件市场发布。

## 独立源码分发

总仓维护实现与测试，模块分发复用 `scripts/open-source/` 的正式清单与工具。独立构建需要的公共支持模块随清单携带，业务代码不为隐藏平台依赖而另行分叉。Data、LogicFlow、脚本、模板、前端基座、审批引擎与编辑器、IDE、Schema 和 Skills 的入口见[开源同步说明](../../../scripts/open-source/README.md)。所有公开仓库的主分支统一为 `master`。
