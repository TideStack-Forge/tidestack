# Document Management Auto Configuration

文档管理模块的 Spring Boot 自动配置模块，提供开箱即用的自动装配功能。

## 功能概述

本模块提供以下自动配置功能：

1. **持久化层自动配置** - 自动配置 TypedDataModel 仓储实现（JPA 尚未实现）
2. **存储层自动配置** - 自动配置本地文件系统存储服务（S3、OSS 尚未实现）
3. **服务层自动配置** - 自动配置所有业务服务和门面服务
4. **Web API 自动配置** - 自动扫描并注册所有 REST API Controllers

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

    # 当前内置存储类型: filesystem
    storage-type: filesystem

    # 当前为 typed-model；该属性尚不参与实现选择
    persistence-type: typed-model

    # 是否启用 REST API
    enable-web-api: true

    # 本地文件系统存储配置
    storage:
      filesystem:
        base-path: /app/deploy/data/document-libraries
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

文档管理模块会自动配置并启动，无需任何额外代码。

## 配置项说明

### 核心配置

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `ouroboros.document-management.enabled` | boolean | `true` | 控制主配置；不是所有子模块的统一关闭开关 |
| `ouroboros.document-management.storage-type` | string | `filesystem` | 存储类型 |
| `ouroboros.document-management.persistence-type` | string | `typed-model` | 当前仅绑定属性，持久化自动配置未使用它选择实现 |
| `ouroboros.document-management.enable-web-api` | boolean | `true` | 是否启用 REST API |

### 存储类型

- `filesystem` - 本地文件系统存储（默认）
- `s3` - AWS S3 存储（待实现）
- `oss` - 阿里云 OSS 存储（待实现）

### 持久化类型

- `typed-model` - 使用 TypedDataModel（默认）
- `jpa` - 使用 JPA（待实现）

### 文件系统存储配置

| 配置项 | 类型 | 默认值 | 说明 |
|-------|------|--------|------|
| `ouroboros.document-management.storage.filesystem.base-path` | string | `/app/deploy/data/document-libraries` | 存储根路径 |
| `ouroboros.document-management.storage.filesystem.path-strategy` | enum | `HASH` | 路径策略：HASH/DATE/FLAT |
| `ouroboros.document-management.storage.filesystem.buffer-size` | int | `8192` | 缓冲区大小（字节） |
| `ouroboros.document-management.storage.filesystem.calculate-checksum` | boolean | `true` | 是否计算文件校验和 |

## 自动配置内容

### 1. 持久化层配置

持久化自动配置独立注册，当前直接装配 TypedDataModel 仓储；`persistence-type` 不参与其条件判断。对应仓储已有自定义 Bean 时跳过默认实现：

- `LibraryRepository` - 档案库仓储
- `LibraryNodeRepository` - 节点仓储
- `AclEntryRepository` - 权限条目仓储

**条件**：
- 实现及其依赖位于 classpath
- `@ConditionalOnMissingBean` - 没有用户自定义的 Bean

### 2. 存储层配置

当 `storage-type=filesystem` 时，自动配置：

- `StorageService` - 本地文件系统存储服务

**条件**：
- 实现及其依赖位于 classpath
- `@ConditionalOnMissingBean` - 没有用户自定义的 Bean

### 3. 服务层配置

自动配置以下服务：

**核心服务**：
- `DocumentLibraryService` - 档案库服务
- `LibraryContentService` - 档案库内容服务
- `PermissionAdminService` - 权限管理服务
- `ACLService` - ACL 服务

**门面服务**：
- `DocumentLibraryManagementFacade` - 档案库管理门面
- `DocumentLibraryContentFacade` - 档案库内容门面
- `DocumentLibraryAdminFacade` - 档案库后台管理门面

**条件**：
- `@ConditionalOnBean` - 依赖的仓储和存储服务已配置
- `@ConditionalOnMissingBean` - 没有用户自定义的 Bean

### 4. Web API 配置

当 `enable-web-api=true` 时，自动配置：

- `DocumentLibraryManagementController`
- `DocumentLibraryNavigationController`、`DocumentFileQueryController`、`DocumentFileOperationController`、`DocumentNodeManagementController`
- `DocumentLibraryAdminQueryController`、`DocumentLibraryAdminLifecycleController`（其他后台职责由专用控制器承担）

**条件**：
- `@ConditionalOnWebApplication` - 是 Web 应用
- `@ConditionalOnClass` - Spring MVC 存在
- `@ConditionalOnProperty` - `enable-web-api=true`

## 自定义配置

### 替换默认实现

如果需要使用自定义实现，只需定义相应的 Bean 即可：

```java
@Configuration
public class CustomDocumentManagementConfiguration {

    @Bean
    public StorageService customStorageService() {
        return new MyCustomStorageService();
    }

    @Bean
    public LibraryRepository customLibraryRepository() {
        return new MyCustomLibraryRepository();
    }
}
```

由于使用了 `@ConditionalOnMissingBean`，自动配置会检测到用户自定义的 Bean 并跳过默认配置。

### 禁用某个功能

**关闭主配置**：

```yaml
ouroboros:
  document-management:
    enabled: false
```

此属性只控制 `DocumentManagementAutoConfiguration`。存储与持久化模块各自通过自动配置资源独立注册，仍可装配；完整退出需要同时移除相关依赖或在应用的自动配置排除列表中显式排除对应配置类，不能仅依赖此属性。

**禁用 REST API**：

```yaml
ouroboros:
  document-management:
    enable-web-api: false
```

**禁用自动配置的特定部分**：

```java
@SpringBootApplication
@EnableAutoConfiguration(exclude = {
    DocumentManagementWebApiAutoConfiguration.class
})
public class Application {
    // ...
}
```

## 配置优先级

1. 用户自定义的 `@Bean`（最高优先级）
2. `application.yml` 中的配置属性
3. 自动配置的默认 Bean（最低优先级）

## 故障排查

### 如何查看自动配置结果

启动应用时添加 `--debug` 参数或在 `application.yml` 中设置：

```yaml
logging:
  level:
    org.springframework.boot.autoconfigure: DEBUG
```

查看控制台输出，找到 `DocumentManagementAutoConfiguration` 相关的日志。

### 常见问题

**Q: 为什么自动配置没有生效？**

A: 检查以下几点：
1. `ouroboros.document-management.enabled` 是否为 `true`
2. 相关依赖是否在 classpath 中
3. 是否有冲突的用户自定义 Bean

**Q: 如何确认某个 Bean 是否被自动配置？**

A: 注入 `ApplicationContext` 并检查：

```java
@Autowired
private ApplicationContext context;

public void checkBean() {
    boolean exists = !context.getBeansOfType(LibraryRepository.class).isEmpty();
    System.out.println("LibraryRepository exists: " + exists);
}
```

**Q: 自动配置的服务找不到依赖？**

A: 确保依赖的模块都在 classpath 中：
- 持久化层需要 `document-management-persistence`
- 存储层需要 `document-management-storage`
- 服务层需要 `document-management-service`

## 架构设计

### 配置分层

```
DocumentManagementAutoConfiguration (主配置)
    ├── DocumentManagementPersistenceAutoConfiguration (持久化配置)
    ├── DocumentStorageAutoConfiguration (独立内容存储配置)
    ├── DocumentManagementServiceAutoConfiguration (服务配置)
    └── DocumentManagementWebApiAutoConfiguration (Web API 配置)
```

### 依赖关系

```
Web API Controllers
    ↓ (依赖)
Facade Services
    ↓ (依赖)
Core Services
    ↓ (依赖)
Repositories + Storage Service
```

自动配置会按照依赖顺序自动装配所有组件。

## 扩展指南

### 添加新的存储类型

1. 实现 `StorageService` 接口
2. 创建新的配置类：

```java
@Configuration
@ConditionalOnProperty(prefix = "ouroboros.document-management", name = "storage-type", havingValue = "s3")
public class S3StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StorageService s3StorageService() {
        return new S3StorageService();
    }
}
```

3. 按[自动配置注册约定](../../../docs/design/cross-cutting/spring-autoconfiguration.md)注册新配置，或由使用方显式导入；不必修改现有主配置

### 添加新的持久化类型

1. 实现仓储接口
2. 创建新的配置类
3. 按[自动配置注册约定](../../../docs/design/cross-cutting/spring-autoconfiguration.md)注册新配置，或由使用方显式导入；不必修改现有主配置

## 相关文档

- [模块架构](../ARCHITECTURE.md)
- [共同设计](../DESIGN.md)
- [Document Management WebApp](../document-management-webapp/README.md)
- [Document Management](../README.md)
