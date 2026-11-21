package com.ouroboros.logicflow.nodes;

import java.io.File;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.exception.PlatformException;
import com.ouroboros.logicflow.exception.NodeDesignException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Maps;
import com.ouroboros.util.excel.ExcelReader;

public class ReadExcelNode extends AbstractSimpleNode<Object> {

  // ========== 数据源配置 ==========
  private final String excelSourcePath;
  private final String xlsFlagPath;

  // ========== Sheet选择配置 ==========
  //All | Multiple | Single
  private final String sheetSelectionMode;
  //Name | Index
  private final String sheetIdentifierType;

  // Multiple模式配置
  //多个sheet名称
  private final List<String> sheetNames;
  //多个sheet索引
  private final List<Integer> sheetIndexes;

  // Single模式配置
  //单个sheet名称（优先级高）
  private final String sheetName;
  //单个sheet索引（fallback）
  private final Integer sheetIndex;

  // ========== 数据格式配置 ==========
  //ListOfMap | ListOfList | SheetData
  private final String sheetDataFormat;
  //List | Map
  private final String multiSheetResultFormat;

  // ========== 数据范围配置 ==========
  //数据开始行，0-based
  private final Integer startRow;
  //数据开始列，0-based
  private final Integer startColumn;
  //最大读取行数，null表示不限制
  private final Integer maxRows;
  //最大读取列数，null表示不限制
  private final Integer maxColumns;

  // ========== 表头配置 ==========
  //是否使用自定义表头
  private final Boolean useCustomHeaders;
  //自定义表头列表
  private final List<String> customHeaders;

  // ========== 数据处理配置 ==========
  //是否跳过空行
  private final Boolean skipEmptyRows;
  //是否跳过空列
  private final Boolean skipEmptyColumns;
  //是否去除单元格值的前后空格
  private final Boolean trimCellValues;
  //null值替换字符串
  private final String nullValueReplacement;

  // ========== 错误处理配置 ==========
  //Throw | Skip | Default
  private final String errorHandlingStrategy;
  //错误时的默认值
  private final Object defaultValueOnError;
  //Sheet读取失败时是否继续处理其他Sheet
  private final Boolean continueOnSheetError;

  protected ReadExcelNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);

    // 数据源配置
    excelSourcePath = meta.getExtraProp(String.class, "excelSourcePath").orElse("data.file");
    xlsFlagPath = meta.getExtraProp(String.class, "xlsFlagPath").orElse("data.isXls");

    // Sheet选择配置
    sheetSelectionMode = meta.getExtraProp(String.class, "sheetSelectionMode").orElse("Single");
    sheetIdentifierType = meta.getExtraProp(String.class, "sheetIdentifierType").orElse("Index");

    // Multiple模式配置
    sheetNames = meta.getExtraProp(List.class, "sheetNames")
        .map(list -> (List<?>) list)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(Converter::toString)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());

    sheetIndexes = meta.getExtraProp(List.class, "sheetIndexes")
        .map(list -> (List<?>) list)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(Converter::toInteger)
        .filter(index -> index != null && index >= 0)
        .collect(Collectors.toList());

    // Single模式配置
    sheetName = meta.getExtraProp(String.class, "sheetName").orElse(null);
    sheetIndex = meta.getExtraProp(Integer.class, "sheetIndex").orElse(0);

    // 数据格式配置
    sheetDataFormat = meta.getExtraProp(String.class, "sheetDataFormat").orElse("ListOfMap");
    multiSheetResultFormat = meta.getExtraProp(String.class, "multiSheetResultFormat").orElse("List");

    // 数据范围配置
    startRow = meta.getExtraProp(Integer.class, "startRow").orElse(0);
    startColumn = meta.getExtraProp(Integer.class, "startColumn").orElse(0);
    maxRows = meta.getExtraProp(Integer.class, "maxRows").orElse(null);
    maxColumns = meta.getExtraProp(Integer.class, "maxColumns").orElse(null);

    // 表头配置
    useCustomHeaders = meta.getExtraProp(Boolean.class, "useCustomHeaders").orElse(false);
    customHeaders = meta.getExtraProp(List.class, "customHeaders")
        .map(list -> (List<?>) list)
        .orElseGet(Collections::emptyList)
        .stream()
        .map(Converter::toString)
        .filter(StringUtils::isNotEmpty)
        .collect(Collectors.toList());

    // 数据处理配置
    skipEmptyRows = meta.getExtraProp(Boolean.class, "skipEmptyRows").orElse(true);
    skipEmptyColumns = meta.getExtraProp(Boolean.class, "skipEmptyColumns").orElse(false);
    trimCellValues = meta.getExtraProp(Boolean.class, "trimCellValues").orElse(true);
    nullValueReplacement = meta.getExtraProp(String.class, "nullValueReplacement").orElse(null);

    // 错误处理配置
    errorHandlingStrategy = meta.getExtraProp(String.class, "errorHandlingStrategy").orElse("Throw");
    defaultValueOnError = meta.getExtraProp(Object.class, "defaultValueOnError").orElse(null);
    continueOnSheetError = meta.getExtraProp(Boolean.class, "continueOnSheetError").orElse(false);

    // 配置验证
    validateConfiguration(meta);
  }

  /**
   * 验证配置的一致性和有效性
   *
   * @param meta 流程节点元数据
   * @throws NodeDesignException 当配置无效时抛出设计期异常
   */
  private void validateConfiguration(FlowNodeMeta meta) {
    List<String> errors = new ArrayList<>();

    // 验证Sheet选择配置
    if ("Multiple".equalsIgnoreCase(sheetSelectionMode)) {
      if ("Name".equalsIgnoreCase(sheetIdentifierType) && sheetNames.isEmpty()) {
        errors.add("Multiple模式下使用Name标识符时，sheetNames不能为空");
      }
      if ("Index".equalsIgnoreCase(sheetIdentifierType) && sheetIndexes.isEmpty()) {
        errors.add("Multiple模式下使用Index标识符时，sheetIndexes不能为空");
      }
    }

    // 验证Single模式配置
    if ("Single".equalsIgnoreCase(sheetSelectionMode)) {
      if (StringUtils.isEmpty(sheetName) && sheetIndex < 0) {
        errors.add("Single模式下必须配置有效的sheetName或sheetIndex");
      }
    }

    // 验证数据范围配置
    if (startRow < 0) {
      errors.add("startRow必须大于等于0，当前值: " + startRow);
    }
    if (startColumn < 0) {
      errors.add("startColumn必须大于等于0，当前值: " + startColumn);
    }
    if (maxRows != null && maxRows <= 0) {
      errors.add("maxRows必须大于0，当前值: " + maxRows);
    }
    if (maxColumns != null && maxColumns <= 0) {
      errors.add("maxColumns必须大于0，当前值: " + maxColumns);
    }

    // 验证表头配置
    if (useCustomHeaders && customHeaders.isEmpty()) {
      errors.add("启用自定义表头时，customHeaders不能为空");
    }

    // 验证数据格式配置
    List<String> validDataFormats = Arrays.asList("ListOfMap", "ListOfList", "SheetData");
    if (!validDataFormats.contains(sheetDataFormat)) {
      errors.add("sheetDataFormat必须是" + String.join("、", validDataFormats) + "之一，当前值: " + sheetDataFormat);
    }

    // 验证多Sheet结果格式配置
    List<String> validResultFormats = Arrays.asList("List", "Map");
    if (!validResultFormats.contains(multiSheetResultFormat)) {
      errors.add("multiSheetResultFormat必须是" + String.join("、", validResultFormats) + "之一，当前值: " + multiSheetResultFormat);
    }

    // 验证Sheet选择模式配置
    List<String> validSelectionModes = Arrays.asList("All", "Multiple", "Single");
    if (!validSelectionModes.contains(sheetSelectionMode)) {
      errors.add("sheetSelectionMode必须是" + String.join("、", validSelectionModes) + "之一，当前值: " + sheetSelectionMode);
    }

    // 验证Sheet标识符类型配置
    List<String> validIdentifierTypes = Arrays.asList("Name", "Index");
    if (!validIdentifierTypes.contains(sheetIdentifierType)) {
      errors.add("sheetIdentifierType必须是" + String.join("、", validIdentifierTypes) + "之一，当前值: " + sheetIdentifierType);
    }

    // 验证错误处理配置
    List<String> validErrorStrategies = Arrays.asList("Throw", "Skip", "Default");
    if (!validErrorStrategies.contains(errorHandlingStrategy)) {
      errors.add("errorHandlingStrategy必须是" + String.join("、", validErrorStrategies) + "之一，当前值: " + errorHandlingStrategy);
    }

    if ("Default".equals(errorHandlingStrategy) && defaultValueOnError == null) {
      errors.add("使用Default错误处理策略时，必须设置defaultValueOnError");
    }

    // 验证路径配置
    if (StringUtils.isEmpty(excelSourcePath)) {
      errors.add("excelSourcePath不能为空");
    }
    if (StringUtils.isEmpty(xlsFlagPath)) {
      errors.add("xlsFlagPath不能为空");
    }

    // 验证配置组合的合理性
    if ("ListOfMap".equals(sheetDataFormat) && useCustomHeaders && customHeaders.size() > 100) {
      errors.add("自定义表头数量过多，可能影响性能，建议不超过100列");
    }

    if (maxRows != null && maxColumns != null && maxRows * maxColumns > 1000000) {
      errors.add("数据量过大(行×列=" + (maxRows * maxColumns) + ")，可能导致内存溢出，建议调整maxRows和maxColumns");
    }

    if (!errors.isEmpty()) {
      String errorMessage = "ReadExcelNode配置验证失败: " + String.join("; ", errors);
      throw new NodeDesignException(errorMessage, meta);
    }
  }

  @Override
  protected Object doExecute(Object input) {
    if (!(input instanceof Map<?, ?>)) {
      throw new PlatformException("输入必须是 Map 数据类型");
    }

    try {
      var excelData = readExcel((Map<String, Object>) input)
          .stream()
          .map(this::processSheetData)
          //过滤掉处理失败的Sheet（当continueOnSheetError=true时）
          .filter(Objects::nonNull)
          .collect(Collectors.toList());

      return formatResult(excelData);

    } catch (Exception e) {
      return handleError(e);
    }
  }

  /**
   * 处理单个Sheet数据
   */
  private Tuple2<String, Object> processSheetData(ExcelReader.SheetData sheetData) {
    try {
      // 检查Sheet读取状态
      if (!sheetData.isSuccess()) {
        if (continueOnSheetError) {
          //返回null，后续会被过滤掉
          return null;
        } else {
          throw new PlatformException("读取Sheet失败: " + sheetData.getErrorMessage());
        }
      }

      Object processedData = processDataByFormat(sheetData);
      return Tuple.of(sheetData.getSheetName(), processedData);

    } catch (Exception e) {
      if (continueOnSheetError) {
        //忽略错误，继续处理其他Sheet
        return null;
      } else {
        throw new PlatformException("处理Sheet数据失败: " + e.getMessage(), e);
      }
    }
  }

  /**
   * 根据配置格式处理数据
   */
  private Object processDataByFormat(ExcelReader.SheetData sheetData) {
    switch (sheetDataFormat) {
      case "ListOfMap":
        return processListOfMap(sheetData);

      case "ListOfList":
        return processListOfList(sheetData);

      case "SheetData":
        return sheetData;

      default:
        throw new PlatformException("不支持的数据格式: " + sheetDataFormat);
    }
  }

  /**
   * 处理ListOfMap格式的数据
   */
  private List<Map<String, Object>> processListOfMap(ExcelReader.SheetData sheetData) {
    List<Map<String, Object>> result;

    if (useCustomHeaders && !customHeaders.isEmpty()) {
      result = sheetData.toMappedRows(startRow, startColumn, customHeaders);
    } else {
      result = sheetData.toMappedRows(startRow, startColumn);
    }

    // 应用行数限制
    if (maxRows != null && result.size() > maxRows) {
      result = result.subList(0, maxRows);
    }

    // 应用数据处理配置
    return result.stream()
        .filter(row -> !skipEmptyRows || !isEmptyMapRow(row))
        .map(this::processMapRow)
        .collect(Collectors.toList());
  }

  /**
   * 处理Map行数据
   */
  private Map<String, Object> processMapRow(Map<String, Object> row) {
    Map<String, Object> processedRow = new LinkedHashMap<>();

    for (Map.Entry<String, Object> entry : row.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      // 跳过空列（如果配置了skipEmptyColumns）
      if (skipEmptyColumns && isEmptyValue(value)) {
        continue;
      }

      // 处理单元格值
      value = processCellValue(value);

      processedRow.put(key, value);
    }

    return processedRow;
  }

  /**
   * 处理ListOfList格式的数据
   */
  private List<List<Object>> processListOfList(ExcelReader.SheetData sheetData) {
    List<List<Object>> rows = new ArrayList<>(sheetData.getRows());

    // 应用数据范围限制
    if (startRow > 0 && startRow < rows.size()) {
      rows = rows.subList(startRow, rows.size());
    }

    if (maxRows != null && rows.size() > maxRows) {
      rows = rows.subList(0, maxRows);
    }

    // 处理列范围和数据清理
    return rows.stream()
        .map(this::processListRow)
        .filter(row -> !skipEmptyRows || !isEmptyListRow(row))
        .collect(Collectors.toList());
  }

  /**
   * 处理List行数据
   */
  private List<Object> processListRow(List<Object> row) {
    List<Object> processedRow = new ArrayList<>();

    int startCol = Math.max(0, startColumn);
    int endCol = maxColumns != null ?
        Math.min(row.size(), startCol + maxColumns) : row.size();

    for (int i = startCol; i < endCol; i++) {
      Object value = i < row.size() ? row.get(i) : null;

      // 跳过空列（如果配置了skipEmptyColumns）
      if (skipEmptyColumns && isEmptyValue(value)) {
        continue;
      }

      // 处理单元格值
      value = processCellValue(value);

      processedRow.add(value);
    }

    return processedRow;
  }

  /**
   * 处理单元格值
   */
  private Object processCellValue(Object value) {
    // 去除前后空格
    if (value != null && trimCellValues && value instanceof String) {
      value = ((String) value).trim();
      if (((String) value).isEmpty()) {
        value = null;
      }
    }

    // 替换null值
    if (value == null && nullValueReplacement != null) {
      value = nullValueReplacement;
    }

    return value;
  }

  /**
   * 检查Map行是否为空
   */
  private boolean isEmptyMapRow(Map<String, Object> row) {
    return row == null || row.isEmpty() ||
        row.values().stream().allMatch(this::isEmptyValue);
  }

  /**
   * 检查List行是否为空
   */
  private boolean isEmptyListRow(List<Object> row) {
    return row == null || row.isEmpty() ||
        row.stream().allMatch(this::isEmptyValue);
  }

  /**
   * 检查值是否为空
   */
  private boolean isEmptyValue(Object value) {
    return value == null ||
        (value instanceof String && ((String) value).trim().isEmpty());
  }

  /**
   * 格式化最终结果
   */
  private Object formatResult(List<Tuple2<String, Object>> excelData) {
    if (excelData.isEmpty()) {
      if ("Default".equals(errorHandlingStrategy)) {
        return defaultValueOnError;
      } else {
        throw new PlatformException("未找到有效的Sheet数据");
      }
    }

    // 根据Sheet选择模式格式化结果
    switch (sheetSelectionMode.toLowerCase()) {
      case "all":
      case "multiple":
        if ("Map".equals(multiSheetResultFormat)) {
          return excelData.stream()
              .collect(Collectors.toMap(Tuple2::_1, Tuple2::_2, (v1, v2) -> v2));
        } else {
          return excelData.stream()
              .map(Tuple2::_2)
              .collect(Collectors.toList());
        }

      case "single":
        return excelData.get(0)._2;

      default:
        throw new PlatformException("不支持的Sheet选择模式: " + sheetSelectionMode);
    }
  }

  /**
   * 处理执行错误
   */
  private Object handleError(Exception e) {
    switch (errorHandlingStrategy) {
      case "Skip":
        return Collections.emptyList();

      case "Default":
        return defaultValueOnError;

      case "Throw":
      default:
        if (e instanceof PlatformException) {
          throw (PlatformException) e;
        }
        throw new PlatformException("Excel读取失败: " + e.getMessage(), e);
    }
  }

  /**
   * 根据配置的读取模式优化读取，避免不必要的全量读取
   */
  private List<ExcelReader.SheetData> readExcel(Map<String, Object> inputMap) {
    var excelSource = Maps.Hierarchical.getValue(inputMap, excelSourcePath);

    if (!excelSource.isPresent()) {
      throw new PlatformException("读取Excel数据失败，在入参中未找到数据");
    }
    var excelData = excelSource.get();

    // 根据不同的数据源类型和读取模式进行优化读取
    try {
      if (excelData instanceof String excelFilePath) {
        return readExcelByPath(excelFilePath);
      }

      if (excelData instanceof File excelFile) {
        return readExcelByFile(excelFile);
      }

      if (excelData instanceof MultipartFile multipartFile) {
        return readExcelByMultipartFile(multipartFile);
      }

      if (excelData instanceof InputStream inputStream) {
        boolean isXls = Maps.Hierarchical.getValue(inputMap, xlsFlagPath)
            .map(Boolean.class::cast)
            .orElse(false);
        return readExcelByInputStream(inputStream, isXls);
      }

      if (excelData instanceof byte[] bytes) {
        boolean isXls = Maps.Hierarchical.getValue(inputMap, xlsFlagPath)
            .map(Boolean.class::cast)
            .orElse(false);
        return readExcelByBytes(bytes, isXls);
      }

      throw new PlatformException("无法识别的Excel数据类型: " + excelData.getClass().getSimpleName());

    } catch (Exception e) {
      if (e instanceof PlatformException pe) {
        throw pe;
      }
      throw new PlatformException("读取Excel数据失败: " + e.getMessage(), e);
    }
  }

  /**
   * 从文件路径读取 Excel
   */
  private List<ExcelReader.SheetData> readExcelByPath(String filePath) throws Exception {
    switch (sheetSelectionMode.toLowerCase()) {
      case "all":
        return ExcelReader.readExcel(filePath);

      case "single":
        if (StringUtils.isNotEmpty(sheetName)) {
          return ExcelReader.readExcel(filePath, sheetName);
        } else {
          return ExcelReader.readExcel(filePath, sheetIndex);
        }

      case "multiple":
        if ("Name".equalsIgnoreCase(sheetIdentifierType) && !sheetNames.isEmpty()) {
          return ExcelReader.readExcelByNames(filePath, sheetNames);
        } else if ("Index".equalsIgnoreCase(sheetIdentifierType) && !sheetIndexes.isEmpty()) {
          return ExcelReader.readExcel(filePath, sheetIndexes);
        } else {
          throw new PlatformException("Multiple模式下未配置有效的Sheet标识符");
        }

      default:
        throw new PlatformException("不支持的Sheet选择模式: " + sheetSelectionMode);
    }
  }

  /**
   * 从文件对象读取 Excel
   */
  private List<ExcelReader.SheetData> readExcelByFile(File file) throws Exception {
    switch (sheetSelectionMode.toLowerCase()) {
      case "all":
        return ExcelReader.readExcel(file);

      case "single":
        if (StringUtils.isNotEmpty(sheetName)) {
          return ExcelReader.readExcel(file, sheetName);
        } else {
          return ExcelReader.readExcel(file, sheetIndex);
        }

      case "multiple":
        if ("Name".equalsIgnoreCase(sheetIdentifierType) && !sheetNames.isEmpty()) {
          return ExcelReader.readExcelByNames(file, sheetNames);
        } else if ("Index".equalsIgnoreCase(sheetIdentifierType) && !sheetIndexes.isEmpty()) {
          return ExcelReader.readExcel(file, sheetIndexes);
        } else {
          throw new PlatformException("Multiple模式下未配置有效的Sheet标识符");
        }

      default:
        throw new PlatformException("不支持的Sheet选择模式: " + sheetSelectionMode);
    }
  }

  /**
   * 从 MultipartFile 读取 Excel
   */
  private List<ExcelReader.SheetData> readExcelByMultipartFile(MultipartFile multipartFile) throws Exception {
    boolean isXls = Optional.ofNullable(multipartFile.getOriginalFilename())
        .map(String::toLowerCase)
        .map(name -> name.endsWith(".xls"))
        .orElse(false);

    try (InputStream inputStream = multipartFile.getInputStream()) {
      return readExcelByInputStream(inputStream, isXls);
    }
  }

  /**
   * 从输入流读取 Excel
   */
  private List<ExcelReader.SheetData> readExcelByInputStream(InputStream inputStream, boolean isXls) throws Exception {
    switch (sheetSelectionMode.toLowerCase()) {
      case "all":
        return ExcelReader.readExcel(inputStream, isXls);

      case "single":
        if (StringUtils.isNotEmpty(sheetName)) {
          return ExcelReader.readExcel(inputStream, isXls, sheetName);
        } else {
          return ExcelReader.readExcel(inputStream, isXls, sheetIndex);
        }

      case "multiple":
        if ("Name".equalsIgnoreCase(sheetIdentifierType) && !sheetNames.isEmpty()) {
          return ExcelReader.readExcelByNames(inputStream, isXls, sheetNames);
        } else if ("Index".equalsIgnoreCase(sheetIdentifierType) && !sheetIndexes.isEmpty()) {
          return ExcelReader.readExcel(inputStream, isXls, sheetIndexes);
        } else {
          throw new PlatformException("Multiple模式下未配置有效的Sheet标识符");
        }

      default:
        throw new PlatformException("不支持的Sheet选择模式: " + sheetSelectionMode);
    }
  }

  /**
   * 从字节数组读取 Excel
   */
  private List<ExcelReader.SheetData> readExcelByBytes(byte[] bytes, boolean isXls) throws Exception {
    switch (sheetSelectionMode.toLowerCase()) {
      case "all":
        return ExcelReader.readExcel(bytes, isXls);

      case "single":
        if (StringUtils.isNotEmpty(sheetName)) {
          return ExcelReader.readExcel(bytes, isXls, sheetName);
        } else {
          return ExcelReader.readExcel(bytes, isXls, sheetIndex);
        }

      case "multiple":
        if ("Name".equalsIgnoreCase(sheetIdentifierType) && !sheetNames.isEmpty()) {
          return ExcelReader.readExcelByNames(bytes, isXls, sheetNames);
        } else if ("Index".equalsIgnoreCase(sheetIdentifierType) && !sheetIndexes.isEmpty()) {
          return ExcelReader.readExcel(bytes, isXls, sheetIndexes);
        } else {
          throw new PlatformException("Multiple模式下未配置有效的Sheet标识符");
        }

      default:
        throw new PlatformException("不支持的Sheet选择模式: " + sheetSelectionMode);
    }
  }

  public static class Builder implements NodeBuilder<ReadExcelNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "ReadExcel".equalsIgnoreCase(meta.getType());
    }

    @Override
    public ReadExcelNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new ReadExcelNode(owner, metadata);
    }
  }
}