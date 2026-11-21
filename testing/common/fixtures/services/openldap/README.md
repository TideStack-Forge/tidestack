# OpenLDAP Fixture

这个 fixture 用于提供 LDAP 接入测试所需的最小目录服务依赖。

它是一个按需启用的服务夹具样例，不代表公共联调或回归流程默认依赖 LDAP。

## 目标

- 为 LDAP 登录/绑定场景提供统一的外部依赖定义
- 让 harness 能看到服务启动、检查、停止的标准化入口
- 为后续接入更多目录服务实现保留兼容位

## 当前形态

- `up` / `down` 已提供可执行 `docker compose` 命令
- `check` 仍保留为描述性 lifecycle，后续可升级为真正的 bind/health 脚本

## 运行前提

- 本机已安装 `docker` 和 `docker compose`
- 如果要改端口或管理员密码，可通过环境变量覆盖：
  - `OB_TEST_LDAP_PORT`
  - `OB_TEST_LDAP_ADMIN_PASSWORD`
