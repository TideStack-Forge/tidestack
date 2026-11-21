package com.ouroboros.organizationstructure.service.impl;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.organizationstructure.AppointmentAggregator;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.dto.response.OrganizationNodeDTO;
import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.entity.OrganizationNodeEntity;
import com.ouroboros.organizationstructure.entity.PositionEntity;
import com.ouroboros.organizationstructure.model.CompanyModel;
import com.ouroboros.organizationstructure.model.DepartmentModel;
import com.ouroboros.organizationstructure.model.PositionModel;
import com.ouroboros.organizationstructure.service.AdminLevelService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationQueryFacadeImplTreeNavigationTest {

  @Test
  void buildTreeShouldThrowBusinessExceptionWith404WhenRootNodeIsMissing() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    when(organizationAggregator.getById("missing-root")).thenReturn(Optional.empty());

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> facade.buildTree("missing-root", false, Collections.emptyList()));

    assertEquals(404, exception.getErrorCode());
    assertEquals("组织节点不存在", exception.getMessage());
  }

  @Test
  void buildTreeWithTypeShouldThrowBusinessExceptionWith404WhenRootNodeIsMissing() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    when(organizationAggregator.getAll()).thenReturn(Collections.singletonList(company("company-1", null)));

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> facade.buildTree("missing-root", false, Collections.emptyList(), "company"));

    assertEquals(404, exception.getErrorCode());
    assertEquals("组织节点不存在", exception.getMessage());
  }

  @Test
  void getNodeChildrenShouldThrowBusinessExceptionWith404WhenNodeIsMissing() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    when(organizationAggregator.getById("missing-node")).thenReturn(Optional.empty());

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> facade.getNodeChildren("missing-node"));

    assertEquals(404, exception.getErrorCode());
    assertEquals("组织节点不存在", exception.getMessage());
  }

  @Test
  void getNodePathShouldThrowBusinessExceptionWith404WhenNodeIsMissing() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    when(organizationAggregator.getById("missing-node")).thenReturn(Optional.empty());

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    BusinessException exception = assertThrows(BusinessException.class,
        () -> facade.getNodePath("missing-node"));

    assertEquals(404, exception.getErrorCode());
    assertEquals("组织节点不存在", exception.getMessage());
  }

  @Test
  void buildTreeShouldExcludePositionsWhenIncludePositionsIsFalse() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    CompanyModel company = company("company-1", null);
    DepartmentModel department = department("department-1", "company-1");
    PositionModel position = position("position-1", "department-1");
    when(organizationAggregator.getById("company-1")).thenReturn(Optional.of(company));
    when(organizationAggregator.getDescendants("company-1")).thenReturn(Arrays.asList(department, position));
    when(organizationAggregator.getDescendants("department-1")).thenReturn(Collections.singletonList(position));
    when(organizationAggregator.getDescendants("position-1")).thenReturn(Collections.emptyList());

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    List<OrganizationNodeDTO> tree = facade.buildTree("company-1", false, Collections.emptyList());

    assertEquals(1, tree.size());
    assertEquals("company-1", tree.get(0).getId());
    assertEquals(1, tree.get(0).getChildren().size());
    assertEquals("department-1", tree.get(0).getChildren().get(0).getId());
    assertNull(tree.get(0).getChildren().get(0).getChildren());
  }

  @Test
  void buildTreeShouldResolveAdminLevelScopeFromAdminLevelType() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    AdminLevelService adminLevelService = mock(AdminLevelService.class);
    CompanyModel branch = company("branch-1", null, "BRANCH");
    DepartmentModel division = department("division-1", "branch-1", "DIVISION");
    when(organizationAggregator.getById("branch-1")).thenReturn(Optional.of(branch));
    when(organizationAggregator.getDescendants("branch-1")).thenReturn(Collections.singletonList(division));
    when(organizationAggregator.getDescendants("division-1")).thenReturn(Collections.emptyList());
    when(adminLevelService.findByCode("BRANCH")).thenReturn(Optional.of(adminLevel("BRANCH", "ORG")));
    when(adminLevelService.findByCode("DIVISION")).thenReturn(Optional.of(adminLevel("DIVISION", "DEPT")));

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator, adminLevelService);

    List<OrganizationNodeDTO> tree = facade.buildTree("branch-1", false, Collections.emptyList());

    assertEquals("ORG", tree.get(0).getAdminLevelScope());
    assertEquals("DEPT", tree.get(0).getChildren().get(0).getAdminLevelScope());
  }

  @Test
  void getNodeChildrenShouldReturnOnlyDirectChildren() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    CompanyModel company = company("company-1", null);
    DepartmentModel directDepartment = department("department-1", "company-1");
    DepartmentModel nestedDepartment = department("department-2", "department-1");
    PositionModel nestedPosition = position("position-1", "department-1");
    when(organizationAggregator.getById("company-1")).thenReturn(Optional.of(company));
    when(organizationAggregator.getDescendants("company-1"))
        .thenReturn(Arrays.asList(directDepartment, nestedDepartment, nestedPosition));

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    List<OrganizationNode> children = facade.getNodeChildren("company-1");

    assertEquals(Collections.singletonList("department-1"), extractIds(children));
  }

  @Test
  void getNodePathShouldReturnAncestorsRootFirst() {
    OrganizationAggregator organizationAggregator = mock(OrganizationAggregator.class);
    CompanyModel company = company("company-1", null);
    DepartmentModel department = department("department-1", "company-1");
    PositionModel position = position("position-1", "department-1");
    when(organizationAggregator.getById("position-1")).thenReturn(Optional.of(position));
    when(organizationAggregator.getAncestors("position-1")).thenReturn(Arrays.asList(department, company));

    OrganizationQueryFacadeImpl facade = newFacade(organizationAggregator);

    List<OrganizationNode> path = facade.getNodePath("position-1");

    assertEquals(Arrays.asList("company-1", "department-1", "position-1"), extractIds(path));
  }

  private OrganizationQueryFacadeImpl newFacade(OrganizationAggregator organizationAggregator) {
    return newFacade(organizationAggregator, null);
  }

  private OrganizationQueryFacadeImpl newFacade(OrganizationAggregator organizationAggregator, AdminLevelService adminLevelService) {
    return new OrganizationQueryFacadeImpl(
        organizationAggregator,
        mock(EmployeeAggregator.class),
        mock(AppointmentAggregator.class),
        adminLevelService);
  }

  private List<String> extractIds(List<OrganizationNode> nodes) {
    return nodes.stream().map(OrganizationNode::getId).collect(Collectors.toList());
  }

  private CompanyModel company(String id, String parent) {
    return company(id, parent, null);
  }

  private CompanyModel company(String id, String parent, String adminLevel) {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setId(id);
    entity.setName(id);
    entity.setType("company");
    entity.setParent(parent);
    entity.setAdminLevel(adminLevel);
    return new CompanyModel(entity, leaderId -> null, parentId -> null);
  }

  private DepartmentModel department(String id, String parent) {
    return department(id, parent, null);
  }

  private DepartmentModel department(String id, String parent, String adminLevel) {
    OrganizationNodeEntity entity = new OrganizationNodeEntity();
    entity.setId(id);
    entity.setName(id);
    entity.setType("department");
    entity.setParent(parent);
    entity.setAdminLevel(adminLevel);
    return new DepartmentModel(entity, leaderId -> null, parentId -> null, companyId -> (Company) null);
  }

  private PositionModel position(String id, String parent) {
    PositionEntity entity = new PositionEntity();
    entity.setId(id);
    entity.setPositionName(id);
    entity.setParent(parent);
      return new PositionModel(entity, () -> null, () -> null, () -> 0, () -> null);
  }

  private AdminLevelEntity adminLevel(String code, String scope) {
    AdminLevelEntity entity = new AdminLevelEntity();
    entity.setCode(code);
    entity.setScope(scope);
    entity.setEnabled(Boolean.TRUE);
    return entity;
  }
}
