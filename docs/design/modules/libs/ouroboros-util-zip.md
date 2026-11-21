# ouroboros-util-zip 模块设计摘要

## 模块定位

`ouroboros-util-zip` 是 `libs` 支撑层中的归档文件工具模块，负责为平台提供 ZIP 包读取、检查、解压与写出能力。它是很多“包文件进入平台后需要先做安全处理和结构读取”的场景底座，例如开发平台里的组件包、模板包或其他归档资源处理。

## 责任边界

该模块负责 ZIP 文件列表读取、条目信息检查、内容窥探、解压、写包和安全限制控制；它不负责组件包、模板包或部署包的业务语义判断，也不负责文件仓库、版本治理或微前端部署。ZIP 只是它处理的载体，不是产品对象本身。

## 依赖关系

模块直接依赖 JDK ZIP 能力、`commons-io`、`slf4j` 和 `vavr`，保持在工具层即可复用的轻量边界。向上它会被开发平台模块直接消费，例如 `ouroboros-dev-platform-component-lib` 会用它读取并安装组件库 ZIP 包。

## 关键抽象

- `ZipReader`：读取 ZIP 文件内容的主入口，负责文件列表、条目信息、内容查看和解压。
- `ZipReader.ZipEntryWrapper`：解压时对单个条目的受控包装，便于读取字符串、字节和复制到输出流。
- `ZipReader.ZipEntryInfo`、`ZipStatistics`：条目级和归档级统计视图。
- `ZipWriter`：构建 ZIP 文件的主入口，支持添加文件、目录、内存条目和批量写入。
- `ZipWriter.ZipWriterConfig`、`ZipWriterBuilder`：写入时的压缩级别、注释、重复条目与进度回调配置。
- `ZipLimits`：ZIP 操作安全限制模型，表达单文件、总大小、数量、内存和超时的限制策略。

## 关键子模块

- `com.ouroboros.util.ZipReader`：读取、内容访问和受控解压路径。
- `com.ouroboros.util.ZipWriter`：创建 ZIP、批量写入和写出配置路径。
- `com.ouroboros.util.ZipLimits`：独立的安全限制策略对象，承接不同场景的限制模板。

## 扩展机制

该模块不依赖平台 SPI，主要通过 `Predicate<File>`、`BiFunction<ZipEntryWrapper, OutputStream, Boolean>` 和写入构建器回调承接定制。需要共享扩展治理时，应在上层模块定义包语义，不要把业务规则直接塞进 ZIP 工具本身。

## 深入文档索引

- `推荐阅读`：[libs/util/ouroboros-util-zip/GUIDE.md](../../../../libs/util/ouroboros-util-zip/GUIDE.md)
- `暂无独立深度设计文档`：当前仓库中尚无比 GUIDE 更系统的 `ouroboros-util-zip` 设计专题。

## 演进状态

`ouroboros-util-zip` 已经具备较完整的读写能力，但它同时也是一个安全敏感模块。当前安全限制一部分体现在 `ZipReader` / `ZipWriter` 的内建阈值中，另一部分抽象在 `ZipLimits` 里；后续若继续增强，应优先收敛安全策略入口，避免限制规则分散后难以维护。
