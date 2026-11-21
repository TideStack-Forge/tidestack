# 支撑层架构说明

## 文档目标

本文面向后端与架构维护者，用于说明 `libs` 支撑层的稳定架构边界、模块族分工和扩展方式。它吸收原 `libs/DesignOverview.md` 中仍然有效的架构信息，但不再维护旧入口文件。

本文不替代各模块摘要页。单个模块的职责、关键类和维护入口仍以 [libs 模块设计](../modules/libs/README.md) 及其子页为准。

## 架构定位

`libs` 是后端分层中的公共底座，为 `core`、`security`、`ability`、`app-module`、`dev-module` 和 `platform` 提供可复用的基础能力。它的设计重点是稳定抽象、低耦合和跨模块复用，而不是承载具体低代码领域语义。

```text
platform / app-module / dev-module
              ↑
          ability
              ↑
          security
              ↑
            core
              ↑
            libs
```

依赖方向必须保持向下：上层可以消费 `libs` 的工具、公共模型、公共 Web 支持、异常、缓存、表达式、脚本和模板能力；`libs` 不应反向理解上层的业务模型、运行时编排或产品交付语义。

## 模块族分工

| 模块族 | 定位 | 典型内容 | 约束 |
|--------|------|----------|------|
| `libs/util` | 纯工具与数据转换 | `Functions`、`Maps`、`Lists`、`Json`、`JsonBag`、`Dates`、`Paths`、`SPI` | 避免 Spring/ApplicationContext 依赖，优先保持无状态、低副作用 |
| `libs/util/ouroboros-util-excel` | 文件处理专门化工具 | Excel 读取、单元格值封装、公式/日期处理 | 只沉淀通用导入能力，不承载业务导入流程 |
| `libs/util/ouroboros-util-zip` | ZIP 文件工具 | 压缩、解压、路径安全处理 | 只提供文件能力，不绑定平台资源模型 |
| `libs/common` | 框架感知公共组件 | 异常、Web 结果、模型基类、服务基类、缓存、表达式、模板、脚本、锁、应用事件 | 可以接入 Spring 与 SPI，但不得吸收上层模块的领域规则 |

`util` 系列优先回答“通用数据和类型问题怎么处理”；`common` 系列优先回答“平台共享基础设施怎么以统一接口接入”。两者之间的边界不能因为短期复用方便而被打散。

## 关键设计约束

- **保持低层语义纯度**：支撑层可以提供通用抽象，但不得内置数据模型、流程、安全、配置、通知等上层场景规则。
- **优先稳定公共契约**：被多个上层模块依赖的 SPI、模型基类、异常类型和 Web 返回结构应保持兼容，破坏性变化需要在架构文档中说明迁移路径。
- **工具层避免框架泄漏**：`util` 系列不应引入 Spring 生命周期、自动配置或应用上下文依赖。
- **公共组件避免业务聚合**：`common` 系列可以框架感知，但只应提供可复用机制，不应成为隐式业务服务层。
- **扩展点显式注册**：SPI 实现、Spring 自动配置和资源约定必须通过标准 `META-INF/` 机制注册，避免隐藏注册表或平行扩展路径。

## 主要机制

### SPI 扩展

支撑层大量使用 SPI 来维持低层接口与上层实现之间的解耦。典型扩展点包括：

- `ObjectMapperConfigurator`：统一 Jackson 配置扩展。
- `ExpressionContextContributor`：表达式上下文贡献接口。
- `ScriptContextContributor`：脚本执行上下文贡献接口。
- `CacheManager`：缓存实现选择与适配。
- `WebApiResultExceptionMapper`：Web API 异常到返回模型的映射。

新增扩展点时，应先确认它是否属于跨模块公共机制；若只服务单个核心或能力模块，应保留在对应模块内，而不是下沉到 `libs`。

### 函数式错误处理

支撑层在类型转换、I/O、服务接口和脚本/表达式执行中广泛使用 Vavr `Try` 与 `Either` 表达失败分支。这类接口适合用于可组合的基础能力，但不应被用来吞掉异常或绕过平台异常体系；跨到 Web 或业务 API 边界时，仍需要映射成统一异常与响应模型。

### 上下文贡献

脚本、表达式和模板处理通常通过 `Map<String, Object>` 传递运行上下文，再由 SPI 贡献者注入安全上下文、工具函数或平台辅助对象。该模式的维护重点是：

- 贡献者必须可组合，不能假设唯一实现。
- 注入对象应保持最小必要范围，避免把上层服务整体暴露给脚本或表达式。
- 涉及安全、租户、工作空间的信息，应由对应上层模块负责定义语义，支撑层只提供承载和组合机制。

### 模型与构建器模式

`common-model` 通过自引用泛型、模型基类、构建器和额外属性能力，为上层模型提供一致的基础形态。维护者应把它视为“模型结构工具箱”，而不是具体领域模型来源。

## 与上层的集成点

| 上层主题 | 使用的支撑能力 | 边界说明 |
|----------|----------------|----------|
| 动态数据模型 | 类型转换、JSON、Schema、层级 Map、公共模型基类 | `libs` 提供数据形态工具，动态数据语义由 `core/ouroboros-data` 负责 |
| 配置管理 | 层级路径访问、资源发现、表达式求值、缓存 | `libs` 提供基础机制，配置来源和变更语义由能力层负责 |
| 开发平台 | 模板、脚本、Excel 导入、开发上下文承载 | `libs` 提供通用工具，开发流程与应用管理语义由 `dev-module` 负责 |
| 统一运行时基座 | Web 结果、异常映射、缓存、公共应用事件 | `libs` 统一横切基础能力，最终装配与产品入口由平台层负责 |
| 安全接入 | 上下文贡献、异常映射、SPI 发现 | `libs` 不定义安全策略，只承载可扩展的接入机制 |

## 契约与边界

### 对外契约（真实类名 + 模块）

| 契约 | 类型与位置 | 上层应如何依赖 |
|------|-----------|----------------|
| `com.ouroboros.exception.OuroborosException` | 抽象基类，`libs/common/common-exception`（`.../exception/OuroborosException.java:11`） | 继承它定义业务异常；不要直接依赖 `RuntimeException` 语义 |
| `BusinessException`(-4000) / `PlatformException`(-6000) / `InternalException`(-5000) | `libs/common/common-exception`（`:8`） | 按语义选类型：业务不合法 / 开发者用法错误 / 平台内部错误 |
| `WebApiResult`、`PagedWebApiResult`、`ItemsWebApiResult`、`AbstractWebApiResult` | `libs/common/common-web`（`.../web/WebApiResult.java:13`、`AbstractWebApiResult.java:10`） | Web 层统一返回结构；`AbstractWebApiResult` 承载 `code/msg/data/errors/extra` |
| `WebApiResultExceptionMapper` | SPI，`libs/common/common-web`（`.../web/WebApiResultExceptionMapper.java:8`） | 提供实现做异常→结果映射，通过 `META-INF/services` 注册 |
| `ObjectMapperConfigurator` | SPI，`libs/util/ouroboros-util`（`.../util/config/ObjectMapperConfigurator.java:7`） | 追加 Jackson 配置，不要自建 `ObjectMapper` |
| `ExpressionContextContributor` / `ScriptContextContributor` | SPI，`common-expression`（`:5`）/ `common-script`（`:5`） | 用 `Map<String,Object>` 贡献绑定，不做权限决策 |
| `CacheManager` / `DistributedLockManager` | SPI，`common-cache`（`CacheManager.java:16`）/ `common-lock`（`DistributedLockManager.java:10`） | 需要缓存/锁时取 SPI 实现，勿自行 new |
| `Model` / `AbstractModel` / `AbstractExtraPropsModel` | `libs/common/common-model`（`.../model/AbstractModel.java:5`） | 上层模型基类与 builder 形态 |
| `OuroborosExceptionMapper` | SPI，`libs/common/common-exception-mapper`（`.../mapper/OuroborosExceptionMapper.java:10`） | 跨层异常归一化的声明式入口（`toOuroborosException`） |
| `SPI` 工具类 | `libs/util/ouroboros-util`（`.../util/SPI.java`） | 所有 SPI 发现的统一入口；按 `@Priority` 排序（`SPI.java:31-34`） |

### 错误语义

- `common-exception` 定义以下平台异常类型（`libs/common/common-exception/.../exception/`）：`OuroborosException`（基类）、`BusinessException`、`PlatformException`、`InternalException`、`ValidationException`。
- `ValidationException` 已 `@Deprecated`（`ValidationException.java:13`），替代者是 `com.ouroboros.data.exception.RecordValidationException`（core 模块）；新代码不应再抛它。
- **SPI 缺失不 fail-closed，而是 fail-open**：`SPI.getPrimaryService` / `getCachedPrimaryService` 以 `.orElse(null)` 收尾（`SPI.java:365-366`、`:369-370`），`ServiceLoader` 无实现时返回空流（`:44`），`SPI` 全类无一处 `throw`。消费方靠硬编码兜底补偿：`CacheManager.java:122-123`、`DistributedLockManager.java:25-26`、`AbstractWebApiResult.java:11-14`。
- 已知缺口：`EL.DEFAULT_EL = ...findFirst().orElse(null)`（`EL.java:13-15`），缺失实现时后续解引用会 NPE。
- 其余失败走 JDK 异常：`IllegalArgumentException`（`ZipWriter.java:418-419`、`ExcelReader.java:97`）、`UnsupportedOperationException`（`ContextWrappingHelper.java:65`）、`TextTemplateException`（`common-template/.../TextTemplateException.java:3`）。
- I/O 与脚本/表达式路径也大量返回 Vavr `Try.failure`（如 `ZipReader.java:401`、`Scripts.java:41`），跨到 Web 边界仍需映射为平台异常。
- 扩展点注册方式：libs 的服务实现通过 `META-INF/services/<接口 FQN>` 声明（如 `common-web/src/main/resources/META-INF/services/com.ouroboros.web.WebApiResultExceptionMapper` → `DefaultWebApiResultExceptionMapper`），**没有** `.imports`、`spring.factories` 或 `@Configuration`；缺失实现时由 `orElseGet` 内建默认实现，而不是由 Spring 条件注解补位。

### 边界条件

- **前置条件**：`libs/util` 不得依赖 Spring——三个 util 模块 main 源码中 `org.springframework` import 为 0 命中，依赖仅 `jakarta.annotation-api`、commons、vavr、jackson（`libs/util/ouroboros-util/pom.xml:31-67`）；仅允许用 `jakarta.annotation.Priority`。
- **ZIP 不变量**：拒绝含 `..`、以 `/` 开头或含 `\` 的 entry，并校验 `normalize()` 后必须位于输出目录内（`ZipReader.java:622-637`、`ZipWriter.java:418-419`）；`MAX_ENTRIES`、单 entry 大小、总大小均有硬上限。
- **Excel 不变量**：只接受 `.xls`/`.xlsx`，且文件必须存在、是文件、可读，否则校验失败（`ExcelReader.java:663-695`、`:689-690`）。
- **线程安全**：`Trees` 静态方法线程安全，但传入的树结构本身不保证（`Trees.java:18-20`）；`Snowflakes.Snowflake.nextId()` 为 `synchronized`（`Snowflakes.java:76`）。
- **幂等/缓存**：`SPI` 的实例缓存是进程级静态 map，`computeIfAbsent` 后不再失效（`SPI.java:19`、`:192-196`）——实现集合变化需要重启。
- **明确不做**：不定义安全策略与租户/工作空间语义（只提供承载与组合机制）；不做业务聚合；不承担最终交付入口。`libs` 内**没有**任何 `.imports`/`spring.factories`/`@Configuration`，支撑能力只通过 SPI 与普通类暴露。
- **判定口径**：上层需要"通用数据/类型处理"时用 `libs/util`；需要"以统一接口接入平台共享基础设施"时用 `libs/common`；两者边界不得因短期复用方便而打散。
- **存量偏离**：`common-web`、`common-exception-mapper` 的 pom 依赖 `ouroboros-data-core`（`libs/common/common-web/pom.xml:42`、`common-exception-mapper/pom.xml:28`），因此 `DefaultWebApiResultExceptionMapper` 直接识别 `com.ouroboros.data.exception.*`；这属于已登记的 P0-3 待收敛项（见 [index.md](README.md)）。

## 维护边界

当一个能力被多个上层模块重复需要，且不携带具体业务语义时，可以考虑沉淀到 `libs`。反过来，如果能力需要理解某个核心引擎、业务能力或产品运行场景，它应保留在对应层内，即使当前实现也被多个地方调用。

判断是否应进入支撑层时，优先问三个问题：

1. 这个抽象脱离当前业务场景后是否仍成立？
2. 它是否可以用稳定接口服务多个上层模块？
3. 它是否会迫使 `libs` 依赖或理解上层语义？

只有前两个问题为“是”、第三个问题为“否”时，才适合下沉。

## 后续演进

- 脚本、表达式和模板上下文需要继续明确安全注入边界，避免通用贡献者变成隐式权限通道。
- 缓存、锁和异常映射如果出现多个生产实现，应优先补充跨模块扩展专题，而不是把实现细节塞回模块摘要。
- `bom` 与依赖版本治理仍属于构建/架构治理主题，不并入支撑层模块摘要；需要时应在 architecture 维度单独说明。
- 已吸收到本文和模块摘要的历史材料不再保留 `DesignOverview.md` 入口，后续新增设计信息应直接更新正式文档。
