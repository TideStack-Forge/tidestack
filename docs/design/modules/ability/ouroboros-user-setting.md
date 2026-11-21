# ouroboros-user-setting 模块设计摘要

## 模块定位

`ouroboros-user-setting` 是 ability 层的用户设置能力模块，负责按“用户 + 客户端类型 + 设置项名称”维度保存、读取和删除用户级设置值，并对外暴露供前端自动加载的设置集合。它是用户偏好数据的持久化与读取面，而不是偏好语义的裁决者。

## 责任边界

该模块负责 `UserSetting` 模型、设置读写服务、设置提供者加载链与 `/api/user-setting` REST 入口，不负责设置项的业务含义定义，也不负责界面渲染。设置值以原始 JSON 字符串落库、由消费方自行解释，模块本身不做 schema 校验或默认值推导。

## 依赖关系

- 该域由 `ouroboros-user-setting-core` 与 `ouroboros-user-setting-runtime` 两个子模块组成，父 pom 只做聚合，不声明公共依赖。
- `ouroboros-user-setting-core` 依赖 `ouroboros-util`、`jackson-databind` 与 `vavr`；其中 `Try` 是其 `UserSettingService` 的返回值载体。该模块用 `gmavenplus-plugin` 的 `compileTests` 目标支持 Groovy 测试。
- `ouroboros-user-setting-runtime` 依赖 `ouroboros-user-setting-core`、`ouroboros-data-typed-runtime`（`TypedDataModel` 持久化）、`ouroboros-web-security-core` 与 `ouroboros-web-security-runtime`（`@PermitAll` 等安全注解），并以 `swagger-annotations` 生成接口文档；`ouroboros-webapi-runtime` 只在 test 作用域。
- 被依赖面：`ability/ouroboros-ability-all/ouroboros-ability-all-runtime` 依赖 runtime 模块；`ability/ouroboros-widget/ouroboros-widget-user-dashboard` 直接依赖 `ouroboros-user-setting-core`，属于跨能力域的 core 级复用；其余引用在 `bom/pom.xml`。

## 关键抽象

- `UserSetting`：设置只读视图契约，同时暴露 `getRawSettingValue()` 原始 JSON 与 `getSettingValue()`、`getStringValue()`、`getNumberValue()` 等类型化读取。
- `DefaultUserSetting`：`UserSetting` 的默认实现，采用链式 setter。
- `UserSettingService`：写入与删除契约，方法以 `Try<Void>` 返回，覆盖单个设置到全量用户的删除粒度。
- `UserSettingProvider`：读取契约，接口注释明确其“分层加载机制”定位，便于后续叠加默认设置 Provider。
- `UserSettingEntity`：runtime 侧的持久化实体，通过 `TypedDataModel<String, UserSettingEntity>` 访问。

## 关键子模块

- `ouroboros-user-setting-core`：4 个 main java 文件，只有模型与服务契约，不引入 Spring 与数据访问。
- `ouroboros-user-setting-runtime`：6 个 main java 文件，含 `UserSettingAutoConfiguration`、`UserSettingController`、`UserSettingServiceImpl` 与请求/响应 DTO。
- `UserSettingAutoConfiguration`：以 `@DataModelScan` + `@ComponentScan` 装配，并在 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 双处注册。

## 扩展机制

扩展点集中在 `UserSettingProvider`：`UserSettingController` 以 `List<UserSettingProvider>` 注入全部实现，`UserSettingServiceImpl` 自身同时实现 `UserSettingService` 与 `UserSettingProvider`，因此新增默认值来源或外部设置源只需新增一个 Provider 实现，无需改动控制层。通用规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `暂无独立深度设计文档`：当前仓库中尚无该模块的补充专题页。

## 演进状态

该模块已经完成 core / runtime 两段拆分，core 保持无框架依赖，整体处于收敛状态，共 10 个 main java 文件，规模小且无未文档化的第三子模块。唯一需要注意的漂移迹象是 `UserSettingServiceImpl` 同时实现了写入服务与读取提供者两个角色，使 Provider 的分层加载语义在默认实现处被合并；如果后续要接入多来源合并，应先把“默认 Provider”与“持久化 Provider”拆开，而不是在该实现里继续叠加分支。
