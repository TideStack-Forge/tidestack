# Common-Dev - 开发模式支持指南

本文档描述了 `common-dev` 模块的核心类和接口,该模块为 Ouroboros 低代码平台的开发模式提供基础支持。

## 模块概述

`common-dev` 提供开发环境管理、应用上下文、元数据管理、事件系统等开发模式所需的核心抽象和工具类。

**主要功能:**
- 工作空间上下文管理(开发/运行)
- 应用上下文线程绑定
- 元数据提供者机制
- 开发事件系统
- 环境管理

## 核心类/接口

### 工作空间上下文

#### DevelopWorkspaceContext
开发工作空间上下文接口,提供开发环境各类目录路径。

```java
public interface DevelopWorkspaceContext {
  String getRootDir();                    // 获取根目录
  String getDevRootDir();                 // 获取开发工作空间根目录
  String getRunRootDir();                 // 获取运行根目录
  String getDebugSupportFilesDir();       // 获取调试支持文件目录
  String getDevelopSupportFilesDir();     // 获取开发支持文件目录
}
```

**使用示例:**
```java
@Autowired
private DevelopWorkspaceContext workspaceContext;

public void loadDevFiles() {
  String devRoot = workspaceContext.getDevRootDir();
  Path configPath = Paths.get(devRoot, "config");
  // 加载开发配置文件...
}
```

#### RunWorkspaceContext
运行工作空间上下文接口,提供运行时工作空间路径。

```java
public interface RunWorkspaceContext {
  String getWorkspacePath();  // 获取运行工作空间路径
}
```

### 应用上下文管理

#### DevAppHolder
线程级应用名称持有者,用于在当前线程绑定应用上下文。

**核心方法:**
```java
// 设置当前线程的应用名称
DevAppHolder.setAppName("myApp");

// 获取当前线程的应用名称
String appName = DevAppHolder.getAppName();

// 移除当前线程的应用名称
DevAppHolder.removeAppName();

// 在指定应用上下文中执行代码
String result = DevAppHolder.runWithAppName("myApp", () -> {
  // 在此闭包内 DevAppHolder.getAppName() 返回 "myApp"
  return doSomething();
});
```

**典型场景:**
```java
// 场景1: 在特定应用上下文中加载配置
public Config loadConfig(String appName) {
  return DevAppHolder.runWithAppName(appName, () -> {
    // 此处可以通过 DevAppHolder.getAppName() 获取 appName
    return configService.load();
  });
}

// 场景2: 设置后手动管理上下文
try {
  DevAppHolder.setAppName("myApp");
  processAppData();
} finally {
  DevAppHolder.removeAppName();
}
```

#### AppHomeResolver
应用根目录解析器接口。

```java
public interface AppHomeResolver {
  String getAppHomeByAppId(String appId);  // 根据应用ID获取应用根目录
}
```

### 元数据管理

#### Metadata
元数据模型,描述低代码平台中的各类元数据对象。

**属性:**
- `name`: 唯一标识
- `type`: 元数据类型
- `label`: 中文标签
- `sourceClassName`: 来源类名
- `sourceType`: 来源类型(LOW_CODE/PRO_CODE/UNKNOWN)
- `priority`: 优先级
- `details`: 详细信息(Map)

#### MetadataProvider
元数据提供者接口,用于获取应用模块的元数据。

```java
public interface MetadataProvider {
  List<Metadata> getAppAllMetadataByModule(String appName, String moduleName);
}
```

**实现示例:**
```java
@Component
public class MyMetadataProvider implements MetadataProvider {
  @Override
  public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
    // 扫描指定应用和模块的元数据
    List<Metadata> metadataList = new ArrayList<>();
    // ... 加载元数据
    return metadataList;
  }
}
```

#### 应用级元数据快照

`ApplicationMetadataSnapshotProvider` 扩展 `MetadataProvider`，通过 `getApplicationMetadataSnapshot(appName)` 返回应用级快照。状态为 `CURRENT`、`STALE` 或 `UNAVAILABLE`；按模块读取的默认实现委托给快照。消费方应区分陈旧、不可用与有效空模块，不能把空列表都认定为“当前无定义”。提供者发现和缓存发布流程见 [元数据发现管道](../../../docs/design/architecture/pipelines/metadata-discovery.md)。

`MetadataProviderMixer` 提供 distinct 与 concat 两种混合方式：distinct 按名称忽略大小写合并，优先级较高者覆盖；concat 保留各 provider 的列表。需由实际消费者选择混合入口。

#### MetadataSourceType
元数据来源类型枚举。

```java
public enum MetadataSourceType {
  LOW_CODE("low-code"),   // 低代码模式生成
  PRO_CODE("pro-code"),   // 专业代码模式
  UNKNOWN("unknown");     // 未知来源
}
```

### 事件系统

#### 应用事件

**DevAppEvent (抽象基类)**
```java
public abstract class DevAppEvent extends EventObject {
  private String appName;
  // ...
}
```

**具体事件类型:**
- `CreateAppEvent`: 应用创建事件
- `DeleteAppEvent`: 应用删除事件

**使用示例:**
```java
@Component
public class AppEventListener {
  @EventListener
  public void onAppCreated(CreateAppEvent event) {
    String appName = event.getAppName();
    // 处理应用创建逻辑
  }

  @EventListener
  public void onAppDeleted(DeleteAppEvent event) {
    String appName = event.getAppName();
    // 处理应用删除逻辑
  }
}
```

#### 模型事件

**DevAppModelEvent (基类)**
```java
public class DevAppModelEvent extends DevAppEvent {
  private final Object model;  // 模型对象
  // ...
}
```

**具体事件类型:**
- `CreateModelEvent`: 模型创建事件
- `UpdateModelEvent`: 模型更新事件
- `DeleteModelEvent`: 模型删除事件

**使用示例:**
```java
@EventListener
public void onModelCreated(CreateModelEvent event) {
  String appName = event.getAppName();
  Object model = event.getModel();
  // 处理模型创建后的逻辑
}
```

#### 文件变更事件

**DevSingleFileChanged**
```java
public class DevSingleFileChanged extends DevAppEvent {
  private String fileName;  // 变更的文件名
  // ...
}
```

**使用示例:**
```java
@EventListener
public void onFileChanged(DevSingleFileChanged event) {
  String appName = event.getAppName();
  String fileName = event.getFileName();
  // 重新加载文件或触发热更新
}
```

### 环境管理

#### Environment
环境模型,描述开发环境配置。

```java
public class Environment {
  private String name;         // 环境名称
  private String title;        // 环境标题
  private String description;  // 环境描述
  // ...
}
```

#### EnvironmentProvider
环境提供者接口。

```java
public interface EnvironmentProvider {
  List<Environment> getAll();  // 获取所有环境
}
```

**实现示例:**
```java
@Component
public class MyEnvironmentProvider implements EnvironmentProvider {
  @Override
  public List<Environment> getAll() {
    return Arrays.asList(
      createEnv("dev", "开发环境", "本地开发环境"),
      createEnv("test", "测试环境", "功能测试环境"),
      createEnv("prod", "生产环境", "生产环境")
    );
  }
}
```

### 注解

#### @DevModel
标记类为开发模式模型类。

```java
@DevModel
public class MyDevModel {
  // 开发模式下的模型定义
}
```

## 典型应用场景

### 场景1: 实现开发模块(dev-module)

```java
// 1. 定义模型
@DevModel
public class MyModel {
  private String id;
  private String name;
  // ...
}

// 2. 实现元数据提供者
@Component
public class MyModelMetadataProvider implements MetadataProvider {
  @Override
  public List<Metadata> getAppAllMetadataByModule(String appName, String moduleName) {
    // 从文件系统或数据库加载元数据
    return loadMetadata(appName, moduleName);
  }
}

// 3. 监听模型事件
@Component
public class MyModelEventHandler {
  @EventListener
  public void onModelCreated(CreateModelEvent event) {
    // 处理模型创建
  }

  @EventListener
  public void onModelUpdated(UpdateModelEvent event) {
    // 处理模型更新
  }
}
```

### 场景2: 多应用上下文管理

```java
public class MultiAppService {
  public void processApps(List<String> appNames) {
    appNames.forEach(appName -> {
      DevAppHolder.runWithAppName(appName, () -> {
        // 在每个应用的上下文中执行操作
        loadAppConfig();
        processAppData();
        return null;
      });
    });
  }
}
```

### 场景3: 工作空间路径管理

```java
@Service
public class DevFileService {
  @Autowired
  private DevelopWorkspaceContext workspaceContext;

  @Autowired
  private AppHomeResolver appHomeResolver;

  public Path getAppModelPath(String appName, String modelName) {
    String appHome = appHomeResolver.getAppHomeByAppId(appName);
    return Paths.get(appHome, "models", modelName + ".json");
  }

  public Path getDebugFile(String fileName) {
    String debugDir = workspaceContext.getDebugSupportFilesDir();
    return Paths.get(debugDir, fileName);
  }
}
```

## 注意事项

1. **线程安全**: `DevAppHolder` 使用普通 `ThreadLocal`，不会自动跨线程传播；异步任务需要在其执行线程显式绑定和清理
2. **资源清理**: 手工设置后在 finally 清理；嵌套切换优先使用 `runWithAppName()`，它在退出时恢复原非空应用名
3. **事件监听**: Spring 事件监听器默认是同步的,长时间操作应考虑异步处理
4. **元数据缓存**: 频繁访问的元数据应考虑缓存,避免重复加载
5. **路径分隔符**: 使用 `java.nio.file.Paths` 构建路径,确保跨平台兼容性

## 扩展开发

实现新的开发模块时,应该:
1. 定义模型类并使用 `@DevModel` 标注
2. 实现 `MetadataProvider` 提供元数据
3. 监听相关事件(Create/Update/Delete)进行响应
4. 如需环境管理,实现 `EnvironmentProvider`
5. 参考 [development-module-guide.md](../../../docs/guides/development-module-guide.md) 了解完整的开发模块模式
