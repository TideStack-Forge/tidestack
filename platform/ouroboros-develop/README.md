# Ouroboros Develop

Ouroboros 低代码平台的开发环境镜像。基于 `ouroboros-mothership` 基座镜像构建，包含开发平台特有的配置和模块。

## 核心特性

- **继承基座能力**：复用 `ouroboros-mothership` 的全部扩展机制和配置体系
- **开发平台定制**：内置开发环境默认配置、工作区路径、调试支持
- **多运行时支持**：Docker 和 Kubernetes 双后端，适配不同部署场景
- **完整开发工具链**：测试应用管理、开发 helper、后端 API 隧道会话

## 镜像构建

### 基础镜像

```dockerfile
ARG BASE_IMAGE=registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-mothership:latest
FROM $BASE_IMAGE

# 静态层
COPY ./root /

# 公共层
COPY ./common-layer /

# 模板层
COPY ./template-layer /

# 构建层
COPY ./build-layer /
```

`ouroboros-develop` 在 `ouroboros-mothership` 基础上叠加四层：

1. **静态层**：应用元数据（`/app/metadata/`）
2. **公共层**：共享模块和配置
3. **模板层**：经过发布校验的项目模板产物
4. **构建层**：开发平台特有模块

### 构建命令

```bash
# 在目标 worktree 根目录查看正式构建入口
bash build.sh --help
# 本地镜像探针（会构建 reactor、准备镜像层和模板/文档产物）
bash build.sh probe-develop -t develop-probe
```

### CI/CD 发布

分支、MR 与发布引用规则以[版本管理](../../docs/rules/version-control.md)和[CI/CD](../../docs/rules/ci-cd.md)为共同正文。镜像上下文为 `platform/ouroboros-develop/docker/`，构建文件为 `dockerfile`；仅执行 Maven package 不会准备全部镜像层。

## 容器内目录结构

继承 `ouroboros-mothership` 的目录结构，额外添加：

```
/app
├── base/                           # 基座级（继承自 mothership）
│   └── service/
│       ├── ouroboros-mothership.jar
│       ├── ouroboros-lib/
│       └── third-party-lib/
├── metadata/                       # 应用级元数据（开发平台定制）
│   ├── application.yml             # 开发平台默认配置
│   ├── app.metadata.json           # 应用元信息
│   └── extend/                     # 开发模块扩展包
├── deploy/                         # 部署级（宿主机挂载）
│   ├── extend/                     # 部署扩展包（默认禁用）
│   ├── workspace/                  # 开发工作区
│   ├── logs/                       # 日志输出
│   └── application.yml             # 部署配置
├── debug-support/                  # 调试支持文件
└── develop-support/                # 开发支持文件
```

## 环境变量

继承 `ouroboros-mothership` 的全部环境变量，额外支持：

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `OUROBOROS_PLATFORM_CACHE_TOKEN_SECRET` | `ouroboros-develop-platform-cache-token` | 平台缓存令牌密钥 |
| `HOST_WORKSPACE_PATH` | 无 | 宿主机工作区路径（用于文件系统映射） |

完整的基座环境变量参见 [ouroboros-mothership/README.md](../ouroboros-mothership/README.md)。

## 默认配置

### application.yml（镜像内置）

```yaml
spring:
  application:
    name: ouroboros-develop
  servlet:
    multipart:
      max-file-size: 200MB
      max-request-size: 200MB
  mvc:
    async:
      request-timeout: -1

ouroboros:
  platform:
    cache:
      token:
        secret: ${OUROBOROS_PLATFORM_CACHE_TOKEN_SECRET:ouroboros-develop-platform-cache-token}
  dev:
    workspaceRoot: /app/deploy/workspace
    debugSupportFilesDir: /app/debug-support
    developSupportFilesDir: /app/develop-support
    runtime:
      default-backend: docker
      kubernetes:
        namespace: ouroboros-dev
        mounted-workspace-root: /workspace
        helper-ready-timeout: 30s
    container:
      dockerApi:
        host: tcp://host.docker.internal:2375
```

## 使用指南

### 基本运行

```bash
docker run -d \
  --name ouroboros-develop \
  -p 88:80 \
  -p 5005:5005 \
  -e ENABLE_DEBUG=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-develop:latest
```

### docker-compose 部署

```yaml
services:
  ouroboros-develop:
    image: registry.cn-hangzhou.aliyuncs.com/ouroboros/ouroboros-develop:${VERSION}
    ports:
      - "88:80"
      - "15005:5005"
    volumes:
      - ${PWD}:/app/deploy
    environment:
      - ENABLE_DEBUG=true
      - HOST_WORKSPACE_PATH=${PWD}/workspace
    networks:
      - ouroboros-dev
```

### 开启远程调试

```bash
docker run -d \
  --name ouroboros-develop \
  -p 88:80 \
  -p 5005:5005 \
  -e ENABLE_DEBUG=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-develop:latest

# IDEA 配置 Remote JVM Debug
# Host: localhost
# Port: 5005
```

### 切换到 Kubernetes 后端

```yaml
# application.yml 或环境变量
ouroboros:
  dev:
    runtime:
      default-backend: kubernetes
      kubernetes:
        namespace: ouroboros-dev
        shared-pvc-name: ouroboros-dev-runtime-workspace
```

## 扩展包管理

继承 `ouroboros-mothership` 的扩展机制：

- **基座级**：`/app/base/service/ouroboros-lib`（不可变）
- **应用级**：`/app/metadata/extend`（镜像内置）
- **部署级**：`/app/deploy/extend`（宿主机挂载，默认禁用）

### 添加开发模块

开发模块通常打包在镜像内：

```dockerfile
FROM ouroboros-mothership-base:latest
COPY my-dev-module.jar /app/metadata/extend/
```

### 清理部署级扩展残留

如果 `/app/deploy/extend` 存在旧版本扩展包，可能导致资源重复加载：

```bash
# 检查部署级扩展目录
docker exec <container> ls -la /app/deploy/extend

# 清理残留
docker exec <container> rm /app/deploy/extend/old-module.jar
```

详细说明参见 [ouroboros-mothership/README.md](../ouroboros-mothership/README.md) 的故障排查章节。

---

# 运行时配置说明

`ouroboros-develop` 把测试应用和开发 helper 的拉起能力抽象到统一运行时层，支持两类后端：

- `DOCKER`
- `KUBERNETES`

其中测试应用当前开放两类暴露策略：

- `TEMP_PORT`
- `STABLE_DOMAIN`

`INTERNAL_ONLY` 枚举已经预留，但测试应用创建界面暂未开放。

开发 helper 当前只支持后端切换，不区分 `TEMP_PORT` / `STABLE_DOMAIN`。

这份文档说明：

- Docker / Kubernetes 相关配置项
- 各类组合方式和适用场景
- Gateway / Ingress 的边界
- 已有的测试和验证方法

## 1. 配置优先级

测试应用运行时相关配置的优先级如下：

1. 创建请求显式传入的参数
2. 配置中心中的平台默认值
3. 应用配置文件中的兜底值

当前后端和暴露策略的解析规则：

- 后端类型：`request.backendType -> dev-app-management.test-app.preferred-runtime-backend -> ouroboros.dev.runtime.default-backend`
- 暴露策略：`request.exposureStrategy -> dev-app-management.test-app.default-exposure-strategy -> TEMP_PORT`
- 测试应用镜像：`request.image -> dev-app-management.test-app.container.image`

## 2. 核心配置项

### 2.1 应用配置文件

建议在 `application.yml` 或等价配置源中声明运行时基础配置：

```yaml
ouroboros:
  dev:
    runtime:
      default-backend: docker
      debug:
        max-lifetime-seconds: 1800
        idle-timeout-seconds: 600
        broker-host: 127.0.0.1
        bind-scope: LOOPBACK_ONLY
      gateway:
        enabled: false
        provider: apisix
        stable-domain-host-suffix: develop.example.com
        apisix:
          base-url: http://127.0.0.1:9180
          admin-key: change-me
          route-id-prefix: develop-runtime
          connect-timeout-millis: 2000
          read-timeout-millis: 5000
      kubernetes:
        namespace: ouroboros-dev
        shared-pvc-name: ouroboros-dev-runtime-workspace
        mounted-workspace-root: /workspace
        helper-ready-timeout: 45s
        ingress-host-suffix: develop.example.com
    container:
      dockerApi:
        host: tcp://127.0.0.1:2375
        username:
        password:
```

说明：

- `ouroboros.dev.container.docker-api.*` 是 Docker 远程 API 配置；默认 host 为 `tcp://127.0.0.1:2375`
- `ouroboros.dev.runtime.default-backend` 默认是 `DOCKER`
- `ouroboros.dev.runtime.gateway.*` 用于稳定域名网关同步
- `ouroboros.dev.runtime.kubernetes.*` 用于 Kubernetes 后端
- Docker 镜像中的默认元数据配置目前是 `default-backend: docker`，且 Docker API host 缺省指向 `tcp://host.docker.internal:2375`

### 2.2 配置中心中的平台默认值

测试应用的默认行为还会受到以下配置中心键影响：

- `dev-app-management.test-app.container.image`
- `dev-app-management.test-app.port-range`
- `dev-app-management.test-app.preferred-runtime-backend`
- `dev-app-management.test-app.default-exposure-strategy`
- `dev-app-management.test-app.default-datasource.driver-class-name`
- `dev-app-management.test-app.default-datasource.url-template`
- `dev-app-management.test-app.default-datasource.username`
- `dev-app-management.test-app.default-datasource.password`
- `dev-app-management.test-app.memory-datasource.driver-class-name`
- `dev-app-management.test-app.memory-datasource.url`

补充说明：

- `dev-app-management.test-app.port-range` 只对 `DOCKER + TEMP_PORT` 的默认端口生成有效
- 测试容器当前只直接暴露 `web` 端口（80），后端 API 端口（8080）通过按需隧道会话访问

## 3. Docker 模式

### 3.1 适用场景

适合本机开发、单机联调、以及通过独立网关做稳定域名暴露的场景。

### 3.2 基础要求

1. 安装并启动 Docker
2. 打开 Docker Remote API
3. 配置 `ouroboros.dev.container.docker-api.host`

常见开启方式：

- Windows Docker Desktop
  - `Settings -> General -> Expose daemon on tcp://localhost:2375 without TLS`
- macOS
  - Docker Desktop 不能直接改 daemon 配置时，可以用 `socat` 容器转发：

```bash
docker run -d \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -p 127.0.0.1:2375:2375 \
  bobrik/socat \
  TCP-LISTEN:2375,fork UNIX-CONNECT:/var/run/docker.sock
```

如果平台本身也运行在 Docker 容器里，通常要把 host 配成 `tcp://host.docker.internal:2375`。

### 3.3 组合行为

#### Docker + TEMP_PORT

- 测试应用使用宿主机端口映射
- 当前只映射 `web:80`，后端 API 通过隧道会话访问
- 工作区挂载方式是 `HOST_PATH`
- 创建页会展示 web 端口字段，并从 `dev-app-management.test-app.port-range` 自动给出默认值
- 适合本机临时联调

#### Docker + STABLE_DOMAIN

- 不走宿主机端口输入
- 当前 APISIX 适配器会把稳定域名直接路由到容器名 `ouroboros-test-app-{appName}-{profile}:80`
- 当前 gateway 自动同步只覆盖 `DOCKER + TEST_APP + STABLE_DOMAIN`
- 当前只下发 web 路由，不单独为 api 生成一条稳定域名路由
- 如果未启用 gateway，仅有域名预览，没有实际路由下发

## 4. Kubernetes 模式

### 4.1 适用场景

适合需要把运行时放到集群中统一调度、使用 PVC 共享工作区、或者正式稳定暴露的场景。

### 4.2 基础要求

除了配置 `ouroboros.dev.runtime.kubernetes.*` 之外，还需要为平台提供 `KubernetesClient` Bean。`ouroboros-dev-runtime-kubernetes` 的自动装配是以 `KubernetesClient` 为前提触发的。

一个最小示例：

```java
@Bean
public KubernetesClient kubernetesClient() {
  return new KubernetesClientBuilder().build();
}
```

Fabric8 客户端会优先使用本机 `kubeconfig`，在集群内运行时也可以走 in-cluster 配置。

另外需要准备：

- 可写的 namespace
- 共享 PVC，对应 `ouroboros.dev.runtime.kubernetes.shared-pvc-name`
- Ingress Controller；如果要走稳定域名，还要配置 `ouroboros.dev.runtime.kubernetes.ingress-host-suffix`

### 4.3 组合行为

#### Kubernetes + TEMP_PORT

- 不使用用户手工填写的端口映射
- 测试应用会创建 `NodePort Service`
- 当前会为 `web` 生成确定性的 NodePort，后端 API 通过隧道会话访问
- 工作区挂载方式是 `PVC`
- 更适合本地单节点集群或临时环境

注意：

- 当前状态同步里拼装的访问地址是 `http://127.0.0.1:{nodePort}`
- 因此这条链路更适合像 OrbStack / Docker Desktop Kubernetes 这类本机集群
- 如果是共享集群或跨主机环境，优先使用 `STABLE_DOMAIN`

#### Kubernetes + STABLE_DOMAIN

- 创建 `ClusterIP Service + Ingress`
- 不依赖运行时 gateway 下发路由
- 稳定域名由 `appName-profile.<ingress-host-suffix>` 生成
- 更适合共享环境和正式预览环境

## 5. Gateway 与 Ingress 的边界

当前有两条"稳定域名"链路，边界要分清：

### 5.1 Docker 稳定域名

- 依赖 `ouroboros.dev.runtime.gateway.enabled=true`
- 当前已实现 provider：`apisix`
- 路由同步条件：
  - 后端是 `DOCKER`
  - 运行时类型是 `TEST_APP`
  - 暴露策略是 `STABLE_DOMAIN`

示例：

```yaml
ouroboros:
  dev:
    runtime:
      gateway:
        enabled: true
        provider: apisix
        stable-domain-host-suffix: develop.example.com
        apisix:
          base-url: http://127.0.0.1:9180
          admin-key: your-admin-key
          route-id-prefix: develop-runtime
```

### 5.2 Kubernetes 稳定域名

- 不走 APISIX route sync
- 直接由 Kubernetes `Ingress` 负责暴露
- 依赖 `ouroboros.dev.runtime.kubernetes.ingress-host-suffix`

建议：

- 如果同时支持 `Docker + STABLE_DOMAIN` 和 `Kubernetes + STABLE_DOMAIN`，把 `ouroboros.dev.runtime.gateway.stable-domain-host-suffix` 与 `ouroboros.dev.runtime.kubernetes.ingress-host-suffix` 配成同一个后缀
- 这样创建页的稳定域名预览和 Kubernetes Ingress 实际域名才不会分叉

## 6. 组合矩阵

| 后端 | 暴露策略 | 工作区 | 对外访问方式 | 典型用途 |
| --- | --- | --- | --- | --- |
| Docker | TEMP_PORT | HOST_PATH | 宿主机端口映射 | 本机临时联调 |
| Docker | STABLE_DOMAIN | HOST_PATH | 容器名 + Gateway 路由 | 单机稳定预览、网关接管 |
| Kubernetes | TEMP_PORT | PVC | NodePort | 本机集群、短期试运行 |
| Kubernetes | STABLE_DOMAIN | PVC | ClusterIP + Ingress | 共享环境、正式预览 |

## 7. 推荐组合

### 7.1 本机开发默认方案

- 后端：`DOCKER`
- 暴露：`TEMP_PORT`
- 适合日常开发和问题定位

### 7.2 单机平台但希望统一入口

- 后端：`DOCKER`
- 暴露：`STABLE_DOMAIN`
- 配合 `APISIX` 之类网关
- 适合把端口映射从平台逻辑里剥离出去

### 7.3 本机 Kubernetes 验证

- 后端：`KUBERNETES`
- 暴露：优先 `TEMP_PORT`
- 适合 OrbStack / Docker Desktop Kubernetes 本地 smoke

### 7.4 共享或正式预览环境

- 后端：`KUBERNETES`
- 暴露：`STABLE_DOMAIN`
- 配合 PVC、Ingress Controller、统一域名后缀

## 8. 测试与验证

下面这些链路已经在当前运行时改造过程中被纳入验证范围。

### 8.1 应用管理模块回归

```bash
mvn -pl :ouroboros-dev-app-management -am test \
  -Dtest=AppContainerServiceRuntimeDelegationTest,AppContainerServiceImplBaseImageTest,AppRuntimeInitServiceTest,AppContainerControllerBaseImageTest,AppContainerControllerRuntimeInitTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

### 8.2 Docker runtime 编译校验

```bash
mvn -pl :ouroboros-dev-runtime-docker -am test -DskipTests
```

### 8.3 本地 browser bootstrap 契约测试

```bash
bash scripts/dev-runtime/test-bootstrap-develop-browser-e2e-local-contract.sh
```

### 8.4 本地 Docker browser 联调 bootstrap

```bash
bash scripts/dev-runtime/bootstrap-develop-browser-e2e-local.sh
```

相关契约说明：

- `platform/ouroboros-develop/e2e/README.md`
- `platform/ouroboros-develop/e2e/contracts/runtime-management.local-bootstrap.contract.md`
- `platform/ouroboros-develop/e2e/contracts/runtime-management.smoke.contract.md`

### 8.5 Kubernetes 契约与 mock 集群 smoke

```bash
bash scripts/dev-runtime/test-smoke-k8s-runtime-contract.sh
mvn -pl :ouroboros-develop -Dtest=PlatformDevelopKubernetesRuntimeSmokeTest test
```

### 8.6 Kubernetes 真实集群 smoke

启用环境变量：

- `K8S_PLATFORM_REAL_SMOKE=true`
- `K8S_SMOKE_NAMESPACE`
- `K8S_SMOKE_PVC`
- `K8S_SMOKE_INGRESS_SUFFIX`

执行：

```bash
mvn -pl :ouroboros-develop -Dtest=PlatformDevelopKubernetesRealClusterSmokeTest test
```

### 8.7 APISIX gateway 真实联调 smoke

启用环境变量：

- `APISIX_GATEWAY_REAL_SMOKE=true`
- `APISIX_GATEWAY_BASE_URL`
- `APISIX_GATEWAY_ADMIN_KEY`
- `APISIX_GATEWAY_STABLE_DOMAIN_SUFFIX` 可选

执行：

```bash
mvn -pl :ouroboros-dev-runtime-gateway-apisix -Dtest=ApisixGatewayRealSmokeTest test
```

## 9. 小结

如果只需要把现有 Docker 方案平滑跑起来，最小配置是：

- 打开 Docker Remote API
- 设置 `ouroboros.dev.container.docker-api.host`
- 保持 `ouroboros.dev.runtime.default-backend=docker`

如果要切到 Kubernetes，需要额外补齐三件事：

1. 提供 `KubernetesClient` Bean
2. 配置 namespace / PVC / workspace mount
3. 根据场景决定走 `NodePort` 还是 `Ingress`

如果要做稳定域名统一入口：

- Docker 线优先走 `Gateway`
- Kubernetes 线优先走 `Ingress`

## 10. 后端 API 隧道会话

测试容器当前只直接暴露 web 端口（80），后端 API 端口（8080）通过按需隧道会话访问。

### 10.1 隧道会话机制

隧道会话提供临时的后端 API 访问通道，工作方式类似于调试会话：

- 按需创建：需要访问后端 API 时才创建会话
- 独立管理：与调试会话独立，可以同时存在
- 自动清理：会话有生命周期限制，超时自动关闭

### 10.2 API 端点

请求使用 `OpenDebugSessionRequest`；返回使用 `WebApiResult<DebugSessionDTO>`。正式入口：

| 操作 | 路由 |
| --- | --- |
| 打开会话 | `POST /api/dev/runtime/backend-api-sessions` |
| 查询会话 | `GET /api/dev/runtime/backend-api-sessions/{sessionId}` |
| 关闭会话 | `DELETE /api/dev/runtime/backend-api-sessions/{sessionId}` |

创建请求在 body 传 `appName`、`profile`，并按目标运行时提供 `runtimeKind`、`backendType` 和所需的 `expectedGeneration` 等字段。用响应 `data.sessionId` 管理该会话；访问地址在 `data.endpoint`，不是 `tunnelEndpoint`。成功结果的 `code` 为 `0`，状态及主机/端口以返回 DTO 为准，不硬编码会话 ID 或临时端口。

### 10.3 使用场景

- API 调试和测试
- 后端服务健康检查
- 数据查询和管理操作
- 与前端分离的后端开发

### 10.4 与调试会话的区别

| 特性 | 调试会话 | 后端 API 会话 |
| --- | --- | --- |
| 目标端口 | 调试端口（如 5005） | 后端 API 端口（8080） |
| 用途 | Java 远程调试 | HTTP API 访问 |
| 会话管理 | 独立 | 独立 |
| 可同时存在 | 是 | 是 |

## 相关文档

- [Ouroboros Mothership](../ouroboros-mothership/README.md) - 基座镜像文档
- [架构文档](../../ARCHITECTURE.md) - 整体架构说明
- [部署指南](../../docs/deployment-guide.md) - 部署配置详解
