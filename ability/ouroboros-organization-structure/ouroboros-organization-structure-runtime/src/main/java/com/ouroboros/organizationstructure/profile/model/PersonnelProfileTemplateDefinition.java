package com.ouroboros.organizationstructure.profile.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.exception.ValidationException;

/**
 * 人员档案模板定义
 */
public class PersonnelProfileTemplateDefinition implements Serializable {

  private static final long serialVersionUID = 1L;

  private String key;
  private String label;
  private String description;
  private List<PersonnelProfileFieldDefinition> fields;
  private Map<String, Object> metadata;

  public void validate() {
    if (StringUtils.isBlank(key)) {
      throw new ValidationException("人员档案模板 key 不能为空");
    }
    if (getFields().isEmpty()) {
      throw new ValidationException("人员档案模板字段不能为空");
    }

    Set<String> names = new HashSet<String>();
    for (PersonnelProfileFieldDefinition field : getFields()) {
      if (field == null) {
        throw new ValidationException("人员档案模板字段不能为空");
      }
      if (StringUtils.isBlank(field.getName())) {
        throw new ValidationException("人员档案模板字段名称不能为空");
      }
      if (StringUtils.isBlank(field.getLabel())) {
        throw new ValidationException("人员档案模板字段标签不能为空: " + field.getName());
      }
      if (StringUtils.isBlank(field.getType())) {
        throw new ValidationException("人员档案模板字段类型不能为空: " + field.getName());
      }
      if (!names.add(field.getName())) {
        throw new ValidationException("人员档案模板字段名称重复: " + field.getName());
      }
    }
  }

  public PersonnelProfileTemplateDefinition copy() {
    PersonnelProfileTemplateDefinition copy = new PersonnelProfileTemplateDefinition();
    copy.setKey(key);
    copy.setLabel(label);
    copy.setDescription(description);
    List<PersonnelProfileFieldDefinition> copiedFields = new ArrayList<PersonnelProfileFieldDefinition>();
    for (PersonnelProfileFieldDefinition field : getFields()) {
      copiedFields.add(field == null ? null : field.copy());
    }
    copy.setFields(copiedFields);
    if (metadata != null) {
      copy.setMetadata(new LinkedHashMap<String, Object>(metadata));
    }
    return copy;
  }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public List<PersonnelProfileFieldDefinition> getFields() {
    return fields == null ? Collections.<PersonnelProfileFieldDefinition>emptyList() : fields;
  }

  public void setFields(List<PersonnelProfileFieldDefinition> fields) {
    this.fields = fields;
  }

  public Map<String, Object> getMetadata() {
    return metadata == null ? Collections.<String, Object>emptyMap() : metadata;
  }

  public void setMetadata(Map<String, Object> metadata) {
    this.metadata = metadata;
  }
}
