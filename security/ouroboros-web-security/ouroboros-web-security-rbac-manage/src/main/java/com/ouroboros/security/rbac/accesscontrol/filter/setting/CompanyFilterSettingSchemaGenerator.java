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
public class CompanyFilterSettingSchemaGenerator implements ACFilterSettingSchemaGenerator {

  @Override
  public boolean supports(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var name = accessControlFilterDefine.getName();
    return StringUtils.isNotBlank(name) && "$byCompany".equalsIgnoreCase(name);
  }

  @Override
  public List<SchemaNode> generate(AccessControlDefine accessControlDefine, AccessControlFilterDefine accessControlFilterDefine) {
    var keyPrefix = "accessControlParams.$byCompany.";

    // 当前公司选项开关
    var currentCompany = new FormItemNode();
    currentCompany.setLabel("本公司");
    currentCompany.setType("switch");
    currentCompany.setName(keyPrefix + "currentCompany");

    // 当前公司下属公司选项开关
    var currentChildrenCompany = new FormItemNode();
    currentChildrenCompany.setLabel("含下属公司");
    currentChildrenCompany.setType("switch");
    currentChildrenCompany.setName(keyPrefix + "currentChildrenCompany");
    currentChildrenCompany.setVisibleOn("${" + keyPrefix + "currentCompany" + "}");

    // 选择公司选择框
    var selectCompany = getSelectCompanyNode();

    // 容器
    var fieldSet = new CommonContainer();
    fieldSet.setType("fieldSet");
    fieldSet.setBody(Arrays.asList(currentCompany, currentChildrenCompany, selectCompany));
    fieldSet.setProp("title", "根据公司控制访问范围");
    fieldSet.setClassName("mb-5");
    return Collections.singletonList(fieldSet);
  }

  private static FormItemNode getSelectCompanyNode() {
    var selectCompany = new FormItemNode();
    selectCompany.setLabel("选择公司");
    selectCompany.setType("tree-select");
    selectCompany.setName("accessControlParams.createdByCompany");
    selectCompany.setPlaceholder("请选择公司");
    selectCompany.setProp("autoCheckChildren", false);
    selectCompany.setProp("multiple", true);
    selectCompany.setProp("clearable", true);
    selectCompany.setProp("extractValue", true);
    selectCompany.setProp("joinValues", false);
    selectCompany.setProp("labelField", "name");
    selectCompany.setProp("valueField", "id");
    selectCompany.setProp("maxTagCount", 5);
    selectCompany.setProp("source", "get:/api/organization-structure/tree?types=company");
    return selectCompany;
  }
}
