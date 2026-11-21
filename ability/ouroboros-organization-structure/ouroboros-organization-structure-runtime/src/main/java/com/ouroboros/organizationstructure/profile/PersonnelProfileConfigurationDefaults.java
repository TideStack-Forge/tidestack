package com.ouroboros.organizationstructure.profile;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldDefinition;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileFieldOverride;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeLayoutConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileRuntimeOverrideConfig;
import com.ouroboros.organizationstructure.profile.model.PersonnelProfileTemplateDefinition;

/**
 * 人员档案配置默认值
 */
public final class PersonnelProfileConfigurationDefaults {

  public static final String DEFAULT_TEMPLATE_KEY = "personnel-profile";

  private PersonnelProfileConfigurationDefaults() {
  }

  public static PersonnelProfileTemplateDefinition defaultTemplateDefinition() {
    PersonnelProfileTemplateDefinition definition = new PersonnelProfileTemplateDefinition();
    definition.setKey(DEFAULT_TEMPLATE_KEY);
    definition.setLabel("人员档案模板");
    definition.setFields(defaultFields());
    return definition;
  }

  public static PersonnelProfileRuntimeOverrideConfig defaultOverrideConfig() {
    PersonnelProfileRuntimeOverrideConfig config = new PersonnelProfileRuntimeOverrideConfig();
    config.setTemplateKey(DEFAULT_TEMPLATE_KEY);
    config.setFieldOverrides(Collections.<PersonnelProfileFieldOverride>emptyList());
    return config;
  }

  public static PersonnelProfileRuntimeLayoutConfig defaultLayoutConfig() {
    return defaultEditLayoutConfig();
  }

  public static PersonnelProfileRuntimeLayoutConfig defaultEditLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = new PersonnelProfileRuntimeLayoutConfig();
    config.setTemplateKey(DEFAULT_TEMPLATE_KEY);
    config.setFields(defaultFields());
    return config;
  }

  public static PersonnelProfileRuntimeLayoutConfig defaultDisplayLayoutConfig() {
    PersonnelProfileRuntimeLayoutConfig config = new PersonnelProfileRuntimeLayoutConfig();
    config.setTemplateKey(DEFAULT_TEMPLATE_KEY);
    config.setFields(defaultFields());
    return config;
  }

  public static List<PersonnelProfileFieldDefinition> defaultFields() {
    List<PersonnelProfileFieldDefinition> fields = new ArrayList<PersonnelProfileFieldDefinition>();
    fields.add(field("name", "姓名", "input-text", 10, true, true, params("placeholder", "请输入姓名", "clearable", Boolean.TRUE)));
    fields.add(field("gender", "性别", "radios", 20, false, true, params(
        "options", Arrays.asList(option("男", "男"), option("女", "女")))));
    fields.add(field("mobile", "手机", "input-text", 30, false, false, params("placeholder", "请输入手机号", "clearable", Boolean.TRUE)));
    fields.add(field("email", "邮箱", "input-text", 40, false, false, params("placeholder", "请输入邮箱", "clearable", Boolean.TRUE)));
    fields.add(field("birthday", "生日", "input-date", 50, false, false, params("format", "YYYY-MM-DD", "placeholder", "请选择生日")));
    return fields;
  }

  private static PersonnelProfileFieldDefinition field(String name,
                                                       String label,
                                                       String type,
                                                       Integer order,
                                                       boolean core,
                                                       boolean required,
                                                       Map<String, Object> params) {
    PersonnelProfileFieldDefinition definition = new PersonnelProfileFieldDefinition();
    definition.setName(name);
    definition.setLabel(label);
    definition.setType(type);
    definition.setOrder(order);
    definition.setRequired(required);
    definition.setCore(core);
    definition.setHidden(false);
    definition.setParams(params);
    return definition;
  }

  private static Map<String, Object> params(Object... entries) {
    Map<String, Object> params = new LinkedHashMap<String, Object>();
    for (int i = 0; i < entries.length; i += 2) {
      params.put(String.valueOf(entries[i]), entries[i + 1]);
    }
    return params;
  }

  private static Map<String, Object> option(String label, String value) {
    Map<String, Object> option = new HashMap<String, Object>();
    option.put("label", label);
    option.put("value", value);
    return option;
  }
}
