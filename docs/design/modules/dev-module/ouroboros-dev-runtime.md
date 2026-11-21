# ouroboros-dev-runtime 模块设计摘要

## 模块定位

`ouroboros-dev-runtime` 是开发平台的运行时编排模块：它把“开发辅助容器”和“测试应用”统一抽象为运行时实例，并在其上提供后端适配（Docker / Kubernetes）、调试会话 broker、工作区供给与网关路由同步。开发平台从“管理应用元数据”走到“真正拉起一个可访问运行环境”，落点就在这一层。

## 责任边界

该模块负责运行时契约与状态模型、后端适配、调试会话与隧道、工作区供给、网关路由同步；不负责应用版本治理，不负责元数据同步本身，也不负责网关产品自身的运维。作为契约层的 `ouroboros-dev-runtime-core`，其 compile 依赖只有 `vavr`、`spring-context` 与 `spring-boot-autoconfigure`，持久化一类基础设施全部以 SPI 外置。

## 依赖关系

父 pom `ouroboros-dev-runtime`（packaging `pom`）聚合 5 个子模块。由子 pom 的 `<dependency>` 可见：`-broker` 只依赖同域 `-core`；`-docker` 依赖 `-core`、`-broker`、同域 `ouroboros-dev-container-core`、`ouroboros-dev-container-docker`、`ouroboros-dev-helper-core`，以及跨域的 `libs/common` `common-dev` 与 ability 域 `ouroboros-message-queue-core`；`-kubernetes` 依赖 `-core`、`-broker`、`common-dev`、`ouroboros-dev-helper-dev`、`io.fabric8:kubernetes-client`，并在 test scope 复用 `-docker` 与 `kubernetes-server-mock`；`-gateway-apisix` 只依赖 `-core`、`spring-boot-autoconfigure`、`spring-web`。

被谁依赖（`git grep` artifactId）：`ouroboros-dev-app-management` 运行时依赖 `-core`，`-kubernetes` 仅为其测试依赖，`ouroboros-dev-module-all` 聚合 `-broker`、`-docker`、`-kubernetes`、`-gateway-apisix`，`bom` 统一管理全部 5 个 artifact，`platform/ouroboros-develop` 经 `ouroboros-dev-module-all` 间接引入。还存在一处反向注入：core 定义的 `PersistentRuntimeLockStore.PersistenceGateway` 由 `ouroboros-dev-app-management` 的 `VCSArtifactBindingStorage` 实现。

## 关键抽象

- `RuntimeBackend`：后端适配契约，按 `RuntimeKind`（`TEST_APP`、`DEV_HELPER`）与 `BackendType`（`DOCKER`、`KUBERNETES`）组织 create/start/stop/restart/delete/getStatus。
- `DevRuntimeService` 与 `DefaultDevRuntimeService`：面向开发平台的运行时门面。
- `RuntimeSpec`、`RuntimeInstance`、`RuntimeIdentityKey`、`RuntimeStatus`、`RuntimeCapability`：运行时输入、实例、身份键、状态与能力模型。
- `RuntimeLockService`、`RuntimeStateStore`、`WorkspaceProvisioner`、`TunnelTransport`、`DebugConnector`：锁、状态、工作区与调试通道扩展点。
- `DevRuntimeEventPublisher` 与 `DevRuntimeEventType`（`RUNTIME_CREATED`、`RUNTIME_STATUS_CHANGED`、`RUNTIME_ENDPOINT_CHANGED`、`RUNTIME_DELETED`、`RUNTIME_FAILED`）：对外事件面。
- `RuntimeGatewayCoordinator`、`GatewayRouteSyncPublisher`、`StableDomainHostResolver`：网关路由同步与稳定域名抽象。

## 关键子模块

- `ouroboros-dev-runtime-core`：110 个 main 文件中的 75 个，承载契约、模型、默认内存实现、调试会话 store 与网关抽象，注册 `DevRuntimeCoreAutoConfiguration`。
- `ouroboros-dev-runtime-broker`：`DefaultTunnelBroker`、`BrokerPortAllocator`、`TcpTunnelTransport`、`DefaultDebugSessionService`、`DebugSessionReaper`，注册 `DebugRuntimeBrokerAutoConfiguration`。
- `ouroboros-dev-runtime-docker`：`DockerDevHelperRuntimeBackend`、`DockerTestAppRuntimeBackend`、`DockerDebugConnector`、`DockerRuntimeEventBridge`、`DockerRuntimeMapper`、`HostPathWorkspaceProvisioner`。
- `ouroboros-dev-runtime-kubernetes`：`KubernetesDevHelperRuntimeBackend`、`KubernetesTestAppRuntimeBackend`、`KubernetesRuntimeResourceFactory`、`KubernetesRuntimeStatusSynchronizer`、`KubernetesRuntimeLeaseCleaner`、`PvcWorkspaceProvisioner`。
- `ouroboros-dev-runtime-gateway-apisix`：`ApisixAdminClient` 与 `RestTemplateApisixAdminClient`、`ApisixRouteMapper`、`ApisixGatewayRouteSyncConsumer`。

## 扩展机制

扩展方式是新增 `RuntimeBackend`、`WorkspaceProvisioner`、`TunnelTransport`、`DebugConnector`、`RuntimeLockStore` 等 SPI 实现，而不是在默认实现里加分支。5 个子模块都在 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 中同时注册各自的自动配置类；`-gateway-apisix` 额外要求 `ouroboros.dev.runtime.gateway.enabled=true` 且 `provider=apisix`，缺省不激活。`RuntimeBackendResolver` 按 `List<RuntimeBackend>` 与 `DevRuntimeProperties`（前缀 `ouroboros.dev.runtime`，默认 backend 为 `DOCKER`）完成选择。共享规则见 [../../cross-cutting/spring-autoconfiguration.md](../../cross-cutting/spring-autoconfiguration.md) 与 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `暂无独立深度设计文档`：`dev-module/ouroboros-dev-runtime/**` 下未发现 `DESIGN.md`、`README.md` 或 `docs/*.md`。
- 相关专题：[../../layers/application-layer.md](../../layers/application-layer.md) 记录了该模块的分层归属。

## 演进状态

模块已完成按后端拆分，边界基本收敛，但有三点值得维护者留意。其一，`core` 承载 75/110 个文件，契约、默认内存实现和网关抽象混在同一 artifact，继续追加运行时模型时需要防止它单向膨胀。其二，网关只有 `apisix` 一个真实 provider，`-gateway-apisix` 仅 7 个文件，且其装配同时受 `enabled` 与 `provider` 两个属性约束（`DevRuntimeProperties` 中缺省为 `enabled=false`、`provider` 为空串），缺省路径不会触达该子系统。其三，两个后端对 helper 的接入路径并不对称：`-docker` 直接依赖 `ouroboros-dev-helper-core` 与 `ouroboros-message-queue-core`，`-kubernetes` 走 `ouroboros-dev-helper-dev`；新增后端前应先明确这条差异是否有意为之。
