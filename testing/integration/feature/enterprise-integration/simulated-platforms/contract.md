# 企业集成外部平台仿真验收

本次按用户确认，以外部平台仿真验证内部机制作为交付验收。使用正式 Mothership 构件、原认证/RBAC/配置/数据/RPC/通知及回调实现，数据库和 Redis 为专用真实容器。仿真服务只替换钉钉、企微、飞书的网络边界，不替换平台服务、认证过滤器、令牌缓存或持久化。

仿真服务在隔离 Docker 网络中以供应商域名别名接收 HTTPS，请求仍使用生产 URL、字段和令牌位置。测试 CA 仅放入本次 JVM truststore，保留主机名和证书验证；不修改系统 DNS 或证书，不改生产端点和来源约束。隔离网络禁止访问真实企业，所有消息和写 API 只产生仿真记录。控制接口仅向本机发布，使用一次性密钥，证据不保存密码、原始令牌或应用密钥。

仿真服务覆盖应用及用户令牌、一次性授权码/PKCE、分页组织目录、消息接收、回调挑战及独立签名/加密，提供按接口注入失败、限流和未知结果的测试控制。测试断言平台 API 结果、实际接收请求、目录/绑定事实和持久任务状态，不以源码匹配证明功能。

双正式后端节点共用测试库和 Redis，验证节点间轮换、停用和版本冲突。已有配置/权限迁移和可靠受理数据库回归继续作为各自证据，不能把仿真结果描述为供应商认证、真实终端或生产迁移通过。这些实际环境差异在本次验收后作为发布核对项，不再阻止本次内部机制交付。

运行说明及场景结果随实现记录；全部自有容器、网络和进程在结束时清理，生成的私钥及运行时只保留在忽略的 `tmp/` 内。

## 运行

在 `feat/ent-integration` worktree 根目录执行。使用 Java 21、Maven、Python 3、Node、OpenSSL、Docker 和现有 Playwright 浏览器；先构建正式 Mothership 及前端产物。后端依赖清单位于 `platform/ouroboros-mothership/target/runtime.classpath`，沿原 `maven-dependency-plugin` 的 `build-classpath` 生成。本场景不替换 RPC 模型或供应商生产 URL。

```sh
python3 testing/integration/feature/enterprise-integration/simulated-platforms/scripts/run.py \
  --root tmp/enterprise-integration/simulation-acceptance
```

`--root` 必须是本 worktree `tmp/` 内尚不存在的目录。镜像需已存在：Java 21 的项目 Temurin 镜像、`docker.1ms.run/node:lts-alpine`、`container-registry.oracle.com/mysql/community-server:8.3.0`（amd64）、`redis:7-alpine`。夹具只检查镜像，不隐式下载；启动前核对正式依赖文件，按 manifest 记录并校验自有资源。

runner 依次执行外部服务契约、正式 HTTP 场景、原可靠通知服务、生产浏览器、MySQL 暂停/恢复、主密钥重加密与实际双节点重启、持久事实复核，最后清理。失败也清理已创建的自有容器和网络；保留受限配置及脱敏证据便于复查。需要手工清理中断夹具时执行：

```sh
python3 testing/integration/feature/enterprise-integration/simulated-platforms/fixtures/runtime.py down \
  --root tmp/enterprise-integration/simulation-acceptance
```

`RuntimeMechanisms.java` 仅在测试侧启动原应用上下文，调用真实领域服务和 QueryDSL 观察事实，不贡献测试 Controller、不注册替代 Bean。两个被测 HTTP 节点始终使用正式构件。场景普通通知由测试调用方明确批准；业务通知的身份和披露边界继续由原业务事件组件回归证明。
