# Common Modules - 公共组件库指南

本目录包含 Ouroboros 低代码平台的所有公共组件模块,提供异常处理、Web API、缓存、模板引擎、脚本执行等基础能力。

## 模块总览

| 模块 | 功能说明 | 文档链接 |
|------|---------|---------|
| **common-exception** | 统一异常体系 (BusinessException, ValidationException, PlatformException, InternalException) | [GUIDE.md](common-exception/GUIDE.md) |
| **common-exception-mapper** | 异常映射器,将第三方异常转换为 Ouroboros 异常 | [GUIDE.md](common-exception-mapper/GUIDE.md) |
| **common-web** | Web API 响应封装 (WebApiResult, PagedResult, SelectOption) | [GUIDE.md](common-web/GUIDE.md) |
| **common-cache** | 统一缓存接口,支持命名空间和过期时间管理 | [GUIDE.md](common-cache/GUIDE.md) |
| **common-template** | FreeMarker 模板引擎封装,用于生成动态 JSON 配置 | [GUIDE.md](common-template/GUIDE.md) |
| **common-script** | 脚本执行引擎 (JavaScript via GraalVM, Groovy) | [GUIDE.md](common-script/GUIDE.md) |
| **common-expression** | 表达式引擎 (基于 Spring SpEL),支持动态表达式求值 | [GUIDE.md](common-expression/GUIDE.md) |
| **common-model** | 数据模型基类,支持 Builder 模式、克隆和扩展属性 | [GUIDE.md](common-model/GUIDE.md) |
| **common-app** | 应用上下文、生命周期事件和 Web 应用信息管理 | [GUIDE.md](common-app/GUIDE.md) |
| **common-service** | 服务层基类,封装标准 CRUD 操作(支持软删除) | [GUIDE.md](common-service/GUIDE.md) |
| **common-dev** | 开发模式支持,提供工作空间管理、元数据提供者和开发事件 | [GUIDE.md](common-dev/GUIDE.md) |
| **common-error-code** | 正式错误码引用与国际化边界 | [设计](../../docs/design/modules/libs/common-error-code.md) |
| **common-metadata** | typed 元数据路径与资源定位 | [发现管道](../../docs/design/architecture/pipelines/metadata-discovery.md) |
| **common-lock** | 共享锁接口及实现 | [GUIDE.md](common-lock/GUIDE.md) |
| **common-all** | 聚合模块,引入 POM 声明的六个公共模块 | [GUIDE.md](common-all/GUIDE.md) |

## 快速开始

### 引入所有公共组件

如果你需要使用多个公共组件,可引入 `common-all`（`packaging=pom`），其直接依赖为 web、expression、app、exception、model、script；其余按需引入：

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-all</artifactId>
    <type>pom</type>
</dependency>
```

### 按需引入单个组件

如果你只需要特定功能,可以按需引入单个模块:

```xml
<!-- 异常处理 -->
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-exception</artifactId>
</dependency>

<!-- Web API 响应 -->
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-web</artifactId>
</dependency>

<!-- 缓存 -->
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-cache</artifactId>
</dependency>

<!-- 其他模块类似... -->
```

## 模块分类

### 异常处理

- **common-exception** - 定义统一的异常层次结构
- **common-exception-mapper** - 将第三方异常映射到 Ouroboros 异常

### Web 开发

- **common-web** - 提供标准化的 Web API 响应格式
- **common-cache** - 缓存管理接口和实现

### 模板与脚本

- **common-template** - FreeMarker 模板引擎,用于生成动态配置
- **common-script** - 执行 JavaScript 和 Groovy 脚本
- **common-expression** - SpEL 表达式求值

### 数据模型

- **common-model** - 提供模型基类和 Builder 模式支持
- **common-service** - 封装标准的 CRUD 服务实现

### 应用支持

- **common-app** - 应用上下文和生命周期管理
- **common-dev** - 开发模式专用工具和抽象

## 用法与依赖的共同正文

具体 API、代码示例和失败行为维护在上表对应指南。本页负责选型与导航，避免与分模块指南维护两套示例。

`common-all/pom.xml` 只聚合六个直接依赖；cache、template、service、dev、exception-mapper、lock 等能力按实际需要显式声明。传递依赖以 Maven 依赖树为准，不把目录中所有 common 模块都当作聚合包内容。异常类型与已弃用兼容类以 [异常指南](common-exception/GUIDE.md) 为准；异常转换需要调用正式 mapper，不能仅凭引入依赖认为所有异常自动映射。

## 最佳实践

### 1. 异常处理

- 使用正确的异常类型: 业务边界用平台异常体系；数据内核校验与已弃用的兼容类型见异常指南
- 提供清晰的错误消息,避免技术术语
- 在服务层捕获第三方异常并转换为 Ouroboros 异常

### 2. Web API 响应

- 统一使用 `WebApiResult` 包装所有 API 响应
- 使用 `WebApiResult.of(Try/Either)` 简化异常处理
- 分页数据使用 `PagedWebApiResult`, 列表数据使用 `ItemsWebApiResult`

### 3. 缓存使用

- 使用有意义的命名空间(如 "users", "sessions")
- 为所有缓存项设置合理的过期时间
- 使用缓存指南中的 typed `getOrPut` 重载实现懒加载模式

### 4. 脚本执行

- 对重复执行的脚本使用预编译 (`Scripts.compile()`)
- 始终检查执行结果: `result.isSuccess()` 和 `result.getCause()`
- 谨慎处理用户输入的脚本(潜在安全风险)

### 5. 表达式求值

- 使用表达式指南中的 `EL.evalTo(Class<T>, ...)`  避免类型转换
- 利用内置工具类 (`Dates`, `Converter`) 简化表达式
- 使用安全导航运算符 `?.` 避免 NPE

### 6. 数据模型设计

- 优先使用 `AbstractModel` 实现不可变模型
- 使用 Builder 模式构建对象
- 使用 `map()` 方法创建修改副本,而不是直接修改对象

## 注意事项

1. **版本兼容性**: 所有 common 模块版本应保持一致,由 `ouroboros-bom` 统一管理
2. **SPI 扩展**: 多个模块支持 SPI 扩展机制,详见各模块文档
3. **线程安全**: 按具体实现核对共享可变状态；不能从工具类命名推定线程安全
4. **Spring 集成**: 多个模块提供 Spring Boot Auto-Configuration,自动配置相关 Bean

## 常见问题

### Q: common-all 包含所有模块吗?

A: 不是。`common-all` 直接聚合六个通用模块(exception, web, expression, script, app, model)，依赖时声明 `<type>pom</type>`. `common-dev`, `common-cache`, `common-template`, `common-service`, `common-exception-mapper` 等模块需要按需单独引入。

### Q: 如何自定义异常映射?

A: 实现 `OuroborosExceptionMapper` 接口并通过 SPI 注册。详见 [common-exception-mapper/GUIDE.md](common-exception-mapper/GUIDE.md)

### Q: 如何扩展表达式引擎的上下文?

A: 实现 `ExpressionContextContributor` 接口并通过 SPI 注册。详见 [common-expression/GUIDE.md](common-expression/GUIDE.md)

### Q: 缓存的默认实现是什么?

A: 默认使用内存缓存 (`MemoryCacheManager`)。可以通过 SPI 注册自定义实现(如 Redis)。详见 [common-cache/GUIDE.md](common-cache/GUIDE.md)

### Q: 服务层如何处理软删除?

A: 使用 `SoftDeleteModelServiceImpl` 或 `SimpleSoftDeleteModelServiceImpl`,并确保模型实现 `SoftDelete` 接口。详见 [common-service/GUIDE.md](common-service/GUIDE.md)

## 参考资料

- [Ouroboros 工具库指南](../util/ouroboros-util/GUIDE.md)
- [开发模块模式指南](../../docs/guides/development-module-guide.md)
- [Spring Boot 文档](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [Spring Expression Language (SpEL)](https://docs.spring.io/spring-framework/reference/core/expressions.html)
- [FreeMarker 文档](https://freemarker.apache.org/docs/)

## 贡献指南

如果你需要添加新的公共组件:

1. 在 `libs/common/` 下创建新模块目录
2. 遵循现有模块的结构和命名约定
3. 编写清晰的 GUIDE.md 文档
4. 更新本文档的模块列表和分类
5. 考虑是否应该加入 `common-all` 聚合模块
