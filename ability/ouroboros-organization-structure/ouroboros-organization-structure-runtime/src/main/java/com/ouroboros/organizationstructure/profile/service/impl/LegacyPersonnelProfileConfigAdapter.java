package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationDefaults;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationKeys;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldDefinition;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

@Service
public class LegacyPersonnelProfileConfigAdapter {

  private static final String EDIT_EMPLOYEE_RESOURCE =
      "META-INF/ouroboros/metadata/system-setting/organization-management/edit-employee.ui-schema.json";
  private static final String EMPLOYEE_DETAIL_RESOURCE =
      "META-INF/ouroboros/metadata/system-setting/organization-management/employee-detail.ui-schema.json";

  private final ConfigurationService runtimeConfigurationService;

  public LegacyPersonnelProfileConfigAdapter(ConfigurationService runtimeConfigurationService) {
    this.runtimeConfigurationService = runtimeConfigurationService;
  }

  public PersonnelProfileFormSchemeEntity buildDefaultScheme(String appName) {
    PersonnelProfileTemplateDefinition editDefinition = getResolvedTemplateDefinition(true);
    PersonnelProfileTemplateDefinition detailDefinition = getResolvedTemplateDefinition(false);

    PersonnelProfileFormSchemeEntity entity = new PersonnelProfileFormSchemeEntity();
    entity.setAppName(StringUtils.trimToNull(appName));
    entity.setName("默认人员档案表单方案");
    entity.setDescription("由旧版人员档案配置自动投影生成");
    entity.setEnabled(true);
    entity.setIsDefault(true);
    entity.setPriority(0);
    entity.setScope(scopeOf(appName));
    entity.setMatchingRules(Collections.<String, Object>emptyMap());
    entity.setCreateSchema(loadAndExpand(EDIT_EMPLOYEE_RESOURCE, editDefinition, true));
    entity.setEditSchema(loadAndExpand(EDIT_EMPLOYEE_RESOURCE, editDefinition, true));
    entity.setDetailSchema(loadAndExpand(EMPLOYEE_DETAIL_RESOURCE, detailDefinition, false));
    entity.setLegacySource(legacySource());
    return entity;
  }

  private Map<String, Object> scopeOf(String appName) {
    Map<String, Object> scope = new LinkedHashMap<String, Object>();
    if (StringUtils.isBlank(appName)) {
      scope.put("type", "GLOBAL");
      return scope;
    }
    scope.put("type", "APP");
    scope.put("appName", appName);
    return scope;
  }

  private Map<String, Object> legacySource() {
    Map<String, Object> source = new LinkedHashMap<String, Object>();
    source.put("source", "runtime-configuration");
    source.put("templateKey", PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    source.put("importedKeys", Arrays.asList(
        PersonnelProfileConfigurationKeys.TEMPLATE,
        PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE,
        PersonnelProfileConfigurationKeys.EDIT_LAYOUT,
        PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT));
    return source;
  }

  private PersonnelProfileTemplateDefinition getResolvedTemplateDefinition(boolean editMode) {
    PersonnelProfileTemplateDefinition template = getTemplateDefinition().copy();
    PersonnelProfileRuntimeOverrideConfig overrideConfig = getRuntimeOverrideConfig();
    PersonnelProfileRuntimeLayoutConfig layoutConfig = editMode ? getRuntimeLayoutConfig() : getDisplayLayoutConfig();
    PersonnelProfileTemplateDefinition resolved = template.copy();
    resolved.setFields(layoutConfig.applyTo(overrideConfig.applyTo(template)));
    return resolved;
  }

  private PersonnelProfileTemplateDefinition getTemplateDefinition() {
    PersonnelProfileTemplateDefinition definition = readConfiguration(PersonnelProfileConfigurationKeys.TEMPLATE,
        PersonnelProfileTemplateDefinition.class,
        PersonnelProfileConfigurationDefaults::defaultTemplateDefinition);
    return normalizeTemplate(definition);
  }

  private PersonnelProfileRuntimeOverrideConfig getRuntimeOverrideConfig() {
    PersonnelProfileRuntimeOverrideConfig config = readConfiguration(PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE,
        PersonnelProfileRuntimeOverrideConfig.class,
        PersonnelProfileConfigurationDefaults::defaultOverrideConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  private PersonnelProfileRuntimeLayoutConfig getRuntimeLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = readConfiguration(PersonnelProfileConfigurationKeys.EDIT_LAYOUT,
        PersonnelProfileRuntimeLayoutConfig.class,
        PersonnelProfileConfigurationDefaults::defaultEditLayoutConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  private PersonnelProfileRuntimeLayoutConfig getDisplayLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = readConfiguration(PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT,
        PersonnelProfileRuntimeLayoutConfig.class,
        PersonnelProfileConfigurationDefaults::defaultDisplayLayoutConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  private <T> T readConfiguration(String key, Class<T> clazz, Supplier<T> defaultSupplier) {
    String value = runtimeConfigurationService.get(String.class, key).orElse(null);
    if (StringUtils.isBlank(value)) {
      return defaultSupplier.get();
    }
    return Json.tryToBean(clazz, value)
        .getOrElseThrow(cause -> new ValidationException("人员档案运行时配置解析失败: " + key + ", " + cause.getMessage()));
  }

  private PersonnelProfileTemplateDefinition normalizeTemplate(PersonnelProfileTemplateDefinition definition) {
    if (definition == null) {
      return PersonnelProfileConfigurationDefaults.defaultTemplateDefinition();
    }
    PersonnelProfileTemplateDefinition copy = definition.copy();
    if (StringUtils.isBlank(copy.getKey())) {
      copy.setKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    }
    if (StringUtils.isBlank(copy.getLabel())) {
      copy.setLabel("人员档案模板");
    }
    if (copy.getFields().isEmpty()) {
      copy.setFields(PersonnelProfileConfigurationDefaults.defaultFields());
    }
    return copy;
  }

  private Map<String, Object> loadAndExpand(String resourcePath, PersonnelProfileTemplateDefinition definition, boolean editMode) {
    return ResourceUtil.getResourceContentStream(resourcePath)
        .findFirst()
        .map(Json::toMap)
        .map(this::unwrapSchema)
        .map(schema -> toMap(expandNode(schema, definition, editMode)))
        .orElse(Collections.emptyMap());
  }

  private Map<String, Object> unwrapSchema(Map<String, Object> resource) {
    Object schema = resource.get("schema");
    if (schema == null) {
      return resource;
    }
    return toMap(schema);
  }

  private Object expandNode(Object node, PersonnelProfileTemplateDefinition definition, boolean editMode) {
    if (node instanceof Map) {
      return expandMap((Map<String, Object>) node, definition, editMode);
    }
    if (node instanceof List) {
      return expandList((List<Object>) node, definition, editMode);
    }
    return node;
  }

  private Object expandMap(Map<String, Object> source, PersonnelProfileTemplateDefinition definition, boolean editMode) {
    if (isPersonnelProfileField(source)) {
      return expandPersonnelProfileField(source, definition, editMode);
    }
    if (isPersonnelProfileLayout(source)) {
      return expandPersonnelProfileLayout(definition, editMode);
    }
    Map<String, Object> target = new LinkedHashMap<String, Object>();
    for (Map.Entry<String, Object> entry : source.entrySet()) {
      target.put(entry.getKey(), expandNode(entry.getValue(), definition, editMode));
    }
    return target;
  }

  private List<Object> expandList(List<Object> source, PersonnelProfileTemplateDefinition definition, boolean editMode) {
    List<Object> target = new ArrayList<Object>(source.size());
    for (Object item : source) {
      Object expanded = expandNode(item, definition, editMode);
      if (expanded instanceof List) {
        target.addAll((List<?>) expanded);
      } else {
        target.add(expanded);
      }
    }
    return target;
  }

  private List<Object> expandPersonnelProfileLayout(PersonnelProfileTemplateDefinition definition, boolean editMode) {
    List<Object> nodes = new ArrayList<Object>();
    for (PersonnelProfileFieldDefinition field : definition.getFields()) {
      if (field == null || Boolean.TRUE.equals(field.getHidden())) {
        continue;
      }
      nodes.add(editMode ? field.toEditNode() : field.toDetailNode());
    }
    return nodes;
  }

  private Map<String, Object> expandPersonnelProfileField(Map<String, Object> source,
                                                          PersonnelProfileTemplateDefinition definition,
                                                          boolean editMode) {
    String fieldName = stringValue(source.get("fieldName"));
    if (fieldName == null) {
      return source;
    }
    for (PersonnelProfileFieldDefinition field : definition.getFields()) {
      if (fieldName.equals(field.getName())) {
        return editMode ? toMap(field.toEditNode()) : toMap(field.toDetailNode());
      }
    }
    return source;
  }

  private boolean isPersonnelProfileField(Map<String, Object> node) {
    return "personnel-profile-field".equals(String.valueOf(node.get("type")));
  }

  private boolean isPersonnelProfileLayout(Map<String, Object> node) {
    return "personnel-profile-layout".equals(String.valueOf(node.get("type")));
  }

  private Map<String, Object> toMap(Object node) {
    if (!(node instanceof Map)) {
      if (node == null) {
        return new LinkedHashMap<String, Object>();
      }
      return new LinkedHashMap<String, Object>(Json.toMap(Json.toJsonString(node)));
    }
    Map<String, Object> target = new LinkedHashMap<String, Object>();
    for (Map.Entry<?, ?> entry : ((Map<?, ?>) node).entrySet()) {
      target.put(String.valueOf(entry.getKey()), entry.getValue());
    }
    return target;
  }

  private String stringValue(Object value) {
    return value == null ? null : String.valueOf(value);
  }
}
