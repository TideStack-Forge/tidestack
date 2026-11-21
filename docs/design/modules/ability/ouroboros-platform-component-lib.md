# ouroboros-platform-component-lib 模块设计摘要

## 模块定位

`ouroboros-platform-component-lib` 是 ability 层的平台组件库**运行时消费端**，负责在应用启动时读取应用目录下的 `platform-component-lib.json`，把已导入的组件库拼装成微前端 entry 并注入 `web-app-info`。它是“组件库已被导入”这一事实在运行时的呈现面。

## 责任边界

该模块只做配置读取与 `web-app-info` 贡献，不负责组件库包的上传、清单解析、冲突校验、卸载或应用侧引用关系治理。它不提供任何管理控制器，也没有组件库安装语义——这些属于开发平台侧模块。

**与同名模块的区别（重要）**：`dev-module/ouroboros-dev-platform-component-lib` 是**另一个模块**，artifactId 为 `ouroboros-dev-platform-component-lib`（name 为 `Ouroboros Develop Platform Component Lib`），包名 `com.ouroboros.dev.componentlib`，含 `PlatformComponentLibController`、`DevAppPlatformComponentLibController`、`ComponentLibConflictException` 等 18 个类，负责组件库 ZIP 检查与导入治理，其文档见 [../dev-module/ouroboros-dev-platform-component-lib.md](../dev-module/ouroboros-dev-platform-component-lib.md)。本模块（ability 层）只消费那份治理结果，两者包名、artifactId、职责均不同，不可互相引用。

## 依赖关系

- artifactId 为 `ouroboros-platform-component-lib`，直接挂在 `ouroboros-root` 下，并在 `ability/pom.xml` 中注册为独立模块；它是**单模块**，pom 中没有 `<modules>`。
- 依赖面很窄：`spring-boot`、`spring-web`、`jakarta.annotation-api`、`common-app`（`AppContext`、`WebAppInfoContributor`、`OuroborosContextRefreshingEvent`）、`common-web`、`jackson-databind`、`vavr`。不依赖 `ouroboros-data`、不依赖安全模块。
- 被依赖面：`ability/ouroboros-ability-all/ouroboros-ability-all-runtime` 直接依赖它，`bom/pom.xml` 统一管理版本；除开发平台测试外，未发现其他 Java 模块引用其类型。

## 关键抽象

- `PlatformComponentLibAutoConfiguration`：`@Configuration` + `@ComponentScan` 的装配入口，`@PostConstruct` 只做启动日志。
- `PlatformComponentLibWebAppInfoContributor`：`@Component`，监听 `OuroborosContextRefreshingEvent` 完成加载。
- `PlatformComponentLibWebAppInfoContributor.SingletonHolder`：实现 `com.ouroboros.WebAppInfoContributor` 的静态持有者，是 SPI 注册实际暴露的类型。
- `PlatformComponentLibWebAppInfoContributor.ComponentLibConfig` / `ImportedComponentLib` / `ComponentLibEntry`：`platform-component-lib.json` 的配置模型、导入项与最终 entry（均为该文件内的嵌套类型）。
- `ouroboros.platformComponentLib.baseUrl`：可选的微前端基地址配置项，缺省时直接使用 `publicPath`。

## 关键子模块

该模块不分子模块，全部能力集中在两个类中：

- `PlatformComponentLibAutoConfiguration`：自动配置注册点。
- `PlatformComponentLibWebAppInfoContributor`：配置读取、entry 拼装与 `platformComponentLibraries` 注入。

## 扩展机制

该模块自身不定义 SPI，而是实现 `libs/common` 的 `WebAppInfoContributor` 扩展点，并通过 `META-INF/services/com.ouroboros.WebAppInfoContributor` 注册 `PlatformComponentLibWebAppInfoContributor$SingletonHolder`；`PlatformComponentLibAutoConfiguration` 同时注册在 `META-INF/spring.factories` 与 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`。新增 web-app-info 字段应新增 Contributor，而不是修改本模块。通用规则见 [../../cross-cutting/extension-mechanisms.md](../../cross-cutting/extension-mechanisms.md)。

## 深入文档索引

- `暂无独立深度设计文档`：当前仓库中尚无该模块的补充专题页；开发平台侧的组件库治理能力见 [../dev-module/ouroboros-dev-platform-component-lib.md](../dev-module/ouroboros-dev-platform-component-lib.md)。

## 演进状态

该模块职责单一且已收敛，2 个 main java 文件，无未文档化的子模块，也没有向管理面扩张的迹象。需要守住的是边界：一旦把组件库安装、校验或卸载逻辑放进这里，就会与 `ouroboros-dev-platform-component-lib` 形成平行路径。另外 `WebAppInfoContributor` 的实现依赖构造器里给静态 `SingletonHolder.INSTANCE` 赋值的写法，加载失败时只记录日志并注入空列表，属于静默降级点，排查 web-app-info 缺项时应优先检查这里。
