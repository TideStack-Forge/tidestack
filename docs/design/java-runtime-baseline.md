# Java 版本说明

- **当前编译基线**: Java 21
- **当前字节码基线**: Java 21
- **后端框架基线**: Spring Boot 3.5.11
- **默认运行镜像**: JDK 21（`eclipse-temurin:21-jdk-noble`）

根 `pom.xml` 和 `bom/pom.xml` 的当前主线配置均为 `maven.compiler.source=21`、`maven.compiler.target=21`、`spring-boot.version=3.5.11`。平台镜像默认线已切到 JDK 21，代码语言/API 基线也按 Java 21 管理。

## 当前约束

- 新代码按 Java 21 与 Spring Boot 3.5.11 编写。
- 不再依赖 Jabel 把 Java 21 语法降级到 Java 8 字节码。
- Java 21 是当前默认编码与编译基线；更高版本 Java API 必须另有决策和验证。
- `legacy-jdk8` 模块 profile 已从当前主线 POM 中移除；不要新增同名 profile 作为 Java 21 构建入口。

## 构建配置

根构建入口当前配置：

```xml
<properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <spring-boot.version>3.5.11</spring-boot.version>
</properties>
```

Maven 编译插件使用 JDK 21 toolchain：

```xml
<configuration>
    <jdkToolchain>
        <version>21</version>
    </jdkToolchain>
    <source>21</source>
    <target>21</target>
    <parameters>true</parameters>
</configuration>
```

## Jabel 历史说明

旧主线曾使用 Jabel 插件，在 Java 17 编译器上使用部分新语法，并降级输出 Java 8 字节码。Spring Boot 3.5 主线升级后，这条兼容构建路径已经不再是当前默认事实。

维护历史文档或迁移证据时可以保留 Jabel 相关记录；新增当前态指南、README、AGENT 入口或架构说明时，不应再把 “Java 8 runtime / Jabel 降级” 写成现状。

## 快速核查命令

```bash
rg -n '<spring-boot.version>|<maven.compiler.source>|<maven.compiler.target>' pom.xml bom/pom.xml
rg -n 'jabel|-Xplugin:jabel' --glob '**/pom.xml'
rg -n 'eclipse-temurin:21-jdk-noble|jdk21' build.sh Jenkinsfile platform
```

预期现状：

- 根 `pom.xml` 和 `bom/pom.xml` 显示 Java 21 + Spring Boot 3.5.11。
- 当前主线 POM 中不应再出现 Jabel 插件或 `-Xplugin:jabel`。
- 当前主线 POM 中不应再出现 `legacy-jdk8` profile。
- 默认镜像线指向 JDK 21，Jenkins 仍使用 JDK 21 toolchain 编译。

## 相关文档

- 根构建配置：`pom.xml`
- 依赖版本管理：`bom/pom.xml`
- 开发环境说明：`../development-guide.md`
