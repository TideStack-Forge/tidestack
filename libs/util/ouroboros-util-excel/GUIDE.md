# Ouroboros Util Excel - Excel 工具类指南

本文档描述了 `ouroboros-util-excel` 模块中的 Excel 读写工具类。

## Excel 读取 - ExcelReader

路径: `com.ouroboros.util.excel.ExcelReader`

### 基本读取

#### 读取整个 Excel 文件

```java
// 读取所有 Sheet
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel("file.xlsx");

// 读取指定 Sheet (按索引)
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel("file.xlsx", 0);

// 读取指定 Sheet (按名称)
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel("file.xlsx", "Sheet1");

// 读取多个 Sheet (按索引列表)
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel("file.xlsx", Arrays.asList(0, 1, 2));

// 读取多个 Sheet (按名称列表)
List<ExcelReader.SheetData> sheets = ExcelReader.readExcelByNames("file.xlsx", Arrays.asList("Sheet1", "Sheet2"));
```

#### 从流读取

```java
// 从 InputStream 读取
String filename = "file.xlsx";
try (FileInputStream fis = new FileInputStream(filename)) {
    boolean isXls = filename.endsWith(".xls");
    List<ExcelReader.SheetData> sheets = ExcelReader.readExcel(fis, isXls);
}

// 从字节数组读取
byte[] excelBytes = ...;
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel(excelBytes, false); // false = xlsx
```

### ExcelReader.SheetData 使用

#### 基本信息

```java
ExcelReader.SheetData sheet = ExcelReader.readSheet("file.xlsx", 0);

// 获取基本信息
String name = sheet.getSheetName();
int rowCount = sheet.getRowCount();
int columnCount = sheet.getColumnCount();
boolean isEmpty = sheet.isEmpty();
boolean success = sheet.isSuccess();

// 获取原始数据
List<List<Object>> rows = sheet.getRows();
```

#### 转换为 Map 列表

```java
// 使用第一行作为表头,从第二行开始作为数据
List<Map<String, Object>> data = sheet.toMappedRows();

// 使用自定义表头，从第1行开始作为数据（不会自动跳过原表头）
List<String> headers = Arrays.asList("姓名", "年龄", "邮箱");
List<Map<String, Object>> data = sheet.toMappedRows(headers);

// 指定表头行与起始列（索引从0开始）
List<Map<String, Object>> data = sheet.toMappedRows(2, 0); // 第3行为表头，第4行起为数据

// 指定起始位置和自定义表头
List<Map<String, Object>> data = sheet.toMappedRows(1, 1, headers); // 从第2行、第2列开始
```

### 读取单行数据

```java
// 读取指定行
List<Object> row = ExcelReader.readRow("file.xlsx", sheetIndex, rowIndex);

// 从流读取指定行
List<Object> row = ExcelReader.readRow(inputStream, isXls, sheetIndex, rowIndex);
```

### 文件验证

```java
// 验证文件有效性
ExcelReader.ValidationResult result = ExcelReader.validateExcelFile("file.xlsx");
if (result.isValid()) {
    // 文件有效
} else {
    List<String> errors = result.getErrors();
}
```

### 数据类型说明

ExcelReader 读取的单元格数据类型:
- **字符串**: `String`
- **数字**: `Double`
- **布尔值**: `Boolean`
- **日期**: `java.util.Date`
- **空单元格、空字符串、错误单元格或转换失败**: `null`；不能仅据 null 判断原单元格为空
- **公式**: 先求值，再按结果类型转换

## Excel 写入 - ExcelWriter

路径: `com.ouroboros.util.excel.ExcelWriter`

### 基本写入

#### 快速创建 Excel

```java
// 从文件路径创建
ExcelWriter.writeExcel("output.xlsx", sheetData);

// 写入多个 Sheet
ExcelWriter.writeExcel("output.xlsx", Arrays.asList(sheet1, sheet2));

// 写入到流
try (FileOutputStream fos = new FileOutputStream("output.xlsx")) {
    ExcelWriter.writeExcel(fos, sheetData, false); // false = xlsx
}

// 写入到字节数组
byte[] excelBytes = ExcelWriter.writeExcelToBytes(sheetData, false);
```

### 构建 ExcelWriter.SheetData

#### 基本构建

```java
// 使用表头和数据行
List<String> headers = Arrays.asList("姓名", "年龄", "邮箱");
List<List<Object>> rows = Arrays.asList(
    Arrays.asList("张三", 25, "zhang@example.com"),
    Arrays.asList("李四", 30, "li@example.com")
);
ExcelWriter.SheetData sheet = new ExcelWriter.SheetData("员工信息", headers, rows);

// 不使用表头
ExcelWriter.SheetData sheet = new ExcelWriter.SheetData("数据", rows);
```

#### 从 Map 列表创建

```java
List<Map<String, Object>> dataList = Arrays.asList(
    Maps.of("name", "张三", "age", 25),
    Maps.of("name", "李四", "age", 30)
);

// 自动从第一个 Map 提取表头
ExcelWriter.SheetData sheet = ExcelWriter.SheetData.fromMapList("员工信息", dataList);

// 指定表头顺序
List<String> headers = Arrays.asList("name", "age", "email");
ExcelWriter.SheetData sheet = ExcelWriter.SheetData.fromMapList("员工信息", headers, dataList);
```

#### 设置冻结窗格

```java
ExcelWriter.SheetData sheet = new ExcelWriter.SheetData("数据", headers, rows)
    .setFreezeRowCount(1)  // 冻结第1行(表头)
    .setFreezeColCount(2); // 冻结前2列
```

### 自定义样式

#### 使用 CellStyler

```java
ExcelWriter.CellStyler styler = context -> {
    if (context.isHeader()) {
        // 表头样式
        CellStyle style = context.getOrCreateStyleByType("header", () -> {
            CellStyle s = context.createCellStyle();
            Font font = context.createFont();
            font.setBold(true);
            s.setFont(font);
            s.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return s;
        });
        context.applyStyle(style);
    } else {
        // 数据行样式 - 斑马纹
        String styleKey = context.getDataRowIndex() % 2 == 0 ? "even" : "odd";
        CellStyle style = context.getOrCreateStyleByType(styleKey, () -> {
            CellStyle s = context.createCellStyle();
            if (styleKey.equals("even")) {
                s.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
                s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            return s;
        });
        context.applyStyle(style);
    }
};

// 使用样式写入
ExcelWriter.writeExcel("output.xlsx", sheetData, styler);
```

#### CellContext 常用方法

```java
// 获取上下文信息
int row = context.getRowIndex();
int col = context.getColumnIndex();
Object value = context.getValue();
boolean isHeader = context.isHeader();
String columnName = context.getColumnName();

// 创建样式和字体(带缓存)
CellStyle style = context.getOrCreateStyle("cacheKey", () -> {
    CellStyle s = context.createCellStyle();
    // 配置样式
    return s;
});

Font font = context.getOrCreateFont("fontKey", f -> {
    f.setBold(true);
    f.setFontHeightInPoints((short) 12);
});

// 应用样式
context.applyStyle(style);

// 链式调用
context.applyOrCreateStyle("myStyle", s -> {
    s.setAlignment(HorizontalAlignment.CENTER);
});
```

## 实践建议

### 读取

1. **大文件处理** - 当前基于 HSSFWorkbook/XSSFWorkbook 读取完整工作簿并构造行列表；InputStream 只是输入形式。超大文件需另行选择适合的流式处理方案
2. **类型转换** - 读取后需要进行类型检查和转换,Excel 的数字都是 `Double` 类型
3. **空值处理** - 空值、错误值与转换失败均可能返回 `null`；按业务要求补充校验
4. **日期处理** - 日期会被解析为 `java.util.Date`,需要转换为合适的日期类型

### 写入

1. **内存管理** - 当前写入也在内存中构造工作簿，大量数据需评估堆内存
2. **样式缓存** - 使用 `CellContext` 的 `getOrCreateStyle` 方法缓存样式,避免创建过多样式对象
3. **字体缓存** - 同样使用 `getOrCreateFont` 缓存字体
4. **列宽自动调整** - ExcelWriter 会自动调整列宽,但对于特殊需求可以手动设置

### 示例:完整的读写流程

```java
// 读取
List<ExcelReader.SheetData> sheets = ExcelReader.readExcel("input.xlsx");
ExcelReader.SheetData sheet = sheets.get(0);
List<Map<String, Object>> data = sheet.toMappedRows();

// 处理数据
data.forEach(row -> {
    // 业务逻辑
});

// 写入
List<String> headers = Arrays.asList("姓名", "年龄", "邮箱");
ExcelWriter.SheetData output = ExcelWriter.SheetData.fromMapList("处理结果", headers, data);
ExcelWriter.writeExcel("output.xlsx", output);
```

## 支持的格式

- **.xls** - Excel 97-2003 格式 (HSSF)
- **.xlsx** - Excel 2007+ 格式 (XSSF)