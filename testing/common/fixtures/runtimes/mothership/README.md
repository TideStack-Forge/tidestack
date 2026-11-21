# Mothership Runtime Fixture

用于描述 `mothership` 运行基座。

这个夹具重点强调：

- `app.*` 路由应优先在这里验证
- 未认证探针标准与 develop 一致
- 本地 smoke 仍需要显式 datasource 和 runtime 扩展对齐
