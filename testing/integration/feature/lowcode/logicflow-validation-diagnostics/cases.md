# Logicflow Validation Diagnostics Cases

| Case ID | Objective | Preconditions / Input | Mode | Pass Criteria | Evidence |
| --- | --- | --- | --- | --- | --- |
| LF-VAL-FIXTURE-001 | 准备一个稳定返回 validationSnapshot 诊断快照的 logicflow 模型 | 夹具按场景合同准备，管理员身份可登录 | harness | 带 includeValidationSnapshot=true 的保存/读取返回 .data.validationSnapshot，诊断包含目标节点名，正式元数据不保存诊断字段 | fixture harness report, save response |
| LF-VAL-UI-001 | 编辑页显示后端校验错误入口 | develop-shell 已使用 `--logicflow_editor` 注入静态微前端 | harness | 目标模型编辑页可打开，且 diagnostics 入口可见 | browser screenshot, console/network log |
| LF-VAL-DIALOG-001 | 结构化诊断列表可展示目标诊断 | `LF-VAL-FIXTURE-001` 已通过 | harness | 展开 diagnostics 入口后显示诊断列表与目标节点信息 | diagnostics list screenshot, response log |
| LF-VAL-NAV-001 | “定位到节点”能够聚焦到预期节点 | `LF-VAL-DIALOG-001` 已通过，目标节点名已知 | harness | 点击目标诊断项后属性面板出现，且节点名等于 `SMOKE_EXPECTED_NODE_NAME` | browser trace, focused node screenshot |
