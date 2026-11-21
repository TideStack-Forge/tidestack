package com.ouroboros.util.excel;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import io.vavr.control.Either;

/**
 * Excel 文件读取工具类
 * 支持 .xls 和 .xlsx 格式文件的读取，提供多种读取方式和数据转换功能
 *
 */
public class ExcelReader {

  /**
   * 读取 Excel 文件，返回所有 Sheet 的数据
   *
   * @param filePath Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @return 包含所有 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件路径无效时抛出
   */
  public static List<SheetData> readExcel(String filePath) throws IOException {
    return readExcel(new File(filePath));
  }

  /**
   * 读取 Excel 文件，返回指定索引的 Sheet 数据
   *
   * @param filePath   Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetIndex Sheet 索引，从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件路径无效时抛出
   */
  public static List<SheetData> readExcel(String filePath, int sheetIndex) throws IOException {
    return readExcel(new File(filePath), sheetIndex);
  }

  /**
   * 读取 Excel 文件，返回指定名称的 Sheet 数据
   *
   * @param filePath  Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetName Sheet 名称
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件路径无效或 Sheet 名称为空时抛出
   */
  public static List<SheetData> readExcel(String filePath, String sheetName) throws IOException {
    return readExcel(new File(filePath), sheetName);
  }

  /**
   * 读取 Excel 文件，返回指定索引列表的 Sheet 数据
   *
   * @param filePath     Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetIndexes Sheet 索引列表，索引从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件路径无效或索引列表为空时抛出
   */
  public static List<SheetData> readExcel(String filePath, List<Integer> sheetIndexes) throws IOException {
    return readExcel(new File(filePath), sheetIndexes);
  }

  /**
   * 读取 Excel 文件，返回指定名称列表的 Sheet 数据
   *
   * @param filePath   Excel 文件路径，必须以 .xls 或 .xlsx 结尾
   * @param sheetNames Sheet 名称列表
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件路径无效或名称列表为空时抛出
   */
  public static List<SheetData> readExcelByNames(String filePath, List<String> sheetNames) throws IOException {
    return readExcelByNames(new File(filePath), sheetNames);
  }

  /**
   * 读取 Excel 文件，返回所有 Sheet 的数据
   *
   * @param excelFile Excel 文件对象
   * @return 包含所有 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件无效时抛出
   */
  public static List<SheetData> readExcel(File excelFile) throws IOException {
    ValidationResult validation = validateExcelFile(excelFile);
    if (!validation.isValid()) {
      throw new IllegalArgumentException("文件验证失败: " + String.join(", ", validation.getErrors()));
    }

    try (InputStream fis = new FileInputStream(excelFile)) {
      return readExcel(fis, excelFile.getName().toLowerCase().endsWith(".xls"));
    }
  }

  /**
   * 读取 Excel 文件，返回指定索引的 Sheet 数据
   *
   * @param excelFile  Excel 文件对象
   * @param sheetIndex Sheet 索引，从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件无效时抛出
   */
  public static List<SheetData> readExcel(File excelFile, int sheetIndex) throws IOException {
    ValidationResult validation = validateExcelFile(excelFile);
    if (!validation.isValid()) {
      throw new IllegalArgumentException("文件验证失败: " + String.join(", ", validation.getErrors()));
    }

    try (InputStream fis = new FileInputStream(excelFile)) {
      return readExcel(fis, excelFile.getName().toLowerCase().endsWith(".xls"), sheetIndex);
    }
  }

  /**
   * 读取 Excel 文件，返回指定名称的 Sheet 数据
   *
   * @param excelFile Excel 文件对象
   * @param sheetName Sheet 名称
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件无效或 Sheet 名称为空时抛出
   */
  public static List<SheetData> readExcel(File excelFile, String sheetName) throws IOException {
    ValidationResult validation = validateExcelFile(excelFile);
    if (!validation.isValid()) {
      throw new IllegalArgumentException("文件验证失败: " + String.join(", ", validation.getErrors()));
    }

    try (InputStream fis = new FileInputStream(excelFile)) {
      return readExcel(fis, excelFile.getName().toLowerCase().endsWith(".xls"), sheetName);
    }
  }

  /**
   * 读取 Excel 文件，返回指定索引列表的 Sheet 数据
   *
   * @param excelFile    Excel 文件对象
   * @param sheetIndexes Sheet 索引列表，索引从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件无效或索引列表为空时抛出
   */
  public static List<SheetData> readExcel(File excelFile, List<Integer> sheetIndexes) throws IOException {
    ValidationResult validation = validateExcelFile(excelFile);
    if (!validation.isValid()) {
      throw new IllegalArgumentException("文件验证失败: " + String.join(", ", validation.getErrors()));
    }

    try (InputStream fis = new FileInputStream(excelFile)) {
      return readExcel(fis, excelFile.getName().toLowerCase().endsWith(".xls"), sheetIndexes);
    }
  }

  /**
   * 读取 Excel 文件，返回指定名称列表的 Sheet 数据
   *
   * @param excelFile  Excel 文件对象
   * @param sheetNames Sheet 名称列表
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当文件无效或名称列表为空时抛出
   */
  public static List<SheetData> readExcelByNames(File excelFile, List<String> sheetNames) throws IOException {
    ValidationResult validation = validateExcelFile(excelFile);
    if (!validation.isValid()) {
      throw new IllegalArgumentException("文件验证失败: " + String.join(", ", validation.getErrors()));
    }

    try (InputStream fis = new FileInputStream(excelFile)) {
      return readExcelByNames(fis, excelFile.getName().toLowerCase().endsWith(".xls"), sheetNames);
    }
  }

  /**
   * 读取 Excel 文件字节数组，返回所有 Sheet 的数据
   *
   * @param buffer Excel 文件字节数组
   * @param isXls  是否为 .xls 格式文件
   * @return 包含所有 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当字节数组为空时抛出
   */
  public static List<SheetData> readExcel(byte[] buffer, boolean isXls) throws IOException {
    if (buffer == null || buffer.length == 0) {
      throw new IllegalArgumentException("缓冲区为空");
    }
    return readExcel(new ByteArrayInputStream(buffer), isXls);
  }

  /**
   * 读取 Excel 文件字节数组，返回指定索引的 Sheet 数据
   *
   * @param buffer     Excel 文件字节数组
   * @param isXls      是否为 .xls 格式文件
   * @param sheetIndex Sheet 索引，从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当字节数组为空时抛出
   */
  public static List<SheetData> readExcel(byte[] buffer, boolean isXls, int sheetIndex) throws IOException {
    if (buffer == null || buffer.length == 0) {
      throw new IllegalArgumentException("缓冲区为空");
    }
    return readExcel(new ByteArrayInputStream(buffer), isXls, sheetIndex);
  }

  /**
   * 读取 Excel 文件字节数组，返回指定名称的 Sheet 数据
   *
   * @param buffer    Excel 文件字节数组
   * @param isXls     是否为 .xls 格式文件
   * @param sheetName Sheet 名称
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当字节数组为空或 Sheet 名称为空时抛出
   */
  public static List<SheetData> readExcel(byte[] buffer, boolean isXls, String sheetName) throws IOException {
    if (buffer == null || buffer.length == 0) {
      throw new IllegalArgumentException("缓冲区为空");
    }
    return readExcel(new ByteArrayInputStream(buffer), isXls, sheetName);
  }

  /**
   * 读取 Excel 文件字节数组，返回指定索引列表的 Sheet 数据
   *
   * @param buffer       Excel 文件字节数组
   * @param isXls        是否为 .xls 格式文件
   * @param sheetIndexes Sheet 索引列表，索引从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当字节数组为空或索引列表为空时抛出
   */
  public static List<SheetData> readExcel(byte[] buffer, boolean isXls, List<Integer> sheetIndexes) throws IOException {
    if (buffer == null || buffer.length == 0) {
      throw new IllegalArgumentException("缓冲区为空");
    }
    return readExcel(new ByteArrayInputStream(buffer), isXls, sheetIndexes);
  }

  /**
   * 读取 Excel 文件字节数组，返回指定名称列表的 Sheet 数据
   *
   * @param buffer     Excel 文件字节数组
   * @param isXls      是否为 .xls 格式文件
   * @param sheetNames Sheet 名称列表
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当字节数组为空或名称列表为空时抛出
   */
  public static List<SheetData> readExcelByNames(byte[] buffer, boolean isXls, List<String> sheetNames) throws IOException {
    if (buffer == null || buffer.length == 0) {
      throw new IllegalArgumentException("缓冲区为空");
    }
    return readExcelByNames(new ByteArrayInputStream(buffer), isXls, sheetNames);
  }

  /**
   * 从输入流读取 Excel 文件，返回所有 Sheet 的数据
   *
   * @param in    Excel 文件输入流
   * @param isXls 是否为 .xls 格式文件
   * @return 包含所有 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当输入流为空时抛出
   */
  public static List<SheetData> readExcel(InputStream in, boolean isXls) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("输入流不能为空");
    }

    Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector = workbook -> {
      var sheets = StreamSupport
          .stream(Spliterators.spliterator(workbook.sheetIterator(), workbook.getNumberOfSheets(), Spliterator.ORDERED), false)
          .map(Either::<SheetData, Sheet>right)
          .collect(Collectors.toList());
      return sheets;
    };
    return doReadExcel(in, isXls, sheetSelector);
  }

  /**
   * 从输入流读取 Excel 文件，返回指定索引的 Sheet 数据
   *
   * @param in         Excel 文件输入流
   * @param isXls      是否为 .xls 格式文件
   * @param sheetIndex Sheet 索引，从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当输入流为空时抛出
   */
  public static List<SheetData> readExcel(InputStream in, boolean isXls, int sheetIndex) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("输入流不能为空");
    }

    Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector = workbook -> {
      if (sheetIndex < 0 || sheetIndex >= workbook.getNumberOfSheets()) {
        var sheetData = new SheetData("SHEET_INDEX_OUT_OF_RANGE_" + sheetIndex, "Sheet索引超出范围: " + sheetIndex);
        return Collections.singletonList(Either.<SheetData, Sheet>left(sheetData));
      }
      return Collections.singletonList(Either.<SheetData, Sheet>right(workbook.getSheetAt(sheetIndex)));
    };
    return doReadExcel(in, isXls, sheetSelector);
  }

  /**
   * 从输入流读取 Excel 文件，返回指定名称的 Sheet 数据
   *
   * @param in        Excel 文件输入流
   * @param isXls     是否为 .xls 格式文件
   * @param sheetName Sheet 名称
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当输入流为空或 Sheet 名称为空时抛出
   */
  public static List<SheetData> readExcel(InputStream in, boolean isXls, String sheetName) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("输入流不能为空");
    }
    if (sheetName == null || sheetName.trim().isEmpty()) {
      throw new IllegalArgumentException("Sheet名称不能为空");
    }

    Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector = workbook -> {
      var sheetIndex = workbook.getSheetIndex(sheetName);
      if (sheetIndex < 0) {
        var sheetData = new SheetData(sheetName, "Sheet不存在: " + sheetName);
        return Collections.singletonList(Either.<SheetData, Sheet>left(sheetData));
      }
      return Collections.singletonList(Either.<SheetData, Sheet>right(workbook.getSheetAt(sheetIndex)));
    };
    return doReadExcel(in, isXls, sheetSelector);
  }

  /**
   * 从输入流读取 Excel 文件，返回指定索引列表的 Sheet 数据
   *
   * @param in           Excel 文件输入流
   * @param isXls        是否为 .xls 格式文件
   * @param sheetIndexes Sheet 索引列表，索引从 0 开始
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当输入流为空或索引列表为空时抛出
   */
  public static List<SheetData> readExcel(InputStream in, boolean isXls, List<Integer> sheetIndexes) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("输入流不能为空");
    }
    if (sheetIndexes == null || sheetIndexes.isEmpty()) {
      throw new IllegalArgumentException("Sheet索引列表不能为空");
    }

    Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector = workbook -> {
      var sheets = sheetIndexes.stream()
          .map(index -> {
            if (index < 0 || index >= workbook.getNumberOfSheets()) {
              var sheetData = new SheetData("SHEET_INDEX_OUT_OF_RANGE_" + index, "Sheet索引超出范围: " + index);
              return Either.<SheetData, Sheet>left(sheetData);
            }
            return Either.<SheetData, Sheet>right(workbook.getSheetAt(index));
          })
          .collect(Collectors.toList());
      return sheets;
    };
    return doReadExcel(in, isXls, sheetSelector);
  }

  /**
   * 从输入流读取 Excel 文件，返回指定名称列表的 Sheet 数据
   *
   * @param in         Excel 文件输入流
   * @param isXls      是否为 .xls 格式文件
   * @param sheetNames Sheet 名称列表
   * @return 包含指定 Sheet 数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当输入流为空或名称列表为空时抛出
   */
  public static List<SheetData> readExcelByNames(InputStream in, boolean isXls, List<String> sheetNames) throws IOException {
    if (in == null) {
      throw new IllegalArgumentException("输入流不能为空");
    }
    if (sheetNames == null || sheetNames.isEmpty()) {
      throw new IllegalArgumentException("Sheet名称列表不能为空");
    }

    Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector = workbook -> {
      var sheets = sheetNames.stream()
          .map(name -> {
            if (name == null || name.trim().isEmpty()) {
              var sheetData = new SheetData("EMPTY_SHEET_NAME", "Sheet名称为空");
              return Either.<SheetData, Sheet>left(sheetData);
            }
            var sheetIndex = workbook.getSheetIndex(name);
            if (sheetIndex < 0) {
              var sheetData = new SheetData(name, "Sheet不存在: " + name);
              return Either.<SheetData, Sheet>left(sheetData);
            }
            return Either.<SheetData, Sheet>right(workbook.getSheetAt(sheetIndex));
          })
          .collect(Collectors.toList());
      return sheets;
    };
    return doReadExcel(in, isXls, sheetSelector);
  }

  /**
   * 执行 Excel 读取操作的内部方法
   *
   * @param in            Excel 文件输入流
   * @param isXls         是否为 .xls 格式文件
   * @param sheetSelector Sheet 选择器函数
   * @return 包含 Sheet 数据的列表
   * @throws IOException 当文件读取失败时抛出
   */
  private static List<SheetData> doReadExcel(InputStream in, boolean isXls, Function<Workbook, List<Either<SheetData, Sheet>>> sheetSelector) throws IOException {
    try (Workbook workbook = isXls ? new HSSFWorkbook(in) : new XSSFWorkbook(in)) {
      FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
      List<Either<SheetData, Sheet>> sheets = sheetSelector.apply(workbook);

      List<SheetData> sheetDataList = sheets.stream()
          .map(either -> either.fold(data -> data, sheet -> readSheet(sheet, evaluator)))
          .collect(Collectors.toList());

      return sheetDataList;
    } catch (Exception e) {
      throw new IOException("读取Excel文件失败: " + e.getMessage(), e);
    }
  }

  /**
   * 读取单个 Sheet 的数据
   *
   * @param sheet     Sheet 对象
   * @param evaluator 公式计算器
   * @return Sheet 数据对象
   */
  private static SheetData readSheet(Sheet sheet, FormulaEvaluator evaluator) {
    try {
      List<List<Object>> rows = new ArrayList<>();
      int lastRowNum = sheet.getLastRowNum();

      for (int r = 0; r <= lastRowNum; r++) {
        Row row = sheet.getRow(r);
        if (row == null) {
          rows.add(Collections.emptyList());
          continue;
        }
        int lastCellNum = Math.max(0, row.getLastCellNum());
        List<Object> one = new ArrayList<>(lastCellNum);
        for (int c = 0; c < lastCellNum; c++) {
          Cell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
          one.add(readCellAsObject(cell, evaluator));
        }
        rows.add(trimTrailingNulls(one));
      }

      rows = trimTrailingEmptyRows(rows);

      return new SheetData(sheet.getSheetName(), rows);
    } catch (Exception e) {
      return new SheetData(sheet.getSheetName(), "读取Sheet失败: " + e.getMessage());
    }
  }

  /**
   * 读取指定索引的 Sheet 数据
   *
   * @param filePath   Excel 文件路径
   * @param sheetIndex Sheet 索引，从 0 开始
   * @return Sheet 数据对象
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当 Sheet 索引无效时抛出
   */
  public static SheetData readSheet(String filePath, int sheetIndex) throws IOException {
    List<SheetData> sheets = readExcel(filePath, sheetIndex);
    if (sheets.isEmpty()) {
      throw new IllegalArgumentException("无法读取Sheet，索引: " + sheetIndex);
    }
    return sheets.get(0);
  }

  /**
   * 读取指定名称的 Sheet 数据
   *
   * @param filePath  Excel 文件路径
   * @param sheetName Sheet 名称
   * @return Sheet 数据对象
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当 Sheet 名称无效时抛出
   */
  public static SheetData readSheet(String filePath, String sheetName) throws IOException {
    List<SheetData> sheets = readExcel(filePath, sheetName);
    if (sheets.isEmpty()) {
      throw new IllegalArgumentException("无法读取Sheet，名称: " + sheetName);
    }
    return sheets.get(0);
  }

  /**
   * 读取指定 Sheet 和行的数据
   *
   * @param filePath   Excel 文件路径
   * @param sheetIndex Sheet 索引，从 0 开始
   * @param rowIndex   行索引，从 0 开始
   * @return 包含指定行数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当索引无效时抛出
   */
  public static List<Object> readRow(String filePath, int sheetIndex, int rowIndex) throws IOException {
    List<SheetData> sheets = readExcel(filePath, sheetIndex);
    if (sheets.isEmpty()) {
      throw new IllegalArgumentException("无法读取Sheet，索引: " + sheetIndex);
    }
    SheetData sheet = sheets.get(0);
    if (rowIndex < 0 || rowIndex >= sheet.getRows().size()) {
      throw new IllegalArgumentException("无效的 rowIndex: " + rowIndex + "，有效范围: 0-" + (sheet.getRows().size() - 1));
    }
    return sheet.getRows().get(rowIndex);
  }

  /**
   * 从输入流读取指定 Sheet 和行的数据
   *
   * @param in         Excel 文件输入流
   * @param isXls      是否为 .xls 格式文件
   * @param sheetIndex Sheet 索引，从 0 开始
   * @param rowIndex   行索引，从 0 开始
   * @return 包含指定行数据的列表
   * @throws IOException              当文件读取失败时抛出
   * @throws IllegalArgumentException 当索引无效或输入流为空时抛出
   */
  public static List<Object> readRow(InputStream in, boolean isXls, int sheetIndex, int rowIndex) throws IOException {
    List<SheetData> sheets = readExcel(in, isXls, sheetIndex);
    if (sheets.isEmpty()) {
      throw new IllegalArgumentException("无法读取Sheet，索引: " + sheetIndex);
    }
    SheetData sheet = sheets.get(0);
    if (!sheet.isSuccess()) {
      throw new IllegalArgumentException("Sheet读取失败: " + sheet.getErrorMessage());
    }
    if (rowIndex < 0 || rowIndex >= sheet.getRows().size()) {
      throw new IllegalArgumentException("无效的 rowIndex: " + rowIndex + "，有效范围: 0-" + (sheet.getRows().size() - 1));
    }
    return sheet.getRows().get(rowIndex);
  }

  /**
   * 读取单元格数据为 Object 类型，保持原数据类型
   *
   * @param cell      单元格对象
   * @param evaluator 公式计算器
   * @return 单元格值，可能为 String/Double/Boolean/Date/null
   */
  private static Object readCellAsObject(Cell cell, FormulaEvaluator evaluator) {
    if (cell == null) {
      return null;
    }

    try {
      CellType type = cell.getCellType();
      if (type == CellType.FORMULA) {
        type = evaluator.evaluateFormulaCell(cell);
      }

      switch (type) {
        case STRING:
          String s = cell.getStringCellValue();
          return (s == null || s.isEmpty()) ? null : s;
        case NUMERIC:
          if (DateUtil.isCellDateFormatted(cell)) {
            // java.util.Date
            return cell.getDateCellValue();
          } else {
            return cell.getNumericCellValue();
          }
        case BOOLEAN:
          return cell.getBooleanCellValue();
        case BLANK:
        case _NONE:
        case ERROR:
        default:
          return null;
      }
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 去除行末尾的 null 值
   *
   * @param row 行数据列表
   * @return 处理后的行数据列表
   */
  private static List<Object> trimTrailingNulls(List<Object> row) {
    if (row == null || row.isEmpty()) {
      return row != null ? row : Collections.emptyList();
    }

    int lastNonNull = row.size() - 1;
    while (lastNonNull >= 0 && row.get(lastNonNull) == null) {
      lastNonNull--;
    }

    if (lastNonNull == row.size() - 1) {
      return row;
    } else if (lastNonNull < 0) {
      return Collections.emptyList();
    } else {
      return new ArrayList<>(row.subList(0, lastNonNull + 1));
    }
  }

  /**
   * 去除末尾的空行
   *
   * @param rows 行数据列表
   * @return 处理后的行数据列表
   */
  private static List<List<Object>> trimTrailingEmptyRows(List<List<Object>> rows) {
    if (rows == null || rows.isEmpty()) {
      return rows != null ? rows : Collections.emptyList();
    }

    int lastNonEmpty = rows.size() - 1;
    while (lastNonEmpty >= 0) {
      List<Object> r = rows.get(lastNonEmpty);
      if (r == null || r.isEmpty() || r.stream().allMatch(Objects::isNull)) {
        lastNonEmpty--;
      } else {
        break;
      }
    }

    if (lastNonEmpty == rows.size() - 1) {
      return rows;
    } else if (lastNonEmpty < 0) {
      return Collections.emptyList();
    } else {
      return new ArrayList<>(rows.subList(0, lastNonEmpty + 1));
    }
  }

  /**
   * 验证 Excel 文件路径的有效性
   *
   * @param filePath Excel 文件路径
   * @return 验证结果对象
   */
  public static ValidationResult validateExcelFile(String filePath) {
    if (filePath == null || filePath.trim().isEmpty()) {
      return new ValidationResult(false, Arrays.asList("文件路径不能为空"));
    }
    return validateExcelFile(new File(filePath.trim()));
  }

  /**
   * 验证 Excel 文件的有效性
   *
   * @param file Excel 文件对象
   * @return 验证结果对象
   */
  public static ValidationResult validateExcelFile(File file) {
    List<String> errors = new ArrayList<>();

    if (file == null) {
      errors.add("文件对象不能为空");
    } else {
      if (!file.exists()) {
        errors.add("文件不存在: " + file.getAbsolutePath());
      } else if (!file.isFile()) {
        errors.add("不是一个有效的文件: " + file.getAbsolutePath());
      } else if (!file.canRead()) {
        errors.add("文件无法读取: " + file.getAbsolutePath());
      } else {
        String fileName = file.getName().toLowerCase();
        if (!fileName.endsWith(".xls") && !fileName.endsWith(".xlsx")) {
          errors.add("不支持的文件格式，仅支持 .xls 和 .xlsx 文件");
        }
      }
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  // ----------------- 数据模型 -----------------

  /**
   * 文件验证结果类
   * 封装文件验证的结果信息，包含验证状态和错误信息
   */
  public static class ValidationResult {
    private final boolean valid;
    private final List<String> errors;

    /**
     * 构造文件验证结果
     *
     * @param valid  是否验证通过
     * @param errors 错误信息列表
     */
    public ValidationResult(boolean valid, List<String> errors) {
      this.valid = valid;
      this.errors = errors != null ? new ArrayList<>(errors) : Collections.emptyList();
    }

    /**
     * 获取验证是否通过
     *
     * @return true 表示验证通过，false 表示验证失败
     */
    public boolean isValid() {
      return valid;
    }

    /**
     * 获取错误信息列表
     *
     * @return 不可变的错误信息列表
     */
    public List<String> getErrors() {
      return Collections.unmodifiableList(errors);
    }

    @Override
    public String toString() {
      return valid ? "验证通过" : "验证失败: " + String.join(", ", errors);
    }
  }

  /**
   * Sheet 数据封装类
   * 封装单个 Sheet 的所有数据和元信息，提供多种数据访问和转换方法
   */
  public static class SheetData {
    private final String sheetName;
    private final List<List<Object>> rows;
    private final boolean success;
    private final String errorMessage;

    /**
     * 构造成功的 Sheet 数据对象
     *
     * @param sheetName Sheet 名称
     * @param rows      行数据列表，每行为一个 Object 列表
     */
    public SheetData(String sheetName, List<List<Object>> rows) {
      this.sheetName = sheetName;
      this.rows = rows != null ? rows : Collections.emptyList();
      this.success = true;
      this.errorMessage = null;
    }

    /**
     * 构造失败的 Sheet 数据对象
     *
     * @param sheetName    Sheet 名称
     * @param errorMessage 错误信息
     */
    public SheetData(String sheetName, String errorMessage) {
      this.sheetName = sheetName;
      this.rows = Collections.emptyList();
      this.success = false;
      this.errorMessage = errorMessage;
    }

    /**
     * 获取 Sheet 读取是否成功
     *
     * @return true 表示读取成功，false 表示读取失败
     */
    public boolean isSuccess() {
      return success;
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
     * 获取所有行数据
     *
     * @return 不可变的行数据列表
     */
    public List<List<Object>> getRows() {
      return Collections.unmodifiableList(rows);
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息，成功时返回 null
     */
    public String getErrorMessage() {
      return errorMessage;
    }

    /**
     * 将数据转换为 Map 列表，使用第一行作为表头，从第二行开始作为数据
     * 等同于 toMappedRows(0, 0)
     *
     * @return Map 列表，每个 Map 代表一行数据，key 为列名，value 为单元格值
     */
    public List<Map<String, Object>> toMappedRows() {
      return toMappedRows(0, 0);
    }

    /**
     * 将数据转换为 Map 列表，使用自定义表头，从第一行开始作为数据
     * 等同于 toMappedRows(0, 0, headers)
     *
     * @param headers 自定义表头列表
     * @return Map 列表，每个 Map 代表一行数据，key 为列名，value 为单元格值
     */
    public List<Map<String, Object>> toMappedRows(List<String> headers) {
      return toMappedRows(0, 0, headers);
    }

    /**
     * 将数据转换为 Map 列表，指定数据起始位置，使用指定行作为表头
     *
     * @param startRow 数据起始行索引，该行将作为表头
     * @param startCol 数据起始列索引
     * @return Map 列表，每个 Map 代表一行数据，key 为列名，value 为单元格值
     */
    public List<Map<String, Object>> toMappedRows(int startRow, int startCol) {
      checkPos(startRow, startCol);
      if (startRow >= rows.size()) {
        return Collections.emptyList();
      }

      List<Object> headerRow = safeRow(startRow);
      List<String> headers = projectHeaders(headerRow, startCol);
      return (List<Map<String, Object>>) mapBody(startRow + 1, startCol, headers);
    }

    /**
     * 将数据转换为 Map 列表，指定数据起始位置和自定义表头
     *
     * @param startRow 数据起始行索引
     * @param startCol 数据起始列索引
     * @param headers  自定义表头列表
     * @return Map 列表，每个 Map 代表一行数据，key 为列名，value 为单元格值
     */
    public List<Map<String, Object>> toMappedRows(int startRow, int startCol, List<String> headers) {
      checkPos(startRow, startCol);
      List<String> hs = normalizeHeaders(headers);
      return (List<Map<String, Object>>) mapBody(startRow, startCol, hs);
    }

    /**
     * 检查行列索引的有效性
     *
     * @param startRow 起始行索引
     * @param startCol 起始列索引
     * @throws IllegalArgumentException 当索引小于 0 时抛出
     */
    private void checkPos(int startRow, int startCol) {
      if (startRow < 0 || startCol < 0) {
        throw new IllegalArgumentException("startRow/startCol 索引从 0 开始。");
      }
    }

    /**
     * 安全获取指定行数据
     *
     * @param idx 行索引
     * @return 行数据列表，索引无效时返回空列表
     */
    private List<Object> safeRow(int idx) {
      if (idx < 0 || idx >= rows.size()) {
        return Collections.emptyList();
      }
      List<Object> r = rows.get(idx);
      return r != null ? r : Collections.emptyList();
    }

    /**
     * 从指定行提取表头信息
     *
     * @param headerRow 表头行数据
     * @param startCol  起始列索引
     * @return 表头列表
     */
    private List<String> projectHeaders(List<Object> headerRow, int startCol) {
      if (headerRow == null || headerRow.isEmpty() || headerRow.size() <= startCol) {
        return Collections.emptyList();
      }

      List<String> headers = new ArrayList<>();
      for (int i = startCol; i < headerRow.size(); i++) {
        Object v = headerRow.get(i);
        String name = (v == null) ? "" : String.valueOf(v).trim();
        headers.add(name.isEmpty() ? ("COL" + (i - startCol + 1)) : name);
      }
      // 去掉尾部空表头
      int end = headers.size() - 1;
      while (end >= 0 && (headers.get(end) == null || headers.get(end).isEmpty() || headers.get(end).startsWith("COL"))) {
        end--;
      }
      return end >= 0 ? new ArrayList<>(headers.subList(0, end + 1)) : Collections.emptyList();
    }

    /**
     * 标准化表头列表
     *
     * @param headers 原始表头列表
     * @return 标准化后的表头列表
     */
    private List<String> normalizeHeaders(List<String> headers) {
      if (headers == null) {
        return Collections.emptyList();
      }
      List<String> hs = headers.stream()
          .map(h -> h == null ? "" : h.trim())
          .collect(Collectors.toCollection(ArrayList::new));
      // 空名填充列名
      for (int i = 0; i < hs.size(); i++) {
        if (hs.get(i).isEmpty()) {
          hs.set(i, "COL" + (i + 1));
        }
      }
      // 去尾空（理论上不会有，因为已替换）
      int end = hs.size() - 1;
      while (end >= 0 && (hs.get(end) == null || hs.get(end).isEmpty())) {
        end--;
      }
      return end >= 0 ? new ArrayList<>(hs.subList(0, end + 1)) : Collections.emptyList();
    }

    /**
     * 将数据行映射为 Map 列表
     *
     * @param dataStartRow 数据起始行索引
     * @param startCol     起始列索引
     * @param headers      表头列表
     * @return Map 列表
     */
    private List<? extends Map<String, Object>> mapBody(int dataStartRow, int startCol, List<String> headers) {
      if (headers == null || headers.isEmpty()) {
        return Collections.emptyList();
      }

      List<LinkedHashMap<String, Object>> out = new ArrayList<>();
      for (int r = dataStartRow; r < rows.size(); r++) {
        List<Object> row = safeRow(r);
        // 取该行对应列范围内的值
        boolean allNull = true;
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
          String key = headers.get(i);
          Object val = (row.size() > startCol + i) ? row.get(startCol + i) : null;
          if (val != null) {
            allNull = false;
          }
          map.put(key, val);
        }
        // 跳过纯空行
        if (!allNull) {
          out.add(map);
        }
      }
      return out;
    }

    /**
     * 获取行数
     *
     * @return Sheet 的总行数
     */
    public int getRowCount() {
      return rows.size();
    }

    /**
     * 获取列数，基于第一行的列数
     *
     * @return Sheet 的列数，空 Sheet 返回 0
     */
    public int getColumnCount() {
      return rows.isEmpty() ? 0 : rows.get(0).size();
    }

    /**
     * 检查 Sheet 是否为空
     *
     * @return true 表示 Sheet 为空或所有单元格都为 null
     */
    public boolean isEmpty() {
      return rows.isEmpty() || rows.stream().allMatch(row ->
          row == null || row.isEmpty() || row.stream().allMatch(Objects::isNull));
    }

    @Override
    public String toString() {
      if (!success) {
        return "SheetData{name='" + sheetName + "', success=false, error='" + errorMessage + "'}";
      }
      return "SheetData{name='" + sheetName + "', rows=" + rows.size() + ", columns=" + getColumnCount() + "}";
    }
  }

  /**
   * 类型安全的单元格值封装类
   * 提供类型安全的方式访问单元格数据
   */
  public static class CellValue {
    private final Object value;
    private final CellType type;

    /**
     * 构造单元格值对象
     *
     * @param value 单元格原始值
     * @param type  单元格类型
     */
    public CellValue(Object value, CellType type) {
      this.value = value;
      this.type = type;
    }

    /**
     * 尝试将值转换为字符串
     *
     * @return 包含字符串值的 Optional，转换失败时为空
     */
    public Optional<String> asString() {
      return value instanceof String ? Optional.of((String) value) : Optional.empty();
    }

    /**
     * 尝试将值转换为双精度浮点数
     *
     * @return 包含数值的 Optional，转换失败时为空
     */
    public Optional<Double> asDouble() {
      return value instanceof Double ? Optional.of((Double) value) : Optional.empty();
    }

    /**
     * 尝试将值转换为布尔值
     *
     * @return 包含布尔值的 Optional，转换失败时为空
     */
    public Optional<Boolean> asBoolean() {
      return value instanceof Boolean ? Optional.of((Boolean) value) : Optional.empty();
    }

    /**
     * 尝试将值转换为日期
     *
     * @return 包含日期值的 Optional，转换失败时为空
     */
    public Optional<Date> asDate() {
      return value instanceof Date ? Optional.of((Date) value) : Optional.empty();
    }

    /**
     * 检查值是否为 null
     *
     * @return true 表示值为 null
     */
    public boolean isNull() {
      return value == null;
    }

    /**
     * 获取原始值
     *
     * @return 单元格的原始值对象
     */
    public Object getRawValue() {
      return value;
    }

    /**
     * 获取单元格类型
     *
     * @return 单元格的数据类型
     */
    public CellType getType() {
      return type;
    }

    @Override
    public String toString() {
      return "CellValue{value=" + value + ", type=" + type + "}";
    }
  }
}