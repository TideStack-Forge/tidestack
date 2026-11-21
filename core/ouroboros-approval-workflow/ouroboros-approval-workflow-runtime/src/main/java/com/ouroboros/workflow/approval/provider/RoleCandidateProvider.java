package com.ouroboros.workflow.approval.provider;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(200)
public class RoleCandidateProvider implements CandidateProvider {

  @Autowired
  RoleCenter roleCenter;

  @Override
  public String getName() {
    return "Role";
  }

  @Override
  public String getLabel() {
    return "角色";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildMultiSelect(getValueKey(), getLabel(), getMin(), getMax());
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.MultiSelect;
  }

  @Override
  public String getValueKey() {
    return "roles";
  }

  @Override
  public List<CandidateItem> getItems() {
    return RoleCenter.instance().getAllRoles().stream()
        .map(this::roleToCandidateItem)
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(String category, String keyword) {
    return getItems().stream()
        .filter(item -> ObjectUtils.isEmpty(keyword) || item.getLabel().contains(keyword))
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(List<String> valueList) {
    return getItems().stream()
        .filter(item -> ObjectUtils.isEmpty(valueList) || valueList.contains(item.getValue()))
        .collect(Collectors.toList());
  }

  private CandidateItem roleToCandidateItem(Role role) {
    CandidateItem item = new CandidateItem();
    item.setLabel(role.getName());
    item.setValue(role.getId());
    return item;
  }
}
