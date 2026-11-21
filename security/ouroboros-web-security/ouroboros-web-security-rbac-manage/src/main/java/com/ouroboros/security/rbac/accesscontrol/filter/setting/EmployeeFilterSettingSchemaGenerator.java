package com.ouroboros.security.rbac.accesscontrol.filter.setting;

import java.util.Collections;
import java.util.List;

import jakarta.annotation.Priority;

import org.apache.commons.lang3.StringUtils;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;
import com.ouroboros.security.authority.accesscontrol.setting.ACFilterSettingSchemaGenerator;
import com.ouroboros.uischema.schema.CommonContainer;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;

@Priority(-1)
public class EmployeeFilterSettingSchemaGenerator implements ACFilterSettingSchemaGenerator {

  @Override
  public boolean supports(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var name = accessControlFilterDefine.getName();
    return StringUtils.isNotBlank(name) && "$byEmployee".equalsIgnoreCase(name);
  }

  @Override
  public List<SchemaNode> generate(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var keyPrefix = "accessControlParams.$byEmployee.";
    var currentEmployee = new FormItemNode();
    currentEmployee.setLabel("本人");
    currentEmployee.setType("switch");
    currentEmployee.setName(keyPrefix + "currentEmployee");
    var fieldSet = new CommonContainer();
    fieldSet.setType("fieldSet");
    fieldSet.setBody(Collections.singletonList(currentEmployee));
    fieldSet.setProp("title", "根据员工控制访问范围");
    fieldSet.setClassName("mb-5");
    return Collections.singletonList(fieldSet);
  }
}
