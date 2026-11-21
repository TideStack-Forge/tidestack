# Ouroboros 应用开发指南：元数据驱动方法

> 新的作者态 JSON 元数据源码可放在 `src/main/metadata`，通过 Maven 映射到 `META-INF/ouroboros/**` 后仍由现有 Provider + Center 加载。目录、短后缀和 IDE 编辑器边界见 [Metadata Source Editor Guide](metadata-source-editor-guide.md)。

> 业务模型的动态 Action、Event、消费订阅、注解接入及 Java / TypeScript 类型生成见[业务行为开发指南](business-model-behaviors.md)。

## 1. 引言：Ouroboros 的开发哲学

欢迎来到 Ouroboros 低代码开发平台！本平台的核心开发原则是**元数据驱动配置**。在本生态系统中，您将通过一系列声明式的 JSON 文件来定义模块的结构、外观、行为和安全性，而不是在 Java 代码中进行硬编码。

构建后的资源统一从 `META-INF/ouroboros` 发现；新建后缀类元数据放在 `src/main/metadata`，具体源码、产物路径及兼容读取边界以[元数据源码与编辑指南](metadata-source-editor-guide.md)为准。下文的 `ui-model/` 等分类用于理解旧资源布局，不是要求把新文件写回旧目录。本指南将引导您了解此路径下每个关键文件和子目录的用途，向您展示如何以“Ouroboros 的方式”构建新功能。

我们将以“文档管理”模块为例，贯穿整个指南，以说明这些概念如何协同工作。

## 2. Ouroboros 模块剖析：分步指南

构建一个新功能模块通常涉及创建和关联多个元数据文件。以下是标准的开发工作流程。

### 第 1 步：定义你的模块 (`app-modules.json`)

首先，您必须将您的新功能注册为一个可被平台发现的模块。这在 `app-modules.json` 文件中完成，它是您模块的主要入口点。

- **目的：** 声明一个模块的存在及其高级属性。
- **操作：** 在模块的 `src/main/metadata/` 下创建或修改 `app-modules.json`，构建后进入 `META-INF/ouroboros/metadata/`。

- **示例 (`document-management-webapp/.../app-modules.json`):**
  ```json
  [
    {
      "fullName": "DocumentManagement",
      "title": "文档管理",
      "icon": "fa fa-folder-open",
      "menu": "DocumentManagement",
      "authorities": [
        "DocumentManagement"
      ],
      "order": 50
    }
  ]
  ```
- **字段说明:**
  - `fullName`: 模块的唯一机器可读名称。
  - `title`: 出现在 UI 中的人类可读名称。
  - `icon`: 模块的图标 (例如，来自 Font Awesome)。
  - `menu`: 通过菜单 `fullName` 关联菜单定义。
  - `authorities`: 通过权限 `fullName` 关联一组权限定义。

### 第 2 步：定义权限与接口归属

新权限元数据使用 `src/main/metadata/**/*.authority.json`，构建到 `META-INF/ouroboros/metadata/`。文件名按短后缀识别类型，权限身份来自 `fullName`，不要求文件名等于权限全名。旧 `META-INF/ouroboros/authority/*.json` 是 [JarAuthorityProvider](../../security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/provider/JarAuthorityProvider.java) 保留的兼容来源，不作为新文件默认位置。

[文档管理权限源定义](../../app-module/document-management/document-management-webapp/src/main/metadata/documentManagement.authority.json) 同时关联 API、UI 和菜单。下面仅节选其字段形状，完整声明沿源文件维护：

```json
{
  "fullName": "DocumentManagement",
  "title": "文档管理",
  "apis": ["POST /api/document-libraries"],
  "uiViews": ["/document-management/library-list"],
  "menuItems": ["DocumentManagement"]
}
```

`apis` 以方法和路径表达接口归属，`uiViews` 与 `menuItems` 表达展示入口。具体控制器路由、权限声明和实际授权需要一起核对；UI 可见不代表后端已获准。创建权限定义也不会自动为账号授予角色或该权限。

高代码接口可通过 `com.ouroboros.security.annotation.Authority` 声明归属，由 [AnnotationAuthorityProvider](../../security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/provider/AnnotationAuthorityProvider.java) 沿原机制收集。同一权限全名的定义与接口关联不应另建注册表。单值/数组形式和多权限接口行为见[多权限接口需求](../requirements/security/multi-authority-api.md)。

角色授予、字段/行限制与当前凭据边界仍由[安全共同设计](../../security/DESIGN.md)和[委托授权](../../security/delegated-authorization-design.md)维护；业务代码消费正式 Permit/Deny，不根据前端菜单或任意 claim 自行放行。

### 第 3 步: 定义 UI 路由与渲染类型 (`ui-model/`)

现在，您需要为要在应用中显示的每个页面定义一个“UI 模型”。这非常重要，因为它充当了**路由表**的角色，告诉平台“在哪个路径下，使用哪种方式渲染页面”。

- **目的:** 定义平台中所有用户界面的核心入口和渲染契约。每个 `ui-model` 文件都代表一个可路由的页面或视图。
- **操作:** 在 `src/main/metadata/` 下创建 `*.ui-model.json`。页面身份由声明的 `path` 决定；旧 `ui-model/` 是兼容资源布局。
- **示例 (`.../ui-model/dev/apps.json`):
  ```json
  {
    "path": "/dev/apps",
    "type": "amis",
    "title": "打开应用"
  }
  ```
- **核心字段说明:**
  - `path`: 定义了页面的访问路径 (URL)。
  - `type`: 定义了页面的渲染方式。这是最关键的字段。
    - `"amis"` 或 `"ui-schema"`: 表示这个页面由一个具体的 Amis JSON 文件来定义，平台会接着去 `ui-schema/` 目录中寻找对应的文件进行渲染。
    - `"micro-web"`: 表示这个页面是一个独立的微前端应用。
    - `"internal"`: 表示这是一个前端基座直接实现的平台内置页面。

简单来说，`ui-model` 决定了“去哪里”，而 `ui-schema` 提供了“看什么”（当 `type` 是 `amis` 时）。

### 第 4 步: 构建 UI 页面蓝图 (`ui-schema/`)

一旦您在 `ui-model` 中定义了一个类型为 `amis` 的页面，您就需要提供该页面的具体实现，即“页面蓝图”。

- **目的:** 当 `ui-model` 的渲染类型指向 Amis 时，此目录下的文件提供了页面的具体实现。
- **操作:** 创建一个与 `ui-model` 的 `path` 相对应的 JSON 文件。新建 JAR 元数据使用 `src/main/metadata/**/*.ui-schema.json`，以 `name` 和 `schema` 包裹 AMIS 定义，名称与路径映射见[共同目录约定](metadata-source-editor-guide.md)。旧 `ui-schema/dev/apps.json` 是 `/dev/apps` 的兼容裸 Schema 形状。
- **核心作用:** 文件内容是使用 [百度 Amis](https://baidu.github.io/amis/) 框架编写的完整 JSON 定义，描述了页面的布局、组件（表格、表单等）、数据交互和行为。

### 第 5 步 (可选): 配置模块设置 (`configurations.json`)

`configurations.json` 文件可用于定义模块特有的设置。该文件包含配置元数据对象数组，运行时值由配置服务另行存取。

- **目的：** 为模块提供灵活的配置选项，例如特性开关、默认值或第三方集成参数。
- **操作：** 在 `src/main/metadata/` 目录下创建 `configurations.json` 文件。
- **格式：** `List<ConfigurationMetadata>` 对应的 JSON 数组，每个对象声明 `key`、`valueType`、`defaultValue` 等元数据；见[配置定义共同约定](../design/cross-cutting/configuration-definition-conventions.md)。

### 第 6 步：创建导航菜单 (`menu-model/`)

为了让用户能够访问您新创建的页面，您必须将它们添加到一个导航菜单中。

- **目的：** 构建应用程序的导航栏或侧边栏菜单。
- **操作：** 在 `src/main/metadata/` 下创建 `*.menu.json`，声明菜单的 `fullName`、`title`、`url` 等字段，以 `fullName` 与应用模块关联。文件名不代替菜单身份；旧 `menu-model/` 是兼容资源布局。

### 第 7 步 (可选): 集成微前端资源 (`micro-web/`)

如果您的模块包含一个独立的微前端应用或组件，并希望将其集成到 Ouroboros 平台中，`micro-web/` 目录将用于注册和配置这些资源。

- **目的：** 注册和配置微前端应用或组件的入口点、资源文件等，使其能够被主平台动态加载和管理。
- **操作：** JAR 元数据使用 `src/main/metadata/**/*.micro-web.json`，应用 home 使用 `micro-web/*.micro-web.json`。声明微前端名称、类型、资源路径及加载策略；构建与发现范围遵循[共同目录约定](metadata-source-editor-guide.md)。

### 第 8 步 (可选): 复用字段类型 (`field-types/`)

为了在整个平台中保持数据一致性，Ouroboros 提供了一个通用字段类型目录。在您的 UI 或数据模型中定义字段之前，请检查 `field-types/` 中是否已存在可复用的定义。

- **目的：** 标准化通用数据字段（如电子邮件、电话、货币等），并预定义其数据类型、UI 渲染方式和验证规则。
- **操作：** 在创建数据模型时，引用这些字段。如果您需要创建一种新的、可广泛应用的字段类型，新建字段类型使用 `src/main/metadata/**/*.field-type.json`；应用 home 使用 `field-types/**/*.field-type.json`，不能用普通 `.json` 代替。

## 3. 总结与开发工作流

在 Ouroboros 中开发新模块或功能时，请遵循以下步骤：

1.  **定义模块:** 在 `app-modules.json` 中创建或更新模块定义。
2.  **定义权限:** 在 `src/main/metadata/` 下创建 `*.authority.json`，通过 `fullName` 关联模块与接口，并按正式角色机制授予权限。
3.  **定义 UI 路由与渲染类型:** 使用 `*.ui-model.json` 声明页面路径与渲染类型。
4.  **构建 UI 页面蓝图:** 使用 `*.ui-schema.json` 提供页面定义，采用共同目录约定中的包裹格式。
5.  **(可选) 配置模块设置:** 在 `src/main/metadata/` 目录下创建 `configurations.json`。
6.  **创建导航:** 使用 `*.menu.json` 声明菜单身份和目标 URL。
7.  **(可选) 集成微前端资源:** 使用 `*.micro-web.json` 注册微前端资源。
8.  **(可选) 利用复用:** 优先引用已有字段类型，新定义使用 `*.field-type.json`。
9.  **实现后端:** 通过现有 Controller、WebAPI 或业务 Action 实现接口，核对权限归属、授权与业务行为。
