# WebAPI Model OpenAPI 3.1 支持指南

## 概述

`ouroboros-webapi-model` 现已支持以 OpenAPI 3.1 为目标的操作级契约建模，并兼容现有 OpenAPI 3.0 风格字段。除了低代码 `WebAPIModel` 外，高代码 Spring MVC 接口现在也可以在 `RequestMapping` 注册拦截阶段被抽取为带参数、请求体和响应契约的 `WebAPIModel`。

当前支持的核心能力包括：

- 参数定义（路径参数、查询参数、请求头、Cookie）
- 请求体 Schema
- 响应 Schema
- 数据类型和验证规则
- OpenAPI 3.1 Schema 关键字（联合类型、组合、`additionalProperties`、对象属性约束）
- 示例值
- 标签分组
- 安全要求
- 外部文档链接

这些信息可以用于：
- 自动生成 Swagger/OpenAPI 文档
- API 参数验证
- 代码生成
- API 测试工具集成

## 文档导出能力

当前 runtime 已支持把全部已注册的 `WebAPIModel` 统一导出为 OpenAPI 3.1 文档：

- 导出入口：`GET /api/sys/webapi/openapi.json`
- 返回格式：原生 `application/json`
- 默认行为：导出当前应用下全部已注册的高代码和低代码 `WebAPIModel`
- 文档版本：固定输出 `openapi: 3.1.0`

这条导出链路不会重新扫描 Spring 注解，而是直接复用已经在 `RequestMapping` 注册拦截阶段抽取好的 `WebAPIModel`。因此高代码和低代码接口会自然汇总到同一份文档里。

### 可用过滤参数

- `scope`：按 `WebAPIScope` 过滤，可重复传值
- `handlerType`：按 `HandlerType` 过滤，可重复传值
- `tag`：按 operation tag 过滤，可重复传值
- `pathPrefix`：按路径前缀过滤，可重复传值
- `api`：按完整 `METHOD path` 过滤，可重复传值
- `title`：临时覆盖导出文档标题
- `version`：临时覆盖导出文档版本
- `serverUrl`：在文档级 `servers` 中追加导出地址
- `pretty`：是否格式化 JSON 输出

示例：

```http
GET /api/sys/webapi/openapi.json?scope=public&handlerType=RequestMapping&pretty=true
```

### 文档组装规则

- 文档默认汇总所有已注册 `WebAPIModel`
- `title` 默认优先取 `AppInfo.title`，其次 `AppInfo.name`，最后兜底为 `Ouroboros WebAPI`
- `version` 默认优先取运行时包版本号，取不到时使用 `dev`
- 请求级 `serverUrl` 会和 operation 级 `servers` 合并为文档级 `servers`
- 返回的是原生 OpenAPI JSON，不会包在 `WebApiResult` 中

## 文档级模型

`com.ouroboros.webapi.openapi` 包现在同时承载操作级模型和文档级模型。

新增的文档级模型包括：

- `OpenAPIDocumentModel`：OpenAPI 3.1 根文档
- `InfoModel`：文档 `info`
- `PathItemModel`：同一路径下的方法集合
- `OperationModel`：从 `WebAPIModel` 映射出的 operation 视图
- `ComponentsModel`：第一阶段的组件容器，当前只聚合 `components.schemas`

## 高代码抽取流程

高代码接口不会通过独立扫描器重复扫描注解，而是沿用现有的 Spring MVC 集成机制：

1. Spring 注册 `RequestMapping`
2. `RequestMappingIntegrationCenter` 拦截路由
3. `RequestMappingOpenAPIExtractor` 基于 `RequestMappingInfo + handlerType + method` 提取契约
4. 抽取结果统一落到 `WebAPIModel`
5. 后续 discover、动态注册和文档导出统一消费 `WebAPIModel`

这样可以保证高代码和低代码共用同一层接口契约模型。

## 当前支持的注解来源

第一阶段支持以下主流注解体系：

- Spring MVC 原生注解
  - `@RequestMapping`
  - `@GetMapping`
  - `@PostMapping`
  - `@PutMapping`
  - `@DeleteMapping`
  - `@PatchMapping`
  - `@PathVariable`
  - `@RequestParam`
  - `@RequestHeader`
  - `@CookieValue`
  - `@RequestBody`
- `io.swagger.v3.oas.annotations.*`
  - `@Tag` / `@Tags`
  - `@Operation`
  - `@Parameter`
  - `@io.swagger.v3.oas.annotations.parameters.RequestBody`
  - `@ApiResponse`
  - `@ApiResponses`
  - `@Schema`
  - `@ExternalDocumentation`
  - `@SecurityRequirement`
- `jakarta.validation.*`
  - `@Valid`
  - `@NotNull`
  - `@NotBlank`
  - `@NotEmpty`
  - `@Size`
  - `@Min`
  - `@Max`
  - `@DecimalMin`
  - `@DecimalMax`
  - `@Pattern`
  - `@Email`

显式 Swagger 注解优先于 Spring 和自动推断；`jakarta.validation` 负责补充 Schema 约束而不是改变参数位置。

控制器级 Tag、SecurityRequirement 和 ApiResponses 可作为方法的默认合同。方法级 Tag/SecurityRequirement 覆盖对应类级声明，`@Operation` 中显式 tags/security 优先；方法响应按状态码覆盖类级响应。只有类级错误响应时仍保留返回类型推导的成功 Schema；方法显式声明响应时沿用显式响应优先的行为。安全声明描述合同，实际权限仍由运行时认证授权执行。

## OpenAPI 3.1 Schema 增强

`SchemaModel` 现在除了保留已有 `type` / `nullable` 等兼容字段外，还可以承载常用的 OpenAPI 3.1 / JSON Schema 关键字：

- `types`
- `oneOf`
- `anyOf`
- `allOf`
- `not`
- `additionalProperties`
- `multipleOf`
- `exclusiveMinimum`
- `exclusiveMaximum`
- `minProperties`
- `maxProperties`
- `constValue`

这让后续统一导出 OpenAPI 3.1 文档时，不需要再回到 Spring 注解层做二次推断。

## 当前非目标

当前实现还没有覆盖以下内容：

- Swagger UI 页面生成
- `@ControllerAdvice` 全局异常自动归并
- WebFlux / `RouterFunction` 的 OpenAPI 抽取
- `javax.validation.*` 旧注解兼容（当前抽取使用 `jakarta.validation.*`）
- `components.parameters`
- `components.responses`
- `components.headers`
- `components.links`
- `components.securitySchemes`

## 核心模型

### WebAPIModel 接口

扩展后的 `WebAPIModel` 接口包含以下方法：

**基础字段**（已有）：
- `getMethod()` - HTTP 方法
- `getPath()` - API 路径
- `getApi()` - API 标识
- `getScope()` - 作用域
- `getHandlerType()` - 处理器类型
- `getSummary()` - 简短摘要

**OpenAPI 扩展字段**：
- `getDescription()` - 详细描述
- `getOperationId()` - 操作 ID
- `getTags()` - 标签列表
- `getParameters()` - 参数列表
- `getRequestBody()` - 请求体定义
- `getResponses()` - 响应定义
- `getDeprecated()` - 是否废弃
- `getSecurity()` - 安全要求
- `getServers()` - 服务器列表
- `getExternalDocs()` - 外部文档

### 数据模型类

所有模型类位于 `com.ouroboros.webapi.openapi` 包下：

| 模型类 | 说明 |
|--------|------|
| `OpenAPIDocumentModel` | OpenAPI 3.1 文档根对象 |
| `InfoModel` | 文档元信息 |
| `ComponentsModel` | 组件容器（当前只包含 `schemas`） |
| `PathItemModel` | Path Item 定义 |
| `OperationModel` | Operation 定义 |
| `ParameterModel` | 参数定义 |
| `ParameterLocation` | 参数位置（PATH, QUERY, HEADER, COOKIE） |
| `SchemaModel` | JSON Schema 定义 |
| `RequestBodyModel` | 请求体定义 |
| `ResponseModel` | 响应定义 |
| `MediaTypeModel` | 媒体类型（Content-Type） |
| `HeaderModel` | 响应头定义 |
| `ExampleModel` | 示例值 |
| `EncodingModel` | 编码信息 |
| `LinkModel` | API 链接 |
| `ServerModel` | 服务器定义 |
| `ServerVariableModel` | 服务器变量 |
| `ExternalDocsModel` | 外部文档 |
| `SecurityRequirementModel` | 安全要求 |

所有模型类都继承自 `AbstractExtraPropsModel`，支持 Builder 模式构建。

## 使用示例

### 1. 定义一个简单的 GET API

```java
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.openapi.*;

SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .get("/users/{id}")
    .summary("获取用户详情")
    .description("根据用户 ID 获取用户的详细信息")
    .operationId("getUserById")
    .tag("用户管理")
    .parameter(ParameterModel.builder()
        .name("id")
        .in(ParameterLocation.PATH)
        .description("用户ID")
        .required(true)
        .schema(SchemaModel.builder().stringType().build())
        .build())
    .response("200", ResponseModel.builder()
        .description("成功")
        .content(Map.of(
            "application/json", MediaTypeModel.builder()
                .schema(SchemaModel.builder()
                    .objectType()
                    .properties(Map.of(
                        "id", SchemaModel.builder().stringType().build(),
                        "username", SchemaModel.builder().stringType().build(),
                        "email", SchemaModel.builder().stringType().format("email").build()
                    ))
                    .build())
                .build()
        ))
        .build())
    .response("404", ResponseModel.builder()
        .description("用户不存在")
        .build())
    .build();
```

### 2. 定义带查询参数的列表 API

```java
SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .get("/users")
    .summary("用户列表")
    .description("分页查询用户列表")
    .operationId("listUsers")
    .tag("用户管理")
    // 页码参数
    .parameter(ParameterModel.builder()
        .name("page")
        .in(ParameterLocation.QUERY)
        .description("页码（从1开始）")
        .required(false)
        .schema(SchemaModel.builder()
            .integerType()
            .minimum(1)
            .defaultValue(1)
            .build())
        .build())
    // 每页数量参数
    .parameter(ParameterModel.builder()
        .name("size")
        .in(ParameterLocation.QUERY)
        .description("每页数量")
        .required(false)
        .schema(SchemaModel.builder()
            .integerType()
            .minimum(1)
            .maximum(100)
            .defaultValue(20)
            .build())
        .build())
    // 搜索关键词参数
    .parameter(ParameterModel.builder()
        .name("keyword")
        .in(ParameterLocation.QUERY)
        .description("搜索关键词")
        .required(false)
        .schema(SchemaModel.builder().stringType().build())
        .build())
    .response("200", ResponseModel.builder()
        .description("成功")
        .content(Map.of(
            "application/json", MediaTypeModel.builder()
                .schema(SchemaModel.builder()
                    .objectType()
                    .properties(Map.of(
                        "total", SchemaModel.builder().integerType().build(),
                        "items", SchemaModel.builder()
                            .arrayType()
                            .items(SchemaModel.builder().ref("#/components/schemas/User").build())
                            .build()
                    ))
                    .build())
                .build()
        ))
        .build())
    .build();
```

### 3. 定义 POST API（带请求体）

```java
SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .post("/users")
    .summary("创建用户")
    .description("创建一个新用户账号")
    .operationId("createUser")
    .tag("用户管理")
    // 请求体定义
    .requestBody(RequestBodyModel.builder()
        .description("用户信息")
        .required(true)
        .content(Map.of(
            "application/json", MediaTypeModel.builder()
                .schema(SchemaModel.builder()
                    .objectType()
                    .properties(Map.of(
                        "username", SchemaModel.builder()
                            .stringType()
                            .minLength(3)
                            .maxLength(20)
                            .pattern("^[a-zA-Z0-9_]+$")
                            .description("用户名（3-20个字符，字母数字下划线）")
                            .build(),
                        "email", SchemaModel.builder()
                            .stringType()
                            .format("email")
                            .description("邮箱地址")
                            .build(),
                        "password", SchemaModel.builder()
                            .stringType()
                            .minLength(8)
                            .format("password")
                            .description("密码（至少8位）")
                            .build(),
                        "age", SchemaModel.builder()
                            .integerType()
                            .minimum(18)
                            .maximum(120)
                            .description("年龄（18-120）")
                            .build()
                    ))
                    .required(List.of("username", "email", "password"))
                    .build())
                .example(Map.of(
                    "username", "john_doe",
                    "email", "john@example.com",
                    "password", "securepass123",
                    "age", 25
                ))
                .build()
        ))
        .build())
    // 成功响应
    .response("201", ResponseModel.builder()
        .description("用户创建成功")
        .content(Map.of(
            "application/json", MediaTypeModel.builder()
                .schema(SchemaModel.builder()
                    .objectType()
                    .properties(Map.of(
                        "id", SchemaModel.builder().stringType().build(),
                        "username", SchemaModel.builder().stringType().build(),
                        "email", SchemaModel.builder().stringType().build(),
                        "createdAt", SchemaModel.builder().stringType().format("date-time").build()
                    ))
                    .build())
                .build()
        ))
        .build())
    // 错误响应
    .response("400", ResponseModel.builder()
        .description("请求参数错误")
        .build())
    .response("409", ResponseModel.builder()
        .description("用户名或邮箱已存在")
        .build())
    .build();
```

### 4. 定义复杂嵌套对象

```java
// 定义地址 Schema
SchemaModel addressSchema = SchemaModel.builder()
    .objectType()
    .properties(Map.of(
        "street", SchemaModel.builder().stringType().build(),
        "city", SchemaModel.builder().stringType().build(),
        "zipCode", SchemaModel.builder().stringType().pattern("^\\d{6}$").build()
    ))
    .required(List.of("city"))
    .build();

// 定义用户 Schema（包含地址）
SchemaModel userSchema = SchemaModel.builder()
    .objectType()
    .properties(Map.of(
        "id", SchemaModel.builder().stringType().build(),
        "username", SchemaModel.builder().stringType().build(),
        "addresses", SchemaModel.builder()
            .arrayType()
            .items(addressSchema)
            .minItems(1)
            .maxItems(5)
            .build()
    ))
    .build();
```

### 5. 使用枚举值

```java
SchemaModel statusSchema = SchemaModel.builder()
    .stringType()
    .enumValues(List.of("ACTIVE", "INACTIVE", "PENDING"))
    .description("用户状态")
    .defaultValue("PENDING")
    .build();
```

### 6. 定义文件上传 API

```java
SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .post("/upload")
    .summary("上传文件")
    .operationId("uploadFile")
    .tag("文件管理")
    .requestBody(RequestBodyModel.builder()
        .required(true)
        .content(Map.of(
            "multipart/form-data", MediaTypeModel.builder()
                .schema(SchemaModel.builder()
                    .objectType()
                    .properties(Map.of(
                        "file", SchemaModel.builder()
                            .stringType()
                            .format("binary")
                            .description("上传的文件")
                            .build(),
                        "description", SchemaModel.builder()
                            .stringType()
                            .description("文件描述")
                            .build()
                    ))
                    .required(List.of("file"))
                    .build())
                .build()
        ))
        .build())
    .response("200", ResponseModel.builder()
        .description("上传成功")
        .build())
    .build();
```

### 7. 添加安全要求

```java
SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .get("/admin/users")
    .summary("管理员获取用户列表")
    .tag("管理员接口")
    .securityRequirement(SecurityRequirementModel.of("bearerAuth", List.of()))
    .response("200", ResponseModel.builder()
        .description("成功")
        .build())
    .response("401", ResponseModel.builder()
        .description("未授权")
        .build())
    .response("403", ResponseModel.builder()
        .description("无权限")
        .build())
    .build();
```

### 8. 标记废弃的 API

```java
SimpleWebAPIModel api = SimpleWebAPIModel.builder()
    .get("/v1/users")
    .summary("获取用户列表（已废弃）")
    .description("此接口已废弃，请使用 /v2/users")
    .deprecated(true)
    .tag("用户管理")
    .externalDocs(ExternalDocsModel.builder()
        .description("迁移指南")
        .url("https://docs.example.com/migration-guide")
        .build())
    .build();
```

## Schema 类型快速参考

### 基础类型

```java
// 字符串
SchemaModel.builder().stringType().build()

// 数字
SchemaModel.builder().numberType().build()

// 整数
SchemaModel.builder().integerType().build()

// 布尔
SchemaModel.builder().booleanType().build()

// 数组
SchemaModel.builder()
    .arrayType()
    .items(SchemaModel.builder().stringType().build())
    .build()

// 对象
SchemaModel.builder()
    .objectType()
    .properties(Map.of(...))
    .build()
```

### 字符串格式

```java
// 日期时间
SchemaModel.builder().stringType().format("date-time").build()

// 日期
SchemaModel.builder().stringType().format("date").build()

// 邮箱
SchemaModel.builder().stringType().format("email").build()

// UUID
SchemaModel.builder().stringType().format("uuid").build()

// 密码
SchemaModel.builder().stringType().format("password").build()

// 二进制
SchemaModel.builder().stringType().format("binary").build()
```

### 验证规则

```java
// 字符串长度
SchemaModel.builder()
    .stringType()
    .minLength(3)
    .maxLength(20)
    .build()

// 数字范围
SchemaModel.builder()
    .integerType()
    .minimum(1)
    .maximum(100)
    .build()

// 正则表达式
SchemaModel.builder()
    .stringType()
    .pattern("^[a-zA-Z0-9]+$")
    .build()

// 数组长度
SchemaModel.builder()
    .arrayType()
    .minItems(1)
    .maxItems(10)
    .uniqueItems(true)
    .build()
```

## 向后兼容性

所有 OpenAPI 3.0 扩展字段都是可选的，通过接口的 `default` 方法提供默认实现。这意味着：

1. 现有代码无需修改即可继续工作
2. 可以逐步迁移，按需添加 OpenAPI 信息
3. 未实现新方法的类会返回合理的默认值（如空集合、null 等）

## 最佳实践

1. **使用 operationId**：为每个 API 设置唯一的 operationId，便于代码生成和引用
2. **添加示例**：为请求和响应添加示例值，帮助 API 使用者理解
3. **完善描述**：summary 用于简短说明，description 用于详细解释
4. **标签分组**：使用 tags 对 API 进行分类组织
5. **定义所有响应**：不仅定义成功响应，也要定义常见的错误响应（400, 401, 404, 500 等）
6. **使用 Schema 引用**：对于复杂或重复的 Schema，可以使用 `$ref` 引用
7. **验证规则**：为参数和字段添加适当的验证规则（长度、范围、格式等）

## 后续工程缺口

统一 OpenAPI 3.1 导出及高代码抽取已经实现，不再列为待实现项。尚缺的版本化合同、漂移/消费者兼容门控与导出覆盖按[API-001](../../../docs/tech-debt-tracker.md#api-001-版本化接口契约与漂移校验)跟踪；Swagger UI、基于 Schema 的统一参数验证不由文档示例推定已落地。

## 参考资料

- [OpenAPI 3.0 规范](https://spec.openapis.org/oas/v3.0.3)
- [JSON Schema 规范](https://json-schema.org/)
- [Swagger UI](https://swagger.io/tools/swagger-ui/)
