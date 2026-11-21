# Ouroboros I18n 前后端对接规范

本页是语言列表、语言包结构和词条约定的共同正文，适用于当前 `ouroboros-i18n-runtime` 与 Web 主应用。接口字段以 [Controller](../ouroboros-i18n-runtime/src/main/java/com/ouroboros/i18n/runtime/controller/I18nLocaleController.java) 和正式类型为依据；主应用的加载、缓存和渲染机制见 [React 集成设计](../../../frontends/web/main-app/docs/i18n-design.md)。

本文是对接说明，不能代替版本化 OpenAPI 合同。合同的权威输入、导出方向与尚缺的校验见 [契约生命周期](../../../docs/api/contract-lifecycle.md)。

## 1. 语言与词条模型

语言代码使用语言提供器实际提供的值，例如 `zh-CN`、`en-US`。客户端使用语言列表返回的 `value` 请求语言包，不自行把连字符改成下划线。

[I18nLocale](../ouroboros-i18n-core/src/main/java/com/ouroboros/i18n/model/I18nLocale.java) 定义以下字段：

| 字段 | 含义与消费方式 |
| --- | --- |
| `locale`、`name` | 语言代码和显示名称 |
| `version` | 语言包版本；主应用用语言列表中的版本与缓存版本作相等比较 |
| `enabled`、`isDefault` | 是否启用、是否为默认语言 |
| `direction` | `LTR` 或 `RTL`；主应用缺省按 `LTR` 处理 |
| `timestamp` | 生成时间戳，毫秒；不等同于客户端缓存写入时间 |
| `messages` | 二级字典：`messages[namespace][key] = string`；第二级 key 内的点和方括号属于 key 字面量 |

语言列表由服务聚合各 `I18nLocaleProvider` 的可用语言，只返回启用语言。语言包的提供、合并和服务端缓存由 [I18nLocaleServiceImpl](../ouroboros-i18n-runtime/src/main/java/com/ouroboros/i18n/runtime/service/I18nLocaleServiceImpl.java) 负责，前端消费合并后的字典。

## 2. HTTP 接口与响应包裹

以下均为相对后端基址的路径。两个接口用于启动时加载语言信息；本页不定义认证或权限模块的策略。

| 方法与路径 | 成功时的 `data` | 用途 |
| --- | --- | --- |
| `GET /api/i18n/languages` | `{ "items": LanguageOption[] }` | 获取语言选项、版本与默认语言标记 |
| `GET /api/i18n/locale/{locale}` | `I18nLocale` | 获取指定语言的完整字典 |

原始后端响应遵循 [AbstractWebApiResult](../../../libs/common/common-web/src/main/java/com/ouroboros/web/AbstractWebApiResult.java)：`code = 0` 表示成功，非零表示业务错误，默认错误码为 `-1`；`msg` 为消息，`data` 为业务数据。下面示例省略未使用的可选扩展字段，不代表完整的序列化输出。

### 2.1 语言列表

每个 [LanguageOption](../ouroboros-i18n-runtime/src/main/java/com/ouroboros/i18n/runtime/dto/LanguageOption.java) 包含 `value`、`label`、`version`、`isDefault`。`version` 用于缓存校验，`isDefault` 用于未保存偏好时选择初始语言；主应用同时消费这四个字段。

```json
{
  "code": 0,
  "msg": null,
  "data": {
    "items": [
      { "value": "zh-CN", "label": "简体中文", "version": "1.0.0", "isDefault": true },
      { "value": "en-US", "label": "English", "version": "1.0.0", "isDefault": false }
    ]
  }
}
```

### 2.2 完整语言包

```json
{
  "code": 0,
  "msg": null,
  "data": {
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
        "page.crud.headerToolbar[0].label": "新增用户"
      },
      "menu": { "system.user-management.title": "用户管理" },
      "common": { "save": "保存", "welcome": "欢迎 {{name}}" },
      "validation": { "minLength": "最小长度为 {{min}} 个字符" }
    }
  }
}
```

语言包不存在时，当前 Controller 把服务错误映射为 `WebApiResult.fail(message)`，默认 `code = -1`、`data = null`。消费者应检查业务结果和数据，不能仅根据 Controller 上的 Swagger `404` 注释推断真实 HTTP 状态码。

### 2.3 主应用的响应适配

[HTTP Client](../../../frontends/web/main-app/src/utils/http/client.ts) 将 `raw.code ?? raw.status ?? 0` 归一为同时含 `code` 和 `status` 的对象。因此主应用 `ApiResponse.status` 是客户端适配字段，不能据此把后端原始示例写成只含 `status`。

主应用通过现有 [I18nApi](../../../frontends/web/main-app/src/common/i18n/api.ts) 访问接口：语言列表读取 `response.data.data?.items`，语言包读取 `response.data.data`，语言包没有数据时抛错。业务组件复用该服务链，不另建 HTTP 包裹或加载器。

## 3. Namespace 与 key 约定

| Namespace | 维护内容 |
| --- | --- |
| UI Schema 标识，如 `/sys/user-management` | 该 Schema 的标题、字段标签、按钮和提示等文本 |
| `ui-model` | UI 模型文案 |
| `menu` | 菜单文案，例如 `system.user-management.title` |
| `authority` | 权限文案；这里只约定词条分类，权限设计由安全模块维护 |
| `common` | 跨页面公共文案 |
| `validation` | 验证消息 |
| `base` | 供 AMIS `registerLocale` 消费的基础组件文案 |
| 其他分类 | 来自相应提供器的静态消息分类 |

Schema 的 namespace 必须与渲染时传入的 `schemaId` 一致。词条 key 与提取器/Schema 处理器生成的路径对应，例如 `page.crud.columns.userName.label`；不能把路径字符串另拆成多层 JSON 对象。调整节点标识或数组结构时，应同步核对提取结果与语言包。

显式命名使用 `localeKey`、`localeKeys`、`localePath` 时，按现有 [UISchemaTextExtractor](../ouroboros-i18n-runtime/src/main/java/com/ouroboros/i18n/runtime/extractor/UISchemaTextExtractor.java) 和 [前端 Schema 处理器](../../../frontends/web/main-app/src/common/i18n/amis.ts) 核对同一 Schema 的 key；完整路径规则只在实现和前端设计中说明。

文本插值使用 `{{param}}`。主应用管理器仅替换已提供的 `word` 参数名；未提供的参数保留原标记。该机制不提供 ICU 复数规则或任意表达式求值。

## 4. 消费边界与排查

主应用的缓存、语言切换和事件订阅统一由 [i18n 单例入口](../../../frontends/web/main-app/src/common/i18n/index.ts) 管理；微前端通过主应用公共属性使用同一管理器。API 调用成功、语言包加载成功和所有页面刷新成功是不同的观察点。

| 症状 | 优先核对 |
| --- | --- |
| 语言列表为空 | 请求业务结果、`data.items`、提供器和启用标记 |
| 语言包加载失败 | 请求的 locale 是否来自语言列表、业务 `code/msg` 和 `data` |
| 文案未替换 | namespace/schemaId、完整 key、当前语言包内容和组件订阅 |
| 修改字典后仍见旧文本 | 语言列表版本、IndexedDB 条目与有效期，以及页面是否重新计算 Schema |

版本变化后的客户端缓存失效、菜单/页签/AMIS 的实际刷新和失败恢复应在目标运行环境验证，不能仅凭字段表或静态链接检查宣称通过。
