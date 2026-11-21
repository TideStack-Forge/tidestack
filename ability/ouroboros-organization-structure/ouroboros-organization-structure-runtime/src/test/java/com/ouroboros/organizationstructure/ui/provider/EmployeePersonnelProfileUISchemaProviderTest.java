package com.ouroboros.organizationstructure.ui.provider;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileRuntimeConfigService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmployeePersonnelProfileUISchemaProviderTest {

  @Test
  void providerShouldPreferResolvedSchemeSchemas() {
    try (var context = new org.springframework.context.support.GenericApplicationContext()) {
      context.registerBean(com.ouroboros.common.app.util.SpringContextUtil.class);
      context.registerBean(PersonnelProfileRuntimeConfigService.class, StubRuntimeConfigService::new);
      context.refresh();
    EmployeePersonnelProfileUISchemaProvider provider = new EmployeePersonnelProfileUISchemaProvider();

    Map<?, ?> editSchema = cast(provider.getUISchema("/system-setting/organization-management/edit-employee").orElse(null));
    Map<?, ?> detailSchema = cast(provider.getUISchema("/system-setting/organization-management/employee-detail").orElse(null));

    assertEquals("scheme-edit", editSchema.get("marker"));
    assertEquals("scheme-detail", detailSchema.get("marker"));
    assertTrue(provider.getUISchemaMap().containsKey("/system-setting/organization-management/edit-employee"));
    }
  }

  private Map<?, ?> cast(Object value) {
    return (Map<?, ?>) value;
  }

  private static final class StubRuntimeConfigService implements PersonnelProfileRuntimeConfigService {
    @Override
    public Map<String, Object> getResolvedEditSchema() {
      return schema("scheme-edit");
    }

    @Override
    public Map<String, Object> getResolvedDetailSchema() {
      return schema("scheme-detail");
    }

    @Override
    public PersonnelProfileTemplateDefinition getTemplateDefinition() {
      return null;
    }

    @Override
    public PersonnelProfileRuntimeOverrideConfig getRuntimeOverrideConfig() {
      return null;
    }

    @Override
    public PersonnelProfileRuntimeLayoutConfig getRuntimeLayoutConfig() {
      return null;
    }

    @Override
    public PersonnelProfileRuntimeLayoutConfig getDisplayLayoutConfig() {
      return null;
    }

    @Override
    public PersonnelProfileTemplateDefinition getResolvedTemplateDefinition() {
      return null;
    }

    @Override
    public PersonnelProfileTemplateDefinition getResolvedTemplateDefinition(boolean editMode) {
      return null;
    }

    @Override
    public void saveTemplateDefinition(PersonnelProfileTemplateDefinition definition, String updateBy) {
    }

    @Override
    public void saveRuntimeOverrideConfig(PersonnelProfileRuntimeOverrideConfig definition, String updateBy) {
    }

    @Override
    public void saveRuntimeLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy) {
    }

    @Override
    public void saveDisplayLayoutConfig(PersonnelProfileRuntimeLayoutConfig definition, String updateBy) {
    }

    private Map<String, Object> schema(String marker) {
      Map<String, Object> schema = new LinkedHashMap<String, Object>();
      schema.put("type", "page");
      schema.put("marker", marker);
      return schema;
    }
  }
}
