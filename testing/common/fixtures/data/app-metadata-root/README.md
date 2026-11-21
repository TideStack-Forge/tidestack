# App Metadata Root Fixture

这是一个公共数据夹具，用来表达“某个应用元数据根目录”。

它不绑定具体项目，也不默认代表 `mes-business-model` 或任何单一业务仓。

## 这个目录是什么

`appRoot` 指的是应用元数据的根目录，通常会包含：

- `business-model/`
- `ui-schema/`
- `menu-model/`
- 其他应用级 metadata / extend / config 目录

## 这个目录可以从哪里来

这个夹具不把实例路径写死，而是要求执行时注入来源。当前支持的来源优先级：

1. `--app-root`
2. `OB_TEST_APP_ROOT`
3. `--repo app-metadata=/abs/path/to/repo`
4. `OB_TEST_REPOS`

典型来源包括：

- 一个已 checkout 的应用元数据版本库
- 从平台导出的应用包并解压后的目录
- 某次联调临时准备的本地副本

## 推荐做法

默认优先使用显式仓库绑定，而不是依赖某个历史 run 留下来的临时目录。例如：

```bash
python3 testing/common/runners/api/metadata_high_code_data_api_regression.py \
  --mode inventory \
  --repo app-metadata=/abs/path/to/app-metadata-repo
```

只有在以下场景才建议直接传 `--app-root`：

- 应用元数据并不在仓库根目录
- 输入来自导出包/解压目录，而不是仓库 checkout
- 你需要覆盖到某个已经物化好的特定目录

如果场景涉及多个仓库，可以重复传入 `--repo`：

```bash
--repo app-metadata=/abs/path/to/app-metadata-repo \
--repo platform-config=/abs/path/to/platform-config-repo
```

## 派生输出

在 `appRoot` 解析成功后，runner 可以继续派生：

- `businessModelDir = appRoot/business-model`
- `uiSchemaDir = appRoot/ui-schema`

这些是结构性派生结果，不是要在公共夹具里硬编码的实例路径。
