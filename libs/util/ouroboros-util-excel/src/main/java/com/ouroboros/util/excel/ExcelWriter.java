package com.ouroboros.util.excel;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel 文件写入工具类
 * 支持 .xls 和 .xlsx 格式文件的写入，提供多种写入方式和数据格式
 *
 * @author Claude Code
 */
public class ExcelWriter {

  private static final Logger LOGGER = Logger.getLogger(ExcelWriter.class.getName());

  /**
   * 写入单个 Sheet 到 Excel 文件
   *
   * @param filePath  Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetData Sheet 数据
   * @throws IOException              当文件写入失败时抛出
   * @throws IllegalArgumentException 当文件路径无效时抛出
   */
  public static void writeExcel(String filePath, SheetData sheetData) throws IOException {
    writeExcel(new File(filePath), sheetData);
  }

  /**
   * 写入多个 Sheet 到 Excel 文件
   *
   * @param filePath   Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetDatas Sheet 数据列表
   * @throws IOException              当文件写入失败时抛出
   * @throws IllegalArgumentException 当文件路径无效或列表为空时抛出
   */
  public static void writeExcel(String filePath, List<SheetData> sheetDatas) throws IOException {
    writeExcel(new File(filePath), sheetDatas);
  }

  /**
   * 写入单个 Sheet 到 Excel 文件
   *
   * @param file      Excel 文件对象
   * @param sheetData Sheet 数据
   * @throws IOException              当文件写入失败时抛出
   * @throws IllegalArgumentException 当文件无效时抛出
   */
  public static void writeExcel(File file, SheetData sheetData) throws IOException {
    writeExcel(file, Collections.singletonList(sheetData));
  }

  /**
   * 写入多个 Sheet 到 Excel 文件
   *
   * @param file       Excel 文件对象
   * @param sheetDatas Sheet 数据列表
   * @throws IOException              当文件写入失败时抛出
   * @throws IllegalArgumentException 当文件无效或列表为空时抛出
   */
  public static void writeExcel(File file, List<SheetData> sheetDatas) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("文件对象不能为空");
    }
    if (sheetDatas == null || sheetDatas.isEmpty()) {
      throw new IllegalArgumentException("Sheet数据列表不能为空");
    }

    String fileName = file.getName().toLowerCase();
    if (!fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
      throw new IllegalArgumentException("不支持的文件格式，仅支持 .xls 和 .xlsx 文件");
    }

    boolean isXls = fileName.endsWith(".xls");

    // 确保父目录存在
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs()) {
        throw new IOException("无法创建目录: " + parent.getAbsolutePath());
      }
    }

    try (FileOutputStream fos = new FileOutputStream(file)) {
      writeExcel(fos, sheetDatas, isXls);
    }
  }

  /**
   * 写入单个 Sheet 到输出流
   *
   * @param out       输出流
   * @param sheetData Sheet 数据
   * @param isXls     是否为 .xls 格式文件
   * @throws IOException              当写入失败时抛出
   * @throws IllegalArgumentException 当输出流为空时抛出
   */
  public static void writeExcel(OutputStream out, SheetData sheetData, boolean isXls) throws IOException {
    writeExcel(out, Collections.singletonList(sheetData), isXls);
  }

  /**
   * 写入多个 Sheet 到输出流
   *
   * @param out        输出流
   * @param sheetDatas Sheet 数据列表
   * @param isXls      是否为 .xls 格式文件
   * @throws IOException              当写入失败时抛出
   * @throws IllegalArgumentException 当输出流为空或列表为空时抛出
   */
  public static void writeExcel(OutputStream out, List<SheetData> sheetDatas, boolean isXls) throws IOException {
    writeExcel(out, sheetDatas, isXls, null);
  }

  /**
   * 写入单个 Sheet 到输出流（支持自定义格式）
   *
   * @param out        输出流
   * @param sheetData  Sheet 数据
   * @param isXls      是否为 .xls 格式文件
   * @param cellStyler 单元格样式设置器
   * @throws IOException              当写入失败时抛出
   * @throws IllegalArgumentException 当输出流为空时抛出
   */
  public static void writeExcel(OutputStream out, SheetData sheetData, boolean isXls, CellStyler cellStyler) throws IOException {
    writeExcel(out, Collections.singletonList(sheetData), isXls, cellStyler);
  }

  /**
   * 写入多个 Sheet 到输出流（支持自定义格式）
   *
   * @param out        输出流
   * @param sheetDatas Sheet 数据列表
   * @param isXls      是否为 .xls 格式文件
   * @param cellStyler 单元格样式设置器
   * @throws IOException              当写入失败时抛出
   * @throws IllegalArgumentException 当输出流为空或列表为空时抛出
   */
  public static void writeExcel(OutputStream out, List<SheetData> sheetDatas, boolean isXls, CellStyler cellStyler) throws IOException {
    if (out == null) {
      throw new IllegalArgumentException("输出流不能为空");
    }
    if (sheetDatas == null || sheetDatas.isEmpty()) {
      throw new IllegalArgumentException("Sheet数据列表不能为空");
    }

    try (Workbook workbook = isXls ? new HSSFWorkbook() : new XSSFWorkbook()) {
      for (SheetData sheetData : sheetDatas) {
        writeSheet(workbook, sheetData, cellStyler);
      }
      workbook.write(out);
    } catch (Exception e) {
      throw new IOException("写入Excel文件失败: " + e.getMessage(), e);
    }
  }

  /**
   * 写入单个 Sheet 到文件（支持自定义格式）
   *
   * @param filePath   Excel 文件路径
   * @param sheetData  Sheet 数据
   * @param cellStyler 单元格样式设置器
   * @throws IOException 当文件写入失败时抛出
   */
  public static void writeExcel(String filePath, SheetData sheetData, CellStyler cellStyler) throws IOException {
    writeExcel(new File(filePath), sheetData, cellStyler);
  }

  /**
   * 写入多个 Sheet 到文件（支持自定义格式）
   *
   * @param filePath   Excel 文件路径
   * @param sheetDatas Sheet 数据列表
   * @param cellStyler 单元格样式设置器
   * @throws IOException 当文件写入失败时抛出
   */
  public static void writeExcel(String filePath, List<SheetData> sheetDatas, CellStyler cellStyler) throws IOException {
    writeExcel(new File(filePath), sheetDatas, cellStyler);
  }

  /**
   * 写入单个 Sheet 到文件（支持自定义格式）
   *
   * @param file       Excel 文件对象
   * @param sheetData  Sheet 数据
   * @param cellStyler 单元格样式设置器
   * @throws IOException 当文件写入失败时抛出
   */
  public static void writeExcel(File file, SheetData sheetData, CellStyler cellStyler) throws IOException {
    writeExcel(file, Collections.singletonList(sheetData), cellStyler);
  }

  /**
   * 写入多个 Sheet 到文件（支持自定义格式）
   *
   * @param file       Excel 文件对象
   * @param sheetDatas Sheet 数据列表
   * @param cellStyler 单元格样式设置器
   * @throws IOException 当文件写入失败时抛出
   */
  public static void writeExcel(File file, List<SheetData> sheetDatas, CellStyler cellStyler) throws IOException {
    if (file == null) {
      throw new IllegalArgumentException("文件对象不能为空");
    }
    if (sheetDatas == null || sheetDatas.isEmpty()) {
      throw new IllegalArgumentException("Sheet数据列表不能为空");
    }

    String fileName = file.getName().toLowerCase();
    if (!fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
      throw new IllegalArgumentException("不支持的文件格式，仅支持 .xls 和 .xlsx 文件");
    }

    boolean isXls = fileName.endsWith(".xls");

    // 确保父目录存在
    File parent = file.getParentFile();
    if (parent != null && !parent.exists()) {
      if (!parent.mkdirs()) {
        throw new IOException("无法创建目录: " + parent.getAbsolutePath());
      }
    }

    try (FileOutputStream fos = new FileOutputStream(file)) {
      writeExcel(fos, sheetDatas, isXls, cellStyler);
    }
  }

  /**
   * 写入字节数组（支持自定义格式）
   *
   * @param sheetData  Sheet 数据
   * @param isXls      是否为 .xls 格式文件
   * @param cellStyler 单元格样式设置器
   * @return Excel 文件字节数组
   * @throws IOException 当写入失败时抛出
   */
  public static byte[] writeExcelToBytes(SheetData sheetData, boolean isXls, CellStyler cellStyler) throws IOException {
    return writeExcelToBytes(Collections.singletonList(sheetData), isXls, cellStyler);
  }

  /**
   * 写入字节数组（支持自定义格式）
   *
   * @param sheetDatas Sheet 数据列表
   * @param isXls      是否为 .xls 格式文件
   * @param cellStyler 单元格样式设置器
   * @return Excel 文件字节数组
   * @throws IOException 当写入失败时抛出
   */
  public static byte[] writeExcelToBytes(List<SheetData> sheetDatas, boolean isXls, CellStyler cellStyler) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      writeExcel(baos, sheetDatas, isXls, cellStyler);
      return baos.toByteArray();
    }
  }

  /**
   * 写入 Sheet 数据到工作簿
   *
   * @param workbook  工作簿对象
   * @param sheetData Sheet 数据
   */
  private static void writeSheet(Workbook workbook, SheetData sheetData) {
    writeSheet(workbook, sheetData, null);
  }

  /**
   * 写入 Sheet 数据到工作簿（支持自定义格式）
   *
   * @param workbook   工作簿对象
   * @param sheetData  Sheet 数据
   * @param cellStyler 单元格样式设置器
   */
  private static void writeSheet(Workbook workbook, SheetData sheetData, CellStyler cellStyler) {
    Sheet sheet = workbook.createSheet(sheetData.getSheetName());
    sheet.createFreezePane(sheetData.getFreezeColCount(),  sheetData.getFreezeRowCount());

    // 创建样式和字体缓存
    Map<String, CellStyle> styleCache = new HashMap<>();
    Map<String, Font> fontCache = new HashMap<>();

    int rowIndex = 0;

    // 写入表头
    if (sheetData.getHeaders() != null && !sheetData.getHeaders().isEmpty()) {
      Row headerRow = sheet.createRow(rowIndex);
      List<String> headers = sheetData.getHeaders();
      for (int i = 0; i < headers.size(); i++) {
        Cell cell = headerRow.createCell(i);
        cell.setCellValue(headers.get(i));

        // 应用自定义样式
        if (cellStyler != null) {
          CellContext context = new CellContext(
              workbook,
              sheet,
              cell,
              rowIndex,
              i,
              headers.get(i),
              true,
              headers.get(i),
              i,
              styleCache,
              fontCache
          );
          cellStyler.style(context);
        } else {
          // 默认表头样式
          CellStyle headerStyle = workbook.createCellStyle();
          Font headerFont = workbook.createFont();
          headerFont.setBold(true);
          headerStyle.setFont(headerFont);
          cell.setCellStyle(headerStyle);
        }
      }
      rowIndex++;
    }

    // 写入数据行
    if (sheetData.getRows() != null) {
      int dataRowIndex = 0;
      for (List<Object> rowData : sheetData.getRows()) {
        Row row = sheet.createRow(rowIndex);
        for (int i = 0; i < rowData.size(); i++) {
          Cell cell = row.createCell(i);
          Object value = rowData.get(i);
          setCellValue(cell, value);

          // 应用自定义样式
          if (cellStyler != null) {
            String columnName = null;
            if (sheetData.getHeaders() != null && i < sheetData.getHeaders().size()) {
              columnName = sheetData.getHeaders().get(i);
            }

            CellContext context = new CellContext(
                workbook,
                sheet,
                cell,
                rowIndex,
                i,
                value,
                false,
                columnName,
                dataRowIndex,
                styleCache,
                fontCache
            );
            cellStyler.style(context);
          }
        }
        rowIndex++;
        dataRowIndex++;
      }
    }

    // 自动调整列宽
    if (sheetData.getHeaders() != null && !sheetData.getHeaders().isEmpty()) {
      for (int i = 0; i < sheetData.getHeaders().size(); i++) {
        try {
          var currentColIndex = i;
          var colHeaderWidth = getCellWidth(sheetData.getHeaders().get(i));
          var dataColumnWidth = sheetData.getRows()
              .stream()
              .map(row -> row.size() > currentColIndex ? row.get(currentColIndex) : null)
              .filter(Objects::nonNull)
              .map(Objects::toString)
              .mapToInt(ExcelWriter::getCellWidth)
              .max()
              .orElse(colHeaderWidth);
          sheet.setColumnWidth(currentColIndex, (Math.max(colHeaderWidth, dataColumnWidth) + 2) * 256);
        } catch (Exception e) {
          LOGGER.log(Level.WARNING, "自动调整列宽失败，已保留默认列宽", e);
        }
      }
    }
  }

  public static int getCellWidth(String value) {
    if (value == null) {
      return 0;
    }

    int width = 0;
    for (char c : value.toCharArray()) {
      // 中文（CJK unicode 范围）
      if (c >= 0x4e00 && c <= 0x9fa5) {
        width += 2; // 中文宽度 2
      } else {
        width += 1; // 英文宽度 1
      }
    }
    return width;
  }

  /**
   * 设置单元格值
   *
   * @param cell  单元格对象
   * @param value 值对象
   */
  private static void setCellValue(Cell cell, Object value) {
    if (value == null) {
      cell.setBlank();
      return;
    }

    if (value instanceof String) {
      cell.setCellValue((String) value);
    } else if (value instanceof Number) {
      cell.setCellValue(((Number) value).doubleValue());
    } else if (value instanceof Boolean) {
      cell.setCellValue((Boolean) value);
    } else if (value instanceof Date) {
      cell.setCellValue((Date) value);
      // 设置日期格式
      CellStyle dateStyle = cell.getSheet().getWorkbook().createCellStyle();
      CreationHelper createHelper = cell.getSheet().getWorkbook().getCreationHelper();
      dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-MM-dd HH:mm:ss"));
      cell.setCellStyle(dateStyle);
    } else {
      cell.setCellValue(value.toString());
    }
  }

  /**
   * 写入字节数组
   *
   * @param sheetData Sheet 数据
   * @param isXls     是否为 .xls 格式文件
   * @return Excel 文件字节数组
   * @throws IOException 当写入失败时抛出
   */
  public static byte[] writeExcelToBytes(SheetData sheetData, boolean isXls) throws IOException {
    return writeExcelToBytes(Collections.singletonList(sheetData), isXls);
  }

  /**
   * 写入字节数组
   *
   * @param sheetDatas Sheet 数据列表
   * @param isXls      是否为 .xls 格式文件
   * @return Excel 文件字节数组
   * @throws IOException 当写入失败时抛出
   */
  public static byte[] writeExcelToBytes(List<SheetData> sheetDatas, boolean isXls) throws IOException {
    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      writeExcel(baos, sheetDatas, isXls);
      return baos.toByteArray();
    }
  }

  // ----------------- 数据模型 -----------------

  /**
   * Sheet 数据封装类
   * 封装单个 Sheet 的所有数据，包括 Sheet 名称、表头和数据行
   */
  public static class SheetData {
    private final String sheetName;
    private final List<String> headers;
    private final List<List<Object>> rows;
    private int freezeRowCount = 0;
    private int freezeColCount = 0;

    /**
     * 构造 Sheet 数据对象（包含表头）
     *
     * @param sheetName Sheet 名称
     * @param headers   表头列表
     * @param rows      数据行列表
     */
    public SheetData(String sheetName, List<String> headers, List<List<Object>> rows) {
      this.sheetName = sheetName != null && !sheetName.isEmpty() ? sheetName : "Sheet1";
      this.headers = headers != null ? new ArrayList<>(headers) : Collections.emptyList();
      this.rows = rows != null ? new ArrayList<>(rows) : Collections.emptyList();
    }

    /**
     * 构造 Sheet 数据对象（不包含表头）
     *
     * @param sheetName Sheet 名称
     * @param rows      数据行列表
     */
    public SheetData(String sheetName, List<List<Object>> rows) {
      this(sheetName, null, rows);
    }

    /**
     * 从 Map 列表创建 Sheet 数据
     * 使用 Map 的 key 作为表头，value 作为数据
     *
     * @param sheetName Sheet 名称
     * @param dataList  Map 数据列表
     * @return Sheet 数据对象
     */
    public static SheetData fromMapList(String sheetName, List<Map<String, Object>> dataList) {
      if (dataList == null || dataList.isEmpty()) {
        return new SheetData(sheetName, Collections.emptyList(), Collections.emptyList());
      }

      // 从第一个 Map 获取表头
      Map<String, Object> firstRow = dataList.get(0);
      List<String> headers = new ArrayList<>(firstRow.keySet());

      // 提取数据行
      List<List<Object>> rows = new ArrayList<>();
      for (Map<String, Object> dataRow : dataList) {
        List<Object> row = new ArrayList<>();
        for (String header : headers) {
          row.add(dataRow.get(header));
        }
        rows.add(row);
      }

      return new SheetData(sheetName, headers, rows);
    }

    /**
     * 从 Map 列表创建 Sheet 数据（指定表头顺序）
     *
     * @param sheetName Sheet 名称
     * @param headers   表头列表（指定顺序）
     * @param dataList  Map 数据列表
     * @return Sheet 数据对象
     */
    public static SheetData fromMapList(String sheetName, List<String> headers, List<Map<String, Object>> dataList) {
      if (dataList == null || dataList.isEmpty()) {
        return new SheetData(sheetName, headers, Collections.emptyList());
      }

      if (headers == null || headers.isEmpty()) {
        return fromMapList(sheetName, dataList);
      }

      // 提取数据行
      List<List<Object>> rows = new ArrayList<>();
      for (Map<String, Object> dataRow : dataList) {
        List<Object> row = new ArrayList<>();
        for (String header : headers) {
          row.add(dataRow.get(header));
        }
        rows.add(row);
      }

      return new SheetData(sheetName, headers, rows);
    }

    public SheetData setFreezeRowCount(int freezeRowCount) {
      this.freezeRowCount = freezeRowCount;
      return this;
    }

    public SheetData setFreezeColCount(int freezeColCount) {
      this.freezeColCount = freezeColCount;
      return this;
    }

    public int getFreezeRowCount() {
      return freezeRowCount;
    }

    public int getFreezeColCount() {
      return freezeColCount;
    }

    /**
     * 获取 Sheet 名称
     *
     * @return Sheet 名称
     */
    public String getSheetName() {
      return sheetName;
    }

    /**
     * 获取表头列表
     *
     * @return 表头列表
     */
    public List<String> getHeaders() {
      return Collections.unmodifiableList(headers);
    }

    /**
     * 获取数据行列表
     *
     * @return 数据行列表
     */
    public List<List<Object>> getRows() {
      return Collections.unmodifiableList(rows);
    }

    @Override
    public String toString() {
      return "SheetData{name='" + sheetName + "', headers=" + headers.size() + ", rows=" + rows.size() + "}";
    }
  }

  /**
   * 单元格样式设置器接口
   * 用于自定义单元格格式，接收完整的单元格上下文信息
   */
  @FunctionalInterface
  public interface CellStyler {
    /**
     * 设置单元格样式
     *
     * @param context 单元格上下文信息
     */
    void style(CellContext context);
  }

  /**
   * 单元格上下文类
   * 包含单元格的完整上下文信息，供样式设置器使用
   */
  public static class CellContext {
    private final Workbook workbook;
    private final Sheet sheet;
    private final Cell cell;
    private final int rowIndex;
    private final int columnIndex;
    private final Object value;
    private final boolean isHeader;
    private final String columnName;
    private final int dataRowIndex;
    private final Map<String, CellStyle> styleCache;
    private final Map<String, Font> fontCache;

    /**
     * 构造单元格上下文
     *
     * @param workbook     工作簿对象
     * @param sheet        Sheet 对象
     * @param cell         单元格对象
     * @param rowIndex     行索引（包括表头）
     * @param columnIndex  列索引
     * @param value        单元格值
     * @param isHeader     是否为表头
     * @param columnName   列名（表头名称）
     * @param dataRowIndex 数据行索引（不包括表头，从0开始）
     * @param styleCache   样式缓存
     * @param fontCache    字体缓存
     */
    public CellContext(Workbook workbook, Sheet sheet, Cell cell,
                       int rowIndex, int columnIndex, Object value,
                       boolean isHeader, String columnName, int dataRowIndex,
                       Map<String, CellStyle> styleCache, Map<String, Font> fontCache) {
      this.workbook = workbook;
      this.sheet = sheet;
      this.cell = cell;
      this.rowIndex = rowIndex;
      this.columnIndex = columnIndex;
      this.value = value;
      this.isHeader = isHeader;
      this.columnName = columnName;
      this.dataRowIndex = dataRowIndex;
      this.styleCache = styleCache != null ? styleCache : new HashMap<>();
      this.fontCache = fontCache != null ? fontCache : new HashMap<>();
    }

    /**
     * 获取工作簿对象
     *
     * @return 工作簿对象
     */
    public Workbook getWorkbook() {
      return workbook;
    }

    /**
     * 获取 Sheet 对象
     *
     * @return Sheet 对象
     */
    public Sheet getSheet() {
      return sheet;
    }

    /**
     * 获取单元格对象
     *
     * @return 单元格对象
     */
    public Cell getCell() {
      return cell;
    }

    /**
     * 获取行索引（包括表头）
     *
     * @return 行索引
     */
    public int getRowIndex() {
      return rowIndex;
    }

    /**
     * 获取列索引
     *
     * @return 列索引
     */
    public int getColumnIndex() {
      return columnIndex;
    }

    /**
     * 获取单元格值
     *
     * @return 单元格值
     */
    public Object getValue() {
      return value;
    }

    /**
     * 判断是否为表头
     *
     * @return true 表示是表头
     */
    public boolean isHeader() {
      return isHeader;
    }

    /**
     * 获取列名（表头名称）
     *
     * @return 列名，如果没有表头则返回 null
     */
    public String getColumnName() {
      return columnName;
    }

    /**
     * 获取数据行索引（不包括表头，从0开始）
     *
     * @return 数据行索引
     */
    public int getDataRowIndex() {
      return dataRowIndex;
    }

    /**
     * 获取 Sheet 名称
     *
     * @return Sheet 名称
     */
    public String getSheetName() {
      return sheet.getSheetName();
    }

    /**
     * 创建新的单元格样式
     *
     * @return 新的单元格样式
     */
    public CellStyle createCellStyle() {
      return workbook.createCellStyle();
    }

    /**
     * 创建新的字体
     * 注意：通常应该使用 getOrCreateFont 来避免创建重复字体
     *
     * @return 新的字体对象
     */
    public Font createFont() {
      return workbook.createFont();
    }

    /**
     * 获取创建助手（用于创建数据格式等）
     * 注意：DataFormat 在 POI 内部已经自动缓存，不需要手动缓存
     *
     * @return 创建助手
     */
    public CreationHelper getCreationHelper() {
      return workbook.getCreationHelper();
    }

    /**
     * 获取或创建字体（带缓存）
     * 如果缓存中已存在该 key 对应的字体，则直接返回；否则创建新字体并缓存
     *
     * @param cacheKey     缓存键
     * @param fontCreator  字体创建函数
     * @return 字体对象
     */
    public Font getOrCreateFont(String cacheKey, Supplier<Font> fontCreator) {
      return fontCache.computeIfAbsent(cacheKey, k -> fontCreator.get());
    }

    /**
     * 获取或创建字体（带缓存，使用配置器）
     * 如果缓存中已存在该 key 对应的字体，则直接返回；否则自动创建新字体，传递给配置器进行配置，然后缓存
     *
     * @param cacheKey          缓存键
     * @param fontConfigurator  字体配置函数，接收已创建的 Font 对象进行配置
     * @return 字体对象
     */
    public Font getOrCreateFont(String cacheKey, Consumer<Font> fontConfigurator) {
      return fontCache.computeIfAbsent(cacheKey, k -> {
        Font font = createFont();
        fontConfigurator.accept(font);
        return font;
      });
    }

    /**
     * 获取或创建字体（带缓存）
     * 根据字体特征自动生成缓存键
     *
     * @param fontType     字体类型（如 "header-bold", "data-normal" 等）
     * @param fontCreator  字体创建函数
     * @return 字体对象
     */
    public Font getOrCreateFontByType(String fontType, Supplier<Font> fontCreator) {
      return getOrCreateFont(fontType, fontCreator);
    }

    /**
     * 获取或创建字体（带缓存，使用配置器）
     * 根据字体特征自动生成缓存键
     *
     * @param fontType          字体类型（如 "header-bold", "data-normal" 等）
     * @param fontConfigurator  字体配置函数
     * @return 字体对象
     */
    public Font getOrCreateFontByType(String fontType, Consumer<Font> fontConfigurator) {
      return getOrCreateFont(fontType, fontConfigurator);
    }

    /**
     * 获取字体缓存中的字体数量
     *
     * @return 缓存的字体数量
     */
    public int getFontCacheSize() {
      return fontCache.size();
    }

    /**
     * 获取或创建样式（带缓存）
     * 如果缓存中已存在该 key 对应的样式，则直接返回；否则创建新样式并缓存
     *
     * @param cacheKey      缓存键
     * @param styleCreator  样式创建函数
     * @return 单元格样式
     */
    public CellStyle getOrCreateStyle(String cacheKey, Supplier<CellStyle> styleCreator) {
      return styleCache.computeIfAbsent(cacheKey, k -> styleCreator.get());
    }

    /**
     * 获取或创建样式（带缓存，使用配置器）
     * 如果缓存中已存在该 key 对应的样式，则直接返回；否则自动创建新样式，传递给配置器进行配置，然后缓存
     *
     * @param cacheKey           缓存键
     * @param styleConfigurator  样式配置函数，接收已创建的 CellStyle 对象进行配置
     * @return 单元格样式
     */
    public CellStyle getOrCreateStyle(String cacheKey, Consumer<CellStyle> styleConfigurator) {
      return styleCache.computeIfAbsent(cacheKey, k -> {
        CellStyle style = createCellStyle();
        styleConfigurator.accept(style);
        return style;
      });
    }

    /**
     * 获取或创建样式（带缓存）
     * 根据样式类型自动生成缓存键
     *
     * @param styleType     样式类型（如 "header", "data-even", "data-odd" 等）
     * @param styleCreator  样式创建函数
     * @return 单元格样式
     */
    public CellStyle getOrCreateStyleByType(String styleType, Supplier<CellStyle> styleCreator) {
      return getOrCreateStyle(styleType, styleCreator);
    }

    /**
     * 获取或创建样式（带缓存，使用配置器）
     * 根据样式类型自动生成缓存键
     *
     * @param styleType          样式类型（如 "header", "data-even", "data-odd" 等）
     * @param styleConfigurator  样式配置函数
     * @return 单元格样式
     */
    public CellStyle getOrCreateStyleByType(String styleType, Consumer<CellStyle> styleConfigurator) {
      return getOrCreateStyle(styleType, styleConfigurator);
    }

    /**
     * 获取或创建列样式（带缓存）
     * 根据列名自动生成缓存键
     *
     * @param styleCreator 样式创建函数
     * @return 单元格样式
     */
    public CellStyle getOrCreateColumnStyle(Supplier<CellStyle> styleCreator) {
      String cacheKey = "column:" + (columnName != null ? columnName : "col" + columnIndex);
      return getOrCreateStyle(cacheKey, styleCreator);
    }

    /**
     * 获取或创建列样式（带缓存，使用配置器）
     * 根据列名自动生成缓存键
     *
     * @param styleConfigurator 样式配置函数
     * @return 单元格样式
     */
    public CellStyle getOrCreateColumnStyle(Consumer<CellStyle> styleConfigurator) {
      String cacheKey = "column:" + (columnName != null ? columnName : "col" + columnIndex);
      return getOrCreateStyle(cacheKey, styleConfigurator);
    }

    /**
     * 应用样式到当前单元格
     *
     * @param style 要应用的样式
     * @return 当前上下文对象（支持链式调用）
     */
    public CellContext applyStyle(CellStyle style) {
      cell.setCellStyle(style);
      return this;
    }

    /**
     * 获取或创建样式并应用到当前单元格
     *
     * @param cacheKey     缓存键
     * @param styleCreator 样式创建函数
     * @return 当前上下文对象（支持链式调用）
     */
    public CellContext applyOrCreateStyle(String cacheKey, Supplier<CellStyle> styleCreator) {
      CellStyle style = getOrCreateStyle(cacheKey, styleCreator);
      return applyStyle(style);
    }

    /**
     * 获取或创建样式并应用到当前单元格（使用配置器）
     *
     * @param cacheKey          缓存键
     * @param styleConfigurator 样式配置函数
     * @return 当前上下文对象（支持链式调用）
     */
    public CellContext applyOrCreateStyle(String cacheKey, Consumer<CellStyle> styleConfigurator) {
      CellStyle style = getOrCreateStyle(cacheKey, styleConfigurator);
      return applyStyle(style);
    }

    /**
     * 获取样式缓存中的样式数量
     *
     * @return 缓存的样式数量
     */
    public int getStyleCacheSize() {
      return styleCache.size();
    }

    @Override
    public String toString() {
      return "CellContext{" +
          "sheet='" + sheet.getSheetName() + '\'' +
          ", row=" + rowIndex +
          ", column=" + columnIndex +
          ", value=" + value +
          ", isHeader=" + isHeader +
          ", columnName='" + columnName + '\'' +
          ", dataRowIndex=" + dataRowIndex +
          ", cachedStyles=" + styleCache.size() +
          ", cachedFonts=" + fontCache.size() +
          '}';
    }
  }
}
