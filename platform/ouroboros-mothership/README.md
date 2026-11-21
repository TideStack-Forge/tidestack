# Ouroboros Mothership

Ouroboros 低代码平台的应用运行基座。这是一个 Spring Boot 应用，打包为 Docker 镜像，负责加载和运行用户应用。

## 与 `ouroboros-web` 的关系

当前仓库里有两种并行存在的运行形态：

| 镜像 | 角色 | 适合场景 |
|---|---|---|
| `ouroboros-mothership` | all-in-one 运行基座 | 前端静态资源、Nginx、Spring Boot 后端一起打包和运行 |
| `ouroboros-web` | 前端基座镜像 | 前后端分离部署、联合调试、split 拓扑 |

`mothership` 没有被 `ouroboros-web` 替代。两者是并行提供不同灵活度的部署选择：

- 如果你希望继续用一个镜像承载完整应用，使用 `ouroboros-mothership`
- 如果你希望前端入口独立运行，并把同源 API 代理到外部后端，使用 `ouroboros-web`

`ouroboros-web` 的详细用法、环境变量、`MICRO_PROXY_MAP` 和 split 拓扑示例见 [frontends/web/README.md](../../frontends/web/README.md)。

## 核心特性

- **分层扩展机制**：支持基座级、应用级、部署级三层扩展包管理
- **灵活配置**：多层配置文件覆盖，支持实例级定制
- **安全隔离**：内建元数据时默认禁用部署级扩展，减少运行时注入风险
- **开发友好**：支持远程调试、自定义类路径、启动命令打印

## 平台缓存管理

普通应用运行基座通过 `ouroboros-ability-all-runtime` 装载公共缓存管理能力。部署前设置 `OUROBOROS_PLATFORM_CACHE_TOKEN_SECRET`（或配置 `ouroboros.platform.cache.token.secret`）；缺失时启动失败。升级已有环境时也需要补充此配置。

向管理员角色授予 `systemSetting.platformCache` 权限后，可访问“平台缓存管理”菜单和 `/system-setting/platform-cache` 页面。操作对象是当前服务实例的缓存管理器。

## 容器内目录结构

```
/app
├── base/                           # 基座级（镜像内置，不可变）
│   ├── service/
│   │   ├── ouroboros-mothership.jar
│   │   ├── ouroboros-lib/          # 平台核心库
│   │   ├── third-party-lib/        # 第三方依赖
│   │   ├── application-base.yml    # 基座默认配置
│   │   ├── logback.xml
│   │   └── start.sh
│   └── web/                        # 前端静态资源
├── metadata/                       # 应用级元数据
│   ├── extend/                     # 应用扩展包（通常来自镜像或软链接）
│   └── application.yml             # 应用配置
├── deploy/                         # 部署级（宿主机挂载，运行时可变）
│   ├── extend/                     # 部署扩展包（默认禁用）
│   ├── metadata/                   # 部署元数据（可选）
│   ├── logs/                       # 日志输出
│   ├── workspace/                  # 工作空间
│   ├── application.yml             # 部署配置
│   └── instance.yml                # 实例配置
└── init.sh                         # 容器初始化脚本
```

## 启动流程

### 1. 初始化阶段（init.sh）

容器启动时检查元数据来源，完整判断见 [init.sh](docker/root/app/init.sh)：

| 元数据来源 | 初始化行为 | `ENABLE_DEPLOY_EXTEND` 默认值 |
| --- | --- | --- |
| 镜像内建 `/app/metadata` | 使用内建元数据 | `false` |
| 外部挂载 `/app/deploy/metadata` | 首次启动建立 `/app/metadata` 软链接；重启识别并复用该链接 | `true` |

显式传入的 `ENABLE_DEPLOY_EXTEND` 优先于默认值。重启时不能仅凭 `/app/metadata` 目录存在就判为内建，否则部署扩展中的助手 JAR 会停止加载。

**设计意图**：
- 内建元数据（镜像打包）：默认禁用 `/app/deploy/extend`，防止运行时注入未知代码
- 部署元数据（外部挂载）：默认启用 `/app/deploy/extend`，允许灵活扩展

### 2. 类路径加载（start.sh）

加载顺序（优先级从高到低）：

1. **自定义类路径**：`CUSTOM_CLASS_PATHS`（环境变量 `CUSTOM_CLASS_PATH`，分号分隔）
2. **部署级扩展**：`/app/deploy/extend`（需 `ENABLE_DEPLOY_EXTEND=true`）
3. **应用级扩展**：`/app/metadata/extend` + `/app/extend`
4. **基座级库**：`/app/base/service/ouroboros-lib` + `third-party-lib`
5. **自定义托底**：`CUSTOM_BASE_CLASS_PATHS`（环境变量 `CUSTOM_BASE_CLASS_PATH`）

最终传递给 Spring Boot 的 `loader.path` 参数。

### 3. 配置文件加载

Spring Boot 配置加载顺序（优先级从低到高）：

1. `classpath:application.yml` - Jar 内部默认配置
2. `file:/app/base/service/application-base.yml` - 基座级配置
3. `file:/app/metadata/application.yml` - 应用级配置
4. `file:/app/application.yml` - 应用级自定义配置
5. `file:/app/config/*.yml` - 应用级配置目录
6. `file:/app/deploy/application.yml` - 部署级配置
7. `file:/app/deploy/config/*.yml` - 部署级配置目录
8. `file:/app/deploy/instance.yml` - 实例级配置（最高优先级）

这里只列出存在的文件；`start.sh` 将它们拼入 `--spring.config.location`，目录中的 `.yml` 文件按 shell glob 展开。额外命令行参数等 Spring 配置源仍可能覆盖文件值。all-in-one 前端由 Nginx 监听 80；下面只映射 `8080:8080` 的示例用于直接访问后端，需要前端时另映射宿主端口到容器 80。

## 环境变量

| 变量名 | 默认值 | 说明 |
|--------|--------|------|
| `ENABLE_DEPLOY_EXTEND` | 自动检测 | 是否启用 `/app/deploy/extend`。内建 metadata 时默认 `false`，部署 metadata 时默认 `true` |
| `ENABLE_NGINX` | `true` | 是否启动 nginx 提供前端服务 |
| `ENABLE_DEBUG` / `DEBUG` | `false` | 开启 JDWP 远程调试（监听 5005 端口） |
| `XMS` | 无 | JVM 最小堆内存（如 `512m`） |
| `XMX` | 无 | JVM 最大堆内存（如 `2g`） |
| `JAVA_OPTS` | 无 | 其他 JVM 参数（如 `-XX:+UseG1GC`） |
| `SHOW_CMD` | `false` | 打印完整启动命令（用于调试） |
| `CUSTOM_CLASS_PATH` | 无 | 自定义类路径，分号分隔（如 `/opt/lib1;/opt/lib2`） |
| `CUSTOM_BASE_CLASS_PATH` | 无 | 自定义托底类路径，分号分隔 |

## 使用指南

### 基本运行

```bash
docker run -d \
  --name ouroboros-app \
  -p 8080:8080 \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest
```

### 开启远程调试

```bash
docker run -d \
  --name ouroboros-app \
  -p 8080:8080 \
  -p 5005:5005 \
  -e ENABLE_DEBUG=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest
```

### 调整 JVM 内存

```bash
docker run -d \
  --name ouroboros-app \
  -p 8080:8080 \
  -e XMS=1g \
  -e XMX=4g \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest
```

### 启用部署级扩展（内建元数据场景）

```bash
docker run -d \
  --name ouroboros-app \
  -p 8080:8080 \
  -e ENABLE_DEPLOY_EXTEND=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest
```

**警告**：启用部署级扩展意味着容器会加载宿主机挂载的 `/app/deploy/extend` 目录中的 Jar 包。确保该目录内容可信。

### 查看启动命令

```bash
docker run -d \
  --name ouroboros-app \
  -p 8080:8080 \
  -e SHOW_CMD=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest

# 查看日志中的启动命令
docker logs ouroboros-app | grep "java -jar"
```

## 扩展机制

### 添加应用级扩展

**方式 1：打包到镜像**

```dockerfile
# Dockerfile
FROM ouroboros-mothership-base:latest
COPY my-extension.jar /app/metadata/extend/
```

**方式 2：挂载到容器**

```bash
docker run -d \
  -v /path/to/my-extension.jar:/app/extend/my-extension.jar \
  ouroboros-mothership:latest
```

### 添加部署级扩展

```bash
# 宿主机操作
mkdir -p /data/ouroboros/deploy/extend
cp my-extension.jar /data/ouroboros/deploy/extend/

# 启动容器时启用部署扩展
docker run -d \
  -e ENABLE_DEPLOY_EXTEND=true \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest
```

### 扩展包冲突处理

如果同一个扩展包的不同版本出现在多个层级，类路径加载顺序决定最终生效版本：

```
自定义类路径 > 部署级 > 应用级 > 基座级
```

**示例**：
- `/app/deploy/extend/my-ext-1.0.jar`（部署级）
- `/app/metadata/extend/my-ext-2.0.jar`（应用级）

如果 `ENABLE_DEPLOY_EXTEND=true`，最终加载 `1.0` 版本。

## 故障排查

### 问题：设计时菜单或功能出现重复

**症状**：
- 设计时界面出现重复的菜单项
- 同一个功能被注册多次
- 日志中出现重复的 Bean 定义警告

**根因**：
多个版本的扩展包同时被加载到类路径。常见场景：
1. `/app/deploy/extend` 残留旧版本 Jar（如 `my-ext-1.0.0.jar`）
2. `/app/metadata/extend` 有新版本 Jar（如 `my-ext-2.0.0.jar`）
3. 两份 Jar 都被加载，导致资源重复扫描

**解决方案**：

```bash
# 1. 检查部署级扩展目录
docker exec <container> ls -la /app/deploy/extend

# 2. 清理历史版本
docker exec <container> rm /app/deploy/extend/my-ext-1.0.0.jar

# 3. 如果使用内建元数据，确保禁用部署扩展
docker stop <container>
docker run -d \
  -e ENABLE_DEPLOY_EXTEND=false \
  -v /data/ouroboros/deploy:/app/deploy \
  ouroboros-mothership:latest

# 4. 验证类路径
docker exec <container> sh -c 'for p in /proc/[0-9]*/cmdline; do tr "\000" " " < "$p" 2>/dev/null | grep -q "java" && tr "\000" " " < "$p"; done' | grep loader.path
```

### 问题：配置不生效

**检查配置加载顺序**：

```bash
# 查看 Spring Boot 启动日志
docker logs <container> | grep "spring.config.location"

# 检查配置文件是否存在
docker exec <container> ls -la /app/deploy/application.yml
docker exec <container> cat /app/deploy/application.yml
```

**常见原因**：
- 配置文件路径错误（如放在 `/app/deploy/conf` 而非 `/app/deploy/config`）
- YAML 格式错误（缩进、冒号后缺少空格）
- 配置被更高优先级的文件覆盖（检查 `instance.yml`）

### 问题：扩展包未加载

**验证类路径**：

```bash
# 查看完整启动命令
docker exec <container> sh -c 'for p in /proc/[0-9]*/cmdline; do tr "\000" " " < "$p" 2>/dev/null | grep -q "java" && tr "\000" " " < "$p"; done'

# 检查 loader.path 是否包含扩展目录
docker logs <container> | grep loader.path
```

**常见原因**：
- `ENABLE_DEPLOY_EXTEND=false` 导致 `/app/deploy/extend` 未加载
- 扩展包文件名不符合规范（必须是 `.jar` 后缀）
- 扩展包损坏或不兼容

### 问题：容器启动失败

**查看启动日志**：

```bash
docker logs <container>
```

**常见原因**：
- 端口冲突（8080 已被占用）
- 内存不足（调整 `XMS`/`XMX` 或 Docker 内存限制）
- 数据库连接失败（检查 `application.yml` 中的数据源配置）
- 扩展包依赖冲突（检查类路径中的 Jar 版本）

## 开发指南

### 构建镜像

```bash
# 在目标 worktree 根目录执行
bash build.sh --help
# 本地 amd64 镜像探针，准备 Maven 产物和依赖层后构建镜像
bash build.sh probe-mothership -t mothership-probe
```

镜像上下文是 `platform/ouroboros-mothership/docker/`，构建文件是 `dockerfile`。单独 Maven package 不会准备 `common-layer`、`build-layer` 与前端镜像输入，完整流程以根 `build.sh` 为准。

### 本地调试

```bash
# 启动容器并开启调试
docker run -d \
  --name ouroboros-dev \
  -p 8080:8080 \
  -p 5005:5005 \
  -e ENABLE_DEBUG=true \
  -e SHOW_CMD=true \
  -v $(pwd)/deploy:/app/deploy \
  ouroboros-mothership:latest

# IDEA 配置 Remote JVM Debug
# Host: localhost
# Port: 5005
```

### 打包自定义应用

```dockerfile
FROM ouroboros-mothership-base:latest

# 复制应用元数据
COPY metadata/ /app/metadata/

# 复制应用扩展包
COPY extend/*.jar /app/metadata/extend/

# 复制应用配置
COPY application.yml /app/metadata/
```

## 相关文档

- [Ouroboros 架构文档](../../ARCHITECTURE.md)
- [应用开发指南](../../docs/guides/app-development-guide.md)
- [部署指南](../../docs/deployment-guide.md)
