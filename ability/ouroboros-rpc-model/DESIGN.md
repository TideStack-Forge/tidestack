# ouroboros-rpc-model 设计文档

> 本文是 `ouroboros-rpc-model` 的当前权威设计入口，结论以当前源码为准（审计基线 `2.0.0-rc.1-SNAPSHOT`）。
> 路径约定：`core/` = `ouroboros-rpc-model-core`，`runtime/` = `ouroboros-rpc-model-runtime`，`dev/` = `ouroboros-rpc-model-dev`，`lf/` = `ouroboros-rpc-model-logicflow-node`；`…/` = `src/main/java/com/ouroboros/rpc/`。
> 本文吸收了 `docs/requirements/` 下三篇 RPC 规格的有效结论，并显式标注规格与实现的差异（见第 9 节）。

开发期工作区模型的保存与测试演进见 [RPC 模型测试设计](docs/model-testing.md)（已实现，含前后端机制、接口合同、限制与验证记录）；本文中的运行态测试调用仍按现有实现描述。

## 1. 模块定位与子模块结构

`ouroboros-rpc-model` 是能力层（ability）的外部服务调用模型模块：把远程调用定义、认证凭据、元数据来源和逻辑流节点组织成统一 RPC 能力，让平台把外部接口当作模型化资源消费，而不是在各业务模块零散拼接 HTTP 调用代码。

聚合 `pom.xml` 声明四个子模块（`pom.xml:16-21`）：

| 子模块 | 职责 | 关键内容 |
|--------|------|----------|
| `core` | 无 Spring 容器的契约层 | `RPCModelDefinition`、`RPCRequest`/`HttpRPCRequest`、`RPCAuthCredential`、两个 Center、两类 metadata + 两类 SPI、`execution/*` 调用上下文与拦截器契约、`RPCException`/`AuthenticateException` |
| `runtime` | 执行、认证实现、元数据 provider、运行时治理与自动装配 | `RpcInvocationExecutor`、4 个拦截器、`OpenApiRpcProtocolInvoker`、5 类认证实现、`runtime/*` 策略与状态、`/api/sys/rpc-runtime` |
| `dev` | 开发期管理入口 | `@DevelopModule("rpc-model")`、`@DevelopModule("rpc-auth")` 两个 `BaseDevelopService`、`RPCModelInspector`、`src/main/metadata/dev/**` 表单资源 |
| `lf` | LogicFlow 接入 | `CallRpcNode`（节点 + `NodeBuilder`）、`CallRpcNode.flow-node.json` |

`runtime` 的依赖面（`runtime/pom.xml:15-111`）：`core`、`common-app`、`common-metadata`、`common-expression`、`common-cache`、`common-web`、`common-exception-mapper`、`ouroboros-data-typed-runtime`、`ouroboros-configuration-core`、`json-path`、`swagger-annotations`。`core` 不依赖 Spring 容器，`dev`、`lf` 依赖 `core` 和 `runtime`；开发期测试复用同一协议执行链。

## 2. 对外契约

### 2.1 发布的 SPI（必须写在 `META-INF/services/`）

| SPI | 注册实现 |
|-----|----------|
| `RPCModelDefinitionBuilder` | `OpenApiRpcModelDefinitionBuilder` |
| `RPCModelMetadataProvider` | `FileRpcModelMetadataProvider$Proxy`、`JarRPCModelMetadataProvider` |
| `RPCAuthCredentialBuilder` | `AccessTokenRpcAuthCredential$Builder`、`PlainRpcCredential$Builder`、`HttpBasicRpcAuthCredential$Builder`、`HmacSignatureRpcAuthCredential$Builder` |
| `RPCAuthCredentialMetadataProvider` | `FileRpcAuthCredentialMetadataProvider$Proxy`、`JarRPCAuthCredentialMetadataProvider` |

`lf` 另注册 `com.ouroboros.logicflow.nodes.NodeBuilder` → `CallRpcNode$Builder`。

### 2.2 发布的自动配置

三个子模块都双处注册 `RpcRuntimeAutoConfiguration` / `RpcDevAutoConfiguration` / `RpcLogicflowNodeAutoConfiguration`（`META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`）。`RpcRuntimeAutoConfiguration` 是 `@ComponentScan + @DataModelScan`，并声明 `OpenApiRpcProtocolInvoker` 与 `RpcInvocationExecutor` 两个 `@Bean`（`…/RpcRuntimeAutoConfiguration.java:24-55`）。

### 2.3 发布的 Java 契约

- 定义侧：`RPCModelDefinition`（`getName/getTitle/getGroup/getType/getAuthCredential/getExtraProps` + `Simple` 实现）。
- 调用侧：`runtime` 提供 `RpcInvocationExecutor.invoke(RpcInvocationRequest) -> RpcInvocationResult`；`core` 定义请求、结果、`RpcInvocationContext`（含 `internalCall`/`purpose`/`options`/拦截器间 `attribute` 通道）、`RpcInvocationInterceptor`（`@Order` 成链）、`RpcProtocolInvoker`（`supports` + `invoke`）。
- 认证侧：`RPCAuthCredential.applyToRequest(request, context) -> Try<T>`。

### 2.4 发布的管理 API 与 UI 资源

- REST：`/api/sys/rpc-runtime` 下 7 个端点（列表、详情、`PUT …/policy`、`POST …/actions/{health-check,reset-status,repair-policy,test-invoke}`），见 `…/runtime/controller/RpcRuntimeController.java:35-188`；权限资源 `rpcRuntimeManagement.authority.json` 与其逐条对应。
- 菜单与页面：`rpcRuntimeManagement.menu.json`（`systemSetting.rpcRuntimeManagement`，`/system-setting/rpc-runtime-management`）、`app-modules.json`、`rpc-runtime-management.ui-model.json`、`system-setting/rpc-runtime-{management,detail}.ui-schema.json`。
- 开发期页面：`dev/` 下的 `/dev/{appName}/rpc-model` 与 `/dev/{appName}/rpc-auth`（4 个凭据类型 schema：`AccessToken`、`PlainCredentials`、`HttpBasic`、`HmacSignature`）。

### 2.5 发布的数据契约

| 契约 | 形态 | 位置 |
|------|------|------|
| 模型元数据 | `<home>/rpc-model/**.rpc.json` → `RPCModelMetadata` | `FileRpcModelMetadataProvider.java:38-39` |
| 凭据元数据 | `<home>/rpc-model/auth/**.rpc.auth.json` → `RPCCredentialMetadata` | `FileRpcAuthCredentialMetadataProvider.java:38-39` |
| classpath 元数据 | 同名后缀，由 `common-metadata` 的 `TypedMetadataCatalog` 扫描 | `JarRPCModelMetadataProvider.java:25-40` |
| 运行时策略 | `@Model(rawName = "t_sys_rpc_runtime_policy")` | `RpcRuntimePolicyModel.java:13` |
| 运行时状态 | `@Model(rawName = "t_sys_rpc_runtime_status")` | `RpcRuntimeStatusModel.java:10` |

### 2.6 谁消费

- `ability/ouroboros-ability-all/ouroboros-ability-all-core` 引入 `core`（`pom.xml:112`），`-runtime` 引入 `runtime`（`pom.xml:222`）与 `lf`（`pom.xml:290`）。
- `dev-module/ouroboros-dev-module-all` 引入 `dev`（`pom.xml:142`）。
- `dev-module/ouroboros-dev-helper` 直接使用 `core` 的元数据类型与两个 metadata provider SPI：`RPCMetadataAdapter`、`RPCAuthMetadataAdapter`、`PRCMetadataCollector`、`RPCAuthMetadataCollector`（`pom.xml:36`）。
- `bom/pom.xml:1169-1184` 统一管理四个构件版本。

## 3. 核心模型与装载机制

- `RPCModelDefinition` 保留 `name/title/group/type/authCredential/extraProps`。`group` 是用于浏览的可选分组路径；OpenAPI 的请求定义在子类 `OpenApiRpcModelDefinitionBuilder.OpenApiRpcModelDefinition.getRequest()` 上；`timeout`/`retry` 从 `extraProps` 读取。
- `RPCModelDefinitionCenter`、`RPCAuthCredentialCenter` 都是**纯静态**中心：`refresh()` 时用 `SPI.getSortedServiceStream` 拉取 provider 元数据（同名后者覆盖，`TreeMap(CASE_INSENSITIVE)`），过滤 `isEnabled`，再按 builder `support()` 命中第一个构建；构建失败的条目**只记 error 并丢弃**，不会中断装载（`RPCModelDefinitionCenter.java:43-77`、`RPCAuthCredentialCenter.java:51-90`）。
- 刷新时机：`RpcRuntimeAutoConfiguration` 监听 `OuroborosMetadataRefreshedEvent` 后刷新两个中心（`…/RpcRuntimeAutoConfiguration.java:35-39`）。运行时策略/状态不依赖该事件，读写直接落库。

### 3.1 分组声明与浏览

沿用 `RPCModelMetadata.group`，开发平台与高代码共用同一字段。值为完整可读路径，例如 `钉钉/通讯录`，以 `/` 分隔层级；普通字面量是一级分组。各段去除首尾空白，空值归入“未分组”，路径中不允许空层级。不建分类表、分类 ID 或模型分类关系；模型调用和权限始终以原 `name` 为依据，改分组不改模型名称。

```json
{
  "name": "dingtalk.contact.user.get",
  "title": "钉钉读取成员详情",
  "type": "OpenAPI",
  "group": "钉钉/通讯录"
}
```

以上仅展示相关字段；请求与凭据配置继续按原模型声明。Java 元数据提供者调用 `setGroup("钉钉/通讯录")`。JAR 模型的分组由包内元数据声明，开发平台编辑的是应用自己的模型，不在运行时改写已安装 JAR。

开发平台表单可以选择已有路径或输入新路径，也可以清空；已有路径来自正式 `MetadataProviderMixer` 合并后的元数据，包含安装模块及应用元数据并保留原覆盖优先级。开发列表、运行管理列表和逻辑流模型选择弹窗统一使用“左侧分组树、右侧模型列表”。分组树提供“全部模型”和“未分组”；点击父分组包含该分组自身及所有子分组的模型，按路径段区分同名前缀。名称或标题搜索在当前分组范围内生效。运行管理只展示分组，源定义仍在开发平台或高代码中维护。

开发与运行列表直接在各自 UI Schema 中声明原生 `input-tree` 和 CRUD 的过滤配置。逻辑流选择器在原生 Picker 的 `pickerSchema` 中声明同样的树和列表布局，设置 `filter.canAccessSuperData` 读取完整模型集合。列表加载、分页、弹窗确认、取消与选中值由 AMIS 组件管理。分组路径格式化器 `rpcModelGroups` 由完整的 `itemsRaw` 集合派生分组树，搜索、筛选或分页不会裁掉分组，刷新后随最新模型重建。分组节点的值为 `group:<完整路径>`，只作为界面过滤条件；未分组节点与名为“未分组”的实际分组具有不同路径值。

逻辑流 `rpc-model` 属性转译器使用现有元数据接口的 `mode=raw` 获取带 `group` 的平面模型列表，交给标准 Picker 展示。用户在右侧选模型并确认后提交 RPC `name`；取消保留原值，已存名称通过原选项加载机制回显标题。调用方自定义 source 继续支持原 `value`/`label` 形态，也可显式指定 `valueField`、`labelField`。

`mode=groups` 返回去重后的完整路径及祖先路径，供分组输入框选用。默认 `select`、`raw` 和详情接口沿用原合同、应用访问控制与元数据装配机制。

`group` 从元数据经定义构建器传到运行时注册项和列表/详情响应。`RPCModelDefinition.getGroup()` 默认返回空值，旧构建器与原 `Simple` 构造方式仍归入未分组。第三方协议构建器如需展示分组，应在定义中保留该值。

## 4. HMAC/签名与鉴权链路

### 4.1 认证在调用链中的位置

`OpenApiRpcProtocolInvoker.invoke` 的顺序是：取 `HttpRPCRequestDefine` → `HttpRpcRequestBuilder.buildRequest(requestDefine, parameters)` → `authenticate(...)` → 取 `SerializedBody` → `WebClient` 发送（`…/execution/protocol/OpenApiRpcProtocolInvoker.java:49-109`）。

`authenticate` 的判定（同文件 `:113-125`）：`authCredential` 为空 → 不加认证直接放行；凭据名在 `RPCAuthCredentialCenter` 查不到 → `AuthenticateException("RPC模型[x]引用的认证凭据[y]不存在")`；否则 `credential.applyToRequest(request, parameters)`。

### 4.2 五类凭据

| 类型值 | 实现 | 形态 |
|--------|------|------|
| `AccessToken` | `AccessTokenRpcAuthCredential$Builder` 按 `accessToken.mode` 分派：`static` → `StaticAccessTokenRPCAuthCredential`，否则 `DynamicAccessTokenRPCAuthCredential`（`:45-59`） | 默认写 `Authorization: Bearer ${accessToken}`，可用 `accessToken.customPlacement` 覆盖位置 |
| `PlainCredentials` | `PlainRpcCredential$Implementation` | 按 `plainCredentials.credentials`（单个 Map 或列表）逐条注入，`in` 取 `HttpRPCRequestParameterDefine.InScope`（`PATH/QUERY/HEADER/COOKIE/BODY`） |
| `HttpBasic` | `HttpBasicRpcAuthCredential$Implementation` | `Authorization: Basic base64(user:pass)` |
| `HmacSignature` | `HmacSignatureRpcAuthCredential$Implementation` | 按 preset 注入 header 或 query |
| 历史签名占位项 | 已被 `HmacSignature` 取代，代码中不存在兼容分支 | — |

动态令牌获取通过统一执行器发起内部调用（`internalCall=true`、`purpose="token-fetch"`），并用 `ThreadLocal` 做自递归保护（`DynamicAccessTokenRPCAuthCredential.java:63-100`、`AbstractAccessTokenRPCAuthCredential.java:68-78`）。

### 4.3 HMAC 时序与单元

`Implementation.applyToRequest` 的固定时序（`HmacSignatureRpcAuthCredential.java:98-118`）：

1. `preset.resolveContext(request, config, variables, configGetter, clock, bodySerializer)`：求值表达式、补齐 vendor 时间戳/nonce/action/version/securityToken、序列化请求体、算出 payload hash。
2. 把 `SerializedBody` 写入请求属性 `rpc.serializedBody`（`SerializedBody.REQUEST_ATTRIBUTE`），并在缺失时补 `Content-Type`。
3. `preset.canonicalize(...)` → `buildStringToSign(...)` → `sign(...)` → `apply(request, context, signature)`。

关键单元：

| 单元 | 职责 |
|------|------|
| `HmacSignatureConfig` | 把 `extraProps.hmacSignature` 映射为稳定配置对象；`preset/accessKey/secretKey` 必填，`signedHeaders.include/exclude` 先 trim+小写去重，交集直接失败（`:74-128`） |
| `HmacSignaturePreset` | 9 方法契约：`getName`、`isHeaderSignature`、`getRequiredSignedHeaders`、`validate`、`resolveContext`、`canonicalize`、`buildStringToSign`、`sign`、`apply`（`hmac/HmacSignaturePreset.java:10-35`） |
| `HmacSignaturePresetRegistry` | **硬编码**注册 6 个 preset 并按小写名索引；`requireSupported` 负责 preset 合法性 + 必需 header 不可被 exclude（`:19-53`） |
| `ResolvedSigningContext` | 本次签名使用的逻辑上下文（AK/SK、region/service/action/version/nonce、timestamp 文本、serializedBody、canonicalRequest） |
| `CanonicalHttpRequest` | 承载 method/path/query/canonicalHeaders/signedHeaders/payloadHash 并拼 canonical request 文本 |
| `HmacSigningSupport` | SHA-256、HMAC、RFC3986 编码、query 合并/规范化排序、header 规范化、URI/host 处理 |
| `HmacRequestBodySerializer` | 生成"用于签名"的稳定 body 文本；空 body/空 map → `SerializedBody.empty()`；非 JSON / multipart / form-urlencoded / octet-stream / 二进制 → `AuthenticateException`；对象键用 `TreeMap` 保证字典序 |

**签名与发送共用同一份 body 文本**：`OpenApiRpcProtocolInvoker` 优先取 `request.getAttribute(SerializedBody.REQUEST_ATTRIBUTE)`，用它的 `bodyText` 调 `bodyValue(...)`（`:80-96`）。

### 4.4 六个 preset 的实际契约

| preset | 落点 | 必需字段 | 备注 |
|--------|------|----------|------|
| `aws_sigv4` | `Authorization: AWS4-HMAC-SHA256 Credential=AK/date/region/service/aws4_request, SignedHeaders=…, Signature=…` | `region`、`service` | 派生密钥 `AWS4+SK → date → region → service → aws4_request`；`payloadHashMode ∈ {auto, empty_sha256, unsigned_payload}`，其中 `empty_sha256` **只在空 body 时接受**（`:159-170`）；注入 `x-amz-date`、`x-amz-content-sha256`、可选 `x-amz-security-token` |
| `tencent_tc3` | `Authorization: TC3-HMAC-SHA256 …` | `service` | scope `date/service/tc3_request`；canonical uri 固定 `/`；时间戳为 Unix 秒；注入 `X-TC-Timestamp/-Action/-Version/-Region/-Token`；`x-tc-region`、`x-tc-token` 存在时进签名头 |
| `huawei_sdk_hmac_sha256` | `Authorization: SDK-HMAC-SHA256 Access=…, SignedHeaders=…, Signature=…` | 无 | string to sign 就是 canonical request；`X-Sdk-Date`；`content-type` 存在时进签名头；可选 `X-Security-Token` |
| `volcengine_hmac_sha256` | `Authorization: HMAC-SHA256 Credential=AK/date/region/service/request …` | `region`、`service` | 派生密钥 `SK → date → region → service → request`；注入 `X-Date`、可选 `X-Security-Token` |
| `aliyun_acs3` | `Authorization: ACS3-HMAC-SHA256 Credential=…, SignedHeaders=…, Signature=…` | `action`、`version`（配置或请求 header 二选一） | 时间戳 `yyyy-MM-dd'T'HH:mm:ss'Z'`；nonce 配置/请求/自动生成三级；注入 `x-acs-date`、`x-acs-signature-nonce`、`x-acs-content-sha256`、可选 action/version/token |
| `aliyun_rpc_v1` | query `Signature` | `action`、`version`（配置或 query 二选一） | canonical uri 固定 `/`，string to sign = `METHOD&%2F&percentEncode(canonicalQuery)`，密钥 `SK + "&"`；算法 `HMAC-SHA1`（默认）或 `HMAC-SHA256`（`aliyunRpcV1.signatureMethod`）；canonical query 排除 `Signature` 自身；`payloadHashMode` 被忽略并记 `warn` |

其余边界：`accessKey`/`secretKey`/`securityToken`/`region`/`service`/`payloadHashMode`/`signedHeaders`/`aliyunRpcV1.signatureMethod` 只允许来自配置；`action`/`version`/`nonce` 允许配置或请求二选一（配置优先）；时间戳只由请求或 signer 提供；`region`/`service` 不允许从 host/header 反推（`resolveRequiredValue`，`AbstractHeaderHmacSignaturePreset.java:100-112`）。表达式求值走 `ExpressionUtils.eval`，支持 `#{配置项}` 与 `${EL}`，配置读取先 `ConfigurationService` 后 `Environment`（`HmacSignatureRpcAuthCredential.java:35-43`）。

## 5. 错误语义

### 5.1 异常类型与错误码

- `RPCException extends OuroborosException`，`DEFAULT_ERROR_CODE = 3000`（`exception/RPCException.java:6`）。
- `AuthenticateException extends RPCException`，未覆盖错误码 → 也是 `3000`（`exception/AuthenticateException.java:3-15`）。**模块内没有第二套 RPC 专用错误码**。

### 5.2 各类失败的对外表现

| 场景 | 类型 | 位置 |
|------|------|------|
| 模型不存在 | `RPCException("RPC模型不存在: x")` 作为 `RpcInvocationResult.failure` | `RpcInvocationExecutor.java:28` |
| 无匹配协议执行器 | `RPCException("未匹配到协议执行器: type")` | `RpcInvocationExecutor.java:36` |
| 已建立调用上下文后，拦截器链或协议调用抛出 `Exception` | 包成 `RPCException("调用失败", cause)` | `RpcInvocationExecutor.java:50-55` |
| 请求定义缺失 / 构建请求失败 | `RPCException("请求定义不存在")` / `RPCException("构建请求失败", cause)` | `OpenApiRpcProtocolInvoker.java:53,61` |
| 认证失败（`applyToRequest` 返回 failure） | **`RPCException("认证失败", cause)`**，cause 才是 `AuthenticateException` | `OpenApiRpcProtocolInvoker.java:70-73` |
| 远端调用失败（含 HTTP 4xx/5xx） | `RPCException("调用失败", WebClientResponseException)` | `OpenApiRpcProtocolInvoker.java:105-108` |
| 策略被禁用 / 不可用 | `RPCException("RPC模型已被禁用: x")` / `("RPC模型当前不可用: x")` | `AvailabilityRpcInvocationInterceptor.java:36,40,50` |
| 策略/状态读写失败 | `OuroborosExceptionMapper` 映射后包 `InternalException` | `RpcRuntimePolicyServiceImpl.java:174-176`、`RpcRuntimeStatusServiceImpl.java:127-129` |
| 策略写入非法值 | `ValidationException`（`timeoutOverrideMs<=0`、`retryOverride<0`、`forcedAvailabilityStatus` 非 `HEALTHY/UNAVAILABLE`） | `RpcRuntimePolicyServiceImpl.java:107-149` |
| 凭据元数据没有匹配 builder | `PlatformException("未找到合适的构建器")`（仅用于装载期丢弃） | `RPCAuthCredentialCenter.java:77` |
| `modelName` 为空 | `IllegalArgumentException` | `RpcRuntimePolicyServiceImpl.java:101-105` |
| LogicFlow 节点拿到 failure | `FlowRuntimeException` | `CallRpcNode.java:69-71` |

### 5.3 本地错误与远端错误的区分（重要）

协议层通过 `RpcExecutionException.stage` 区分请求构建、认证、传输、HTTP 状态、解码和超限。开发期测试映射为稳定 reason 与脱敏 evidence，普通运行仍返回原 `RpcInvocationResult`，不新增远端状态对应的全局数值错误码：

- 正常调用出口是 `RpcInvocationResult`（`isSuccess/payload/error/durationMs`），结果中的失败通过 `getError()` 取异常，没有"本地/远端"或"可重试"标记。请求读取、定义查找与协议 `supports()` 在 Executor 的 try/catch 之外；空请求或这些阶段抛出的异常不保证转换为 failure。
- 远端 HTTP 错误由同一 WebClient exchange 读取实际状态和正文后返回 `RpcExecutionException("HTTP_STATUS", ...)`。2xx 解码失败标记 `RESPONSE_DECODE`，无响应保留 null；204 等空成功响应不再误报解码失败。
- 错误码只有 `3000`（RPC 域）与平台异常码，远端 4xx/5xx 不映射成独立码，运维侧只能靠 message + cause 类型推断。
- 好处是敏感信息不外泄：远端响应体不会进入 message，只作为 cause 保留。

### 5.4 认证链路的错误收敛（已落地）

- 元数据文件：目录不存在 → 空列表且不记 error；单文件读取失败/空文件/JSON 非法 → `warn`（带文件路径）+ 跳过；目录扫描失败 → `warn`（带目录）+ 空列表（`FileRpcModelMetadataProvider.java:50-93`、`FileRpcAuthCredentialMetadataProvider.java:50-93`）。
- 静态令牌：字面量为空、配置键为空、配置读取为空 → 三类带 credential 名称（配置键场景带 key）的 `AuthenticateException`（`StaticAccessTokenRPCAuthCredential.java:32-47`）。
- 令牌注入：token 为空 → `AuthenticateException("认证凭据[x]获取令牌失败")`；placement 表达式求值失败 → 带 credential + 参数名 + 参数位置的 `AuthenticateException` 并保留 cause（`AbstractAccessTokenRPCAuthCredential.java:82-109`）。
- 动态令牌：缺 `tokenModelName`、执行器不可用、递归、结果为 null、结果失败、路径解析失败、取值空、取到对象/数组或自动探测失败 → 统一 `AuthenticateException`，消息为 `认证凭据[x]动态访问令牌获取失败[tokenModelName=y]：detail`，下游原因追加 `，原因:<message>` 并保留 cause（`DynamicAccessTokenRPCAuthCredential.java:63-131,191-231`）。
- expires 降级：解析失败/类型非法 → `warn` + 回退 `defaultExpireInMs`，无默认值回退 `0`，**不使认证失败**（`DynamicAccessTokenRPCAuthCredential.java:133-214`）。
- 明文凭据：单个参数定义非法（非 Map、`name`/`in`/`value` 缺失或空白、`in` 不在 `InScope`）→ `warn` + 跳过；全部非法 → 调用期 `AuthenticateException("认证凭据[x]没有有效的明文认证参数定义")`；求值失败 → 带 credential + 参数名 + 参数位置（`PlainRpcCredential.java:81-152`）。
- HMAC：配置字段非法/缺失、preset 不支持、`signedHeaders` 交集、必需 header 被 exclude、body 类型不支持 → `AuthenticateException`，消息含 credential 名称、字段名、preset，且不含 secret/token/签名原文。

## 6. 运行时治理

### 6.1 策略与状态分离

- `RpcRuntimePolicyModel`（`t_sys_rpc_runtime_policy`）：`modelName`(PK)、`tenantId`、`enabled`、`loggingEnabled`、`logRequestBody`、`logResponseBody`、`maskHeaders`、`maskFields`、`metricsEnabled`、`availabilityEnabled`、`forcedAvailabilityStatus`、`timeoutOverrideMs`、`retryOverride`、`updatedAt`、`updatedBy`，另有非持久化的 `normalizationWarnings`/`hasNormalizationWarnings`。
- `RpcRuntimeStatusModel`（`t_sys_rpc_runtime_status`）：`modelName`(PK)、`tenantId`、`availability`、`lastHealthCheckAt/Success`、`lastInvokeAt`、`lastSuccessAt`、`lastFailureAt`、`lastFailureReason`、`consecutiveFailureCount`、`successCount`、`failureCount`、`avgLatencyMs`。
- 无持久化记录时 `defaultPolicy` 为 `enabled=true`、其余开关 `false`、override 为 `null`（`RpcRuntimePolicyServiceImpl.java:61-71`）；`getPolicy` 走宽松 normalize（非法值降级 + `normalizationWarnings`），`updatePolicy` 走严格校验并抛 `ValidationException`（`:77-149`）。`repairPolicy` 用当前合法配置覆盖落库（`RpcRuntimeOperationServiceImpl.java:67-71`）。

### 6.2 统一执行管线与拦截器顺序

`RpcInvocationExecutor.invoke`：查定义 → 匹配 `RpcProtocolInvoker`（`supports`）→ 建 `RpcInvocationContext` → 按 `ObjectProvider.orderedStream()` 顺序成链（index 0 为最外层）→ 最内层调 `invoker.invoke(context)`；任何异常统一转 `RpcInvocationResult.failure`（`RpcInvocationExecutor.java:25-74`、`RpcRuntimeAutoConfiguration.java:46-55`）。

实际拦截器与顺序（`@Order` 升序 = 由外向内）：

| 顺序 | 拦截器 | 职责 |
|------|--------|------|
| 0 | `RuntimePolicyRpcInvocationInterceptor` | 读策略 → 挂 context attribute `rpcRuntimePolicy`，把 `timeoutOverrideMs`/`retryOverride` 写入 context option |
| 100 | `AvailabilityRpcInvocationInterceptor` | `enabled=false`、`forcedAvailabilityStatus=UNAVAILABLE`、或 `availabilityEnabled` 且状态 `UNAVAILABLE` → 直接短路失败；旁路：option `bypassPolicyGuards=true`，或 `internalCall && purpose="health-check"` |
| 200 | `LoggingRpcInvocationInterceptor` | 仅 `loggingEnabled=true` 且非内部调用：记录开始/结束、耗时、脱敏后的 request/response 摘要（`maskHeaders`∪`maskFields` 用于 header/query/cookie，`maskFields` 用于 body/response；掩码常量 `************`），`logRequestBody`/`logResponseBody` 控制是否含 body |
| 300 | `MetricsRpcInvocationInterceptor` | 仅 `metricsEnabled || availabilityEnabled`：累计成功/失败、连续失败、平均耗时、最近时间与失败原因；`internalCall` 或 `purpose="manual-test"` 跳过状态写入；失败时把 `availability` 置 `UNAVAILABLE` |

被可用性守卫拒绝的调用位于 Logging/Metrics 之外层，**不写日志也不计数**。

### 6.3 超时、重试与熔断边界

- 超时：`policy.timeoutOverrideMs` → 定义 `extraProps.timeout` → 默认 `3000ms`；重试：`policy.retryOverride` → 定义 `extraProps.retry` → 默认 `0`（`OpenApiRpcProtocolInvoker.java:137-155`）。两者最终作用于 WebClient `.timeout(...)`/`.retry(...)`（`:99-100`）。
- **没有熔断器、限流器或降级编排**。可用性语义是"一次失败即 `UNAVAILABLE`"：`MetricsRpcInvocationInterceptor` 在任何失败后直接置不可用（`:72-74`），随后调用被 `Availability` 拦截器短路，直到探活成功、`reset-status` 或强制 `HEALTHY`。没有失败阈值、恢复窗口、半开探测。

### 6.4 治理入口

`RpcRuntimeRegistryService` 聚合定义 + 策略 + 状态供列表/详情使用（`RpcRuntimeRegistryServiceImpl.java:30-53`）；`RpcRuntimePolicyService` / `RpcRuntimeStatusService` / `RpcRuntimeOperationService`（`testInvoke`/`healthCheck`/`resetStatus`/`repairPolicy`）分别负责策略、状态与运维动作。详情页动作：立即探活（health-check）、恢复可用（reset-status）、测试调用（test-invoke，`purpose="manual-test"`）、修复并保存当前合法配置（repair-policy）。

## 7. 扩展点

| 扩展面 | 机制 | 注册方式 |
|--------|------|----------|
| 新元数据来源 | `RPCModelMetadataProvider` / `RPCAuthCredentialMetadataProvider` | `META-INF/services/`，`SPI.getSortedServiceStream` 按 `@Priority` 排序（File Proxy `-10` 优先于 Jar `0`，同名后者覆盖） |
| 新模型类型 | `RPCModelDefinitionBuilder.support/build` | 同上 |
| 新认证类型 | `RPCAuthCredentialBuilder.support/build` | 同上；新类型还需在 `dev/` 增加 `rpc-auth/type/<Type>.ui-schema.json` 并在 `form-items.ui-schema.json` 的选项中登记 |
| 新协议执行器 | `RpcProtocolInvoker` Spring Bean（`supports` 判定 `definition.getType()`） | `@Bean`/`@Component`，由 `RpcInvocationExecutor` 的 `ObjectProvider.orderedStream()` 收集 |
| 横切治理能力 | `RpcInvocationInterceptor` Spring Bean | `@Component` + `@Order`；可用 context attribute/option 与后续拦截器通信 |
| 元数据驱动的新 `dev` 页面 | `dev/` 的 `src/main/metadata/**` | 由 `@DevelopModule` + `@LowCodeProvider` 元数据装载链路消费 |

**不是扩展点**：`HmacSignaturePreset`。6 个 preset 由 `HmacSignaturePresetRegistry` 硬编码注册，未开放 SPI；新增厂商签名模式必须改 registry。

## 8. 已知限制与未落地项

1. **无逐 HTTP 状态数值错误码**（见 5.3）：RPC 域保持原错误体系；开发测试使用阶段、reason 和真实 response.status。
2. **无熔断/限流/降级**：可用性是"一失败即不可用"，无阈值与恢复窗口（见 6.3）。
3. **规格列出但未落地的拦截器**：`AuditInterceptor`、`ExceptionMappingInterceptor` 在代码中不存在；异常映射目前靠各层手工包装。
4. **定义模型字段不完整**：`RPCModelDefinition` 无 `description`/`healthCheck`/`defaultTimeoutMs`/`defaultRetry` 独立字段；`request` 在 OpenAPI 子类；`timeout`/`retry` 只能从 `extraProps` 取，缺类型约束。
5. **凭据构建失败静默降级**：Center 记 error 后丢弃凭据，模型仍注册成功，调用期才报"认证凭据不存在"，不是启动期 fail-fast。
6. **协议单一**：唯一 `RpcProtocolInvoker` 实现只支持 `type=OpenAPI`；其他类型在 Executor 直接失败。
7. **多租户只预留字段**：两张表与两个模型都有 `tenantId`，但 controller/service 接口不传租户参数，仅在 `updatePolicy`/`resetStatus` 时保留既有值。
8. **表达式分析范围**：RPC 初始化要求表达式提供者报告输入引用；SpEL 暂不分析投影、集合选择和显式 `#root/#this`，明确失败，普通运行求值语义不变。
9. **`HttpBasic` 消息缺上下文**：失败只报 `获取用户名或密码失败`，未纳入上一轮错误收敛范围。
10. **静态捕获 Spring 依赖**：`HmacSignatureRpcAuthCredential`、`PlainRpcCredential`、`HttpBasicRpcAuthCredential`、`AccessTokenRpcAuthCredential`、`CallRpcNode` 都把容器依赖写入 `static` 字段（builder/节点多由 SPI 或流程引擎实例化）。同一 JVM 内多个 Spring 上下文会互相覆盖，静态字段为空时对应能力的配置读取会失败。
11. **UI 条件与规格不一致**：`HmacSignature.ui-schema.json` 中 `service` 恒为 `required`（`huawei_sdk_hmac_sha256`、`aliyun_rpc_v1` 也被强制），`region` 恒显示，`aliyunRpcV1.signatureMethod` 无 `visibleOn`。
12. **演进方向未启动**：presigned URL 输出、`HmacSignaturePreset` 公共 SPI 化、新协议支持均未实现。

## 9. 规格与实现的差异

三篇 RPC 产品规格（`../../docs/requirements/`）的有效结论已吸收进本文，核实结论如下。

### 9.1 运行时治理与执行解耦（[2026-03-14-rpc-runtime-governance-design.md](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-runtime-governance-design.md)）——部分失效

- **已失效的类名**：文中 `RPCModel.call()`、`OpenApiRpcModel`、`RPCModelRegistry`、`RPCModelCenter`、`RPCModel` 在代码中均不存在；现为 `RPCModelDefinition(Simple)` + `RpcInvocationExecutor` + `RPCModelDefinitionCenter` + `RPCAuthCredentialCenter`。第 4 节"现状问题"与第 7.1 节架构图里的 `RPCModelRegistry` 不可再引用。
- **仍然成立**：定义层/运行时治理层/执行层分离；统一执行管线；策略与状态分离；`internalCall`/`purpose` 区分内部调用；运行时管理界面与独立持久化；开发期界面不再承载运行时治理开关（`dev/` 元数据中检索不到 `loggingEnabled`/`metricsEnabled`/`availabilityEnabled` 等字段）。
- **未落地**：`AuditInterceptor`、`ExceptionMappingInterceptor`（10.4 节）；8.1 节的 `healthCheck`/`defaultTimeoutMs`/`defaultRetry`/`description` 定义字段。
- **命名与细节差异**：表名实际为 `t_sys_rpc_runtime_policy`/`t_sys_rpc_runtime_status`（15.1 节写的是 `rpc_runtime_policy`/`rpc_runtime_status`）；API 多一个 `POST …/actions/repair-policy`，且没有独立的 `RpcRuntimeHealthCheckResultVO`（复用 `RpcRuntimeInvocationResultVO`）；策略模型多出 `normalizationWarnings`；10.2 节的"预检查阶段"被拆进拦截器，没有独立阶段；13.4 节的开关类动作由详情页表单承载，独立按钮只有探活/恢复可用/测试调用/修复策略。

### 9.2 AK/SK HMAC 签名认证（[2026-03-14-rpc-hmac-signature-design.md](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-hmac-signature-design.md)）——主体成立，若干细节以代码为准

- **仍然成立**：统一 `HmacSignature` 类型 + preset 方案；6 个 preset；`hmacSignature` 单一配置根节点；配置/请求/自动生成三级优先级；`signedHeaders.include/exclude` 规则；body 序列化在签名与发送间共享；`HmacSignaturePreset` 未 SPI 化；`aliyun_rpc_v1` 忽略 `payloadHashMode` 并 warn。
- **以代码为准的差异**：
  - 规格 7.1 的 `HmacSigningContext` 实际名为 `ResolvedSigningContext`；另新增规格未列的 `HmacSigningSupport`、`SerializedBody`、`HmacSignaturePresetRegistry`。
  - 规格 7.1.1 的 preset 契约缺两项：实际接口还要求 `isHeaderSignature()` 与 `getRequiredSignedHeaders(config)`，`resolveContext` 另接收 `variables/configGetter/clock/serializer`。
  - 规格 7.5.3 的 `empty_sha256`"无论 body 是否为空都用空串 SHA-256"**不成立**：非空 body 直接判配置非法（`AwsSigV4HmacSignaturePreset.java:160-165`）。
  - 规格 7.4.1 的 `aliyun_rpc_v1` query 编码未单独实现"`+` → `%20`"分支；实际对参数名/值先 percent-decode 再按 RFC3986 unreserved 集合编码（`+`→`%2B`、`*`→`%2A`、`~` 保留）。
  - 规格表格未列的实现细节：`aliyun_acs3` 默认签名头含 `x-acs-version`；`tencent_tc3` 支持 `securityToken` → `X-TC-Token` 并可选进入签名头。
  - 规格 7.7 把 preset 必填/取值范围校验归为"运行时构建认证对象时"、把表达式求值归为"调用期"：实际是"配置文本与 preset 合法性在 `Builder.build()`（装载期）失败并导致凭据被丢弃，表达式求值与请求兜底在调用期"。

### 9.3 认证与元数据加载异常收敛（[2026-03-14-rpc-local-error-handling-design.md](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-local-error-handling-design.md)）——全部成立

逐条核实结果：provider 的"空目录→空列表 / 坏文件 warn+skip / 扫描失败 warn+空列表"、静态令牌三类失败、placement 求值失败消息契约、动态令牌 8 类失败分支、expires "warn + 回退 `defaultExpireInMs` 否则 `0`"、明文凭据"跳过坏定义 / 全空则失败 / 求值失败带上下文"全部与代码一致，证据见 5.4 节行号。

两处补充（规格未覆盖，不算失效）：`AbstractAccessTokenRPCAuthCredential.java:81` 的 TODO 仍在；`expiresValueSource != "response"` 时直接返回默认值且不记 warn（规格只描述了 `response` 分支）；动态令牌新增了按 key 含 `token` 的自动识别与时间戳启发式换算。

## 10. 补充资料

以下材料保留为评审与演进参考，不替代本文：

- `补充深度资料`：[runtime-governance 规格](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-runtime-governance-design.md)、[hmac-signature 规格](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-hmac-signature-design.md)、[local-error-handling 规格](https://gitlab.whxinpai.com/ouroboros-lowcode/ouroboros/-/blob/d2ebfd746acbaab0d3fc461ba4850e68c0d14cff/docs/product-specs/api/2026-03-14-rpc-local-error-handling-design.md)

## 11. 维护重点

- 新协议／新认证方式优先通过 definition builder、protocol invoker、credential builder 体系接入，不要改写 `RpcInvocationExecutor` 与中心对象。
- 改动签名逻辑时同时检查"签名用的 body 文本"与"发送用的 body 文本"是否仍是同一份 `SerializedBody`。
- 新增拦截器时明确 `@Order` 位置：当前可用性守卫位于日志/指标之外，被提前拒绝的调用不会进入内层日志和指标拦截器；调整顺序时需明确是否改变这一统计边界。
- 修改 `RPCModelDefinition` 或中心装载语义时，同步检查 `dev/` 表单字段、`bom` 中四条构件与 `dev-helper` 的元数据 collector。
- 评审文中的未来协议扩展讨论只作为演进参考，不覆盖本文。


## 10. 开发期工作区测试

RPC 新增与更新通过统一模型 CRUD 保存，`isAutoCommitWhenCreate/Update=false`，只修改应用工作区并保留资源锁。原服务创建策略默认仍提交。显式 commit 要求去除首尾空白后的 1～200 字符，沿原模型资源集合提交，不夹带其他模型修改。

主应用 `rpc-model-editor` 在原右侧宽 Drawer 中复用 AMIS 字段与页脚动作归一化。保存不关闭；dirty 禁用运行测试；提交失败保留保存结果和信息。列表及详情调试复用同一编辑器并默认测试页签。测试输入不会写入模型、VCS 或统一配置。

`RpcModelTestController` 提供 POST `.../{fullName}/test/init-form` 与 POST `.../{fullName}/test`。两者均沿用 POST 的应用编辑权限分类；初始化只分析、不构建认证或发送请求，接收 configValues 并返回 ready 与缺项。字段发现统一遵循 RpcFieldSemantics，依赖快照与执行共用调用级配置视图；配置化认证/令牌引用先解析后读取，revision 包含实际依赖边。`dev/service/testing` 两次读取同应用主模型、凭据与递归令牌依赖，比对内容和分支，执行前复核 revision；之后只用不可变副本。`RpcExecutionResources` 通过原 Executor/Context 显式传递，开发测试绕过运行治理、完整报文日志和共享令牌缓存，普通调用路径保持原策略。

测试报文来自同一 `OpenApiRpcProtocolInvoker` 的应用层实际 exchange，统一脱敏。默认总预算 120000 ms、请求正文 1048576 bytes、预览 262144 bytes，配置键见 [开发平台配置](../../docs/handbook/docs/operations/development-platform-configuration.md#rpc-模型测试)。认证正文、非结构化和二进制正文省略；JSON 按 UTF-8 安全截断。测试支持已保存未提交的内容，使用开发平台网络出口。完整合同与验证见 [专项设计](docs/model-testing.md) 和 [正式验收场景](../../testing/integration/feature/lowcode/rpc-model-testing/contract.md)。
