package com.ouroboros.organizationstructure.security;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.OrganizationAggregator;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.organizationstructure.enums.OrganizationNodeType;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlFilterDefine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OrganizationStructureAccessControlFilterBuilderTest {

  @Test
  void emptyOrganizationSelectionsLeaveAllRowsAccessible() {
    AccessControlFilterDefine filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byDepartment");
    AccessControlDefine define = mock(AccessControlDefine.class);
    when(define.getFilterDefine()).thenReturn(java.util.List.of(filter));
    var builder = new OrganizationStructureAccessControlFilterBuilder();
    var params = new LinkedHashMap<String, Object>(Map.of(
        "$byDepartment", Map.of("currentDepartment", false, "currentChildrenDepartment", false),
        "createdByDepartment", java.util.List.of()));
    assertEquals(com.ouroboros.security.authority.accesscontrol.RowConstraint.all(), builder.resolve(params, define));
    assertTrue(params.isEmpty());
  }

  @Test
  void selectedCurrentDepartmentWithoutAContextProducesNoRows() {
    AccessControlFilterDefine filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byDepartment");
    AccessControlDefine define = mock(AccessControlDefine.class);
    when(define.getFilterDefine()).thenReturn(java.util.List.of(filter));
    var builder = new OrganizationStructureAccessControlFilterBuilder();
    var rows = builder.resolve(new LinkedHashMap<>(Map.of("$byDepartment", Map.of("currentDepartment", true))), define);
    assertEquals(com.ouroboros.security.authority.accesscontrol.RowConstraint.Mode.NONE, rows.mode());
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class,
        () -> builder.build(new LinkedHashMap<>(Map.of("$byDepartment", Map.of("currentDepartment", true))), define));
  }

  @Test
  void malformedOrganizationSelectionCannotBeIgnored() {
    AccessControlFilterDefine filter = mock(AccessControlFilterDefine.class);
    when(filter.getName()).thenReturn("$byCompany");
    AccessControlDefine define = mock(AccessControlDefine.class);
    when(define.getFilterDefine()).thenReturn(java.util.List.of(filter));
    var builder = new OrganizationStructureAccessControlFilterBuilder();
    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class,
        () -> builder.resolve(new LinkedHashMap<>(Map.of("$byCompany", Map.of("currentCompany", "true"))), define));
  }

  @AfterEach
  void clearContext() {
    OrganizationContextHolder.clear();
  }

  @Test
  void buildShouldCollectOnlyCompanyDescendantsForCurrentChildrenCompany() {
    OrganizationAggregator aggregator = mock(OrganizationAggregator.class);
    Company currentCompany = mock(Company.class);
    when(currentCompany.getId()).thenReturn("company-1");

    Company childCompany = mock(Company.class);
    when(childCompany.getId()).thenReturn("company-2");
    when(childCompany.getType()).thenReturn(OrganizationNodeType.COMPANY.getType());

    Department childDepartment = mock(Department.class);
    when(childDepartment.getId()).thenReturn("department-1");
    when(childDepartment.getType()).thenReturn(OrganizationNodeType.DEPARTMENT.getType());

    when(aggregator.getDescendants("company-1")).thenReturn(Arrays.asList(childCompany, childDepartment));

    OrganizationContext context = mock(OrganizationContext.class);
    when(context.getCompany()).thenReturn(java.util.Optional.of(currentCompany));
    OrganizationContextHolder.setContext(context);

    AccessControlFilterDefine filterDefine = mock(AccessControlFilterDefine.class);
    when(filterDefine.getName()).thenReturn("$byCompany");
    AccessControlDefine define = mock(AccessControlDefine.class);
    when(define.getFilterDefine()).thenReturn(java.util.Collections.singletonList(filterDefine));

    Map<String, Object> params = new LinkedHashMap<>();
    Map<String, Object> byCompany = new LinkedHashMap<>();
    byCompany.put("currentCompany", true);
    byCompany.put("currentChildrenCompany", true);
    params.put("$byCompany", byCompany);
    params.put("createdByCompany", java.util.Collections.singletonList("custom-company"));

    OrganizationStructureAccessControlFilterBuilder builder = new OrganizationStructureAccessControlFilterBuilder();
    ReflectionTestUtils.setField(builder, "organizationAggregator", aggregator);

    Map<String, Object> filters = builder.build(params, define);

    @SuppressWarnings("unchecked")
    java.util.List<String> createdByCompany = (java.util.List<String>) filters.get("createdByCompany");
    assertEquals(3, createdByCompany.size());
    assertTrue(createdByCompany.contains("company-1"));
    assertTrue(createdByCompany.contains("company-2"));
    assertTrue(createdByCompany.contains("custom-company"));
    assertFalse(createdByCompany.contains("department-1"));
  }
}
