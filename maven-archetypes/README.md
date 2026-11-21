# Ouroboros Maven Archetype

本聚合目前包含 [ouroboros-module-archetype](ouroboros-module-archetype)，用于生成平台后端模块起点。模板源位于该模块的 `src/main/resources/archetype-resources/`，模板元数据在 `META-INF/maven/archetype-metadata.xml`。

## 构建与交付

聚合和模板均继承根 POM。发布地址沿用根 POM 的 `distributionManagement`：release 使用 `releases` / `${deploy.release.url}`，snapshot 使用 `snapshots` / `${deploy.snapshot.url}`；不要在子 POM 另加一份占位仓库配置。交付规则见 [CI/CD](../docs/rules/ci-cd.md)。

在仓库根目录先安装模板及所需父工程：

```bash
mvn install -pl maven-archetypes/ouroboros-module-archetype -am
```

发布任务获准后，准备对应 Maven settings 与仓库地址，再在根目录执行：

```bash
mvn deploy -pl maven-archetypes/ouroboros-module-archetype -am
```

`-am` 会包含所需上游项目，先核对本次交付范围。命令入口存在不代表模板已发布或已通过生成工程验收。

## 消费

生成时使用 `com.ouroboros:ouroboros-module-archetype`，版本取目标交付版本。团队 Maven settings 需能访问实际发布仓库；IDEA 的 Archetype 配置也使用这些实际坐标与仓库。生成后按模板 README 核对参数、注册资源并构建生成工程。
