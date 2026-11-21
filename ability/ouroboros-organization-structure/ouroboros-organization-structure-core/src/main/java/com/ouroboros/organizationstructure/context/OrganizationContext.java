package com.ouroboros.organizationstructure.context;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;

/**
 * @author liansz
 */
public interface OrganizationContext {

  /**
   * 员工
   */
  Optional<Employee> getEmployee();

  /**
   * 员工所在部门
   */
  Optional<Department> getDepartment();

  /**
   * 员工所在公司
   */
  Optional<Company> getCompany();

  List<OrganizationNode> getOrgChain();

  default List<String> getOrgChainIds() {
    return getOrgChain()
        .stream()
        .map(OrganizationNode::getId)
        .collect(Collectors.toList());
  }

  /**
   * 按行政级别获取组织节点（从当前位置向上查找最近的匹配节点）
   *
   * @param adminLevel 行政级别，如 AdminLevel.DEPARTMENT
   * @return 匹配的组织节点
   */
  default Optional<OrganizationNode> getByAdminLevel(String adminLevel) {
    return getOrgChain().stream()
        .filter(node -> Objects.equals(adminLevel, node.getAdminLevel()))
        .findFirst();
  }

  /**
   * 获取从指定行政级别开始往上的组织链路
   *
   * @param adminLevel 行政级别
   * @return 从指定级别到公司的链路，如果未找到则返回空列表
   */
  default List<OrganizationNode> getOrgChainFromLevel(String adminLevel) {
    List<OrganizationNode> chain = getOrgChain();
    for (int i = 0; i < chain.size(); i++) {
      if (Objects.equals(adminLevel, chain.get(i).getAdminLevel())) {
        return new ArrayList<>(chain.subList(i, chain.size()));
      }
    }
    return new ArrayList<>();
  }
}
