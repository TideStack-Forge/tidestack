package com.ouroboros.businessmodel.validation.webapi.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.lang.reflect.Method;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.BusinessModelRelation;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.data.dsl.statement.PopulateClause;

public class PopulateDefinitionValidator {
  private final WhereDefinitionValidator whereDefinitionValidator;
  private final Supplier<BusinessModelDataModelResolver> dataModelResolverSupplier;
  private BusinessModelDataModelResolver dataModelResolver;

  public PopulateDefinitionValidator(WhereDefinitionValidator whereDefinitionValidator,
                                     Supplier<BusinessModelDataModelResolver> dataModelResolverSupplier) {
    this.whereDefinitionValidator = whereDefinitionValidator;
    this.dataModelResolverSupplier = dataModelResolverSupplier;
  }

  /**
   * 解析器会为整条可达模型链构建数据模型站，只有校验 WHERE 时才真正需要它。
   * 若在构造时立即创建，未配置 WHERE 的模型（大多数）会为此付出无谓的构建开销。
   */
  private BusinessModelDataModelResolver resolver() {
    if (dataModelResolver == null) {
      dataModelResolver = dataModelResolverSupplier.get();
    }
    return dataModelResolver;
  }

  public List<String> validate(DefaultBusinessModel currentModel, Object rawPopulate, String path) {
    List<String> errors = new ArrayList<String>();
    validatePopulate(currentModel, rawPopulate, path, errors);
    return errors;
  }

  private void validatePopulate(DefaultBusinessModel currentModel,
                                Object rawPopulate,
                                String path,
                                List<String> errors) {
    if (rawPopulate == null) {
      return;
    }

    if (rawPopulate instanceof CharSequence) {
      addStringEntries(currentModel, rawPopulate.toString(), path, errors);
      return;
    }

    if (rawPopulate instanceof Map<?, ?> map) {
      addMapEntries(currentModel, map, path, errors);
      return;
    }

    if (rawPopulate instanceof Collection<?> collection) {
      int index = 0;
      for (Object item : collection) {
        String itemPath = path + "[" + index + "]";
        if (item instanceof CharSequence) {
          addStringEntries(currentModel, item.toString(), itemPath, errors);
        } else if (item instanceof Map<?, ?> itemMap) {
          addMapEntries(currentModel, itemMap, itemPath, errors);
        } else {
          errors.add(itemPath + ": populate 列表成员必须是字符串或对象");
        }
        index += 1;
      }
      return;
    }

    errors.add(path + ": populate 定义必须是字符串、列表或对象");
  }

  private void addStringEntries(DefaultBusinessModel currentModel, String rawValue, String path, List<String> errors) {
    for (Object entry : PopulateClause.fromRaw(rawValue).getEntries()) {
      String relationName = readPopulateEntryFieldName(entry);
      Object options = readPopulateEntryOptions(entry);
      validateRelation(currentModel, relationName, options, path + " -> " + relationName, errors);
    }
  }

  private void addMapEntries(DefaultBusinessModel currentModel, Map<?, ?> rawMap, String path, List<String> errors) {
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (!(entry.getKey() instanceof CharSequence)) {
        errors.add(path + ": populate 对象的 relation 名称必须是字符串");
        continue;
      }
      String relationName = String.valueOf(entry.getKey()).trim();
      if (relationName.isEmpty()) {
        errors.add(path + ": populate 对象的 relation 名称不能为空");
        continue;
      }
      validateRelation(currentModel, relationName, entry.getValue(), path + " -> " + relationName, errors);
    }
  }

  private void validateRelation(DefaultBusinessModel currentModel,
                                String relationName,
                                Object relationValue,
                                String path,
                                List<String> errors) {
    var relation = currentModel.getRelation(relationName).orElse(null);
    if (relation == null) {
      errors.add(path + ": 填充关联关系不存在");
      return;
    }

    var targetModel = relation.getRelatedModel();
    if (targetModel == null) {
      errors.add(path + ": 目标模型不存在: " + relation.getRelatedModelName());
      return;
    }

    if (relationValue == null || Boolean.TRUE.equals(relationValue)) {
      return;
    }

    if (relationValue instanceof CharSequence || relationValue instanceof Collection<?>) {
      validateFieldList(targetModel, relationValue, path + " -> select", errors);
      return;
    }

    if (!(relationValue instanceof Map<?, ?> relationMap)) {
      errors.add(path + ": 关联配置必须是 true、字符串、列表或对象");
      return;
    }

    Map<String, Object> options = normalizeOptionMap(relationMap);

    if (options.containsKey("SELECT")) {
      validateFieldList(targetModel, options.get("SELECT"), path + " -> select", errors);
    }
    if (options.containsKey("OMIT")) {
      validateFieldList(targetModel, options.get("OMIT"), path + " -> omit", errors);
    }
    if (options.containsKey("WHERE")) {
      errors.addAll(whereDefinitionValidator.validate(
          targetModel,
          options.get("WHERE"),
          path + " -> where",
          resolver()
      ));
    }
    if (options.containsKey("LIMIT")) {
      validateIntegerOption(options.get("LIMIT"), 1L, path + " -> limit", errors);
    }
    if (options.containsKey("OFFSET")) {
      validateIntegerOption(options.get("OFFSET"), 0L, path + " -> offset", errors);
    }
    if (options.containsKey("POPULATE")) {
      validatePopulate(targetModel, options.get("POPULATE"), path + " -> populate", errors);
    }
  }

  private Map<String, Object> normalizeOptionMap(Map<?, ?> rawMap) {
    var normalized = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
      if (!(entry.getKey() instanceof CharSequence)) {
        continue;
      }
      normalized.put(String.valueOf(entry.getKey()).trim().toUpperCase(), entry.getValue());
    }
    return normalized;
  }

  private void validateFieldList(DefaultBusinessModel targetModel,
                                 Object rawFieldList,
                                 String path,
                                 List<String> errors) {
    List<String> fieldNames = parseFieldNames(rawFieldList, path, errors);
    if (fieldNames == null) {
      return;
    }

    for (String fieldName : fieldNames) {
      boolean exists = resolver().resolve(targetModel.getFullName())
          .map(dataModel -> dataModel.getField(fieldName).isPresent())
          .orElse(false);
      if (!exists) {
        exists = targetModel.getField(fieldName).isPresent() || targetModel.getRelation(fieldName).isPresent();
      }
      if (!exists) {
        errors.add(path + ": 字段不存在: " + fieldName);
      }
    }
  }

  private List<String> parseFieldNames(Object rawFieldList, String path, List<String> errors) {
    if (rawFieldList == null) {
      return Collections.emptyList();
    }

    if (rawFieldList instanceof CharSequence) {
      String raw = rawFieldList.toString();
      List<String> fieldNames = new ArrayList<String>();
      for (String token : raw.split(",")) {
        String normalized = token.trim();
        if (!normalized.isEmpty()) {
          fieldNames.add(normalized);
        }
      }
      return fieldNames;
    }

    if (rawFieldList instanceof Collection<?> collection) {
      List<String> fieldNames = new ArrayList<String>();
      int index = 0;
      for (Object item : collection) {
        if (!(item instanceof CharSequence)) {
          errors.add(path + "[" + index + "]: 字段名必须是字符串");
          return null;
        }
        String normalized = item.toString().trim();
        if (!normalized.isEmpty()) {
          fieldNames.add(normalized);
        }
        index += 1;
      }
      return fieldNames;
    }

    errors.add(path + ": 字段列表必须是字符串或字符串数组");
    return null;
  }

  private void validateIntegerOption(Object rawValue, long minValue, String path, List<String> errors) {
    if (rawValue == null) {
      return;
    }

    if (!(rawValue instanceof Byte)
        && !(rawValue instanceof Short)
        && !(rawValue instanceof Integer)
        && !(rawValue instanceof Long)) {
      errors.add(path + ": 必须是整数");
      return;
    }

    long value = ((Number) rawValue).longValue();
    if (value < minValue) {
      errors.add(path + ": 必须大于等于 " + minValue);
    }
  }

  private String readPopulateEntryFieldName(Object entry) {
    Object value = invokePopulateEntryMethod(entry, "fieldName");
    return value == null ? "" : String.valueOf(value);
  }

  private Object readPopulateEntryOptions(Object entry) {
    return invokePopulateEntryMethod(entry, "options");
  }

  private Object invokePopulateEntryMethod(Object entry, String methodName) {
    if (entry == null) {
      return null;
    }
    try {
      Method method = entry.getClass().getMethod(methodName);
      return method.invoke(entry);
    } catch (ReflectiveOperationException ex) {
      throw new IllegalStateException("读取 PopulateClause 结果失败", ex);
    }
  }
}
