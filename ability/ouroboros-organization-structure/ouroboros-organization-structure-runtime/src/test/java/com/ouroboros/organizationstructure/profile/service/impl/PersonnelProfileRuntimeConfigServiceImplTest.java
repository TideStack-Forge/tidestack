package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationDefaults;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationKeys;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldDefinition;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldOverride;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonnelProfileRuntimeConfigServiceImplTest {

  @Test
  void saveTemplateDefinitionShouldPersistJsonStringThroughConfigurationService() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);

    PersonnelProfileTemplateDefinition definition = PersonnelProfileConfigurationDefaults.defaultTemplateDefinition();
    definition.setMetadata(metadata("version", "1"));

    service.saveTemplateDefinition(definition, "operator-1");

    ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(configurationManagementService).update(payloadCaptor.capture(), eq("operator-1"));
    assertTrue(payloadCaptor.getValue().containsKey(PersonnelProfileConfigurationKeys.TEMPLATE));
    assertEquals(Json.toJsonString(definition), payloadCaptor.getValue().get(PersonnelProfileConfigurationKeys.TEMPLATE));
  }

  @Test
  void getResolvedTemplateDefinitionShouldApplyOverridesAndKeepConfiguredOrder() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);

    PersonnelProfileTemplateDefinition templateDefinition = new PersonnelProfileTemplateDefinition();
    templateDefinition.setKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    templateDefinition.setLabel("人员档案模板");
    templateDefinition.setFields(Arrays.asList(
        field("name", "姓名", "String", 20),
        field("gender", "性别", "String", 20),
        field("mobile", "手机号", "String", 10)));

    PersonnelProfileRuntimeOverrideConfig overrideConfig = new PersonnelProfileRuntimeOverrideConfig();
    overrideConfig.setTemplateKey(templateDefinition.getKey());
    PersonnelProfileFieldOverride nameOverride = new PersonnelProfileFieldOverride();
    nameOverride.setName("name");
    nameOverride.setLabel("员工姓名");
    nameOverride.setOrder(30);
    nameOverride.setRequired(Boolean.TRUE);
    overrideConfig.setFieldOverrides(Arrays.asList(nameOverride));

    PersonnelProfileRuntimeLayoutConfig layoutConfig = new PersonnelProfileRuntimeLayoutConfig();
    layoutConfig.setTemplateKey(templateDefinition.getKey());
    layoutConfig.setFields(Arrays.asList(
        field("mobile", "手机号", "String", 10),
        field("gender", "性别", "String", 20),
        field("name", "姓名", "String", 30)));

    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.TEMPLATE))
        .thenReturn(Optional.of(Json.toJsonString(templateDefinition)));
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE))
        .thenReturn(Optional.of(Json.toJsonString(overrideConfig)));
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.EDIT_LAYOUT))
        .thenReturn(Optional.of(Json.toJsonString(layoutConfig)));
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT))
        .thenReturn(Optional.of(Json.toJsonString(layoutConfig)));

    PersonnelProfileTemplateDefinition resolved = service.getResolvedTemplateDefinition(true);

    assertEquals(Arrays.asList("mobile", "gender", "name"), Arrays.asList(
        resolved.getFields().get(0).getName(),
        resolved.getFields().get(1).getName(),
        resolved.getFields().get(2).getName()));
    assertEquals("员工姓名", resolved.getFields().get(2).getLabel());
    assertEquals(Boolean.TRUE, resolved.getFields().get(2).getRequired());

    PersonnelProfileTemplateDefinition displayResolved = service.getResolvedTemplateDefinition(false);
    assertEquals(Arrays.asList("mobile", "gender", "name"), Arrays.asList(
        displayResolved.getFields().get(0).getName(),
        displayResolved.getFields().get(1).getName(),
        displayResolved.getFields().get(2).getName()));
  }

  @Test
  void saveRuntimeLayoutConfigShouldPersistStructuredFieldDefinitions() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);

    PersonnelProfileRuntimeLayoutConfig layoutConfig = new PersonnelProfileRuntimeLayoutConfig();
    layoutConfig.setTemplateKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    layoutConfig.setFields(Arrays.asList(
        field("name", "姓名", "String", 10),
        field("mobile", "手机号", "String", 20)));

    service.saveRuntimeLayoutConfig(layoutConfig, "operator-2");

    ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(configurationManagementService).update(payloadCaptor.capture(), eq("operator-2"));
    assertTrue(payloadCaptor.getValue().containsKey(PersonnelProfileConfigurationKeys.EDIT_LAYOUT));
    assertEquals(Json.toJsonString(layoutConfig), payloadCaptor.getValue().get(PersonnelProfileConfigurationKeys.EDIT_LAYOUT));
  }

  @Test
  void saveDisplayLayoutConfigShouldPersistStructuredFieldDefinitions() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);

    PersonnelProfileRuntimeLayoutConfig layoutConfig = new PersonnelProfileRuntimeLayoutConfig();
    layoutConfig.setTemplateKey(PersonnelProfileConfigurationDefaults.DEFAULT_TEMPLATE_KEY);
    layoutConfig.setFields(Arrays.asList(
        field("gender", "性别", "radios", 10),
        field("name", "姓名", "input-text", 20)));

    service.saveDisplayLayoutConfig(layoutConfig, "operator-3");

    ArgumentCaptor<Map> payloadCaptor = ArgumentCaptor.forClass(Map.class);
    verify(configurationManagementService).update(payloadCaptor.capture(), eq("operator-3"));
    assertTrue(payloadCaptor.getValue().containsKey(PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT));
    assertEquals(Json.toJsonString(layoutConfig), payloadCaptor.getValue().get(PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT));
  }

  @Test
  void resolvedSchemasShouldPreferDefaultSchemeWhenAvailable() throws Exception {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);
    PersonnelProfileFormSchemeService schemeService = mock(PersonnelProfileFormSchemeService.class);

    PersonnelProfileFormSchemeEntity scheme = new PersonnelProfileFormSchemeEntity();
    scheme.setEditSchema(schema("edit"));
    scheme.setDetailSchema(schema("detail"));

    when(schemeService.findDefaultScheme(null)).thenReturn(Optional.of(scheme));
    injectField(service, "personnelProfileFormSchemeService", schemeService);

    assertEquals("edit", service.getResolvedEditSchema().get("marker"));
    assertEquals("detail", service.getResolvedDetailSchema().get("marker"));
  }

  @Test
  void resolvedSchemasShouldFallbackToLegacyAdapterWhenNoSchemeExists() throws Exception {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);
    PersonnelProfileFormSchemeService schemeService = mock(PersonnelProfileFormSchemeService.class);
    LegacyPersonnelProfileConfigAdapter adapter = mock(LegacyPersonnelProfileConfigAdapter.class);

    when(schemeService.findDefaultScheme(null)).thenReturn(Optional.empty());
    injectField(service, "personnelProfileFormSchemeService", schemeService);

    PersonnelProfileFormSchemeEntity scheme = new PersonnelProfileFormSchemeEntity();
    scheme.setEditSchema(schema("legacy-edit"));
    scheme.setDetailSchema(schema("legacy-detail"));

    when(adapter.buildDefaultScheme(null)).thenReturn(scheme);
    injectField(service, "legacyPersonnelProfileConfigAdapter", adapter);

    assertEquals("legacy-edit", service.getResolvedEditSchema().get("marker"));
    assertEquals("legacy-detail", service.getResolvedDetailSchema().get("marker"));
    assertFalse(service.getResolvedEditSchema().isEmpty());
  }

  @Test
  void resolvedSchemasShouldIgnoreEmptyLegacyScheme() throws Exception {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    ConfigurationManagementService configurationManagementService = mock(ConfigurationManagementService.class);
    PersonnelProfileRuntimeConfigServiceImpl service = new PersonnelProfileRuntimeConfigServiceImpl(runtimeConfigurationService, configurationManagementService);
    LegacyPersonnelProfileConfigAdapter adapter = mock(LegacyPersonnelProfileConfigAdapter.class);

    PersonnelProfileFormSchemeEntity emptyLegacyScheme = new PersonnelProfileFormSchemeEntity();
    emptyLegacyScheme.setEditSchema(new LinkedHashMap<String, Object>());
    emptyLegacyScheme.setDetailSchema(new LinkedHashMap<String, Object>());

    when(adapter.buildDefaultScheme(null)).thenReturn(emptyLegacyScheme);
    injectField(service, "legacyPersonnelProfileConfigAdapter", adapter);

    assertTrue(service.getResolvedEditSchema().isEmpty());
    assertTrue(service.getResolvedDetailSchema().isEmpty());
  }

  private PersonnelProfileFieldDefinition field(String name, String label, String type, Integer order) {
    PersonnelProfileFieldDefinition field = new PersonnelProfileFieldDefinition();
    field.setName(name);
    field.setLabel(label);
    field.setType(type);
    field.setOrder(order);
    return field;
  }

  private Map<String, Object> metadata(String key, Object value) {
    Map<String, Object> metadata = new LinkedHashMap<String, Object>();
    metadata.put(key, value);
    return metadata;
  }

  private Map<String, Object> schema(String marker) {
    Map<String, Object> schema = new LinkedHashMap<String, Object>();
    schema.put("type", "page");
    schema.put("marker", marker);
    return schema;
  }

  private void injectField(Object target, String name, Object value) throws Exception {
    java.lang.reflect.Field field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
