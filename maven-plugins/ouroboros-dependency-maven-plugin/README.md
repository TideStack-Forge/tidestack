# Ouroboros Dependency Maven Plugin

`ouroboros-dependency-maven-plugin` 用于扩展 jar 项目打包时过滤平台基座已经提供的运行时依赖。它通常配合 `maven-dependency-plugin:copy-dependencies` 使用：先把扩展项目运行时依赖复制到 `target/lib`，再根据开发平台暴露的 runtime manifest 生成精简后的 `target/extension-lib`。

## 坐标

```xml
<plugin>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-dependency-maven-plugin</artifactId>
    <version>1.0.0</version>
</plugin>
```

插件版本独立于 Ouroboros 主工程版本管理，不参与主工程默认 CI/CD 构建和 deploy。维护插件本身时，在仓库根目录执行：

```bash
mvn -f maven-plugins/pom.xml clean deploy
```

## 扩展项目用法

`manifestUrl` 填开发平台的运行清单地址。简单说，就是把你平时访问开发平台的地址拿过来，在后面加上 `/ouroboros/runtime-manifest.json`。

例如开发平台访问地址是 `http://127.0.0.1:88`，就填写：

```text
http://127.0.0.1:88/ouroboros/runtime-manifest.json
```

如果是测试或正式环境，按实际域名和端口填写，例如：

```text
https://dev.example.com/ouroboros/runtime-manifest.json
```

```xml
<plugin>
    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-dependency-maven-plugin</artifactId>
    <version>1.0.0</version>
    <executions>
        <execution>
            <id>filter-platform-provided-dependencies</id>
            <phase>package</phase>
            <goals>
                <goal>filter</goal>
            </goals>
            <configuration>
                <!-- 开发平台访问地址 + /ouroboros/runtime-manifest.json -->
                <manifestUrl>http://127.0.0.1:88/ouroboros/runtime-manifest.json</manifestUrl>
                <inputDirectory>${project.build.directory}/lib</inputDirectory>
                <outputDirectory>${project.build.directory}/extension-lib</outputDirectory>
                <reportFile>${project.build.directory}/ouroboros-dependency-filter-report.json</reportFile>
                <ignoreVersionDifferences>false</ignoreVersionDifferences>
            </configuration>
        </execution>
    </executions>
</plugin>
```

默认策略：

- `groupId:artifactId:version` 与 manifest 精确匹配时，jar 会被剔除。
- `groupId:artifactId` 相同但版本不同时，插件输出 warning，并保留扩展项目自己的 jar。
- 设置 `ignoreVersionDifferences=true` 后，同名异版本依赖也会被视为基座已提供并剔除。

交付扩展包时，优先上传扩展项目自身 jar 和 `target/extension-lib` 中保留下来的 jar。`target/ouroboros-dependency-filter-report.json` 会记录每个依赖的 copy/exclude 决策。
