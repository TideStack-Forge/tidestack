# 开发模块快速参考

> 本文档提供 Ouroboros 开发模块实现的通用快速参考，适合作为 AI 助手与平台开发者的高频查阅材料。完整文档参见 `docs/guides/development-module-guide.md`。

## 核心原则

1. **文件驱动**：模型存储为 JSON 文件，不使用数据库
2. **版本控制**：基于 Git 的 VCS 管理
3. **统一接口**：继承 `BaseDevelopService<M>` 获得标准功能
4. **事件驱动**：发布 CreateModelEvent、UpdateModelEvent、DeleteModelEvent
5. **SPI 机制**：通过 Java SPI 实现插件式扩展

## 模块结构速查

```
{module}-dev/
├── pom.xml
└── src/main/
    ├── java/com/ouroboros/{module}/dev/
    │   ├── model/{Model}.java                          # 模型类
    │   ├── service/{Model}DevService.java              # 服务实现
    │   ├── inspector/{Model}Inspector.java             # VCS 检查器
    │   └── {Module}DevAutoConfiguration.java           # 自动配置
    └── resources/META-INF/
        ├── spring.factories                            # Spring Boot 配置
        ├── services/com.ouroboros.vcs.file.inspector.VCSFileInspector
        ├── ouroboros/dev-menu-model/*.json
        └── ouroboros/ui-model/dev/{appName}/*.json
```

## 必需依赖

```xml
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-dev-model-manage-webapi-scaffold</artifactId>
</dependency>
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-dev-version-control-system</artifactId>
</dependency>
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-util</artifactId>
</dependency>
<dependency>
    <groupId>com.ouroboros</groupId>
    <artifactId>common-exception</artifactId>
</dependency>
<dependency>
    <groupId>io.vavr</groupId>
    <artifactId>vavr</artifactId>
</dependency>
```

## 1. 模型类模板

```java
package com.ouroboros.{module}.dev.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class {Model}Model {
    private String name;        // 唯一标识（fullName）
    private String title;       // 显示名称
    // 其他字段...

    // Getters and Setters
}
```

## 2. 服务类模板（简单模块）

```java
package com.ouroboros.{module}.dev.service;

import com.ouroboros.modelmanage.base.BaseDevelopService;
import org.springframework.stereotype.Service;

@Service
@com.ouroboros.modelmanage.annotation.DevelopModule("{module-name}")
public class {Model}DevService extends BaseDevelopService<{Model}Model> {

    static final String MODEL_FILE_DIRECTORY = "{module-name}";
    static final String MODEL_FILE_EXTENSION = ".json";

    @Override
    protected String getModelFileDirectory() {
        return MODEL_FILE_DIRECTORY;
    }

    @Override
    protected String getModelFileExtension() {
        return MODEL_FILE_EXTENSION;
    }

    @Override
    protected boolean isAutoCommitWhenUpdate() {
        return false;  // false = 手动提交，true = 自动提交
    }

    @Override
    protected String getFullName({Model}Model model) {
        return model.getName();
    }

    @Override
    protected String getModelDisplayName() {
        return "{Model Display Name}";
    }

    @Override
    protected Try<Void> validateModel({Model}Model model) {
        if (model == null || StringUtils.isBlank(model.getName())) {
            return Try.failure(new BusinessException("Invalid model"));
        }
        return Try.success(null);
    }

    @Override
    protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
        return file -> {
            var model = buildModelFromFileContent(file.getContent());
            return model.map(m -> new ModelFileMetaWrapper<>(m, file));
        };
    }

    @Override
    protected Predicate<ModelFileMetaWrapper<{Model}Model>> buildSearchFilter(
            Map<String, Object> searchParam) {
        var keyword = searchParam.getOrDefault("keyword", "").toString().toLowerCase();
        return wrapper -> {
            if (StringUtils.isBlank(keyword)) return true;
            var model = wrapper.getModel();
            return model.getName().toLowerCase().contains(keyword);
        };
    }
}
```

## 3. Inspector 模板

```java
package com.ouroboros.{module}.dev.inspector;

import com.ouroboros.vcs.file.inspector.AbstractVCSFileInspector;
import org.springframework.stereotype.Component;

@Component
public class {Model}Inspector {
    private static final String MODEL_FILE_DIRECTORY = "{module-name}";
    private static final String MODEL_FILE_EXTENSION = ".json";
    private static BaseDevelopService<{Model}Model> devService;

    public {Model}Inspector(BaseDevelopService<{Model}Model> devService) {
        {Model}Inspector.devService = devService;
    }

    public static class Proxy extends AbstractVCSFileInspector {
        @Override
        protected void postProcessFile(VCSFile file, FileDescription description) {
            String modelName = file.getFileName()
                .substring(0, file.getFileName().length() - MODEL_FILE_EXTENSION.length());

            description.setName(modelName)
                .setIcon("{module-icon}")
                .setType("{module-type}")
                .setTitle(modelName);

            if (devService != null) {
                devService.getOne(modelName).ifPresent(model -> {
                    description.setTitle(model.getTitle());
                });
            }
        }

        @Override
        public boolean support(VCSFile file) {
            return file.getPath().startsWith(MODEL_FILE_DIRECTORY)
                && file.getPath().endsWith(MODEL_FILE_EXTENSION);
        }
    }
}
```

## 4. 自动配置模板

```java
package com.ouroboros.{module}.dev;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@ComponentScan
@Configuration
public class {Module}DevAutoConfiguration {
    private static final Logger logger = LoggerFactory.getLogger({Module}DevAutoConfiguration.class);

    @PostConstruct
    void init() {
        logger.info("Ouroboros {Module} Dev Auto Configuration loaded");
    }
}
```

## 5. 自动配置注册

按[共同注册约定](../../docs/design/cross-cutting/spring-autoconfiguration.md)维护两份资源。

`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`：

```text
com.ouroboros.{module}.dev.{Module}DevAutoConfiguration
```

`META-INF/spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.ouroboros.{module}.dev.{Module}DevAutoConfiguration
```

## 6. SPI 注册

文件：`META-INF/services/com.ouroboros.vcs.file.inspector.VCSFileInspector`

```
com.ouroboros.{module}.dev.inspector.{Model}Inspector$Proxy
```

## 7. 菜单配置

文件：`src/main/metadata/AllModels.{Module}.dev-menu.json`

```json
{
  "fullName": "AllModels.{Module}",
  "url": "/dev/{appName}/{module-name}",
  "icon": "icon-{module}",
  "title": "{Module Display Name}",
  "stage": "dev",
  "order": 100
}
```

## 8. UI Schema 列表页

文件：`META-INF/ouroboros/ui-model/dev/{appName}/{module-name}.json`

```json
{
  "type": "page",
  "title": "{Module Display Name}",
  "body": {
    "type": "crud",
    "api": "/api/dev/${appName}/{module-name}",
    "filter": {
      "body": [
        {"type": "input-text", "name": "keyword", "label": "Keyword", "clearable": true}
      ]
    },
    "headerToolbar": [
      "filter-toggler",
      {"type": "button", "label": "Create", "level": "primary", "actionType": "link", "link": "/dev/${appName}/{module-name}/add"},
      "reload"
    ],
    "columns": [
      {"name": "model.name", "label": "Name"},
      {"name": "model.title", "label": "Title"},
      {"name": "lastModifiedTime", "label": "Last Modified", "type": "datetime"},
      {
        "type": "operation",
        "label": "Operations",
        "buttons": [
          {"label": "Edit", "actionType": "link", "link": "/dev/${appName}/{module-name}/${model.name}"},
          {"label": "Delete", "actionType": "ajax", "confirmText": "Confirm delete?", "api": {"method": "delete", "url": "/api/dev/${appName}/{module-name}/${model.name}"}}
        ]
      }
    ]
  }
}
```

## 9. UI Schema 详情页

文件：`META-INF/ouroboros/ui-model/dev/{appName}/{module-name}/{fullName}.json`

```json
{
  "type": "page",
  "title": "${fullName}",
  "body": {
    "type": "service",
    "api": "/api/dev/${appName}/{module-name}/${fullName}/status",
    "body": [
      {
        "type": "form",
        "api": {"method": "put", "url": "/api/dev/${appName}/{module-name}/${fullName}"},
        "actions": [
          {"type": "submit", "label": "Save", "level": "primary"},
          {"type": "button", "label": "Commit", "level": "success", "actionType": "ajax", "api": {"method": "post", "url": "/api/dev/${appName}/{module-name}/${fullName}/commit", "data": {"commitMsg": "Update model"}}},
          {"type": "button", "label": "Rollback", "level": "warning", "actionType": "ajax", "api": {"method": "post", "url": "/api/dev/${appName}/{module-name}/${fullName}/rollback"}}
        ],
        "body": [
          {"type": "input-text", "name": "model.name", "label": "Name", "disabled": true},
          {"type": "input-text", "name": "model.title", "label": "Title", "required": true}
        ]
      }
    ]
  }
}
```

## 高级功能（可选）

### 自定义 Controller

```java
@RestController
@RequestMapping("/api/dev/{appName}/{module-name}")
public class {Module}Controller {
    @Autowired
    private {Module}DevService service;

    @GetMapping("/custom-endpoint")
    public WebApiResult<?> customEndpoint() {
        // 自定义逻辑
    }
}
```

### 关联文件管理

```java
@Override
protected Map<String, Supplier<InputStream>> getAssociatedFilesForCreate(
        String fullName, {Model}Model model) {
    Map<String, Supplier<InputStream>> files = new HashMap<>();
    // 添加关联文件
    files.put("path/to/file", () -> new ByteArrayInputStream(content));
    return files;
}

@Override
protected Set<String> getAssociatedFiles({Model}Model model) {
    // 返回需要同时删除的关联文件路径
    return Collections.singleton("path/to/file");
}
```

### SPI 扩展

```java
// 定义 SPI 接口
public interface {Module}Provider {
    List<{Data}> get{Data}s();
}

// 实现
public class Default{Module}Provider implements {Module}Provider {
    @Override
    public List<{Data}> get{Data}s() {
        return ResourceUtil.getResourceNameAndContentListFromDirectory(
            "META-INF/ouroboros/{module-data}/",
            Pattern.compile(".*\\.json"))
            .stream()
            .map(tuple -> Json.tryToBean({Data}.class, tuple._2()))
            .filter(Try::isSuccess)
            .map(Try::get)
            .collect(Collectors.toList());
    }
}

// SPI 注册：META-INF/services/com.ouroboros.{module}.{Module}Provider
com.ouroboros.{module}.Default{Module}Provider

// 消费 SPI
var data = SPI.getCachedSortedServiceStream({Module}Provider.class)
    .map({Module}Provider::get{Data}s)
    .flatMap(Collection::stream)
    .collect(Collectors.toList());
```

## 复杂度选择

| 需求 | 级别 | 参考 |
|------|------|------|
| 简单配置/元数据管理 | 简单 | ouroboros-resource-dev |
| 可视化编辑器 | 中等 | ouroboros-logicflow-dev |
| 复杂业务模型 | 复杂 | ouroboros-business-model-dev |

**简单模块**：只需上述 1-9 节内容
**中等模块**：+ 自定义 Controller + SPI 扩展
**复杂模块**：+ 模块树 + 引用管理 + UI Schema Provider + 多 Inspector

## 自动提供的 API 端点

所有继承 `BaseDevelopService` 的模块自动获得以下端点（通过 `ModelDevelopController`）：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/dev/{appName}/{moduleName}` | 搜索/列表 |
| GET | `/api/dev/{appName}/{moduleName}/{fullName}` | 获取单个 |
| POST | `/api/dev/{appName}/{moduleName}` | 创建 |
| PUT | `/api/dev/{appName}/{moduleName}/{fullName}` | 更新 |
| DELETE | `/api/dev/{appName}/{moduleName}/{fullName}` | 删除 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/lock` | 锁定 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/release-lock` | 解锁 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/commit` | 提交 |
| POST | `/api/dev/{appName}/{moduleName}/{fullName}/rollback` | 回滚 |
| GET | `/api/dev/{appName}/{moduleName}/{fullName}/status` | 状态 |
| GET | `/api/dev/{appName}/{moduleName}/{fullName}/change-log` | 历史 |

## 快速检查清单

实现新模块时确保：

- [ ] 模型类实现 JSON 序列化
- [ ] Service 继承 `BaseDevelopService` 并添加 `@DevelopModule` 注解
- [ ] 实现 `VCSFileInspector` 并注册 SPI
- [ ] 创建 `AutoConfiguration` 并同步注册到 imports 与 factories
- [ ] 添加菜单配置 JSON
- [ ] 创建列表页和详情页 UI Schema
- [ ] 更新 pom.xml 添加必要依赖

## 常见错误

1. ❌ 模型类未添加 `@JsonIgnoreProperties(ignoreUnknown = true)`
2. ❌ Service 忘记添加 `@DevelopModule` 注解
3. ❌ Inspector 的 SPI 文件名或内容错误
4. ❌ imports 或 factories 中类名拼写错误
5. ❌ UI Schema 中 API 路径与模块名不匹配
6. ❌ getFullName() 返回的标识符包含非法字符（如空格）
7. ❌ 文件扩展名包含多余的点（如 ".json.json"）

---

**参考完整文档**: `docs/guides/development-module-guide.md`
**示例模块**:
- 简单：`core/ouroboros-resource/ouroboros-resource-dev`
- 中等：`core/ouroboros-logicflow/ouroboros-logicflow-dev`
- 复杂：`core/ouroboros-business-model/ouroboros-business-model-dev`
