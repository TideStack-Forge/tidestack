#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd -P)"
MANIFEST="${REPO_ROOT}/scripts/open-source/manifests/logicflow/logicflow-public-repo-bootstrap.yaml"
CHECK_SCRIPT="${SCRIPT_DIR}/check-public-repo-leaks.sh"
VERSION="2.0.0-rc.1-SNAPSHOT"
OUTPUT_DIR="${REPO_ROOT}/target/logicflow-public-repo-dry-run"
CHECK=false

usage() {
  cat <<'USAGE'
Usage: bootstrap-public-repo-dry-run.sh [--check] [--output DIR]

Generates a local, history-free LogicFlow public repo dry-run. The output is a
throwaway snapshot and must not be treated as a real public repository.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check)
      CHECK=true
      shift
      ;;
    --output)
      if [[ $# -lt 2 ]]; then
        echo "Missing value for --output" >&2
        exit 2
      fi
      OUTPUT_DIR="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -f "${MANIFEST}" ]]; then
  echo "Missing export manifest: ${MANIFEST}" >&2
  exit 1
fi

if [[ ! -x "${CHECK_SCRIPT}" && ! -f "${CHECK_SCRIPT}" ]]; then
  echo "Missing leak check script: ${CHECK_SCRIPT}" >&2
  exit 1
fi

resolve_output_dir() {
  local output="$1"
  if [[ "${output}" != /* ]]; then
    output="${REPO_ROOT}/${output}"
  fi
  mkdir -p "$(dirname "${output}")"
  (cd "$(dirname "${output}")" && printf '%s/%s\n' "$(pwd -P)" "$(basename "${output}")")
}

OUTPUT_DIR_ABS="$(resolve_output_dir "${OUTPUT_DIR}")"
case "${OUTPUT_DIR_ABS}" in
  "${REPO_ROOT}/target/"*|"${REPO_ROOT}/tmp/testing-evidence/open-source/"*)
    ;;
  *)
    echo "Refusing to write dry-run outside target/ or tmp/testing-evidence/open-source/: ${OUTPUT_DIR_ABS}" >&2
    exit 1
    ;;
esac

parse_public_modules() {
  awk '
    /^(public_modules|support_modules):/ { in_public = 1; next }
    in_public && /^[a-z_]+:/ { exit }
    in_public && /^[[:space:]]+source:/ { source = $2 }
    in_public && /^[[:space:]]+target:/ {
      target = $2
      if (source != "" && target != "") {
        print source "|" target
      }
      source = ""
      target = ""
    }
  ' "${MANIFEST}"
}

copy_public_modules() {
  local entry source target
  while IFS='|' read -r source target; do
    if [[ -z "${source}" || -z "${target}" ]]; then
      continue
    fi
    if [[ ! -d "${REPO_ROOT}/${source}" ]]; then
      echo "Missing public module source: ${source}" >&2
      exit 1
    fi
    mkdir -p "${OUTPUT_DIR_ABS}/${target}"
    rsync -a \
      --exclude='target/' \
      --exclude='.flattened-pom.xml' \
      --exclude='.DS_Store' \
      "${REPO_ROOT}/${source}/" "${OUTPUT_DIR_ABS}/${target}/"
  done < <(parse_public_modules)
}

write_root_pom() {
  cat > "${OUTPUT_DIR_ABS}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ouroboros</groupId>
    <artifactId>ouroboros-logicflow</artifactId>
    <version>${VERSION}</version>
    <packaging>pom</packaging>

    <name>Ouroboros LogicFlow</name>
    <description>Standalone open-source LogicFlow build dry-run.</description>

    <modules>
        <module>ouroboros-util</module>
        <module>common-code-intelligence</module>
        <module>common-expression</module>
        <module>ouroboros-logicflow-bom</module>
        <module>ouroboros-logicflow-core</module>
        <module>ouroboros-logicflow-builtin-nodes</module>
        <module>ouroboros-logicflow-authoring</module>
        <module>ouroboros-logicflow-data-model</module>
        <module>samples/script-node-standalone</module>
    </modules>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <spring-boot.version>3.5.11</spring-boot.version>
        <junit.version>5.12.2</junit.version>
        <junit4.version>4.13.2</junit4.version>
        <junit.platform.version>1.12.2</junit.platform.version>
        <jackson3.version>3.1.4</jackson3.version>
        <jackson-annotations.version>2.21</jackson-annotations.version>
        <commons-lang3.version>3.20.0</commons-lang3.version>
        <jakarta.annotation-api.version>2.1.1</jakarta.annotation-api.version>
        <vavr.version>0.10.4</vavr.version>
        <json-schema-validator.version>3.0.6</json-schema-validator.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>common-expression</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>common-code-intelligence</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-util</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-collections4</artifactId>
                <version>4.4</version>
            </dependency>
            <dependency>
                <groupId>commons-io</groupId>
                <artifactId>commons-io</artifactId>
                <version>2.22.0</version>
            </dependency>
            <dependency>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-dependencies</artifactId>
                <version>\${spring-boot.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>org.junit</groupId>
                <artifactId>junit-bom</artifactId>
                <version>\${junit.version}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-core</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-builtin-nodes</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-authoring</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-data-model</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-data-core</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>tools.jackson.core</groupId>
                <artifactId>jackson-core</artifactId>
                <version>\${jackson3.version}</version>
            </dependency>
            <dependency>
                <groupId>tools.jackson.core</groupId>
                <artifactId>jackson-databind</artifactId>
                <version>\${jackson3.version}</version>
            </dependency>
            <dependency>
                <groupId>com.fasterxml.jackson.core</groupId>
                <artifactId>jackson-annotations</artifactId>
                <version>\${jackson-annotations.version}</version>
            </dependency>
            <dependency>
                <groupId>jakarta.annotation</groupId>
                <artifactId>jakarta.annotation-api</artifactId>
                <version>\${jakarta.annotation-api.version}</version>
            </dependency>
            <dependency>
                <groupId>org.apache.commons</groupId>
                <artifactId>commons-lang3</artifactId>
                <version>\${commons-lang3.version}</version>
            </dependency>
            <dependency>
                <groupId>io.vavr</groupId>
                <artifactId>vavr</artifactId>
                <version>\${vavr.version}</version>
            </dependency>
            <dependency>
                <groupId>com.networknt</groupId>
                <artifactId>json-schema-validator</artifactId>
                <version>\${json-schema-validator.version}</version>
            </dependency>
            <dependency>
                <groupId>junit</groupId>
                <artifactId>junit</artifactId>
                <version>\${junit4.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-launcher</artifactId>
            <version>\${junit.platform.version}</version>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.vintage</groupId>
            <artifactId>junit-vintage-engine</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>junit</groupId>
            <artifactId>junit</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <pluginManagement>
            <plugins>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-compiler-plugin</artifactId>
                    <version>3.15.0</version>
                    <configuration>
                        <release>21</release>
                        <source>21</source>
                        <target>21</target>
                        <encoding>UTF-8</encoding>
                        <parameters>true</parameters>
                    </configuration>
                </plugin>
                <plugin>
                    <groupId>org.apache.maven.plugins</groupId>
                    <artifactId>maven-surefire-plugin</artifactId>
                    <version>3.5.6</version>
                </plugin>
                <plugin>
                    <groupId>org.codehaus.gmavenplus</groupId>
                    <artifactId>gmavenplus-plugin</artifactId>
                    <version>5.1.0</version>
                    <configuration>
                        <targetBytecode>21</targetBytecode>
                    </configuration>
                </plugin>
            </plugins>
        </pluginManagement>
        <plugins>
            <plugin>
                <artifactId>maven-resources-plugin</artifactId>
                <version>3.3.1</version>
                <executions>
                    <execution>
                        <id>copy-metadata-sources</id>
                        <phase>process-resources</phase>
                        <goals>
                            <goal>copy-resources</goal>
                        </goals>
                        <configuration>
                            <outputDirectory>\${project.build.outputDirectory}/META-INF/ouroboros/metadata</outputDirectory>
                            <resources>
                                <resource>
                                    <directory>\${project.basedir}/src/main/metadata</directory>
                                    <filtering>false</filtering>
                                    <includes>
                                        <include>**/*</include>
                                    </includes>
                                    <excludes>
                                        <exclude>**/.gitkeep</exclude>
                                    </excludes>
                                </resource>
                            </resources>
                        </configuration>
                    </execution>
                </executions>
            </plugin>
            <plugin>
                <groupId>org.codehaus.gmavenplus</groupId>
                <artifactId>gmavenplus-plugin</artifactId>
                <executions>
                    <execution>
                        <goals>
                            <goal>compileTests</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
EOF
}

write_bom_pom() {
  mkdir -p "${OUTPUT_DIR_ABS}/ouroboros-logicflow-bom"
  cat > "${OUTPUT_DIR_ABS}/ouroboros-logicflow-bom/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.ouroboros</groupId>
        <artifactId>ouroboros-logicflow</artifactId>
        <version>${VERSION}</version>
    </parent>

    <artifactId>ouroboros-logicflow-bom</artifactId>
    <packaging>pom</packaging>
    <name>Ouroboros LogicFlow BOM</name>
    <description>Dependency alignment for public Ouroboros LogicFlow artifacts.</description>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-core</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-builtin-nodes</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-authoring</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-data-model</artifactId>
                <version>\${project.version}</version>
            </dependency>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-data-core</artifactId>
                <version>\${project.version}</version>
            </dependency>
        </dependencies>
    </dependencyManagement>
</project>
EOF
}

write_readme() {
  cat > "${OUTPUT_DIR_ABS}/README.md" <<'EOF'
# Ouroboros LogicFlow

Ouroboros LogicFlow is a Java 21 model execution library. This dry-run contains
the public core, official builtin nodes, authoring metadata provider, and the
public data-model node bundle that integrates with Ouroboros Data Model.

## Modules

- `ouroboros-logicflow-core`: runtime model, compiler, validator, diagnostics, and extension contracts.
- `ouroboros-logicflow-builtin-nodes`: official builtin nodes, including `ScriptNode`.
- `ouroboros-logicflow-authoring`: NodeDefine provider and official `META-INF/ouroboros/metadata/*.flow-node.json` resources.
- `ouroboros-logicflow-data-model`: public data-model node bundle backed by `ouroboros-data-core` contracts.
- `ouroboros-logicflow-bom`: Maven BOM for consumer dependency version alignment.

Internal platform adapters, development APIs, runtime application adapters, web
adapters, debug helpers, Excel nodes and platform exception nodes are not part
of this public surface.

## Build

```bash
mvn test
```

## Consumer Dependency Management

Consumers should import the BOM and depend on the modules they use. They should
not inherit the repository root parent.

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.ouroboros</groupId>
      <artifactId>ouroboros-logicflow-bom</artifactId>
      <version>2.0.0-rc.1-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

See `samples/script-node-standalone` for a standalone ScriptNode sample.

## Documentation

- Contribution guide: `CONTRIBUTING.md`
- Adapter integration guide: `docs/adapter-integration-guide.md`
- Data-model integration guide: `docs/data-model-integration.md`
EOF
  cp "${OUTPUT_DIR_ABS}/README.md" "${OUTPUT_DIR_ABS}/README.en.md"
  cat > "${OUTPUT_DIR_ABS}/README.md" <<'EOF'
# Ouroboros LogicFlow

Ouroboros LogicFlow 是面向 Java 21 的模型执行库。本公开仓库包含核心执行模型、官方内置节点、authoring metadata provider，以及与 Ouroboros Data Model 集成的公开 data-model 节点包。

English documentation: [`README.en.md`](README.en.md)

## 模块

- `ouroboros-logicflow-core`：运行时模型、编译器、校验器、诊断能力和扩展 contract。
- `ouroboros-logicflow-builtin-nodes`：官方内置节点，包括 `ScriptNode`。
- `ouroboros-logicflow-authoring`：NodeDefine provider 与官方 `META-INF/ouroboros/metadata/*.flow-node.json` 资源。
- `ouroboros-logicflow-data-model`：基于 `ouroboros-data-core` contract 的公开 data-model 节点包。
- `ouroboros-logicflow-bom`：面向消费者的依赖版本对齐 BOM。

平台 adapter、development API、debug helper、Excel 和平台异常节点随总仓公开。本独立分发保留选中的 LogicFlow 模块与共享 `ouroboros-util` 构建支持。

## 构建

```bash
mvn test
```

## 消费方依赖管理

消费方应导入 BOM，并按需依赖具体模块；不要继承本仓库 root parent。

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>com.ouroboros</groupId>
      <artifactId>ouroboros-logicflow-bom</artifactId>
      <version>2.0.0-rc.1-SNAPSHOT</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

`samples/script-node-standalone` 提供了 standalone ScriptNode 示例。

## 文档

- 贡献指南：`CONTRIBUTING.md` / `CONTRIBUTING.en.md`
- Adapter 接入指南：`docs/adapter-integration-guide.md` / `docs/adapter-integration-guide.en.md`
- Data Model 集成指南：`docs/data-model-integration.md` / `docs/data-model-integration.en.md`
EOF
}

write_contributing() {
  cat > "${OUTPUT_DIR_ABS}/CONTRIBUTING.md" <<'EOF'
# Contributing to Ouroboros LogicFlow

Thank you for improving the public LogicFlow project. This repository is a
public projection of the internal Ouroboros monorepo, so maintainers import
accepted changes back through a manifest-scoped sync workflow.

## Development Baseline

- Java 21 is required.
- Run `mvn test` before submitting a change.
- Keep public modules free of platform runtime, web, security, development,
  message queue, script engine and internal metadata dependencies.
- `ouroboros-logicflow-data-model` may depend on public `ouroboros-data-core`
  contracts, but other LogicFlow public modules should not import data APIs.
- Add or update tests for behavior changes.

## Public Boundary Rules

- Add executable nodes through `NodeBuilder` SPI descriptors.
- Add authoring metadata as `.flow-node.json` resources consumed by
  `LogicFlowNodeDefineProvider`.
- Add script engines through `LogicFlowScriptEngine`; do not embed a concrete
  platform script engine in builtin nodes.
- Add data-model nodes only through `ouroboros-logicflow-data-model`.
- Put web, runtime facade, debug helper, Excel and platform exception concerns
  in adapters outside this public repository.

## Contribution Policy

External contributions are accepted with DCO sign-off.

## Pull Request Checklist

- [ ] `mvn test` passes.
- [ ] Public module boundaries remain clean.
- [ ] New node builders are registered through SPI.
- [ ] NodeDefine JSON is updated for user-visible node changes.
- [ ] Documentation is updated for API or adapter behavior changes.
EOF
  cp "${OUTPUT_DIR_ABS}/CONTRIBUTING.md" "${OUTPUT_DIR_ABS}/CONTRIBUTING.en.md"
  cat > "${OUTPUT_DIR_ABS}/CONTRIBUTING.md" <<'EOF'
# 参与贡献 Ouroboros LogicFlow

感谢你改进公开 LogicFlow 项目。本仓库是内部 Ouroboros monorepo 的公开投影，维护者会通过 manifest 作用域同步流程把被接受的变更回流到内部仓库。

English documentation: [`CONTRIBUTING.en.md`](CONTRIBUTING.en.md)

## 开发基线

- 需要 Java 21。
- 提交前运行 `mvn test`。
- 公开模块不得引入 platform runtime、web、security、development、message queue、具体 script engine 或内部 metadata 依赖。
- `ouroboros-logicflow-data-model` 可以依赖公开的 `ouroboros-data-core` contract；其他 LogicFlow 公开模块不得导入 data API。
- 行为变更必须新增或更新测试。

## 公开边界规则

- 可执行节点通过 `NodeBuilder` SPI descriptor 增加。
- authoring metadata 以 `.flow-node.json` 资源提供，并由 `LogicFlowNodeDefineProvider` 消费。
- 脚本引擎通过 `LogicFlowScriptEngine` 增加；内置节点不得绑定具体平台脚本引擎。
- data-model 节点只放在 `ouroboros-logicflow-data-model` 中。
- web、runtime facade、debug helper、Excel 和平台异常相关能力应放在本公开仓库外的 adapter 中。

## 贡献策略

外部贡献需要 DCO sign-off。

## Pull Request 检查清单

- [ ] `mvn test` 通过。
- [ ] 公开模块边界保持干净。
- [ ] 新节点 builder 已通过 SPI 注册。
- [ ] 面向用户的节点变化已更新 NodeDefine JSON。
- [ ] API 或 adapter 行为变化已更新文档。
EOF
}

write_adapter_guide() {
  mkdir -p "${OUTPUT_DIR_ABS}/docs"
  cat > "${OUTPUT_DIR_ABS}/docs/adapter-integration-guide.md" <<'EOF'
# Adapter Integration Guide

Ouroboros LogicFlow keeps core execution and authoring contracts independent
from platform runtime concerns. Applications integrate platform behavior through
small public extension points.

## Adapter Points

| Adapter point | Public contract | Required when | Integration approach |
| --- | --- | --- | --- |
| Node implementation | `NodeBuilder` | You add a new executable node type. | Implement `NodeBuilder`, return a node for the supported type, and register it in `META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder`. |
| Node authoring metadata | `LogicFlowNodeDefineProvider` and `.flow-node.json` resources | Editors or design-time tools need node palette/schema metadata. | Package `.flow-node.json` files under `META-INF/ouroboros/metadata` or provide a custom `LogicFlowNodeDefineProvider`. |
| Script execution | `LogicFlowScriptEngine` | `ScriptNode` must execute a concrete script language. | Implement the engine, declare supported languages with `getSupportedLanguages()`, and register the SPI descriptor. |
| Flow model lookup | `FlowModelProvider` / `FlowModelMetaProvider` | `CallFlowNode` loads child flows from an application catalog. | Implement providers backed by your storage/catalog and keep application lifecycle dependencies in the adapter. |
| Runtime diagnostics | `FlowRuntimeAnalyzer` | Applications need runtime analysis or debug reports. | Provide analyzers through SPI from a debug/runtime adapter, not from core or builtin nodes. |
| Data-model nodes | `ouroboros-logicflow-data-model` plus `ouroboros-data-core` contracts | Flows need CRUD/query/count/data-model operations. | Depend on the public data-model node bundle and provide runtime `DataModel` references through the LogicFlow reference catalog. |
| Web/runtime facade | Public `FlowModel` execution APIs | Applications expose LogicFlow over HTTP or transactions. | Build a web/runtime adapter outside the public core and call the public execution APIs from that adapter. |

## Node Bundle Pattern

Each node bundle should provide both execution and authoring artifacts:

- A `NodeBuilder` SPI descriptor for runtime discovery.
- One or more `.flow-node.json` files for design-time metadata.
- Tests proving the descriptor and metadata stay paired.

## Data-Model Integration

`ouroboros-logicflow-data-model` is public because `ouroboros-data-core` is part
of the data model public project. Keep data-model imports scoped to this module;
core, builtin nodes and authoring should remain independent from data contracts.
EOF
  cp "${OUTPUT_DIR_ABS}/docs/adapter-integration-guide.md" "${OUTPUT_DIR_ABS}/docs/adapter-integration-guide.en.md"
  cat > "${OUTPUT_DIR_ABS}/docs/adapter-integration-guide.md" <<'EOF'
# Adapter 接入指南

Ouroboros LogicFlow 将核心执行和 authoring contract 与平台 runtime 关注点分离。应用通过小型公开扩展点接入平台行为。

English documentation: [`adapter-integration-guide.en.md`](adapter-integration-guide.en.md)

## Adapter 点位

| Adapter 点位 | 公开 contract | 何时需要 | 接入方式 |
| --- | --- | --- | --- |
| 节点实现 | `NodeBuilder` | 需要增加新的可执行节点类型。 | 实现 `NodeBuilder`，为支持的类型返回节点，并在 `META-INF/services/com.ouroboros.logicflow.nodes.NodeBuilder` 注册。 |
| 节点 authoring metadata | `LogicFlowNodeDefineProvider` 和 `.flow-node.json` 资源 | 编辑器或设计工具需要节点面板/schema metadata。 | 将 `.flow-node.json` 打包到 `META-INF/ouroboros/metadata`，或提供自定义 `LogicFlowNodeDefineProvider`。 |
| 脚本执行 | `LogicFlowScriptEngine` | `ScriptNode` 需要执行具体脚本语言。 | 实现 engine，通过 `getSupportedLanguages()` 声明语言，并注册 SPI descriptor。 |
| Flow model 查找 | `FlowModelProvider` / `FlowModelMetaProvider` | `CallFlowNode` 需要从应用 catalog 加载子流程。 | 提供基于存储/catalog 的 provider，将应用生命周期依赖留在 adapter 中。 |
| Runtime 诊断 | `FlowRuntimeAnalyzer` | 应用需要 runtime analysis 或 debug report。 | 通过 debug/runtime adapter 提供 SPI analyzer，不放入 core 或 builtin nodes。 |
| Data-model 节点 | `ouroboros-logicflow-data-model` 与 `ouroboros-data-core` contract | Flow 需要 CRUD/query/count/data-model 操作。 | 依赖公开 data-model 节点包，并通过 LogicFlow reference catalog 在运行时提供 `DataModel` 引用。 |
| Web/runtime facade | 公开 `FlowModel` 执行 API | 应用需要通过 HTTP 或事务暴露 LogicFlow。 | 在公开 core 外构建 web/runtime adapter，由 adapter 调用公开执行 API。 |

## 节点包模式

每个节点包应同时提供执行与 authoring artifact：

- 用于 runtime discovery 的 `NodeBuilder` SPI descriptor。
- 一个或多个用于设计期 metadata 的 `.flow-node.json` 文件。
- 证明 descriptor 与 metadata 成对存在的测试。

## Data Model 集成

`ouroboros-logicflow-data-model` 之所以公开，是因为 `ouroboros-data-core` 属于数据模型公开项目。data-model import 应仅限于该模块；core、builtin nodes 和 authoring 应保持独立于 data contract。
EOF
}

write_data_model_guide() {
  mkdir -p "${OUTPUT_DIR_ABS}/docs"
  cat > "${OUTPUT_DIR_ABS}/docs/data-model-integration.md" <<'EOF'
# Data-Model Integration Guide

`ouroboros-logicflow-data-model` is the LogicFlow bridge to the public Ouroboros
Data Model contracts. It is the only LogicFlow module that may depend on
`ouroboros-data-core`.

## Integration Points

| Integration point | Public dependency | Required when | Integration approach |
| --- | --- | --- | --- |
| Data model execution nodes | `NodeBuilder` SPI plus `com.ouroboros.data.model.DataModel` | A flow needs CRUD/query/count/batch data operations. | Implement node builders in `ouroboros-logicflow-data-model` and use `NodeReferenceCatalog` to resolve `DataModel` instances at runtime. |
| Plugin descriptors | `com.ouroboros.data.model.PluginDescriptor` | Flow metadata carries data-model plugin configuration. | Convert plugin metadata to `PluginDescriptor` instances inside the data-model bundle and keep conversion logic scoped there. |
| Data-model authoring metadata | `.flow-node.json` resources | Editors or design tools need the data-model node palette. | Keep the JSON resources next to the data-model node bundle and expose them through the LogicFlow authoring provider. |
| Public data-model contracts | `ouroboros-data-core` | The data-model bundle must compile against public data-model APIs. | Depend on public `ouroboros-data-core` contracts only; keep platform runtime dependencies in integration adapters; shared public foundation helpers are allowed. |

## Guardrails

- Only this module may import `com.ouroboros.data.*`.
- Do not add `common-expression`, `common-metadata` or any
  platform adapter dependency here.
- Keep data-model node metadata and SPI descriptors paired.
- Treat data-model execution nodes as public integration, not internal glue.
EOF
  cp "${OUTPUT_DIR_ABS}/docs/data-model-integration.md" "${OUTPUT_DIR_ABS}/docs/data-model-integration.en.md"
  cat > "${OUTPUT_DIR_ABS}/docs/data-model-integration.md" <<'EOF'
# Data Model 集成指南

`ouroboros-logicflow-data-model` 是 LogicFlow 连接公开 Ouroboros Data Model contract 的桥接模块，也是唯一可以依赖 `ouroboros-data-core` 的 LogicFlow 模块。

English documentation: [`data-model-integration.en.md`](data-model-integration.en.md)

## 集成点

| 集成点 | 公开依赖 | 何时需要 | 接入方式 |
| --- | --- | --- | --- |
| 数据模型执行节点 | `NodeBuilder` SPI 与 `com.ouroboros.data.model.DataModel` | Flow 需要 CRUD/query/count/batch data 操作。 | 在 `ouroboros-logicflow-data-model` 中实现 node builder，并通过 `NodeReferenceCatalog` 解析运行时 `DataModel` 实例。 |
| 插件描述 | `com.ouroboros.data.model.PluginDescriptor` | Flow metadata 携带 data-model plugin 配置。 | 在 data-model 节点包内转换 plugin metadata 到 `PluginDescriptor`，并把转换逻辑限制在该模块。 |
| Data-model authoring metadata | `.flow-node.json` 资源 | 编辑器或设计工具需要 data-model 节点面板。 | 将 JSON 资源放在 data-model 节点包旁，并通过 LogicFlow authoring provider 暴露。 |
| 公开数据模型 contract | `ouroboros-data-core` | data-model 节点包需要基于公开 data-model API 编译。 | 只依赖公开 `ouroboros-data-core` contract，将平台 runtime 依赖保留在集成 adapter；允许复用公开基础工具库。 |

## Guardrails

- 只有该模块可以 import `com.ouroboros.data.*`。
- 不在该模块中添加 `common-expression`、`common-metadata` 或任何 platform adapter 依赖。
- 保持 data-model 节点 metadata 与 SPI descriptor 成对维护。
- 将 data-model 执行节点视为公开集成，而不是内部 glue。
EOF
}

write_license() {
  cat > "${OUTPUT_DIR_ABS}/LICENSE" <<'EOF'
                                 Apache License
                           Version 2.0, January 2004
                        https://www.apache.org/licenses/

   TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

   1. Definitions.

      "License" shall mean the terms and conditions for use, reproduction,
      and distribution as defined by Sections 1 through 9 of this document.

      "Licensor" shall mean the copyright owner or entity authorized by
      the copyright owner that is granting the License.

      "Legal Entity" shall mean the union of the acting entity and all
      other entities that control, are controlled by, or are under common
      control with that entity. For the purposes of this definition,
      "control" means (i) the power, direct or indirect, to cause the
      direction or management of such entity, whether by contract or
      otherwise, or (ii) ownership of fifty percent (50%) or more of the
      outstanding shares, or (iii) beneficial ownership of such entity.

      "You" (or "Your") shall mean an individual or Legal Entity
      exercising permissions granted by this License.

      "Source" form shall mean the preferred form for making modifications,
      including but not limited to software source code, documentation
      source, and configuration files.

      "Object" form shall mean any form resulting from mechanical
      transformation or translation of a Source form, including but
      not limited to compiled object code, generated documentation,
      and conversions to other media types.

      "Work" shall mean the work of authorship, whether in Source or
      Object form, made available under the License, as indicated by a
      copyright notice that is included in or attached to the work
      (an example is provided in the Appendix below).

      "Derivative Works" shall mean any work, whether in Source or Object
      form, that is based on (or derived from) the Work and for which the
      editorial revisions, annotations, elaborations, or other modifications
      represent, as a whole, an original work of authorship. For the purposes
      of this License, Derivative Works shall not include works that remain
      separable from, or merely link (or bind by name) to the interfaces of,
      the Work and Derivative Works thereof.

      "Contribution" shall mean any work of authorship, including
      the original version of the Work and any modifications or additions
      to that Work or Derivative Works thereof, that is intentionally
      submitted to Licensor for inclusion in the Work by the copyright owner
      or by an individual or Legal Entity authorized to submit on behalf of
      the copyright owner. For the purposes of this definition, "submitted"
      means any form of electronic, verbal, or written communication sent
      to the Licensor or its representatives, including but not limited to
      communication on electronic mailing lists, source code control systems,
      and issue tracking systems that are managed by, or on behalf of, the
      Licensor for the purpose of discussing and improving the Work, but
      excluding communication that is conspicuously marked or otherwise
      designated in writing by the copyright owner as "Not a Contribution."

      "Contributor" shall mean Licensor and any individual or Legal Entity
      on behalf of whom a Contribution has been received by Licensor and
      subsequently incorporated within the Work.

   2. Grant of Copyright License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      copyright license to reproduce, prepare Derivative Works of,
      publicly display, publicly perform, sublicense, and distribute the
      Work and such Derivative Works in Source or Object form.

   3. Grant of Patent License. Subject to the terms and conditions of
      this License, each Contributor hereby grants to You a perpetual,
      worldwide, non-exclusive, no-charge, royalty-free, irrevocable
      (except as stated in this section) patent license to make, have made,
      use, offer to sell, sell, import, and otherwise transfer the Work,
      where such license applies only to those patent claims licensable
      by such Contributor that are necessarily infringed by their
      Contribution(s) alone or by combination of their Contribution(s)
      with the Work to which such Contribution(s) was submitted. If You
      institute patent litigation against any entity (including a
      cross-claim or counterclaim in a lawsuit) alleging that the Work
      or a Contribution incorporated within the Work constitutes direct
      or contributory patent infringement, then any patent licenses
      granted to You under this License for that Work shall terminate
      as of the date such litigation is filed.

   4. Redistribution. You may reproduce and distribute copies of the
      Work or Derivative Works thereof in any medium, with or without
      modifications, and in Source or Object form, provided that You
      meet the following conditions:

      (a) You must give any other recipients of the Work or
          Derivative Works a copy of this License; and

      (b) You must cause any modified files to carry prominent notices
          stating that You changed the files; and

      (c) You must retain, in the Source form of any Derivative Works
          that You distribute, all copyright, patent, trademark, and
          attribution notices from the Source form of the Work,
          excluding those notices that do not pertain to any part of
          the Derivative Works; and

      (d) If the Work includes a "NOTICE" text file as part of its
          distribution, then any Derivative Works that You distribute must
          include a readable copy of the attribution notices contained
          within such NOTICE file, excluding those notices that do not
          pertain to any part of the Derivative Works, in at least one
          of the following places: within a NOTICE text file distributed
          as part of the Derivative Works; within the Source form or
          documentation, if provided along with the Derivative Works; or,
          within a display generated by the Derivative Works, if and
          wherever such third-party notices normally appear. The contents
          of the NOTICE file are for informational purposes only and
          do not modify the License. You may add Your own attribution
          notices within Derivative Works that You distribute, alongside
          or as an addendum to the NOTICE text from the Work, provided
          that such additional attribution notices cannot be construed
          as modifying the License.

      You may add Your own copyright statement to Your modifications and
      may provide additional or different license terms and conditions
      for use, reproduction, or distribution of Your modifications, or
      for any such Derivative Works as a whole, provided Your use,
      reproduction, and distribution of the Work otherwise complies with
      the conditions stated in this License.

   5. Submission of Contributions. Unless You explicitly state otherwise,
      any Contribution intentionally submitted for inclusion in the Work
      by You to the Licensor shall be under the terms and conditions of
      this License, without any additional terms or conditions.
      Notwithstanding the above, nothing herein shall supersede or modify
      the terms of any separate license agreement you may have executed
      with Licensor regarding such Contributions.

   6. Trademarks. This License does not grant permission to use the trade
      names, trademarks, service marks, or product names of the Licensor,
      except as required for reasonable and customary use in describing the
      origin of the Work and reproducing the content of the NOTICE file.

   7. Disclaimer of Warranty. Unless required by applicable law or
      agreed to in writing, Licensor provides the Work (and each
      Contributor provides its Contributions) on an "AS IS" BASIS,
      WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
      implied, including, without limitation, any warranties or conditions
      of TITLE, NON-INFRINGEMENT, MERCHANTABILITY, or FITNESS FOR A
      PARTICULAR PURPOSE. You are solely responsible for determining the
      appropriateness of using or redistributing the Work and assume any
      risks associated with Your exercise of permissions under this License.

   8. Limitation of Liability. In no event and under no legal theory,
      whether in tort (including negligence), contract, or otherwise,
      unless required by applicable law (such as deliberate and grossly
      negligent acts) or agreed to in writing, shall any Contributor be
      liable to You for damages, including any direct, indirect, special,
      incidental, or consequential damages of any character arising as a
      result of this License or out of the use or inability to use the
      Work (including but not limited to damages for loss of goodwill,
      work stoppage, computer failure or malfunction, or any and all
      other commercial damages or losses), even if such Contributor
      has been advised of the possibility of such damages.

   9. Accepting Warranty or Additional Liability. While redistributing
      the Work or Derivative Works thereof, You may choose to offer,
      and charge a fee for, acceptance of support, warranty, indemnity,
      or other liability obligations and/or rights consistent with this
      License. However, in accepting such obligations, You may act only
      on Your own behalf and on Your sole responsibility, not on behalf
      of any other Contributor, and only if You agree to indemnify,
      defend, and hold each Contributor harmless for any liability
      incurred by, or claims asserted against, such Contributor by reason
      of your accepting any such warranty or additional liability.

   END OF TERMS AND CONDITIONS

   APPENDIX: How to apply the Apache License to your work.

      To apply the Apache License to your work, attach the following
      boilerplate notice, with the fields enclosed by brackets "[]"
      replaced with your own identifying information. Do not include
      the brackets! The text should be enclosed in the appropriate
      comment syntax for the file format. We also recommend that a
      file or class name and description of purpose be included on the
      same "printed page" as the copyright notice for easier
      identification within third-party archives.

   Copyright [yyyy] TideStack

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       https://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
EOF
}

write_notice() {
  cat > "${OUTPUT_DIR_ABS}/NOTICE" <<'EOF'
Ouroboros LogicFlow
Copyright (c) TideStack.

This product includes software developed for the Ouroboros LogicFlow project.
EOF
}

write_ci_stub() {
  mkdir -p "${OUTPUT_DIR_ABS}/.github/workflows"
  cat > "${OUTPUT_DIR_ABS}/.github/workflows/build.yml" <<'EOF'
name: build

on:
  push:
  pull_request:

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '21'
          cache: maven
      - run: mvn test
EOF
}

write_snapshot_note() {
  cat > "${OUTPUT_DIR_ABS}/OPEN_SOURCE_SNAPSHOT.md" <<'EOF'
# Open Source Snapshot

This dry-run is a clean snapshot candidate for the first public LogicFlow
release. It is intentionally generated without internal Git ancestry.

Suggested public initial commit message:

```text
Initial open source release
```

Internal references, internal commit identifiers, and private synchronization
ledger entries must stay outside this public snapshot.
EOF
  cp "${OUTPUT_DIR_ABS}/OPEN_SOURCE_SNAPSHOT.md" "${OUTPUT_DIR_ABS}/OPEN_SOURCE_SNAPSHOT.en.md"
  cat > "${OUTPUT_DIR_ABS}/OPEN_SOURCE_SNAPSHOT.md" <<'EOF'
# 开源快照说明

本快照是 LogicFlow 首次公开发布的 clean snapshot 候选。它刻意不包含内部 Git ancestry。

English documentation: [`OPEN_SOURCE_SNAPSHOT.en.md`](OPEN_SOURCE_SNAPSHOT.en.md)

建议公开初始提交信息：

```text
Initial open source release
```

内部引用、内部 commit 标识和私有同步 ledger 条目必须留在公开快照之外。
EOF
}

write_sample() {
  local sample_dir="${OUTPUT_DIR_ABS}/samples/script-node-standalone"
  mkdir -p "${sample_dir}/src/test/java/com/ouroboros/logicflow/samples"
  mkdir -p "${sample_dir}/src/test/resources/META-INF/services"

  cat > "${sample_dir}/pom.xml" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.ouroboros.samples</groupId>
    <artifactId>logicflow-script-node-standalone-sample</artifactId>
    <version>${VERSION}</version>
    <packaging>jar</packaging>

    <name>LogicFlow ScriptNode Standalone Sample</name>

    <properties>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
        <junit.version>5.12.2</junit.version>
    </properties>

    <dependencyManagement>
        <dependencies>
            <dependency>
                <groupId>com.ouroboros</groupId>
                <artifactId>ouroboros-logicflow-bom</artifactId>
                <version>${VERSION}</version>
                <type>pom</type>
                <scope>import</scope>
            </dependency>
        </dependencies>
    </dependencyManagement>

    <dependencies>
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>ouroboros-logicflow-core</artifactId>
        </dependency>
        <dependency>
            <groupId>com.ouroboros</groupId>
            <artifactId>ouroboros-logicflow-builtin-nodes</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter</artifactId>
            <version>\${junit.version}</version>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.15.0</version>
                <configuration>
                    <release>21</release>
                </configuration>
            </plugin>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-surefire-plugin</artifactId>
                <version>3.5.6</version>
            </plugin>
        </plugins>
    </build>
</project>
EOF

  cat > "${sample_dir}/src/test/java/com/ouroboros/logicflow/samples/ScriptNodeStandaloneSampleTest.java" <<'EOF'
package com.ouroboros.logicflow.samples;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.logicflow.model.FlowModelFactory;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;

class ScriptNodeStandaloneSampleTest {

  @Test
  void scriptNodeRunsFromPublicCoreAndBuiltinNodes() {
    var flowModel = FlowModelFactory.build(FlowModelMeta.builder()
        .name("script-node-standalone-sample")
        .inputDataType("string")
        .outputDataType("string")
        .node(FlowNodeMeta.builder()
            .name("script")
            .type("script")
            .inputPort("input", "string")
            .extraProp("language", "sample")
            .extraProp("script", "return $0 + '-public'")
            .build())
        .entryNode("script")
        .exitNode("script")
        .build(), NodeReferenceCatalog.empty());

    var result = flowModel.execute("logicflow");

    assertTrue(result.isSuccess());
    assertEquals("logicflow-public", result.get());
  }
}
EOF

  cat > "${sample_dir}/src/test/java/com/ouroboros/logicflow/samples/SampleScriptEngine.java" <<'EOF'
package com.ouroboros.logicflow.samples;

import java.util.Map;
import java.util.List;
import java.util.function.Function;

import io.vavr.control.Try;

import com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine;

public final class SampleScriptEngine implements LogicFlowScriptEngine {
  @Override
  public List<String> getSupportedLanguages() {
    return List.of("sample");
  }

  @Override
  public Function<Map<String, Object>, Try<Object>> compile(String language, String script) {
    return bindings -> Try.of(() -> bindings.get("$0") + "-public");
  }
}
EOF

  cat > "${sample_dir}/src/test/resources/META-INF/services/com.ouroboros.logicflow.nodes.script.LogicFlowScriptEngine" <<'EOF'
com.ouroboros.logicflow.samples.SampleScriptEngine
EOF
}

write_gitignore() {
  cat > "${OUTPUT_DIR_ABS}/.gitignore" <<'EOF'
target/
.DS_Store
*.iml
EOF
}

write_report() {
  local report_dir="${REPO_ROOT}/tmp/testing-evidence/open-source/logicflow-public-repo-bootstrap"
  local report_file="${report_dir}/bootstrap-report.md"
  mkdir -p "${report_dir}"

  local content_hash
  content_hash="$(
    cd "${OUTPUT_DIR_ABS}"
    find . -type f -print | LC_ALL=C sort | while read -r file_path; do
      shasum -a 256 "${file_path}"
    done | shasum -a 256 | awk '{print $1}'
  )"

  cat > "${report_file}" <<EOF
---
doc_type: logicflow-public-repo-bootstrap-report
roadmap: logicflow-open-source-extraction
feature: 2026-07-25-logicflow-public-repo-bootstrap
status: generated
updated: 2026-07-25
---

# LogicFlow Public Repo Bootstrap Report

## Dry-Run Snapshot

- Output directory: \`${OUTPUT_DIR_ABS}\`
- Export mode: clean snapshot copy
- Content hash: \`${content_hash}\`
- Internal git ancestry copied: no
- Public remote created: no
- Public push/release performed: no

## Included Surface

- Root public parent POM: \`pom.xml\`
- Public BOM: \`ouroboros-logicflow-bom\`
- Public modules: \`ouroboros-logicflow-core\`, \`ouroboros-logicflow-builtin-nodes\`, \`ouroboros-logicflow-authoring\`, \`ouroboros-logicflow-data-model\`
- Public docs/legal draft: \`README.md\`, \`CONTRIBUTING.md\`, \`LICENSE\`, \`NOTICE\`, \`docs/adapter-integration-guide.md\`
- CI stub: \`.github/workflows/build.yml\`
- Sample: \`samples/script-node-standalone\`

## Excluded Surface

Internal adapter and platform modules are excluded by default: extra-nodes,
runtime, dev, web-adapter, all, frontend, platform, security, and CodeStable
roadmap/goal material.

## Dependency Management

- Repository build entry: public root parent.
- Consumer alignment entry: \`ouroboros-logicflow-bom\`.
- Consumer root-parent inheritance: not required.
- Public parent pins the LogicFlow standalone test/runtime baseline: JUnit 4 +
  JUnit Vintage for existing tests, plus Jackson 3.1.4 / annotations 2.21 for
  \`json-schema-validator\`.
- Consumer sample imports \`ouroboros-logicflow-bom\` and declares LogicFlow module dependencies without versions.

## Pending Human Review

- This report is internal evidence only and is not copied into the public dry-run snapshot.
EOF
}

rm -rf "${OUTPUT_DIR_ABS}"
mkdir -p "${OUTPUT_DIR_ABS}"

copy_public_modules
python3 - "${OUTPUT_DIR_ABS}" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

namespace = {"p": "http://maven.apache.org/POM/4.0.0"}
ET.register_namespace("", namespace["p"])
for module in ("ouroboros-util", "common-code-intelligence", "common-expression"):
    path = Path(sys.argv[1]) / module / "pom.xml"
    root = ET.parse(path).getroot()
    parent = root.find("p:parent", namespace)
    parent.find("p:artifactId", namespace).text = "ouroboros-logicflow"
    relative_path = parent.find("p:relativePath", namespace)
    if relative_path is None:
        relative_path = ET.SubElement(parent, "{" + namespace["p"] + "}relativePath")
    relative_path.text = "../pom.xml"
    repositories = root.find("p:repositories", namespace)
    if repositories is not None:
        root.remove(repositories)
    if module == "common-code-intelligence":
        dependencies = root.find("p:dependencies", namespace)
        dependency = ET.SubElement(dependencies, "{" + namespace["p"] + "}dependency")
        for tag, value in (("groupId", "com.ouroboros"), ("artifactId", "ouroboros-util")):
            ET.SubElement(dependency, "{" + namespace["p"] + "}" + tag).text = value
    ET.indent(root, space="    ")
    ET.ElementTree(root).write(path, encoding="UTF-8", xml_declaration=True)
PY
write_root_pom
write_bom_pom
write_readme
write_contributing
write_adapter_guide
write_data_model_guide
write_license
write_notice
write_ci_stub
write_snapshot_note
write_sample
write_gitignore
write_report

if [[ "${CHECK}" == true ]]; then
  bash "${CHECK_SCRIPT}" --dry-run-dir "${OUTPUT_DIR_ABS}" --strict-clean
fi

echo "Generated LogicFlow public repo dry-run: ${OUTPUT_DIR_ABS}"
