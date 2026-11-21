package com.ouroboros.notification.template;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.notification.adapter.NotificationAdapterCenter;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.Problem;
import com.ouroboros.notification.util.Classes;
import com.ouroboros.util.Json;
import com.ouroboros.util.JsonSchemas;

/** Builds no clients and sends nothing. Configuration references are inspected through the original service. */
@Component
public class NotificationTemplateDefinitionValidator {
  public static final int MAX_JSON_BYTES = 65_535;
  private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = Json.mapperCopy();
  private final ConfigurationService configuration;

  public NotificationTemplateDefinitionValidator(ConfigurationService configuration) { this.configuration = configuration; }

  public List<Problem> validate(Map<String, Object> candidate, Map<String, Object> previous) {
    var problems = new ArrayList<Problem>();
    for (String field : List.of("payloadSchema", "channel")) {
      int bytes = Json.tryToJsonString(candidate.get(field)).getOrElseThrow(error ->
          new com.ouroboros.exception.BusinessException(400, "通知模板不是有效 JSON", error)).getBytes(StandardCharsets.UTF_8).length;
      if (bytes > MAX_JSON_BYTES) { problems.add(problem(field, null, "STORAGE_CAPACITY", "JSON 超出原 TEXT 字段的 65535 字节容量")); }
    }
    if (!(candidate.get("channel") instanceof Map<?, ?> channels)) {
      problems.add(problem("channel", null, "INVALID_TYPE", "渠道定义必须是对象")); return problems;
    }
    boolean enabled = Boolean.TRUE.equals(candidate.get("isEnabled"));
    if (enabled && channels.isEmpty()) { problems.add(problem("channel", null, "CHANNEL_REQUIRED", "启用的模板至少需要一个有效渠道")); }
    Map<?, ?> oldChannels = previous.get("channel") instanceof Map<?, ?> old ? old : Map.of();
    channels.forEach((key, definition) -> {
      String channel = String.valueOf(key);
      if (enabled || !Objects.equals(definition, oldChannels.get(key))) { validateChannel(channel, definition, problems); }
    });
    return problems;
  }

  public List<Problem> diagnostics(Map<String, Object> template) {
    var diagnostics = new ArrayList<Problem>();
    if (template.get("channel") instanceof Map<?, ?> channels) {
      channels.keySet().forEach(key -> {
        String channel = String.valueOf(key);
        var supplier = NotificationAdapterCenter.findSupplier(channel);
        if (supplier.isEmpty()) { diagnostics.add(problem("channel." + channel, channel, "ADAPTER_MISSING", "渠道实现未安装")); }
        else if (supplier.get().getManagement().isEmpty()) {
          diagnostics.add(problem("channel." + channel, channel, "MANAGEMENT_UNAVAILABLE", "渠道未提供管理描述，无法完整检查"));
        }
      });
    }
    return diagnostics;
  }

  private void validateChannel(String channel, Object definition, List<Problem> problems) {
    String path = "channel." + channel;
    if (!(definition instanceof Map<?, ?> parts)) { problems.add(problem(path, channel, "INVALID_TYPE", "渠道定义必须是对象")); return; }
    parts.keySet().stream().map(String::valueOf).filter(key -> !List.of("schema", "options").contains(key))
        .forEach(key -> problems.add(problem(path + "." + key, channel, "UNKNOWN_FIELD", "渠道字段不支持")));
    var supplier = NotificationAdapterCenter.findSupplier(channel);
    if (supplier.isEmpty() || supplier.get().getChannelSchemaClass() == null || supplier.get().getChannelOptionsClass() == null) {
      problems.add(problem(path, channel, "ADAPTER_MISSING", "渠道实现未安装或未就绪")); return;
    }
    var description = supplier.get().getManagement();
    if (description.isEmpty()) { problems.add(problem(path, channel, "MANAGEMENT_UNAVAILABLE", "渠道未提供管理描述，无法完整检查")); return; }
    if (!(parts.get("schema") instanceof Map<?, ?> schema)) { problems.add(problem(path + ".schema", channel, "INVALID_TYPE", "渠道内容必须是对象")); return; }
    Object optionsValue = parts.getOrDefault("options", null);
    if (optionsValue != null && !(optionsValue instanceof Map<?, ?>)) {
      problems.add(problem(path + ".options", channel, "INVALID_TYPE", "渠道选项必须是对象")); return;
    }
    Map<String, Object> schemaMap = object(schema);
    Map<String, Object> optionsMap = optionsValue instanceof Map<?, ?> options ? object(options) : Map.of();
    var descriptor = supplier.get();
    var resolvedSchema = resolve(schemaMap, path + ".schema", channel, problems);
    var resolvedOptions = resolve(optionsMap, path + ".options", channel, problems);
    if (resolvedSchema == null || resolvedOptions == null) { return; }
    var boundSchema = bind(descriptor.getChannelSchemaClass(), resolvedSchema, path + ".schema", channel, problems);
    var boundOptions = bind(descriptor.getChannelOptionsClass(), resolvedOptions, path + ".options", channel, problems);
    if (boundSchema == null || boundOptions == null) { return; }
    validateStructure(Classes.toManagementSchema(descriptor.getChannelSchemaClass(), description.get().schemaFields()), boundSchema,
        path + ".schema", channel, problems);
    validateStructure(Classes.toManagementSchema(descriptor.getChannelOptionsClass(), description.get().optionsFields()), boundOptions,
        path + ".options", channel, problems);
    supplier.get().validateDefinition(boundSchema, boundOptions).forEach(issue -> problems.add(
        problem(path + "." + issue.path(), channel, issue.code(), issue.message())));
  }

  private Map<String, Object> bind(Class<?> type, Object value, String path, String channel, List<Problem> problems) {
    try {
      Object bound = MAPPER.readerFor(type).with(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
          .readValue(MAPPER.writeValueAsString(value));
      var serialized = Json.tryToMap(MAPPER.writer().without(com.fasterxml.jackson.databind.SerializationFeature.FAIL_ON_EMPTY_BEANS)
          .writeValueAsString(bound)).getOrElseThrow(error ->
          new com.ouroboros.exception.BusinessException(400, "渠道配置必须能绑定为对象", error));
      return object((Map<?, ?>) submittedValues(value, serialized));
    } catch (com.fasterxml.jackson.core.JsonProcessingException failure) {
      String field = failure instanceof com.fasterxml.jackson.databind.JsonMappingException mapping
          ? mapping.getPath().stream().map(reference -> reference.getFieldName() == null
              ? "[" + reference.getIndex() + "]" : "." + reference.getFieldName()).collect(java.util.stream.Collectors.joining()) : "";
      problems.add(problem(path + field, channel, "CHANNEL_BINDING_INVALID", "渠道字段不能转换为声明类型或包含未知字段"));
      return null;
    }
  }

  /** Validate submitted properties after Java coercion, without inventing fields from computed getters. */
  private Object submittedValues(Object submitted, Object bound) {
    if (submitted instanceof Map<?, ?> source && bound instanceof Map<?, ?> typed) {
      var result = new java.util.LinkedHashMap<String, Object>();
      source.forEach((key, value) -> result.put(String.valueOf(key),
          submittedValues(value, typed.containsKey(key) ? typed.get(key) : value)));
      return result;
    }
    if (submitted instanceof List<?> source && bound instanceof List<?> typed && source.size() == typed.size()) {
      return java.util.stream.IntStream.range(0, source.size()).mapToObj(index -> submittedValues(source.get(index), typed.get(index))).toList();
    }
    return bound;
  }

  private void validateStructure(Map<String, Object> schema, Map<String, Object> value, String path, String channel, List<Problem> problems) {
    JsonSchemas.validateResult(schema, value).getMessages().forEach(issue -> problems.add(problem(
        path + (issue.getInstanceLocation() == null ? "" : issue.getInstanceLocation()), channel,
        "CHANNEL_" + issue.getKeyword(), "渠道字段不符合定义（" + issue.getKeyword() + "）")));
  }

  private Object resolve(Object value, String path, String channel, List<Problem> problems) {
    int initialErrors = problems.size();
    Object resolved = NotificationConfigurationReferences.resolve(value, path, (field, key) -> {
      java.util.Optional<com.ouroboros.configuration.ConfigurationSnapshot> snapshot;
      try { snapshot = configuration.getSnapshot(key); }
      catch (RuntimeException failure) {
        throw new com.ouroboros.exception.BusinessException(503, "[NOTIFICATION_CONFIGURATION_UNAVAILABLE] 配置依赖读取失败", failure);
      }
      if (snapshot.isEmpty()) { problems.add(problem(field, channel, "CONFIGURATION_MISSING", "配置引用不存在：" + key)); }
      else if (snapshot.get().metadata().isManagementProtected()) {
        problems.add(problem(field, channel, "PROTECTED_REFERENCE", "模板不能引用受保护配置：" + key));
      } else if (snapshot.get().value() == null || snapshot.get().value() instanceof String raw && raw.isBlank()) {
        problems.add(problem(field, channel, "CONFIGURATION_EMPTY", "配置引用尚未配置：" + key));
      } else {
        var converted = Json.tryToBean(String.class, Json.tryToJsonString(snapshot.get().value())
            .getOrElseThrow(error -> new com.ouroboros.exception.BusinessException(503, "配置引用无法序列化", error)));
        if (converted.isSuccess()) { return converted.get(); }
        problems.add(problem(field, channel, "CONFIGURATION_TYPE_INVALID", "配置引用无法转换为字符串：" + key));
      }
      return "";
    });
    return problems.size() == initialErrors ? resolved : null;
  }

  private Map<String, Object> object(Map<?, ?> source) {
    var result = new java.util.LinkedHashMap<String, Object>(); source.forEach((key, value) -> result.put(String.valueOf(key), value));
    return result;
  }

  private Problem problem(String path, String channel, String code, String message) { return new Problem(path, channel, code, message); }
}
