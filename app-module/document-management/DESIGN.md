# 文档管理模块设计文档

本文是文档管理机制的共同正文，供开发者与各类 Agent 共用。组件分工见 [架构总览](ARCHITECTURE.md)，使用方式见 [README](README.md)；接口变更在本页同步，不另维护模型专用副本。

## 1. 概述

### 1.1 项目背景

文档管理模块是 Ouroboros 低代码平台的应用模块，提供企业级的文档库管理、目录管理、文档存储和细粒度权限控制功能。模块参考了飞书、钉钉等主流协作平台的知识库设计，通过统一的节点抽象（LibraryNode）和灵活的权限继承机制，实现了简洁而强大的文档管理能力。

### 1.2 设计目标

1. **完整的文档管理能力**：支持文档库、目录、文档的完整生命周期管理
2. **细粒度权限控制**：区分预览和下载权限，支持多级继承
3. **统一的节点抽象**：目录和文档使用统一的 LibraryNode 接口，简化层级管理
4. **接口驱动设计**：Core 包定义所有接口，便于测试和替换实现
5. **平台化设计**：充分利用 Ouroboros 平台的能力（数据模型、UI Schema、权限系统）
6. **高扩展性**：通过接口抽象和分层设计，便于功能扩展和实现替换

### 1.3 核心特性

- ✅ **统一的节点模型**：LibraryNode 统一抽象目录和文档
- ✅ **细粒度权限**：9 种权限类型（VIEW, DOWNLOAD, CREATE_FILE, CREATE_DIR, RENAME_MOVE, DELETE_FILE, DELETE_DIR, NAVIGATE, MANAGE），支持作用域区分
- ✅ **权限继承**：子节点自动继承父节点权限，可叠加自身权限
- ✅ **元数据与文件分离**：元数据通过 Repository 持久化，文件通过 StorageService 存储
- ✅ **服务职责分离**：DocumentLibraryService、LibraryContentService、ACLService、PermissionAdminService
- ✅ **存储抽象**：支持本地文件系统、对象存储等多种存储方式

---

## 2. 核心概念设计

### 2.1 LibraryNode（节点抽象）

**核心设计理念**：文档管理模块的核心创新是 `LibraryNode` 节点抽象，它统一了目录和文档的概念。

#### 2.1.1 LibraryNode 接口

```java
/**
 * 文档库节点的基类接口
 * 目录（DirectoryNode）和文档（DocumentNode）都继承此接口
 */
public interface LibraryNode {
    // 标识信息
    String getId();                   // 节点ID
    String getParentId();             // 父目录ID（null 表示根目录）
    NodeType getType();               // 节点类型（DIRECTORY / DOCUMENT）
    String getName();                 // 节点名称
    String getDescription();          // 描述
    String getLibraryId();            // 所属文档库ID

    // 审计信息
    String getCreatedBy();            // 创建人
    LocalDateTime getCreatedAt();     // 创建时间
    String getUpdatedBy();            // 更新人
    LocalDateTime getUpdatedAt();     // 更新时间

    // 权限
    List<AclEntry> getPermissions();  // 节点自身的权限条目
}
```

**设计优势**：

1. **统一的层级结构**
   - 目录和文档都是节点，都有 `parentId` 和 `libraryId`
   - 简化了层级关系的管理
   - 避免了目录和文档分离的复杂性

2. **简化权限继承**
   - 所有节点都有权限列表
   - 权限计算只需遍历节点路径，收集权限条目
   - 不需要区分目录权限和文档权限

3. **类型安全**
   - 通过 `NodeType` 枚举区分节点类型
   - 避免了运行时类型判断错误

4. **易于扩展**
   - 未来可以添加新的节点类型（如快捷方式、符号链接、外部文档引用等）
   - 只需实现 `LibraryNode` 接口并添加新的 `NodeType`

#### 2.1.2 DirectoryNode（目录节点）

```java
/**
 * 目录节点接口，继承自 LibraryNode
 */
public interface DirectoryNode extends LibraryNode {
    /**
     * 获取子节点列表（包括子目录和文档）
     */
    List<LibraryNode> getChildren();

    /**
     * 判断是否为根目录
     */
    default boolean isRoot() {
        return getParentId() == null;
    }
}
```

**特点**：
- 可以包含子目录和文档（通过 `getChildren()` 返回）
- 根目录的 `parentId` 为 null
- 支持任意深度的层级结构

**设计考虑**：
- `getChildren()` 返回的是 `List<LibraryNode>` 而不是分别返回子目录和子文档，体现了节点抽象的统一性
- 调用方可以通过 `node.getType()` 或 `instanceof` 判断节点类型

#### 2.1.3 DocumentNode（文档节点）

```java
/**
 * 文档节点接口，继承自 LibraryNode
 */
public interface DocumentNode extends LibraryNode {
    /**
     * 获取文档大小（字节）
     */
    Long getSize();

    /**
     * 获取文档MIME类型
     */
    String getMimeType();

    /**
     * 获取存储路径（实际文件存储位置）
     */
    String getStoragePath();

    /**
     * 获取文件扩展名
     */
    default String getExtension() {
        String name = getName();
        if (name == null) {
            return "";
        }
        if (name.contains(".")) {
            return name.substring(name.lastIndexOf(".") + 1);
        }
        return "";
    }
}
```

**特点**：
- 必须属于某个目录（`parentId` 不为 null）
- 通过 `storagePath` 关联到实际文件内容
- 包含文件元信息（大小、类型、扩展名）

**元数据与文件分离**：
- `DocumentNode` 只存储元数据（名称、大小、类型等）
- 实际文件内容通过 `StorageService.retrieve(storagePath)` 获取
- 这种分离使得元数据和文件可以分别管理和优化

### 2.2 DocumentLibrary（文档库）

```java
/**
 * 文档库接口
 */
public interface DocumentLibrary {
    // 基本信息
    String getId();                  // 文档库ID
    String getName();                // 名称
    String getDisplayName();         // 显示名称
    String getDescription();         // 描述

    // 权限管理
    String getOwner();               // 拥有者ID
    List<String> getAdminIds();      // 管理员ID列表
    List<AclEntry> getPermissions(); // 文档库级别的权限

    // 配额限制
    Long maxSizeLimit();             // 最大存储空间限制（字节）
    Long maxFileCountLimit();        // 最大文件数量限制

    // 状态管理
    boolean isDeleted();             // 是否被删除（软删除）
    boolean isDisabled();            // 是否被禁用
    boolean isLocked();              // 是否被锁定（只读）
    boolean isTrashEnabled();        // 是否启用回收站

    // 审计信息
    String getCreatedBy();
    LocalDateTime getCreatedAt();
    String getUpdatedBy();
    LocalDateTime getUpdatedAt();
}
```

**设计要点**：

1. **拥有者与管理员**
   - `owner`：文档库的创建者，拥有最高权限（所有权限）
   - `adminIds`：管理员列表，拥有管理权限但不能删除文档库
   - 拥有者和管理员自动拥有所有权限

2. **配额限制**
   - `maxSizeLimit`：限制文档库的总存储空间
   - `maxFileCountLimit`：限制文档库的文件数量
   - 在上传文档时检查配额

3. **状态管理**
   - `isDeleted`：软删除标识，删除后可恢复
   - `isDisabled`：禁用标识，禁用后用户无法访问
   - `isLocked`：锁定标识，锁定后只读
   - `isTrashEnabled`：是否启用回收站功能

4. **权限存储**
   - 文档库也实现了权限条目列表（`getPermissions()`）
   - 文档库的权限会继承到所有子节点

**聚合根定位**：`DocumentLibrary` 是文档库的聚合根：文档库下的节点（目录与文档）以及权限条目都归属于它，文档库级权限会继承到所有子节点。

---

## 3. 权限模型设计

### 3.1 权限类型

```java
public enum Permission {
    // --- 作用于文件和目录 (BOTH) ---
    RENAME_MOVE("rename_move", "重命名/移动", PermissionScope.BOTH),
    DELETE_DIR("delete_dir", "删除目录", PermissionScope.DIRECTORY),
    DELETE_FILE("delete_file", "删除文件", PermissionScope.FILE),
    MANAGE("manage", "管理", PermissionScope.BOTH),
    DOWNLOAD("download", "下载", PermissionScope.BOTH),
    VIEW("view", "查看", PermissionScope.BOTH),

    // --- 作用于目录 (DIRECTORY) ---
    CREATE_FILE("create_file", "创建文件", PermissionScope.DIRECTORY),
    CREATE_DIR("create_dir", "创建目录", PermissionScope.DIRECTORY),
    NAVIGATE("navigate", "导航", PermissionScope.DIRECTORY);

    private final String code;
    private final String displayName;
    private final PermissionScope scope;

    // 辅助枚举：定义权限可以作用于的对象类型
    public enum PermissionScope {
        FILE,       // 只能用于 DocumentNode（文件）
        DIRECTORY,  // 只能用于 DirectoryNode（目录）
        BOTH;       // 既可用于 DocumentNode，也可用于 DirectoryNode
    }
}
```

**权限说明**：

| 权限 | 代码 | 作用域 | 说明              | 典型场景                     |
|------|------|--------|-----------------|--------------------------|
| VIEW | view | BOTH | 查看文档内容或浏览目录     | 企业文档防泄密：允许员工查看但禁止下载      |
| DOWNLOAD | download | BOTH | 下载文档或目录下的文件     | 需要离线查看或编辑文档              |
| CREATE_FILE | create_file | DIRECTORY | 在目录中创建/上传文件     | 内容贡献者：可以上传文件但不能修改目录结构    |
| CREATE_DIR | create_dir | DIRECTORY | 在目录中创建子目录       | 项目管理者：可以创建子目录组织文档        |
| RENAME_MOVE | rename_move | BOTH | 重命名或移动文件/目录     | 内容管理者：可以重组文档结构           |
| DELETE_FILE | delete_file | FILE | 删除文件            | 内容审核者：可以删除不合规文件          |
| DELETE_DIR | delete_dir | DIRECTORY | 删除目录            | 项目管理者：可以删除整个项目目录         |
| NAVIGATE | navigate | DIRECTORY | 导航可看到目录（不含查看内容） | 允许用户感知到该目录但不能浏览目录下无权限的内容 |
| MANAGE | manage | BOTH | 管理权限（设置ACL等）    | 文档库管理员：可以管理文档权限          |

**设计亮点**：

1. **作用域区分（PermissionScope）**
   - 明确权限适用范围（文件/目录/两者）
   - 编译期类型安全：防止将目录专属权限应用于文件
   - 语义清晰：避免权限误用

2. **预览与下载分离**
   - 这是企业文档管理的核心需求
   - 允许用户在线查看文档，但禁止下载到本地
   - 防止敏感文档泄露

3. **创建权限细分**
   - 区分创建文件（CREATE_FILE）和创建目录（CREATE_DIR）
   - 提供更精细的权限控制
   - 例如：允许普通用户上传文件，但只有管理员能创建目录

4. **删除权限细分**
   - 区分删除文件（DELETE_FILE）和删除目录（DELETE_DIR）
   - 防止误删整个目录
   - 例如：允许用户删除自己的文件，但不能删除公共目录

5. **导航权限独立**
   - 允许浏览目录结构但不查看内容
   - 适用于需要了解文档组织结构的场景
   - 例如：项目经理可以看到文档分类，但不能查看具体内容

6. **权限正交性**
   - 各权限相互独立，可以灵活组合
   - 例如：可以授予 VIEW + CREATE_FILE（只读 + 上传）权限

### 3.2 权限集合（PermissionSet）

```java
/**
 * 权限集合，使用 EnumSet 高效存储
 */
public interface PermissionSet {
    // 查询方法
    boolean has(Permission permission);
    boolean hasAll(Permission... permissions);
    boolean hasAny(Permission... permissions);

    // 修改方法（返回新对象，保持不可变性）
    PermissionSet add(Permission permission);
    PermissionSet remove(Permission permission);
    PermissionSet merge(PermissionSet other);  // 合并（并集）

    // 序列化
    String toPermissionString();  // 转为字符串："view,download,upload"

    // 工厂方法
    static PermissionSet empty();
    static PermissionSet of(Permission... permissions);
    static PermissionSet fromString(String permissionString);
}
```

**设计特点**：

1. **不可变性**
   - 所有修改操作（add、remove、merge）返回新对象
   - 线程安全
   - 避免意外修改

2. **高性能**
   - 内部使用 `EnumSet` 实现（基于位运算）
   - 空间占用小，操作效率高

3. **语义化**
   - 提供工厂方法快速创建常用权限组合
   - 例如：`PermissionSet.of(VIEW, DOWNLOAD)`

### 3.3 权限条目（AclEntry）

```java
/**
 * 权限条目，表示"谁（主体）对什么（资源）拥有哪些权限"
 */
public interface AclEntry {
    String getId();                     // 权限条目ID
    String getResourceId();             // 资源ID（文档库、目录、文档）
    ResourceType getResourceType();     // 资源类型
    String getPrincipal();              // 主体标识（用户ID、用户组ID等）
    String getPrincipalType();          // 主体类型（USER、USER_GROUP、ROLE、DEPARTMENT）
    PermissionSet getPermissions();     // 权限集合
}
```

**ACL（Access Control List）模型**：

```
权限条目 = (资源, 主体, 权限集合)

示例：
- 资源: DocumentLibrary "产品文档库" (id=lib-001)
- 主体: User "张三" (id=user-123)
- 权限: VIEW, DOWNLOAD

解释：用户"张三"对"产品文档库"拥有预览和下载权限
```

**设计优势**：

1. **灵活的主体类型**
   - 支持用户、用户组、角色、部门等多种主体类型
   - 通过 `PrincipalTypeProvider` 扩展自定义主体类型

2. **资源类型区分**
   - `ResourceType` 枚举：LIBRARY、DIRECTORY、DOCUMENT
   - 不同资源类型可以有不同的权限管理策略

3. **独立的权限条目**
   - 每个权限条目是独立的数据对象
   - 便于审计、查询和管理

### 3.4 主体类型（PrincipalType）

**核心设计**：`PrincipalType` 采用 **SPI 扩展机制**，支持动态注册和扩展主体类型。该设计通过 `PrincipalTypeProvider` 接口配合 Java SPI 机制，实现主体类型的自动发现和加载。

#### 3.4.1 PrincipalType 接口

```java
/**
 * 抽象的权限控制主体类型接口
 *
 * <p>定义权限控制中的主体类型（如用户、用户组、角色、部门等），
 * 每个主体类型都关联一个 {@link PrincipalResolver} 用于解析主体为用户列表。
 *
 * <p>实现示例：
 * <pre>
 * public class UserPrincipalType implements PrincipalType {
 *     {@literal @}Override
 *     public String getName() {
 *         return "USER";
 *     }
 *
 *     {@literal @}Override
 *     public String getDisplayName() {
 *         return "用户";
 *     }
 *
 *     {@literal @}Override
 *     public PrincipalResolver getResolver() {
 *         return new UserPrincipalResolver();
 *     }
 *
 *     {@literal @}Override
 *     public Optional&lt;SchemaNode&gt; getConfigUISchema() {
 *         return Optional.empty(); // 无需配置UI
 *     }
 * }
 * </pre>
 */
public interface PrincipalType {

  /**
   * 获取主体类型名称（唯一标识）
   *
   * @return 主体类型名称，如 "USER"、"GROUP"、"ROLE"
   */
  String getName();

  /**
   * 获取主体类型的显示名称
   *
   * @return 显示名称，如 "用户"、"用户组"、"角色"
   */
  String getDisplayName();

  /**
   * 获取该主体类型对应的解析器
   *
   * @return Principal 解析器实例
   */
  PrincipalResolver getResolver();

  /**
   * 获取该主体类型的配置用户界面架构
   *
   * <p>返回用于前端配置该主体类型的 UI Schema 定义。
   * 如果主体类型不需要额外的配置界面，返回 {@link Optional#empty()}。
   *
   * <p>使用场景示例：
   * <ul>
   *   <li>用户组类型可能需要配置"选择用户组"的下拉框或树形选择器</li>
   *   <li>角色类型可能需要配置"选择角色"的多选框</li>
   *   <li>用户类型通常不需要额外配置，直接输入用户 ID 即可</li>
   * </ul>
   *
   * @return UI Schema 配置节点，如果不需要配置界面则返回 empty
   */
  Optional<SchemaNode> getConfigUISchema();
}
```

#### 3.4.2 PrincipalTypeProvider（SPI 提供者）

```java
/**
 * 权限控制主体类型提供者接口
 *
 * <p>使用 Java SPI 机制动态发现和加载主体类型定义。
 *
 * <p>SPI 注册方式：在 {@code META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider}
 * 文件中注册实现类：
 * <pre>
 * com.ouroboros.example.CustomPrincipalTypeProvider
 * </pre>
 *
 * <p>实现示例：
 * <pre>
 * public class UserPrincipalTypeProvider implements PrincipalTypeProvider {
 *     {@literal @}Override
 *     public List&lt;PrincipalType&gt; getPrincipalTypes() {
 *         return Arrays.asList(
 *             new UserPrincipalType(),
 *             new UserGroupPrincipalType(),
 *             new RolePrincipalType()
 *         );
 *     }
 * }
 * </pre>
 */
public interface PrincipalTypeProvider {

  /**
   * 获取该提供者提供的所有主体类型
   *
   * @return 主体类型列表
   */
  List<PrincipalType> getPrincipalTypes();

  /**
   * 根据类型名称获取主体类型
   *
   * @param name 主体类型名称
   * @return 主体类型，如果不存在则返回 empty
   */
  static Optional<PrincipalType> get(String name) {
    return PrincipalTypeRegistry.getInstance().get(name);
  }

  /**
   * 获取所有已注册的主体类型
   *
   * @return 所有主体类型列表
   */
  static List<PrincipalType> getAll() {
    return PrincipalTypeRegistry.getInstance().getAll();
  }

  /**
   * 主体类型注册表（单例）
   *
   * <p>通过 SPI 自动加载所有 {@link PrincipalTypeProvider} 实现，
   * 并缓存所有提供的 {@link PrincipalType}。
   */
  class PrincipalTypeRegistry {

    private static final PrincipalTypeRegistry INSTANCE = new PrincipalTypeRegistry();

    private final Map<String, PrincipalType> registry = new ConcurrentHashMap<>();

    private PrincipalTypeRegistry() {
      loadPrincipalTypes();
    }

    /**
     * 通过 SPI 加载所有主体类型
     *
     * <p>加载顺序：
     * <ol>
     *   <li>加载所有通过 PrincipalTypeProvider 提供的主体类型</li>
     *   <li>加载所有直接注册的 PrincipalType 实现</li>
     * </ol>
     */
    private void loadPrincipalTypes() {
      // 1. 加载所有 PrincipalTypeProvider 提供的类型
      ServiceLoader<PrincipalTypeProvider> providerLoader =
          ServiceLoader.load(PrincipalTypeProvider.class);

      for (PrincipalTypeProvider provider : providerLoader) {
        List<PrincipalType> types = provider.getPrincipalTypes();
        if (types != null) {
          for (PrincipalType type : types) {
            if (type != null && type.getName() != null) {
              registry.put(type.getName(), type);
            }
          }
        }
      }

      // 2. 加载所有直接注册的 PrincipalType
      ServiceLoader<PrincipalType> typeLoader =
          ServiceLoader.load(PrincipalType.class);

      for (PrincipalType type : typeLoader) {
        if (type != null && type.getName() != null) {
          registry.put(type.getName(), type);
        }
      }
    }

    public static PrincipalTypeRegistry getInstance() {
      return INSTANCE;
    }

    /**
     * 根据名称获取主体类型
     *
     * @param name 主体类型名称
     * @return 主体类型，如果不存在则返回 empty
     */
    public Optional<PrincipalType> get(String name) {
      if (name == null || name.isEmpty()) {
        return Optional.empty();
      }
      return Optional.ofNullable(registry.get(name));
    }

    /**
     * 获取所有已注册的主体类型
     *
     * @return 所有主体类型列表
     */
    public List<PrincipalType> getAll() {
      return new ArrayList<>(registry.values());
    }

    /**
     * 检查指定类型是否已注册
     *
     * @param name 主体类型名称
     * @return 如果已注册返回 true
     */
    public boolean contains(String name) {
      return name != null && registry.containsKey(name);
    }

    /**
     * 获取已注册的主体类型数量
     *
     * @return 类型数量
     */
    public int size() {
      return registry.size();
    }
  }
}
```

**SPI 注册方式**：

在 `META-INF/services/com.ouroboros.documentmanagement.model.acl.PrincipalTypeProvider` 文件中注册：

```
com.ouroboros.example.CustomPrincipalTypeProvider
```

#### 3.4.3 PrincipalResolver（主体解析器）

```java
/**
 * Principal 解析器接口，用于将 Principal 解析为用户ID列表
 */
public interface PrincipalResolver {
    /**
     * 将 principal 扩展为用户ID列表
     * 例如：将用户组ID扩展为该组下的所有用户ID
     *
     * @param principal 主体标识（如用户组ID、角色ID、部门ID）
     * @return 用户ID列表
     */
    List<String> expandToUserIds(String principal);

    /**
     * 判断指定用户是否属于该 principal
     *
     * @param principal 主体标识
     * @param userId 用户ID
     * @return 是否包含该用户
     */
    boolean containsUser(String principal, String userId);
}
```

#### 3.4.4 内置主体类型示例

**用户类型（USER）**：

```java
public class UserPrincipalType implements PrincipalType {
    @Override
    public String getName() {
        return "USER";
    }

    @Override
    public String getDisplayName() {
        return "用户";
    }

    @Override
    public PrincipalResolver getResolver() {
        return new PrincipalResolver() {
            @Override
            public List<String> expandToUserIds(String principal) {
                // 用户直接返回自己
                return List.of(principal);
            }

            @Override
            public boolean containsUser(String principal, String userId) {
                // 判断是否是同一个用户
                return principal.equals(userId);
            }
        };
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        return Optional.empty(); // 无需配置UI
    }
}
```

**用户组类型（USER_GROUP）**：

```java
public class UserGroupPrincipalType implements PrincipalType {
    @Autowired
    private UserGroupService userGroupService;

    @Override
    public String getName() {
        return "USER_GROUP";
    }

    @Override
    public String getDisplayName() {
        return "用户组";
    }

    @Override
    public PrincipalResolver getResolver() {
        return new PrincipalResolver() {
            @Override
            public List<String> expandToUserIds(String principal) {
                // 查询用户组下的所有用户
                return userGroupService.getUserIdsInGroup(principal);
            }

            @Override
            public boolean containsUser(String principal, String userId) {
                // 判断用户是否在该用户组中
                return userGroupService.isUserInGroup(userId, principal);
            }
        };
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        // 返回配置UI Schema
        return Optional.of(SchemaNode.builder()
            .type("select")
            .name("principal")
            .label("用户组")
            .options(List.of(
                SchemaNode.Option.builder().value("group1").label("项目组A").build(),
                SchemaNode.Option.builder().value("group2").label("项目组B").build()
            ))
            .build());
    }
}
```

#### 3.4.5 权限判断逻辑

```java
/**
 * 判断用户是否与权限条目相关
 */
boolean isRelevantToUser(AclEntry entry, String userId) {
    // 1. 获取权限条目的主体类型
    Optional<PrincipalType> principalType =
        PrincipalTypeProvider.get(entry.getPrincipalType());

    if (principalType.isEmpty()) {
        return false;
    }

    // 2. 使用该主体类型的解析器判断
    PrincipalResolver resolver = principalType.get().getResolver();
    return resolver.containsUser(entry.getPrincipal(), userId);
}
```

#### 3.4.6 设计优势

1. **高度可扩展**
   - 通过 SPI 机制动态注册主体类型
   - 无需修改核心代码即可扩展新的主体类型
   - 支持第三方模块注册自定义主体类型

2. **解析器分离**
   - 每个主体类型有自己的解析器
   - 解析逻辑与主体类型绑定
   - 便于实现复杂的解析规则

3. **统一接口**
   - 所有主体类型使用统一的 `PrincipalResolver` 接口
   - 权限判断逻辑无需区分主体类型
   - 简化权限计算算法

4. **UI 集成**
   - 主体类型可以提供配置 UI Schema
   - 支持不同主体类型的差异化配置界面
   - 便于前端表单构建

#### 3.4.7 扩展示例

**扩展：项目组类型**：

```java
public class ProjectGroupPrincipalType implements PrincipalType {
    @Autowired
    private ProjectService projectService;

    @Override
    public String getName() {
        return "PROJECT_GROUP";
    }

    @Override
    public String getDisplayName() {
        return "项目组";
    }

    @Override
    public PrincipalResolver getResolver() {
        return new PrincipalResolver() {
            @Override
            public List<String> expandToUserIds(String principal) {
                // 查询项目组下的所有成员
                return projectService.getProjectMembers(principal);
            }

            @Override
            public boolean containsUser(String principal, String userId) {
                // 判断用户是否是项目成员
                return projectService.isProjectMember(principal, userId);
            }
        };
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        return Optional.of(SchemaNode.builder()
            .type("tree-select")
            .name("principal")
            .label("项目组")
            .api("/api/projects/list")
            .build());
    }
}
```

**扩展：职级类型**：

```java
public class JobLevelPrincipalType implements PrincipalType {
    @Autowired
    private HRService hrService;

    @Override
    public String getName() {
        return "JOB_LEVEL";
    }

    @Override
    public String getDisplayName() {
        return "职级";
    }

    @Override
    public PrincipalResolver getResolver() {
        return new PrincipalResolver() {
            @Override
            public List<String> expandToUserIds(String principal) {
                // 查询指定职级的所有员工
                return hrService.getUsersByJobLevel(principal);
            }

            @Override
            public boolean containsUser(String principal, String userId) {
                // 判断用户是否是该职级
                return hrService.getUserJobLevel(userId).equals(principal);
            }
        };
    }

    @Override
    public Optional<SchemaNode> getConfigUISchema() {
        return Optional.of(SchemaNode.builder()
            .type("radio-group")
            .name("principal")
            .label("职级")
            .options(List.of(
                SchemaNode.Option.builder().value("senior").label("高级工程师").build(),
                SchemaNode.Option.builder().value("junior").label("初级工程师").build()
            ))
            .build());
    }
}
```

**设计优势**：

1. **高度可扩展**
   - 通过 SPI 机制动态注册主体类型
   - 无需修改核心代码即可扩展新的主体类型
   - 支持第三方模块注册自定义主体类型

2. **解析器分离**
   - 每个主体类型有自己的解析器
   - 解析逻辑与主体类型绑定
   - 便于实现复杂的解析规则

3. **统一接口**
   - 所有主体类型使用统一的 `PrincipalResolver` 接口
   - 权限判断逻辑无需区分主体类型
   - 简化权限计算算法

### 3.5 权限继承机制

**核心原则**：子节点继承父节点的权限，并可叠加自身权限

#### 3.5.1 继承层级

```
DocumentLibrary (文档库)
  │ 权限: 用户A → VIEW
  │
  ├─ DirectoryNode (根目录)
  │   │ 权限: (继承自文档库)
  │   │
  │   ├─ DirectoryNode (目录A)
  │   │   │ 权限: 用户A → CREATE_FILE (自身)
  │   │   │ 有效权限: 用户A → VIEW + CREATE_FILE (继承 + 自身)
  │   │   │
  │   │   ├─ DirectoryNode (目录A1)
  │   │   │   │ 权限: 用户B → DOWNLOAD (自身)
  │   │   │   │ 有效权限:
  │   │   │   │   - 用户A → VIEW + CREATE_FILE (继承)
  │   │   │   │   - 用户B → DOWNLOAD (自身)
  │   │   │   │
  │   │   │   └─ DocumentNode (文档.pdf)
  │   │   │       │ 权限: 用户C → MANAGE (自身)
  │   │   │       │ 有效权限:
  │   │   │       │   - 用户A → VIEW (继承，CREATE_FILE 不适用于文件)
  │   │   │       │   - 用户B → DOWNLOAD (继承)
  │   │   │       │   - 用户C → MANAGE (自身)
```

#### 3.5.2 权限计算算法

**实现位置**：通过 `ACLService` 接口实现

权限计算通过 `ACLService` 接口提供，支持多种实现方式。接口定义如下：

```java
public interface ACLService {
    /**
     * 获取用户对资源的有效权限（考虑继承）
     */
    PermissionSet getEffectivePermissions(String resourceId,
                                         ResourceType resourceType,
                                         String userId);

    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(String resourceId, ResourceType resourceType,
                         String userId, Permission permission);

    /**
     * 检查用户是否有任一指定权限
     */
    boolean hasAnyPermission(String resourceId, ResourceType resourceType,
                            String userId, Permission... permissions);

    /**
     * 检查用户是否有所有指定权限
     */
    boolean hasAllPermissions(String resourceId, ResourceType resourceType,
                             String userId, Permission... permissions);
}
```

**算法特点**：

1. **一次遍历**
   - 时间复杂度 O(n)，n 为路径长度
   - 不需要递归查询数据库

2. **权限累积**
   - 使用 `merge()` 操作合并权限（并集）
   - 多个授权源的权限会叠加，不会互相覆盖

3. **主体类型无关**
   - 通过 `PrincipalResolver` 统一处理所有主体类型
   - 扩展主体类型无需修改算法
   - SPI 机制自动发现新注册的主体类型

#### 3.5.3 权限继承示例

**场景 1：单用户多层级**

```
/产品文档库 (Library)
  权限: 用户A → VIEW

/产品文档库/需求文档 (Directory)
  权限: 用户A → CREATE_FILE

/产品文档库/需求文档/需求1.pdf (Document)
  权限: (无)

结果：
用户A对"需求1.pdf"的有效权限 = VIEW（CREATE_FILE 不适用于文件）
```

**场景 2：多用户多主体**

```
/产品文档库 (Library)
  权限:
    - 用户组"产品部" → VIEW
    - 角色"产品经理" → CREATE_FILE

/产品文档库/PRD (Directory)
  权限:
    - 用户"张三" → DOWNLOAD

/产品文档库/PRD/产品需求.pdf (Document)
  权限:
    - 角色"产品总监" → MANAGE

用户"张三"的主体:
  - user-123 (用户自己)
  - group-product (用户组"产品部")
  - role-pm (角色"产品经理")

结果：
用户"张三"对"产品需求.pdf"的有效权限 = VIEW + DOWNLOAD
  - VIEW: 继承自文档库（通过用户组"产品部"）
  - DOWNLOAD: 继承自目录（用户自己）
  - CREATE_FILE: 不适用（文件不支持此权限）
```

---

## 4. 服务层设计

### 4.1 服务职责分离

文档管理模块将服务层分为四个独立的服务接口，每个服务有明确的职责边界：

```
DocumentLibraryService   ← 文档库级别的 CRUD
LibraryContentService    ← 文档库内容的原子化操作
ACLService               ← 权限计算和检查（只读）
PermissionAdminService   ← 权限管理（读写）
```

**设计原则**：

1. **单一职责**：每个服务只负责一个业务领域
2. **CQRS 分离**：ACLService（查询）与 PermissionAdminService（命令）分离
3. **原子化操作**：每个服务方法都是独立的、完整的业务操作
4. **接口驱动**：Core 包定义接口，实现包提供具体实现

### 4.2 DocumentLibraryService（文档库服务）

**职责**：文档库的生命周期管理，包括创建、查询、更新、删除、管理员管理、统计等完整功能

**核心方法**：

```java
public interface DocumentLibraryService {

  // ==================== 创建和查询 ====================

  DocumentLibrary createLibrary(DocumentLibrary documentLibrary);
  DocumentLibrary createLibrary(DocumentLibrary documentLibrary, boolean createRootDirectory);

  Optional<DocumentLibrary> getLibraryById(String libraryId);
  Optional<DocumentLibrary> getLibraryByName(String libraryName);

  List<DocumentLibrary> listLibraries();
  List<DocumentLibrary> listLibraries(boolean includeDeleted);
  List<DocumentLibrary> listLibrariesByOwner(String userId);
  List<DocumentLibrary> listLibrariesByAdmin(String userId);
  List<DocumentLibrary> listAccessibleLibraries(String userId);

  // ==================== 更新操作 ====================

  DocumentLibrary updateLibrary(DocumentLibrary documentLibrary, String updatedBy);
  DocumentLibrary updateQuota(String libraryId, Long maxSizeLimit, Long maxFileCountLimit, String updatedBy);

  DocumentLibrary enableLibrary(String libraryId, String updatedBy);
  DocumentLibrary disableLibrary(String libraryId, String updatedBy);
  DocumentLibrary lockLibrary(String libraryId, String updatedBy);
  DocumentLibrary unlockLibrary(String libraryId, String updatedBy);
  DocumentLibrary enableTrash(String libraryId, String updatedBy);
  DocumentLibrary disableTrash(String libraryId, String updatedBy);

  // ==================== 管理员管理 ====================

  DocumentLibrary addAdmin(String libraryId, String adminUserId, String updatedBy);
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String updatedBy);
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String updatedBy);
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String updatedBy);

  boolean isAdmin(String libraryId, String userId);
  boolean isOwner(String libraryId, String userId);

  // ==================== 删除操作 ====================

  DocumentLibrary softDeleteLibrary(String libraryId, String deletedBy);
  DocumentLibrary restoreLibrary(String libraryId, String restoredBy);
  void hardDeleteLibrary(String libraryId);

  // ==================== 统计信息 ====================

  LibraryStatistics getStatistics(String libraryId);
  boolean isQuotaExceeded(String libraryId);
  boolean canUploadFile(String libraryId, long fileSize);
  boolean isNameAvailable(String name);
  boolean isNameAvailable(String name, String excludeId);

  interface LibraryStatistics {
    String getLibraryId();
    long getDocumentCount();
    long getDirectoryCount();
    long getUsedSpace();
    Long getMaxSizeLimit();
    Long getMaxFileCountLimit();
    double getSpaceUsageRate();
    double getFileCountUsageRate();
    boolean isSpaceExceeded();
    boolean isFileCountExceeded();
  }
}
```

**核心功能分类**：

#### 4.2.1 创建和查询（9个方法）

1. **创建文档库**
   - `createLibrary(documentLibrary)`：创建文档库
   - `createLibrary(documentLibrary, createRootDirectory)`：创建并可选自动创建根目录
   - 业务规则：
     - 创建者自动成为拥有者（owner）
     - 文档库名称必须唯一
     - 自动生成ID和时间戳

2. **查询文档库**
   - `getLibraryById`：根据ID查询
   - `getLibraryByName`：根据名称查询
   - `listLibraries`：列出所有文档库
   - `listLibraries(includeDeleted)`：支持包含已删除的文档库

3. **按角色查询**
   - `listLibrariesByOwner`：查询用户拥有的文档库
   - `listLibrariesByAdmin`：查询用户管理的文档库
   - `listAccessibleLibraries`：查询用户可访问的所有文档库（owner + admin + 有权限）

#### 4.2.2 更新操作（9个方法）

1. **基本信息更新**
   - `updateLibrary`：更新文档库基本信息
   - 可更新：名称、描述、配额限制、状态开关
   - 不可更改：拥有者、创建时间、创建人

2. **配额管理**
   - `updateQuota`：更新存储空间和文件数量限制

3. **状态管理**
   - `enableLibrary / disableLibrary`：启用/禁用文档库
   - `lockLibrary / unlockLibrary`：锁定/解锁文档库（只读模式）
   - `enableTrash / disableTrash`：启用/禁用回收站功能

#### 4.2.3 管理员管理（6个方法）

- `addAdmin / removeAdmin`：单个管理员的添加/移除
- `addAdmins / removeAdmins`：批量管理员操作
- `isAdmin / isOwner`：权限检查

#### 4.2.4 删除操作（3个方法）

1. **软删除**
   - `softDeleteLibrary`：标记为已删除，数据保留
   - `restoreLibrary`：恢复已删除的文档库

2. **硬删除**
   - `hardDeleteLibrary`：物理删除，不可恢复
   - 级联删除所有关联数据（节点、文件、权限）

#### 4.2.5 统计信息（5个方法 + 内部接口）

- `getStatistics`：获取文档库统计信息
- `isQuotaExceeded`：检查配额是否超限
- `canUploadFile`：检查是否可上传指定大小的文件
- `isNameAvailable`：检查名称是否可用
- `LibraryStatistics`：统计信息接口（10个getter方法）

**设计要点**：

1. **原子化操作**
   - 每个方法都是独立的、完整的业务操作
   - 不涉及权限检查（由 ACLService 负责）
   - 不涉及文件操作（由 LibraryContentService 和 StorageService 负责）

2. **清晰的职责边界**
   - 只管理文档库级别的元数据
   - 不直接操作目录和文档（通过 LibraryContentService）
   - 不直接操作权限（通过 PermissionAdminService）

3. **完整的生命周期**
   - 创建 → 查询 → 更新 → 启用/禁用 → 锁定/解锁 → 软删除 → 恢复 → 硬删除

4. **丰富的查询维度**
   - 按ID、名称查询
   - 按角色查询（owner、admin、accessible）
   - 支持软删除过滤

5. **统计和验证**
   - 实时统计信息（文档数、空间使用）
   - 配额检查（上传前验证）
   - 名称唯一性验证

**方法总览**：共 **32个方法**，覆盖文档库管理的所有核心场景

### 4.3 LibraryContentService（内容服务）

**职责**：文档库内容（目录和文档）的原子化 CRUD 操作

```java
public interface LibraryContentService {
    // ========== 目录操作 ==========

    /**
     * 创建目录
     * @param libraryId 文档库ID
     * @param parentId 父目录ID（如果为null，则在根目录下创建）
     * @param name 目录名称
     * @param createdBy 创建人ID
     * @return 创建的目录节点
     */
    DirectoryNode createDirectory(String libraryId, String parentId,
                                  String name, String createdBy);

    /**
     * 获取目录信息
     */
    Optional<DirectoryNode> getDirectory(String libraryId, String directoryId);

    /**
     * 列出目录下的所有子目录
     */
    List<DirectoryNode> listDirectories(String libraryId, String directoryId);

    /**
     * 重命名目录
     */
    void renameDirectory(String libraryId, String directoryId,
                        String newName, String renamedBy);

    /**
     * 移动目录
     * @param directoryId 要移动的目录ID
     * @param targetDirectoryId 目标父目录ID
     */
    void moveDirectory(String libraryId, String directoryId,
                      String targetDirectoryId, String movedBy);

    /**
     * 删除目录（递归删除子目录和文档）
     */
    void deleteDirectory(String libraryId, String directoryId, String deletedBy);

    /**
     * 获取目录的完整路径
     * @return 路径列表（从根目录到当前目录）
     */
    List<DirectoryNode> getDirectoryPath(String libraryId, String directoryId);

    // ========== 文档操作 ==========

    /**
     * 上传文档
     * @param libraryId 文档库ID
     * @param directoryId 目录ID
     * @param name 文档名称
     * @param inputStream 文件输入流
     * @param size 文件大小
     * @param mimeType MIME类型
     * @param uploadedBy 上传人ID
     * @return 上传的文档节点
     */
    DocumentNode uploadDocument(String libraryId, String directoryId, String name,
                               InputStream inputStream, Long size, String mimeType,
                               String uploadedBy);

    /**
     * 获取文档信息
     */
    Optional<DocumentNode> getDocument(String libraryId, String documentId);

    /**
     * 获取文档内容
     * @return 文件输入流
     */
    Optional<InputStream> getDocumentContent(String libraryId, String documentId);

    /**
     * 列出目录下的所有文档
     */
    List<DocumentNode> listDocuments(String libraryId, String directoryId);

    /**
     * 重命名文档
     */
    void renameDocument(String libraryId, String documentId,
                       String newName, String renamedBy);

    /**
     * 移动文档
     */
    void moveDocument(String libraryId, String documentId,
                     String targetDirectoryId, String movedBy);

    /**
     * 删除文档
     */
    void deleteDocument(String libraryId, String documentId, String deletedBy);
}
```

**设计要点**：

1. **原子化操作**
   - 每个方法都是独立的、完整的业务操作
   - 不需要调用方组合多个方法完成一个业务

2. **统一管理目录和文档**
   - 避免过度分层（不分为 DirectoryService 和 DocumentService）
   - 目录和文档操作放在同一个服务中，便于管理

3. **内部协调存储**
   - `uploadDocument` 内部调用 `StorageService.store()` 存储文件
   - `getDocumentContent` 内部调用 `StorageService.retrieve()` 获取文件
   - 调用方不需要直接操作 `StorageService`

4. **路径计算**
   - `getDirectoryPath` 返回从根到当前节点的完整路径
   - 用于面包屑导航和权限计算

**文档上传流程**：

```
1. uploadDocument(libraryId, directoryId, name, inputStream, size, mimeType, uploadedBy)
   ↓
2. 检查文件名是否重复
   ↓
3. 检查文档库配额（存储空间、文件数量）
   ↓
4. 调用 StorageService.store(inputStream, size, mimeType)
   ↓ 返回 StorageResult{storagePath, size, checksum}
5. 创建 DocumentNode 元数据
   - 设置 storagePath（关联到实际文件）
   - 设置 size, mimeType, name 等
   ↓
6. 保存元数据到 LibraryNodeRepository
   ↓
7. 返回 DocumentNode
```

### 4.4 ACLService（权限检查服务）

**职责**：权限计算和检查（只读操作）

```java
public interface ACLService {
    /**
     * 获取用户对资源的有效权限（考虑继承）
     *
     * @param resourceId 资源ID
     * @param resourceType 资源类型
     * @param userId 用户ID
     * @return 有效权限集合
     */
    PermissionSet getEffectivePermissions(String resourceId,
                                         ResourceType resourceType,
                                         String userId);

    /**
     * 检查用户是否有指定权限
     */
    boolean hasPermission(String resourceId, ResourceType resourceType,
                         String userId, Permission permission);

    /**
     * 检查用户是否有任一指定权限
     */
    boolean hasAnyPermission(String resourceId, ResourceType resourceType,
                            String userId, Permission... permissions);

    /**
     * 检查用户是否有所有指定权限
     */
    boolean hasAllPermissions(String resourceId, ResourceType resourceType,
                             String userId, Permission... permissions);
}
```

**实现要点**：

```java
@Service
public class ACLServiceImpl implements ACLService {

    @Autowired
    private LibraryNodeRepository nodeRepository;

    @Autowired
    private LibraryRepository libraryRepository;

    @Override
    public PermissionSet getEffectivePermissions(
        String resourceId,
        ResourceType resourceType,
        String userId
    ) {
        // 1. 获取节点路径（从文档库到当前节点）
        //    路径包括：DocumentLibrary + 所有父节点 + 当前节点
        List<LibraryNode> nodePath = getNodePath(resourceId, resourceType);

        // 2. 通过 ACLService 实现计算有效权限
        //    ACLService 实现会通过 PrincipalTypeProvider 获取
        //    每个权限条目对应的 PrincipalResolver 来判断用户是否匹配
        //    实现权限继承和叠加逻辑
    }

    @Override
    public boolean hasPermission(
        String resourceId,
        ResourceType resourceType,
        String userId,
        Permission permission
    ) {
        PermissionSet effectivePermissions = getEffectivePermissions(
            resourceId, resourceType, userId
        );
        return effectivePermissions.has(permission);
    }

    /**
     * 获取节点路径（从文档库到当前节点）
     */
    private List<LibraryNode> getNodePath(String resourceId, ResourceType resourceType) {
        List<LibraryNode> path = new ArrayList<>();

        if (resourceType == ResourceType.LIBRARY) {
            // 文档库：只包含文档库本身
            libraryRepository.findById(resourceId).ifPresent(path::add);
        } else {
            // 目录或文档：包含文档库 + 所有父节点 + 当前节点
            LibraryNode current = nodeRepository.findById(resourceId).orElse(null);
            while (current != null) {
                path.add(0, current); // 添加到列表头部
                if (current.getParentId() == null) {
                    // 到达根节点，添加文档库
                    libraryRepository.findById(current.getLibraryId())
                        .ifPresent(lib -> path.add(0, lib));
                    break;
                }
                current = nodeRepository.findById(current.getParentId()).orElse(null);
            }
        }

        return path;
    }

    // 其他方法类似...
}
```

**设计亮点**：

1. **只读服务**
   - 只提供查询方法，不修改权限
   - 符合 CQRS 原则

2. **权限计算透明**
   - 自动处理权限继承
   - 调用方不需要关心继承逻辑

3. **可缓存**
   - 查询方法可以添加缓存
   - 提高权限检查性能

### 4.5 PermissionAdminService（权限管理服务）

**职责**：权限的 CRUD 操作（读写操作）

```java
public interface PermissionAdminService {
    /**
     * 为资源添加或更新权限条目
     */
    AclEntry setPermission(String resourceId, ResourceType resourceType,
                          String principalId, String principalType,
                          PermissionSet permissions);

    /**
     * 移除权限条目（通过权限条目ID）
     */
    void removePermission(String permissionEntryId);

    /**
     * 移除权限条目（通过资源和主体）
     */
    void removePermission(String resourceId, ResourceType resourceType,
                         String principalId, String principalType);

    /**
     * 更新权限条目
     */
    AclEntry updatePermission(String permissionEntryId, PermissionSet permissions);

    /**
     * 获取权限条目（通过ID）
     */
    Optional<AclEntry> getPermission(String permissionEntryId);

    /**
     * 获取权限条目（通过资源和主体）
     */
    Optional<AclEntry> getPermission(String resourceId, ResourceType resourceType,
                                    String principalId, String principalType);

    /**
     * 获取资源的所有权限条目
     */
    List<AclEntry> getPermissions(String resourceId, ResourceType resourceType);
}
```

**设计要点**：

1. **与 ACLService 分离**
   - ACLService：只读，权限检查
   - PermissionAdminService：读写，权限管理
   - 符合 CQRS 原则

2. **权限变更后清除缓存**
   ```java
   @Override
   @CacheEvict(value = "effectivePermissions", allEntries = true)
   public AclEntry setPermission(...) {
       // 实现
   }
   ```

3. **支持多种查询方式**
   - 按权限条目ID查询
   - 按资源+主体组合查询
   - 查询资源的所有权限条目

### 4.6 业务门面服务（Facade Services）

**职责**：业务门面服务位于服务层之上，协调多个底层服务完成复杂的业务场景，提供面向不同角色和视角的高层业务接口。

**设计原则**：

1. **面向业务场景**：每个门面服务针对特定角色和业务场景设计
2. **职责边界清晰**：从不同角色视角定义职责（用户视角、管理员视角、系统管理员视角）
3. **服务编排**：协调调用多个底层服务（DocumentLibraryService、LibraryContentService、ACLService、PermissionAdminService）
4. **权限集成**：在门面层统一处理权限验证和业务规则
5. **事务管理**：门面方法作为事务边界，保证业务操作的原子性

**三个门面服务**：

```
DocumentLibraryContentFacade      ← 用户视角：档案库内容的使用
DocumentLibraryManagementFacade   ← 管理员视角：档案库的管理
DocumentLibraryAdminFacade        ← 系统管理员视角：档案库的后台管理
```

#### 4.6.1 DocumentLibraryContentFacade（内容管理门面）

**职责**：从档案库使用者的角度提供内容管理功能，包括目录浏览、文件查看、上传下载、移动重命名、权限管理等

**核心方法分类**：

```java
public interface DocumentLibraryContentFacade {

  // ==================== 目录浏览 (7个方法) ====================

  /**
   * 获取根目录
   * 业务规则：验证用户是否有档案库访问权限
   */
  Optional<DirectoryNode> getRootDirectory(String libraryId, String userId);

  /**
   * 浏览目录内容
   * 业务规则：
   * - 验证用户是否有目录的 NAVIGATE 权限
   * - 返回子目录和文档列表
   * - 过滤用户无权限的节点
   */
  List<LibraryNode> browseDirectory(String libraryId, String directoryId, String userId);

  /**
   * 列出子目录
   * 业务规则：只返回用户有 NAVIGATE 权限的子目录
   */
  List<DirectoryNode> listSubdirectories(String libraryId, String directoryId, String userId);

  /**
   * 列出文档
   * 业务规则：只返回用户有 VIEW 权限的文档
   */
  List<DocumentNode> listDocuments(String libraryId, String directoryId, String userId);

  /**
   * 获取节点路径（面包屑导航）
   * 业务规则：从根目录到当前节点的完整路径
   */
  List<LibraryNode> getNodePath(String libraryId, String nodeId, String userId);

  /**
   * 获取目录树（可选深度）
   * 业务规则：递归获取目录结构，只包含用户有权限的目录
   */
  Optional<DirectoryNode> getDirectoryTree(String libraryId, String userId);
  Optional<DirectoryNode> getDirectoryTree(String libraryId, String directoryId,
      int maxDepth, String userId);

  // ==================== 文件查询和搜索 (4个方法) ====================

  /**
   * 获取文档详情
   * 业务规则：验证用户是否有 VIEW 权限
   */
  Optional<DocumentNode> getDocument(String libraryId, String documentId, String userId);

  /**
   * 获取目录详情
   */
  Optional<DirectoryNode> getDirectory(String libraryId, String directoryId, String userId);

  /**
   * 搜索文档（按名称）
   * 业务规则：只返回用户有 VIEW 权限的文档
   */
  List<DocumentNode> searchDocuments(String libraryId, String namePattern, String userId);

  /**
   * 在指定目录下搜索文档
   */
  List<DocumentNode> searchDocumentsInDirectory(
      String libraryId, String directoryId, String namePattern,
      boolean recursive, String userId);

  // ==================== 文件查看和下载 (2个方法) ====================

  /**
   * 预览文档
   * 业务规则：
   * - 验证用户是否有 VIEW 权限
   * - 返回文件流用于在线预览
   */
  Optional<InputStream> previewDocument(String libraryId, String documentId, String userId);

  /**
   * 下载文档
   * 业务规则：
   * - 验证用户是否有 DOWNLOAD 权限
   * - 记录下载日志
   */
  Optional<InputStream> downloadDocument(String libraryId, String documentId, String userId);

  // ==================== 文件上传 (1个方法) ====================

  /**
   * 上传文件
   * 业务规则：
   * - 验证用户是否有目录的 CREATE_FILE 权限
   * - 检查文件名是否重复
   * - 检查档案库配额
   * - 上传文件到存储
   * - 创建文档节点
   */
  DocumentNode uploadFile(String libraryId, String directoryId, String fileName,
      String contentType, long fileSize, InputStream inputStream, String userId);

  // ==================== 目录创建 (1个方法) ====================

  /**
   * 创建目录
   * 业务规则：
   * - 验证用户是否有父目录的 CREATE_DIR 权限
   * - 检查目录名是否重复
   * - 创建目录节点
   */
  DirectoryNode createDirectory(String libraryId, String parentDirectoryId,
      String directoryName, String userId);

  // ==================== 移动和重命名 (4个方法) ====================

  /**
   * 移动文档
   * 业务规则：
   * - 验证用户是否有源文档的 RENAME_MOVE 权限
   * - 验证用户是否有目标目录的 CREATE_FILE 权限
   * - 检查目标位置是否有同名文件
   * - 执行移动操作
   */
  DocumentNode moveDocument(String libraryId, String documentId,
      String targetDirectoryId, String userId);

  /**
   * 移动目录
   * 业务规则：
   * - 验证用户是否有源目录的 RENAME_MOVE 权限
   * - 验证用户是否有目标目录的 CREATE_DIR 权限
   * - 检查是否会产生循环移动
   * - 执行移动操作
   */
  DirectoryNode moveDirectory(String libraryId, String directoryId,
      String targetDirectoryId, String userId);

  /**
   * 重命名文档
   * 业务规则：
   * - 验证用户是否有 RENAME_MOVE 权限
   * - 检查同级目录下是否有重名
   */
  DocumentNode renameDocument(String libraryId, String documentId,
      String newName, String userId);

  /**
   * 重命名目录
   */
  DirectoryNode renameDirectory(String libraryId, String directoryId,
      String newName, String userId);

  // ==================== 删除操作 (2个方法) ====================

  /**
   * 删除文档
   * 业务规则：
   * - 验证用户是否有 DELETE_FILE 权限
   * - 如果启用回收站，移到回收站
   * - 否则物理删除
   */
  void deleteDocument(String libraryId, String documentId, String userId);

  /**
   * 删除目录
   * 业务规则：
   * - 验证用户是否有 DELETE_DIR 权限
   * - 递归检查所有子节点的删除权限
   * - 如果启用回收站，移到回收站
   * - 否则物理删除
   */
  void deleteDirectory(String libraryId, String directoryId, String userId);

  // ==================== 权限查询 (4个方法) ====================

  /**
   * 获取文档的有效权限
   * 业务规则：计算继承权限 + 自身权限
   */
  PermissionSet getDocumentPermissions(String libraryId, String documentId, String userId);

  /**
   * 获取目录的有效权限
   */
  PermissionSet getDirectoryPermissions(String libraryId, String directoryId, String userId);

  /**
   * 检查是否有文档的指定权限
   */
  boolean hasDocumentPermission(String libraryId, String documentId,
      String userId, Permission permission);

  /**
   * 检查是否有目录的指定权限
   */
  boolean hasDirectoryPermission(String libraryId, String directoryId,
      String userId, Permission permission);

  // ==================== 权限管理（需要 MANAGE 权限）(6个方法) ====================

  /**
   * 获取文档的 ACL 条目
   * 业务规则：验证用户是否有 MANAGE 权限
   */
  List<AclEntry> getDocumentAclEntries(String libraryId, String documentId, String userId);

  /**
   * 获取目录的 ACL 条目
   */
  List<AclEntry> getDirectoryAclEntries(String libraryId, String directoryId, String userId);

  /**
   * 授予文档权限
   * 业务规则：验证用户是否有 MANAGE 权限
   */
  AclEntry grantDocumentPermission(String libraryId, String documentId,
      String principal, String principalType, PermissionSet permissions, String userId);

  /**
   * 授予目录权限
   */
  AclEntry grantDirectoryPermission(String libraryId, String directoryId,
      String principal, String principalType, PermissionSet permissions, String userId);

  /**
   * 撤销文档权限
   */
  void revokeDocumentPermission(String libraryId, String documentId,
      String principal, String principalType, String userId);

  /**
   * 撤销目录权限
   */
  void revokeDirectoryPermission(String libraryId, String directoryId,
      String principal, String principalType, String userId);

  // ==================== 统计信息 (1个方法 + 接口) ====================

  /**
   * 获取目录统计信息
   * 业务规则：验证用户是否有目录访问权限
   */
  DirectoryStatistics getDirectoryStatistics(String libraryId,
      String directoryId, String userId);

  /**
   * 目录统计信息接口
   */
  interface DirectoryStatistics {
    String getDirectoryId();
    long getSubdirectoryCount();      // 直接子目录数量
    long getDocumentCount();          // 直接文档数量
    long getTotalSubdirectoryCount(); // 递归子目录数量
    long getTotalDocumentCount();     // 递归文档数量
    long getTotalSize();              // 递归文件总大小
  }
}
```

**设计要点**：

1. **用户视角**
   - 所有方法都包含 `userId` 参数
   - 每个方法都进行权限验证
   - 只返回用户有权限访问的内容

2. **服务编排**
   - 协调 `LibraryContentService`（内容操作）
   - 协调 `ACLService`（权限检查）
   - 协调 `PermissionAdminService`（权限管理）
   - 协调 `DocumentLibraryService`（配额检查）

3. **业务规则集成**
   - 配额检查（上传前）
   - 名称冲突检查（创建、重命名、移动）
   - 循环移动检查（移动目录）
   - 权限继承计算（权限查询）
   - 递归权限检查（删除目录）

4. **典型业务流程**

**上传文件流程**：
```
1. 验证用户是否有目录的 CREATE_FILE 权限
   ↓ (ACLService.hasPermission)
2. 检查文件名是否重复
   ↓ (LibraryContentService)
3. 检查档案库配额
   ↓ (DocumentLibraryService.canUploadFile)
4. 上传文件到存储
   ↓ (LibraryContentService.uploadDocument 内部调用 StorageService)
5. 创建文档节点元数据
   ↓
6. 返回 DocumentNode
```

**删除目录流程**：
```
1. 验证用户是否有 DELETE_DIR 权限
   ↓ (ACLService.hasPermission)
2. 递归检查所有子节点的删除权限
   ↓ (ACLService + LibraryContentService)
3. 判断是否启用回收站
   ↓ (DocumentLibraryService.getLibrary)
4a. 如果启用回收站：移到回收站
4b. 否则：物理删除
   ↓ (LibraryContentService.deleteDirectory)
5. 返回
```

**方法总览**：共 **45+ 个方法**，覆盖档案库内容管理的所有用户场景

---

#### 4.6.2 DocumentLibraryManagementFacade（档案库管理门面）

**职责**：从档案库所有者和管理员的角度提供档案库管理功能，包括创建档案库、管理基本信息、管理管理员、转移所有权、管理访问权限等

**权限控制**：
- **所有者（Owner）**：拥有所有管理权限
- **管理员（Admin）**：只能管理访问权限（添加/修改/删除 AclEntry）
- **管理员限制**：不能修改基本信息、不能添加/删除管理员、不能转移所有权

**核心方法分类**：

```java
public interface DocumentLibraryManagementFacade {

  // ==================== 档案库创建和查询 (6个方法) ====================

  /**
   * 创建档案库
   * 业务规则：
   * - 任何有权限的用户都可以创建档案库
   * - 创建者自动成为所有者（owner）
   * - 验证档案库名称是否已存在
   * - 自动创建根目录
   */
  DocumentLibrary createLibrary(String name, String displayName,
      String description, String userId);

  /**
   * 查询用户拥有的档案库列表（作为所有者）
   */
  List<DocumentLibrary> listOwnedLibraries(String userId);

  /**
   * 查询用户管理的档案库列表（作为管理员）
   */
  List<DocumentLibrary> listManagedLibraries(String userId);

  /**
   * 查询用户可访问的所有档案库（所有者 + 管理员 + 有权限访问）
   */
  List<DocumentLibrary> listAccessibleLibraries(String userId);

  /**
   * 根据ID获取档案库详情
   * 业务规则：
   * - 验证用户是否是所有者或管理员
   * - 或者验证用户是否有档案库的访问权限
   */
  Optional<DocumentLibrary> getLibrary(String libraryId, String userId);

  /**
   * 根据名称获取档案库
   */
  Optional<DocumentLibrary> getLibraryByName(String name, String userId);

  // ==================== 基本信息管理（所有者操作）(3个方法) ====================

  /**
   * 更新档案库基本信息（所有者操作）
   * 业务规则：
   * - 验证用户是否是所有者
   * - 可更新：名称、显示名称、描述
   * - 不可更改：所有者、创建时间
   * - 验证新名称是否与其他档案库冲突
   */
  DocumentLibrary updateLibraryInfo(String libraryId, String name,
      String displayName, String description, String userId);

  /**
   * 更新档案库配额限制（所有者操作）
   * 业务规则：
   * - 验证用户是否是所有者
   * - 更新最大存储空间和最大文件数量限制
   * - null 表示不限制
   */
  DocumentLibrary updateLibraryQuota(String libraryId, Long maxSizeLimit,
      Long maxFileCountLimit, String userId);

  /**
   * 启用/禁用回收站功能（所有者操作）
   */
  DocumentLibrary setTrashEnabled(String libraryId, boolean enabled, String userId);

  // ==================== 管理员管理（所有者操作）(6个方法) ====================

  /**
   * 添加档案库管理员（所有者操作）
   * 业务规则：
   * - 验证用户是否是所有者
   * - 添加新的管理员
   * - 管理员可以管理档案库的访问权限
   */
  DocumentLibrary addAdmin(String libraryId, String adminUserId, String userId);

  /**
   * 移除档案库管理员（所有者操作）
   */
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String userId);

  /**
   * 批量添加管理员（所有者操作）
   */
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String userId);

  /**
   * 批量移除管理员（所有者操作）
   */
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String userId);

  /**
   * 获取档案库的所有管理员列表
   */
  List<String> listAdmins(String libraryId, String userId);

  // ==================== 所有权转移（所有者操作）(1个方法) ====================

  /**
   * 转移档案库所有权（所有者操作）
   * 业务规则：
   * - 验证用户是否是当前所有者
   * - 将所有权转移给新用户
   * - 原所有者自动成为管理员（可选）
   */
  DocumentLibrary transferOwnership(String libraryId, String newOwnerId,
      boolean keepAsAdmin, String userId);

  // ==================== 访问权限管理（所有者和管理员都可以）(7个方法) ====================

  /**
   * 查询档案库的所有访问权限条目
   * 业务规则：验证用户是否是所有者或管理员
   */
  List<AclEntry> getLibraryPermissions(String libraryId, String userId);

  /**
   * 为档案库授予访问权限（所有者和管理员都可以）
   * 业务规则：
   * - 验证用户是否是所有者或管理员
   * - 添加或更新权限条目
   * - 如果已存在，则合并权限
   */
  AclEntry grantLibraryPermission(String libraryId, String principal,
      String principalType, PermissionSet permissions, String userId);

  /**
   * 更新档案库的访问权限（所有者和管理员都可以）
   * 业务规则：更新权限条目（替换权限，而不是合并）
   */
  AclEntry updateLibraryPermission(String libraryId, String principal,
      String principalType, PermissionSet permissions, String userId);

  /**
   * 撤销档案库的访问权限（所有者和管理员都可以）
   */
  void revokeLibraryPermission(String libraryId, String principal,
      String principalType, String userId);

  /**
   * 批量授予档案库访问权限
   */
  List<AclEntry> batchGrantLibraryPermissions(String libraryId,
      List<AclEntry> aclEntries, String userId);

  /**
   * 批量撤销档案库访问权限
   */
  void batchRevokeLibraryPermissions(String libraryId,
      List<String> principals, String userId);

  // ==================== 角色和权限检查 (3个方法) ====================

  /**
   * 检查用户是否是档案库所有者
   */
  boolean isOwner(String libraryId, String userId);

  /**
   * 检查用户是否是档案库管理员
   */
  boolean isAdmin(String libraryId, String userId);

  /**
   * 检查用户是否可以管理档案库（所有者或管理员）
   */
  boolean canManageLibrary(String libraryId, String userId);

  // ==================== 统计信息 (3个方法) ====================

  /**
   * 获取档案库统计信息
   * 业务规则：验证用户是否是所有者或管理员
   */
  LibraryStatistics getLibraryStatistics(String libraryId, String userId);

  /**
   * 检查档案库配额是否超限
   */
  boolean isQuotaExceeded(String libraryId, String userId);

  /**
   * 检查是否可以上传指定大小的文件
   */
  boolean canUploadFile(String libraryId, long fileSize, String userId);
}
```

**设计要点**：

1. **角色权限区分**
   - **所有者权限**：所有管理操作
   - **管理员权限**：仅访问权限管理
   - 方法中明确标注角色要求

2. **权限验证层级**
   ```
   操作类型          验证规则
   ----------------------------------------
   创建档案库        → 任何用户（平台级权限控制）
   基本信息管理      → 必须是所有者
   管理员管理        → 必须是所有者
   所有权转移        → 必须是所有者
   访问权限管理      → 所有者或管理员
   查询统计信息      → 所有者或管理员
   ```

3. **服务编排**
   - `DocumentLibraryService`：档案库 CRUD、管理员管理、统计
   - `ACLService`：权限检查
   - `PermissionAdminService`：权限管理
   - `LibraryContentService`：根目录创建

4. **典型业务流程**

**创建档案库流程**：
```
1. 验证用户是否有创建档案库的平台权限
   ↓
2. 验证档案库名称是否已存在
   ↓ (DocumentLibraryService.isNameAvailable)
3. 创建档案库
   ↓ (DocumentLibraryService.createLibrary)
4. 创建根目录
   ↓ (LibraryContentService.createDirectory)
5. 返回 DocumentLibrary
```

**转移所有权流程**：
```
1. 验证当前用户是否是所有者
   ↓ (DocumentLibraryService.isOwner)
2. 更新所有者
   ↓
3. 如果 keepAsAdmin=true，将原所有者添加为管理员
   ↓ (DocumentLibraryService.addAdmin)
4. 记录审计日志
   ↓
5. 返回更新后的 DocumentLibrary
```

**方法总览**：共 **30+ 个方法**，覆盖档案库管理的所有场景

---

#### 4.6.3 DocumentLibraryAdminFacade（档案库后台管理门面）

**职责**：从模块管理员的角度提供档案库的后台管理功能，包括系统级所有权转移、配额管理、状态管理、删除操作、权限查询管理、统计监控等

**权限控制**：
- **所有操作均不验证权限**，因为模块管理员属于系统级管理员
- 方法参数中的 `operatorId` 仅用于审计追踪，不用于权限验证
- 可以对任何档案库执行任何操作

**核心方法分类**：

```java
public interface DocumentLibraryAdminFacade {

  // ==================== 档案库查询 (6个方法) ====================

  /**
   * 查询所有档案库（包括已删除、已禁用、已锁定）
   * 业务规则：不进行权限验证
   */
  List<DocumentLibrary> listAllLibraries();

  /**
   * 查询所有档案库（包含过滤条件）
   */
  List<DocumentLibrary> listLibraries(boolean includeDeleted,
      boolean includeDisabled, boolean includeLocked);

  /**
   * 根据所有者查询档案库
   */
  List<DocumentLibrary> listLibrariesByOwner(String ownerId);

  /**
   * 根据管理员查询档案库
   */
  List<DocumentLibrary> listLibrariesByAdmin(String adminId);

  /**
   * 根据ID获取档案库详情（即使已删除/禁用/锁定也能查询）
   */
  Optional<DocumentLibrary> getLibrary(String libraryId);

  /**
   * 根据名称获取档案库
   */
  Optional<DocumentLibrary> getLibraryByName(String name);

  // ==================== 所有权和管理员管理（系统级）(8个方法) ====================

  /**
   * 转移档案库所有权（系统级操作）
   * 业务规则：不验证操作者权限，直接转移所有权
   */
  DocumentLibrary transferOwnership(String libraryId, String newOwnerId,
      boolean keepOriginalOwnerAsAdmin, String operatorId);

  /**
   * 设置档案库所有者（系统级操作）
   * 业务规则：直接设置新的所有者，不保留原所有者
   */
  DocumentLibrary setOwner(String libraryId, String newOwnerId, String operatorId);

  /**
   * 添加档案库管理员（系统级操作）
   */
  DocumentLibrary addAdmin(String libraryId, String adminUserId, String operatorId);

  /**
   * 移除档案库管理员（系统级操作）
   */
  DocumentLibrary removeAdmin(String libraryId, String adminUserId, String operatorId);

  /**
   * 批量添加管理员
   */
  DocumentLibrary addAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 批量移除管理员
   */
  DocumentLibrary removeAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 批量设置管理员（替换现有的所有管理员）
   */
  DocumentLibrary setAdmins(String libraryId, List<String> adminUserIds, String operatorId);

  /**
   * 获取档案库的所有管理员列表
   */
  List<String> listAdmins(String libraryId);

  // ==================== 配额管理 (3个方法) ====================

  /**
   * 更新档案库配额限制
   * 业务规则：
   * - null 表示不限制
   * - 可以设置为 0 来禁止上传
   */
  DocumentLibrary updateQuota(String libraryId, Long maxSizeLimit,
      Long maxFileCountLimit, String operatorId);

  /**
   * 移除档案库配额限制（设置为不限制）
   */
  DocumentLibrary removeQuota(String libraryId, String operatorId);

  /**
   * 批量更新多个档案库的配额
   */
  List<DocumentLibrary> batchUpdateQuota(List<String> libraryIds,
      Long maxSizeLimit, Long maxFileCountLimit, String operatorId);

  // ==================== 档案库状态管理 (8个方法) ====================

  /**
   * 启用档案库
   * 业务规则：设置档案库为启用状态，启用后用户可以正常访问
   */
  DocumentLibrary enableLibrary(String libraryId, String operatorId);

  /**
   * 禁用档案库
   * 业务规则：禁用后除管理员外，其他用户无法访问
   */
  DocumentLibrary disableLibrary(String libraryId, String operatorId);

  /**
   * 锁定档案库（只读模式）
   * 业务规则：锁定后不能上传、删除、修改文件，只能查看和下载
   */
  DocumentLibrary lockLibrary(String libraryId, String operatorId);

  /**
   * 解锁档案库
   */
  DocumentLibrary unlockLibrary(String libraryId, String operatorId);

  /**
   * 启用回收站功能
   */
  DocumentLibrary enableTrash(String libraryId, String operatorId);

  /**
   * 禁用回收站功能
   */
  DocumentLibrary disableTrash(String libraryId, String operatorId);

  /**
   * 批量启用档案库
   */
  List<DocumentLibrary> batchEnableLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量禁用档案库
   */
  List<DocumentLibrary> batchDisableLibraries(List<String> libraryIds, String operatorId);

  // ==================== 删除操作 (7个方法) ====================

  /**
   * 软删除档案库
   * 业务规则：标记为已删除状态，不物理删除数据，可恢复
   */
  DocumentLibrary softDeleteLibrary(String libraryId, String operatorId);

  /**
   * 恢复已删除的档案库
   */
  DocumentLibrary restoreLibrary(String libraryId, String operatorId);

  /**
   * 硬删除档案库
   * 业务规则：
   * - 物理删除档案库及其所有数据
   * - 删除档案库元数据、所有节点、所有权限、所有文件
   * - 此操作不可恢复
   */
  void hardDeleteLibrary(String libraryId, String operatorId);

  /**
   * 批量软删除档案库
   */
  List<DocumentLibrary> batchSoftDeleteLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量恢复档案库
   */
  List<DocumentLibrary> batchRestoreLibraries(List<String> libraryIds, String operatorId);

  /**
   * 批量硬删除档案库（警告：此操作不可恢复）
   */
  void batchHardDeleteLibraries(List<String> libraryIds, String operatorId);

  /**
   * 清理已软删除的档案库
   * 业务规则：
   * - 查找所有已软删除超过指定天数的档案库
   * - 执行硬删除操作
   * - 用于定期清理任务
   */
  int cleanupDeletedLibraries(int retentionDays, String operatorId);

  // ==================== 权限查询和管理 (10个方法) ====================

  /**
   * 查询档案库的所有访问权限条目
   */
  List<AclEntry> getLibraryPermissions(String libraryId);

  /**
   * 为档案库授予访问权限
   */
  AclEntry grantLibraryPermission(String libraryId, String principal,
      String principalType, PermissionSet permissions, String operatorId);

  /**
   * 更新档案库的访问权限
   */
  AclEntry updateLibraryPermission(String libraryId, String principal,
      String principalType, PermissionSet permissions, String operatorId);

  /**
   * 撤销档案库的访问权限
   */
  void revokeLibraryPermission(String libraryId, String principal,
      String principalType, String operatorId);

  /**
   * 批量授予档案库访问权限
   */
  List<AclEntry> batchGrantLibraryPermissions(String libraryId,
      List<AclEntry> aclEntries, String operatorId);

  /**
   * 批量撤销档案库访问权限
   */
  void batchRevokeLibraryPermissions(String libraryId,
      List<String> principals, String operatorId);

  /**
   * 清空档案库的所有访问权限
   * 业务规则：删除档案库的所有权限条目，不影响所有者和管理员的访问
   */
  void clearLibraryPermissions(String libraryId, String operatorId);

  /**
   * 查询用户在所有档案库中的权限
   * 业务规则：返回指定用户作为主体的所有权限条目，用于审计和权限分析
   */
  List<AclEntry> getUserPermissions(String userId);

  /**
   * 撤销用户在所有档案库中的权限
   * 业务规则：删除指定用户作为主体的所有权限条目，用于用户离职或权限回收场景
   */
  void revokeAllUserPermissions(String userId, String operatorId);

  // ==================== 统计和监控 (6个方法 + 接口) ====================

  /**
   * 获取档案库统计信息
   */
  LibraryStatistics getLibraryStatistics(String libraryId);

  /**
   * 获取所有档案库的汇总统计信息
   */
  GlobalStatistics getGlobalStatistics();

  /**
   * 获取配额超限的档案库列表
   */
  List<DocumentLibrary> listQuotaExceededLibraries();

  /**
   * 获取已禁用的档案库列表
   */
  List<DocumentLibrary> listDisabledLibraries();

  /**
   * 获取已锁定的档案库列表
   */
  List<DocumentLibrary> listLockedLibraries();

  /**
   * 获取已软删除的档案库列表
   */
  List<DocumentLibrary> listDeletedLibraries();

  /**
   * 全局统计信息接口
   */
  interface GlobalStatistics {
    long getTotalLibraryCount();           // 档案库总数
    long getEnabledLibraryCount();         // 启用的档案库数量
    long getDisabledLibraryCount();        // 禁用的档案库数量
    long getLockedLibraryCount();          // 锁定的档案库数量
    long getDeletedLibraryCount();         // 已删除的档案库数量
    long getTotalDocumentCount();          // 文档总数
    long getTotalDirectoryCount();         // 目录总数
    long getTotalUsedSpace();              // 总存储空间使用量（字节）
    long getQuotaExceededLibraryCount();   // 配额超限的档案库数量
  }
}
```

**设计要点**：

1. **系统级管理**
   - 所有操作不验证权限
   - `operatorId` 仅用于审计日志
   - 可以操作任何档案库

2. **批量操作支持**
   - 配额批量更新
   - 状态批量变更
   - 删除批量操作
   - 提高管理效率

3. **定期清理任务**
   - `cleanupDeletedLibraries`：自动清理过期的软删除档案库
   - 支持配置保留天数
   - 返回清理数量

4. **全局监控**
   - `GlobalStatistics`：提供系统级统计信息
   - 监控配额、状态、空间使用
   - 支持运维和决策

5. **服务编排**
   - `DocumentLibraryService`：所有档案库操作
   - `PermissionAdminService`：权限管理
   - `LibraryContentService`：节点和文件删除
   - `StorageService`：物理文件删除

6. **典型业务流程**

**硬删除档案库流程**：
```
1. 查询档案库信息
   ↓ (DocumentLibraryService.getLibraryById)
2. 删除所有权限条目
   ↓ (PermissionAdminService.deleteByLibrary)
3. 递归删除所有节点（目录和文档）
   ↓ (LibraryContentService.deleteDirectoryRecursively)
4. 删除所有物理文件
   ↓ (StorageService.delete)
5. 删除档案库元数据
   ↓ (DocumentLibraryService.hardDeleteLibrary)
6. 记录审计日志
   ↓
7. 返回
```

**清理过期删除档案库流程**：
```
1. 查询所有软删除的档案库
   ↓ (DocumentLibraryService.listLibraries)
2. 过滤出超过保留天数的档案库
   ↓
3. 对每个档案库执行硬删除
   ↓ (hardDeleteLibrary)
4. 统计删除数量
   ↓
5. 返回清理数量
```

**方法总览**：共 **40+ 个方法**，覆盖档案库后台管理的所有场景

---

#### 4.6.4 业务门面服务设计总结

**三个门面服务的职责对比**：

| 维度 | DocumentLibraryContentFacade | DocumentLibraryManagementFacade | DocumentLibraryAdminFacade |
|-----|----------------------------|-------------------------------|---------------------------|
| **角色视角** | 档案库使用者 | 档案库管理者（所有者/管理员） | 系统管理员 |
| **权限验证** | 每个方法都验证权限 | 区分所有者和管理员权限 | 不验证权限 |
| **核心职责** | 内容的浏览、上传、下载、管理 | 档案库的创建、配置、权限管理 | 系统级管理、监控、维护 |
| **方法数量** | 45+ | 30+ | 40+ |
| **典型操作** | 浏览目录、上传文件、下载文档 | 创建档案库、添加管理员、设置权限 | 转移所有权、配额管理、批量操作 |

**服务分层关系**：

```
                    ┌───────────────────────────────┐
                    │    业务门面服务 (Facade)      │
                    │  - DocumentLibraryContentFacade    │
                    │  - DocumentLibraryManagementFacade │
                    │  - DocumentLibraryAdminFacade      │
                    └───────────────┬───────────────┘
                                    │ 协调调用
                    ┌───────────────┴───────────────┐
                    │         核心服务层             │
                    │  - DocumentLibraryService      │
                    │  - LibraryContentService       │
                    │  - ACLService                  │
                    │  - PermissionAdminService      │
                    └───────────────┬───────────────┘
                                    │ 依赖
                    ┌───────────────┴───────────────┐
                    │      基础设施层               │
                    │  - Repository                  │
                    │  - StorageService              │
                    └───────────────────────────────┘
```

**设计优势**：

1. **清晰的职责边界**
   - 每个门面服务针对不同角色和场景
   - 避免职责混乱和权限错误

2. **业务规则集中**
   - 复杂业务逻辑集中在门面层
   - 底层服务保持简单和原子化

3. **事务管理**
   - 门面方法作为事务边界
   - 保证复杂业务操作的原子性

4. **易于测试**
   - 门面层可以 mock 底层服务
   - 便于编写集成测试

5. **API 层友好**
   - Controller 层直接调用门面服务
   - 减少 Controller 的业务逻辑

---

## 5. 存储抽象设计

### 5.1 元数据与文件分离

文档管理模块采用**元数据与文件内容分离**的设计：

```
元数据（Metadata）          文件内容（File Content）
    ↓                           ↓
LibraryNodeRepository      StorageService
    ↓                           ↓
TypedDataModel 或          本地文件系统 或
关系数据库                  对象存储
```

**优势**：

1. **独立优化**
   - 元数据可以使用关系数据库（支持复杂查询）
   - 文件内容可以使用对象存储（支持大文件、CDN 加速）

2. **独立扩展**
   - 元数据存储和文件存储可以独立扩展
   - 例如：元数据使用主从复制，文件使用分布式存储

3. **关联解耦**
   - 通过 `storagePath` 关联
   - 元数据迁移不需要移动文件
   - 文件迁移不需要更新元数据（只需更新 `storagePath`）

### 5.2 Repository 接口设计

Repository 层负责所有元数据的持久化存储和检索，包括文档库、节点（目录和文档）、权限条目等。

#### 5.2.1 LibraryRepository（文档库持久化）

**职责**：文档库元数据的存储和检索

```java
public interface LibraryRepository {

  // 基本 CRUD
  DocumentLibrary save(DocumentLibrary library);
  Optional<DocumentLibrary> findById(String id);
  void deleteById(String id);

  // 按条件查询
  Optional<DocumentLibrary> findByName(String name);
  List<DocumentLibrary> findByOwner(String userId);
  List<DocumentLibrary> findByAdmin(String userId);
  List<DocumentLibrary> findAll();
  List<DocumentLibrary> findAll(boolean includeDeleted);

  // 存在性检查
  boolean existsByName(String name);
  boolean existsByNameAndIdNot(String name, String excludeId);

  // 统计功能
  long countByOwner(String userId);
  long count();
}
```

**核心功能**：

1. **基本 CRUD 操作**
   - `save`：创建或更新文档库
   - `findById`：根据 ID 查询
   - `deleteById`：物理删除（硬删除）

2. **按角色查询**
   - `findByOwner`：查询用户拥有的文档库
   - `findByAdmin`：查询用户管理的文档库

3. **名称唯一性**
   - `existsByName`：检查名称是否已存在
   - `existsByNameAndIdNot`：更新时检查名称冲突（排除自身）

4. **软删除支持**
   - `findAll(includeDeleted)`：支持查询已删除的文档库

**设计要点**：
- 使用 `Optional<T>` 处理不存在的情况
- 支持软删除（通过 `isDeleted` 字段过滤）
- 提供名称唯一性检查（业务约束）

---

#### 5.2.2 LibraryNodeRepository（节点持久化）

**职责**：文档库节点（目录和文档）的存储和检索

```java
public interface LibraryNodeRepository {

  // 基本 CRUD
  LibraryNode save(LibraryNode node);
  Optional<LibraryNode> findById(String id);
  void deleteById(String id);

  // 类型特化查询
  Optional<DirectoryNode> findDirectoryById(String id);
  Optional<DocumentNode> findDocumentById(String id);
  Optional<DirectoryNode> findRootDirectory(String libraryId);

  // 层级查询
  List<LibraryNode> findByParentId(String parentId);
  List<DirectoryNode> findDirectoriesByParentId(String parentId);
  List<DocumentNode> findDocumentsByParentId(String parentId);
  List<LibraryNode> findByLibraryId(String libraryId);
  List<DocumentNode> findDocumentsByLibraryId(String libraryId);

  // 名称查询
  Optional<LibraryNode> findByParentIdAndName(String parentId, String name);
  boolean existsByParentIdAndName(String parentId, String name);
  boolean existsByParentIdAndNameAndIdNot(String parentId, String name, String excludeId);

  // 批量删除
  void deleteByIds(List<String> ids);
  void deleteDirectoryRecursively(String directoryId);

  // 统计功能
  long countDocumentsByLibraryId(String libraryId);
  long sumDocumentSizeByLibraryId(String libraryId);
  long countByParentId(String parentId);
  long countDocumentsRecursively(String directoryId);
  long sumDocumentSizeRecursively(String directoryId);

  // 路径操作
  List<LibraryNode> findPathToNode(String nodeId);
  boolean isDescendantOf(String sourceId, String targetId);
}
```

**核心功能**：

1. **统一节点管理**
   - 统一管理目录和文档节点
   - 通过 `NodeType` 区分类型
   - 提供类型特化方法（`findDirectoryById`, `findDocumentById`）

2. **层级关系查询**
   - 按父节点查询子节点
   - 查询根目录
   - 按类型过滤（目录/文档）

3. **名称冲突检查**
   - 同一父目录下名称必须唯一
   - 支持更新时排除自身的检查

4. **递归操作**
   - 递归删除目录及所有子节点
   - 递归统计文档数量和大小

5. **路径构建**
   - `findPathToNode`：构建从根到目标节点的完整路径
   - `isDescendantOf`：检查是否是子孙节点（防止循环移动）

6. **统计分析**
   - 文档数量统计（直接子节点 / 递归所有子节点）
   - 存储空间统计（支持配额检查）

**设计要点**：
- 使用 `LibraryNode` 作为统一返回类型
- 提供类型安全的特化方法（返回 `DirectoryNode` 或 `DocumentNode`）
- 支持递归操作（删除、统计）
- 路径相关功能支持面包屑导航和权限继承

---

#### 5.2.3 AclEntryRepository（权限条目持久化）

**职责**：ACL 权限条目的存储和检索

```java
public interface AclEntryRepository {

  // 基本 CRUD
  AclEntry save(AclEntry entry);
  List<AclEntry> saveAll(List<AclEntry> entries);
  Optional<AclEntry> findById(String id);
  void deleteById(String id);

  // 按资源查询
  List<AclEntry> findByResource(String resourceId, ResourceType resourceType);
  Optional<AclEntry> findByResourceAndPrincipal(
      String resourceId, ResourceType resourceType,
      String principal, String principalType);

  // 按主体查询
  List<AclEntry> findByPrincipal(String principal, String principalType);
  List<AclEntry> findByPrincipalAndResourceType(
      String principal, String principalType, ResourceType resourceType);

  // 按文档库查询
  List<AclEntry> findByLibrary(String libraryId);

  // 存在性检查
  boolean existsByResourceAndPrincipal(
      String resourceId, ResourceType resourceType,
      String principal, String principalType);

  // 批量删除
  void deleteByIds(List<String> ids);
  void deleteByResource(String resourceId, ResourceType resourceType);
  void deleteByResourceAndPrincipal(
      String resourceId, ResourceType resourceType,
      String principal, String principalType);
  void deleteByPrincipal(String principal, String principalType);
  void deleteByLibrary(String libraryId);

  // 统计功能
  long countByResource(String resourceId, ResourceType resourceType);
  long countByPrincipal(String principal, String principalType);
}
```

**核心功能**：

1. **多维度查询**
   - **资源维度**：查询指定资源的所有权限条目
   - **主体维度**：查询指定主体拥有的所有权限
   - **组合维度**：查询特定资源和主体的权限条目

2. **批量操作**
   - `saveAll`：批量保存权限条目（性能优化）
   - `deleteByIds`：批量删除

3. **级联删除**
   - `deleteByResource`：删除资源时同时删除其所有权限
   - `deleteByLibrary`：删除文档库时同时删除所有相关权限
   - `deleteByPrincipal`：删除用户时清理其所有权限

4. **权限条目独立存储**
   - 权限条目独立于资源存储
   - 便于权限查询和管理
   - 支持灵活的权限分配

**设计要点**：
- 权限条目与资源解耦，独立存储
- 支持按资源、主体、组合三个维度查询
- 提供级联删除功能（避免孤立数据）
- 批量操作支持（优化性能）

---

#### 5.2.4 Repository 设计原则

1. **接口驱动**
   - Core 包定义接口
   - Persistence 包提供实现
   - 便于替换实现（TypedDataModel、JPA、MyBatis 等）

2. **统一返回类型**
   - 单个结果：`Optional<T>`（避免 null）
   - 集合结果：`List<T>`（不返回 null）
   - 统计结果：`long`
   - 判断结果：`boolean`

3. **命名规范**
   - `findByXxx`：查询操作
   - `existsByXxx`：存在性检查
   - `countByXxx`：统计操作
   - `deleteByXxx`：删除操作

4. **查询维度丰富**
   - 支持单一条件查询
   - 支持组合条件查询
   - 支持递归查询
   - 支持统计分析

5. **性能优化**
   - 批量操作支持（`saveAll`, `deleteByIds`）
   - 递归操作封装（避免 N+1 查询）
   - 统计功能下推到数据库

6. **业务约束**
   - 名称唯一性检查
   - 父子关系验证
   - 权限条目唯一性

---

### 5.3 StorageService（文件存储接口）

[`StorageService`](document-management-core/src/main/java/com/ouroboros/documentmanagement/storage/StorageService.java) 是文件内容后端的扩展接口。它提供 `store`、`retrieve`、`delete`、`exists`、`getSize`、`copy` 和 `move`；元数据仍由 Repository 管理。

- `store(InputStream, Long, String)` 返回 `StorageResult`，包含后端路径、实际写入字节数及可选校验和；调用方将路径关联到文档节点。
- `retrieve(String)` 返回 `Optional<RetrievedFile>`。`RetrievedFile` 通过 `Supplier<InputStream>` 延迟打开流，调用方负责关闭取得的流；声明的元信息与打开时的文件状态可能不同。
- `getSize` 使用 `Optional<Long>` 表示没有取得大小。具体后端的失败传播以实现为准：缺失返回空值，权限、网络及其他 I/O 失败传播平台异常。
- 复制、移动针对存储路径；目录层级变更由元数据服务处理，不等同于移动文件内容。接口不承诺跨文件系统或远端后端的事务、原子性及并发覆盖顺序。

接口与结果类型直接维护在上述源码中，本页记录职责和行为边界，不再复制类型定义。

### 5.4 存储实现

#### 5.4.1 公共存储适配

当前由 BlobDocumentStorageService 适配原 StorageService，物理 IO 复用 libs/storage 的 filesystem / s3 后端，文档持有独立实例。UUID 与 HASH / DATE / FLAT 布局、SHA-256 策略仍归文档域。输入及读取流由调用者关闭，错误传播；ETag 不代替文档校验和。

#### 5.4.2 文档事务协调

上传使用唯一内容键，事务回滚触发补偿。节点删除与 doc_content_cleanup 登记在同一元数据事务提交，提交后才删除物理内容；失败保留清理责任并退避重试，仍被有效/可恢复节点引用时保留内容。协调机制属于文档服务和 Repository，不进入公共 storage。

#### 5.4.3 配置切换

DocumentStorageAutoConfiguration 装配文档自有实例。新配置为 `ouroboros.document-management.storage.type/options`，命名与校验为 `content.*`；新配置缺省时转换旧 filesystem 配置，显式新旧位置冲突时报错。完整配置与停写保键迁移见[存储配置指南](../../docs/guides/content-storage.md)。MinIO / S3 接口已有实现，OSS/COS/OBS 尚需真实厂商验收。

以下对象存储配置和装配代码是早期接入草案，包含凭据相关内容，按本轮安全冻结约定保留原文。它不是当前可用的配置合同；当前主线未提供示例中的 `LocalStorageService`、`OSSStorageService` 或相应切换装配。

```yaml
# application.yml
document-management:
  storage:
    # 存储类型：local / oss / s3 / minio
    type: local

    # 本地存储配置
    local:
      root-path: ./storage/documents

    # 对象存储配置（当 type=oss 时生效）
    oss:
      endpoint: https://oss-cn-hangzhou.aliyuncs.com
      bucket-name: my-documents
      access-key-id: ${OSS_ACCESS_KEY}
      access-key-secret: ${OSS_ACCESS_SECRET}
```

**动态配置**：

```java
@Configuration
public class StorageConfiguration {

    @Bean
    @ConditionalOnProperty(name = "document-management.storage.type", havingValue = "local")
    public StorageService localStorageService(
        @Value("${document-management.storage.local.root-path}") String rootPath
    ) {
        return new LocalStorageService(rootPath);
    }

    @Bean
    @ConditionalOnProperty(name = "document-management.storage.type", havingValue = "oss")
    public StorageService ossStorageService(
        @Value("${document-management.storage.oss.endpoint}") String endpoint,
        @Value("${document-management.storage.oss.bucket-name}") String bucketName,
        @Value("${document-management.storage.oss.access-key-id}") String accessKeyId,
        @Value("${document-management.storage.oss.access-key-secret}") String accessKeySecret
    ) {
        return new OSSStorageService(endpoint, bucketName, accessKeyId, accessKeySecret);
    }
}
```

---

## 6. API 层设计

### 6.1 RESTful API 风格

遵循 RESTful 最佳实践：

| HTTP 方法 | 路径模式 | 说明 |
|----------|---------|------|
| GET | `/api/resource` | 获取资源列表 |
| GET | `/api/resource/{id}` | 获取单个资源 |
| POST | `/api/resource` | 创建资源 |
| PUT | `/api/resource/{id}` | 更新资源 |
| DELETE | `/api/resource/{id}` | 删除资源 |
| PUT | `/api/resource/{id}/action` | 资源的特定操作 |

### 6.2 API 端点设计

当前非安全 HTTP 职责与入口如下，路由参数和返回类型以各控制器为准：

| 控制器 | 职责及代表入口 |
| --- | --- |
| [DocumentLibraryManagementController](document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller/DocumentLibraryManagementController.java) | `POST /api/document-libraries` 创建、`GET /api/document-libraries/owned` 查询拥有的库、`GET /api/document-libraries/{libraryId}` 详情、`PUT /api/document-libraries/{libraryId}/info` 更新信息 |
| [DocumentLibraryNavigationController](document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller/DocumentLibraryNavigationController.java) | `/api/document-libraries/{libraryId}/directories` 目录树，及目录浏览、子目录/文档列表和节点路径 |
| [DocumentFileQueryController](document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller/DocumentFileQueryController.java) | 文档/目录详情、搜索及目录统计 |
| [DocumentFileOperationController](document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller/DocumentFileOperationController.java) | `POST .../directories/{directoryId}/upload` 上传，`GET .../documents/{documentId}/preview` 预览，`GET .../documents/{documentId}/download` 下载 |
| [DocumentNodeManagementController](document-management-webapp/src/main/java/com/ouroboros/documentmanagement/webapp/controller/DocumentNodeManagementController.java) | 目录创建、节点重命名、移动和删除 |

后台查询和生命周期分别由 `DocumentLibraryAdminQueryController`、`DocumentLibraryAdminLifecycleController` 承载，当前前缀为 `/api/document-management/admin/libraries`；完整入口见[WebApp 指南](document-management-webapp/README.md)。权限、所有权及身份相关接口暂不在本轮统一。

以下后台接口示意及6.3节的上传/预览/下载代码含权限与身份流程，按本轮安全冻结约定保留原文。这些早期示例不代表当前控制器或返回类型；文件传输当前由 `DocumentFileOperationController` 通过业务门面执行。上传接受 `file` 或 `file[]` 表单字段，返回 `WebApiResult<List<DocumentNode>>`；预览和下载返回 `ResponseEntity<Resource>`。

**DocumentLibraryAdminController**（档案库后台管理）

```
GET    /api/document-libraries/admin                      # 获取所有文档库
GET    /api/document-libraries/admin/{id}                 # 获取文档库详情
POST   /api/document-libraries/admin/{id}/transfer-ownership  # 系统级转移所有权
PUT    /api/document-libraries/admin/{id}/quota           # 系统级更新配额
POST   /api/document-libraries/admin/{id}/enable          # 启用文档库
POST   /api/document-libraries/admin/{id}/disable         # 禁用文档库
POST   /api/document-libraries/admin/{id}/lock            # 锁定文档库
POST   /api/document-libraries/admin/{id}/unlock          # 解锁文档库
DELETE /api/document-libraries/admin/{id}                 # 删除文档库
```

### 6.3 文件上传/下载设计

**上传**：

```java
@PostMapping("/upload")
public ResponseEntity<WebApiResult<DocumentNode>> uploadDocument(
    @RequestParam("libraryId") String libraryId,
    @RequestParam("directoryId") String directoryId,
    @RequestParam("file") MultipartFile file,
    Authentication authentication
) {
    String userId = authentication.getName();

    // 1. 权限检查：是否有 CREATE_FILE 权限
    if (!aclService.hasPermission(directoryId, ResourceType.DIRECTORY, userId, Permission.CREATE_FILE)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
            .body(WebApiResult.error("没有上传权限"));
    }

    // 2. 文件验证：大小、类型
    validateFile(file);

    // 3. 上传文档
    DocumentNode document = libraryContentService.uploadDocument(
        libraryId,
        directoryId,
        file.getOriginalFilename(),
        file.getInputStream(),
        file.getSize(),
        file.getContentType(),
        userId
    );

    // 4. 返回文档信息
    return ResponseEntity.ok(WebApiResult.success(document));
}
```

**预览**：

```java
@GetMapping("/{documentId}/preview")
public ResponseEntity<InputStreamResource> previewDocument(
    @PathVariable String documentId,
    Authentication authentication
) {
    String userId = authentication.getName();

    // 1. 权限检查：是否有 VIEW 权限
    if (!aclService.hasPermission(documentId, ResourceType.DOCUMENT, userId, Permission.VIEW)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // 2. 获取文档信息
    DocumentNode document = libraryContentService.getDocument(libraryId, documentId)
        .orElseThrow(() -> new NotFoundException("文档不存在"));

    // 3. 获取文件内容
    InputStream inputStream = libraryContentService.getDocumentContent(libraryId, documentId)
        .orElseThrow(() -> new NotFoundException("文件内容不存在"));

    // 4. 设置响应头：Content-Disposition: inline（预览）
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.getMimeType()))
        .header("Content-Disposition", "inline; filename=\"" + document.getName() + "\"")
        .body(new InputStreamResource(inputStream));
}
```

**下载**：

```java
@GetMapping("/{documentId}/download")
public ResponseEntity<InputStreamResource> downloadDocument(
    @PathVariable String documentId,
    Authentication authentication
) {
    String userId = authentication.getName();

    // 1. 权限检查：是否有 DOWNLOAD 权限
    if (!aclService.hasPermission(documentId, ResourceType.DOCUMENT, userId, Permission.DOWNLOAD)) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }

    // 2. 获取文档信息
    DocumentNode document = libraryContentService.getDocument(libraryId, documentId)
        .orElseThrow(() -> new NotFoundException("文档不存在"));

    // 3. 获取文件内容
    InputStream inputStream = libraryContentService.getDocumentContent(libraryId, documentId)
        .orElseThrow(() -> new NotFoundException("文件内容不存在"));

    // 4. 设置响应头：Content-Disposition: attachment（下载）
    return ResponseEntity.ok()
        .contentType(MediaType.parseMediaType(document.getMimeType()))
        .header("Content-Disposition", "attachment; filename=\"" + document.getName() + "\"")
        .body(new InputStreamResource(inputStream));
}
```

**设计要点**：

1. **预览与下载分离**
   - 通过 `Content-Disposition` 区分（`inline` vs `attachment`）
   - 预览检查 VIEW 权限，下载检查 DOWNLOAD 权限

2. **流式传输**
   - 使用 `InputStreamResource` 支持大文件
   - 避免将整个文件加载到内存

3. **安全防护**
   - 文件类型验证
   - 文件大小限制
   - 病毒扫描（可选）

---

## 7. UI 层设计

### 7.1 Amis Schema 设计

当前页面在 `document-management-webapp/src/main/metadata` 定义，由已有元数据构建与 Provider 加载。入口为 `document-management.ui-model.json`，相关页面在 `document-management/` 下维护，菜单源为 `DocumentManagement.menu.json`。当前 UI 与调用路径以这些文件为准，导航见[WebApp 指南](document-management-webapp/README.md#ui-model--schema)。

以下早期 Schema 同时包含权限管理操作，按安全冻结约定保留，尚未统一；其中旧文件名、页面路径和接口不能直接当作当前接入示例。

Amis 是基于 JSON Schema 的页面渲染引擎，通过配置生成页面。

**文档库列表页面**（`libraries.json`）：

```json
{
  "type": "page",
  "title": "文档库管理",
  "body": [
    {
      "type": "crud",
      "api": "/api/document-management/libraries",
      "headerToolbar": [
        "reload",
        {
          "type": "button",
          "label": "新建文档库",
          "level": "primary",
          "actionType": "dialog",
          "dialog": {
            "title": "新建文档库",
            "body": {
              "type": "form",
              "api": "post:/api/document-management/libraries",
              "body": [
                {
                  "type": "input-text",
                  "name": "name",
                  "label": "文档库名称",
                  "required": true
                },
                {
                  "type": "textarea",
                  "name": "description",
                  "label": "描述"
                }
              ]
            }
          }
        }
      ],
      "columns": [
        {
          "name": "displayName",
          "label": "名称"
        },
        {
          "name": "description",
          "label": "描述"
        },
        {
          "name": "owner",
          "label": "拥有者"
        },
        {
          "type": "operation",
          "label": "操作",
          "buttons": [
            {
              "type": "button",
              "label": "打开",
              "level": "link",
              "actionType": "url",
              "url": "/document-management/documentLibrary/${id}"
            },
            {
              "type": "button",
              "label": "权限",
              "level": "link",
              "actionType": "dialog",
              "dialog": {
                "title": "权限管理",
                "size": "lg",
                "body": {
                  "type": "crud",
                  "api": "/api/document-management/permissions?resourceId=${id}&resourceType=LIBRARY"
                }
              }
            },
            {
              "type": "button",
              "label": "删除",
              "level": "link",
              "className": "text-danger",
              "actionType": "ajax",
              "api": "delete:/api/document-management/libraries/${id}",
              "confirmText": "确认删除该文档库吗？"
            }
          ]
        }
      ]
    }
  ]
}
```

**文档浏览页面**（`documentLibrary-view.json`）：

```json
{
  "type": "page",
  "title": "${libraryName} - 文档浏览",
  "body": [
    {
      "type": "breadcrumb",
      "source": "${directoryPath}"
    },
    {
      "type": "grid",
      "columns": [
        {
          "type": "panel",
          "title": "目录",
          "body": {
            "type": "crud",
            "api": "/api/document-library/${libraryId}/directories/${currentDirectoryId}/subdirectories",
            "columns": [
              {
                "name": "name",
                "label": "名称",
                "type": "link",
                "href": "/document-management/documentLibrary/${libraryId}?directoryId=${id}"
              }
            ]
          }
        },
        {
          "type": "panel",
          "title": "文件",
          "body": {
            "type": "crud",
            "api": "/api/document-library/${libraryId}/directories/${currentDirectoryId}/documents",
            "columns": [
              {
                "name": "name",
                "label": "文件名"
              },
              {
                "name": "size",
                "label": "大小",
                "type": "bytes"
              },
              {
                "type": "operation",
                "label": "操作",
                "buttons": [
                  {
                    "type": "button",
                    "label": "预览",
                    "actionType": "url",
                    "url": "/api/document-library/documents/${id}/preview"
                  },
                  {
                    "type": "button",
                    "label": "下载",
                    "actionType": "url",
                    "url": "/api/document-library/documents/${id}/download"
                  }
                ]
              }
            ]
          }
        }
      ]
    }
  ]
}
```

---

## 8. 平台集成设计

### 8.1 应用模块注册

**app-modules.json**：
```json
[
  {
    "fullName": "documentManagement",
    "title": "文档管理",
    "menu": "documentManagement",
    "authorities": [
      "documentManagement",
      "documentManagement.library",
      "documentManagement.content",
      "documentManagement.permission"
    ],
    "order": 50
  }
]
```

### 8.2 权限配置

**authority/documentManagement.json**：
```json
{
  "fullName": "documentManagement",
  "title": "文档管理",
  "apis": [
    "GET /api/document-management/libraries",
    "POST /api/document-management/libraries",
    "GET /api/document-library/**",
    "POST /api/document-library/**"
  ],
  "uiViews": [
    "/document-management",
    "/document-management/documentLibrary/{libraryId}"
  ],
  "menuItems": [
    "documentManagement"
  ]
}
```

### 8.3 菜单配置

**menu-model/documentManagement.json**：
```json
{
  "url": "/document-management",
  "fullName": "documentManagement",
  "name": "documentManagement",
  "title": "文档管理",
  "icon": "fa fa-folder-open",
  "order": 50
}
```

---

## 9. 总结

### 9.1 设计亮点

1. **统一的节点抽象（LibraryNode）**
   - 目录和文档统一建模
   - 简化层级管理和权限继承
   - 易于扩展新的节点类型

2. **细粒度权限控制**
   - 9 种权限类型（VIEW, DOWNLOAD, CREATE_FILE, CREATE_DIR, RENAME_MOVE, DELETE_FILE, DELETE_DIR, NAVIGATE, MANAGE），支持作用域区分
   - 预览与下载分离，满足企业防泄密需求
   - 权限继承机制，简化权限管理
   - 作用域区分（FILE/DIRECTORY/BOTH），防止权限误用

3. **清晰的服务分层**
   - DocumentLibraryService：文档库管理
   - LibraryContentService：内容管理（原子化操作）
   - ACLService：权限检查（只读）
   - PermissionAdminService：权限管理（读写）

4. **元数据与文件分离**
   - 元数据通过 Repository 持久化
   - 文件通过 StorageService 抽象存储
   - 支持多种存储方式（本地、对象存储）

5. **接口驱动设计**
   - Core 包定义所有接口和契约
   - 便于测试、替换和扩展
   - 符合依赖倒置原则

### 9.2 适用场景

- ✅ 企业内部知识库
- ✅ 项目文档管理
- ✅ 合同文档管理
- ✅ 培训资料管理
- ✅ 技术文档中心

### 9.3 与主流产品对比

| 功能 | 本模块 | 飞书文档 | 钉钉知识库 |
|------|--------|---------|-----------|
| 文档库管理 | ✅ | ✅ | ✅ |
| 多级目录 | ✅ | ✅ | ✅ |
| 预览/下载分离 | ✅ | ✅ | ✅ |
| 权限继承 | ✅ | ✅ | ✅ |
| 版本管理 | ❌ | ✅ | ✅ |
| 协同编辑 | ❌ | ✅ | ✅ |
| 全文搜索 | ❌ | ✅ | ✅ |
| 评论功能 | ❌ | ✅ | ✅ |

**结论**：核心功能完备，高级功能待扩展。

### 9.4 后续优化方向

**性能优化**：
- 权限缓存（Redis）
- 批量操作优化
- 异步处理（文件上传、病毒扫描）

**功能增强**：
- 文档版本管理
- 全文搜索（Elasticsearch）
- 文档协同编辑
- 回收站功能
- 评论和讨论功能

**安全加固**：
- 文件加密存储
- 水印功能
- 审计日志
- 敏感文档扫描

---

**文档版本**：v3.0（基于 Core 包抽象概念重写）
**最后更新**：2025-12-11
**作者**：Claude Code
