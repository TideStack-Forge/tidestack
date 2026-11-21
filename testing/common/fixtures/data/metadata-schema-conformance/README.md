# 元数据 Schema 一致性夹具

`cases.json` 描述业务模型、动作接口、审批资源、配置、逻辑流、RPC 和 UI 资源的结构边界。每项包含 `id`、元数据 `type`、预期 `valid` 与原始 `document`；可选 `path` 指定旧资源目录，否则按类型贡献的固定文件名或后缀构造路径。夹具使用内联测试数据，不依赖具体应用或已部署环境。

预期结果以平台正式元数据加载和契约校验为依据。typed UI 资源需要 `name/schema` 包装；旧 `ui-schema/` 目录和 `ui-schema-content` 类型校验组件内容。表达式编译、跨资源引用和流程部署不属于结构夹具的验收范围。

公共分发脚本将此数据同步到手册与 IDE Schema 包，供手册 Ajv、TypeScript Host、浏览器 Host、IntelliJ networknt 及可携带的 Python 校验器共用。修改后在平台根目录运行 `python3 scripts/skills/sync_distribution.py`；添加 `--check` 检查分发漂移。
