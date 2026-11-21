# Ouroboros 低代码开发模块设计模式

本文档描述 Ouroboros 平台中低代码开发模块（Dev Module）的标准设计模式，用于指导新开发模块的实现。

## 目录

- [设计理念](#设计理念)
- [核心概念](#核心概念)
- [模块结构](#模块结构)
- [关键组件](#关键组件)
- [实现步骤](#实现步骤)
- [配置文件](#配置文件)
- [API端点](#api端点)
- [最佳实践](#最佳实践)
- [示例参考](#示例参考)

---

## 设计理念

### 核心原则

1. **文件驱动**：所有模型数据以 JSON 文件形式存储在文件系统中，而非数据库
2. **版本控制**：基于 Git 的版本管理，支持提交、回滚、历史查询
3. **元数据分离**：模型元数据与实际资源文件（如有）分开存储
4. **统一接口**：通过 `BaseDevelopService` 提供标准的 CRUD 和版本控制操作
5. **事件驱动**：模型变更时发布事件，支持其他模块监听和响应
6. **并发控制**：文件级别的锁机制，防止多用户冲突
7. **自动装配**：通过 Spring Boot 自动配置和 Java SPI 实现插件式集成

### 为什么不用数据库？

- **版本管理需求**：文件系统配合 Git 天然支持版本控制
- **可读性**：JSON 文件易于阅读和手动编辑
- **可移植性**：文件系统便于导入导出和迁移
- **开发体验**：文件变更可直接触发重新加载，无需数据库迁移
- **协作友好**：Git 提供强大的协作、合并和冲突解决能力

---

## 核心概念

### 1. 模型（Model）

模型是开发模块管理的核心对象，必须满足：

- 可序列化为 JSON
- 包含唯一标识（fullName）
- 使用 `@JsonIgnoreProperties(ignoreUnknown = true)` 注解以支持向后兼容

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public class MyModel {
    private String name;        // 唯一标识
    private String title;       // 显示名称
    // 其他字段...
}
```

### 2. 文件存储

每个模型对应两类文件：

1. **模型文件**：`{modelFileDirectory}/{fullName}{modelFileExtension}`
   - 示例：`ui-schema/form.detail.json`
   - 存储模型元数据的 JSON

2. **关联文件**（可选）：实际的资源文件
   - 示例：`lowcode-resources/public/templates/invoice.docx`
   - 与模型文件分离存储

### 3. 版本控制系统（VCS）

通过 `VCSManager` 接口管理文件：

- **VCSFile**：代表一个受版本控制的文件
- **VCSAction**：批量文件操作（create, update, delete）
- **锁机制**：`lock()`, `unlock()` 防止并发冲突
- **提交**：`commit(message)` 提交变更到 Git
- **回滚**：`rollback()` 撤销未提交的变更
- **历史**：`getChangeLogs()` 查询提交历史

### 4. 开发服务（DevelopService）

通过继承 `BaseDevelopService<M>` 获得标准功能：

- CRUD 操作（create, getOne, update, delete）
- 版本控制（lock, unlock, commit, rollback）
- 搜索和过滤
- 状态查询
- 历史记录

---

## 模块结构

### Maven 模块结构

```
{module-name}-dev/
├── pom.xml                           # Maven 配置
└── src/main/
    ├── java/com/ouroboros/{module}/dev/
    │   ├── model/                    # 模型类
    │   │   └── {Model}.java
    │   ├── service/                  # 服务类
    │   │   └── {Model}DevService.java
    │   ├── inspector/                # VCS 文件检查器
    │   │   └── {Model}Inspector.java
    │   └── {Module}DevAutoConfiguration.java  # 自动配置
    └── resources/META-INF/
        ├── spring.factories          # Spring Boot 自动配置
        ├── services/                 # Java SPI 配置
        │   └── com.ouroboros.vcs.file.inspector.VCSFileInspector
        ├── ouroboros/
        │   ├── dev-menu-model/       # 开发菜单
        │   │   └── AllModels.{Module}.json
        │   └── ui-model/dev/{appName}/  # UI 页面模板
        │       ├── {module-name}.json           # 列表页
        │       ├── {module-name}/add.json       # 创建页
        │       ├── {module-name}/{fullName}.json  # 详情页
        │       └── {module-name}/preview.json   # 预览页（可选）
```

### 目录命名约定

- **模块名**：小写，用连字符分隔（如 `ui-schema`, `lowcode-resources`）
- **模型类**：大驼峰命名（如 `SchemaNodeWrapper`, `ResourceModel`）
- **服务类**：`{Model}DevService`
- **配置类**：`{Module}DevAutoConfiguration`
- **检查器类**：`{Model}Inspector`

---

## 关键组件

### 1. 模型类（Model）

**职责**：定义模型数据结构

**要求**：
- 可序列化为 JSON
- 包含唯一标识字段（通常为 `name` 或 `path`）
- 使用 Jackson 注解

**示例**：

```java
package com.ouroboros.mymodule.dev.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class MyModel {

    private String name;        // 唯一标识（fullName）
    private String title;       // 显示名称
    private String version;     // 版本号

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
```

---

### 2. 开发服务（DevelopService）

**职责**：实现模型的业务逻辑和生命周期管理

**继承**：`BaseDevelopService<M>`

**必须实现的方法**：

```java
package com.ouroboros.mymodule.dev.service;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.mymodule.dev.model.MyModel;
import org.springframework.stereotype.Service;

@Service
@com.ouroboros.modelmanage.annotation.DevelopModule("my-module")  // 模块名称标识
public class MyModuleDevService extends BaseDevelopService<MyModel> {

    static final String MODEL_FILE_DIRECTORY = "my-module";     // 文件存储目录
    static final String MODEL_FILE_EXTENSION = ".json";          // 文件扩展名

    /**
     * 模型文件存储目录（相对于应用根目录）
     */
    @Override
    protected String getModelFileDirectory() {
        return MODEL_FILE_DIRECTORY;
    }

    /**
     * 模型文件扩展名
     */
    @Override
    protected String getModelFileExtension() {
        return MODEL_FILE_EXTENSION;
    }

    /**
     * 是否在更新时自动提交
     * - true: 自动提交（适合简单模型）
     * - false: 手动提交（需要用户显式调用 commit()）
     */
    @Override
    protected boolean isAutoCommitWhenUpdate() {
        return false;  // 推荐手动提交
    }

    /**
     * 获取模型的唯一标识（fullName）
     */
    @Override
    protected String getFullName(MyModel model) {
        return model.getName();
    }

    /**
     * 模型显示名称（用于日志和错误消息）
     */
    @Override
    protected String getModelDisplayName() {
        return "My Module Model";
    }

    /**
     * 模型验证逻辑
     */
    @Override
    protected Try<Void> validateModel(MyModel model) {
        if (model == null) {
            return Try.failure(new BusinessException("Model cannot be null"));
        }

        if (StringUtils.isBlank(model.getName())) {
            return Try.failure(new BusinessException("Model name cannot be empty"));
        }

        // 其他验证逻辑...

        return Try.success(null);
    }
}
```

**可选实现的方法**：

```java
/**
 * 构建搜索结果映射器
 * 定义如何将 VCS 文件转换为搜索结果
 */
@Override
protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> {
        var model = buildModelFromFileContent(file.getContent());
        return model.map(m -> new ModelFileMetaWrapper<>(m, file));
    };
}

/**
 * 构建搜索过滤器
 * 定义搜索条件的过滤逻辑
 */
@Override
protected Predicate<ModelFileMetaWrapper<MyModel>> buildSearchFilter(
        Map<String, Object> searchParam) {

    var keyword = searchParam.getOrDefault("keyword", "").toString().toLowerCase();

    return wrapper -> {
        var model = wrapper.getModel();
        if (StringUtils.isBlank(keyword)) {
            return true;
        }

        var name = Optional.ofNullable(model.getName()).orElse("").toLowerCase();
        var title = Optional.ofNullable(model.getTitle()).orElse("").toLowerCase();

        return name.contains(keyword) || title.contains(keyword);
    };
}

/**
 * 创建时的关联文件
 * 返回需要同时创建的其他文件
 */
@Override
protected Map<String, Supplier<InputStream>> getAssociatedFilesForCreate(
        String fullName, MyModel model) {

    Map<String, Supplier<InputStream>> files = new HashMap<>();

    // 示例：创建一个关联的配置文件
    // files.put("my-module/" + fullName + ".config", () ->
    //     new ByteArrayInputStream("{}".getBytes()));

    return files;
}

/**
 * 删除时的关联文件
 * 返回需要同时删除的其他文件路径
 */
@Override
protected Set<String> getAssociatedFiles(MyModel model) {
    if (model == null) {
        return Collections.emptySet();
    }

    // 示例：删除关联的配置文件
    // String configFile = "my-module/" + model.getName() + ".config";
    // return Collections.singleton(configFile);

    return Collections.emptySet();
}

/**
 * 更新时的关联文件操作
 */
@Override
protected Map<String, Consumer<VCSAction.BatchAction>> getAssociatedFilesForUpdate(
        String fullName, MyModel model, MyModel oldModel) {

    // 返回需要在更新时执行的额外文件操作
    return Collections.emptyMap();
}

/**
 * 自定义搜索排序
 */
@Override
public List<?> search(Map<String, Object> searchParam) {
    var result = (List<ModelFileMetaWrapper<MyModel>>) super.search(searchParam);

    // 按名称排序
    return result.stream()
            .sorted(Comparator.comparing(o ->
                StringUtils.defaultIfBlank(o.getModel().getName(), "")))
            .collect(Collectors.toList());
}
```

---

### 3. VCS 文件检查器（VCSFileInspector）

**职责**：为版本控制系统提供文件元数据扩展

**实现**：通过 Java SPI 机制注册

**示例**：

```java
package com.ouroboros.mymodule.dev.inspector;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.mymodule.dev.model.MyModel;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import com.ouroboros.vcs.file.inspector.FileDescription;
import org.springframework.stereotype.Component;

@Component
public class MyModuleInspector {

    private static final String MODEL_FILE_DIRECTORY = "my-module";
    private static final String MODEL_FILE_EXTENSION = ".json";

    private static BaseDevelopService<MyModel> devService;

    public MyModuleInspector(BaseDevelopService<MyModel> devService) {
        MyModuleInspector.devService = devService;
    }

    /**
     * Proxy 实现用于 SPI 注册
     */
    public static class Proxy extends AbstractVCSFileInspector {

        @Override
        protected void postProcessFile(VCSFile file, FileDescription description) {
            // 提取模型名称（去除扩展名）
            String fileName = file.getFileName();
            String modelName = fileName.substring(0,
                fileName.length() - MODEL_FILE_EXTENSION.length());

            description.setName(modelName)
                    .setIcon("my-module-icon")
                    .setType("my-module")
                    .setTitle(modelName);

            // 尝试加载模型以获取更多元数据
            if (devService != null) {
                var model = devService.getOne(modelName).orElse(null);
                if (model != null) {
                    // 使用模型的 title 作为显示标题
                    String title = model.getTitle();
                    if (StringUtils.isNotEmpty(title)) {
                        description.setTitle(title);
                    }

                    // 添加额外信息
                    if (StringUtils.isNotBlank(model.getVersion())) {
                        description.addExtraInfo("version", model.getVersion());
                    }
                }
            }
        }

        @Override
        public boolean support(VCSFile file) {
            // 判断是否支持该文件
            return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
                    && file.getPath().endsWith(MODEL_FILE_EXTENSION);
        }
    }
}
```

**SPI 注册**：

创建文件 `src/main/resources/META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`：

```
com.ouroboros.mymodule.dev.inspector.MyModuleInspector$Proxy
```

---

### 4. 自动配置（AutoConfiguration）

**职责**：Spring Boot 自动装配

**示例**：

```java
package com.ouroboros.mymodule.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@ComponentScan
@Configuration
public class MyModuleDevAutoConfiguration {

    private static final Logger logger = LoggerFactory.getLogger(MyModuleDevAutoConfiguration.class);

    @PostConstruct
    void init() {
        logger.info("Ouroboros My Module Dev Auto Configuration loaded");
    }
}
```

**Spring Boot 注册**：

按[自动配置注册约定](../design/cross-cutting/spring-autoconfiguration.md)同时创建 imports 与 factories。

`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```text
com.ouroboros.mymodule.dev.MyModuleDevAutoConfiguration
```

`src/main/resources/META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ouroboros.mymodule.dev.MyModuleDevAutoConfiguration
```

---

## 实现步骤

### Step 1: 创建 Maven 模块

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ouroboros</groupId>
        <artifactId>ouroboros-parent</artifactId>
        <version>1.0.0-beta.2</version>
    </parent>

    <artifactId>my-module-dev</artifactId>
    <name>My Module Dev</name>
    <description>My Module development capabilities</description>

    <dependencies>
        <!-- 开发模型管理框架 -->
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>ouroboros-dev-model-manage-webapi-scaffold</artifactId>
        </dependency>

        <!-- 版本控制系统 -->
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>ouroboros-dev-version-control-system</artifactId>
        </dependency>

        <!-- 工具类 -->
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>ouroboros-util</artifactId>
        </dependency>

        <!-- 异常处理 -->
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>common-exception</artifactId>
        </dependency>

        <!-- Spring Boot -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Jackson -->
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>

        <!-- Vavr -->
        <dependency>
            <groupId>io.vavr</groupId>
            <artifactId>vavr</artifactId>
        </dependency>

        <!-- Apache Commons -->
        <dependency>
            <groupId>org.apache.commons</groupId>
            <artifactId>commons-lang3</artifactId>
        </dependency>

        <!-- Logging -->
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
    </dependencies>
</project>
```

### Step 2: 定义模型类

创建 `src/main/java/com/ouroboros/mymodule/dev/model/MyModel.java`

（参考上面的示例代码）

### Step 3: 实现开发服务

创建 `src/main/java/com/ouroboros/mymodule/dev/service/MyModuleDevService.java`

（参考上面的示例代码）

### Step 4: 实现文件检查器

创建 `src/main/java/com/ouroboros/mymodule/dev/inspector/MyModuleInspector.java`

（参考上面的示例代码）

### Step 5: 创建自动配置

创建 `src/main/java/com/ouroboros/mymodule/dev/MyModuleDevAutoConfiguration.java`

（参考上面的示例代码）

### Step 6: 配置 Spring Boot 和 SPI

**spring.factories**：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ouroboros.mymodule.dev.MyModuleDevAutoConfiguration
```

**VCSFileInspector SPI**：

创建 `META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`：

```
com.ouroboros.mymodule.dev.inspector.MyModuleInspector$Proxy
```

### Step 7: 添加菜单模型

创建 `src/main/metadata/AllModels.MyModule.dev-menu.json`：

```json
{
  "fullName": "AllModels.MyModule",
  "url": "/dev/{appName}/my-module",
  "icon": "icon-my-module",
  "title": "My Module",
  "stage": "dev",
  "order": 100
}
```

### Step 8: 创建 UI 页面模板

**列表页** - `META-INF/ouroboros/ui-model/dev/{appName}/my-module.json`：

```json
{
  "type": "page",
  "title": "My Module",
  "body": {
    "type": "crud",
    "syncLocation": false,
    "api": {
      "method": "get",
      "url": "/api/dev/${appName}/my-module"
    },
    "filter": {
      "body": [
        {
          "type": "input-text",
          "name": "keyword",
          "label": "Keyword",
          "placeholder": "Search by name or title",
          "clearable": true
        }
      ]
    },
    "headerToolbar": [
      "filter-toggler",
      {
        "type": "button",
        "label": "Create",
        "level": "primary",
        "actionType": "link",
        "link": "/dev/${appName}/my-module/add"
      },
      "reload"
    ],
    "columns": [
      {
        "name": "model.name",
        "label": "Name",
        "searchable": true
      },
      {
        "name": "model.title",
        "label": "Title"
      },
      {
        "name": "model.version",
        "label": "Version"
      },
      {
        "name": "lastModifiedTime",
        "label": "Last Modified",
        "type": "datetime"
      },
      {
        "type": "operation",
        "label": "Operations",
        "buttons": [
          {
            "type": "button",
            "label": "Edit",
            "level": "link",
            "actionType": "link",
            "link": "/dev/${appName}/my-module/${model.name}"
          },
          {
            "type": "button",
            "label": "Delete",
            "level": "link",
            "className": "text-danger",
            "actionType": "ajax",
            "confirmText": "Are you sure to delete this item?",
            "api": {
              "method": "delete",
              "url": "/api/dev/${appName}/my-module/${model.name}"
            }
          }
        ]
      }
    ]
  }
}
```

**详情页** - `META-INF/ouroboros/ui-model/dev/{appName}/my-module/{fullName}.json`：

```json
{
  "type": "page",
  "title": "${fullName}",
  "body": {
    "type": "service",
    "api": {
      "method": "get",
      "url": "/api/dev/${appName}/my-module/${fullName}/status"
    },
    "body": [
      {
        "type": "form",
        "mode": "horizontal",
        "api": {
          "method": "put",
          "url": "/api/dev/${appName}/my-module/${fullName}"
        },
        "actions": [
          {
            "type": "button",
            "label": "Save",
            "level": "primary",
            "actionType": "submit"
          },
          {
            "type": "button",
            "label": "Commit",
            "level": "success",
            "actionType": "ajax",
            "confirmText": "Commit changes?",
            "api": {
              "method": "post",
              "url": "/api/dev/${appName}/my-module/${fullName}/commit",
              "data": {
                "commitMsg": "Update model"
              }
            }
          },
          {
            "type": "button",
            "label": "Rollback",
            "level": "warning",
            "actionType": "ajax",
            "confirmText": "Rollback uncommitted changes?",
            "api": {
              "method": "post",
              "url": "/api/dev/${appName}/my-module/${fullName}/rollback"
            }
          }
        ],
        "body": [
          {
            "type": "input-text",
            "name": "model.name",
            "label": "Name",
            "required": true,
            "disabled": true
          },
          {
            "type": "input-text",
            "name": "model.title",
            "label": "Title",
            "required": true
          },
          {
            "type": "input-text",
            "name": "model.version",
            "label": "Version"
          }
        ]
      }
    ]
  }
}
```

---

## 配置文件

### 1. 自动配置注册

位置：`src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```text
com.ouroboros.mymodule.dev.MyModuleDevAutoConfiguration
```

兼容声明：`src/main/resources/META-INF/spring.factories`

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ouroboros.mymodule.dev.MyModuleDevAutoConfiguration
```

### 2. SPI 服务配置

位置：`src/main/resources/META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`

```
com.ouroboros.mymodule.dev.inspector.MyModuleInspector$Proxy
```

### 3. 菜单模型

位置：`src/main/metadata/AllModels.{Module}.dev-menu.json`

```json
{
  "fullName": "AllModels.MyModule",
  "url": "/dev/{appName}/my-module",
  "icon": "icon-module",
  "title": "My Module",
  "stage": "dev",
  "order": 100
}
```

**字段说明**：
- `fullName`：菜单唯一标识
- `url`：菜单链接路径
- `icon`：菜单图标
- `title`：菜单显示名称
- `stage`：阶段标识（通常为 "dev"）
- `order`：排序顺序（数字越小越靠前）

### 4. UI 页面模板

#### 列表页

位置：`src/main/resources/META-INF/ouroboros/ui-model/dev/{appName}/{module-name}.json`

使用 Amis CRUD 组件实现。

#### 详情页

位置：`src/main/resources/META-INF/ouroboros/ui-model/dev/{appName}/{module-name}/{fullName}.json`

使用 Amis Form 组件实现。

---

## API端点

所有开发模块通过 `ModelDevelopController` 自动提供统一的 REST API：

### 基础 CRUD

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dev/{appName}/{moduleName}` | 搜索/列出所有模型 |
| GET | `/api/dev/{appName}/{moduleName}/{fullName}` | 获取单个模型 |
| POST | `/api/dev/{appName}/{moduleName}` | 创建模型 |
| PUT | `/api/dev/{appName}/{moduleName}/{fullName}` | 更新模型 |
| DELETE | `/api/dev/{appName}/{moduleName}/{fullName}` | 删除模型 |

### 版本控制

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/lock` | 锁定模型 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/release-lock` | 解锁模型 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/commit` | 提交变更 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/rollback` | 回滚变更 |

### 查询

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dev/{appName}/{moduleName}/{fullName}/status` | 获取文件状态 |
| GET | `/api/dev/{appName}/{moduleName}/{fullName}/change-log` | 获取变更历史 |

### 参数说明

- `{appName}`：应用名称（从路径变量获取，自动设置到 ThreadLocal）
- `{moduleName}`：模块名称（对应 `@DevelopModule` 注解的值）
- `{fullName}`：模型唯一标识

### 请求体示例

**创建模型**：

```json
POST /api/dev/myapp/my-module

{
  "name": "example",
  "title": "Example Model",
  "version": "1.0"
}
```

**更新模型**：

```json
PUT /api/dev/myapp/my-module/example

{
  "name": "example",
  "title": "Updated Title",
  "version": "1.1"
}
```

**提交变更**：

```json
POST /api/dev/myapp/my-module/example/commit

{
  "commitMsg": "Update example model"
}
```

### 响应体示例

**搜索结果**：

```json
[
  {
    "model": {
      "name": "example",
      "title": "Example Model",
      "version": "1.0"
    },
    "creationTime": "2025-12-08T10:00:00",
    "lastModifiedTime": "2025-12-08T15:30:00",
    "lastCommittedTime": "2025-12-08T15:00:00",
    "isLocked": false,
    "lockUser": null,
    "isChanged": true,
    "versionState": "CHANGED"
  }
]
```

**状态查询**：

```json
{
  "model": { ... },
  "isLocked": false,
  "lockUser": null,
  "lockState": "UNLOCKED",
  "versionState": "UNCHANGED",
  "isChanged": false,
  "creationTime": "2025-12-08T10:00:00",
  "lastModifiedTime": "2025-12-08T15:30:00",
  "lastCommittedTime": "2025-12-08T15:00:00"
}
```

---

## 最佳实践

### 1. 模型设计

**DO**：
- ✅ 使用 `@JsonIgnoreProperties(ignoreUnknown = true)` 注解
- ✅ 提供默认构造函数
- ✅ 使用标准的 getter/setter 方法
- ✅ 包含有意义的字段注释
- ✅ 为集合字段提供默认值（如 `new HashMap<>()`）

**DON'T**：
- ❌ 不要在模型类中包含业务逻辑
- ❌ 不要使用循环引用
- ❌ 不要使用不可序列化的字段类型

### 2. 服务实现

**DO**：
- ✅ 重写 `validateModel()` 进行数据验证
- ✅ 使用 `Try` 类型进行错误处理
- ✅ 提供清晰的错误消息
- ✅ 实现自定义的搜索过滤逻辑
- ✅ 发布事件以支持模块间通信

**DON'T**：
- ❌ 不要在 Service 层直接操作文件系统（使用 VCSManager）
- ❌ 不要忽略并发控制（锁机制）
- ❌ 不要抛出原始异常（使用 BusinessException）

### 3. 文件组织

**DO**：
- ✅ 使用有意义的目录名称
- ✅ 遵循统一的文件命名约定
- ✅ 为关联文件使用明确的命名规则（如 `.resource.json`）

**DON'T**：
- ❌ 不要混合不同类型的文件
- ❌ 不要使用特殊字符或空格

### 4. 版本控制

**DO**：
- ✅ 提供有意义的提交消息
- ✅ 在修改前先锁定文件
- ✅ 及时解锁不再编辑的文件
- ✅ 使用批量操作减少提交次数

**DON'T**：
- ❌ 不要频繁提交小改动
- ❌ 不要强制解锁他人的文件（除非必要）
- ❌ 不要忽略未提交的变更

### 5. UI 设计

**DO**：
- ✅ 提供清晰的操作按钮
- ✅ 使用确认对话框防止误操作
- ✅ 显示文件状态（锁定、变更等）
- ✅ 提供搜索和过滤功能

**DON'T**：
- ❌ 不要隐藏重要的操作（如提交、回滚）
- ❌ 不要在未保存时允许导航
- ❌ 不要忽略错误提示

---

## 示例参考

### 现有实现

项目中已有的开发模块可作为参考：

1. **ouroboros-ui-model-schema-dev**
   - 位置：`core/ouroboros-ui-model/ouroboros-ui-model-schema-dev`
   - 管理对象：UI Schema（页面定义）
   - 特点：完整的 CRUD + 版本控制 + 预览功能

2. **ouroboros-resource-dev**
   - 位置：`core/ouroboros-resource/ouroboros-resource-dev`
   - 管理对象：LowCode 资源文件
   - 特点：元数据 + 实际文件分离存储，文件上传/下载

3. **ouroboros-business-model-dev**（如有）
   - 管理对象：业务模型定义
   - 特点：复杂的模型验证和关联关系

### 代码参考

**完整示例**：

查看 `ouroboros-ui-model-schema-dev` 模块的完整实现：

```bash
tree core/ouroboros-ui-model/ouroboros-ui-model-schema-dev
```

**关键文件**：

1. 模型：`SchemaNodeWrapper.java`
2. 服务：`UISchemaDevService.java`
3. 检查器：`UISchemaInspector.java`
4. 配置：`UISchemaDevAutoConfiguration.java`

---

## 总结

遵循本文档的设计模式，可以快速实现一个标准的低代码开发模块，享受以下优势：

1. **统一接口**：自动获得 REST API 和 UI 页面
2. **版本控制**：内置 Git 支持，完整的历史和回滚能力
3. **并发安全**：文件锁机制防止冲突
4. **事件驱动**：支持模块间解耦通信
5. **可扩展**：易于添加自定义逻辑和验证
6. **开发友好**：文件系统 + JSON 便于调试和迁移

### 快速检查清单

实现新开发模块时，请确保：

- [ ] 创建模型类并实现 JSON 序列化
- [ ] 实现 `DevelopService` 并继承 `BaseDevelopService`
- [ ] 使用 `@DevelopModule` 注解标识模块名称
- [ ] 实现 `VCSFileInspector` 并注册 SPI
- [ ] 创建自动配置类并同步注册到 `AutoConfiguration.imports` 与 `spring.factories`
- [ ] 添加菜单模型 JSON
- [ ] 创建 UI 页面模板（列表页和详情页）
- [ ] 更新 pom.xml 添加必要依赖
- [ ] 编写单元测试（推荐）
- [ ] 更新项目文档

---

## 高级模式

基于 `ouroboros-business-model-dev` 和 `ouroboros-logicflow-dev` 的分析，以下是复杂模块的高级实现模式。

### 1. 自定义 Controller

对于复杂业务逻辑，可以添加自定义 Controller，而不是完全依赖 `ModelDevelopController`。

**场景**：
- 需要额外的业务端点（如克隆、移动、重命名）
- 需要辅助功能端点（如字段导入、关系查询）
- 需要提供特定资源（如节点定义）

**示例**（业务模型）：

```java
@RestController
@RequestMapping("/api/dev/{appName}/business-model")
public class BusinessModelController {

  @Autowired
  private BusinessModelDevService service;

  /**
   * 获取初始化表单模板
   */
  @GetMapping("/init-form")
  public WebApiResult<BusinessModelMeta> getInitForm() {
    // 从资源文件加载默认模板
    var template = loadTemplate("default.json");
    return WebApiResult.ok(template);
  }

  /**
   * 移动模型到不同命名空间
   */
  @PostMapping("/{fullName}/move")
  public WebApiResult<Void> move(
      @PathVariable String fullName,
      @RequestBody MoveRequest request) {

    var result = service.move(fullName, request.getNewFullName(), request.getMsg());
    return result.isSuccess()
        ? WebApiResult.ok()
        : WebApiResult.error(result.getCause().getMessage());
  }

  /**
   * 获取引用关系
   */
  @GetMapping("/{fullName}/reference-relations")
  public WebApiResult<List<ReferenceRelationVO>> getReferenceRelations(
      @PathVariable String fullName) {

    var relations = service.getReferenceRelations(fullName);
    return WebApiResult.ok(relations);
  }

  /**
   * 克隆 UI Schema
   */
  @PostMapping("/{fullName}/clone-ui-schema")
  public WebApiResult<Void> cloneUISchema(
      @PathVariable String fullName,
      @RequestBody CloneRequest request) {

    var result = service.cloneUISchema(fullName, request.getTargetName());
    return result.isSuccess()
        ? WebApiResult.ok()
        : WebApiResult.error(result.getCause().getMessage());
  }
}
```

**辅助 Controller 示例**（业务模型助手）：

```java
@RestController
@RequestMapping("/api/dev/{appName}/business-model-helper")
public class BusinessModelHelperController {

  /**
   * 从 Excel 导入字段
   */
  @PostMapping("/{fullName}/import-fields")
  public WebApiResult<List<Field>> importFields(
      @PathVariable String fullName,
      @RequestParam("file") MultipartFile file,
      @RequestParam("mode") String mode) {

    var result = service.importFieldsFromExcel(file, mode);
    return result.isSuccess()
        ? WebApiResult.ok(result.get())
        : WebApiResult.error(result.getCause().getMessage());
  }

  /**
   * 获取可导出字段树（递归关系）
   */
  @GetMapping("/{fullName}/exportable-fields")
  public WebApiResult<FieldTree> getExportableFields(
      @PathVariable String fullName) {

    var tree = service.buildExportableFieldTree(fullName);
    return WebApiResult.ok(tree);
  }
}
```

---

### 2. SPI 扩展模式

使用 Java SPI 允许其他模块扩展功能。

**场景**：
- 节点定义（LogicFlow）
- 字段类型（Field Type）
- 模板提供（Template Provider）
- 插件系统

**示例**（节点定义提供者）：

**定义接口**：

```java
public interface LogicFlowNodeDefineProvider {
    List<NodeDefine> getNodeDefines();
}
```

**实现提供者**：

```java
public class JarLogicFlowNodeDefineProvider implements LogicFlowNodeDefineProvider {

  @Override
  public List<NodeDefine> getNodeDefines() {
    return ResourceUtil
        .getResourceNameAndContentListFromDirectory(
            "META-INF/ouroboros/metadata/",
            Pattern.compile(".*\\.flow-node\\.json"))
        .stream()
        .map(tuple -> Json.tryToBean(NodeDefine.class, tuple._2()))
        .filter(Try::isSuccess)
        .map(Try::get)
        .collect(Collectors.toList());
  }
}
```

**SPI 注册**：

创建 `META-INF/services/com.ouroboros.logicflow.authoring.provider.LogicFlowNodeDefineProvider`：

```
com.ouroboros.logicflow.authoring.provider.JarLogicFlowNodeDefineProvider
```

**消费 SPI**：

```java
@GetMapping("/node-defines")
public WebApiResult<List<NodeDefine>> getNodeDefines() {
  var nodeDefines = SPI.getCachedSortedServiceStream(LogicFlowNodeDefineProvider.class)
      .map(LogicFlowNodeDefineProvider::getNodeDefines)
      .flatMap(Collection::stream)
      .sorted(Comparator.comparingInt(NodeDefine::getOrder))
      .collect(Collectors.toList());

  return WebApiResult.ok(nodeDefines);
}
```

**其他模块贡献节点**：

在任何 JAR 包的 `src/main/metadata/{NodeType}.flow-node.json` 中添加节点定义 JSON；构建后会进入 `META-INF/ouroboros/metadata/`：

```json
{
  "type": "custom-node",
  "label": "Custom Node",
  "icon": "icon-custom",
  "group": "custom",
  "order": 100,
  "properties": [
    {
      "name": "config",
      "type": "text",
      "label": "Configuration"
    }
  ]
}
```

---

### 3. UI Schema Provider

对于复杂的动态 UI Schema，可以实现 `UISchemaProvider` SPI。

**场景**：
- 动态生成编辑器 Schema
- 根据权限或配置动态调整 UI
- 复杂的表单构建逻辑

**示例**：

```java
@Component
public class BusinessModelEditorUISchemaProvider implements UISchemaProvider {

  private static final String MODEL_SCHEMA_PATH = "/dev/{appName}/business-model/editor";

  @Override
  public Optional<Object> getUISchema(String path) {
    if (!path.startsWith(MODEL_SCHEMA_PATH)) {
      return Optional.empty();
    }

    // 动态生成编辑器 Schema
    var schema = new DefaultBusinessModelEditorSchemaGenerator().generate();
    return Optional.of(schema);
  }
}
```

**SPI 注册**：

创建 `META-INF/services/com.ouroboros.uischema.UISchemaProvider`：

```
com.ouroboros.dev.businessmodel.ui.provider.BusinessModelEditorUISchemaProvider
```

---

### 4. 微前端集成

对于复杂的编辑器（如可视化流程编辑器），使用微前端架构。

**场景**：
- 流程设计器
- 表单设计器
- 图形化编辑器

**配置微前端**：

创建 `META-INF/micro-web/logicFlowModelEditor.json`：

```json
{
  "name": "microLogicflow",
  "type": "library",
  "publicPath": "/micro-logicflow-editor/",
  "packageName": "logicFlowEditor.zip"
}
```

**打包前端应用**：

1. 构建前端应用（React/Vue）
2. 打包为 zip 文件
3. 放置到 `META-INF/micro-web/package/logicFlowEditor.zip`

**在 UI Schema 中使用**：

```json
{
  "type": "form",
  "body": [
    {
      "type": "logic-flow-editor",
      "label": false,
      "name": "flowModel",
      "className": "h-full",
      "nodeDefineApi": "/api/dev/${appName}/logicflow/node-define"
    }
  ]
}
```

**自定义组件注册**（前端）：

```javascript
import { registerRenderer } from 'sdk';
import LogicFlowEditor from './LogicFlowEditor';

registerRenderer({
  type: 'logic-flow-editor',
  component: LogicFlowEditor
});
```

---

### 5. 多个 Inspector 管理

对于复杂模块，可以注册多个 VCS 文件检查器。

**场景**：
- 主模型文件 + 配置文件
- 模块树文件
- 资源文件 + 元数据文件

**示例**（业务模型 + 模块树）：

**BusinessModelInspector**：

```java
public class BusinessModelInspector {
  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    public boolean support(VCSFile file) {
      return file.getPath().startsWith("business-model/")
          && file.getPath().endsWith(".business.json");
    }

    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      // 处理业务模型文件...
    }
  }
}
```

**ModulesInspector**：

```java
public class ModulesInspector {
  public static class Proxy extends AbstractVCSFileInspector {
    @Override
    public boolean support(VCSFile file) {
      return file.getPath().equals("business-model/modules.json");
    }

    @Override
    protected void postProcessFile(VCSFile file, FileDescription description) {
      description.setIcon("modules")
          .setType("modules")
          .setTitle("模块树配置");
    }
  }
}
```

**SPI 注册**：

```
com.ouroboros.dev.businessmodel.inspector.BusinessModelInspector$Proxy
com.ouroboros.dev.businessmodel.inspector.ModulesInspector$Proxy
```

---

### 6. 关系和引用管理

对于有关联关系的模型，实现引用检查和级联操作。

**场景**：
- 业务模型引用其他模型的字段
- UI Schema 引用业务模型
- 流程引用业务模型的操作

**引用关系数据结构**：

```java
public class ReferenceRelationVO {
  private String sourceType;        // 引用源类型（如 "ui-schema"）
  private String sourceName;        // 引用源名称
  private String targetType;        // 被引用类型（如 "business-model"）
  private String targetName;        // 被引用名称
  private String referenceType;     // 引用类型（如 "field", "api"）
  private String referencePath;     // 引用路径（如 "body.fields[0]"）
}
```

**服务方法**：

```java
public class BusinessModelDevService extends BaseDevelopService<BusinessModelMeta> {

  /**
   * 获取引用此模型的其他模型
   */
  public List<ReferenceRelationVO> getReferenceRelations(String fullName) {
    List<ReferenceRelationVO> relations = new ArrayList<>();

    // 查找 UI Schema 中的引用
    relations.addAll(findUISchemaReferences(fullName));

    // 查找其他业务模型中的引用
    relations.addAll(findBusinessModelReferences(fullName));

    // 查找流程中的引用
    relations.addAll(findLogicFlowReferences(fullName));

    return relations;
  }

  /**
   * 移动模型前检查引用
   */
  public Try<Void> move(String oldFullName, String newFullName, String msg) {
    // 检查是否被引用
    var references = getReferenceRelations(oldFullName);
    if (!references.isEmpty()) {
      return Try.failure(new BusinessException(
          "Cannot move: model is referenced by " + references.size() + " other models"));
    }

    // 执行移动
    var oldPath = buildModelFilePath(oldFullName);
    var newPath = buildModelFilePath(newFullName);

    var result = getVcsManager()
        .batchOperation()
        .autoLock()
        .unlockWhenCommit()
        .move(oldPath, newPath)
        .commit(msg);

    return result.map(v -> null);
  }
}
```

---

### 7. 初始化模板

为新建模型提供默认模板。

**模板文件**：

创建 `META-INF/business-model-meta-templates/default.json`：

```json
{
  "version": "1.0",
  "modelType": "default",
  "name": "",
  "title": "",
  "isEnabled": false,
  "fields": [],
  "features": {
    "isEnabledAuditFields": true,
    "isEnabledSoftDelete": true,
    "isEnabledChangeLog": false
  },
  "apis": {
    "search": { "isEnabled": true },
    "getOne": { "isEnabled": true },
    "add": { "isEnabled": true },
    "update": { "isEnabled": true },
    "delete": { "isEnabled": true }
  },
  "views": {
    "index": { "isEnabled": true },
    "add": { "isEnabled": true },
    "edit": { "isEnabled": true },
    "detail": { "isEnabled": true }
  }
}
```

**加载模板**：

```java
@GetMapping("/init-form")
public WebApiResult<BusinessModelMeta> getInitForm() {
  try (InputStream is = getClass().getResourceAsStream(
      "/META-INF/business-model-meta-templates/default.json")) {

    var template = Json.tryToBean(BusinessModelMeta.class, is);
    return template.isSuccess()
        ? WebApiResult.ok(template.get())
        : WebApiResult.error("Failed to load template");

  } catch (IOException e) {
    return WebApiResult.error(e.getMessage());
  }
}
```

---

### 8. 递归关系处理

对于有递归关系的模型（如组织架构、分类树），需要防止循环引用。

**场景**：
- 业务模型的关系字段
- 组织架构的上下级
- 分类的父子关系

**递归遍历实现**：

```java
public FieldTree buildRelationTree(String modelName, String fieldPath, Set<String> visited) {
  // 防止循环引用
  String key = modelName + "." + fieldPath;
  if (visited.contains(key)) {
    return FieldTree.circular(key);
  }
  visited.add(key);

  // 加载模型
  var model = getOne(modelName).orElse(null);
  if (model == null) {
    return FieldTree.notFound(modelName);
  }

  // 查找字段
  var field = findField(model, fieldPath);
  if (field == null) {
    return FieldTree.notFound(fieldPath);
  }

  // 如果是关系字段，递归构建
  if (field.getType().equals("relation")) {
    String targetModel = field.getRelationTarget();
    var children = field.getRelationFields().stream()
        .map(f -> buildRelationTree(targetModel, f, new HashSet<>(visited)))
        .collect(Collectors.toList());

    return FieldTree.node(field.getName(), children);
  }

  // 普通字段
  return FieldTree.leaf(field.getName());
}
```

---

### 9. 复杂依赖管理

对于功能丰富的模块，需要引入额外的依赖。

**常见依赖**：

```xml
<!-- Excel 导入导出 -->
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-util-excel</artifactId>
</dependency>

<!-- 拼音转换（用于自动生成标识符） -->
<dependency>
  <groupId>io.github.biezhi</groupId>
  <artifactId>TinyPinyin</artifactId>
</dependency>

<!-- 字段类型库 -->
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-field-type-dev</artifactId>
</dependency>

<!-- 应用模块开发支持 -->
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-app-module-model-dev</artifactId>
</dependency>

<!-- UI Schema 依赖（如果需要动态生成） -->
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-ui-model-schema</artifactId>
</dependency>
```

---

## 模块复杂度对比

根据现有实现，开发模块可分为三个复杂度级别：

### 简单模块

**特征**：
- 单一模型类
- 标准 CRUD 操作
- 基本搜索过滤
- 无额外 Controller
- 1 个 Inspector

**示例**：
- `ouroboros-resource-dev`（资源元数据管理）
- `ouroboros-ui-model-schema-dev`（UI Schema 管理）

**文件数量**：
- Java 类：3-5 个
- UI 资源：5-10 个

### 中等模块

**特征**：
- 单一主模型 + 辅助模型
- 自定义 Controller 提供额外端点
- SPI 扩展点
- 微前端集成
- 2-3 个 Inspector

**示例**：
- `ouroboros-logicflow-dev`（逻辑流模型 + 节点定义）

**文件数量**：
- Java 类：6-10 个
- UI 资源：10-20 个
- 微前端包：1 个

### 复杂模块

**特征**：
- 多个关联模型
- 2+ 个 Controller（主 + 辅助）
- 模块树管理
- 引用关系检查
- 初始化模板
- UI Schema Provider
- 多个 SPI 实现
- 3+ 个 Inspector

**示例**：
- `ouroboros-business-model-dev`（业务模型 + 模块树 + 关联模型包）

**文件数量**：
- Java 类：10-20 个
- UI 资源：40-50 个
- 模板文件：1-5 个

---

## 总结补充

### 选择合适的复杂度

| 需求 | 推荐级别 | 参考模块 |
|------|---------|---------|
| 简单配置管理 | 简单 | resource-dev |
| 单一元数据管理 | 简单 | ui-schema-dev |
| 可视化编辑器 | 中等 | logicflow-dev |
| 复杂业务模型 | 复杂 | business-model-dev |

### 渐进式开发

建议采用渐进式开发策略：

1. **第一阶段**：实现基本的 CRUD 和版本控制（简单模块）
2. **第二阶段**：添加自定义 Controller 和 SPI 扩展（中等模块）
3. **第三阶段**：实现模块树、引用管理、微前端（复杂模块）

### 架构演进

```
简单模块
  ↓
+ 自定义 Controller
+ SPI 扩展
  ↓
中等模块
  ↓
+ 模块树管理
+ 引用关系检查
+ UI Schema Provider
+ 多 Inspector
  ↓
复杂模块
```

---

**版本**：2.0.0
**最后更新**：2025-12-09
**维护者**：Ouroboros Team

---

## 附录：完整模块对比表

| 特性 | resource-dev | ui-schema-dev | logicflow-dev | business-model-dev |
|------|-------------|---------------|---------------|-------------------|
| **复杂度** | 简单 | 简单 | 中等 | 复杂 |
| **模型类** | ResourceModel | SchemaNodeWrapper | FlowModelMeta | BusinessModelMeta |
| **文件目录** | lowcode-resources | ui-schema | logic-flow | business-model |
| **文件后缀** | .resource.json | .ui-schema.json | .logicflow.json | .business.json |
| **自动提交** | false | false | false | true |
| **自定义Controller** | 无 | 无 | 2 个 | 2 个 |
| **SPI扩展** | 否 | 否 | 是（节点定义） | 否 |
| **微前端** | 否 | 否 | 是（编辑器） | 否 |
| **Inspector数量** | 1 | 1 | 1 | 2 |
| **模块树** | 否 | 否 | 否 | 是 |
| **引用管理** | 否 | 否 | 否 | 是 |
| **初始化模板** | 否 | 否 | 否 | 是 |
| **UI Schema Provider** | 否 | 否 | 否 | 是 |
| **特殊功能** | 文件上传/下载 | Schema 预览 | 节点定义、可视化编辑 | 字段导入、模型克隆、关系递归 |
| **Java类数** | 4 | 3 | 8 | 15+ |
| **UI资源数** | 2 | 5 | 5 | 46+ |
