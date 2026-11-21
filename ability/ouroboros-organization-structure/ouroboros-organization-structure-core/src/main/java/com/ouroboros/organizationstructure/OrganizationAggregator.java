package com.ouroboros.organizationstructure;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 组织聚合器接口，提供组织结构数据的访问方法
 */
public interface OrganizationAggregator {

  // ==================== 1. 基础查询 ====================

  /**
   * 根据ID获取节点
   */
  Optional<OrganizationNode> getById(String id);

  /**
   * 根据ID集合获取节点
   */
  List<OrganizationNode> getByIds(Collection<String> ids);

  /**
   * 获取所有节点
   */
  List<OrganizationNode> getAll();

  /**
   * 获取所有结构性节点（不含岗位等职能性节点）
   */
  default List<OrganizationNode> getAllStructural() {
    return getAll().stream()
        .filter(node -> node.getCategory() == NodeCategory.STRUCTURAL)
        .collect(Collectors.toList());
  }

  /**
   * 获取所有职能性节点（岗位等）
   */
  default List<OrganizationNode> getAllFunctional() {
    return getAll().stream()
        .filter(node -> node.getCategory() == NodeCategory.FUNCTIONAL)
        .collect(Collectors.toList());
  }

  // ==================== 2. 祖先查询（不包含自己） ====================

  /**
   * 获取所有祖先节点（从近到远）
   */
  List<OrganizationNode> getAncestors(String id);

  /**
   * 获取符合条件的祖先节点
   */
  default List<OrganizationNode> getAncestors(String id, Predicate<OrganizationNode> predicate) {
    return getAncestors(id).stream().filter(predicate).collect(Collectors.toList());
  }

  /**
   * 获取第一个符合条件的祖先节点
   */
  default Optional<OrganizationNode> getFirstAncestor(String id, Predicate<OrganizationNode> predicate) {
    return getAncestors(id).stream().filter(predicate).findFirst();
  }

  /**
   * 根据条件定位节点，获取其祖先
   */
  default List<OrganizationNode> getAncestorsBy(Predicate<OrganizationNode> locator) {
    return getAll().stream()
        .filter(locator)
        .findFirst()
        .map(node -> getAncestors(node.getId()))
        .orElse(Collections.emptyList());
  }

  // ==================== 3. 后代查询（不包含自己） ====================

  /**
   * 获取所有后代节点
   */
  List<OrganizationNode> getDescendants(String id);

  /**
   * 获取符合条件的后代节点
   */
  default List<OrganizationNode> getDescendants(String id, Predicate<OrganizationNode> predicate) {
    return getDescendants(id).stream().filter(predicate).collect(Collectors.toList());
  }

  /**
   * 根据条件定位节点，获取其后代
   */
  default List<OrganizationNode> getDescendantsBy(Predicate<OrganizationNode> locator) {
    return getAll().stream()
        .filter(locator)
        .findFirst()
        .map(node -> getDescendants(node.getId()))
        .orElse(Collections.emptyList());
  }

  // ==================== 4. 相关节点查询（包含自己） ====================

  /**
   * 获取相关的所有节点（祖先+自己+后代）
   */
  default List<OrganizationNode> getRelatedNodes(String id) {
    return getById(id)
        .map(node -> {
          List<OrganizationNode> ancestors = new ArrayList<>(getAncestors(id));
          Collections.reverse(ancestors);
          ancestors.add(node);
          ancestors.addAll(getDescendants(id));
          return ancestors;
        })
        .orElse(Collections.emptyList());
  }

  /**
   * 根据条件定位节点，获取其相关的所有节点（祖先+自己+后代）
   */
  default List<OrganizationNode> getRelatedNodesBy(Predicate<OrganizationNode> locator) {
    return getAll().stream()
        .filter(locator)
        .findFirst()
        .map(node -> getRelatedNodes(node.getId()))
        .orElse(Collections.emptyList());
  }

  // ==================== 5. 按行政级别查询 ====================

  /**
   * 获取所有指定行政级别的节点
   */
  default List<OrganizationNode> getAllByAdminLevel(String adminLevel) {
    return getAll().stream()
        .filter(node -> Objects.equals(adminLevel, node.getAdminLevel()))
        .collect(Collectors.toList());
  }

  /**
   * 获取第一个指定行政级别的祖先节点
   */
  default Optional<OrganizationNode> getFirstAncestorByAdminLevel(String id, String adminLevel) {
    return getFirstAncestor(id, node -> Objects.equals(adminLevel, node.getAdminLevel()));
  }

  /**
   * 获取所有指定行政级别的祖先节点
   */
  default List<OrganizationNode> getAncestorsByAdminLevel(String id, String adminLevel) {
    return getAncestors(id, node -> Objects.equals(adminLevel, node.getAdminLevel()));
  }

  // ==================== 6. Company查询 ====================

  /**
   * 获取所有Company节点
   */
  default List<Company> getAllCompanies() {
    return getAll().stream()
        .filter(Company.class::isInstance)
        .map(Company.class::cast)
        .collect(Collectors.toList());
  }

  /**
   * 获取最近的Company（自己或向上查找）
   */
  default Optional<Company> getNearestCompany(String id) {
    return getById(id).flatMap(node -> {
      if (node instanceof Company) {
        return Optional.of((Company) node);
      }
      return getFirstAncestor(id, Company.class::isInstance).map(Company.class::cast);
    });
  }

  /**
   * 获取第一个Company祖先节点
   */
  default Optional<Company> getFirstAncestorCompany(String id) {
    return getFirstAncestor(id, Company.class::isInstance).map(Company.class::cast);
  }

  /**
   * 获取所有Company祖先节点
   */
  default List<Company> getAncestorCompanies(String id) {
    return getAncestors(id, Company.class::isInstance).stream()
        .map(Company.class::cast)
        .collect(Collectors.toList());
  }

  // ==================== 7. Department查询 ====================

  /**
   * 获取所有Department节点
   */
  default List<Department> getAllDepartments() {
    return getAll().stream()
        .filter(Department.class::isInstance)
        .map(Department.class::cast)
        .collect(Collectors.toList());
  }

  /**
   * 获取最近的Department（自己或向上查找）
   */
  default Optional<Department> getNearestDepartment(String id) {
    return getById(id).flatMap(node -> {
      if (node instanceof Department) {
        return Optional.of((Department) node);
      }
      return getFirstAncestor(id, Department.class::isInstance).map(Department.class::cast);
    });
  }

  /**
   * 获取最近的满足条件的Department（自己或向上查找）
   */
  default Optional<Department> getNearestDepartment(String id, Predicate<Department> predicate) {
    return getById(id).flatMap(node -> {
      if (node instanceof Department && predicate.test((Department) node)) {
        return Optional.of((Department) node);
      }
      return getAncestorDepartments(id).stream().filter(predicate).findFirst();
    });
  }

  /**
   * 获取最近的指定行政级别的Department（自己或向上查找）
   */
  default Optional<Department> getNearestDepartmentByAdminLevel(String id, String adminLevel) {
    return getNearestDepartment(id, dept -> Objects.equals(adminLevel, dept.getAdminLevel()));
  }

  /**
   * 获取第一个Department祖先节点
   */
  default Optional<Department> getFirstAncestorDepartment(String id) {
    return getFirstAncestor(id, Department.class::isInstance).map(Department.class::cast);
  }

  /**
   * 获取所有Department祖先节点
   */
  default List<Department> getAncestorDepartments(String id) {
    return getAncestors(id, Department.class::isInstance).stream()
        .map(Department.class::cast)
        .collect(Collectors.toList());
  }

  // ==================== 8. Position查询 ====================

  /**
   * 获取后代中所有Position节点
   */
  default List<Position> getDescendantPositions(String id) {
    return getDescendants(id, Position.class::isInstance).stream()
        .map(Position.class::cast)
        .collect(Collectors.toList());
  }

  /**
   * 获取后代中所有指定名称的Position节点
   */
  default List<Position> getDescendantPositionsByName(String id, String positionName) {
    return getDescendantPositions(id).stream()
        .filter(p -> Objects.equals(positionName, p.getPositionName()))
        .collect(Collectors.toList());
  }

  /**
   * 获取后代中第一个指定名称的Position节点
   */
  default Optional<Position> getFirstDescendantPositionByName(String id, String positionName) {
    return getDescendantPositions(id).stream()
        .filter(p -> Objects.equals(positionName, p.getPositionName()))
        .findFirst();
  }

  /**
   * 获取后代中所有满足条件的Position节点
   */
  default List<Position> getDescendantPositions(String id, Predicate<Position> predicate) {
    return getDescendantPositions(id).stream()
        .filter(predicate)
        .collect(Collectors.toList());
  }

  /**
   * 获取后代中第一个满足条件的Position节点
   */
  default Optional<Position> getFirstDescendantPosition(String id, Predicate<Position> predicate) {
    return getDescendantPositions(id).stream()
        .filter(predicate)
        .findFirst();
  }
}
