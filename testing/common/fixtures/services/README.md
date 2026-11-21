# Service Fixtures

这里放外部依赖类夹具，例如 LDAP、OIDC、MySQL、Redis、RabbitMQ。

这些夹具是“按场景声明、按需组合”的可选依赖，不代表所有测试默认都要拉起这些服务。

每个正式服务夹具目录建议包含：

- `fixture.yaml`
- `README.md`
- 可选的生命周期入口

目标是让服务依赖变成可声明、可健康检查、可复位的标准化测试输入。

例如 `services/openldap` 只是“目录服务类依赖”的一个具体实现样例，不能反向推导成公共测试流程默认依赖 LDAP。
