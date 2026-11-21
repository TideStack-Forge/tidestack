package com.ouroboros.businessmodel.validation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

import com.ouroboros.businessmodel.CompositeViewBusinessModel;
import com.ouroboros.businessmodel.meta.CompositeViewBusinessModelMeta;
import com.ouroboros.businessmodel.schema.CompositeViewWebApis;
import com.ouroboros.businessmodel.validation.exception.BusinessDefineFeatureError;
import com.ouroboros.util.SPI;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.SchemaModel;

/** Checks references against source and action contracts without executing either one. */
public final class CompositeViewContractValidator {
  private final Function<String, Optional<CompositeViewSourceSchema>> sources;
  private final Function<String, WebAPIModel> apis;

  public CompositeViewContractValidator(CompositeViewBusinessModel model) {
    this(CompositeViewContractValidator::source, reference -> CompositeViewWebApis.resolve(model.getModelProvider(), reference));
  }

  public CompositeViewContractValidator(Function<String, Optional<CompositeViewSourceSchema>> sources,
                                        Function<String, WebAPIModel> apis) {
    this.sources = sources;
    this.apis = apis;
  }

  public ValidationResult validate(CompositeViewBusinessModelMeta view) {
    List<Throwable> errors = new ArrayList<>();
    Optional<CompositeViewSourceSchema> source;
    try {
      source = view.getDatasetName() == null || view.getDatasetName().isBlank()
          ? SPI.getCachedReversedServiceStream(CompositeViewSourceSchemaProvider.class)
              .filter(CompositeViewSourceSchemaProvider::supportsCurrentContext).findFirst()
              .flatMap(provider -> provider.resolveEmbedded(view.getEmbeddedQuery(), view.getFullName()))
          : sources.apply(view.getDatasetName());
    } catch (com.ouroboros.exception.OuroborosException failure) {
      errors.add(error("embeddedQuery", failure.getMessage()));
      return new ValidationResult(errors);
    }
    if (source.isEmpty() || !source.get().enabled()) {
      errors.add(error("datasetName", "数据集不存在或已禁用: " + view.getDatasetName()));
      return new ValidationResult(errors);
    }
    Map<String, CompositeViewSourceSchema.Field> fields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    source.get().fields().forEach(field -> fields.put(field.name(), field));
    if (fields.isEmpty()) {
      errors.add(error("datasetName", "数据集尚未声明输出字段"));
    }
    if (view.getColumns() != null) {
      for (int i = 0; i < view.getColumns().size(); i++) {
        var column = view.getColumns().get(i);
        if (column != null && (column.getField() == null || !fields.containsKey(column.getField()))) {
          errors.add(error("columns[" + i + "].field", "dataset.field-not-allowed: " + column.getField()));
        }
      }
    }
    if (view.getFilterConfig() != null && view.getFilterConfig().getItems() != null) {
      for (int i = 0; i < view.getFilterConfig().getItems().size(); i++) {
        var filter = view.getFilterConfig().getItems().get(i);
        var field = filter == null || filter.getField() == null ? null : fields.get(filter.getField());
        if (field == null || !field.filterable()) {
          errors.add(error("filterConfig.items[" + i + "].field", "dataset.field-not-allowed: 字段不存在或不可筛选"));
        }
      }
    }
    if (view.getLayoutConfig() != null && view.getLayoutConfig().getDefaultSort() != null) {
      for (int i = 0; i < view.getLayoutConfig().getDefaultSort().size(); i++) {
        var sort = view.getLayoutConfig().getDefaultSort().get(i);
        var field = sort == null || sort.getField() == null ? null : fields.get(sort.getField());
        if (field == null || !field.sortable()) {
          errors.add(error("layoutConfig.defaultSort[" + i + "].field", "dataset.field-not-allowed: 字段不存在或不可排序"));
        }
      }
    }
    if (view.getActionButtons() != null) {
      Map<String, CompositeViewSourceSchema.Field> projectedFields = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
      Optional.ofNullable(view.getColumns()).orElse(List.of()).stream()
          .filter(column -> column != null && column.getField() != null && fields.containsKey(column.getField()))
          .forEach(column -> projectedFields.put(column.getField(), fields.get(column.getField())));
      for (int i = 0; i < view.getActionButtons().size(); i++) {
        var action = view.getActionButtons().get(i);
        if (action != null && action.getApiRef() != null && action.getContextBinding() != null) {
          validateAction(action, "actionButtons[" + i + "]", projectedFields, errors);
        }
      }
    }
    return new ValidationResult(errors);
  }

  private void validateAction(CompositeViewBusinessModelMeta.ActionButtonDef action, String path,
                              Map<String, CompositeViewSourceSchema.Field> fields, List<Throwable> errors) {
    WebAPIModel api;
    try {
      api = apis.apply(action.getApiRef());
    } catch (com.ouroboros.exception.BusinessException failure) {
      errors.add(error(path + ".apiRef", failure.getMessage()));
      return;
    }
    Map<String, Target> targets = targets(api, action.getContextBinding(), path, errors);
    for (var binding : action.getContextBinding().entrySet()) {
      String location = path + ".contextBinding." + binding.getKey();
      Target target = targets.get(binding.getKey());
      if (target == null) {
        errors.add(error(location, "WebAPI 参数不存在: " + binding.getKey()));
        continue;
      }
      SchemaModel source = sourceSchema(binding.getValue(), "batch".equals(action.getScope()), fields);
      if (source == null) {
        errors.add(error(location, "绑定源字段不存在: " + binding.getValue()));
      } else if (!compatible(source, target.schema())) {
        errors.add(error(location, "绑定源与 WebAPI 参数类型不兼容"));
      }
    }
    targets.forEach((name, target) -> {
      if (target.required() && !action.getContextBinding().containsKey(name)
          && target.schema().getDefaultValue() == null) {
        errors.add(error(path + ".contextBinding." + name, "缺少 WebAPI 必填参数绑定"));
      }
    });
  }

  private static Map<String, Target> targets(WebAPIModel api, Map<String, String> bindings,
                                            String path, List<Throwable> errors) {
    Map<String, Target> result = new LinkedHashMap<>();
    for (var parameter : Optional.ofNullable(api.getParameters()).orElse(List.of())) {
      if (parameter.getIn() == ParameterLocation.HEADER || parameter.getIn() == ParameterLocation.COOKIE) {
        continue;
      }
      addTarget(result, parameter.getName(), parameter.getSchema(), Boolean.TRUE.equals(parameter.getRequired())
          || parameter.getIn() == ParameterLocation.PATH, path, errors);
    }
    var body = api.getRequestBody();
    if (body != null) {
      var media = Optional.ofNullable(body.getContent()).orElse(Map.of()).entrySet().stream()
          .filter(entry -> entry.getKey().equals("application/json") || entry.getKey().endsWith("+json"))
          .findFirst();
      if (media.isPresent()) {
        SchemaModel schema = media.get().getValue().getSchema();
        if (schema == null || !"object".equals(schema.getType())
            || schema.getProperties() == null || schema.getRef() != null) {
          errors.add(error(path + ".apiRef", "WebAPI 请求体缺少可解析的对象参数合同"));
        } else {
          boolean bodyBound = schema.getProperties().keySet().stream().anyMatch(name -> bound(bindings, name));
          bodyTargets(result, "", schema, Boolean.TRUE.equals(body.getRequired()) || bodyBound, bindings, path, errors);
        }
      } else {
        errors.add(error(path + ".apiRef", "WebAPI 请求体需要可解析的 JSON 对象合同"));
      }
    }
    return result;
  }

  private static void bodyTargets(Map<String, Target> targets, String prefix, SchemaModel schema,
                                   boolean required, Map<String, String> bindings, String path, List<Throwable> errors) {
    schema.getProperties().forEach((name, child) -> {
      String key = prefix + name;
      boolean childRequired = required && Optional.ofNullable(schema.getRequired()).orElse(List.of()).contains(name);
      if (child != null && "object".equals(child.getType()) && child.getProperties() != null) {
        bodyTargets(targets, key + ".", child, childRequired || bound(bindings, key), bindings, path, errors);
      } else {
        addTarget(targets, key, child, childRequired, path, errors);
      }
    });
  }

  private static boolean bound(Map<String, String> bindings, String name) {
    return bindings.keySet().stream().anyMatch(target -> target.equals(name) || target.startsWith(name + "."));
  }

  private static void addTarget(Map<String, Target> targets, String name, SchemaModel schema,
                                 boolean required, String path, List<Throwable> errors) {
    if (schema == null || schema.getRef() != null || schema.getType() == null) {
      errors.add(error(path + ".contextBinding." + name, "WebAPI 参数缺少明确的类型合同"));
    } else if (targets.putIfAbsent(name, new Target(schema, required)) != null) {
      errors.add(error(path + ".contextBinding." + name, "WebAPI 存在同名参数，绑定目标不唯一"));
    }
  }

  private static SchemaModel sourceSchema(String expression, boolean batch,
                                          Map<String, CompositeViewSourceSchema.Field> fields) {
    if (expression == null) {
      return null;
    }
    String name = expression.trim();
    if (name.startsWith("${") && name.endsWith("}")) {
      name = name.substring(2, name.length() - 1).trim();
    }
    if (batch && "selectedItems".equals(name)) {
      Map<String, SchemaModel> properties = new LinkedHashMap<>();
      fields.forEach((key, value) -> properties.put(key, fieldSchema(value)));
      return SchemaModel.builder().type("array").items(SchemaModel.builder()
          .type("object").properties(properties).build()).build();
    }
    var field = fields.get(name);
    if (field == null) {
      return null;
    }
    SchemaModel schema = fieldSchema(field);
    return batch ? SchemaModel.builder().type("array").items(schema).build() : schema;
  }

  private static SchemaModel fieldSchema(CompositeViewSourceSchema.Field field) {
    String type = field.dataType() == null ? "string" : field.dataType().toLowerCase(java.util.Locale.ROOT);
    String format = switch (type) { case "date" -> "date"; case "datetime", "timestamp" -> "date-time"; default -> null; };
    type = switch (type) { case "int", "long" -> "integer"; case "decimal", "double", "float" -> "number";
      case "date", "datetime", "timestamp" -> "string"; default -> type; };
    return SchemaModel.builder().type(type).format(format).nullable(field.nullable()).build();
  }

  private static boolean compatible(SchemaModel source, SchemaModel target) {
    if (target == null || target.getRef() != null || target.getType() == null) {
      return false;
    }
    if (Boolean.TRUE.equals(source.getNullable()) && !Boolean.TRUE.equals(target.getNullable())) {
      return false;
    }
    if (!(target.getType().equals(source.getType())
        || "number".equals(target.getType()) && "integer".equals(source.getType()))) {
      return false;
    }
    if ("string".equals(target.getType()) && target.getFormat() != null
        && !target.getFormat().equals(source.getFormat())) {
      return false;
    }
    if ("array".equals(target.getType())) {
      return source.getItems() != null && compatible(source.getItems(), target.getItems());
    }
    if ("object".equals(target.getType()) && target.getProperties() != null) {
      Map<String, SchemaModel> properties = Optional.ofNullable(source.getProperties()).orElse(Map.of());
      for (String name : Optional.ofNullable(target.getRequired()).orElse(List.of())) {
        if (!properties.containsKey(name)) {
          return false;
        }
      }
      return properties.entrySet().stream().allMatch(entry -> !target.getProperties().containsKey(entry.getKey())
          ? !Boolean.FALSE.equals(target.getAdditionalProperties())
          : compatible(entry.getValue(), target.getProperties().get(entry.getKey())));
    }
    return true;
  }

  private static Optional<CompositeViewSourceSchema> source(String name) {
    return SPI.getCachedReversedServiceStream(CompositeViewSourceSchemaProvider.class)
        .filter(CompositeViewSourceSchemaProvider::supportsCurrentContext).findFirst()
        .flatMap(provider -> provider.resolve(name));
  }

  private static BusinessDefineFeatureError error(String path, String message) {
    return new BusinessDefineFeatureError(path + ": " + message);
  }

  private record Target(SchemaModel schema, boolean required) {
  }
}
