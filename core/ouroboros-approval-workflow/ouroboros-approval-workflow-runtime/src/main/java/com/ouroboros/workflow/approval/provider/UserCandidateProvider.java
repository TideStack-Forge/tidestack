package com.ouroboros.workflow.approval.provider;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.*;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Trees;
import com.ouroboros.workflow.approval.CandidateProvider;

@Component
@Order(100)
public class UserCandidateProvider implements CandidateProvider {

  // TODO: 这俩待完善设计后再替换
  @Autowired
  private EmployeeAggregator employeeAggregator;

  @Autowired
  private OrganizationAggregator orgNodeAggregator;

  @Override
  public String getName() {
    return "User";
  }

  @Override
  public String getLabel() {
    return "用户";
  }

  @Override
  public SchemaNode getSchema() {
    return RuntimeCandidateSchemaFactory.buildDynamicUserPicker(getValueKey(), getLabel(), getMin(), getMax());
  }

  @Override
  public String getValueKey() {
    return "users";
  }

  @Override
  public ConfigMode getConfigMode() {
    return ConfigMode.Pick;
  }

  @Override
  public String getCategoryLabel() {
    return "部门";
  }

  @Override
  public Integer getMax() {
    return 5;
  }

  @Override
  public List<CandidateItem> getItems() {
    return employeeAggregator.getAll().stream()
        .filter(employee -> StringUtils.isNotBlank(employee.getUser()))
        .map(this::employeeToCandidateItem)
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(String category, String keyword) {
    return employeeAggregator.getAll().stream()
        .filter(employee -> StringUtils.isNotBlank(employee.getUser()))
        .filter(employee -> ObjectUtils.isEmpty(category) || (Objects.equals(employee.getDepartment(), category)))
        .filter(employee -> ObjectUtils.isEmpty(keyword)
            || employee.getName().contains(keyword)
            || StringUtils.containsIgnoreCase(employee.getUser(), keyword))
        .map(this::employeeToCandidateItem)
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateItem> search(List<String> valueList) {
    if (ObjectUtils.isEmpty(valueList)) {
      return List.of();
    }
    Map<String, CandidateItem> itemsByValue = getItems().stream()
        .filter(item -> item.getValue() != null)
        .collect(Collectors.toMap(item -> String.valueOf(item.getValue()), item -> item, (existing, duplicate) -> existing));
    return valueList.stream()
        .map(value -> itemsByValue.getOrDefault(value, unknownCandidateItem(value)))
        .collect(Collectors.toList());
  }

  @Override
  public List<CandidateCategory> getCategories() {
    String currentCompanyId = getCurrentCompanyId();
    List<OrganizationNode> nodes = orgNodeAggregator.getAll().stream()
        .filter(node -> node instanceof Company && StringUtils.equals(node.getId(), currentCompanyId)
            || node instanceof Department && Optional.ofNullable(((Department) node).getCompanyDetail())
            .map(company -> StringUtils.equals(company.getId(), currentCompanyId))
            .orElse(false))
        .collect(Collectors.toList());

    return Trees.buildTree(
        nodes.stream()
            .filter(item -> Objects.equals(item.getParent(), currentCompanyId))
            .collect(Collectors.toList()),
        (node) -> nodes.stream()
            .filter(item -> Objects.equals(node.getId(), item.getParent()))
            .collect(Collectors.toList()),
        this::organizationNodeToCandidateCategory
    );
  }

  private String getCurrentCompanyId() {
    return OrganizationContextHolder.getContext()
        .flatMap(OrganizationContext::getCompany)
        .map(OrganizationNode::getId)
        .orElse(null);
  }

  private CandidateItem employeeToCandidateItem(Employee employee) {
    CandidateItem item = new CandidateItem();
    item.setLabel(employee.getName());
    item.setValue(employee.getUser());
    return item;
  }

  private CandidateItem unknownCandidateItem(String value) {
    CandidateItem item = new CandidateItem();
    item.setValue(value);
    item.setLabel("未知用户（" + value + "）");
    return item;
  }

  private CandidateCategory organizationNodeToCandidateCategory(OrganizationNode node,
                                                                List<CandidateCategory> children) {
    CandidateCategory category = new CandidateCategory();
    category.setLabel(node.getName());
    category.setValue(node.getId());
    category.setChildren(children);
    return category;
  }
}
