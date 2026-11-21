# 项目生成平台与模板发布治理

## 定位

项目生成平台是开发平台中的工具性能力，只负责在“微前端”“平台组件库”和“后端扩展包”入口下载源码 ZIP。它不编译生成项目，不写应用 VCS，不上传微前端包、平台组件库或扩展 Jar，也不新增平行注册表。

运行时唯一主链保持不变：微前端继续走 MicroWeb/平台组件库既有交付链路，后端扩展包继续走扩展 Jar 上传与安装链路。项目生成器只生成可追溯的本地开发起点。

## 模块边界

- 后端能力位于 `dev-module/ouroboros-dev-project-generation`，并由 `dev-module/ouroboros-dev-module-all` 聚合。
- 前端入口只使用既有 `ui-schema`/AMIS 渲染能力；各入口通过 `scope` 请求后端生成的普通 AMIS 表单 schema 和参数表单项，不新增基座 renderer 或平行表单规范。`scope` 取值为：`MICRO_FRONTEND`、`PLATFORM_COMPONENT_LIBRARY` 或 `BACKEND_EXTENSION`。
- 三个正式入口是应用内 `/dev/{appName}/micro-web`、应用内 `/dev/{appName}/extend-jar-management` 和公共菜单 `/platform-component-lib`。应用内平台组件库页面只负责选择已发布组件，不能生成或上传项目。
- 模板源码位于 `project-templates/templates/`，模板源码合同位于 `project-templates/contracts/v2/`；qualification、attestation 和 catalog publication 合同继续位于 `project-templates/contracts/v1/`。
- 模板验证和发布演练脚本位于 `project-templates/scripts/`，本地 publication 演练目录以执行时间为起点生成30天有效期，过期后重新生成；它不代表生产发布。生成的 qualification/publication 证据只写入 `project-templates/target/`，不得提交；同时生成 ignored 的 `project-templates/dist/` 作为本地联调和镜像打包的 runtime store，运行配置不得直接指向 `target`；develop 镜像打包时只把该 runtime store 复制进单独的 `docker/template-layer`。

## 官方模板

当前官方模板按用户可见模板族分三类：

- 微前端入口 4 个：`frontend.micro-app`（前端应用）、`frontend.layout`（自定义布局）、`frontend.component-library`（组件库）、`frontend.login`（自定义登录页）。
- 平台组件库入口 1 个：`frontend.platform-component-library`。
- 后端扩展包入口 1 个：`backend.extension.spring-autoconfigure`。

前端模板族当前开放 React/Webpack 与 Vue/Vite 两条稳定 branch；这些 branch 在 qualification 中仍以不可变 artifact 单独验证和发布。前端模板统一引入 qiankun 与 `ouroboros-sdk`，不引入 AMIS 依赖；后端扩展包首批只保留 Maven/Spring 自动配置扩展包模板。

## 配置表单

项目生成弹窗完全由普通 AMIS schema 渲染。弹窗沿既有表单操作同步显示“取消”和“生成并下载”，入口不配置空 `actions` 屏蔽表单按钮。模板族使用 `list-select` 展示名称和说明；React/Vue、Webpack/Vite 等少量互斥枚举使用 `button-group-select`；只有不适合平铺的长列表才使用 `select`；布尔项使用 `switch`；文本标识使用 `input-text`。级联由 AMIS `visibleOn`、选项级显隐或受控 `source` 表达，不增加专用表单组件。

用户只填写不能可靠派生的信息：前端填写技术栈、构建方式、npm 包名及各模板少量业务选项；后端填写 `groupId`、`artifactId`、`version`、`packageName`，可按需调整 `autoConfigurationClassName`。ZIP 根目录、源码标识、bundle/library 名称、示例组件类型和 Java 包路径由生成器统一派生，不能作为重复输入暴露给用户。

## 发布治理

模板发布必须按以下顺序执行：

1. 先发布不可变模板制品。
2. 为制品生成 canonical qualification descriptor。
3. 使用 Ed25519 签发 qualification attestation。
4. publisher 通过同一 gate 校验 descriptor、attestation、artifact digest、channel/profile、row identity、平台版本和 toolchain。
5. preview publication 或 stable promotion 通过后，才发布更高 sequence 的 signed catalog。

`SNAPSHOT` 平台版本不得写入生产 stable catalog。当前仓库只通过 `project-templates/scripts/test-release-pipeline.sh` 执行隔离 synthetic non-SNAPSHOT fixture，证明 stable promotion gate 可部署、可拒绝不匹配输入，并且不会写真实生产 catalog。

## 验证入口

- 前端模板：`bash project-templates/scripts/verify-frontend-templates.sh --qualification --clean`
- 前端模板族：`bash project-templates/scripts/verify-frontend-templates.sh --family frontend.micro-app --qualification --clean`
- 后端模板：`bash project-templates/scripts/verify-backend-templates.sh --qualification --clean --platform-smoke`
- 后端模板族：`bash project-templates/scripts/verify-backend-templates.sh --family backend.extension.spring-autoconfigure --qualification --clean --platform-smoke`
- 发布治理：`bash project-templates/scripts/verify-template-publication.sh --clean`

上述脚本都 fail closed：未知模板、缺 qualification report、零测试、缺 smoke、签名不匹配、unsafe URI、channel/profile 不匹配、SNAPSHOT stable promotion、旧 sequence replay 或同 sequence 异 digest 都必须失败。
