# 开发者工具与交付资产

支持高低代码融合开发、元数据编辑、项目起步和独立包交付。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| IDE 与元数据 | IDEA/VS Code 插件共享 schema 资源和 runtime；提供元数据可视化编辑、项目绑定，IDEA 包含部署调试入口，各宿主能力以对应 README 为准。 |
| Schema 与生成资产 | metadata-schema 提供 Draft 2020-12 合同；Archetype 与项目模板生成源码起点。 |
| 扩展依赖打包 | 独立 Maven 插件根据运行时 manifest 过滤基座已提供依赖。 |
| 通用库 | common 提供异常、Web、脚本、模板、缓存、生命周期等基础抽象；util/excel/zip 提供通用及专门工具，不定义领域策略。 |
| 面向开发者的 AI 资产 | skills 是平台对外的开发 Skill 产品，ai_automation 是自动化规格和 prompt/参数资产；与 .agents/skills 下本仓维护任务区分。 |
| 产品手册 | handbook 为用户/开发者提供产品说明和机器阅读入口，生成或发布状态需另取证据。 |

## 实现情况

IDE 公共资源和独立构建边界已存在；各独立仓库当前同步、市场发布和线上运行状态需按交付基线核实。自动化规格/示例存在不表示调度任务已启用。legacy-compat 和 micro-app 中的历史代码不按主线功能承诺。

本页按源码与配置说明能力。测试入口说明可复跑资产，实际运行与线上发布状态由对应版本的交付证据确认。

## 启用条件

按 IDE/npm/Gradle/Maven 各自工程准备工具；导出必须使用正式 manifest/scripts，保留 LICENSE/包装边界与固定源提交。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [libs/common](../../libs/common) · [设计/指南](../../libs/common/GUIDE.md) | [测试入口](../../libs/common/common-app/src/test/java/com/ouroboros/lifecycle/OuroborosContextRefreshLifecycleTest.java) |
| [libs/util/ouroboros-util](../../libs/util/ouroboros-util) · [设计/指南](../design/modules/libs/ouroboros-util.md) | [测试入口](../../libs/util/ouroboros-util/src/test/groovy/com/ouroboros/util/TestFunctions.groovy) |
| [libs/util/ouroboros-util-excel](../../libs/util/ouroboros-util-excel) · [设计/指南](../design/modules/libs/ouroboros-util-excel.md) | 未检出独立行为测试；按上层/交付场景判断 |
| [libs/util/ouroboros-util-zip](../../libs/util/ouroboros-util-zip) · [设计/指南](../design/modules/libs/ouroboros-util-zip.md) | [测试入口](../../libs/util/ouroboros-util-zip/src/test/java/com/ouroboros/util/ZipWriterFixedTimestampTest.java) |
| [ide-plugins/common/metadata-editor-runtime](../../ide-plugins/common/metadata-editor-runtime) | [测试入口](../../ide-plugins/common/metadata-editor-runtime/tests/unit/metadata-editor.test.ts) |
| [ide-plugins/common/metadata-editor-schema](../../ide-plugins/common/metadata-editor-schema) | 未检出独立行为测试；按上层/交付场景判断 |
| [ide-plugins/intellij-plugin](../../ide-plugins/intellij-plugin) | [测试入口](../../ide-plugins/intellij-plugin/src/test/kotlin/com/ouroboros/idea/metadata/MetadataDocumentSaveCoordinatorTest.kt) |
| [ide-plugins/vscode-plugin](../../ide-plugins/vscode-plugin) | [测试入口](../../ide-plugins/vscode-plugin/tests/package.test.mjs) |
| [metadata-schema](../../metadata-schema) | 未检出独立行为测试；按上层/交付场景判断 |
| [maven-plugins/ouroboros-dependency-maven-plugin](../../maven-plugins/ouroboros-dependency-maven-plugin) | [测试入口](../../maven-plugins/ouroboros-dependency-maven-plugin/src/test/java/com/ouroboros/maven/extension/dependency/ExtensionDependencyFilterTest.java) |
| [maven-archetypes](../../maven-archetypes) | 未检出独立行为测试；按上层/交付场景判断 |
| [skills](../../skills) | 未检出独立行为测试；按上层/交付场景判断 |
| [ai_automation](../../ai_automation) | 未检出独立行为测试；按上层/交付场景判断 |
| [docs/handbook](../handbook) | 未检出独立行为测试；按上层/交付场景判断 |

## 关联文档

- [ide-plugins/README.md](../../ide-plugins/README.md)
- [metadata-schema/README.md](../../metadata-schema/README.md)
- [docs/guides/repository-sync.md](../guides/repository-sync.md)
