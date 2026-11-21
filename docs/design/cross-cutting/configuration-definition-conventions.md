# Ouroboros 统一配置定义约定

## 目标

本页用于统一说明 Ouroboros 中“配置元数据怎么定义”，尤其是模块在 JAR 内声明配置分组和配置项时应遵守的资源路径、JSON 结构和字段约定。它关注的是配置定义本身，不展开运行时值存储、权限页面和工作台交互细节。

## 先区分两类东西

统一配置体系里至少有两类对象：

- 配置元数据：描述一个配置项叫什么、属于哪组、值类型是什么、默认值是什么、界面该怎么编辑。
- 配置运行时值：描述当前环境下这个配置实际存了什么值。

本页只讲第一类，也就是 `ConfigurationMetadata` 和 `ConfigurationGroupMetadata` 的定义方式。

## 两种定义落点

当前仓库里统一配置元数据主要有两种来源：

### 1. JAR 内声明式资源

适用于模块把“自己携带的默认配置定义”随代码一起发布。

- 配置项源码：`src/main/metadata/configurations.json`（JSON 数组）；构建产物为 `META-INF/ouroboros/metadata/configurations.json`。源码与产物映射见[共同目录约定](../../guides/metadata-source-editor-guide.md)
- 配置分组源码：`src/main/metadata/configuration-groups.json`（JSON 数组）；构建产物为 `META-INF/ouroboros/metadata/configuration-groups.json`，兼容旧的 `META-INF/ouroboros/configuration-groups.json`

这是模块内最常见、也最稳定的定义方式。

### 2. 工作区文件定义

适用于低代码开发态或应用工作区覆盖。

- 配置项目录：`configuration/**/*.config.json`
- 配置分组目录：`configuration-group/**/*.config-group.json`

这类定义由文件 provider 读取，主要用于开发平台或应用级覆盖。本页会顺带说明它和 JAR 资源的差异，但重点仍然放在 JAR 内定义。

## 运行时如何发现这些定义

统一配置运行时通过两类 provider 聚合元数据：

- `ConfigurationMetadataProvider`
- `ConfigurationGroupMetadataProvider`

默认实现里：

- `JarConfigurationMetadataProvider` 读取固定集合文件 `configurations.json`，并兼容旧命名 `configuration.json`；两种命名都按旧 `META-INF/ouroboros/`、新 `META-INF/ouroboros/metadata/` 的顺序读取
- `JarConfigurationGroupMetadataProvider` 读取固定集合文件 `configuration-groups.json`，同样先旧目录、后新 `metadata/` 目录
- `FileConfigurationMetadataProvider` 读取应用 home 下的 `configuration/**/*.config.json`
- `FileConfigurationGroupMetadataProvider` 读取应用 home 下的 `configuration-group/**/*.config-group.json`

刷新时机由 `ConfigurationRuntimeAutoConfiguration` 监听元数据刷新事件统一触发，最终汇总到：

- `ConfigurationMetadataCenter`
- `ConfigurationGroupMetadataCenter`

## 优先级与合并规则

provider 并不是平铺读取，而是会按优先级聚合：

- JAR provider：`@Priority(0)`
- 文件 provider：`@Priority(100)`

这意味着：

- 模块内随 JAR 发布的定义是基础定义。
- 应用工作区里的定义可以在更高优先级上覆盖它。

同一个配置项 `key` 或同一个分组 `path` 冲突时，中心会做字段级 merge，而不是简单报错。这里有两个维护约束需要特别强调：

- JAR 内定义应默认保持“唯一声明”，不要把跨 JAR 重复定义当成常规覆盖机制。
- 如果确实要覆盖，优先使用更高优先级的文件定义，并尽量写完整对象，不要只写一部分字段。

原因是当前 merge 规则并不是所有字段都能区分“未填写”和“显式置空/置 false”：

- `label`、`description`、`groupPath`、`defaultValue`、`title`、`icon`、`description` 会按“后者非空优先”合并。
- `order`、`editorSchema` 会按“后者非 null 优先”合并。
- `extraProps` 会做 map merge。
- `modifiable`、`nullable`、`valueType`、`scope` 这类字段会直接采用后者值。

因此，覆盖定义时如果遗漏了布尔字段或类型字段，行为可能不是“保留旧值”，而是被后者默认值改写。实践上应把覆盖文件写成完整定义，而不是补丁式定义。

## JAR 内配置分组定义

### 固定资源路径

每个模块如果要声明配置分组，在源码元数据目录提供：

```text
src/main/metadata/configuration-groups.json
```

构建后进入 `META-INF/ouroboros/metadata/configuration-groups.json`。旧资源目录仍可读。

该文件内容是一个 JSON 数组，每个元素对应一个 `ConfigurationGroupMetadata`。

### 字段说明

配置分组当前主要字段如下：

- `path`：分组路径，唯一标识。
- `title`：展示标题。
- `icon`：展示图标。
- `order`：同级排序，数字越小越靠前。
- `description`：分组说明。
- 其他未知字段：会进入 `extraProps`，供后续扩展使用。

### 推荐写法

```json
[
  {
    "path": "security",
    "title": "安全设置",
    "icon": "shield",
    "order": 10,
    "description": "认证与会话相关运行时配置"
  },
  {
    "path": "security.session",
    "title": "会话设置",
    "icon": "clock",
    "order": 20,
    "description": "会话超时、空闲检测与并发控制"
  }
]
```

### 分组路径约定

建议所有分组路径都遵守下面几条规则：

- 使用稳定、可长期演进的英文点分路径，例如 `security.session`、`app.theme`。
- 不要使用前后带点的路径，例如 `.security.session.`。
- 不要把展示标题写进 `path`，标题应放在 `title`。
- 多级分组用点号表达父子关系，而不是靠字符串约定，例如 `notification.email.smtp`。

### 父分组要不要显式声明

工作台会根据点分路径自动补出祖先分组，所以即使只声明了 `security.session`，树上也能补出 `security` 这一层虚拟父分组。

但是否“能显示”不等于“建议省略”。如果你希望父分组具备稳定的：

- 标题
- 图标
- 排序
- 描述

就应该把每一级都显式声明出来，而不是只依赖工作台按路径推导。

## JAR 内配置项定义

### 固定资源路径

每个模块声明配置项时，在源码元数据目录提供：

```text
src/main/metadata/configurations.json
```

该文件内容同样是一个 JSON 数组，每个元素对应一个 `ConfigurationMetadata`。

### 常用字段

配置项当前最重要的字段如下：

- `key`：配置键，唯一标识。
- `label`：页面展示名称。
- `description`：配置项说明。
- `groupPath`：所属分组路径。
- `order`：同组内排序。
- `modifiable`：是否允许在管理界面修改。
- `nullable`：是否允许值为空。
- `valueType`：值类型。
- `defaultValue`：默认值。
- `scope`：配置作用域，当前主要是 `frontend` / `backend`。
- `editorSchema`：自定义编辑器 schema。
- `obsolete`：是否已废弃。
- `obsoleteCause`：废弃原因。
- 其他未知字段：会进入 `extraProps`。

### 推荐写法

```json
[
  {
    "key": "security.session.idleTimeout",
    "label": "空闲超时时间",
    "groupPath": "security.session",
    "order": 20,
    "description": "空闲超时时间（单位：秒）",
    "scope": "frontend",
    "modifiable": true,
    "nullable": false,
    "valueType": "int",
    "defaultValue": "3600"
  }
]
```

### 配置键约定

推荐配置键遵守以下规则：

- 全局唯一。
- 使用稳定的点分命名。
- 前缀尽量与 `groupPath` 对齐，便于检索和理解。

例如：

- 分组：`security.session`
- 配置键：`security.session.idleTimeout`

这不是框架强制规则，但对长期维护非常重要。配置键一旦发布出去，后续改名就不仅仅是文案变更，还会影响：

- 已存储的运行时值
- 其他配置值里的引用关系
- 前后端读取逻辑
- 运维与调试成本

### `groupPath` 的语义

`groupPath` 负责把配置项挂到左侧树和右侧面板里。

- 为空或缺失：会进入运行时工作台的“未分组”虚拟分组。
- 有值：会按路径落到对应分组。

如果你希望这个配置项出现在有明确标题、图标和排序的树节点下，就应该同时提供对应的 `configuration-groups.json` 声明。

## `valueType` 与 `defaultValue` 约定

### 支持的值类型

当前 `ConfigValueType` 支持：

- `string`
- `int`
- `long`
- `float`
- `double`
- `boolean`
- `object`
- `map`
- `list`
- `array`

### 默认值怎么写

`defaultValue` 在模型上是字符串语义，运行时会再按 `valueType` 转换成真正值对象。因此推荐遵守下面的写法：

- `string`：直接写字符串。
- `int` / `long` / `float` / `double`：写能被对应类型解析的数字文本。
- `boolean`：写 `true` / `false` 对应的可解析值。
- `map` / `object`：写成 JSON 对象文本。
- `list` / `array`：写成 JSON 数组文本。

对复杂类型，建议显式写成 JSON 文本字符串，而不是依赖反序列化时的隐式转换。例如：

```json
[
  {
    "key": "example.rules",
    "label": "规则列表",
    "groupPath": "example",
    "order": 10,
    "modifiable": true,
    "nullable": false,
    "valueType": "list",
    "defaultValue": "[\"a\",\"b\"]"
  }
]
```

实践上应优先保证一件事：`defaultValue` 最终必须能被对应 `ConfigValueType` 正确解析。

## `scope` 的使用建议

当前常见取值是：

- `backend`
- `frontend`

它决定配置更偏向哪一侧消费，而不是“只有哪一侧能看到”。维护时建议：

- 会注入前端公共配置的项显式写 `frontend`
- 仅后端消费的项写 `backend`

如果不写，当前默认会落到 `backend` 语义。为了避免覆盖时出现误解，建议在正式模块资源里把 `scope` 写全。

## `editorSchema` 与自动编辑器推导

### 自定义编辑器

如果配置项需要特定的编辑体验，可以直接在元数据里写 `editorSchema`，它应是一段可作为表单项使用的 schema。例如：

```json
[
  {
    "key": "app.defaultTheme",
    "label": "默认主题",
    "groupPath": "app.theme",
    "order": 10,
    "modifiable": true,
    "nullable": false,
    "valueType": "string",
    "defaultValue": "dark",
    "scope": "frontend",
    "editorSchema": {
      "type": "button-group-select",
      "options": [
        { "value": "dark", "label": "深色" },
        { "value": "light", "label": "浅色" }
      ]
    }
  }
]
```

### 不写 `editorSchema` 时会发生什么

如果不显式提供 `editorSchema`，运行时会基于 `valueType` 和部分 `extraProps` 自动生成基础编辑器：

- `string` -> `input-text`
- `int` / `long` -> `input-number`
- `float` / `double` -> `input-number`
- `boolean` -> `switch`
- `map` / `object` -> `input-kv`
- `list` / `array` -> `json-editor`

此外还有两个常见的增强规则：

- 如果顶层额外属性里有 `options`，会优先退化成 `select`
- 浮点数类型可以通过 `precision` 指定小数位

例如下面这种写法虽然没有 `editorSchema`，但仍会得到下拉选择器：

```json
[
  {
    "key": "notification.email.protocol",
    "label": "邮件通知协议",
    "groupPath": "notification.email",
    "order": 10,
    "modifiable": true,
    "nullable": true,
    "valueType": "string",
    "defaultValue": "smtp",
    "options": ["smtp"]
  }
]
```

## `extraProps` 的意义

配置模型支持 `@JsonAnySetter`，也就是 JSON 里那些不属于固定字段的属性不会丢掉，而会被放进 `extraProps`。

这带来两个直接收益：

- 可以在不改核心模型的前提下给特定配置项补充扩展属性
- 自动推导编辑器时可以复用这些扩展属性，例如 `options`、`precision`

但这不意味着字段可以随意发明。实践上应遵守两条规则：

- 真正跨模块复用、已经形成稳定语义的扩展字段，应写进共享文档。
- 只是某个配置项私有消费的临时属性，应尽量就近说明它的读取方和用途。

## 工作区文件定义与 JAR 定义的差异

虽然本页重点是 JAR 内定义，但维护者通常也会碰到开发态文件定义。两者有一个很容易踩坑的差异：

- JAR 固定集合文件：内容是数组，使用 `configurations.json` / `configuration-groups.json`。
- 应用 home 工作区文件：内容是单个对象，分别使用 `configuration/**/*.config.json` / `configuration-group/**/*.config-group.json`。

IDE 对 `.config.json` / `.config-group.json` 的编辑器支持不等于 JAR Provider 会扫描这些单对象文件。不要把单对象文件直接放入 `src/main/metadata` 后就假定它已被 JAR 配置 Provider 加载。

例如，工作区里的配置项文件通常是：

```json
{
  "key": "key0",
  "label": "Key 0",
  "description": "Configuration fixture for runtime module update tests",
  "groupPath": "security.auth",
  "order": 20,
  "modifiable": true,
  "nullable": false,
  "valueType": "string",
  "defaultValue": "key0-default"
}
```

对应的分组文件则通常是：

```json
{
  "path": "security.session",
  "title": "会话管理",
  "order": 30,
  "description": "文件覆盖说明"
}
```

不要把这套“单文件单对象”的工作区格式直接搬到 JAR 资源里，否则运行时 provider 无法按预期读取。

## 推荐给模块维护者的最小实践

如果一个模块只是想把自己的配置接入统一配置体系，最小可行做法是：

1. 在模块的 `src/main/metadata/` 下增加 `configurations.json`
2. 给每个配置项写完整的基础元数据
3. 如果希望工作台树结构清晰，再增加 `configuration-groups.json`
4. 为每个组路径显式声明标题、图标和排序
5. 非字符串配置项确认 `defaultValue` 能被对应类型解析
6. 需要特殊控件时显式提供 `editorSchema`

## 维护建议

长期维护时，建议额外遵守这些约束：

- 不要把跨 JAR 重复定义当作常规覆盖机制。
- 不要依赖“省略字段也能保留旧值”这类隐式 merge 假设。
- 不要只声明叶子分组却希望父层也拥有稳定展示信息。
- 不要让 `key`、`groupPath`、`title`、`label` 混用同一套命名职责。
- 不要把运行时值、默认值和环境变量覆盖规则写进元数据命名里。

## 深入阅读入口

- [configuration-system.md](configuration-system.md)
- [meta-inf-resource-conventions.md](meta-inf-resource-conventions.md)
- [../modules/ability/ouroboros-configuration.md](../modules/ability/ouroboros-configuration.md)
- [../../../ability/ouroboros-configuration/DESIGN.md](../../../ability/ouroboros-configuration/DESIGN.md)
