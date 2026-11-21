# Ouroboros 部署参考（维护者视角）

> 适用于当前 checkout 的维护者部署参考；发布和运行结果需按目标提交、镜像 digest 与环境单独验证。

**口径声明**：面向用户与实施者的部署安装步骤以 `docs/handbook/` 为准，本文件不重复其步骤，只做两件事：

1. 给出**可核实的构建、镜像、配置与迁移事实**，并标注来源 `file:line`，供维护者改代码时同步文档；
2. 用户步骤复用 handbook；发现其与代码不一致时调查并登记差距，不能用任一文案替代实现或验收证据。

用户入口：

- 首次本地体验：[getting-started/index.md](handbook/docs/getting-started/index.md)
- 团队/长期环境：[Docker Compose 安装与环境配置](handbook/docs/operations/deployment-methods/docker-compose.md)、[部署架构](handbook/docs/operations/deployment-architecture.md)
- 升级与回滚：[operations/release-upgrade-and-rollback.md](handbook/docs/operations/release-upgrade-and-rollback.md)

CI/CD、分支与标签的共同规则见 [CI/CD](rules/ci-cd.md) 和 [版本管理](rules/version-control.md)，正式发布操作见[发布 Skill](../.agents/skills/publish-ouroboros/SKILL.md)。本文件记录 `build.sh`、镜像入口与流水线接口事实。

---

## 1. 部署形态总览

| 形态 | 载体 | 仓库内是否存在制品/清单 | 状态 |
|------|------|------------------------|------|
| Docker Compose（平台） | `ouroboros-develop` + MySQL 8.0 + RabbitMQ 3-management | 用户侧 compose 见 handbook `.assets/docker-compose.yml`；仓库内开发用 compose 见 `scripts/deploy/develop-docker-compose/docker-compose.yml` | 支持的本地体验路径（见 handbook 快速上手） |
| 独立安装脚本 | `scripts/deploy/deploy-develop-platform*.{sh,bat}` | 存在 | 历史脚本，硬编码 `OUROBOROS_VERSION=1.0.0-beta.2`（`scripts/deploy/deploy-develop-platform.sh:4`），不是当前支持的安装路径 |
| Kubernetes | 无清单 | 没有平台自身完整安装用的 manifest / Helm chart；仓库中的 Kubernetes 模板与相关代码服务于应用运行后端（`ouroboros-dev-runtime-kubernetes`），用于拉起应用实例，不是平台自身的部署方式 | 见运行时后端配置及平台模块说明 |

镜像组合关系（`build.sh:134-135`、`platform/ouroboros-develop/docker/dockerfile:1`）：

```text
eclipse-temurin:21-jdk-noble  (基础镜像)
        └── ouroboros-web          前端静态资源层
                └── ouroboros-mothership   运行基座（all-in-one：Nginx + Spring Boot）
                        └── ouroboros-develop    开发平台（在基座之上叠加）
```

- 运行时端口：Nginx `listen 80`（`platform/ouroboros-mothership/docker/root/etc/nginx/nginx.conf:44`）、后端 `server.port: 8080`（`platform/ouroboros-mothership/docker/root/app/base/service/application-base.yml:2`）、JDWP `5005`（`EXPOSE 80/8080/5005`，`platform/ouroboros-mothership/docker/dockerfile:39-41`）。
- `ouroboros-develop` 必须与配套 `ouroboros-mothership` 版本一致（handbook `operations/deployment-architecture.md:44`；构建时通过 `-e` 指定基座 tag，`build.sh:158-160`）。

### 1.1 Docker Engine 接入方式

平台需要通过 Docker Engine API 拉起调试实例。当前本地部署按操作系统选择接入方式：

- Linux：挂载 `/var/run/docker.sock`，并设置 `OUROBOROS_DEV_CONTAINER_DOCKERAPI_HOST: unix:///var/run/docker.sock`。
- Windows Docker Desktop：本地体验时开启无 TLS 的 `localhost:2375`，平台容器通过 `tcp://host.docker.internal:2375` 访问；该方式不得暴露到局域网或公网（见 `handbook/docs/getting-started/index.md` 的“开启 Docker API”）。
- 仓库开发用 compose：额外提供 `ouroboros-docker-api` socat 服务（仅 `mac` profile，`scripts/deploy/develop-docker-compose/docker-compose.yml:78-93`），并挂载同一个 socket。
- 配置键：`ouroboros.dev.container.docker-api.host`（`DockerConfiguration.java:12,31`，默认值 `tcp://127.0.0.1:2375`），环境变量绑定名 `OUROBOROS_DEV_CONTAINER_DOCKERAPI_HOST`。

---

## 2. 版本与镜像约定

### 2.1 版本号来源

- Maven 版本是唯一权威来源：`pom.xml:9` 当前为 `2.0.0-rc.1-SNAPSHOT`。
- `build.sh` 默认从 Maven 读取：`mvn help:evaluate -Dexpression=project.version`（`build.sh:120`）；也可用 `-v` 覆盖。
- Jenkins 侧 `VERSION` 为空时从 ref 推导（`Jenkinsfile:87` 与 `extractVersion` 函数）：`release/*` → `<版本>-prerelease`，`snapshot/*` → `<版本>-SNAPSHOT`，tag → 原版本。

### 2.2 tag 规则

`build.sh`（`generate_tags`，`build.sh:563-606`）与 `Jenkinsfile` 的 `generateImageTags` 函数规则一致：

| 版本形态 | 生成的 tags |
|----------|-------------|
| `X.Y.Z` | `X.Y.Z`、`X-latest`、`X.Y-latest`、`latest` |
| `X.Y.Z-SNAPSHOT` / `-prerelease` | 仅 `X.Y.Z-SNAPSHOT` / `X.Y.Z-prerelease` |
| `X.Y.Z-alpha\|beta\|rc[.n]` | `X.Y.Z-...`、`X.Y.Z-alpha\|beta\|rc-latest` |
| `X.Y.Z-hotfix.n` | `X.Y.Z-hotfix.n`、`X.Y.Z` |
| `X.Y.Z+build` | 追加 `X.Y.Z` |

架构 tag（`build.sh:1032-1037`、`1059-1064`、`1228-1235`）：

- **amd64 = 裸 tag**（`<image>:<tag>`），**arm64 = `<image>:<tag>-arm64`**。
- 仓库**不产出 `-amd64` 后缀 tag**；`-a` 时用 `docker buildx --platform linux/amd64,linux/arm64` 构建单一多架构 tag（`build.sh:1053-1056`、`1222-1225`）。
- 因此用户侧 `ARCH_AFFIX` 只有两个合法取值：留空（amd64）或 `-arm64`（handbook `.assets/docker-compose.env.example` 同为该口径）。

### 2.3 镜像地址

- 默认 `registry.cn-hangzhou.aliyuncs.com/ouroboros/`（`build.sh:124-125`；`Jenkinsfile:1-2`），可用 `-r`/`-n` 覆盖。
- 默认基础镜像 `registry.cn-hangzhou.aliyuncs.com/ouroboros/eclipse-temurin:21-jdk-noble`（`build.sh:123`；`Jenkinsfile:3`）；arm64 自动使用同名 `-arm64` 镜像（`build.sh:214`）。

---

## 3. 构建与发布命令

### 3.1 `build.sh` 命令与 flag（源码：`build.sh:32-75`）

| 命令 | 行为 |
|------|------|
| `build` | `mvn install`（含 `versions:set -DnewVersion=<version>`） |
| `deploy` | `build` + deploy 到 Maven 仓库 |
| `publish` | 等价于 `publish-mothership` + `publish-develop` |
| `publish-mothership` | 构建并推送基座镜像 |
| `publish-develop` | 构建并推送开发平台镜像 |
| `preflight` | 本地构建 root + 两个镜像，**不 deploy Maven、不推送** |
| `probe-mothership` / `probe-mothership-arm64` / `probe-develop` | 本地探针镜像，不登录、不推送 |

| flag | 含义 |
|------|------|
| `-v <version>` | 版本号（默认取自 Maven 项目版本） |
| `-w <tag>` | Web 镜像 tag（默认 `latest`） |
| `-e <tag>` | 构建 develop 时使用的基座镜像 tag（默认=版本号） |
| `-b <image>` | 基础镜像（amd64；arm64 probe 自动加 `-arm64`） |
| `-t <tags>` | 发布 tags，逗号分隔（probe 命令只接受一个） |
| `-r` / `-n` / `-u` / `-p` | registry / namespace / 用户名 / 密码 |
| `-c <true/false>` | 是否运行构建阶段测试用例 |
| `-j` | publish 相关命令时是否 deploy Maven（`preflight`/`probe` 使用会直接退出 `exit 2`，`build.sh:980-985`） |
| `-a` | 发布多架构镜像（buildx） |
| `-s <file>` | 状态文件，用于多阶段任务传递状态（`build.sh:331-398`、`480-525`） |

注意：`MOTHSERSHIP_IMAGE_NAME` 为 `ouroboros-mothership`、`DEVELOP_IMAGE_NAME` 为 `ouroboros-develop`（`build.sh:134-135`）；`build.sh` 不做静态检查（checkstyle/PMD 由 Jenkins `verify-*` 阶段负责，`build.sh:109-110`）。

### 3.2 Jenkins（`Jenkinsfile`）

参数（`Jenkinsfile:26-40`）：`VERSION`、`TAGS`、`GIT_BRANCH`、`WEB_IMAGE_TAG`、`MOTHERSHIP_IMAGE_TAG`、`DEPLOY_MAVEN`、`PUBLISH_MOTHERSHIP`、`PUBLISH_DEVELOP`、`BASE_IMAGE`、`REGISTRY_URL`、`NAMESPACE`、`MAVEN_DEPLOY_RELEASE_URL`、`MAVEN_DEPLOY_SNAPSHOT_URL`。

阶段顺序（以 `Jenkinsfile` 的 `stage` 定义为准）：

1. `prepare` — 推导版本/tag/开关，写状态文件
2. `verify-node-npm-baseline` — Node/npm 基线门控
3. `prepare-maven-version` — 设置 Maven 版本
4. `verify-checkstyle` → `verify-pmd` → `verify-package-structure` — 静态检查门控
5. `build` → `./build.sh build -v <version> -s <state> -c <tests>`
6. `deploy` → `./build.sh deploy ...`（仅 `DEPLOY_MAVEN`）
7. `publish-mothership` → `./build.sh publish-mothership -v -w -b -r -n -u -p -c -t -s`
8. `publish-develop` → `./build.sh publish-develop -v -r -n -u -p -c -e -t -s`

`snapshot/*` 默认关闭门控与构建测试；`prepare-maven-version` 在未跳过构建时执行。开启构建测试时，ArchUnit 测试随 `build` 中的 Maven reactor 运行。

文档检查（链接与锚点、文档质量）**不在 CI 阶段内**：日常按需本地运行 `bash scripts/docs/check-links.sh` 与 `bash scripts/docs/check-doc-quality.sh`，发版前由 `bash scripts/ci/preflight-backend-release.sh` 一并执行（`scripts/ci/preflight-backend-release.sh:87-88`）。

触发路径与指令（`[skip-deploy]`、`[skip-publish]`、`[gate-checks:on|off]`、`[build-tests:on|off]`、`[tags:...]` 等）见 `Jenkinsfile` 中的发布判定与指令解析函数，以及 [CI/CD 规则](rules/ci-cd.md)。

---

## 4. 配置与环境变量

### 4.1 容器内配置分层

`start.sh` 用 `--spring.config.location` 按次序加载（`platform/ouroboros-mothership/docker/root/app/base/service/start.sh:125-177`），后者覆盖前者：

```text
classpath:application.yml                            # 镜像内置
/app/base/service/application-base.yml              # 基座默认
/app/metadata/application.yml                        # 应用级
/app/application.yml                                 # 应用自定义
/app/config/*.yml                                    # 应用自定义目录
/app/deploy/application.yml                          # 部署级（宿主机挂载目录，推荐入口）
/app/deploy/config/*.yml                             # 部署级目录
/app/deploy/instance.yml                             # 实例级（最高优先级）
```

Compose 把宿主机部署目录挂到 `/app/deploy`（`scripts/deploy/develop-docker-compose/docker-compose.yml:9`），因此**在部署目录放 `application.yml` 即可生效**，不必把配置塞进环境变量。

### 4.2 环境变量（镜像入口读取，`platform/ouroboros-mothership/README.md:104-116`）

| 变量 | 默认 | 说明 |
|------|------|------|
| `ENABLE_NGINX` | `true` | 是否启动容器内 Nginx |
| `ENABLE_DEPLOY_EXTEND` | 自动检测 | 是否启用 `/app/deploy/extend`（`init.sh`） |
| `DEBUG` / `ENABLE_DEBUG` | `false` | 开启 JDWP 5005 |
| `XMS` / `XMX` | 无 | JVM 堆 |
| `JAVA_OPTS` | 无 | 其他 JVM 参数 |
| `SHOW_CMD` | `false` | 打印完整启动命令 |
| `CUSTOM_CLASS_PATH` / `CUSTOM_BASE_CLASS_PATH` | 无 | 自定义加载路径（分号分隔） |
| `TZ` | — | 时区（compose 设为 `Asia/Shanghai`） |

### 4.3 Spring 可绑定键（已核实）

| 键 | 环境变量形式 | 来源 |
|----|--------------|------|
| `spring.datasource.type` / `driver-class-name` / `url` / `username` / `password` | `SPRING_DATASOURCE_*` | `docker-compose.yml:14-18`（Druid + MySQL） |
| `spring.datasource.migration-strategy` | `SPRING_DATASOURCE_MIGRATION_STRATEGY` | `SpringDataStationDefineProvider.java:28` → `DataStationDefine` → `SqlDataStationBuilder.java:26-27` |
| `spring.rabbitmq.host` / `port` / `username` / `password` | `SPRING_RABBITMQ_*` | `docker-compose.yml:20-23` |
| `spring.data.redis.*` | `SPRING_DATA_REDIS_*` | Boot 3.5.11 元数据；`spring-boot-starter-data-redis`（`platform/ouroboros-mothership/pom.xml:39`） |
| `spring.liquibase.enabled` | — | 基座固定 `false`：`application-base.yml:19-20` |
| `HOST_WORKSPACE_PATH` | `HOST_WORKSPACE_PATH` | `DockerRuntimeAutoConfiguration.java:29`、`PropertyConstant.java:16` |
| `ouroboros.dev.container.docker-api.host` / `username` / `password` | `OUROBOROS_DEV_CONTAINER_DOCKERAPI_*` | `DockerConfiguration.java:12` |
| `ouroboros.dev.workspaceRoot` / `debugSupportFilesDir` / `developSupportFilesDir` | — | `DevAppAutoConfiguration.java:40-46`，镜像默认 `/app/deploy/workspace` 等（`platform/ouroboros-develop/docker/root/app/metadata/application.yml:18-20`） |
| `ouroboros.dev.runtime.default-backend` | — | `DevRuntimeProperties.java:8-10`，取值 `DOCKER`（默认）/ `KUBERNETES`（`BackendType.java`） |
| `ouroboros.dev.runtime.debug.max-lifetime-seconds` / `idle-timeout-seconds` / `broker-host` / `bind-scope` | — | `DevRuntimeProperties.java:38-43` |
| `ouroboros.dev.runtime.kubernetes.namespace` / `shared-pvc-name` / `mounted-workspace-root` / `helper-ready-timeout` / `ingress-host-suffix` | — | `KubernetesRuntimeProperties.java:7-13` |
| `ouroboros.dev.runtime.gateway.*`（含 `apisix.*`） | — | `DevRuntimeProperties.java:86-130` |
| `ouroboros.upload.*` / `ouroboros.upload.fileSystem.rootPath` | — | `application-base.yml:47-59` |
| `ouroboros.metadata.sourceType` / `path` | — | `application-base.yml:41-43` |
| `ouroboros.security.rbac.autoCreateUser.enabled` / `.userName` / `.password` | — | `SecurityRbacManageAutoConfiguration.java:34-41` |
| `ouroboros.platform.cache.token.secret` | `OUROBOROS_PLATFORM_CACHE_TOKEN_SECRET` | 公共缓存管理装配时必填；开发平台兼容配置见 `platform/ouroboros-develop/docker/root/app/metadata/application.yml` |

从旧平台升级的应用须在启动前提供缓存令牌密钥；缺失或空白会在公共缓存管理装配时抛出 `Platform cache token secret is required`。通过部署密钥系统注入随机值，同一部署的节点保持一致；不要使用示例常量，也不要与登录令牌密钥混用。该属性用于缓存条目操作令牌，不替代用户认证配置。

旧上传扩展的编译适配及文件系统后端依赖见[内容存储迁移](guides/content-storage.md#旧配置与-java-接口迁移)。仅升级 BOM 不会把已移除的 Java 类转换为新端口，应用扩展必须重新编译。

**首登口令**：`autoCreateUser.userName` 默认 `admin`；`password` 留空时**随机生成 18 字节口令并打印到日志**（`SecurityRbacManageAutoConfiguration.java:86-92`）。用户步骤见 handbook 快速上手的“登录并验证开发平台”（`docker compose logs ouroboros-develop | grep 'Generated random password'`）。**不要**为部署预设固定口令。

### 4.4 WebSocket broker（`WebSocketBrokerProperties.java`，前缀 `ouroboros.websocket`）

| 键 | 默认 |
|----|------|
| `broker-type` | `simple`（`WebSocketBrokerType`：`SIMPLE` / `RABBITMQ_STOMP_RELAY` / `REDIS_PUBSUB`） |
| `endpoint-path` | `/websocket/stomp` |
| `allowed-origin-patterns` | `*` |
| `sockjs-enabled` | `true` |
| `instance-id` | 缺省取 `spring.application.name`（`RedisBrokerAutoConfiguration.java:29`） |
| `rabbitmq.host` / `.port` / `.virtual-host` | `localhost` / `61613` / `/` |
| `rabbitmq.system-login` / `.system-passcode` | `system` / 无默认 |
| `rabbitmq.client-login` / `.client-passcode` | `client` / 无默认 |
| `rabbitmq.user-destination-broadcast` / `.user-registry-broadcast` | `/topic/user-destination` / `/topic/user-registry` |
| `rabbitmq.system-heartbeat-send-interval` / `.system-heartbeat-receive-interval` | `10000` |
| `rabbitmq.registry-exchange` | `websocket.registry` |
| `redis.topic-broadcast-channel` / `.user-broadcast-channel` / `.session-broadcast-channel` / `.user-registry-channel` | `websocket:topic` / `websocket:user` / `websocket:session` / `websocket:user-registry` |
| `redis.registry-key-prefix` / `.registry-entry-ttl` / `.heartbeat-interval` | `websocket:registry` / `60s` / `20s` |

- broker 生效条件：`ouroboros.websocket.broker-type` 精确匹配 kebab-case 值（`RabbitMqBrokerAutoConfiguration.java:19`、`RedisBrokerAutoConfiguration.java:21`）。
- Redis/AMQP 连接仍走 Spring Boot 标准键：`spring.data.redis.*`、`spring.rabbitmq.*`。
- 当前属性、broker 装配条件与使用步骤的共同正文是[WebSocket 配置指南](../ability/ouroboros-websocket/docs/configuration-guide.md)。尚未同步的工程样例见 DOC-013；修改该能力时先维护配置类及共同指南，再同步本文摘要。

### 4.5 已失效的键与写法（勿再使用）

| 失效写法 | 现状 |
|----------|------|
| `tcp://0.0.0.0:2375` 明文 Docker 端口 | 禁止；Linux 改挂 `/var/run/docker.sock`，Windows 本地体验仅使用 Docker Desktop 提供的 `localhost:2375`，具体见 handbook 快速上手的“开启 Docker API” |
| 按旧 `stomp-port` / `username` / `password` 猜测 WebSocket 环境变量 | 先以当前配置类确认完整属性名，再按 Spring 配置绑定规则转换；不应笼统断言整个 `OUROBOROS_WEBSOCKET_*` 前缀无效 |
| `spring.redis.*` | Boot 3.5 已迁移到 `spring.data.redis.*` |
| `HOST_WORKSPACE_PATH`（作为 compose 可配置项） | 仓库开发用 compose 用 `${PWD}/workspace` 计算（`docker-compose.yml:13`）；**handbook 的 compose 才从 `.env` 读取该变量** |
| `ChangeMe123!` / `guest/guest` | 仓库内 `.env`、仓库 compose 默认值仍是弱口令（`.env:18,22-23`、`docker-compose.yml:18,23`），**仅供本地开发**；部署必须按 handbook 快速上手的本地环境配置使用随机强密码 |
| `/actuator/metrics`、`/actuator/threaddump` | 未暴露。仓库无 `management.endpoints.web.exposure.*` 配置（Boot 默认只暴露 `health`）；应用层只匿名放行 `GET /actuator/health`（`DevelopActuatorHealthAccessConfiguration.java:15`） |

---

## 5. 数据库迁移

### 5.1 现状

- 基座关闭 Spring Boot 的 Liquibase 自动执行：`spring.liquibase.enabled: false`（`application-base.yml:19-20`）。**没有** changelog 目录驱动的数据库迁移。
- 平台自带迁移走**数据模型与数据库快照的 diff**：
  - `SqlDataStation.migrate()`（`core/ouroboros-data/ouroboros-data-sql/src/main/java/com/ouroboros/data/station/SqlDataStation.java:72-93`）委托 `SqlMigrationService.getDefault()`；
  - 实现类 `LiquibaseSqlMigrationService`，核心逻辑在 `DatabaseMigration`（`core/ouroboros-data/ouroboros-data-sql-migration/src/main/java/com/ouroboros/data/migration/DatabaseMigration.java`）：快照 → diff → changelog → `update()` 执行（`:89-94`），并提供只生成 SQL 的 `getMigrationSqls`（`:101-105`）；
  - 数据站级策略来自 `spring.datasource.migration-strategy`，缺省按 `AUTO` 处理（`SqlDataStationBuilder.java:26-27`）；`DISABLED` 时该数据站整体跳过（`SqlDataStation.java:74-76`）；模型级策略来自模型元数据 `migrationStrategy`（默认 `AUTO`，`DataModelMeta.java:28`）。
  - 触发点：数据模型生命周期刷新时对可迁移数据站执行迁移，失败抛 `DatabaseMigrationException`（`DefaultDataModelLifecycleBridge.java:35-80`）。**迁移在进程启动/模型刷新路径上自动发生，没有独立的迁移命令行入口。**
- 数据源：`spring.datasource.*`（默认数据站）+ `ouroboros.additional-datasources`（扩展数据源，`DataSourceConfiguration.java:17`）。

### 5.2 维护者须知

- 升级前必须备份，且**不得假设旧版本能读取升级后的数据库**（handbook `operations/release-upgrade-and-rollback.md:24`）；回滚判断与步骤见同一文档 §执行回滚。
- 需要审查将执行的 DDL 时，使用 `SqlDataStation.generateMigrationSql` / `DatabaseMigration.getMigrationSqls`（`:101`），当前没有对外暴露的 CLI 或 HTTP 入口。
- `spring.datasource.migration-strategy` 的值必须是 `MigrationStrategy` 枚举名（`AUTO` / `DISABLED`，大小写敏感，`SqlDataStationBuilder.java:27`）。

### 5.3 账号资料与通知模板的升级前置

账号公开标识沿[公开身份设计的维护阶段门禁](../ability/ouroboros-oidc-server/identity-profile-design.md#维护阶段执行与门禁)迁移。旧库先备份并进入维护窗口，通过原模型迁移补列，启用 `ouroboros.security.rbac.public-subject.verify-on-startup=true` 分批补齐；已有合法标识保持不变，已删除账号也纳入处理。`requireAfterBackfill` 字段存在空值时保留可空，补齐后的下一次模型迁移才收紧非空，随后重新验证就绪。全局迁移被禁用的应用不能只打开回填开关，须另外完成原模型迁移；需要限定范围时，维护代码可使用 `SqlDataStation.generateMigrationSql(List<DataModel>)` 预览并以 `migrateByNames` 选择已审查的模型，不能直接执行手写 DDL 或未经审查开启全库自动迁移。

就绪条件是物理 `public_subject_id` **非空约束与单列唯一约束同时成立**；约束名无特殊要求，仅有可空列上的唯一索引不满足条件。未就绪节点拒绝新的公开身份签发，普通应用登录和当前账号接口应按各自认证契约排查，不能把 `MISSING_IDENTITY` 一律归因于此门禁。`@Anonymous` 表示允许未认证访问，携带有效 Token 时仍建立对应当前用户；只有明确要求登录的入口才使用 `@PermitAll` 等已认证访问声明，无须为取得当前用户而批量替换匿名注解。

通知模板旧表通过 `NotificationTemplateModel` 的原模型迁移补齐 `name_key`、`revision`、`base_default_revision`、`update_by`、`update_at`、`payload_schema`、`channel`、`is_enabled`。升级前预览差异、检查旧模板名称归一化后是否冲突，按[通知运行时管理指南](../ability/ouroboros-notification/docs/runtime-management-guide.md#部署与升级)处理历史数据；不要只补到首个报错字段。迁移完成后验证模板列表读取和运行时覆盖的并发修订行为；进程启动成功不代表旧表已完成升级。

---

## 6. 回滚

**现状：仓库内没有任何自动化回滚。**

- 当前后端发布流水线只构建与发布，不提供应用/数据库一体化回滚命令；代码中出现 rollback 字样不构成部署恢复能力。
- 平台回滚全靠手工：Compose 场景按 handbook `operations/release-upgrade-and-rollback.md:98-123` 手工把 `.env` 的 `OUROBOROS_VERSION` 退回并重建服务。
- Kubernetes 场景（若用户自行部署）只有集群自身能力可用：`kubectl rollout undo deployment/<name>`；仓库不提供、也不验证该路径。
- Maven 制品回滚同样无自动化：`build.sh deploy` 只做 deploy，不提供版本撤回。
- 因此**每次发布前必须记录镜像 digest 与升级前备份位置**（handbook `release-upgrade-and-rollback.md:125-134` 的完成标准），这是当前唯一可靠的「回滚输入」。

---

## 7. 故障排查与运维入口

- 手册运维排障：[handbook/docs/operations/backup-monitoring-and-troubleshooting.md](handbook/docs/operations/backup-monitoring-and-troubleshooting.md)
- 平台升级/回滚：[handbook/docs/operations/release-upgrade-and-rollback.md](handbook/docs/operations/release-upgrade-and-rollback.md)
- 运行实例与实时链路：[handbook/docs/operations/runtime-capability-maintenance.md](handbook/docs/operations/runtime-capability-maintenance.md)、[handbook/docs/operations/task-message-and-realtime-link-maintenance.md](handbook/docs/operations/task-message-and-realtime-link-maintenance.md)
- 仓库 runbooks：[docs/runbooks/README.md](runbooks/README.md)（其中 [troubleshooting-guide.md](runbooks/troubleshooting-guide.md) 为通用排障方法论）
- Agent 侧闭环脚本：`bash scripts/agent-feedback/check-health.sh`、`bash scripts/agent-feedback/query-logs.sh --help`（见 [runbooks/agent-feedback-loop.md](runbooks/agent-feedback-loop.md)）
- 健康检查：`curl -fsS http://127.0.0.1:${PLATFORM_PORT:-80}/actuator/health`（唯一匿名放行的 actuator 端点；`503` 表示生命周期守卫仍未就绪）

---

## 相关文档

- [handbook · 快速上手](handbook/docs/getting-started/index.md)（从本地启动开发平台到验证第一个业务应用）
- [handbook · 部署架构](handbook/docs/operations/deployment-architecture.md) / [Docker Compose 环境要求与安装](handbook/docs/operations/deployment-methods/docker-compose.md)
- [CI/CD](rules/ci-cd.md)、[版本管理](rules/version-control.md) · 发布触发、版本号与标签规则
- [开发指南](development-guide.md) · 本地开发环境与常用命令
- [开发平台模块说明](../platform/ouroboros-develop/README.md) · 镜像分层、运行时后端
- [运行基座模块说明](../platform/ouroboros-mothership/README.md) · 目录结构、环境变量、扩展机制
- [架构文档](../ARCHITECTURE.md)
