# Logicflow Validation Diagnostics Data Fixture

这个 fixture 负责通过 develop 平台 API 准备一个“后端校验失败但可保存”的逻辑流模型，以便验证：

- 模型列表与编辑页展示后端校验诊断
- 编辑页的 diagnostics 入口能列出结构化诊断
- “定位到节点”动作能回到对应节点

它不负责浏览器交互本身，也不负责启动前端壳或后端 runtime。

`prepare.sh` 在保存时请求 `includeValidationSnapshot=true`，检查 `.data.validationSnapshot` 的时间、诊断内容和目标节点。正式元数据不持久化诊断，见[场景合同](../../../../integration/feature/lowcode/logicflow-validation-diagnostics/contract.md#当前合同与执行差距)。本地合同测试位于 `tests/test_prepare.py`；后续运行仍须按场景执行并保存证据。
