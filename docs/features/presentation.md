# 页面与接口

将模型和业务能力交付为页面、菜单、微前端及统一 HTTP 接口。

## 功能组成

| 功能项 | 当前行为与使用边界 |
| --- | --- |
| 页面与导航 | UI 模型、菜单模型和 main-app 组织页面路由、标签页及 AMIS 渲染，菜单扩展通过独立装饰状态和正式注册机制工作。 |
| 微前端与 SDK | qiankun 基座装配微应用，SDK 提供平台交互合同；平台组件库运行时只消费已导入配置，上传治理属于开发模块。 |
| 接口模型 | 高代码 RequestMapping 和低代码 WebAPIModel 进入同一注册链，再统一导出 OpenAPI 3.1。 |
| 组件与偏好 | Widget 管理组件定义及用户仪表盘；UserSetting 按用户/客户端/名称保存设置，设置内容由消费方解释。 |
| 国际化与错误 | i18n 与 error-code 模型组织翻译资源和统一错误码，错误处理仍沿平台异常体系。 |

## 实现情况

基座、SDK、接口建模及页面生成已有实现。NAV-001 记录导航写入所有权欠缺；接口版本化合同与安全/异常导出覆盖存在 API-001；用户设置不自动校验 JSON Schema 或推导默认值。

本页按源码与配置说明能力。测试入口说明可复跑资产，实际运行与线上发布状态由对应版本的交付证据确认。

## 启用条件

页面和微前端必须经正式元数据/组件库装配，按登录身份和权限加载。前端 build 成功不代表行为测试或版本交付通过。

## 相关代码

| 代码与职责入口 | 行为测试追溯 |
| --- | --- |
| [core/ouroboros-ui-model](../../core/ouroboros-ui-model) · [设计/指南](../design/modules/core/ouroboros-ui-model.md) | [测试入口](../../core/ouroboros-ui-model/ouroboros-ui-model-core/src/test/java/com/ouroboros/uimodel/SimpleUIModelIconTest.java) |
| [core/ouroboros-menu-model](../../core/ouroboros-menu-model) · [设计/指南](../design/modules/core/ouroboros-menu-model.md) | [测试入口](../../core/ouroboros-menu-model/ouroboros-menu-model-runtime/src/test/java/com/ouroboros/menu/controller/MenuControllerAuthorityMenusTest.java) |
| [core/ouroboros-webapi-model](../../core/ouroboros-webapi-model) · [设计/指南](../design/modules/core/ouroboros-webapi-model.md) | [测试入口](../../core/ouroboros-webapi-model/ouroboros-webapi-model-core/src/test/java/com/ouroboros/webapi/SimpleWebAPIModelTest.java) |
| [core/ouroboros-i18n](../../core/ouroboros-i18n) · [设计/指南](../design/modules/core/ouroboros-i18n.md) | [测试入口](../../core/ouroboros-i18n/ouroboros-i18n-dev/src/test/java/com/ouroboros/i18n/dev/controller/I18nLocaleDevManageControllerTest.java) |
| [core/ouroboros-error-code](../../core/ouroboros-error-code) · [设计/指南](../design/modules/core/ouroboros-error-code.md) | [测试入口](../../core/ouroboros-error-code/ouroboros-error-code-core/src/test/java/com/ouroboros/errorcode/core/ModuleSmokeTest.java) |
| [ability/ouroboros-platform-component-lib](../../ability/ouroboros-platform-component-lib) · [设计/指南](../design/modules/ability/ouroboros-platform-component-lib.md) | 未检出独立行为测试；按上层/交付场景判断 |
| [ability/ouroboros-widget](../../ability/ouroboros-widget) · [设计/指南](../design/modules/ability/ouroboros-widget.md) | [测试入口](../../ability/ouroboros-widget/ouroboros-widget-manage/src/test/java/com/ouroboros/widget/manage/controller/WidgetSchemaManageControllerHttpStatusTest.java) |
| [ability/ouroboros-user-setting](../../ability/ouroboros-user-setting) · [设计/指南](../design/modules/ability/ouroboros-user-setting.md) | [测试入口](../../ability/ouroboros-user-setting/ouroboros-user-setting-runtime/src/test/java/com/ouroboros/usersetting/controller/UserSettingControllerHttpStatusTest.java) |
| [frontends/web/main-app](../../frontends/web/main-app) | [测试入口](../../frontends/web/main-app/tests/fieldsetSpacingStyle.test.js) |
| [frontends/web/ouroboros-sdk](../../frontends/web/ouroboros-sdk) | [测试入口](../../frontends/web/ouroboros-sdk/tests/unit/build/build-output.test.ts) |
| [frontends/web/ouroboros-debug-console-topbar](../../frontends/web/ouroboros-debug-console-topbar) | [测试入口](../../frontends/web/ouroboros-debug-console-topbar/tests/unit/config/sync-package.test.js) |

## 关联文档

- [docs/requirements/frontend/menu-extension.md](../requirements/frontend/menu-extension.md)
- [docs/api/README.md](../api/README.md)
- [docs/design/modules/frontends/README.md](../design/modules/frontends/README.md)
