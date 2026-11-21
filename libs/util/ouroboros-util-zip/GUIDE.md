# Ouroboros Util Zip - ZIP 工具类指南

本文档描述了 `ouroboros-util-zip` 模块中的 ZIP 文件读写工具类。以下示例中的嵌套类型需显式导入：`ZipReader.ZipEntryInfo`、`ZipReader.ZipEntryWrapper`、`ZipReader.ZipStatistics` 和 `ZipWriter.ZipEntryData`、`ZipWriter.ZipWriterConfig`、`ZipWriter.WriteResult`。

## ZIP 读取 - ZipReader

路径: `com.ouroboros.util.ZipReader`

### 基本用法

#### 获取文件列表

```java
ZipReader reader = new ZipReader();

// 获取所有文件列表
List<String> files = reader.getFileList("archive.zip");
List<String> files = reader.getFileList(zipFile);
List<String> files = reader.getFileList(inputStream);

// 按条件过滤文件列表
List<String> filtered = reader.getFileList(inputStream, entry -> !entry.isDirectory());

// 按扩展名获取文件
List<String> javaFiles = reader.getFilesByExtension("archive.zip", ".java", ".kt");

// 按路径前缀获取文件
List<String> srcFiles = reader.getFilesByPrefix("archive.zip", "src/");
```

#### 获取文件信息

```java
// 获取所有文件的详细信息
List<ZipEntryInfo> infos = reader.getFileInfoList("archive.zip");

for (ZipEntryInfo info : infos) {
    String name = info.getName();
    long size = info.getSize();
    long compressed = info.getCompressedSize();
    double ratio = info.getCompressionRatio(); // 压缩率百分比
    boolean isDir = info.isDirectory();
    long lastModified = info.getLastModified();
}
```

#### 检查文件存在性

```java
// 检查 ZIP 中是否包含指定文件
boolean exists = reader.containsFile("archive.zip", "path/to/file.txt");
boolean exists = reader.containsFile(zipFile, "config.json");
boolean exists = reader.containsFile(inputStream, "README.md");
```

### 读取文件内容

#### 读取为字节数组

```java
// 读取文件内容为字节数组
byte[] content = reader.peekFileContent("archive.zip", "file.txt");
byte[] content = reader.peekFileContent(zipFile, "data.json");
byte[] content = reader.peekFileContent(inputStream, "image.png");
```

#### 读取为字符串

```java
// 使用 UTF-8 编码读取
String content = reader.peekFileContentAsString("archive.zip", "file.txt");

// 指定字符编码
String content = reader.peekFileContentAsString("archive.zip", "file.txt", "GBK");
String content = reader.peekFileContentAsString(zipFile, "data.xml", "UTF-8");
String content = reader.peekFileContentAsString(inputStream, "log.txt", "ISO-8859-1");
```

### 解压文件

#### 解压到指定目录

```java
// 解压整个 ZIP 文件
Try<Void> result = reader.extractFile("archive.zip", "/output/dir");

if (result.isSuccess()) {
    // 所有被处理的条目均成功
} else {
    Throwable error = result.getCause();
    // 处理错误
}

// 从流解压
Try<Void> result = reader.extractFile(inputStream, "/output/dir");
```

#### 自定义文件处理

```java
// 使用自定义处理器解压
BiFunction<ZipEntryWrapper, OutputStream, Boolean> handler = (entry, out) -> {
    try {
        // 可以在这里进行文件过滤、转换等操作
        if (entry.getName().endsWith(".txt")) {
            // 只处理文本文件
            String content = entry.readAsString();
            String processed = content.toUpperCase(); // 转换为大写
            out.write(processed.getBytes());
            return true;
        }
        return false; // 标记该条目失败；不表示无副作用地跳过（输出文件已创建）
    } catch (IOException e) {
        return false;
    }
};

Try<Void> result = reader.extractFile("archive.zip", "/output/dir", handler);
```

任一条目 I/O 失败或处理器返回 `false`，整体结果为 `Try.failure`。条目失败会继续收集，异常的 `getSuppressed()` 包含对应条目名和原始 cause；外层 ZIP 读取失败直接返回该异常。已经写出的文件会保留，失败结果不表示回滚。

### ZIP 统计信息

```java
// 获取 ZIP 文件统计信息
ZipStatistics stats = reader.getZipStatistics("archive.zip");

int fileCount = stats.getFileCount();
int dirCount = stats.getDirectoryCount();
long totalSize = stats.getTotalSize();
long compressedSize = stats.getTotalCompressedSize();
double ratio = stats.getOverallCompressionRatio(); // 整体压缩率
```

### ZipEntryWrapper 使用

`ZipEntryWrapper` 封装了 ZIP 条目和输入流，提供便捷的操作方法：

```java
// 在自定义处理器中使用
BiFunction<ZipEntryWrapper, OutputStream, Boolean> handler = (wrapper, out) -> {
    // 获取基本信息
    String name = wrapper.getName();
    long size = wrapper.getSize();
    boolean isDir = wrapper.isDirectory();
    double ratio = wrapper.getCompressionRatio();

    // 读取内容
    byte[] bytes = wrapper.readAllBytes();
    String content = wrapper.readAsString(); // UTF-8
    String content2 = wrapper.readAsString("GBK"); // 指定编码

    // 复制到输出流
    long copied = wrapper.copyTo(out);

    return true;
};
```

## ZIP 写入 - ZipWriter

路径: `com.ouroboros.util.ZipWriter`

### 基本用法

#### 创建 ZipWriter

```java
// 写入到文件
try (ZipWriter writer = new ZipWriter("output.zip")) {
    // 添加文件...
}

// 写入到流
try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ZipWriter writer = new ZipWriter(baos)) {
    // 添加文件...
}

// 使用静态方法
try (ZipWriter writer = ZipWriter.toFile("output.zip")) {
    // 添加文件...
}
```

#### 添加文件

```java
try (ZipWriter writer = new ZipWriter("archive.zip")) {
    // 添加单个文件
    writer.addFile("path/to/file.txt");
    writer.addFile(new File("data.json"));

    // 添加文件并指定在 ZIP 中的路径
    writer.addFile("local/file.txt", "remote/path/file.txt");
    writer.addFile(file, "archive/data.json");

    // 批量添加文件
    writer.addFiles("file1.txt", "file2.txt", "file3.txt");
    writer.addFiles(file1, file2, file3);

    // 使用 Map 指定文件和路径的对应关系
    Map<String, File> fileMap = new HashMap<>();
    fileMap.put("config/app.json", new File("app.json"));
    fileMap.put("data/users.csv", new File("users.csv"));
    writer.addFiles(fileMap);
}
```

#### 添加目录

```java
try (ZipWriter writer = new ZipWriter("archive.zip")) {
    // 添加整个目录
    writer.addDirectory("src/main/java");

    // 添加目录并指定在 ZIP 中的基路径
    writer.addDirectory("local/dir", "archive/dir");

    // 添加目录并过滤文件
    writer.addDirectory(new File("project"), "backup", file -> {
        String name = file.getName();
        return !name.endsWith(".class"); // 该 predicate 仅过滤文件，不剪枝目录遍历
    });

    // 添加空目录
    writer.addEmptyDirectory("empty/folder");
}
```

#### 添加内存数据

```java
try (ZipWriter writer = new ZipWriter("archive.zip")) {
    // 从字节数组添加
    byte[] data = ...;
    writer.addEntry("data.bin", data);

    // 从字符串添加 (UTF-8)
    writer.addEntry("readme.txt", "Hello, World!");

    // 指定字符编码
    writer.addEntry("data.txt", "你好", "GBK");
    writer.addEntry("config.xml", xmlContent, StandardCharsets.UTF_8);

    // 从输入流添加
    try (InputStream is = new FileInputStream("large.file")) {
        writer.addEntry("archive/large.file", is);
    }

    // 指定大小(可选，用于优化)
    writer.addEntry("file.dat", inputStream, fileSize);
}
```

#### 批量添加数据

```java
try (ZipWriter writer = new ZipWriter("archive.zip")) {
    // 批量添加字节数组
    Map<String, byte[]> dataMap = new HashMap<>();
    dataMap.put("file1.bin", bytes1);
    dataMap.put("file2.bin", bytes2);
    writer.addEntries(dataMap);

    // 批量添加字符串
    Map<String, String> stringMap = new HashMap<>();
    stringMap.put("file1.txt", "content1");
    stringMap.put("file2.txt", "content2");
    writer.addStringEntries(stringMap);

    // 使用 ZipEntryData 添加
    List<ZipEntryData> entries = Arrays.asList(
        ZipEntryData.fromBytes("data.bin", bytes),
        ZipEntryData.fromString("text.txt", "content"),
        ZipEntryData.fromFile("archive/file.pdf", pdfFile)
    );
    writer.addEntries(entries);
}
```

### 快速创建 ZIP

```java
// 快速打包文件
WriteResult result = ZipWriter.createZip("output.zip",
    "file1.txt", "file2.txt", "file3.txt");

if (result.isSuccess()) {
    System.out.println("Created " + result.getEntryCount() + " entries");
    System.out.println("Total bytes: " + result.getTotalBytes());
} else {
    System.err.println("Error: " + result.getErrorMessage());
}

// 快速打包字符串数据
Map<String, String> data = Map.of(
    "readme.txt", "README content",
    "config.json", "{\"key\":\"value\"}"
);
WriteResult result = ZipWriter.createZip("output.zip", data);

// 创建为字节数组
Map<String, byte[]> binaryData = Map.of(
    "data1.bin", bytes1,
    "data2.bin", bytes2
);
byte[] zipBytes = ZipWriter.createZipBytes(binaryData);
```

### 配置和高级用法

#### 使用 Builder 模式

```java
try (ZipWriter writer = ZipWriter.builder()
        .outputTo("archive.zip")
        .compressionLevel(Deflater.BEST_COMPRESSION) // 最佳压缩
        .comment("Created by Ouroboros")
        .allowDuplicateEntries(false)
        .progressCallback((name, bytes) -> {
            System.out.println("Added: " + name + " (" + bytes + " bytes)");
        })
        .build()) {

    writer.addFile("file.txt");
}
```

#### 压缩级别

```java
// 使用配置创建
ZipWriterConfig config = new ZipWriterConfig()
    .setCompressionLevel(Deflater.BEST_SPEED)      // 最快速度
    .setCompressionLevel(Deflater.BEST_COMPRESSION) // 最佳压缩
    .setCompressionLevel(Deflater.DEFAULT_COMPRESSION) // 默认
    .setCompressionLevel(Deflater.NO_COMPRESSION);   // 不压缩

try (ZipWriter writer = new ZipWriter("output.zip", config)) {
    // 添加文件...
}

// 动态修改压缩级别
writer.setCompressionLevel(Deflater.BEST_COMPRESSION);
```

#### 写入到字节数组

```java
try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
     ZipWriter writer = new ZipWriter(baos)) {

    writer.addEntry("file.txt", "content");
    writer.finish(); // 完成写入

    byte[] zipBytes = baos.toByteArray();
}
```

### 实用信息

```java
try (ZipWriter writer = new ZipWriter("archive.zip")) {
    writer.addFile("file1.txt");
    writer.addFile("file2.txt");

    // 获取统计信息
    int count = writer.getEntryCount();
    long bytes = writer.getTotalBytesWritten();
    Set<String> entries = writer.getAddedEntries();

    // 检查条目是否已存在
    boolean exists = writer.hasEntry("file1.txt");

    // 设置注释
    writer.setComment("Archive created on " + new Date());
}
```

## 安全特性

### ZipReader 安全检查

- **路径遍历防护** - 自动检测和阻止 `..` 路径遍历攻击
- **大小限制** - 默认单个文件最大 100MB (`MAX_ENTRY_SIZE`)
- **条目数量限制** - 默认最多 10000 个条目 (`MAX_ENTRIES`)
- **解压路径验证** - 确保解压的文件在指定目录内

### ZipWriter 安全检查

- **路径验证** - 阻止包含 `..` 或以 `/` 开头的不安全路径
- **总大小限制** - 默认总大小不超过 1GB (`MAX_TOTAL_SIZE`)
- **条目数量限制** - 默认最多 50000 个条目 (`MAX_ENTRIES`)
- **重复条目处理** - 可配置是否允许重复条目

## 实践建议

### 读取

1. **使用 Try 处理错误** - 解压返回 `Try<Void>`；单条目 I/O 异常或 handler=false 使整体失败，逐条原因见 suppressed 异常。失败可能保留部分输出，重试前由调用方决定清理或复用。
2. **流的使用** - InputStream 重载顺序消费并关闭传入流，后续读取需重新打开；`peekFileContent` 和 wrapper 的 `readAllBytes/readAsString` 仍会把条目内容加载到内存。
3. **自定义处理器** - 使用 `ZipEntryWrapper` 转换当前条目内容；读取会消费同一条目流，不能依次读取多种格式后仍期望拿到完整数据。
4. **安全检查** - 始终验证文件来源，防止 Zip Bomb 攻击

### 写入

1. **使用 try-with-resources** - 确保 `ZipWriter` 正确关闭
2. **批量操作** - 使用批量添加方法提高性能
3. **压缩级别** - 根据需求选择合适的压缩级别
4. **进度回调** - 对于大文件，使用进度回调提供用户反馈

### 示例：完整的压缩和解压流程

```java
// 创建 ZIP
try (ZipWriter writer = ZipWriter.builder()
        .outputTo("backup.zip")
        .compressionLevel(Deflater.BEST_COMPRESSION)
        .progressCallback((name, bytes) ->
            System.out.println("Compressed: " + name))
        .build()) {

    writer.addDirectory(new File("src"), "source", file ->
        file.getName().endsWith(".java"));
    writer.addEntry("README.md", "Project Backup");
}

// 读取 ZIP
ZipReader reader = new ZipReader();
List<ZipEntryInfo> files = reader.getFileInfoList("backup.zip");
files.forEach(System.out::println);

// 解压 ZIP
Try<Void> result = reader.extractFile("backup.zip", "restore");
result.onFailure(error -> System.err.println("Failed: " + error.getMessage()));
```

## 限制配置

当前硬编码的限制（可以通过修改源码调整）：

**ZipReader:**
- `MAX_ENTRY_SIZE`: 100MB - 单个条目最大大小
- `MAX_ENTRIES`: 10000 - 最大条目数

**ZipWriter:**
- `MAX_TOTAL_SIZE`: 1GB - 总大小限制
- `MAX_ENTRIES`: 50000 - 最大条目数
