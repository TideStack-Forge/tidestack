# Document Management WebApp Module

HTTP 控制器按档案库管理、目录导航、文件查询/传输和节点变更分工；接口签名以 controller 源类型为准。

文档管理 REST API 模块，提供档案库管理与内容操作的 HTTP 接口；页面由源码元数据定义。

## 模块结构

```
document-management-webapp/
├── src/main/java/
│   └── com/ouroboros/documentmanagement/webapp/
│       ├── controller/
│       │   ├── DocumentLibraryManagementController.java     # 档案库管理 API
│       │   ├── DocumentLibraryNavigationController.java     # 目录导航
│       │   ├── DocumentFileQueryController.java             # 文件查询
│       │   ├── DocumentFileOperationController.java         # 上传/下载/预览
│       │   ├── DocumentNodeManagementController.java        # 节点变更
│       │   ├── DocumentLibraryAdminQueryController.java     # 后台查询
│       │   └── DocumentLibraryAdminLifecycleController.java # 后台生命周期管理
│       └── util/
│           └── UserContextHolder.java                       # 用户上下文工具类
├── src/main/resources/
│   └── META-INF/
│       ├── ouroboros/
│       │   ├── ui-model/
│       │   │   └── document-management/                    # UI Model 元数据
│       │   │       ├── library-list.json
│       │   │       ├── directory-browser.json
│       │   │       └── admin-console.json
│       │   ├── ui-schema/
│       │   │   └── document-management/                    # UI Schema 定义
│       │   │       ├── library-list.json
│       │   │       ├── directory-browser.json
│       │   │       └── admin-console.json
│       │   ├── menu-model/                                 # 菜单定义
│       │   │   └── DocumentManagement.json
│       │   ├── authority/                                  # 权限定义
│       │   │   ├── documentManagement.json
│       │   │   └── documentManagement/
│       │   │       └── admin.json
│       │   └── app-modules.json                            # 应用模块定义
│       └── services/
│           └── com.ouroboros.uimodel.UIModelProvider       # SPI 注册
└── pom.xml
```

## Controllers

### 1. DocumentLibraryManagementController

**路径**: `/api/document-libraries`

**职责**: 档案库管理（所有者和管理员视角）

**主要功能**:
- 档案库创建和查询
- 基本信息管理（所有者操作）
- 管理员管理（所有者操作）
- 所有权转移（所有者操作）
- 访问权限管理（所有者和管理员）
- 统计信息查询

**示例接口**:
```
POST   /api/document-libraries                    # 创建档案库
GET    /api/document-libraries/owned              # 查询用户拥有的档案库
GET    /api/document-libraries/{id}               # 获取档案库详情
PUT    /api/document-libraries/{id}/info          # 更新档案库基本信息
POST   /api/document-libraries/{id}/admins/{uid}  # 添加管理员
POST   /api/document-libraries/{id}/permissions   # 授予权限
```

### 2. 内容与节点控制器

目录导航由 `DocumentLibraryNavigationController` 提供，文件查询由 `DocumentFileQueryController` 提供，传输由 `DocumentFileOperationController` 提供，节点创建和变更由 `DocumentNodeManagementController` 提供。

**路径**: `/api/document-libraries/{libraryId}`

**职责**: 档案库内容管理和使用（使用者视角）

**主要功能**:
- 目录浏览和文件列表
- 文件查询和搜索
- 文件预览和下载
- 文件上传
- 目录创建
- 文件/目录移动、重命名、删除
- 权限查询和管理（需要 MANAGE 权限）
- 统计查询

**示例接口**:
```
GET    /api/document-libraries/{libraryId}/directories                                    # 目录树
GET    /api/document-libraries/{libraryId}/nodes/{nodeId}/path                               # 节点路径
GET    /api/document-libraries/{libraryId}/directories/{directoryId}                        # 目录详情
GET    /api/document-libraries/{libraryId}/search                                          # 文档库搜索
GET    /api/document-libraries/{libraryId}/directories/{directoryId}/search                 # 目录内搜索
GET    /api/document-libraries/{libraryId}/directories/{directoryId}/statistics             # 目录统计
PUT    /api/document-libraries/{libraryId}/documents/{documentId}/move                      # 移动文件
PUT    /api/document-libraries/{libraryId}/directories/{directoryId}/move                   # 移动目录
PUT    /api/document-libraries/{libraryId}/directories/{directoryId}/rename                 # 重命名目录
DELETE /api/document-libraries/{libraryId}/directories/{directoryId}                       # 删除目录
GET    /api/document-libraries/{libraryId}/directories/{directoryId}/children              # 获取指定目录下的子目录
GET    /api/document-libraries/{libraryId}/directories/{directoryId}/browse              # 浏览目录
GET    /api/document-libraries/{libraryId}/directories/{directoryId}/documents           # 获取目录下的文件
GET    /api/document-libraries/{libraryId}/documents/{documentId}                          # 获取文件详情
GET    /api/document-libraries/{libraryId}/documents/{documentId}/preview                  # 预览文件
GET    /api/document-libraries/{libraryId}/documents/{documentId}/download                 # 下载文件
POST   /api/document-libraries/{libraryId}/directories/{directoryId}/upload              # 上传文件
POST   /api/document-libraries/{libraryId}/directories                                     # 创建目录
PUT    /api/document-libraries/{libraryId}/documents/{documentId}/rename                   # 重命名文件
DELETE /api/document-libraries/{libraryId}/documents/{documentId}                          # 删除文件
```

### 3. 后台控制器

查询与生命周期分别由 `DocumentLibraryAdminQueryController`、`DocumentLibraryAdminLifecycleController` 提供，所有权及权限操作由各自专用控制器承担。

**路径**: `/api/document-management/admin/libraries`

**职责**: 档案库后台管理（系统管理员视角）

**特点**: 所有操作不验证权限，仅用于审计追踪

**主要功能**:
- 查询所有档案库
- 系统级所有权转移
- 系统级管理员管理
- 配额管理
- 档案库状态管理（启用/禁用、锁定/解锁）
- 删除操作（软删除、硬删除、恢复）
- 权限查询和管理
- 统计和监控

**示例接口**:
```
GET    /api/document-management/admin/libraries                      # 查询所有档案库
POST   /api/document-management/admin/libraries/{id}/transfer-ownership  # 转移所有权
PUT    /api/document-management/admin/libraries/{id}/quota           # 更新配额
POST   /api/document-management/admin/libraries/{id}/enable          # 启用档案库
POST   /api/document-management/admin/libraries/{id}/soft-delete     # 软删除
DELETE /api/document-management/admin/libraries/{id}                 # 硬删除
```


## UI Model & Schema

页面来自 `src/main/metadata` 中的 `.ui-model.json` 与 `.ui-schema.json`，经构建进入 `META-INF/ouroboros/metadata`；本模块没有 `DocumentManagementUIModelProvider` 或 `DocumentManagementUISchemaBuilder` 类。

当前页面入口包括 `document-management.ui-model.json`，以及 `document-management/` 下的 library-content、create-library、create-directory、upload-file、library-settings 和 admin-console 等定义。菜单源为 `DocumentManagement.menu.json`。页面路径、查询参数和后端调用由这些元数据维护，不在本页复制第二份 UI 合同。

新建和编辑文件遵守[元数据源码与编辑指南](../../../docs/guides/metadata-source-editor-guide.md)。

## Authority & App Modules

### 权限定义

本模块定义了两个权限（Authority），用于控制用户对文档管理功能的访问。

#### 1. documentManagement（普通用户权限）

**权限标识**: `documentManagement`

**权限名称**: 文档管理

**包含的功能**:
- 创建和管理自己的文档库
- 访问有权限的文档库
- 浏览目录和文件
- 上传、下载、预览文件
- 创建目录
- 重命名、移动、删除文件和目录
- 管理文档权限（需要 MANAGE 权限）

**API 权限**:
- 所有 `/api/document-management/libraries/**` 路径（管理自己的文档库）
- 所有 `/api/document-library/**` 路径（访问文档内容）

**UI 视图**:
- `/document-management/library-list` - 文档库列表
- `/document-management/directory-browser` - 目录浏览器

**对应菜单**: `DocumentManagement`

#### 2. documentManagement.admin（管理员权限）

**权限标识**: `documentManagement.admin`

**权限名称**: 文档库管理

**包含的功能**:
- 查看所有文档库
- 系统级所有权转移
- 系统级管理员管理
- 配额管理
- 启用/禁用/锁定/解锁文档库
- 软删除/硬删除/恢复文档库
- 查看全局统计信息

**API 权限**:
- 所有 `/api/document-management/admin/**` 路径

**UI 视图**:
- `/document-management/admin-console` - 管理员控制台

### 应用模块定义

`app-modules.json` 定义了两个应用模块：

```json
[
  {
    "fullName": "documentManagement",
    "title": "文档管理",
    "icon": "fa fa-folder-open",
    "menu": "Application.DocumentManagement",
    "authorities": ["documentManagement"],
    "order": 50
  },
  {
    "fullName": "documentManagement.admin",
    "title": "文档库管理",
    "icon": "fa fa-cog",
    "menu": "System.DocumentManagementAdmin",
    "authorities": ["documentManagement.admin"],
    "order": 80
  }
]
```

**模块说明**:

1. **documentManagement 模块**:
   - 面向普通用户
   - 提供文档库管理和文件管理功能
   - 需要 `documentManagement` 权限

2. **documentManagement.admin 模块**:
   - 面向系统管理员
   - 提供文档库后台管理功能
   - 需要 `documentManagement.admin` 权限

### 权限分配

#### 通过统一授权模型分配权限

在 Ouroboros 平台中，角色（Role）仍然是当前最常见的权限分配载体，但它应被理解为统一授权模型的一部分，而不是唯一默认入口：

1. **创建角色或等价授权载体**:
   - 普通用户角色：分配 `documentManagement` 权限
   - 文档管理员角色：分配 `documentManagement` + `documentManagement.admin` 权限

2. **分配授权关系**:
   - 将用户加入对应角色，或通过统一身份/授权链路让用户获得等价权限
   - 用户自动获得角色包含的权限

如果你当前关心的是“角色、主体、Claims 和访问控制参数如何一起工作”，建议先看：

- `docs/handbook/docs/fusion-development/authority-subject-and-access-control-design.md`
- `docs/handbook/docs/guide/advance/authority-and-access-control.md`

#### 权限层级

```
documentManagement（普通用户）
  ├── 创建文档库
  ├── 管理自己的文档库
  ├── 访问有权限的文档库
  └── 文件和目录操作

documentManagement.admin（管理员）
  ├── 包含 documentManagement 的所有功能
  ├── 查看所有文档库
  ├── 系统级管理操作
  └── 全局统计和监控
```

**注意**: `documentManagement.admin` 权限不自动包含 `documentManagement` 权限，如果管理员需要使用普通用户功能，需要同时分配两个权限。

### 资源文件结构

```
META-INF/ouroboros/
├── authority/                           # 权限定义
│   ├── documentManagement.json         # 普通用户权限
│   └── documentManagement/
│       └── admin.json                  # 管理员权限
└── app-modules.json                     # 应用模块定义
```

### 扩展权限

#### 添加新的子权限

如果需要添加更细粒度的权限，例如 `documentManagement.export`（导出权限）：

1. 创建权限定义文件：
```json
// authority/documentManagement/export.json
{
  "fullName": "documentManagement.export",
  "title": "文档导出",
  "apis": [
    "POST /api/document-library/documents/{docId}/export",
    "POST /api/document-library/{libraryId}/export-all"
  ],
  "uiViews": [],
  "menuItems": []
}
```

2. 在 `app-modules.json` 中添加对应的模块（可选）

3. 在需要的角色中分配该权限

## 用户认证

### 通过 Spring Security 获取用户ID

使用 `UserContextHolder` 工具类从 Spring Security 上下文获取当前登录用户信息：

```java
String userId = UserContextHolder.getCurrentUserId();
String userId = UserContextHolder.requireCurrentUserId(); // 未登录则抛异常
boolean isAuthenticated = UserContextHolder.isAuthenticated();
```

所有 Controller 方法都已使用 `UserContextHolder` 获取用户ID，不再需要 `@RequestHeader("X-User-Id")` 参数。
}

// 修改后
public WebApiResult<List<DocumentLibrary>> listOwnedLibraries() {
  String userId = UserContextHolder.requireCurrentUserId();
  // ...
}
```

## Request/Response 格式

### 统一响应格式

控制器使用 `WebApiResult<T>` 或 `ItemsWebApiResult<T>`。响应字段以[公共响应类型](../../../libs/common/common-web/src/main/java/com/ouroboros/web/AbstractWebApiResult.java)为准（`code`、`msg`、`data`）；本页不复制另一套 `success/errorCode/message` 结构。

### 请求示例

**创建档案库**:
```json
POST /api/document-libraries
Content-Type: application/json

{
  "name": "project-docs",
  "displayName": "项目文档",
  "description": "团队项目相关文档"
}
```

**上传文件**:
```
POST /api/document-libraries/{libraryId}/directories/{dirId}/upload
Content-Type: multipart/form-data

file: (binary file data)
```

**授予权限**:
```json
POST /api/document-management/libraries/{libraryId}/permissions
Content-Type: application/json

{
  "principal": "user456",
  "principalType": "USER",
  "permissions": {
    "permissions": ["VIEW", "DOWNLOAD"]
  }
}
```

## 依赖关系

```
document-management-webapp
  ↓
document-management-service (三个门面服务)
  ↓
document-management-core (模型和接口)
```

## 使用方式

### 添加依赖

在需要使用 REST API 的模块中添加：

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>document-management-webapp</artifactId>
</dependency>
```

### 启用模块

由于使用了 Spring Boot 自动扫描，只需添加依赖并确保 Controller 包在扫描路径内即可：

```java
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.ouroboros.documentmanagement.webapp",
    // 其他包...
})
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

## 权限控制

### DocumentLibraryManagementController

- **所有者操作**: 修改基本信息、添加/删除管理员、转移所有权、更新配额
- **所有者和管理员**: 管理访问权限（添加/修改/删除 ACL 条目）
- **所有用户**: 创建档案库、查询自己可访问的档案库

### DocumentLibraryContentController

- **权限验证**: 所有操作都需要相应的权限（VIEW、DOWNLOAD、CREATE_FILE、CREATE_DIR、DELETE_FILE、DELETE_DIR、RENAME_MOVE、MANAGE）
- **继承权限**: 子节点继承父节点权限
- **MANAGE 权限**: 管理节点权限需要 MANAGE 权限

### DocumentLibraryAdminController

- **无权限验证**: 所有操作都不验证权限
- **审计追踪**: 通过 `X-Operator-Id` 记录操作者
- **系统管理员**: 仅供系统管理员使用

## 错误处理

Controller 中的错误会被自动转换为标准的 `WebApiResult` 格式：

```java
// 资源不存在
WebApiResult.notFound("档案库不存在或无权限访问")

// 权限不足
WebApiResult.forbidden("无权限执行此操作")

// 参数错误
WebApiResult.badRequest("参数无效")

// 服务器错误
WebApiResult.error("服务器内部错误")
```

## 扩展指南

### 添加新的 API

1. 在对应的 Controller 中添加新方法
2. 使用 `@GetMapping`、`@PostMapping` 等注解定义路由
3. 注入相应的门面服务并调用方法
4. 返回 `WebApiResult<T>` 格式的响应

### 添加新的 DTO

在 Controller 内部创建静态内部类作为 Request DTO：

```java
public static class CustomRequest {
    @NotBlank public String field1;
    @NotNull public Integer field2;
}
```

### 自定义错误处理

可以创建 `@ControllerAdvice` 来统一处理异常：

```java
@ControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<WebApiResult<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity
            .badRequest()
            .body(WebApiResult.badRequest(ex.getMessage()));
    }
}
```

## 注意事项

1. **用户ID获取**: 通过 `UserContextHolder` 工具类从 Spring Security 上下文获取当前登录用户ID
2. **文件上传**: 使用 `MultipartFile` 接收文件，注意设置 `spring.servlet.multipart.max-file-size` 配置
3. **文件下载**: 返回 `ResponseEntity<Resource>`，设置正确的 Content-Type 和 Content-Disposition
4. **批量操作**: 支持批量操作的接口使用 List 接收参数
5. **权限验证**: 所有业务逻辑的权限验证在 Service 层完成，Controller 只负责参数校验和响应格式化

## 相关文档

- [Document Management Core](../../../README.md) - 核心接口和模型
- [Document Management Service](../../../README.md) - 业务服务实现
- [Document Management](../README.md) - 模块总体说明
