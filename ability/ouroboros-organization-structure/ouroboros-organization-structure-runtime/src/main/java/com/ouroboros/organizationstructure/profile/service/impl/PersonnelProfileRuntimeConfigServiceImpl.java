package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationDefaults;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationKeys;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldOverride;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileRuntimeConfigService;
import com.ouroboros.util.Json;

/**
 * 人员档案运行时配置服务实现
 */
@Service
public class PersonnelProfileRuntimeConfigServiceImpl implements PersonnelProfileRuntimeConfigService {

  private static final Logger LOGGER = LoggerFactory.getLogger(PersonnelProfileRuntimeConfigServiceImpl.class);

  private final ConfigurationService runtimeConfigurationService;

  private final ConfigurationManagementService configurationManagementService;

  @Autowired(required = false)
  private PersonnelProfileFormSchemeService personnelProfileFormSchemeService;

  @Autowired(required = false)
  private LegacyPersonnelProfileConfigAdapter legacyPersonnelProfileConfigAdapter;

  public PersonnelProfileRuntimeConfigServiceImpl(ConfigurationService runtimeConfigurationService,
      ConfigurationManagementService configurationManagementService) {
    this.runtimeConfigurationService = runtimeConfigurationService;
    this.configurationManagementService = configurationManagementService;
  }

  @Override
  public PersonnelProfileTemplateDefinition getTemplateDefinition() {
    PersonnelProfileTemplateDefinition definition = readConfiguration(PersonnelProfileConfigurationKeys.TEMPLATE, PersonnelProfileTemplateDefinition.class, PersonnelProfileConfigurationDefaults::defaultTemplateDefinition);
    return normalizeTemplate(definition);
  }

  @Override
  public PersonnelProfileRuntimeOverrideConfig getRuntimeOverrideConfig() {
    PersonnelProfileRuntimeOverrideConfig config = readConfiguration(PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE, PersonnelProfileRuntimeOverrideConfig.class, PersonnelProfileConfigurationDefaults::defaultOverrideConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  @Override
  public PersonnelProfileRuntimeLayoutConfig getRuntimeLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = readConfiguration(PersonnelProfileConfigurationKeys.EDIT_LAYOUT, PersonnelProfileRuntimeLayoutConfig.class, PersonnelProfileConfigurationDefaults::defaultEditLayoutConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  @Override
  public PersonnelProfileRuntimeLayoutConfig getDisplayLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = readConfiguration(PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT, PersonnelProfileRuntimeLayoutConfig.class, PersonnelProfileConfigurationDefaults::defaultDisplayLayoutConfig);
    if (config.getTemplateKey() == null) {
      config.setTemplateKey(getTemplateDefinition().getKey());
    }
    return config;
  }

  @Override
  public PersonnelProfileTemplateDefinition getResolvedTemplateDefinition() {
    return getResolvedTemplateDefinition(true);
  }

  @Override
  public PersonnelProfileTemplateDefinition getResolvedTemplateDefinition(boolean editMode) {
    PersonnelProfileTemplateDefinition template = getTemplateDefinition().copy();
    PersonnelProfileRuntimeOverrideConfig overrideConfig = getRuntimeOverrideConfig();
    PersonnelProfileRuntimeLayoutConfig layoutConfig = editMode ? getRuntimeLayoutConfig() : getDisplayLayoutConfig();

    PersonnelProfileTemplateDefinition resolved = template.copy();
    resolved.setFields(layoutConfig.applyTo(overrideConfig.applyTo(template)));
    return resolved;
  }

  @Override
  public Map<String, Object> getResolvedEditSchema() {
    return resolvePreferredFormScheme().map(PersonnelProfileFormSchemeEntity::getEditSchema).orElse(Collections.<String, Object>emptyMap());
  }

  @Override
  public Map<String, Object> getResolvedDetailSchema() {
    return resolvePreferredFormScheme().map(PersonnelProfileFormSchemeEntity::getDetailSchema).orElse(Collections.<String, Object>emptyMap());
  }

  @Override
  public void saveTemplateDefinition(PersonnelProfileTemplateDefinition definition, String updateBy) {
    saveConfiguration(PersonnelProfileConfigurationKeys.TEMPLATE, validateTemplate(definition), updateBy);
  }

  @Override
  public void saveRuntimeOverrideConfig(PersonnelProfileRuntimeOverrideConfig definition, String updateBy) {
    saveConfiguration(PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE, normalizeOverrideConfig(definition), updateBy);
  }

  @Override
  public void saveRuntimeLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy) {
    saveConfiguration(PersonnelProfileConfigurationKeys.EDIT_LAYOUT, normalizeLayoutConfig(definition), updateBy);
  }

  @Override
  public void saveDisplayLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy) {
    saveConfiguration(PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT, normalizeLayoutConfig(definition), updateBy);
  }

  private <T> T readConfiguration(String key, Class<T> clazz, Supplier<T> defaultSupplier) {
    String value = runtimeConfigurationService.get(String.class, key).orElse(null);
    if (StringUtils.isBlank(value)) {
      return defaultSupplier.get();
    }
    return Json.tryToBean(clazz, value).getOrElseThrow(cause -> new ValidationException("人员档案运行时配置解析失败: " + key + ", " + cause.getMessage()));
  }

  private void saveConfiguration(String key, Object value, String updateBy) {
    Map<String, Object> payload = new HashMap<String, Object>();
    payload.put(key, Json.toJsonString(value));
    configurationManagementService.update(payload, updateBy);
  }

  private PersonnelProfileTemplateDefinition validateTemplate(PersonnelProfileTemplateDefinition definition) {
    if (definition == null) {
      throw new ValidationException("人员档案模板不能为空");
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
    copy.validate();
    return copy;
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

  private PersonnelProfileRuntimeOverrideConfig normalizeOverrideConfig(PersonnelProfileRuntimeOverrideConfig config) {
    PersonnelProfileRuntimeOverrideConfig copy = config == null ? new PersonnelProfileRuntimeOverrideConfig() : config;
    if (StringUtils.isBlank(copy.getTemplateKey())) {
      copy.setTemplateKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    }
    if (copy.getFieldOverrides().isEmpty()) {
      copy.setFieldOverrides(Collections.<PersonnelProfileFieldOverride>emptyList());
    }
    return copy;
  }

  private PersonnelProfileRuntimeLayoutConfig normalizeLayoutConfig(PersonnelProfileRuntimeLayoutConfig config) {
    PersonnelProfileRuntimeLayoutConfig copy = config == null ? new PersonnelProfileRuntimeLayoutConfig() : config;
    if (StringUtils.isBlank(copy.getTemplateKey())) {
      copy.setTemplateKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    }
    if (copy.getFields().isEmpty()) {
      copy.setFields(PersonnelProfileConfigurationDefaults.defaultFields());
    }
    return copy;
  }

  private Optional<PersonnelProfileFormSchemeEntity> resolvePreferredFormScheme() {
    try {
      if (personnelProfileFormSchemeService != null) {
        Optional<PersonnelProfileFormSchemeEntity> defaultScheme = personnelProfileFormSchemeService.findDefaultScheme(null);
        if (defaultScheme.filter(this::hasUsableSchemas).isPresent()) {
          return defaultScheme;
        }
      }
    } catch (Exception e) {
      LOGGER.warn("人员档案表单方案解析失败，回退到旧运行时配置", e);
    }
    if (legacyPersonnelProfileConfigAdapter != null) {
      return Optional.ofNullable(legacyPersonnelProfileConfigAdapter.buildDefaultScheme(null))
          .filter(this::hasUsableSchemas);
    }
    return Optional.empty();
  }

  private boolean hasUsableSchemas(PersonnelProfileFormSchemeEntity scheme) {
    return scheme != null
        && !scheme.getEditSchema().isEmpty()
        && !scheme.getDetailSchema().isEmpty();
  }
}
