package com.ouroboros.security.rbac.accesscontrol.filter.setting;

import java.util.Arrays;
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
public class DepartmentFilterSettingSchemaGenerator implements ACFilterSettingSchemaGenerator {

  @Override
  public boolean supports(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var name = accessControlFilterDefine.getName();
    return StringUtils.isNotBlank(name) && "$byDepartment".equalsIgnoreCase(name);
  }

  @Override
  public List<SchemaNode> generate(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var keyPrefix = "accessControlParams.$byDepartment.";
    var currentDepartment = new FormItemNode();
    currentDepartment.setLabel("本部门");
    currentDepartment.setType("switch");
    currentDepartment.setName(keyPrefix + "currentDepartment");

    var currentChildrenDepartment = new FormItemNode();
    currentChildrenDepartment.setLabel("含下属部门");
    currentChildrenDepartment.setType("switch");
    currentChildrenDepartment.setName(keyPrefix + "currentChildrenDepartment");
    currentChildrenDepartment.setVisibleOn("${" + keyPrefix + "currentDepartment" + "}");
    var selectDepartment = getSelectDepartmentNode();

    var fieldSet = new CommonContainer();
    fieldSet.setType("fieldSet");
    fieldSet.setBody(Arrays.asList(currentDepartment, currentChildrenDepartment, selectDepartment));
    fieldSet.setProp("title", "根据部门控制访问范围");
    fieldSet.setClassName("mb-5");
    return Collections.singletonList(fieldSet);
  }

  private static FormItemNode getSelectDepartmentNode() {
    var selectDepartment = new FormItemNode();
    selectDepartment.setLabel("选择部门");
    selectDepartment.setType("tree-select");
    selectDepartment.setName("accessControlParams.createdByDepartment");
    selectDepartment.setPlaceholder("请选择部门");
    selectDepartment.setProp("multiple", true);
    selectDepartment.setProp("clearable", true);
    selectDepartment.setProp("extractValue", true);
    selectDepartment.setProp("joinValues", false);
    selectDepartment.setProp("labelField", "name");
    selectDepartment.setProp("valueField", "id");
    selectDepartment.setProp("maxTagCount", 5);
    selectDepartment.setProp("source", "get:/api/organization-structure/tree?types=company,department&selectable=department");
    return selectDepartment;
  }
}
