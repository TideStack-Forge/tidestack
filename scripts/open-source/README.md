# 开源模块导出与镜像同步

平台源码在整体仓库统一修改和验证，已有模块仓库按各自导出范围接收更新。导出范围以 `manifests/` 中的清单为准，避免在同步脚本外维护第二份路径白名单。

模块范围用于独立构建和分发。未导出到独立仓库的平台集成模块仍在总仓公开；基础工具库允许共享依赖。Data 和 LogicFlow 的 `support_modules` 声明随分发复制的公开基础源码与测试：两者均包含 `ouroboros-util`，LogicFlow 还包含 `common-code-intelligence`。脚本分发也携带后者，以满足当前类型推断依赖；制品统一由总仓发布。Data 的名称转换与 LogicFlow 的 JSON Schema 校验直接复用该库。旧的 `DataStrings`、`LogicFlowJsonSchemas` 和同包校验结果辅助类型已移除；直接引用这些辅助类型的消费者应改用 `Strings`、`JsonSchemas` 和 `com.ouroboros.util.jsonschema`，DataModel 与 ValidateNode 的对外行为保持原有契约。

## 配置入口

- Data Model：[导出清单](manifests/data-model/export-manifest.yaml)、[包归属](manifests/data-model/package-ownership.yaml)、[模块边界契约](../../core/ouroboros-data/DESIGN.md)。
- LogicFlow：[快照清单](manifests/logicflow/logicflow-public-repo-bootstrap.yaml)、[同步清单](manifests/logicflow/logicflow-public-sync-pipeline.yaml)、[边界配置](manifests/logicflow/logicflow-open-source-boundary.yaml)。
- IDE 插件：使用 [同步脚本](sync-ide-plugins.mjs)和 `ide-plugins/sync.manifest.json` 中已有的仓库映射。
- 脚本组件与项目模板：使用 [组件清单](manifests/components.json)和 [导出入口](export-component.py)，从总仓生成独立分发仓库。

## 本地检查

以下命令在整体仓库根目录运行：

```bash
# Data Model：复制清单选中的源码，执行公开构建与边界检查。
bash scripts/open-source/export-data-model.sh \
  --manifest scripts/open-source/manifests/data-model/export-manifest.yaml \
  --target target/open-source/data-model --mode dry-run

# LogicFlow：生成本地候选快照并检查内容边界。
bash scripts/open-source/logicflow/bootstrap-public-repo-dry-run.sh --check

# IDE 插件：核对镜像差异。
node scripts/open-source/sync-ide-plugins.mjs --check
```

完整参数和已有操作入口见 [scripts/README.md](../README.md)。LogicFlow 的执行报告写入 `tmp/testing-evidence/open-source/`，快照默认写入 `target/logicflow-public-repo-dry-run/`。这些生成结果不提交。

Data 导出会重建目标目录，因此目标必须是独立候选目录，不能直接使用已有公开 Git checkout。验证候选通过后，将公开文件同步到分发仓并保留其 `.git`，再检查差异、提交和推送；导出器会拒绝覆盖包含 Git 元数据的目标。

## 清单组件分发

首次导出要求目标目录不存在或为空，生成以 `master` 为默认分支的独立 Git 仓库并暂存文件，不提交、不推送，也不导入内部历史。使用 Python 3.10+：

```bash
python3 scripts/open-source/export-component.py --component script --target ../tidestack-script
python3 scripts/open-source/export-component.py --component project-templates --target ../tidestack-project-templates

# 比较当前总仓工作区与分发仓库，存在差异时退出码为 1。
python3 scripts/open-source/export-component.py --component script --target ../tidestack-script --check

# 目标完成本地提交且无修改后，可暂存下一次单向同步结果。
python3 scripts/open-source/export-component.py --component script --target ../tidestack-script --update
```

`--update` 只接受本脚本创建且干净的对应仓库，目标有暂存、未暂存或未跟踪修改时会拒绝覆盖。`--check` 允许核对尚未首次提交的候选仓库。总仓工作区包含本次未提交修改也会被导出，因此正式发布应先固定并验证总仓提交。构建、测试、提交和发布分别执行，导出成功只表示文件同步完成。

脚本仓库保留原 Java 包名、模块坐标和源码，重新生成最小 Maven 父 POM；版本来自总仓 POM/BOM。项目模板仓库保留 `project-templates/` 路径及现有校验流程，独立安装校验依赖的步骤见 [模板说明](../../project-templates/README.md)。

Data Model 本地导出和 IDE 只读检查支持尚未首次提交的工作区，来源记录标记为 `unborn-working-tree`。正式同步和 LogicFlow release dry-run 仍需固定来源提交。导出与同步检查不代表整体仓库已完成公开构建或发布验收。

`common-code-intelligence` 包含共享的 `com.ouroboros.script.intelligence` 类型；LogicFlow 的边界按模块依赖判断，继续禁止引入执行适配制品 `common-script`，不再按整个 `com.ouroboros.script` 命名空间排除共享契约。

Data typed-core 的模型服务携带 `common-service`、异常映射及错误码支持模块；异常边界的放行仅适用于 typed-core，Data 内核仍使用自身异常契约。LogicFlow 的 authoring 语义测试携带公共 `common-expression` 源码，生产模块仍通过表达式端口接入，门禁继续禁止生产依赖该实现。前端基座携带 `ouroboros-amis-runtime`；IDE common 携带共享 amis runtime、嵌入式编辑器、SDK、离线元数据源及 XLSX 静态资源，路径适配统一由 IDE 清单执行。

组件清单还覆盖前端基座、审批流引擎、审批流编辑器、Schema 与 Skills。前端分发携带本地 SDK 和必要构建资源；审批引擎保留原 Maven 层级与清单选中的公共支持模块；Schema 索引转换为仓库根相对路径，Skills 在根目录保留安装入口。新增五个分发采用 Apache-2.0，SDK 保留其 ISC 声明。所有公开仓库主分支统一为 `master`，导出器不创建 `main`。
