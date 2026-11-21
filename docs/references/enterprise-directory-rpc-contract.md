# 企业目录 API 与 RPC 合同

本期只接三家自建应用。接口定义放在企业 RPC 的 contact 能力包，组织模块的 Source 消费同一份定义，连接/密钥/应用令牌继续由共享企业基础维护。这里记录实现合同和本地验证，不构成真实企业权限验证证据。

| 平台 | RPC 名称后缀 | API 与读取规则 |
| --- | --- | --- |
| 钉钉 | department.get | `POST /topapi/v2/department/get`，读取根部门 1 的名称 |
| 钉钉 | department.children | `POST /topapi/v2/department/listsub`，仅直属子部门，逐层遍历 |
| 钉钉 | user.list / user.get | `POST /topapi/v2/user/list` 按 `has_more/next_cursor` 读完每个部门；详情沿 `/topapi/v2/user/get` |
| 企业微信 | department.list | `GET /cgi-bin/department/list`，只返回应用可见部门 |
| 企业微信 | user.list / user.get | `GET /cgi-bin/user/list` 按部门分别读取；详情沿 `/cgi-bin/user/get`。当前官方合同不使用 `fetch_child` 获取所有后代成员 |
| 飞书 | department.get / department.children | `GET /open-apis/contact/v3/departments/{id}` 和 `/children`，根部门 0；子部门按 `has_more/page_token` 分页 |
| 飞书 | user.list / user.get | `GET /open-apis/contact/v3/users/find_by_department` 和 `/users/{id}`，固定 `user_id_type=user_id`、`department_id_type=department_id` |

RPC 全名为 `{provider}.contact.{后缀}`。旧钉钉及企微 API 通过 query 的 `access_token` 注入，飞书通过 `Authorization: Bearer` 注入。钉钉新 API 使用另一个请求头位置，不将旧协议位置套到所有接口。

钉钉需要部门/成员读取权限及根部门可见范围；飞书从根部门 0 读取要求应用通讯录范围覆盖全部成员，并配置相应部门、成员及 user_id 字段读取权限；企微只能读取应用可见部门及其成员。手机号、邮箱还可能需要字段权限，不从缺失字段推断离职。接口返回权限错误、分页标记缺失、游标不前进或中途失败时，读取失败，不生成完整同步快照。

同一成员在多个部门出现时按稳定成员 ID 合并；资料冲突则要求重新读取。企微明确提供的 `main_department` 可作为主部门；钉钉及飞书当前读取合同没有确认主部门时交给管理员，不取部门列表第一项。账号未激活、冻结或禁用与离职分开处理：不会仅因为未激活/冻结而结束任职。企微 `status=5`、飞书明确 `is_resigned=true` 标记离职；钉钉未读取到的成员仍按既定缺失待核对处理。

每次目录最多处理 20,000 个部门和成员，整体读取沿既有 60 秒预算。超过边界或没有权限读取根部门时明确失败，不提交部分结果。读取发生在本地同步事务之前。

官方依据：

- [钉钉获取部门列表](https://open.dingtalk.com/document/development/obtain-the-department-list-v2)、[获取部门用户详情](https://open.dingtalk.com/document/development/queries-the-complete-information-of-a-department-user)。
- [企微部门列表](https://developer.work.weixin.qq.com/document/path/90208)、[部门成员详情](https://developer.work.weixin.qq.com/document/path/90201)。
- [飞书子部门](https://open.feishu.cn/document/server-docs/contact-v3/department/children)、[部门成员](https://open.feishu.cn/document/server-docs/contact-v3/user/find_by_department)。

2026-11-16 已运行三家 Source 的正常遍历、成员合并、分页完整性、权限失败与游标重复测试；RPC 运行入口及开发测试、托管连接、令牌位置、授权和脱敏也有定向行为测试。真实 classpath 通讯录包的 HTTP 参数合同、正式元数据装配、目录 MySQL 8.3 持久化/事务和生产 Schema 浏览器组件验证均已运行。对应日志为工作区 `tmp/testing-evidence/` 下的 `enterprise-directory-rpc-chain-tests.log`、`enterprise-contact-rpc-contract-tests.log`、`enterprise-completion-audit-regression.log`、`enterprise-mysql-component-tests.log` 及 `enterprise-workspace-browser-final.log`。这些是独立边界的检查，不代表真实服务端、浏览器与企业平台已完成端到端联调；真实企业 scope、可见范围、规模、限额及部署组合仍待验证。
