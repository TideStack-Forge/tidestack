# Common Template - 模板引擎指南

本文档描述了 `common-template` 模块的使用方法。该模块基于 FreeMarker 提供了 JAR 资源模板引擎,用于从 JAR 包中的模板文件生成动态内容。

## 模块概述

`common-template` 是一个轻量级的模板引擎封装,主要用于:
- 从 JAR 包资源路径加载 FreeMarker 模板
- 生成动态 JSON 配置(UI Schema、Logic Flow 配置等)
- 支持模板变量替换和逻辑处理
- 自动注入 JSON 工具类,方便在模板中处理 JSON 数据

**依赖技术**:
- FreeMarker 模板引擎
- `common-expression` (上下文包装)
- `ouroboros-util` (JSON 工具)

## 核心类

### TextTemplateEngine

路径: `com.ouroboros.template.TextTemplateEngine`

文本模板引擎,用于“模板字符串 + 参数 Map -> 渲染文本”的场景。它不从 JAR/classpath 加载资源,适合项目生成器这类已经从 ZIP、数据库或其他来源拿到模板文本的调用方。

```java
TextTemplateEngine engine = new TextTemplateEngine();

String result = engine.render(
    "Hello ${name}! <#list items as item>${item}<#sep>, </#list>",
    Map.of("name", "Ouroboros", "items", List.of("React", "Vue"))
);
```

行为边界:
- 输入 `templateText` 和 `parameters` 不能为空。
- 渲染失败会抛出 `TextTemplateException`,不会返回 `null`。
- 禁用 `?api` 和 `?new` 任意类解析,调用方只应传入受控的标量、集合和 helper。
- 如需在生成内容中保留字面 `${...}`,使用 FreeMarker 标准 `<#noparse>...</#noparse>`。
- `TextTemplateEngine` 不包含项目生成器的路径校验、escape helper、ZIP 写入或贡献片段选择逻辑。

### JarTemplateEngine

路径: `com.ouroboros.template.JarTemplateEngine`

JAR 资源模板引擎,从 JAR 包中的指定路径加载 FreeMarker 模板并生成内容。

#### 构造方法

```java
// 使用默认后缀 .json.ftl
JarTemplateEngine engine = new JarTemplateEngine("META-INF/templates");

// 自定义后缀
JarTemplateEngine engine = new JarTemplateEngine("META-INF/templates", ".ftl");
```

**参数说明**:
- `templateResourcePath`: 模板资源路径(相对于 classpath 根目录)
- `suffix`: 模板文件后缀,默认为 `.json.ftl`

#### 核心方法

**1. generate(String, Map) - 生成字符串**

```java
Map<String, Object> config = new HashMap<>();
config.put("appName", "my-app");
config.put("apiPath", "users");

String result = engine.generate("templateName", config);
```

**2. generate(String, Map, Class) - 生成对象**

```java
SchemaNode schema = engine.generate("templateName", config, SchemaNode.class);
```

自动将生成的 JSON 字符串反序列化为指定类型对象。

## 使用示例

### 示例 1: 基础模板生成

**模板文件**: `src/main/resources/META-INF/templates/greeting.json.ftl`
```json
{
  "message": "Hello, ${userName}!",
  "timestamp": "${.now}"
}
```

**Java 代码**:
```java
JarTemplateEngine engine = new JarTemplateEngine("META-INF/templates");

Map<String, Object> data = new HashMap<>();
data.put("userName", "Alice");

String json = engine.generate("greeting", data);
// 结果: {"message": "Hello, Alice!", "timestamp": "..."}
```

### 示例 2: UI Schema 生成(实际案例)

**场景**: 生成版本控制按钮的 UI Schema

**模板文件**: `META-INF/ouroboros/dev/fragment-schema/vcs-buttons.json.ftl`
```ftl
<#noparse>{
  "type": "service",
  "api": {
    "url": "/api/dev/${appName}/</#noparse>${apiPath}<#noparse>/status",
    "method": "get"
  },
  "body": [
    {
      "type": "button",
      "label": "</#noparse>${startButton!'开始编辑'}<#noparse>",
      "level": "primary"
    }
  ]
}</#noparse>
```

**Java 代码**:
```java
@RestController
@RequestMapping("/api/dev-fragment-schema")
public class DevFragmentSchemaController {
  JarTemplateEngine engine = new JarTemplateEngine("META-INF/ouroboros/dev/fragment-schema");

  @GetMapping("/{*path}")
  public WebApiResult<SchemaNode> getSchema(@PathVariable String path,
                                            @RequestParam Map<String, String> params) {
    SchemaNode schema = engine.generate(path, params, SchemaNode.class);
    return WebApiResult.success(schema);
  }
}
```

### 示例 3: 业务模板工具类封装

**场景**: 封装通用模板引擎,提供静态方法访问

```java
public class PrototypeTemplate {
  private static final String TEMPLATE_PATH = "META-INF/prototype-templates/";
  private static final JarTemplateEngine ENGINE = new JarTemplateEngine(TEMPLATE_PATH);

  public static String generate(String templateName, Map<String, ?> config) {
    return ENGINE.generate(templateName, config);
  }

  public static <T> T generate(String templateName, Map<String, ?> config, Class<T> clazz) {
    return ENGINE.generate(templateName, config, clazz);
  }
}

// 使用
SchemaNode schema = PrototypeTemplate.generate("ui-schema/form", config, SchemaNode.class);
```

## 模板编写技巧

### 1. FreeMarker 与 JSON 混合语法

使用 `<#noparse>...</#noparse>` 包裹静态 JSON 内容,避免 FreeMarker 解析冲突:

```ftl
<#noparse>{
  "staticField": "value",
  "dynamicField": "</#noparse>${variable}<#noparse>"
}</#noparse>
```

### 2. 使用内置 JSON 工具

模板引擎自动注入 `Jsons`、`Json`、`JsonUtils` 三个别名(指向同一个 JSON 工具实例):

```ftl
{
  "data": ${Jsons.toJsonString(complexObject)}
}
```

### 3. 默认值和条件渲染

```ftl
{
  "title": "${title!'默认标题'}",
  <#if showDescription>
  "description": "${description}",
  </#if>
  "items": [
    <#list items as item>
    "${item}"<#sep>,</#sep>
    </#list>
  ]
}
```

## 文件组织规范

### 推荐的资源路径结构

```
src/main/resources/
└── META-INF/
    ├── prototype-templates/          # 原型模板
    │   ├── ui-schema/               # UI Schema 模板
    │   │   ├── form.json.ftl
    │   │   └── crudTable.json.ftl
    │   └── logic-flow/              # Logic Flow 模板
    │       └── batchDelete.json.ftl
    └── ouroboros/
        └── dev/
            └── fragment-schema/      # UI 片段模板
                └── vcs-buttons.json.ftl
```

### 模板命名约定

- 使用描述性名称: `form.json.ftl`, `crudTable.json.ftl`
- 保持目录层次与业务逻辑对应
- 默认后缀 `.json.ftl` 可省略(在调用 `generate()` 时只需传递 `form` 而非 `form.json.ftl`)

## 注意事项

### 1. 异常处理

模板生成失败时返回 `null`,而非抛出异常:

```java
String result = engine.generate("template", config);
if (result == null) {
  // 处理模板生成失败的情况
}
```

### 2. 模板缓存

`JarTemplateEngine` 实例应该复用,避免重复创建:

```java
// 推荐: 单例或成员变量
private static final JarTemplateEngine ENGINE = new JarTemplateEngine("...");

// 不推荐: 每次都创建新实例
public String generate() {
  JarTemplateEngine engine = new JarTemplateEngine("...");  // ❌
  return engine.generate("template", config);
}
```

### 3. 模板路径解析

- 模板路径不需要以 `/` 开头(会自动处理)
- 模板名称不包含后缀(后缀由构造函数指定)
- 示例: `engine.generate("ui-schema/form", config)` 会加载 `{basePath}/ui-schema/form.json.ftl`

### 4. JSON 反序列化

使用 `generate(String, Map, Class)` 方法时,确保模板输出是有效的 JSON:

```java
// 模板必须输出合法 JSON
SchemaNode schema = engine.generate("template", config, SchemaNode.class);

// 如果模板输出无效 JSON,会返回 null
if (schema == null) {
  // 检查模板输出或 JSON 格式
}
```

## 典型应用场景

1. **动态 UI Schema 生成**: 根据业务模型生成前端 UI 配置
2. **Logic Flow 配置**: 生成工作流节点配置
3. **代码脚手架**: 生成标准化的业务代码模板
4. **API 响应模板**: 动态生成复杂的 JSON 响应结构
5. **开发工具**: 生成开发辅助的配置文件和脚本

## 参考资源

- FreeMarker 官方文档: https://freemarker.apache.org/docs/
- `common-expression` 模块: 表达式上下文包装
- `ouroboros-util` 模块: JSON 工具类

## 相关模块

- `core/ouroboros-business-model/ouroboros-business-model-util`: 使用示例(PrototypeTemplate)
- `dev-module/ouroboros-dev-model-manage-webapi-scaffold`: 使用示例(DevFragmentSchemaController)
