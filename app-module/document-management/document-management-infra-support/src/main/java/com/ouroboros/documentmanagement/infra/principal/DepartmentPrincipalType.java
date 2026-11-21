package com.ouroboros.documentmanagement.infra.principal;

import com.ouroboros.common.app.util.SpringContextUtil;
import com.ouroboros.documentmanagement.model.acl.PrincipalResolver;
import com.ouroboros.documentmanagement.model.acl.PrincipalType;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.uischema.schema.FormItemNode;
import com.ouroboros.uischema.schema.SchemaNode;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 部门主体类型
 *
 * <p>表示部门的主体类型，将部门解析为该部门下所有员工的用户ID列表。
 */
public class DepartmentPrincipalType implements PrincipalType {
  private final PrincipalResolver resolver = new DepartmentPrincipalResolver();

  @Override
  public String getName() {
    return "DEPARTMENT";
  }

  @Override
  public String getDisplayName() {
    return "部门";
  }

  @Override
  public PrincipalResolver getResolver() {
    return resolver;
  }

  @Override
  public Optional<SchemaNode> getConfigUISchema() {
    FormItemNode node = new FormItemNode();
    node.setType("organization-picker");
    node.setLabel("部门");
    node.setProp("pickCompany", false);
    node.setProp("pickDepartment", true);
    return Optional.of(node);
  }

  /**
   * 部门 Principal 解析器
   */
  private static class DepartmentPrincipalResolver implements PrincipalResolver {
    private EmployeeAggregator employeeAggregator;
    private OrganizationAggregator organizationAggregator;

    private EmployeeAggregator getEmployeeAggregator() {
      if (employeeAggregator == null) {
        employeeAggregator = SpringContextUtil.getBean(EmployeeAggregator.class);
      }
      return employeeAggregator;
    }

    private OrganizationAggregator getOrganizationAggregator() {
      if (organizationAggregator == null) {
        organizationAggregator = SpringContextUtil.getBean(OrganizationAggregator.class);
      }
      return organizationAggregator;
    }

    @Override
    public List<String> expandToUserIds(String principal) {
      if (principal == null || principal.isEmpty()) {
        return Collections.emptyList();
      }

      // 从 EmployeeAggregator 获取指定部门的所有人员
      var employees = getEmployeeAggregator().getByDepartment(principal);

      // 从人员的user属性中取值，过滤掉空值再去重
      return employees.stream()
          .map(Employee::getUser)
          .filter(user -> user != null && !user.isEmpty())
          .distinct()
          .collect(Collectors.toList());
    }

    @Override
    public boolean containsUser(String principal, String userId) {
      if (principal == null || userId == null) {
        return false;
      }

      // 从 EmployeeAggregator 获取部门所有人员进行判断
      var employees = getEmployeeAggregator().getByDepartment(principal);
      return employees.stream()
          .anyMatch(employee -> userId.equals(employee.getUser()));
    }

    @Override
    public List<String> getPrincipalsForUser(String userId) {
      if (userId == null || userId.isEmpty()) {
        return Collections.emptyList();
      }

      // 使用 EmployeeAggregator 获取全部人员后再查找 user 为指定值的，取其 department
      var allEmployees = getEmployeeAggregator().getAll();
      return allEmployees.stream()
          .filter(employee -> userId.equals(employee.getUser()))
          .map(Employee::getDepartment)
          .filter(department -> department != null && !department.isEmpty())
          .distinct()
          .collect(Collectors.toList());
    }

    @Override
    public String getDisplayName(String principal) {
      if (principal == null || principal.isEmpty()) {
        return principal;
      }

      // 通过 OrganizationAggregator 获取当前department节点，然后取其name
      var departmentNode = getOrganizationAggregator().getById(principal);
      return departmentNode.map(OrganizationNode::getName).orElse(principal);
    }
  }
}
