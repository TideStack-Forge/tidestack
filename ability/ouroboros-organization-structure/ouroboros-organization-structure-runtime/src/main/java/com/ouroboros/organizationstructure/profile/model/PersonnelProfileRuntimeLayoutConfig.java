package com.ouroboros.organizationstructure.profile.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 人员档案运行时布局配置
 */
public class PersonnelProfileRuntimeLayoutConfig implements Serializable {

  private static final long serialVersionUID = 1L;

  private String templateKey;
  private List<PersonnelProfileFieldDefinition> fields;

  public List<PersonnelProfileFieldDefinition> applyTo(List<PersonnelProfileFieldDefinition> fields) {
    if (fields == null || fields.isEmpty()) {
      return Collections.emptyList();
    }

    Map<String, Integer> orderIndex = new HashMap<String, Integer>();
    Map<String, PersonnelProfileFieldDefinition> layoutFieldMap = new HashMap<String, PersonnelProfileFieldDefinition>();
    List<PersonnelProfileFieldDefinition> configuredFields = getFields();
    for (int i = 0; i < configuredFields.size(); i++) {
      PersonnelProfileFieldDefinition field = configuredFields.get(i);
      if (field == null || field.getName() == null) {
        continue;
      }
      layoutFieldMap.put(field.getName(), field);
      orderIndex.put(field.getName(), i);
    }

    List<PersonnelProfileFieldDefinition> orderedFields = new ArrayList<PersonnelProfileFieldDefinition>();
    for (PersonnelProfileFieldDefinition field : fields) {
      if (field != null) {
        PersonnelProfileFieldDefinition configuredField = layoutFieldMap.get(field.getName());
        PersonnelProfileFieldDefinition mergedField = field.copy();
        if (configuredField != null) {
          if (configuredField.getOrder() != null) {
            mergedField.setOrder(configuredField.getOrder());
          }
          if (configuredField.getHidden() != null) {
            mergedField.setHidden(configuredField.getHidden());
          }
        }
        if (!Boolean.TRUE.equals(mergedField.getHidden())) {
          orderedFields.add(mergedField);
        }
      }
    }

    if (orderedFields.isEmpty()) {
      return fields;
    }

    Collections.sort(orderedFields, Comparator
        .comparing((PersonnelProfileFieldDefinition field) -> orderIndex.containsKey(field.getName()) ? orderIndex.get(field.getName()) : Integer.MAX_VALUE)
        .thenComparing((PersonnelProfileFieldDefinition field) -> field.getOrder() == null ? Integer.MAX_VALUE : field.getOrder())
        .thenComparing(PersonnelProfileFieldDefinition::getName, String.CASE_INSENSITIVE_ORDER));
    return orderedFields;
  }

  public String getTemplateKey() {
    return templateKey;
  }

  public void setTemplateKey(String templateKey) {
    this.templateKey = templateKey;
  }

  public List<PersonnelProfileFieldDefinition> getFields() {
    return fields == null ? Collections.<PersonnelProfileFieldDefinition>emptyList() : fields;
  }

  public void setFields(List<PersonnelProfileFieldDefinition> fields) {
    this.fields = fields;
  }
}
