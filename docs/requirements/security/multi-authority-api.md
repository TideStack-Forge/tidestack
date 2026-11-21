# 一个 WebAPI 归属多个权限项

## 背景与范围

调用方可能经不同业务管理权限访问同一接口。保留原已确认的数组值方案：单值 @Authority("a") 与多值 @Authority({"a", "b"}) 均可表达接口归属，不引入 Repeatable 的第二套写法。

## 行为约束

一个方法可归属多个权限全名，各 Authority 均收录该方法的 API 描述。平台凭据的接口入口仍以任一已授予权限命中为准；该注解不表达方法 AOP 的“必须同时具有全部权限”。

当前委托/数据参数扩展增加更严格的目标兼容边界：需要数据参数或使用委托凭据时，多个 Permit 只有同一目标和兼容字段/行定义才能合并，否则明确拒绝。原平台入口的任一命中不能解释成任意委托 API 可开放。机制以[统一权限设计](../../../security/delegated-authorization-design.md#22-正式求值-api)为准。

## 验收标准

1. 单值注解仍生成一个权限项，保持原行为。
2. 数组值生成对应多个权限项，每个都包含相同方法/路径描述。
3. 平台凭据命中任一授予即可通过接口入口；无权限仍拒绝。
4. 委托权限及请求参数存在多个不兼容目标时明确 Deny，不返回空参数放行。

正式定义为[Authority 注解](../../../security/ouroboros-web-security/ouroboros-web-security-annotation/src/main/java/com/ouroboros/security/annotation/Authority.java)和[AnnotationAuthorityProvider](../../../security/ouroboros-web-security/ouroboros-web-security-rbac-manage/src/main/java/com/ouroboros/security/rbac/provider/AnnotationAuthorityProvider.java)；求值验收由安全核心归属测试承担。这里维护预期，验收结果由对应任务或 CI 保存。
