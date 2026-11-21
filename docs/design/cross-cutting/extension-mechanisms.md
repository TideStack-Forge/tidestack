# Ouroboros 扩展机制

## 目标

本页作为首轮共享机制权威页，统一说明 Ouroboros 中跨模块复用的扩展方式、适用边界与维护分工。机制分类以 Spring Boot AutoConfiguration、Java SPI、模板与脚本扩展为主；后续如有新的共享机制专题，也应先在这里建立分类与入口。

## Spring Boot AutoConfiguration

AutoConfiguration 适用于需要在 Spring 容器启动期完成装配、暴露 Bean、接入配置项或拼接运行时能力的场景。典型落点包括各模块的 `AutoConfiguration` 类、`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、兼容 `META-INF/spring.factories`、配置属性类和 starter 风格运行时模块。对维护者而言，AutoConfiguration 更适合“应用启动时自动接线”的扩展，而不适合运行时按实现清单发现扩展点。

如需进一步理解放置位置、注册方式与刷新时机，可继续阅读 [spring-autoconfiguration.md](spring-autoconfiguration.md)。

## Java SPI

Java SPI 适用于需要按接口发现多个实现、允许模块外追加实现，且不强依赖 Spring 容器自动装配顺序的场景。典型落点包括 `META-INF/services/`、扩展接口定义、服务实现类，以及像节点注册器、模型提供者、检查器这类可追加实现的扩展点。SPI 更适合“声明扩展契约并由实现方注册”的机制，而不是承担复杂运行时配置编排。

如需理解 SPI 的注册、排序、缓存与 Spring 桥接规则，可继续阅读 [spi-governance.md](spi-governance.md)。

如需理解 provider / center / integrator / builder 这类典型协作形态，可继续阅读 [provider-patterns.md](provider-patterns.md)。

## 模板与脚本扩展

模板与脚本扩展适用于需要把配置、表达式、脚本或可生成内容留给运行时解释的场景。典型落点包括 FreeMarker 模板、脚本执行入口、表达式引擎、JSON/Schema 模板、模块内 `META-INF` 资源和运行时读取的外部化定义。脚本或模板更适合“把变化留在配置与生成层”，不适合替代稳定的 Java 类型契约。

如需判断 FreeMarker、SpEL、JS/Groovy 分别该承接什么职责，可继续阅读 [template-and-script-runtime.md](template-and-script-runtime.md)。

如需判断共享资源应该落在哪个 `META-INF` 命名空间，可继续阅读 [meta-inf-resource-conventions.md](meta-inf-resource-conventions.md)。

## 模块摘要页与跨模块专题的分工

模块摘要页只保留简短机制摘要，用来说明该模块依赖了哪类扩展机制、扩展入口大致位于何处，以及为什么需要它；当机制是跨模块共享约定时，应链接回跨模块专题，而不是在每个模块页重复展开。换言之，模块摘要页只保留简短机制摘要并链接回跨模块专题，本页负责统一解释机制分类、典型落点、适用场景和选型边界。

## 姊妹专题索引

本目录的完整专题清单、阅读顺序与维护规则统一维护在 [README.md](README.md)——**不要在本页再维护第二份清单**。本页只负责扩展机制的**分类与选型**：装配、SPI、Provider、资源命名空间、模板/表达式/脚本、验证路径等机制分别由对应的姊妹专题承接，具体见 README 的「当前专题索引」。

在这些专题之外，后续仍需要继续细化扩展机制自身，例如更细粒度的资源治理和共享机制的测试与演进规则。届时本页继续作为索引页保留总览，并把深度内容拆到对应跨模块专题，而不是让各模块摘要页各自维护一套机制说明。
