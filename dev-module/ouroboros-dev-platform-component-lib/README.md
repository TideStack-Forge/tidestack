# Ouroboros Develop Platform Component Lib

平台组件库管理模块，用于管理和分发低代码平台的组件库。

## 功能特性

### 1. 平台组件库管理

- **上传安装**: 支持上传ZIP格式的组件库包，自动解压并部署微前端资源
- **版本管理**: 支持组件库版本控制，新版本需大于旧版本才能安装
- **组件检查**: 检查组件库清单文件（components.manifest.json）存在、可解析，且 name/version/publicPath 非空；组件资源仍需实际加载验证
- **冲突检测**: 检测publicPath和组件定义的重复，防止冲突
- **卸载管理**: 卸载前检查应用引用，确保不会影响正在使用的应用

### 2. 应用组件库管理

- **导入组件库**: 应用可以从平台组件库中导入需要的组件
- **移除组件库**: 从应用中移除不需要的组件库
- **版本控制**: 应用导入、更新或实际移除配置时通过 VCSManager 写入并提交；配置不存在或未导入时移除直接返回

### 3. 组件库结构

组件库包（ZIP格式）需包含以下结构：

```
component-lib.zip
├── components.manifest.json    # 组件库清单文件（必需）
└── dist/                       # 微前端资源目录
    ├── index.js
    └── ...
```

#### components.manifest.json 示例

```json
{
  "name": "my-component-lib",
  "version": "1.0.0",
  "publicPath": "/component-lib/my-lib",
  "description": "我的组件库",
  "author": "作者名称",
  "renderers": [
    {
      "name": "my-renderer",
      "label": "我的渲染器",
      "description": "自定义渲染器",
      "tags": [
        "custom"
      ]
    }
  ],
  "formItems": [],
  "optionsControls": [],
  "actions": [],
  "rules": [],
  "formulas": [],
  "rendererEditorPlugins": [],
  "actionEditorPlugins": [],
  "validatorEditorPlugins": []
}
```

## 技术架构

### 核心依赖

- `ouroboros-ui-model-microweb`: 微前端部署服务
- `ouroboros-dev-version-control-system`: 版本控制管理
- `ouroboros-dev-app-management`: 应用管理
- `ouroboros-util-zip`: ZIP文件操作

### 存储结构

```
workspace/
└── component-lib/              # 组件库根目录
    ├── lib-name@1.0.0/        # 组件库目录（名称@版本）
    │   ├── components.manifest.json
    │   └── dist/
    └── lib-name@1.0.1/
        ├── components.manifest.json
        └── dist/
```

### 应用配置

应用引入的组件库记录在应用目录下的 `platform-component-lib.json`:

```json
{
  "imports": [
    {
      "name": "my-component-lib",
      "version": "1.0.0",
      "publicPath": "/component-lib/my-lib"
    }
  ]
}
```

## API 接口

### 平台组件库管理

- `GET /api/platform-component-lib` - 获取所有组件库
- `GET /api/platform-component-lib/{name}` - 获取指定组件库
- `GET /api/platform-component-lib/component-defines` - 获取所有组件定义
- `GET /api/platform-component-lib/{name}/related-apps` - 获取使用该组件库的应用
- `POST /api/platform-component-lib/inspect` - 检查组件库包
- `POST /api/platform-component-lib` - 安装组件库
- `DELETE /api/platform-component-lib/{name}` - 卸载组件库

### 应用组件库管理

- `GET /api/dev/{appName}/platform-component-lib` - 获取应用的组件库配置
- `POST /api/dev/{appName}/platform-component-lib` - 导入组件库到应用
- `DELETE /api/dev/{appName}/platform-component-lib/{componentLibName}` - 从应用移除组件库

## 权限配置

模块已集成权限管理，支持以下权限点：

- 平台组件库查看、上传、卸载
- 应用组件库导入、移除

## 使用流程

### 1. 安装平台组件库

1. 准备组件库ZIP包（包含components.manifest.json和dist目录）
2. 在"平台组件库"页面点击"上传组件库"
3. 选择ZIP文件并上传
4. 系统自动检查、解压、部署微前端资源

### 2. 应用引入组件库

1. 进入应用开发界面
2. 打开"平台组件库"标签
3. 点击"导入组件库"
4. 选择已安装的平台组件库
5. 确认导入，系统自动更新应用配置并提交版本

### 3. 使用组件

导入成功后，在页面设计器中即可使用组件库提供的组件。

## 开发说明

### 添加新的组件类型

在 `PlatformComponentLibDTO` 中添加新的List字段，并更新相关的getter/setter方法。

### 扩展验证逻辑

在 `PlatformComponentLibServiceImpl.checkComponentLib()` 方法中添加自定义验证规则。

### 自定义部署逻辑

重写 `MicroWebDeployService` 的实现来支持不同的部署策略。

## 注意事项

1. **版本管理**: 组件库版本必须递增，不支持降级安装
2. **公共路径**: publicPath 必须唯一，不能与其他组件库冲突
3. **组件定义**: 同一类型和名称的组件不能重复定义
4. **卸载限制**: 被应用引用的组件库无法卸载
5. **文件格式**: 仅支持ZIP格式的组件库包
6. **清单文件**: components.manifest.json 必须位于ZIP包根目录

## 元数据文件

模块包含完整的元数据定义：

- **菜单**: `src/main/metadata/**/*.menu-model.json`（打包至 `META-INF/ouroboros/metadata/`）
- **开发菜单**: `src/main/metadata/*.dev-menu.json`
- **UI模型**: `src/main/metadata/**/*.ui-model.json`（打包至 typed 根）
- **UI Schema**: `META-INF/ouroboros/metadata/**/*.ui-schema.json`
- **权限**: `META-INF/ouroboros/authority/`

## 构建和部署

### 构建

在仓库根目录执行：

```bash
mvn -pl :ouroboros-dev-platform-component-lib -am clean install
```

### 部署

```bash
mvn -pl :ouroboros-dev-platform-component-lib -am clean deploy
```

模块会通过Spring Boot自动配置机制加载，无需额外配置。
