# LDAP Login Integration Contract

## Objective

验证 LDAP 接入场景在统一测试体系下具备完整的场景契约，且能够把外部依赖、后端 provider、前端登录交互、首次绑定和权限边界放在同一套证据模型里。

## Scope

- 在范围内：
  - LDAP 服务可达性与基础健康
  - Ouroboros runtime 侧 LDAP provider readiness
  - 开发平台与运行基座登录入口
  - 已映射 LDAP 用户登录成功路径
  - 首次登录绑定路径
  - 权限不足或组织上下文不足路径
- 不在范围内：
  - 非 LDAP 认证方式本身的功能回归
  - LDAP 服务高可用、复制、复杂目录拓扑
  - 与本场景无关的业务页面深链路

## Fixtures

- Services:
  - `services/openldap`
- Runtimes:
  - `runtimes/develop`
  - `runtimes/mothership`
- Frontends:
  - `frontends/develop-shell`
  - `frontends/runtime-shell`
- Data:
  - 无强制数据 fixture
- Identities:
  - `identities/admin`
  - `identities/ldap-user`
  - `identities/first-login-binding-user`
  - `identities/permission-boundary-user`

## Pass Criteria

- 场景可通过统一 fixture harness 生成预检、生命周期计划和证据目录
- LDAP 服务依赖、前端入口、后端入口都能在同一场景资产中被明确声明
- 已映射 LDAP 用户登录成功路径、首次绑定路径、权限不足路径在用例表中分开建模
- 浏览器 happy path 与诊断/harness 路径在同一场景下共存，而不是维护两套脱节资产

## Failure Classification

- 依赖/环境问题：
  - LDAP 服务未启动
  - provider 配置缺失
  - 前端入口 URL 缺失
- 后端问题：
  - LDAP provider 初始化失败
  - 登录 API 返回非预期业务错误
- 前端壳问题：
  - 登录页无法打开
  - 代理链路错误或路由冲突
- 页面交互问题：
  - 首次绑定表单无法完成
  - 登录成功后无法进入预期首页
- 权限与上下文问题：
  - 目录认证成功但无平台权限
  - 组织上下文缺失导致功能边界失败

## Evidence Requirements

- `tmp/testing-evidence/auth-ldap-login/**` 下的 fixture harness 报告
- LDAP 服务和 provider 的关键日志片段
- 浏览器网络/console 证据
- 登录成功、首次绑定、权限不足三个分支的用例记录
