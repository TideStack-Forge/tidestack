# Ouroboros IDE 插件

`ide-plugins` 目录统一承载 Ouroboros 的 IDE 集成能力。IDEA 与 VS Code 插件只保留各自 IDE 的接入代码，公共的 Web 编辑器运行时、AMIS 表单 schema、元数据类型贡献和后续平台绑定合同都放在 `common` 下。

## 目录结构

```text
ide-plugins/
  common/
    metadata-editor-schema/   # 纯 JSON schema、编辑器注册表、i18n、创建规则和资产 manifest
    metadata-editor-runtime/  # 公共 AMIS 运行时、Web 壳、逻辑 Host 和测试
    contracts/          # 跨语言公共合同（TideStack Develop Config 等）
  intellij-plugin/          # JetBrains IntelliJ IDEA 插件
  vscode-plugin/        # VS Code 扩展
  docs/                # 跨 IDE 插件设计参考资料
  ARCHITECTURE.md       # 插件分层、边界和打包流
  DESIGN.md             # 功能设计、支持矩阵和验收检查
```

公共层拆为两个物理包：`common/metadata-editor-schema` 是纯资源包，维护元数据 schema、编辑器注册表、i18n、创建规则和资产 manifest；`common/metadata-editor-runtime` 是运行时包，npm 包名仍保持 `ouroboros-metadata-editor`，负责 Host、序列化、AMIS Web 壳和编辑器运行时代码。后续开发服务器元数据同步、缓存和线上资源浏览会收敛到规划中的 Metadata Workspace module，IDE 插件只实现 UI、文件系统、平台请求和调试等 adapter。

两个 IDE 插件的对外标识统一为 `tidestack.develop`；显示名统一为 `TideStack Develop`。

## 能力范围

| 能力 | IDEA | VS Code | 公共层 |
|---|---|---|---|
| JSON 元数据图形化编辑器 | JCEF 加载公共 Web 壳与 Host bundle | Webview 加载公共 Web 壳并直接调用公共 Host | `common/metadata-editor-runtime` |
| 新规范后缀识别 | 由共同 metadata contribution 声明的 `.<类型>.json` 后缀 | 同 IDEA | `MetadataTypeContribution` |
| 旧规范路径识别 | `resources/META-INF/ouroboros` 子路径及 app/config 根集合文件 | 同 IDEA | 路径匹配工具 |
| 元数据文件模板 | `New > 创建 TideStack 模型` | `TideStack 模型：新建文件` 命令 | `metadata-editor-schema/metadata-types/*` |
| 新建文件入口归一化 | Project View New 菜单 adapter | Command Palette / New File / Explorer adapter | 创建合同 / case corpus / 模板 serializer |
| 多语言 | 从 schema 包 i18n 资源读取 | package.nls 由 schema 包 i18n 生成 | `common/metadata-editor-schema/i18n` |
| 部署与远程调试 | 已有实现 | 规划中 | TideStack Develop Config |
| 项目模板 | 规划中，优先使用 IDE 原生模板能力 | 规划中，优先使用命令式脚手架 | 模板描述规划中 |
| 项目与开发平台绑定 | 项目设置页 | 命令面板入口 | `.tidestack/develop*.json` |
| Metadata Workspace | 规划中，Tool Window / VirtualFileSystem 视 IDE 能力选择 | 规划中，Tree View / virtual document 视 IDE 能力选择 | source / priority / catalog 合同 |
| 开发服务器资源浏览/管理 | 规划中，优先融合文件视图，否则 Tool Window | 规划中，优先融合 Explorer，否则 Tree View | Workspace catalog + capability |

## 构建与测试

```bash
# 公共 schema 资源包
cd ide-plugins/common/metadata-editor-schema
npm ci
npm run build

# 公共编辑器 runtime 包
cd ../metadata-editor-runtime
npm run build

# VS Code 扩展
cd ../../vscode-plugin
npm run compile

# IDEA 插件
cd ../intellij-plugin
./gradlew test
./gradlew buildPlugin
```

VS Code 构建会把公共包 vendor 到 `ide-plugins/vscode-plugin/dist/vendor`，并按 `metadataEditorAssets.json` 把 Webview 模板、runtime、schema、合同与 AMIS 资产复制到 `dist/media`。IDEA 构建会先生成同一公共 Host 的浏览器 bundle，再按同一资产 manifest 校验并打入公共 Web 壳、runtime、schema、合同与 AMIS SDK。

## 自动化回归

从仓库根目录运行 `node ide-plugins/tests/run.mjs --check` 预检，运行 `node ide-plugins/tests/run.mjs` 验证共享浏览器用例、VS Code 原生文档/Webview 与 IntelliJ Platform/JCEF。分层执行、证据和覆盖边界见[测试运行手册](../testing/integration/regression/ide/metadata-editors/harness.playbook.md)。

## 开源仓库同步

`ide-plugins` 是 TideStack Develop 插件的主开发位置，三个独立开源仓库只作为发布镜像维护：

- `tidestack-develop-plugin-common`
- `tidestack-develop-vsc`
- `tidestack-develop-intellij`

同步由 `ide-plugins/sync.manifest.json` 声明，执行入口为：

```bash
# 只读检查，默认检查全部仓库
node scripts/open-source/sync-ide-plugins.mjs --check --all

# 从 monorepo 导出到开源仓库
node scripts/open-source/sync-ide-plugins.mjs --sync --all

# 导出、验证、提交并推送
node scripts/open-source/sync-ide-plugins.mjs --sync --all --verify --commit --push
```

同步脚本会先生成临时导出镜像，再比较或覆盖目标仓库；`--check` 不会写入任何文件。首次真实同步会在每个开源仓库写入 `.tidestack-sync.json`，记录来源 commit、受管路径和内容哈希。之后 CI 可以用 `--check` 发现 monorepo 变更是否尚未导出。

当前 Phase 1 只管理插件源码、构建入口、公共资源和必要的 standalone 路径转换；README、LICENSE、插件市场描述等开源包装信息保留在独立仓库或通过 manifest 的保留规则处理。

## TideStack Develop Config

项目与开发平台的绑定关系跟随项目，但分成两层：

- `.tidestack/develop.json`：项目共享配置，可提交到代码库，保存 `developmentServerUrl`、`appName` 和可选 `editorServiceUrl`。
- `.tidestack/develop.local.json`：本地私有配置，已加入 `.gitignore`，保存 `devKey`、`editorAccessToken` 和可选的本机 `editorServiceUrl` 覆盖。

`profileId` 和 `containerId` 不手动配置、不写入本地配置文件。插件在需要部署、调试或解析平台目标时，用 `devKey` 向开发平台授权得到 profile，再按 `appName + profile` 自动解析目标容器。

两端通过 `common/contracts/tidestack-develop-config.json` 读取同一组路径、字段顺序和历史字段别名；IDE 只负责入口、表单、文件系统和后续调试/模板等原生能力接入。

在线编辑使用 `editorServiceUrl`，未指定时使用 `developmentServerUrl`。配置已有的 `devKey` 即可：宿主通过 `X-TideStack-Dev-Key` 请求头发送密钥，平台沿原认证扩展验证密钥的应用、有效期和所属账号，再进入原授权链，无需另外登录或填写编辑令牌。`editorAccessToken` 仅作为可选 Bearer 覆盖，显式配置时优先使用且不同时发送 devKey。两款 IDE 均支持代码上下文、语言会话和动态目录请求；凭据保留在宿主，关闭编辑器时释放语言会话。无开发平台时，按[本地编辑服务指南](../dev-module/ouroboros-dev-editor-service/README.md)启动独立服务，连接本地 Spring Boot、测试启动的服务或容器中的 helper。

## 相关文档

- [插件架构](ARCHITECTURE.md)
- [功能设计](DESIGN.md)
- [IntelliJ 与 VS Code 插件扩展点对比](docs/ide-extension-points-comparison.md)
- [元数据源代码编辑指南](../docs/guides/metadata-source-editor-guide.md)
