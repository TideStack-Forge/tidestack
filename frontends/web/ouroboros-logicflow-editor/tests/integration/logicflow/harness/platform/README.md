# Logicflow Editor Integration Platform Helpers

`tests/integration/logicflow/harness/platform/` 承载 logicflow editor 集成测试所需的薄平台注入能力。

## Owns

- 测试版 `__utils__` 注入物
- 真实 amis 渲染适配器
- 通用 DOM 挂载、清理与 flush helper

## Does Not Own

- 逻辑流 fixture
- 节点定义 mock 数据
- 编辑器动作封装
- 跨前后端联调场景文档

这些内容继续留在当前包的 `tests/integration/logicflow/` 或仓库根 `testing/` 目录下。
