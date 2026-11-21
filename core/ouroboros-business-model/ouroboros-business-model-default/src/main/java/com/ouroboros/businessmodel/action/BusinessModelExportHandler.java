package com.ouroboros.businessmodel.action;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import com.ouroboros.businessmodel.BusinessModelMetaCenter;
import com.ouroboros.businessmodel.meta.BusinessModelMeta;
import com.ouroboros.data.dict.DataDictItem;
import com.ouroboros.data.model.DataModelCenter;
import com.ouroboros.data.record.Record;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.util.Dates;
import com.ouroboros.util.FileResult;
import com.ouroboros.util.Maps;
import com.ouroboros.util.excel.ExcelWriter;

/** The original model export uses one implementation for Java, generated HTTP and the legacy URL. */
@Component
public final class BusinessModelExportHandler implements BusinessActionHandler {
  public static final String NAME = "businessModelExportHandler";
  public static final String MEDIA_TYPE = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
  private final UserCenter userCenter;

  public BusinessModelExportHandler(UserCenter userCenter) { this.userCenter = userCenter; }

  @Override public String getName() { return NAME; }

  @Override
  public FileResult handle(BusinessActionContext context, BusinessActionInput<?, ?> input) {
    var meta = BusinessModelMetaCenter.getBusinessModelMeta(context.modelFullName())
        .orElseThrow(() -> new BusinessException("业务模型不存在：" + context.modelFullName()));
    if (!meta.getExtraPropDeep(Boolean.class, "features.isEnabledServerSideExport").orElse(false)) {
      throw new BusinessException("业务模型未启用导出");
    }
    var fields = exportFields(meta);
    var model = DataModelCenter.getDataModel(context.modelFullName())
        .orElseThrow(() -> new BusinessException("数据模型不存在：" + context.modelFullName()));
    Object query = input.params() instanceof Map<?, ?> params ? params.get("query") : null;
    if (query != null && !(query instanceof Map<?, ?>)) { throw new BusinessException("导出查询条件必须是对象"); }
    var conditions = new LinkedHashMap<String, Object>();
    if (query instanceof Map<?, ?> values) { values.forEach((key, value) -> conditions.put(String.valueOf(key), value)); }
    var records = model.query(Maps.<String, Object>builder().put("populate", buildPopulate(fields))
        .put("where", Maps.Hierarchical.build(conditions)).build())
        .getOrElseThrow(error -> new BusinessException("查询导出数据失败", error));
    return file(meta, fields, records);
  }

  private List<ExportField> exportFields(BusinessModelMeta meta) {
    var raw = meta.getExtraPropDeep(List.class, "features.serverSideExport.fields")
        .orElseThrow(() -> new BusinessException("未配置导出字段"));
    List<ExportField> fields = new ArrayList<>();
    for (Object value : raw) {
      if (value instanceof Map<?, ?> field) {
        String name = text(field.get("fieldName")); String label = text(field.get("fieldLabel"));
        if (!StringUtils.isAnyBlank(name, label)) {
          String path = text(field.get("value"));
          fields.add(new ExportField(path.isBlank() ? name : path, text(field.get("fieldType")), name, label));
        }
      }
    }
    if (fields.isEmpty()) { throw new BusinessException("导出字段配置为空"); }
    return List.copyOf(fields);
  }

  private Map<String, Object> buildPopulate(List<ExportField> fields) {
    var populate = Maps.newHierarchical();
    fields.stream().filter(field -> field.value().contains("."))
        .map(field -> Arrays.asList(field.value().split("\\.")))
        .map(path -> path.subList(0, path.size() - 1)).sorted(Comparator.comparingInt(List::size))
        .forEach(path -> {
          String key = String.join(".populate.", path);
          if (!populate.hasPath(key)) { populate.setValue(key, new HashMap<String, Object>()); }
        });
    return populate.getWrappedMap();
  }

  private FileResult file(BusinessModelMeta meta, List<ExportField> fields, List<Record> records) {
    Path path;
    try { path = Files.createTempFile("ouroboros-model-export-", ".xlsx"); }
    catch (IOException error) { throw new BusinessException("无法创建导出文件", error); }
    try {
      var headers = fields.stream().map(ExportField::fieldLabel).toList();
      var rows = records.stream().map(record -> fields.stream().map(field -> getValue(record, field))
          .collect(Collectors.toList())).toList();
      try (var output = Files.newOutputStream(path)) {
        ExcelWriter.writeExcel(output, new ExcelWriter.SheetData("导出数据", headers, rows).setFreezeRowCount(1), false);
      }
      return FileResult.temporary(path, Optional.ofNullable(meta.getTitle()).filter(title -> !title.isBlank()).orElse(meta.getName()) + ".xlsx", MEDIA_TYPE);
    } catch (IOException | RuntimeException error) {
      try { Files.deleteIfExists(path); } catch (IOException cleanup) { error.addSuppressed(cleanup); }
      throw new BusinessException("生成导出文件失败", error);
    }
  }

  private static String text(Object value) { return value == null ? "" : value.toString(); }

  private Object getValue(Record record, ExportField exportField) {
    var rawValueOpt = Maps.Hierarchical.getValue(record, exportField.fieldName());
    if (!(rawValueOpt.isPresent())) {
      return null;
    }
    var rawValue = rawValueOpt.get();
    if (StringUtils.endsWithAny(exportField.fieldType().toLowerCase(), "createdby", "updatedby")) {
      return getUserDisplayName(rawValue);
    }
    return extractDisplayValue(rawValue);
  }

  private Object extractDisplayValue(Object rawValue) {
    if (rawValue == null) {
      return null;
    }
    if (rawValue instanceof Number number) {
      return number;
    }
    if (rawValue instanceof Employee employee) {
      return employee.getName();
    }
    if (rawValue instanceof OrganizationNode org) {
      return org.getName();
    }
    if (rawValue instanceof User userValue) {
      return getUserDisplayName(userValue);
    }
    if (rawValue instanceof Date date) {
      return date;
    }
    if (rawValue instanceof LocalDate date) {
      return Dates.toDate(date);
    }
    if (rawValue instanceof LocalDateTime dateTime) {
      return Dates.toDate(dateTime);
    }
    if (rawValue instanceof DataDictItem item) {
      return item.getLabel();
    }
    if (rawValue instanceof Map<?, ?> map) {
      return getMapDisplayValue(map);
    }
    if (rawValue instanceof List<?> list) {
      return getListDisplayValue(list);
    }
    if (rawValue instanceof String str) {
      return str;
    }
    return rawValue;
  }

  private String getUserDisplayName(Object rawValue) {
    if (rawValue == null) {
      return null;
    }
    if (rawValue instanceof Employee employee) {
      return employee.getName();
    }
    if (rawValue instanceof User userValue) {
      return userValue.getUserIdentity();
    }
    if (rawValue instanceof String userId) {
      var user = userCenter.getUser(userId);
      if (!user.isPresent()) {
        return userId;
      }
      return getUserDisplayName(user.get());
    }
    return rawValue.toString();
  }

  private Object getMapDisplayValue(Map<?, ?> map) {
    if (map.isEmpty()) {
      return null;
    }
    if (map.containsKey("label")) {
      return extractDisplayValue(map.get("label"));
    }
    if (map.containsKey("name")) {
      return extractDisplayValue(map.get("name"));
    }
    return map.entrySet().stream()
        .map(entry -> entry.getKey() + ": " + entry.getValue())
        .collect(Collectors.joining(", "));
  }

  private String getListDisplayValue(List<?> list) {
    return list.stream()
        .map(this::extractDisplayValue)
        .map(String::valueOf)
        .collect(Collectors.joining(", "));
  }

  private record ExportField(String value, String fieldType, String fieldName, String fieldLabel) { }
}
