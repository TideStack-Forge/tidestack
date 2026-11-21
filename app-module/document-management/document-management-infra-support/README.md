# Document Management Infrastructure Support

TODO: 当前文档已过时，需要重新同步代码

文档管理基础设施支持模块，提供 PrincipalType 实现和其他基础设施组件。

## 功能概述

### 1. Principal 类型实现

提供不同类型 Principal 的实现，用于权限控制系统。通过 Java SPI 机制自动注册。

#### 已实现的类型

##### UserPrincipalType

用户主体类型，采用透传式解析逻辑：

```java
// 通过 PrincipalTypeProvider 获取
Optional<PrincipalType> userTypeOpt = PrincipalTypeProvider.get("USER");

if (userTypeOpt.isPresent()) {
    PrincipalType userType = userTypeOpt.get();

    // 获取类型信息
    String name = userType.getName();           // "USER"
    String displayName = userType.getDisplayName();  // "用户"

    // 获取解析器
    PrincipalResolver resolver = userType.getResolver();

    // 扩展为用户ID列表（透传）
    List<String> userIds = resolver.expandToUserIds("user123");
    // 结果: ["user123"]

    // 判断用户是否包含（直接比较）
    boolean contains = resolver.containsUser("user123", "user123");
    // 结果: true

    // 获取用户的 Principal 列表（返回自身）
    List<String> principals = resolver.getPrincipalsForUser("user123");
    // 结果: ["user123"]

    // 获取配置 UI Schema
    Optional<SchemaNode> uiSchema = userType.getConfigUISchema();
    // 结果: Optional.of(schema)（用户选择器）
}
```

#### 计划支持的类型

- **GroupPrincipalType** - 用户组类型（将用户组扩展为组内所有用户）
- **RolePrincipalType** - 角色类型（将角色扩展为拥有该角色的所有用户）
- **DepartmentPrincipalType** - 部门类型（将部门扩展为部门内所有用户）

## SPI 机制

### 两层 SPI 架构

模块使用两层 SPI 机制提供灵活的主体类型注册方式：

#### 第一层：PrincipalType 直接注册

**SPI 接口：** `com.ouroboros.documentmanagement.model.acl.PrincipalType`

**注册方式：**

1. 实现 `PrincipalType` 接口
2. 在 `META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalType` 文件中注册实现类

**适用场景：** 单个主体类型的简单注册

#### 第二层：PrincipalTypeProvider 批量注册

**SPI 接口：** `com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider`

**注册方式：**

1. 实现 `PrincipalTypeProvider` 接口
2. 在 `getPrincipalTypes()` 方法中返回多个 `PrincipalType`
3. 在 `META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider` 文件中注册实现类

**适用场景：** 批量注册多个主体类型，或需要动态加载主体类型

### 本模块注册的 SPI 服务

```
META-INF/services/
├── com.ouroboros.documentmanagement.model.acl.PrincipalType
│   └── com.ouroboros.documentmanagement.infra.principal.UserPrincipalType
└── com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider
    └── com.ouroboros.documentmanagement.infra.principal.ServiceLoaderPrincipalTypeProvider
```

**ServiceLoaderPrincipalTypeProvider** 是一个通用的聚合提供者，它通过 `ServiceLoader` 自动加载所有注册为 `PrincipalType` SPI 服务的实现。

## 依赖引入

在需要使用 PrincipalType 的模块中添加依赖：

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>document-management-infra-support</artifactId>
</dependency>
```

引入此依赖后，用户主体类型会自动通过 SPI 注册，无需手动配置。

## 使用示例

### 通过 PrincipalTypeProvider 获取主体类型

```java
@Service
public class ACLServiceImpl implements ACLService {

    @Override
    public boolean hasPermission(String userId, AclEntry entry, Permission permission) {
        // 通过 SPI 获取主体类型
        Optional<PrincipalType> principalTypeOpt =
            PrincipalTypeProvider.get(entry.getPrincipalType());

        if (principalTypeOpt.isEmpty()) {
            return false;
        }

        PrincipalType principalType = principalTypeOpt.get();

        // 获取解析器
        PrincipalResolver resolver = principalType.getResolver();

        // 判断用户是否包含在 Principal 中
        if (resolver.containsUser(entry.getPrincipal(), userId)) {
            return entry.getPermissions().contains(permission);
        }

        return false;
    }
}
```

### 获取所有已注册的主体类型

```java
// 获取所有主体类型
List<PrincipalType> allTypes = PrincipalTypeProvider.getAll();

for (PrincipalType type : allTypes) {
    System.out.println(type.getName() + " - " + type.getDisplayName());
}
// 输出: USER - 用户
```

### 检查主体类型是否存在

```java
// 检查 USER 类型是否已注册
Optional<PrincipalType> userType = PrincipalTypeProvider.get("USER");
if (userType.isPresent()) {
    System.out.println("USER 类型已注册");
}
```

## 扩展指南

### 添加新的主体类型

有两种方式添加新的主体类型：

#### 方式一：直接注册 PrincipalType（推荐）

**适用场景：** 单个主体类型的简单注册

**步骤 1: 创建 PrincipalType 实现类**

```java
package com.example.document.principal;

import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.uischema.schema.SchemaNode;

import java.util.List;
import java.util.Optional;

public class GroupPrincipalType implements PrincipalType {

    private final PrincipalResolver resolver;

    public GroupPrincipalType(GroupService groupService) {
        this.resolver = new GroupPrincipalResolver(groupService);
    }

    @Override
    public String getName() {
        return "GROUP";
    }

    @Override
    public String getDisplayName() {
        return "用户组";
    }

    @Override
    public PrincipalResolver getResolver() {
        return resolver;
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        // 返回树形选择器配置
        SchemaNode schema = new SchemaNode();
        schema.setType("tree-select");
        schema.setProp("label", "选择用户组");
        schema.setProp("name", "groupId");
        schema.setProp("source", "/api/groups/tree");
        schema.setProp("required", true);
        return Optional.of(schema);
    }

    private static class GroupPrincipalResolver implements PrincipalResolver {

        private final GroupService groupService;

        public GroupPrincipalResolver(GroupService groupService) {
            this.groupService = groupService;
        }

        @Override
        public List<String> expandToUserIds(String principal) {
            // 查询组内所有用户
            return groupService.getUserIdsInGroup(principal);
        }

        @Override
        public boolean containsUser(String principal, String userId) {
            // 检查用户是否在组内
            return groupService.isUserInGroup(userId, principal);
        }

        @Override
        public List<String> getPrincipalsForUser(String userId) {
            // 获取用户所属的所有组
            return groupService.getGroupsForUser(userId);
        }
    }
}
```

**步骤 2: 注册 SPI 服务**

创建文件：`META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalType`

```
com.example.document.principal.GroupPrincipalType
```

完成后，GROUP 类型会自动通过 SPI 注册，可以通过 `PrincipalTypeProvider.get("GROUP")` 获取。

---

#### 方式二：通过 PrincipalTypeProvider 批量注册

**适用场景：** 批量注册多个主体类型，或需要依赖注入/动态加载

**步骤 1: 创建 PrincipalType 实现类**

与方式一相同，创建 `GroupPrincipalType` 类（代码见上）

**步骤 2: 创建 PrincipalTypeProvider 实现类**

```java
package com.example.document.principal;

import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider;

import java.util.Arrays;
import java.util.List;

public class CustomPrincipalTypeProvider implements PrincipalTypeProvider {

    @Override
    public List<PrincipalType> getPrincipalTypes() {
        // 可以批量返回多个主体类型
        GroupService groupService = ...; // 获取依赖服务
        RoleService roleService = ...;

        return Arrays.asList(
            new GroupPrincipalType(groupService),
            new RolePrincipalType(roleService)
        );
    }
}
```

**步骤 3: 注册 SPI 服务**

创建文件：`META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider`

```
com.example.document.principal.CustomPrincipalTypeProvider
```

完成后，所有返回的类型会自动通过 SPI 注册。

## 架构说明

### SPI 加载流程

```
应用启动
    ↓
PrincipalTypeProvider.PrincipalTypeRegistry 初始化
    ↓
╔═══════════════════════════════════════════════════════════╗
║ 第一阶段：加载 PrincipalTypeProvider                        ║
╠═══════════════════════════════════════════════════════════╣
║ ServiceLoader 扫描 META-INF/services/                     ║
║     com.ouroboros....acl.PrincipalTypeProvider           ║
║   ↓                                                       ║
║ 加载所有 PrincipalTypeProvider 实现                         ║
║   ↓                                                       ║
║ 调用 getPrincipalTypes() 获取 PrincipalType 列表            ║
║   ↓                                                       ║
║ 注册到 Registry（Map<String, PrincipalType>）               ║
╚═══════════════════════════════════════════════════════════╝
    ↓
╔═══════════════════════════════════════════════════════════╗
║ 第二阶段：加载直接注册的 PrincipalType                        ║
╠═══════════════════════════════════════════════════════════╣
║ ServiceLoader 扫描 META-INF/services/                     ║
║     com.ouroboros....acl.PrincipalType                   ║
║   ↓                                                       ║
║ 加载所有 PrincipalType 实现                                 ║
║   ↓                                                       ║
║ 注册到 Registry（如已存在则覆盖）                            ║
╚═══════════════════════════════════════════════════════════╝
    ↓
应用可通过 PrincipalTypeProvider.get(name) 获取
```

**注意：** 如果同一类型名称在两个阶段都出现，第二阶段（直接注册）会覆盖第一阶段（Provider 提供）的实现。

### PrincipalType 接口

定义主体类型的基本结构：

```java
public interface PrincipalType {
    String getName();                           // 类型名称（唯一标识）
    String getDisplayName();                    // 显示名称
    PrincipalResolver getResolver();            // 解析器
    Optional<SchemaNode> getConfigUISchema();   // 配置 UI 架构（可选）
}
```

**getConfigUISchema()** 方法用于返回前端配置该主体类型的 UI Schema 定义：
- 返回 `Optional<SchemaNode>`，如果不需要配置界面则返回 `Optional.empty()`
- 用于定义选择或配置该主体的前端界面组件
- 例如：用户组类型可能需要树形选择器，角色类型可能需要多选框

### PrincipalTypeProvider 接口

SPI 服务接口，提供主体类型列表：

```java
public interface PrincipalTypeProvider {
    List<PrincipalType> getPrincipalTypes();

    // 静态方法
    static Optional<PrincipalType> get(String name);
    static List<PrincipalType> getAll();
}
```

### UserPrincipalType 实现

用户类型采用透传式设计：

- **getName()** - 返回 "USER"
- **getDisplayName()** - 返回 "用户"
- **getResolver()** - 返回内部的 UserPrincipalResolver
- **getConfigUISchema()** - 返回 `Optional.of(schema)`（用户选择器）

### UserPrincipalResolver 逻辑

- **expandToUserIds(principal)** - 直接将 principal 包装成单元素列表
- **containsUser(principal, userId)** - 判断 principal 是否等于 userId
- **getPrincipalsForUser(userId)** - 返回包含 userId 的单元素列表

### 配置 UI 使用示例

对于需要配置界面的主体类型（如用户组），可以这样实现：

```java
public class GroupPrincipalType implements PrincipalType {

    @Override
    public String getName() {
        return "GROUP";
    }

    @Override
    public String getDisplayName() {
        return "用户组";
    }

    @Override
    public PrincipalResolver getResolver() {
        return new GroupPrincipalResolver();
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        // 返回一个树形选择器配置
        SchemaNode schema = new SchemaNode();
        schema.setType("tree-select");
        schema.setProp("label", "选择用户组");
        schema.setProp("name", "groupId");
        schema.setProp("source", "/api/groups/tree");
        schema.setProp("required", true);

        return Optional.of(schema);
    }
}
```

## 注意事项

1. **类型名称唯一性** - 每个 PrincipalType 的 name 必须唯一
2. **SPI 文件位置** - 必须放在 `META-INF/services/` 目录下
3. **SPI 文件内容** - 每行一个实现类的全限定名
4. **线程安全** - PrincipalResolver 实现应该是线程安全的
5. **性能考虑** - Registry 使用 ConcurrentHashMap 缓存，无需重复加载
6. **依赖注入** - 如果 PrincipalType 需要依赖其他服务，需要在 Provider 中处理依赖获取

## 常见问题

### Q: 如何验证 SPI 是否注册成功？

A: 在应用启动后，可以通过以下代码验证：

```java
List<PrincipalType> allTypes = PrincipalTypeProvider.getAll();
System.out.println("已注册的主体类型数量: " + allTypes.size());
for (PrincipalType type : allTypes) {
    System.out.println("- " + type.getName() + " (" + type.getDisplayName() + ")");
}
```

### Q: 应该使用哪种注册方式？

A: 根据具体场景选择：

- **方式一（直接注册 PrincipalType）**：适合简单场景，无需依赖注入，一个文件一个类型
- **方式二（通过 Provider 注册）**：适合复杂场景，需要依赖注入，或批量注册多个类型

**推荐：** 对于大多数场景，使用方式一更简单直接。

### Q: 如何在 Provider 中注入依赖？

A: 由于 SPI 使用无参构造函数创建实例，需要手动处理依赖：

```java
public class GroupPrincipalTypeProvider implements PrincipalTypeProvider {

    @Override
    public List<PrincipalType> getPrincipalTypes() {
        // 方法1: 使用静态工厂或单例获取服务
        GroupService groupService = ServiceLocator.getGroupService();

        // 方法2: 延迟初始化，在实际使用时再获取
        return Collections.singletonList(new GroupPrincipalType(groupService));
    }
}
```

### Q: 同一类型在两个阶段都注册会怎样？

A: 第二阶段（直接注册的 PrincipalType）会覆盖第一阶段（通过 Provider 注册）的实现。

### Q: ServiceLoaderPrincipalTypeProvider 的作用是什么？

A: 它是一个通用的聚合提供者，将所有直接注册的 `PrincipalType` 聚合到 `PrincipalTypeProvider` 体系中。它本身也通过 SPI 注册为 `PrincipalTypeProvider`。

### Q: 为什么要设计两层 SPI？

A: 两层 SPI 提供了更灵活的扩展方式：

1. **简单扩展**：直接注册 `PrincipalType`，无需额外的 Provider 类
2. **批量扩展**：通过 `PrincipalTypeProvider` 批量注册多个类型
3. **依赖注入**：Provider 方式可以在 `getPrincipalTypes()` 中处理复杂的依赖关系
4. **统一访问**：无论哪种方式注册，都通过 `PrincipalTypeProvider.get(name)` 统一访问

### Q: getConfigUISchema() 什么时候返回配置 UI？

A: 根据主体类型的特点决定：

- **返回 `Optional.empty()`**：
  - 不需要额外选择界面的特殊主体类型

- **返回 `Optional.of(schema)`**：
  - 用户 (USER) - 需要从列表中选择用户
  - 用户组（GROUP）- 需要从树形结构中选择用户组
  - 角色（ROLE）- 需要从列表中选择角色
  - 部门（DEPARTMENT）- 需要从组织树中选择部门
  - 需要提供选择器或配置界面的复杂主体类型

返回的 `SchemaNode` 定义了前端渲染该主体类型选择器的 UI 组件配置（基于 amis schema）。
