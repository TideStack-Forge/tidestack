package com.ouroboros.security.rbac.accesscontrol.setting;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.setting.ACSettingSchemaGenerator;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;

public class FieldControlSettingSchemaGenerator implements ACSettingSchemaGenerator {
  @Override
  public boolean supports(AccessControlDefine accessControlDefine) {
    return Optional.ofNullable(accessControlDefine.getCanControlFields())
        .orElse(false);
  }

  @Override
  public List<SchemaNode> generate(AccessControlDefine accessControlDefine) {
    var fieldName = "accessControlParams.$fieldControl";
    var fieldControl = new FormItemNode();
    fieldControl.setLabel("可访问字段");
    fieldControl.setType("select");
    fieldControl.setName(fieldName);
    fieldControl.setProp("multiple", true);
    fieldControl.setProp("clearable", true);
    fieldControl.setProp("extractValue", true);
    fieldControl.setProp("joinValues", false);
    fieldControl.setProp("labelField", "label");
    fieldControl.setProp("valueField", "name");
    fieldControl.setProp("options", accessControlDefine.getControlledFields());
    fieldControl.setProp("maxTagCount", 5);
    return Collections.singletonList(fieldControl);
  }
}
