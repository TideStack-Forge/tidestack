/**
 * OpenAPI 3.1 数据模型包
 *
 * <p>本包提供面向 OpenAPI 3.1 的操作级与文档级模型，并兼容现有 OpenAPI 3.0 风格字段，包含以下核心模型：
 *
 * <h2>参数模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.ParameterModel} - 参数定义（路径、查询、请求头、Cookie）
 *   <li>{@link com.ouroboros.webapi.openapi.ParameterLocation} - 参数位置枚举
 * </ul>
 *
 * <h2>Schema 模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.SchemaModel} - JSON Schema 定义（类型、联合类型、组合、验证规则）
 * </ul>
 *
 * <h2>请求/响应模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.RequestBodyModel} - 请求体定义
 *   <li>{@link com.ouroboros.webapi.openapi.ResponseModel} - 响应定义
 *   <li>{@link com.ouroboros.webapi.openapi.MediaTypeModel} - 媒体类型（Content-Type）
 *   <li>{@link com.ouroboros.webapi.openapi.HeaderModel} - 响应头定义
 * </ul>
 *
 * <h2>示例模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.ExampleModel} - 示例值定义
 * </ul>
 *
 * <h2>其他支持模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.EncodingModel} - 编码信息
 *   <li>{@link com.ouroboros.webapi.openapi.LinkModel} - API 链接
 *   <li>{@link com.ouroboros.webapi.openapi.ServerModel} - 服务器定义
 *   <li>{@link com.ouroboros.webapi.openapi.ServerVariableModel} - 服务器变量
 *   <li>{@link com.ouroboros.webapi.openapi.ExternalDocsModel} - 外部文档
 *   <li>{@link com.ouroboros.webapi.openapi.SecurityRequirementModel} - 安全要求
 * </ul>
 *
 * <h2>文档导出模型</h2>
 * <ul>
 *   <li>{@link com.ouroboros.webapi.openapi.OpenAPIDocumentModel} - OpenAPI 顶层文档对象
 *   <li>{@link com.ouroboros.webapi.openapi.InfoModel} - 文档信息
 *   <li>{@link com.ouroboros.webapi.openapi.PathItemModel} - 路径项定义
 *   <li>{@link com.ouroboros.webapi.openapi.OperationModel} - 操作定义
 *   <li>{@link com.ouroboros.webapi.openapi.ComponentsModel} - 组件定义
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <h3>定义一个完整的 API</h3>
 * <pre>{@code
 * SimpleWebAPIModel api = SimpleWebAPIModel.builder()
 *     .post("/users")
 *     .summary("创建用户")
 *     .description("创建一个新用户账号")
 *     .operationId("createUser")
 *     .tag("用户管理")
 *     .parameter(ParameterModel.builder()
 *         .name("X-Request-ID")
 *         .in(ParameterLocation.HEADER)
 *         .description("请求追踪ID")
 *         .required(false)
 *         .schema(SchemaModel.builder().stringType().build())
 *         .build())
 *     .requestBody(RequestBodyModel.builder()
 *         .description("用户信息")
 *         .required(true)
 *         .content(Map.of(
 *             "application/json", MediaTypeModel.builder()
 *                 .schema(SchemaModel.builder()
 *                     .objectType()
 *                     .properties(Map.of(
 *                         "username", SchemaModel.builder().stringType().minLength(3).build(),
 *                         "email", SchemaModel.builder().stringType().format("email").build(),
 *                         "age", SchemaModel.builder().integerType().minimum(18).build()
 *                     ))
 *                     .required(List.of("username", "email"))
 *                     .build())
 *                 .build()
 *         ))
 *         .build())
 *     .response("200", ResponseModel.builder()
 *         .description("用户创建成功")
 *         .content(Map.of(
 *             "application/json", MediaTypeModel.builder()
 *                 .schema(SchemaModel.builder()
 *                     .objectType()
 *                     .properties(Map.of(
 *                         "id", SchemaModel.builder().stringType().build(),
 *                         "username", SchemaModel.builder().stringType().build()
 *                     ))
 *                     .build())
 *                 .build()
 *         ))
 *         .build())
 *     .response("400", ResponseModel.builder()
 *         .description("请求参数错误")
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h3>定义路径参数</h3>
 * <pre>{@code
 * SimpleWebAPIModel api = SimpleWebAPIModel.builder()
 *     .get("/users/{id}")
 *     .summary("获取用户详情")
 *     .parameter(ParameterModel.builder()
 *         .name("id")
 *         .in(ParameterLocation.PATH)
 *         .description("用户ID")
 *         .required(true)
 *         .schema(SchemaModel.builder().stringType().build())
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h3>定义查询参数</h3>
 * <pre>{@code
 * SimpleWebAPIModel api = SimpleWebAPIModel.builder()
 *     .get("/users")
 *     .summary("用户列表")
 *     .parameter(ParameterModel.builder()
 *         .name("page")
 *         .in(ParameterLocation.QUERY)
 *         .description("页码")
 *         .required(false)
 *         .schema(SchemaModel.builder().integerType().minimum(1).defaultValue(1).build())
 *         .build())
 *     .parameter(ParameterModel.builder()
 *         .name("size")
 *         .in(ParameterLocation.QUERY)
 *         .description("每页数量")
 *         .required(false)
 *         .schema(SchemaModel.builder().integerType().minimum(1).maximum(100).defaultValue(20).build())
 *         .build())
 *     .build();
 * }</pre>
 *
 * <h3>定义数组类型</h3>
 * <pre>{@code
 * SchemaModel arraySchema = SchemaModel.builder()
 *     .arrayType()
 *     .items(SchemaModel.builder()
 *         .objectType()
 *         .properties(Map.of(
 *             "id", SchemaModel.builder().stringType().build(),
 *             "name", SchemaModel.builder().stringType().build()
 *         ))
 *         .build())
 *     .minItems(0)
 *     .maxItems(100)
 *     .build();
 * }</pre>
 *
 * <h3>定义 OpenAPI 3.1 联合类型</h3>
 * <pre>{@code
 * SchemaModel nullableString = SchemaModel.builder()
 *     .types(Arrays.asList("string", "null"))
 *     .build();
 * }</pre>
 *
 * <h3>定义文档级导出对象</h3>
 * <pre>{@code
 * OpenAPIDocumentModel document = OpenAPIDocumentModel.builder()
 *     .openapi("3.1.0")
 *     .info(InfoModel.builder()
 *         .title("Demo API")
 *         .version("dev")
 *         .build())
 *     .path("/users", PathItemModel.builder()
 *         .get(OperationModel.builder()
 *             .summary("List users")
 *             .build())
 *         .build())
 *     .build();
 * }</pre>
 *
 * @see com.ouroboros.webapi.WebAPIModel
 * @see com.ouroboros.webapi.SimpleWebAPIModel
 */
package com.ouroboros.webapi.openapi;
