package com.ouroboros.businessmodel.projection.uischema.prototypes;

import java.util.*;
import java.util.stream.Collectors;

import com.ouroboros.businessmodel.BusinessModelField;
import com.ouroboros.businessmodel.DefaultBusinessModel;
import com.ouroboros.businessmodel.util.BusinessModelUtil;
import com.ouroboros.businessmodel.util.PrototypeTemplate;
import com.ouroboros.uischema.schema.ApiConfig;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Converter;
import com.ouroboros.util.Maps;

class CrudUiSchemaBuilder {
  private final DefaultBusinessModel businessModel;
  private final Map<String, Object> config;
  private final CrudUiSchemaColumnProcessor columnProcessor;
  private final CrudUiSchemaActionProcessor actionProcessor;

  CrudUiSchemaBuilder(DefaultBusinessModel businessModel,
                      Map<String, Object> config,
                      CrudUiSchemaColumnProcessor columnProcessor,
                      CrudUiSchemaActionProcessor actionProcessor) {
    this.businessModel = businessModel;
    this.config = config;
    this.columnProcessor = columnProcessor;
    this.actionProcessor = actionProcessor;
  }

  SchemaNode build() {
    if (!isAuto()) {
      var customSchemaMap = Optional.ofNullable(config.get("schema"))
          .filter(Map.class::isInstance)
          .map(Map.class::cast)
          .orElseGet(() -> {
            var hashMap = new HashMap<String, Object>();
            hashMap.put("type", "page");
            hashMap.put("body", "请配置自定义页面");
            return hashMap;
          });
      return new SchemaNode(customSchemaMap);
    }

    config.put("businessModel", businessModel);
    config.put("title", businessModel.getTitle());
    columnProcessor.preprocessColumns();
    actionProcessor.preprocessHeaderToolbar();
    actionProcessor.preprocessOperations();
    preprocessFilters();
    preprocessApi();
    actionProcessor.preprocessActions();
    return PrototypeTemplate.generate("ui-schema/crudTable", config, SchemaNode.class);
  }

  private void preprocessFilters() {
    if (!showFilters()) {
      return;
    }
    var filters = getDefaultFilers().stream()
        .filter(Map.class::isInstance)
        .map(itemMap -> {
          var type = Optional.ofNullable(itemMap.get("type"))
              .map(Object::toString)
              .orElse("field");
          if ("field".equalsIgnoreCase(type)) {
            return buildFieldFilter(itemMap);
          }
          if ("relation".equalsIgnoreCase(type)) {
            return buildRelationFilter(itemMap);
          }
          return null;
        })
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
    config.put("filters", filters);
  }

  private SchemaNode buildFieldFilter(Map<String, Object> config) {
    var fieldName = Optional.ofNullable(config.get("fieldName"))
        .map(Object::toString)
        .orElse("");

    var field = businessModel.getField(fieldName).orElse(null);
    if (field == null && !isAuditField(fieldName)) {
      return null;
    }
    var filterOperator = Optional.ofNullable(config.get("filterOperator"))
        .map(Object::toString)
        .orElse("EQ");
    var type = Optional.ofNullable(config.get("filterType"))
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .orElse("input-text");
    var customSchema = Optional.ofNullable(config.get("schema"))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElse(null);
    var label = field != null ? field.getTitle() : getAuditFieldTitle(fieldName);
    var filterProps = Optional.ofNullable(config.get("filterProps"))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElseGet(Collections::emptyMap);

    var schema = new SchemaNode();
    schema.setProp("label", label);
    if ("custom".equals(type)) {
      if (customSchema != null) {
        schema.putProps(customSchema);
      } else {
        schema.setProp("type", "input-text");
      }
    } else if ("input-datetime-range".equals(type)) {
      schema.setProp("type", type);
      schema.setProp("format", "YYYY-MM-DD HH:mm:ss");
      schema.setProp("joinValues", false);
      schema.setProp("extractValue", true);
    } else if ("input-date-range".equals(type)) {
      schema.setProp("type", type);
      schema.setProp("format", "YYYY-MM-DD");
      schema.setProp("joinValues", false);
      schema.setProp("extractValue", true);
    } else {
      schema.setProp("type", type);
    }

    if ("EQ".equalsIgnoreCase(filterOperator) || "IN".equalsIgnoreCase(filterOperator)) {
      schema.setProp("name", fieldName);
    } else {
      schema.setProp("name", fieldName + "." + filterOperator);
    }
    if (!"custom".equalsIgnoreCase(type)) {
      schema.putProps(filterProps);
    }

    return schema;
  }

  private boolean isAuditField(String fieldName) {
    if (!businessModel.getExtraPropDeep(Boolean.class, "features", "isEnabledAuditFields").orElse(false)) {
      return false;
    }
    return "createdAt".equals(fieldName)
        || "createdBy".equals(fieldName)
        || "updatedAt".equals(fieldName)
        || "updatedBy".equals(fieldName);
  }

  private String getAuditFieldTitle(String fieldName) {
    if ("createdAt".equals(fieldName)) {
      return "创建时间";
    }
    if ("createdBy".equals(fieldName)) {
      return "创建人";
    }
    if ("updatedAt".equals(fieldName)) {
      return "更新时间";
    }
    if ("updatedBy".equals(fieldName)) {
      return "更新人";
    }
    return "未知字段";
  }

  private SchemaNode buildRelationFilter(Map<String, Object> config) {
    final var omitFilterProps = new String[]{"defaultRequestParams"};
    var relationName = Optional.ofNullable(config.get("fieldName"))
        .map(Object::toString)
        .orElse("");
    var filterOperator = Optional.ofNullable(config.get("filterOperator"))
        .map(Object::toString)
        .orElse("EQ");
    var filterType = Optional.ofNullable(config.get("filterType"))
        .map(Object::toString)
        .orElse("auto");
    var filterProps = Optional.ofNullable(config.get("filterProps"))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .orElseGet(Collections::emptyMap);
    var relation = businessModel.getRelation(relationName).orElse(null);
    var requestParams = Optional.ofNullable(filterProps.get("defaultRequestParams"))
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .filter(m -> !m.isEmpty());
    // 暂时不支持子表关联字段
    if (relation == null || "child".equalsIgnoreCase(relation.getType())) {
      return null;
    }
    SchemaNode schema;
    if ("select".equalsIgnoreCase(filterType)) {
      schema = UiSchemaUtils.buildSelect(relation);
    } else if ("picker".equalsIgnoreCase(filterType)) {
      schema = UiSchemaUtils.buildRelationFormItem(relation);
    } else {
      schema = UiSchemaUtils.buildRelationFormItem(relation);
    }
    if ("picker".equalsIgnoreCase(schema.getType())) {
      requestParams.ifPresent(defaultParams -> {
        var pickerSchema = schema.getProp(SchemaNode.class, "pickerSchema");
        pickerSchema.ifPresent(picker -> {
          picker.setProp("defaultParams", defaultParams);
        });
      });
    } else {
      requestParams.ifPresent(params -> {
        var api = schema.getProp("source");
        if (api instanceof ApiConfig apiConfig) {
          apiConfig.setData(params);
        }
        if (api instanceof String apiUrl) {
          var apiConfig = new ApiConfig(apiUrl);
          apiConfig.setData(params);
          schema.setProp("source", apiConfig);
        }
      });
    }

    if (filterOperator.endsWith("IN")) {
      schema.setProp("multiple", true);
      schema.setProp("joinValues", false);
      schema.setProp("extractValue", true);
      schema.setProp("maxTagCount", 2);
    }
    schema.setProp("required", false);
    schema.setProp("inputClassName", "w-64");

    if (!filterProps.isEmpty()) {
      schema.putProps(Maps.omit(filterProps, omitFilterProps));
    }

    if ("EQ".equalsIgnoreCase(filterOperator) || "IN".equalsIgnoreCase(filterOperator)) {
      schema.setProp("name", relationName);
    } else {
      schema.setProp("name", relationName + "." + filterOperator);
    }
    return schema;
  }

  private void preprocessApi() {
    if (config.containsKey("api")) {
      return;
    }
    var api = new ApiConfig();
    var baseUrl = BusinessModelUtil.buildKebabCasePath(businessModel);
    var additionalParams = Optional.ofNullable(config.get("additionalParams"))
        .filter(Map.class::isInstance)
        .map(m -> (Map<String, String>) m)
        .orElseGet(Collections::emptyMap);
    var url = "/api" + baseUrl;
    if (!additionalParams.isEmpty()) {
      url = url + "?" + additionalParams.entrySet().stream()
          .map(e -> e.getKey() + "=" + e.getValue())
          .collect(Collectors.joining("&"));
      // 查找所有参数值中包含${}的参数，将${}中间的内容提取出来
      var variables = additionalParams.values().stream()
          .map(String::trim)
          // 过滤掉非表达式的参数
          .filter(v -> v.startsWith("$"))
          // 两种表达式格式：${}和$，提取出其中的内容
          .map(v -> v.startsWith("${") && v.endsWith("}")
              ? v.substring(2, v.length() - 1).trim()
              : v.substring(1))
          .collect(Collectors.toSet());
      if (!variables.isEmpty()) {
        if (variables.size() == 1) {
          api.setSendOn("${" + variables.iterator().next() + "}");
        } else {
          api.setSendOn("${AND(" + String.join(",", variables) + ")}");
        }
      }
    }

    api.setUrl(url);
    config.put("api", api);
  }

  private Boolean isAuto() {
    return Optional.ofNullable(config.get("isAuto"))
        .map(Converter::toBoolean)
        .orElse(businessModel.getExtraPropDeep(Boolean.class, "views", "index", "isAuto").orElse(true));
  }

  private List<Map<String, Object>> getDefaultFilers() {
    return Optional.ofNullable(config.get("defaultFilters"))
        .filter(List.class::isInstance)
        .map(l -> (List<Map<String, Object>>) l)
        .orElseGet(Collections::emptyList);
  }

  private Boolean showFilters() {
    return Optional.ofNullable(config.getOrDefault("showFilters", true))
        .map(Converter::toBoolean)
        .orElse(true);
  }
}
