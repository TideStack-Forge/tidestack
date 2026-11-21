# Ouroboros I18n 模块设计文档

## 1. 概述

### 1.1 模块定位

ouroboros-i18n 是 Ouroboros 平台的国际化（i18n）核心模块，位于 core 层，为整个平台提供多语言支持能力。该模块采用基于 UI
Schema 的文案提取和语言环境管理方案，实现前后端协同的国际化解决方案。

### 1.2 设计目标

- **自动化文案提取**：从 UI Schema、UI Model、菜单、权限树、静态消息中自动提取需要翻译的文案
- **统一语言环境管理**：提供标准化的多语言环境格式和管理接口
- **开发时支持**：提供语言环境导出、导入、编辑、同步的开发工具
- **运行时高效**：提供高性能的语言环境加载和查询接口，支持缓存
- **扩展性**：支持多种数据源（文件系统、数据库）和文案提取器

### 1.3 实施阶段

**Phase 1（已完成）：UI 国际化**

- UI Schema 文案提取（支持复杂嵌套结构、数组索引、localeKey）
- UI Model 文案提取
- 菜单（MenuModel）文案提取
- 权限树（Authority）文案提取
- 静态消息文案提取（从 META-INF/ouroboros/i18n/messages）
- 语言环境生成、导入、导出、同步
- 前端语言环境加载和文案替换
- 管理界面（语言列表、详情查看、同步、设为默认）

**接口错误信息国际化（已落地，2026-09-12 核对）**

本节原写作"Phase 2（未来）"，实际已实现：

- `ErrorCodeTextExtractor`（`ouroboros-i18n-runtime`，`extractor` 包）把统一错误码体系提取为可翻译文案，并在 `META-INF/services/com.ouroboros.i18n.extractor.TextExtractor` 中注册；
- `DefaultErrorMessageLocalizer`（同模块 `error` 包）按当前语言环境把错误码解析为本地化错误消息，是异常体系与 i18n 的桥接点；
- 词条使用独立的 `errors` 命名空间，与界面文案分开维护。
- 模块聚合 pom 现含三个子模块：`ouroboros-i18n-core`、`ouroboros-i18n-runtime`、`ouroboros-i18n-dev`（本文早期版本只描述了前两个）。

## 2. 架构设计

### 2.1 模块结构

```
ouroboros-i18n/
├── ouroboros-i18n-core/          # 核心模型和接口
│   ├── model/                    # 数据模型
│   │   ├── I18nLocale            # 语言环境模型（包含语言元数据）
│   │   ├── I18nEntry             # 语言环境条目
│   │   └── TextDirection         # 文本方向枚举
│   ├── extractor/                # 文案提取器
│   │   └── TextExtractor         # 文案提取接口
│   └── provider/                 # 语言环境提供者
│       └── I18nLocaleProvider    # 语言环境提供者接口（SPI）
│
└── ouroboros-i18n-runtime/       # 运行时实现
    ├── extractor/                # 文案提取器实现
    │   ├── UISchemaTextExtractor # UI Schema 文案提取实现
    │   ├── UIModelTextExtractor  # UI Model 文案提取实现
    │   ├── MenuTextExtractor     # 菜单文案提取实现
    │   ├── AuthorityTextExtractor # 权限树文案提取实现
    │   ├── StaticMessagesTextExtractor # 静态消息文案提取实现
    │   └── ErrorCodeTextExtractor # 错误码文案提取实现
    ├── provider/                 # 语言环境提供者实现
    │   ├── JarI18nLocaleProvider # classpath 元数据实现
    │   ├── FileSystemI18nLocaleProvider # 文件系统实现（开发时）
    │   └── DatabaseI18nLocaleProvider   # 数据库实现（运行时）
    ├── service/                  # 服务层
    │   ├── I18nLocaleService     # 语言环境查询服务（只读）
    │   └── manage/               # 管理服务
    │       └── I18nLocaleManageService # 语言环境管理服务（读写）
    ├── controller/               # REST API
    │   ├── I18nLocaleController  # 只读 API（前端使用）
    │   └── I18nLocaleManageController # 管理 API（开发工具使用）
    ├── entity/                   # 数据库实体
    │   └── I18nLocaleEntity      # 语言环境数据库实体
    ├── model/                    # 实现类
    │   ├── I18nLocaleImpl        # 语言环境实现类
    │   └── I18nEntryImpl         # 语言环境条目实现类
    ├── dto/                      # 数据传输对象
    │   ├── I18nLanguageSummary   # 语言摘要信息
    │   ├── SyncRequest           # 同步请求
    │   ├── SyncResult            # 同步结果
    │   ├── ExportResult          # 导出结果
    │   └── LanguageOption        # 语言选项
    └── autoconfigure/            # Spring Boot 自动配置
        └── I18nAutoConfiguration
    
    # UI Schema 和菜单定义在 resources 目录：
    # src/main/resources/META-INF/ouroboros/
    # ├── ui-model/                # UI 路由定义
    # ├── ui-schema/               # UI 页面定义（Amis JSON）
    # ├── authority/               # 权限定义
    # ├── menu-model/              # 菜单定义
    # └── i18n/messages/           # 静态消息文件
```

### 2.2 核心流程

#### 2.2.1 文案提取流程

```
[SPI 加载所有 UISchemaProvider]
         ↓
[遍历获取所有 UISchema]
         ↓
[递归遍历 SchemaNode 树]
         ↓
[提取文本属性（label, title, placeholder, etc.）]
         ↓
[生成唯一 key（基于 schemaId + viewPath + propertyName）]
         ↓
         +
         ↓
[SPI 加载所有 UIModelProvider]
         ↓
[遍历获取所有 UIModel]
         ↓
[提取 title 属性]
         ↓
[生成唯一 key（ui-model.{path}.title）]
         ↓
         +
         ↓
[SPI 加载所有 MenuModelProvider]
         ↓
[遍历获取所有 MenuModel]
         ↓
[提取 title 属性]
         ↓
[生成唯一 key（menu.{fullName}.title）]
         ↓
         +
         ↓
[SPI 加载所有 AuthorityProvider]
         ↓
[遍历获取所有 Authority]
         ↓
[提取 title 属性]
         ↓
[生成唯一 key（authority.{fullName}.title）]
         ↓
         +
         ↓
[读取静态消息文件（META-INF/ouroboros/i18n/messages/*.json）]
         ↓
[解析 JSON 结构：{category: {key: message}}]
         ↓
[使用 category 作为 source，key 作为实际 key]
         ↓
         ↓
[合并所有条目构建 I18nLocale]
         ↓
[导出为 JSON 格式]
```

#### 2.2.2 语言环境使用流程

```
[前端启动]
    ↓
[请求语言环境 API: GET /api/i18n/locale/{locale}]
    ↓
[后端从存储加载语言环境（支持多数据源合并）]
    ↓
[返回 JSON 语言环境]
    ↓
[前端缓存语言环境]
    ↓
[渲染 UI Schema 时替换文案]
```

#### 2.2.3 同步文案流程

```
[用户点击同步文案按钮]
    ↓
[选择是否删除多余文案（removeOrphaned）]
    ↓
[调用同步 API: POST /api/i18n/manage/sync]
    ↓
[提取最新文案模板]
    ↓
[获取现有语言环境]
    ↓
[合并逻辑：保留现有翻译 + 补充缺失的 key]
    ↓
[可选：删除不在模板中的 key]
    ↓
[保存到数据库]
    ↓
[返回同步结果（新增数量、删除数量）]
    ↓
[前端显示成功提示并刷新列表]
```

## 3. 数据模型设计

### 3.1 核心模型

```java
// 语言环境模型
public interface I18nLocale {
  String getLocale();              // 语言代码（zh-CN, en-US）

  String getName();                // 语言名称（简体中文, English）

  String getVersion();             // 语言环境版本

  Boolean getEnabled();            // 是否启用

  Boolean getIsDefault();          // 是否默认语言

  Map<String, Map<String, String>> getMessages(); // namespace -> (key -> 翻译文本)

  TextDirection getDirection();    // 文本方向（LTR/RTL）

  Long getTimestamp();             // 生成时间戳
}

// 语言环境条目（用于提取阶段）
public interface I18nEntry {
  String getNamespace();           // namespace（schemaPath/menu/authority/common/base）

  String getKey();                 // key（文案key）

  String getSourceText();          // 原始文本

  String getContext();             // 上下文（UI Schema 路径）

  String getPropertyName();        // 属性名（label, title, etc.）
}

// 文本方向枚举
public enum TextDirection {
  LTR,  // 从左到右（Left-to-Right）
  RTL   // 从右到左（Right-to-Left）
}
```

### 3.2 语言环境 JSON 格式

**完整语言环境格式**（用于语言环境加载）：

```json
{
  "locale": "zh-CN",
  "name": "简体中文",
  "version": "1.0.0",
  "enabled": true,
  "isDefault": true,
  "direction": "LTR",
  "timestamp": 1706169600000,
  "messages": {
    "/sys/user-management": {
      "page.crud.columns.userName.label": "用户名",
      "page.crud.columns.userName.placeholder": "请输入用户名",
      "page.crud.headerToolbar[0].label": "新增用户",
      "page.crud.filter.userName.placeholder": "搜索用户名"
    },
    "/order/order-list": {
      "page.filter.orderNo.placeholder": "搜索订单号",
      "page.crud.columns.orderNo.label": "订单号"
    },
    "ui-model": {
      "/sys/user-management.title": "用户管理",
      "/order/order-list.title": "订单列表"
    },
    "menu": {
      "system.title": "系统管理",
      "system.user-management.title": "用户管理",
      "dev.data-model.title": "数据模型"
    },
    "authority": {
      "system.title": "系统权限",
      "system.user.title": "用户管理",
      "system.user.create.title": "创建用户"
    },
    "common": {
      "save": "保存",
      "cancel": "取消",
      "success": "操作成功"
    },
    "validation": {
      "required": "此字段为必填项",
      "email": "请输入有效的邮箱地址"
    }
  }
}
```

**语言摘要格式**（用于语言列表，不含 messages）：

```json
{
  "locale": "zh-CN",
  "name": "简体中文",
  "version": "1.0.0",
  "enabled": true,
  "isDefault": true,
  "messageCount": 1250,
  "direction": "LTR",
  "hasDbRecord": true
}
```

**字段说明**：

- `hasDbRecord`：标记该语言是否在数据库中有记录
    - `true`：语言在数据库中有记录，可以删除
    - `false`：语言仅来自文件Provider，不能删除，但可以通过启用/禁用操作创建数据库记录

### 3.3 Key 生成规则

#### 3.3.1 Key 结构

```
结构：messages[namespace][key] = 翻译文本

Namespace 类型：
1. schemaPath：UI Schema 的路径（如：/sys/user-management）
2. ui-model：固定值，所有 UI Model 文案
3. menu：固定值，所有菜单文案
4. authority：固定值，所有权限文案
5. common：固定值，通用文案（从静态消息文件提取）
6. validation：固定值，验证消息（从静态消息文件提取）
7. 其他：从静态消息文件的 category 动态生成
```

#### 3.3.2 UI Schema Key 规则

```
Namespace：{schemaPath}
Key：{viewPath}.{propertyName}

示例：
messages["/sys/user-management"]["page.crud.columns.userName.label"] = "用户名"
messages["/sys/user-management"]["page.crud.headerToolbar[0].label"] = "新增用户"
messages["/order/order-list"]["page.filter.orderNo.placeholder"] = "搜索订单号"

规则：
1. Namespace（schemaPath）：UI Schema 的路径
   - 保持原始路径格式（以 / 开头）
   - 去除结尾的 .json
   - 示例：/sys/user-management.json → /sys/user-management

2. Key：{viewPath}.{propertyName}
   - viewPath：视图树路径（从根节点到目标节点）
     * 使用节点的 id/name/type 构建路径
     * 忽略以 u: 开头的 id（自动生成的临时 id）
     * 数组元素使用索引：{propertyName}[{index}]
     * 支持 localeKey：如果节点有 localeKey 属性，直接使用作为完整 key
   - propertyName：属性名称
     * label, title, placeholder, description, tooltip, remark 等
     * 支持对象类型的属性（如 title: {type: 'tpl', tpl: '文案'}）
   
完整示例：
UI Schema: /sys/user-management.json
节点路径: page → crud → columns → userName
属性: label
Namespace: /sys/user-management
Key: page.crud.columns.userName.label

特殊处理：
- localeKey 优先：如果节点有 localeKey="common.save"，则直接使用 "common.save" 作为 key
- 数组索引：headerToolbar[0].label 而不是 headerToolbar.0.label
- 忽略 u: 前缀：id="u:abc123" 会被忽略，使用 name 或 type
```

#### 3.3.3 UI Model Key 规则

```
Namespace：ui-model（固定值）
Key：{path}.{propertyName}

示例：
messages["ui-model"]["/sys/user-management.title"] = "用户管理"
messages["ui-model"]["/order/order-list.title"] = "订单列表"

规则：
1. Namespace：固定为 "ui-model"
2. Key：{path}.{propertyName}
   - path：UI Model 的路径
   - propertyName：当前仅支持 title
```

#### 3.3.4 菜单 Key 规则

```
Namespace：menu（固定值）
Key：{fullName}.{propertyName}

示例：
messages["menu"]["system.title"] = "系统管理"
messages["menu"]["system.user-management.title"] = "用户管理"
messages["menu"]["dev.data-model.title"] = "数据模型"

规则：
1. Namespace：固定为 "menu"
2. Key：{fullName}.{propertyName}
   - fullName：MenuModel 的 fullName 属性（层级结构用 . 分隔）
   - propertyName：当前仅支持 title
```

#### 3.3.5 权限树 Key 规则

```
Namespace：authority（固定值）
Key：{fullName}.{propertyName}

示例：
messages["authority"]["system.title"] = "系统权限"
messages["authority"]["system.user.title"] = "用户管理"
messages["authority"]["system.user.create.title"] = "创建用户"

规则：
1. Namespace：固定为 "authority"
2. Key：{fullName}.{propertyName}
   - fullName：Authority 的 fullName 属性（层级结构用 . 分隔）
   - propertyName：当前仅支持 title
```

#### 3.3.6 静态消息 Key 规则

```
Namespace：{category}（从 JSON 文件的第一级 key）
Key：{key}（从 JSON 文件的第二级 key）

静态消息文件位置：META-INF/ouroboros/i18n/messages/*.json

文件格式：
{
  "category": {
    "key": "message"
  }
}

示例文件：common.json
{
  "common": {
    "save": "保存",
    "cancel": "取消"
  }
}

生成结果：
messages["common"]["save"] = "保存"
messages["common"]["cancel"] = "取消"

规则：
1. Namespace：使用 JSON 文件中的 category 名称
2. Key：直接使用 key，不添加前缀
3. 支持多个文件，每个文件可以包含多个 category
4. 不依赖文件名，完全基于 JSON 内容结构
```

## 4. 核心接口设计

### 4.1 文案提取器

```java
// 文案提取接口
public interface TextExtractor {
  /**
   * 提取文案
   * @return 提取的语言环境条目列表
   */
  Either<OuroborosException, List<I18nEntry>> extract();
}

// UI Schema 文案提取实现
public class UISchemaTextExtractor implements TextExtractor {
  // 需要提取的属性列表
  private static final String[] TEXT_PROPERTIES = {
      "label", "title", "placeholder", "description", "tpl",
      "tooltip", "remark", "disabledTip", "validationErrors",
      "submitText", "resetText", "confirmText", "cancelText",
      "btnLabel", "optionLabel", "emptyText", "loadingText", "noResultsText"
  };

  // 子节点属性
  private static final String[] CHILD_PROPERTIES = {
      "body", "columns", "items", "tabs", "controls", "fields", "dialog", "drawer",
      "headerToolbar", "footerToolbar", "actions", "buttons", "itemActions", "itemAction",
      "left", "right", "aside", "toolbar", "grid", "grids"
  };

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 1. 通过 SPI 加载所有 UISchemaProvider
    // 2. 收集所有 UI Schema
    // 3. 递归遍历提取文案
    // 4. 支持 localeKey 直接使用
    // 5. 忽略 u: 开头的 id
    // 6. 处理数组索引格式
    // 7. 支持对象类型的文本属性
  }
}

// 静态消息文案提取实现
public class StaticMessagesTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 1. 读取 META-INF/ouroboros/i18n/messages/*.json
    // 2. 解析两级 JSON 结构：{category: {key: message}}
    // 3. 使用 category 作为 source，key 作为实际 key
    // 4. Java 8 兼容：使用 IOUtils.toString() 而不是 readAllBytes()
  }
}

// UI Model 文案提取实现
public class UIModelTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 1. 通过 SPI 加载所有 UIModelProvider
    // 2. 提取 title 属性
    // 3. 生成 key：ui-model.{path}.title
  }
}

// 菜单文案提取实现
public class MenuTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 1. 通过 SPI 加载所有 MenuModelProvider
    // 2. 提取 title 属性
    // 3. 生成 key：menu.{fullName}.title
  }
}

// 权限树文案提取实现
public class AuthorityTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 1. 通过 SPI 加载所有 AuthorityProvider
    // 2. 提取 title 属性
    // 3. 生成 key：authority.{fullName}.title
  }
}
```

### 4.2 语言环境提供者（SPI）

```java
// 语言环境提供者接口
public interface I18nLocaleProvider {
  /**
   * 获取指定语言的语言环境
   * @param locale 语言代码
   * @return 语言环境，如果不存在返回 empty
   */
  Optional<I18nLocale> getDictionary(String locale);

  /**
   * 获取所有支持的语言
   * @return 语言代码列表
   */
  List<String> getSupportedLocales();
}

// 文件系统实现（开发时）
@Priority(0)
public class FileSystemI18nLocaleProvider implements I18nLocaleProvider {
  // 从工作空间加载语言环境文件
  // 使用 Proxy 模式解决 SPI 和 Spring IoC 集成问题

  @Priority(0)
  public static class Proxy implements I18nLocaleProvider {
    private static FileSystemI18nLocaleProvider provider;
    // 委托给实际的 Spring Bean
  }
}

// 数据库实现（运行时）
@Priority(100)
public class DatabaseI18nLocaleProvider implements I18nLocaleProvider {
  // 从数据库加载语言环境
  // 使用 Proxy 模式解决 SPI 和 Spring IoC 集成问题

  @Priority(100)
  public static class Proxy implements I18nLocaleProvider {
    private static DatabaseI18nLocaleProvider provider;
    // 委托给实际的 Spring Bean
  }
}
```

### 4.3 语言环境服务

```java
// 只读服务（前端使用）
public interface I18nLocaleService {
  /**
   * 获取语言环境（包含完整翻译内容）
   * 多个版本的语言环境会自动合并，高版本覆盖低版本
   * @param locale 语言代码
   * @return 语言环境
   */
  Either<OuroborosException, I18nLocale> getLocale(String locale);

  /**
   * 获取所有支持的语言（仅元数据，不含 messages）
   * @return 语言列表
   */
  List<I18nLocale> getSupportedLanguages();

  /**
   * 保存语言环境
   * @param locale 语言环境
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> saveLocale(I18nLocale locale);
}

// 管理服务（开发工具使用）
public interface I18nLocaleManageService {
  /**
   * 获取所有语言列表（包括禁用的）
   * @return 语言摘要列表
   */
  List<I18nLanguageSummary> getAllLanguages();

  /**
   * 获取语言详情
   * @param locale 语言代码
   * @return 语言详情
   */
  Optional<I18nLocale> getLanguageDetail(String locale);

  /**
   * 导出语言文件
   * @param locale 语言代码
   * @return 导出结果
   */
  Either<OuroborosException, ExportResult> exportLanguage(String locale);

  /**
   * 保存完整语言环境
   * @param locale 语言环境对象
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> saveLocale(I18nLocale locale);

  /**
   * 从提取器同步文案到数据库
   * @param locale 语言代码
   * @param removeOrphaned 是否删除现有locale中多出来的key（默认false）
   * @return 同步结果（新增和删除的条目数量）
   */
  Either<OuroborosException, SyncResult> syncFromExtractors(String locale, boolean removeOrphaned);

  /**
   * 设置默认语言
   * @param locale 语言代码
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> setDefaultLocale(String locale);

  /**
   * 删除语言
   * @param locale 语言代码
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> deleteLanguage(String locale);

  /**
   * 启用/禁用语言
   * @param locale 语言代码
   * @param enabled 是否启用
   * @return 是否成功
   */
  Either<OuroborosException, Boolean> setLanguageEnabled(String locale, boolean enabled);

  /**
   * 提取文案生成语言环境模板
   * @param sourceLocale 源语言
   * @return 语言环境模板
   */
  Either<OuroborosException, I18nLocale> extractLocaleTemplate(String sourceLocale);

  /**
   * 导出翻译模板
   * @return 包含所有文案键的模板文件
   */
  Either<OuroborosException, ExportResult> exportTemplate();
}
```

### 4.4 REST API

```java
// 只读 API（前端使用）
@RestController
@RequestMapping("/api/i18n")
public class I18nLocaleController {

  /**
   * 获取语言环境
   * GET /api/i18n/locale/{locale}
   */
  @GetMapping("/locale/{locale}")
  @Anonymous
  public WebApiResult<I18nLocale> getLocale(@PathVariable String locale) {
    return localeService.getLocale(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 获取支持的语言列表
   * GET /api/i18n/languages
   */
  @GetMapping("/languages")
  @Anonymous
  public ItemsWebApiResult<LanguageOption> getLanguages() {
    List<LanguageOption> options = localeService.getSupportedLanguages().stream()
        .map(locale -> new LanguageOption(
            locale.getLocale(),
            locale.getName(),
            locale.getVersion(),
            locale.getIsDefault()
        ))
        .collect(Collectors.toList());
    return ItemsWebApiResult.success(options);
  }
}

// 管理 API（开发工具使用）
@RestController
@RequestMapping("/api/i18n/manage")
public class I18nLocaleManageController {

  /**
   * 获取所有语言列表（包括禁用的）
   * GET /api/i18n/manage/languages
   */
  @GetMapping("/languages")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<List<I18nLanguageSummary>> getAllLanguages() {
    List<I18nLanguageSummary> languages = manageService.getAllLanguages();
    return WebApiResult.success(languages);
  }

  /**
   * 获取语言详情
   * GET /api/i18n/manage/languages/{locale}
   */
  @GetMapping("/languages/{locale}")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<I18nLocale> getLanguageDetail(@PathVariable String locale) {
    return manageService.getLanguageDetail(locale)
        .map(WebApiResult::success)
        .orElse(WebApiResult.fail("语言不存在"));
  }

  /**
   * 导出语言文件
   * GET /api/i18n/manage/languages/{locale}/export
   */
  @GetMapping("/languages/{locale}/export")
  @Authority("systemSetting.i18nManagement")
  public ResponseEntity<byte[]> exportLanguage(@PathVariable String locale) {
    // 返回文件下载
  }

  /**
   * 导出翻译模板
   * GET /api/i18n/manage/template/export
   */
  @GetMapping("/template/export")
  @Authority("systemSetting.i18nManagement")
  public ResponseEntity<byte[]> exportTemplate() {
    // 返回模板文件下载
  }

  /**
   * 导入语言文件
   * POST /api/i18n/manage/import
   */
  @PostMapping("/import")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<Boolean> importLocale(@RequestParam("file") MultipartFile file) {
    // 解析上传的 JSON 文件并保存
  }

  /**
   * 从提取器同步文案
   * POST /api/i18n/manage/sync
   */
  @PostMapping("/sync")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<SyncResult> syncFromExtractors(@RequestBody SyncRequest request) {
    return manageService.syncFromExtractors(request.getLocale(), request.getRemoveOrphaned())
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 设置默认语言
   * POST /api/i18n/manage/languages/{locale}/set-default
   */
  @PostMapping("/languages/{locale}/set-default")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<Boolean> setDefaultLocale(@PathVariable String locale) {
    return manageService.setDefaultLocale(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 删除语言
   * DELETE /api/i18n/manage/languages/{locale}
   */
  @DeleteMapping("/languages/{locale}")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<Boolean> deleteLanguage(@PathVariable String locale) {
    return manageService.deleteLanguage(locale)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }

  /**
   * 启用/禁用语言
   * POST /api/i18n/manage/languages/{locale}/enabled
   */
  @PostMapping("/languages/{locale}/enabled")
  @Authority("systemSetting.i18nManagement")
  public WebApiResult<Boolean> setLanguageEnabled(
      @PathVariable String locale,
      @RequestParam boolean enabled) {
    return manageService.setLanguageEnabled(locale, enabled)
        .map(WebApiResult::ok)
        .getOrElseGet(error -> WebApiResult.fail(error.getMessage()));
  }
}
```

## 5. 前端集成方案

前端已实现语言环境加载和文案替换功能，支持以下特性：

- 根据 locale 加载语言环境（不关注版本号）
- namespace + key 结构的文案查询
- 参数替换：支持 `{{param}}` 格式（需传参）和 `${param}` 格式（amis 模板语法）
- UI Schema、菜单、权限树的文案自动替换

## 6. 管理界面

### 6.1 语言管理页面

**位置**：系统设置 → 语言管理  
**权限**：`systemSetting.i18nManagement`  
**功能**：

- 语言列表展示（语言代码、名称、状态、默认语言、文案数量、文本方向、版本、数据库记录状态）
- 操作列使用下拉菜单组织：
    - 独立按钮：查看、导出
    - 下拉菜单"操作"：同步文案、设为默认、启用/禁用、删除
- 按钮状态控制：
    - 设为默认：已是默认或未启用时禁用
    - 启用/禁用：默认语言时禁用
    - 删除：默认语言或无数据库记录时禁用
- 头部工具栏：刷新、导出语言模板、导入翻译文件

### 6.2 同步文案功能

**触发方式**：点击语言列表中的"同步文案"按钮  
**界面**：对话框形式，包含：

- 隐藏字段：locale（当前语言代码）
- 复选框：删除多余文案（removeOrphaned，默认不勾选）
- 说明文字：勾选后将删除不在提取器中的翻译键

**同步逻辑**：

- `removeOrphaned = false`：只补充缺失的翻译键，保留现有翻译
- `removeOrphaned = true`：完全同步，删除多余的翻译键

**结果反馈**：Toast 提示显示"同步完成！新增 X 条，删除 Y 条"

### 6.3 导入导出功能

**导出模板**：

- 按钮：导出语言模板
- 功能：提取所有文案键生成模板文件
- 格式：完整的 I18nLocale JSON 格式

**导出语言**：

- 按钮：每行的"导出"按钮
- 功能：导出指定语言的完整翻译文件
- 格式：I18nLocale JSON 格式

**导入翻译**：

- 按钮：导入翻译文件
- 界面：文件上传对话框
- 支持：JSON 格式的 I18nLocale 文件
- 验证：文件格式、必填字段检查
- 导入逻辑：
    - 语言已存在：保持原有的enabled和isDefault状态
    - 语言不存在：isDefault设为false，enabled遵从导入文件

### 6.4 设为默认功能

**触发方式**：点击"设为默认"按钮  
**确认提示**：确定要将 {locale} 设为默认语言吗？  
**逻辑**：

1. 将所有语言的 `isDefault` 设为 `false`
2. 将指定语言的 `isDefault` 设为 `true`，`enabled` 设为 `true`
3. 如果语言不在数据库中，自动创建记录
4. 清除所有语言缓存

### 6.5 启用/禁用功能

**触发方式**：点击"启用"或"禁用"按钮  
**确认提示**：确定要启用/禁用 {locale} 语言吗？  
**逻辑**：

1. 检查是否为默认语言（默认语言不能禁用）
2. 如果语言不在数据库中，自动创建记录（同代码同名，messages为空）
3. 更新enabled状态
4. 清除所有语言缓存

### 6.6 删除语言功能

**触发方式**：点击"删除"按钮  
**确认提示**：确定要删除 {locale} 语言吗？此操作不可恢复！  
**逻辑**：

1. 检查是否为默认语言（默认语言不能删除）
2. 检查是否有数据库记录（无记录不能删除）
3. 删除数据库记录
4. 清除所有语言缓存
4. 刷新列表显示

## 7. 开发工作流

### 7.1 语言环境生成流程

```
1. 开发者在语言管理页面点击"导出语言模板"
   ↓
2. 调用 GET /api/i18n/manage/template/export
   ↓
3. 后端遍历所有 TextExtractor 提取文案
   ↓
4. 生成语言环境模板（包含所有自动提取的分类）
   ↓
5. 导出为 JSON 文件下载
   ↓
6. 开发者使用翻译工具翻译
   ↓
7. 开发者手动添加静态消息分类的文案
   ↓
8. 通过"导入翻译文件"上传翻译后的文件
   ↓
9. 保存到数据库
```

### 7.2 SPI 集成方案

**问题**：SPI 加载的类无法注入 Spring Bean  
**解决方案**：Proxy 模式

```java

@Component
public class DatabaseI18nLocaleProvider {
  public DatabaseI18nLocaleProvider() {
    Proxy.provider = this;  // 构造函数中设置静态引用
  }

  @Priority(100)
  public static class Proxy implements I18nLocaleProvider {
    private static DatabaseI18nLocaleProvider provider;

    @Override
    public Optional<I18nLocale> getDictionary(String locale) {
      return Optional.ofNullable(provider)
          .flatMap(p -> p.getLocale(locale));
    }

    @Override
    public List<String> getSupportedLocales() {
      return Optional.ofNullable(provider)
          .map(DatabaseI18nLocaleProvider::getSupportedLocales)
          .orElse(Collections.emptyList());
    }
  }
}
```

**SPI 注册**：

```
# META-INF/services/com.ouroboros.i18n.provider.I18nLocaleProvider
com.ouroboros.i18n.runtime.provider.JarI18nLocaleProvider
com.ouroboros.i18n.runtime.provider.FileSystemI18nLocaleProvider$Proxy
com.ouroboros.i18n.runtime.provider.DatabaseI18nLocaleProvider$Proxy
```

### 7.3 语言环境版本合并机制

**应用场景**：

- 开发时的通用文案（静态消息）
- 应用部署后运营时的优化文案
- 多个版本合并为最终语言环境

**合并逻辑**：

```java
/**
 * 语言环境合并逻辑：
 * 1. 加载同一 locale 的所有版本语言环境（开发版本 + 运营版本）
 * 2. 按版本号从低到高排序
 * 3. 逐个合并 messages：
 *    - 一级 key 相同时，合并二级 messages
 *    - 二级 key 重复时，高版本覆盖低版本
 * 4. 返回合并后的语言环境
 */
public I18nLocale mergeLocales(List<I18nLocale> locales) {
  // 按版本排序
  locales.sort(Comparator.comparing(I18nLocale::getVersion));

  Map<String, Map<String, String>> mergedMessages = new HashMap<>();

  for (I18nLocale locale : locales) {
    for (Map.Entry<String, Map<String, String>> primaryEntry : locale.getMessages().entrySet()) {
      String primaryKey = primaryEntry.getKey();
      Map<String, String> secondaryEntries = primaryEntry.getValue();

      mergedMessages.computeIfAbsent(primaryKey, k -> new HashMap<>())
          .putAll(secondaryEntries); // 高版本覆盖低版本
    }
  }

  return new I18nLocaleImpl(
      locales.get(locales.size() - 1).getLocale(),
      locales.get(locales.size() - 1).getName(),
      locales.get(locales.size() - 1).getVersion(), // 使用最高版本号
      mergedMessages
  );
}
```

## 8. 性能优化

### 8.1 缓存策略

```java

@Service
public class I18nLocaleServiceImpl implements I18nLocaleService {

  @Cacheable(value = "i18n-locale", key = "#locale")
  public Either<OuroborosException, I18nLocale> getLocale(String locale) {
    // 1. 加载同一 locale 的所有版本语言环境
    List<I18nLocale> locales = loadAllVersions(locale);

    // 2. 按版本合并
    I18nLocale merged = mergeLocales(locales);

    return Either.right(merged);
  }

  @CacheEvict(value = "i18n-locale", key = "#locale.locale")
  public Either<OuroborosException, Boolean> saveLocale(I18nLocale locale) {
    // 保存时清除缓存
  }
}
```

### 8.2 前端缓存

前端根据 locale 缓存语言环境，不关注版本号。后端返回的语言环境已经是合并后的最终版本。

### 8.3 文案提取优化

- **递归深度限制**：最大深度 20 层，防止无限递归
- **重复节点检测**：避免循环引用导致的性能问题
- **批量处理**：一次性加载所有 Provider 数据，减少 SPI 调用次数
- **异步处理**：大量文案提取时使用异步处理，提供进度反馈

## 9. 扩展性设计

### 9.1 支持其他文案来源

```java
// 扩展文案提取器 - 错误信息
public class ErrorMessageTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 从异常类中提取错误信息
  }
}

// 扩展文案提取器 - 配置项
public class ConfigurationTextExtractor implements TextExtractor {
  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    // 从配置元数据中提取描述文本
  }
}

// 扩展语言环境提供者
public class RemoteI18nLocaleProvider implements I18nLocaleProvider {
  // 从远程服务加载语言环境
}
```

### 9.2 支持参数化文案

文案支持两种参数格式：

- `{{param}}`：需要传递参数的占位符，如 `"最大长度为 {{max}}"`
- `${param}`：amis 模板字符串语法，不需要传递参数，如 `"请输入${label}"`

### 9.3 支持复杂 UI 结构

- **localeKey 支持**：节点可以指定 `localeKey` 属性，直接使用作为翻译键
- **数组索引处理**：`headerToolbar[0].label` 格式，便于理解
- **对象类型属性**：支持 `title: {type: 'tpl', tpl: '文案'}` 格式
- **忽略临时 ID**：自动忽略 `u:` 开头的自动生成 ID

## 10. 实施状态

### 10.1 已完成功能 ✅

**核心功能**：

- [x] 数据模型设计（I18nLocale, I18nEntry, TextDirection）
- [x] 文案提取器实现（UI Schema, UI Model, Menu, Authority, Static Messages, Error Code）
- [x] SPI 集成（Jar Provider + FileSystem/Database Provider with Proxy pattern）
- [x] 语言环境服务（只读 + 管理服务分离）
- [x] REST API（前端 + 管理 API）
- [x] 缓存支持（Spring Cache）

**管理功能**：

- [x] 语言列表管理（查看、导出、同步、设为默认、启用/禁用、删除）
- [x] 文案同步功能（增量 + 完全同步）
- [x] 导入导出功能（模板导出 + 文件导入，智能状态保持）
- [x] 管理界面（Amis JSON Schema，下拉菜单优化）
- [x] 权限控制（systemSetting.i18nManagement）
- [x] 数据库记录跟踪（hasDbRecord字段）
- [x] 自动创建语言记录（启用/禁用、设为默认时）

**高级特性**：

- [x] 多来源合并（classpath 元数据 + 文件系统 + 数据库）
- [x] 复杂 UI 结构支持（localeKey, 数组索引, 对象属性）
- [x] 静态消息提取（META-INF/ouroboros/i18n/messages）
- [x] Jackson 序列化支持（@JsonCreator 注解）
- [x] 通配符支持（CHILD_PROPERTIES: `definitions.*`, TEXT_PROPERTIES: `*Text`）
- [x] 智能缓存清除（状态变更时清除所有缓存）
- [x] 数据库Provider优先级控制（isDefault状态）

### 10.2 技术债务和改进点

**性能优化**：

- [ ] 大量文案提取时的异步处理
- [ ] 文案提取进度反馈

**用户体验**：

- [ ] 批量操作支持（批量导出、批量设置状态）
- [ ] 文案搜索和过滤功能

**扩展性**：

- 新增文案来源已可实现 `TextExtractor` 并通过 SPI 注册；不是待实现的插件化能力
- [ ] 远程语言环境同步
- [ ] 翻译记忆和术语库集成

## 11. 未来规划

### 11.1 Phase 2 - 接口错误信息国际化

- 统一错误码体系
- 错误信息语言环境管理
- 与异常体系集成
- 动态错误信息生成

### 11.2 Phase 3 - 高级特性

- 支持 RTL（从右到左）语言
- 支持复数形式和性别变化
- 集成机器翻译服务
- 翻译工作流和审批
- 版本控制和变更追踪

### 11.3 Phase 4 - 生态集成

- IDE 插件支持
- CI/CD 集成
- 翻译平台对接
- 多租户支持

---

**文档版本**：v2.0.0  
**更新日期**：2026-02-02  
**作者**：Ouroboros 架构团队  
**状态**：Phase 1 已完成，进入维护和优化阶段
