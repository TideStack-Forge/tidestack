# ouroboros-util-excel 模块设计摘要

## 模块定位

`ouroboros-util-excel` 是 `libs` 支撑层中的专门化文件工具模块，负责把平台常见的 Excel 导入、导出与表格转换能力沉淀为独立工具库。它的价值在于为上层模块提供“表格结构处理”这一通用能力，而不是把业务导入规则散落到各个模块里重复实现。

## 责任边界

该模块负责 `.xls` / `.xlsx` 的读取、写入、基本文件校验、表头到数据行的结构转换，以及写出时的样式定制与冻结窗格支持；它不负责业务字段映射规则、导入模板治理、文件存储传输，也不负责平台级报表产品逻辑。凡是已经依赖具体业务模型才能成立的 Excel 语义，都应留在上层模块。

## 依赖关系

从依赖上看，`ouroboros-util-excel` 直接基于 Apache POI 提供 Office 文件处理能力，并用 `vavr` 等基础依赖补充返回值和工具风格。它通常被 `common-*`、能力层和应用交付层在导入导出场景复用，但本身不依赖 Spring、不参与平台运行时自动装配。

## 关键抽象

- `ExcelReader`：统一的读取入口，覆盖文件、流、字节数组和按 Sheet 选择等常见入口。
- `ExcelReader.SheetData`：读取侧的表格结果对象，承载原始二维数据、读取状态与“转成表头映射行”的辅助能力。
- `ExcelReader.ValidationResult`：对文件格式、存在性和可读性的前置校验结果。
- `ExcelWriter`：统一的写入入口，支持文件、流、字节数组输出。
- `ExcelWriter.SheetData`：写入侧的数据模型，承载 sheet 名称、表头、数据行和冻结配置。
- `ExcelWriter.CellStyler`、`CellContext`：写出样式扩展面，用于控制表头样式、数据行样式和缓存复用。

## 关键子模块

- `com.ouroboros.util.excel.ExcelReader`：读取、校验和读后结构转换。
- `com.ouroboros.util.excel.ExcelWriter`：写入、样式控制和输出序列化。
- 当前模块没有再拆子 artifact，维护时主要按“读路径”和“写路径”理解源码即可。

## 扩展机制

该模块不承载平台级 SPI 或自动配置扩展，主要扩展面是 `CellStyler` 这类函数式回调。维护时应优先把变化限制在样式和数据投影层，而不是把业务导入规则继续下沉到工具库中。

## 深入文档索引

- `推荐阅读`：[libs/util/ouroboros-util-excel/GUIDE.md](../../../../libs/util/ouroboros-util-excel/GUIDE.md)
- `暂无独立深度设计文档`：当前仓库中尚无比 GUIDE 更系统的 `ouroboros-util-excel` 设计专题。

## 演进状态

`ouroboros-util-excel` 当前边界比较稳定，适合作为共享导入导出工具继续复用。后续维护应重点防止两类漂移：一类是把业务字段规则塞进工具层，另一类是因为单次需求继续扩张读写模型，导致 `SheetData` 失去“通用表格中间表示”的定位。
