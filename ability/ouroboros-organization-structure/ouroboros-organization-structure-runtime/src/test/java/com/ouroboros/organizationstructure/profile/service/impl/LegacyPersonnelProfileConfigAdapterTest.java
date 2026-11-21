package com.ouroboros.organizationstructure.profile.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.organizationstructure.profile.PersonnelProfileConfigurationKeys;
import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LegacyPersonnelProfileConfigAdapterTest {

  @Test
  void buildDefaultSchemeShouldProjectLegacyConfigIntoUsableSchemas() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.TEMPLATE)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.EDIT_LAYOUT)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT)).thenReturn(Optional.empty());

    LegacyPersonnelProfileConfigAdapter adapter = new LegacyPersonnelProfileConfigAdapter(runtimeConfigurationService);

    PersonnelProfileFormSchemeEntity scheme = adapter.buildDefaultScheme("hr-app");

    assertEquals("hr-app", scheme.getAppName());
    assertTrue(Boolean.TRUE.equals(scheme.getIsDefault()));
    assertFalse(scheme.getCreateSchema().isEmpty());
    assertFalse(scheme.getEditSchema().isEmpty());
    assertFalse(scheme.getDetailSchema().isEmpty());
    assertEquals("APP", scheme.getScope().get("type"));
    assertEquals("runtime-configuration", scheme.getLegacySource().get("source"));
  }

  @Test
  void buildDefaultSchemeWithNullShouldProduceGlobalRuntimeSchemasInsteadOfDevTemplateSchema() {
    ConfigurationService runtimeConfigurationService = mock(ConfigurationService.class);
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.TEMPLATE)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.RUNTIME_OVERRIDE)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.EDIT_LAYOUT)).thenReturn(Optional.empty());
    when(runtimeConfigurationService.get(String.class, PersonnelProfileConfigurationKeys.DISPLAY_LAYOUT)).thenReturn(Optional.empty());

    LegacyPersonnelProfileConfigAdapter adapter = new LegacyPersonnelProfileConfigAdapter(runtimeConfigurationService);

    PersonnelProfileFormSchemeEntity scheme = adapter.buildDefaultScheme(null);

    assertEquals("GLOBAL", scheme.getScope().get("type"));
    assertEquals("form", scheme.getEditSchema().get("type"));
    assertEquals("page", scheme.getDetailSchema().get("type"));
    assertEquals("/api/organization/employees/${id}", apiUrlOf(scheme.getEditSchema(), "initApi"));
    assertEquals("/api/organization/employees/${id}", apiUrlOf(scheme.getEditSchema(), "api"));
    assertEquals("/api/organization/employees/${id}", nestedApiUrlOf(scheme.getDetailSchema()));
    assertEquals("com.ouroboros.uischema.schema.FormItemNode", firstBodyItem(scheme.getEditSchema()).getClass().getName());
    assertEquals("com.ouroboros.uischema.schema.SchemaNode", firstDetailBodyItem(scheme.getDetailSchema()).getClass().getName());
    assertEquals("name", mapOf(firstBodyItem(scheme.getEditSchema())).get("name"));
    assertEquals("name", mapOf(firstDetailBodyItem(scheme.getDetailSchema())).get("name"));
  }

  private String apiUrlOf(Map<String, Object> schema, String key) {
    return String.valueOf(((Map<?, ?>) schema.get(key)).get("url"));
  }

  private String nestedApiUrlOf(Map<String, Object> schema) {
    List<?> pageBody = (List<?>) schema.get("body");
    Map<?, ?> service = (Map<?, ?>) pageBody.get(0);
    return String.valueOf(((Map<?, ?>) service.get("api")).get("url"));
  }

  private Object firstBodyItem(Map<String, Object> schema) {
    List<?> body = (List<?>) schema.get("body");
    return body.get(0);
  }

  private Object firstDetailBodyItem(Map<String, Object> schema) {
    List<?> pageBody = (List<?>) schema.get("body");
    Map<?, ?> service = (Map<?, ?>) pageBody.get(0);
    List<?> serviceBody = (List<?>) service.get("body");
    Map<?, ?> fieldset = (Map<?, ?>) serviceBody.get(0);
    List<?> fieldsetBody = (List<?>) fieldset.get("body");
    return fieldsetBody.get(0);
  }

  private Map<String, Object> mapOf(Object value) {
    return Json.toMap(Json.toJsonString(value));
  }
}
