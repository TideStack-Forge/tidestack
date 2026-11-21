package com.ouroboros.organizationstructure.profile.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员档案运行时字段覆盖配置
 */
public class PersonnelProfileRuntimeOverrideConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  private String templateKey;
  private List<PersonnelProfileFieldOverride> fieldOverrides;

  public List<PersonnelProfileFieldDefinition> applyTo(PersonnelProfileTemplateDefinition template) {
    if (template == null) {
      return Collections.emptyList();
    }

    Map<String, PersonnelProfileFieldOverride> overrideMap = new HashMap<String, PersonnelProfileFieldOverride>();
    for (PersonnelProfileFieldOverride fieldOverride : getFieldOverrides()) {
      if (fieldOverride != null && fieldOverride.getName() != null) {
        overrideMap.put(fieldOverride.getName(), fieldOverride);
      }
    }

    List<PersonnelProfileFieldDefinition> mergedFields = new ArrayList<PersonnelProfileFieldDefinition>();
    for (PersonnelProfileFieldDefinition field : template.getFields()) {
      if (field == null) {
        continue;
      }
      PersonnelProfileFieldDefinition mergedField = field.merge(overrideMap.get(field.getName()));
      if (!Boolean.TRUE.equals(mergedField.getHidden())) {
        mergedFields.add(mergedField);
      }
    }

    Collections.sort(mergedFields, Comparator
        .comparing((PersonnelProfileFieldDefinition field) -> field.getOrder() == null ? Integer.MAX_VALUE : field.getOrder())
        .thenComparing(PersonnelProfileFieldDefinition::getName, String.CASE_INSENSITIVE_ORDER));
    return mergedFields;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public List<PersonnelProfileFieldOverride> getFieldOverrides() {
    return fieldOverrides == null ? Collections.<PersonnelProfileFieldOverride>emptyList() : fieldOverrides;
  }

  public void setFieldOverrides(List<PersonnelProfileFieldOverride> fieldOverrides) {
    this.fieldOverrides = fieldOverrides;
  }
}
