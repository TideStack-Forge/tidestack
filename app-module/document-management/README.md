# 文档管理模块

Ouroboros 低代码平台的文档管理应用模块，提供企业级的文档库管理、目录管理、文档上传下载、以及细粒度的权限控制功能。

## 模块结构

```
document-management/
├── document-management-core/          # 核心接口和模型
├── document-management-persistence/   # 元数据/权限数据持久化（TypedDataModel 实现）
├── document-management-storage/       # 文档存储服务（本地文件系统实现）
├── document-management-infra-support/ # 基础设施支持（PrincipalType 实现等）
├── document-management-service/       # 业务服务和门面服务实现
├── document-management-webapp/        # REST API Controllers、UI Model 和 UI Schema
└── document-management-autoconfigure/ # Spring Boot 自动配置

```

## 核心功能

### 1. 文档库管理
- 创建文档库：拥有文档库创建权限的用户可以创建新的文档库
- 文档库列表：查看所有有权访问的文档库
- 文档库设置：修改文档库名称、描述等基本信息
- 文档库管理员：文档库创建者自动成为管理员，可分配其他管理员

### 2. 目录管理
- 创建目录：在文档库或目录下创建子目录
- 目录浏览：以树形或列表形式展示目录结构
- 目录操作：重命名、移动、删除目录
- 目录路径：显示从根目录到当前目录的完整路径

### 3. 文档管理
- 文档上传：支持上传文件到指定目录
- 文档预览：在线预览文档内容（需要预览权限）
- 文档下载：下载文档到本地（需要下载权限）
- 文档操作：重命名、移动、删除文档

### 4. 权限管理

#### 权限类型

权限分为三种作用范围：

##### 通用权限（文件和目录均可）

- **预览（VIEW）**：允许在线查看文档内容或浏览目录内容（只读）
- **下载（DOWNLOAD）**：允许下载文档，当指定给目录的时候表示目录中的所有文档都可以下载
- **重命名/移动（RENAME_MOVE）**：允许重命名或移动文档/目录
- **管理（MANAGE）**：允许设置权限等管理操作

##### 目录专用权限

- **导航（NAVIGATE）**：允许进入目录（但不能浏览）
- **创建目录（CREATE_DIR）**：允许在目录下创建子目录
- **创建文件（CREATE_FILE）**：允许在目录下上传文档
- **删除目录（DELETE_DIR）**：允许删除目录

##### 文件专用权限

- **删除文件（DELETE_FILE）**：允许删除文档

##### 权限代码对照表

| 权限代码 | 显示名称 | 作用范围 | 说明          |
|---------|---------|---------|-------------|
| `view` | 预览 | 文件和目录 | 查看文档内容或浏览目录 |
| `download` | 下载 | 文件和目录 | 下载文档或目录     |
| `rename_move` | 重命名/移动 | 文件和目录 | 重命名或移动      |
| `manage` | 管理 | 文件和目录 | 设置权限等管理操作   |
| `navigate` | 导航 | 仅目录 | 进入目录（但不能浏览） |
| `create_dir` | 创建目录 | 仅目录 | 创建子目录       |
| `create_file` | 创建文件 | 仅目录 | 上传文档        |
| `delete_dir` | 删除目录 | 仅目录 | 删除整个目录      |
| `delete_file` | 删除文件 | 仅文件 | 删除单个文档      |

#### 权限主体

权限主体（Principal）表示被授权的对象，可以是用户、用户组、角色、部门等。

##### 主体类型

- **Principal**：权限主体标识字符串（如用户ID、组ID等）
- **PrincipalType**：权限主体类型，定义主体的类型和解析逻辑
  - `getName()`：类型名称（如 "USER"、"GROUP"、"ROLE"）
  - `getDisplayName()`：显示名称（如 "用户"、"用户组"、"角色"）
  - `getResolver()`：解析器，用于将主体解析为用户ID列表
  - `getConfigUISchema()`：配置UI架构，定义前端选择该主体类型的界面组件

##### 已支持的主体类型

- **USER（用户）**：单个用户，提供用户选择器界面

##### 扩展主体类型

可通过 SPI 机制注册新的主体类型，详见 `document-management-infra-support` 模块文档。

#### 权限继承

- 目录权限自动继承父目录的权限
- 文档权限默认继承所在目录的权限
- 可以为子目录或文档单独设置额外的权限
- 最终权限 = 继承权限 + 自身权限（并集）

**示例 1：基本继承**
```
/项目文档 目录：用户A有 VIEW（预览）、NAVIGATE（导航）权限
/项目文档/设计文档 目录：用户B有 DOWNLOAD（下载）权限
结果：
  - 用户A对 /项目文档/设计文档 有 VIEW、NAVIGATE 权限（继承）
  - 用户B对 /项目文档/设计文档 有 DOWNLOAD 权限（自身）
  - 用户A的最终权限：VIEW、NAVIGATE
  - 用户B的最终权限：DOWNLOAD
```

**示例 2：权限组合**
```
/财务 目录：用户C有 VIEW、NAVIGATE、CREATE_FILE 权限
/财务/2024 目录：用户C又被赋予 DELETE_FILE 权限
结果：
  - 用户C对 /财务/2024 的最终权限：VIEW、NAVIGATE、CREATE_FILE（继承）+ DELETE_FILE（自身）
  - 最终权限集合：VIEW、NAVIGATE、CREATE_FILE、DELETE_FILE
```

## REST API

当前非安全接口路由统一见 [WebApp 控制器说明](document-management-webapp/README.md#controllers)，参数和响应以对应控制器源类型为准。

### 1. 文档库管理 API (`DocumentLibraryManagementController`)

```
# 管理员管理（所有者操作）
POST   /api/document-management/libraries/{id}/admins/{uid}         # 添加管理员
DELETE /api/document-management/libraries/{id}/admins/{uid}         # 移除管理员
POST   /api/document-management/libraries/{id}/admins/batch         # 批量添加管理员
DELETE /api/document-management/libraries/{id}/admins/batch         # 批量移除管理员
GET    /api/document-management/libraries/{id}/admins              # 获取所有管理员列表
POST   /api/document-management/libraries/{id}/transfer-ownership   # 转移所有权

# 访问权限管理（所有者和管理员）
GET    /api/document-management/libraries/{id}/permissions          # 查询权限
POST   /api/document-management/libraries/{id}/permissions          # 授予权限
PUT    /api/document-management/libraries/{id}/permissions          # 更新权限
DELETE /api/document-management/libraries/{id}/permissions          # 撤销权限
POST   /api/document-management/libraries/{id}/permissions/batch    # 批量授予权限
DELETE /api/document-management/libraries/{id}/permissions/batch    # 批量撤销权限

# 角色和权限检查
GET    /api/document-management/libraries/{id}/is-owner            # 检查是否为所有者
GET    /api/document-management/libraries/{id}/is-admin            # 检查是否为管理员
GET    /api/document-management/libraries/{id}/can-manage          # 检查是否可管理

```

### 2. 文档库内容 API

```
# 权限查询
GET    /api/document-library/documents/{docId}/permissions?libraryId={libraryId}    # 查询文件有效权限
GET    /api/document-library/{libraryId}/directories/{dirId}/permissions            # 查询目录有效权限
GET    /api/document-library/documents/{docId}/has-permission?libraryId={libraryId}&permission={permission}  # 检查文件权限
GET    /api/document-library/{libraryId}/directories/{dirId}/has-permission?permission={permission}  # 检查目录权限

# 权限管理（需要 MANAGE 权限）
GET    /api/document-library/documents/{docId}/acl-entries?libraryId={libraryId}    # 查询文件权限条目
GET    /api/document-library/{libraryId}/directories/{dirId}/acl-entries            # 查询目录权限条目
POST   /api/document-library/documents/{docId}/acl-entries                          # 为文件添加权限条目
POST   /api/document-library/{libraryId}/directories/{dirId}/acl-entries            # 为目录添加权限条目
DELETE /api/document-library/documents/{docId}/acl-entries?libraryId={libraryId}&principal={principal}&principalType={principalType}  # 撤销文件权限条目
DELETE /api/document-library/{libraryId}/directories/{dirId}/acl-entries?principal={principal}&principalType={principalType}  # 撤销目录权限条目

```

### 3. 文档库后台管理 API

```
# 所有权和管理员管理（系统级）
POST   /api/document-management/admin/libraries/{id}/transfer-ownership         # 转移所有权
POST   /api/document-management/admin/libraries/{id}/admins/{adminUserId}       # 添加管理员
DELETE /api/document-management/admin/libraries/{id}/admins/{adminUserId}       # 移除管理员
POST   /api/document-management/admin/libraries/{id}/admins/batch               # 批量添加管理员
DELETE /api/document-management/admin/libraries/{id}/admins/batch               # 批量移除管理员
PUT    /api/document-management/admin/libraries/{id}/owner                      # 设置文档库所有者
POST   /api/document-management/admin/libraries/{id}/admins/batch               # 批量设置管理员
PUT    /api/document-management/admin/libraries/{id}/admins                     # 批量设置管理员
GET    /api/document-management/admin/libraries/{id}/admins                     # 获取所有管理员列表

# 权限查询和管理
GET    /api/document-management/admin/libraries/{id}/permissions                # 查询文档库的所有访问权限条目
POST   /api/document-management/admin/libraries/{id}/permissions                # 为文档库授予访问权限
PUT    /api/document-management/admin/libraries/{id}/permissions                # 更新文档库的访问权限
DELETE /api/document-management/admin/libraries/{id}/permissions                # 撤销文档库的访问权限
POST   /api/document-management/admin/libraries/{id}/permissions/batch          # 批量授予文档库访问权限
DELETE /api/document-management/admin/libraries/{id}/permissions/batch          # 批量撤销文档库访问权限
DELETE /api/document-management/admin/libraries/{id}/permissions/clear          # 清空文档库的所有访问权限
GET    /api/document-management/admin/libraries/users/{userId}/permissions      # 查询用户在所有文档库中的权限
DELETE /api/document-management/admin/libraries/users/{userId}/permissions      # 撤销用户在所有文档库中的权限

```

## 技术架构

### 核心技术栈
- **Spring Boot 3.5.11**：Web 框架
- **Spring Security**：安全认证
- **Ouroboros Data Model**：动态数据模型，持久化仓储由 document-management-persistence 提供
- **Ouroboros UI Model**：UI 模型和 Schema 管理
- **Amis**：前端页面渲染

### 权限计算
权限计算通过 `ACLService` 接口实现，支持：
- 多个权限条目的合并
- 目录层级的权限继承
- 用户组、角色、部门的权限判断
- 高效的权限检查算法

### 扩展性设计
- **接口抽象**：核心功能定义为接口，便于替换实现
- **服务分离**：文档库、文件系统、权限服务各自独立
- **存储抽象**：当前提供本地文件系统 StorageService，元数据使用 TypedDataModel 仓储；其他存储后端需通过 StorageService 扩展
- **权限主体扩展**：支持多种权限主体类型，易于扩展

## 使用方式

### 1. 添加依赖

在 `pom.xml` 中添加：

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>document-management-autoconfigure</artifactId>
</dependency>
```

### 2. 配置属性

在 `application.yml` 中配置：

```yaml
ouroboros:
  document-management:
    # 是否启用文档管理模块
    enabled: true

    # 存储类型: filesystem（本地文件系统）
    storage-type: filesystem

    # 当前实现为 TypedDataModel；此属性尚不选择仓储实现
    persistence-type: typed-model

    # 是否启用 REST API
    enable-web-api: true

    # 本地文件系统存储配置
    storage:
      filesystem:
        base-path: /var/lib/ouroboros/document-storage
        path-strategy: HASH
        buffer-size: 8192
        calculate-checksum: true
```

### 3. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

由于使用了 Spring Boot 自动配置，所有组件会自动装配，无需额外代码。

### 4. 访问 API

启动应用后，可以通过 REST API 访问：

```bash
# 创建文档库
curl -X POST http://localhost:8080/api/document-libraries \
  -H "Content-Type: application/json" \
  -d '{"name":"project-docs","displayName":"项目文档","description":"团队项目文档"}'

# 查询用户可访问的文档库
curl -X GET http://localhost:8080/api/document-libraries/accessible

# 上传文件
curl -X POST http://localhost:8080/api/document-libraries/{libraryId}/directories/{dirId}/upload \
  -F "file=@document.pdf"
```

### 5. 自定义配置（可选）

如果需要使用自定义实现，可以定义自己的 Bean：

```java
@Configuration
public class CustomDocumentManagementConfig {

    @Bean
    public StorageService customStorageService() {
        return new MyCustomStorageService();
    }
}
```
