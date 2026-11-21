package com.ouroboros.dev.app.service.impl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppOwnerService;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.dev.app.model.AppOwnerEntity;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.UserRoleCenter;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppEditAccessEvaluatorImplTest {

  private static final String EDIT_ACCESS_MANAGEMENT_AUTHORITY = AppEditAccessEvaluator.EDIT_ACCESS_MANAGEMENT_AUTHORITY;

  @Mock
  private AppEditScopeService appEditScopeService;

  @Mock
  private UserRoleCenter userRoleCenter;

  @Mock
  private EmployeeAggregator employeeAggregator;

  @Mock
  private AppOwnerService appOwnerService;

  private AppEditAccessEvaluator evaluator;

  @BeforeEach
  void setUp() {
    evaluator = new AppEditAccessEvaluatorImpl(appEditScopeService, userRoleCenter, employeeAggregator, appOwnerService);
  }

  @Test
  void unrestrictedAppAllowsAnyAuthenticatedDeveloper() {
    when(appEditScopeService.getByAppName("sales-app")).thenReturn(Collections.emptyList());

    assertTrue(evaluator.canView("sales-app", "alice"));
    assertTrue(evaluator.canEdit("sales-app", "alice"));
    assertFalse(evaluator.isRestricted("sales-app"));
  }

  @Test
  void restrictedAppAllowsExactUserMatch() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.emptyList());
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.USER, "alice")));

    assertTrue(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void restrictedAppAllowsRoleMatch() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.singletonList(new TestRole("dev-role")));
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.ROLE, "dev-role")));

    assertTrue(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void restrictedAppAllowsDepartmentOrCompanyMatch() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.emptyList());
    when(employeeAggregator.getByUser("alice"))
        .thenReturn(java.util.Optional.of(new TestEmployee("emp-1", department("dept-rd"), company("company-acme"))));
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.ORG, "dept-rd")));

    assertTrue(evaluator.canEdit("sales-app", "alice"));

    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.ORG, "company-acme")));

    assertTrue(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void organizationGrantDoesNotMatchAncestorOrDescendantByChainAlone() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.emptyList());
    when(employeeAggregator.getByUser("alice"))
        .thenReturn(java.util.Optional.of(new TestEmployee("emp-1", department("dept-rd"), company("company-acme"))));
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.ORG, "dept-rd-sub")));

    assertFalse(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void organizationGrantDoesNotMatchWhenUserHasNoEmployeeBinding() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.emptyList());
    when(employeeAggregator.getByUser("alice")).thenReturn(java.util.Optional.empty());
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.ORG, "dept-rd")));

    assertFalse(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void editAccessManagementAuthorityDoesNotBypassAppEditScopes() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.singletonList(
        new TestRole("edit-access-manager", EDIT_ACCESS_MANAGEMENT_AUTHORITY)));
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.USER, "bob")));

    assertTrue(evaluator.canManageApp("sales-app", "alice"));
    assertTrue(evaluator.canManageEditAccess("sales-app", "alice"));
    assertTrue(evaluator.canView("sales-app", "alice"));
    assertFalse(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void restrictedAppDeniesUnmatchedUser() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.singletonList(new TestRole("dev-role")));
    when(employeeAggregator.getByUser("alice"))
        .thenReturn(java.util.Optional.of(new TestEmployee("emp-1", department("dept-rd"), company("company-acme"))));
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.USER, "bob")));
    when(appEditScopeService.isRestricted("sales-app")).thenReturn(true);

    assertFalse(evaluator.canView("sales-app", "alice"));
    assertFalse(evaluator.canEdit("sales-app", "alice"));
    assertTrue(evaluator.isRestricted("sales-app"));
  }

  @Test
  void ownerCanManageAndViewButDoesNotAutoEditRestrictedApp() {
    when(appOwnerService.isOwner("sales-app", "alice")).thenReturn(true);
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.emptyList());
    when(appEditScopeService.getByAppName("sales-app"))
        .thenReturn(Collections.singletonList(scope(AppEditScopeSubjectType.USER, "bob")));

    assertTrue(evaluator.canManageApp("sales-app", "alice"));
    assertTrue(evaluator.canManageEditAccess("sales-app", "alice"));
    assertTrue(evaluator.canView("sales-app", "alice"));
    assertFalse(evaluator.canEdit("sales-app", "alice"));
  }

  @Test
  void roleIdAloneDoesNotGrantEditAccessManagementPermission() {
    when(userRoleCenter.getRolesByUser("alice")).thenReturn(Collections.singletonList(new TestRole("administrator")));

    assertFalse(evaluator.canManageApp("sales-app", "alice"));
    assertFalse(evaluator.canManageEditAccess("sales-app", "alice"));
  }

  private static AppEditScopeEntity scope(AppEditScopeSubjectType subjectType, String subjectId) {
    AppEditScopeEntity entity = new AppEditScopeEntity();
    entity.setAppName("sales-app");
    entity.setSubjectType(subjectType.name());
    entity.setSubjectId(subjectId);
    return entity;
  }

  private static AppOwnerEntity owner(String appName, String ownerUserIdentity) {
    AppOwnerEntity owner = new AppOwnerEntity();
    owner.setAppName(appName);
    owner.setOwnerUserIdentity(ownerUserIdentity);
    return owner;
  }

  private static Department department(String id) {
    return new TestDepartment(id);
  }

  private static Company company(String id) {
    return new TestCompany(id);
  }

  private static class TestRole implements Role {
    private final String id;
    private final List<? extends OuroborosGrantedAuthority> authorities;

    private TestRole(String id, String... authorities) {
      this.id = id;
      this.authorities = java.util.Arrays.stream(authorities)
          .map(TestGrantedAuthority::new)
          .collect(Collectors.toList());
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return id;
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return authorities;
    }
  }

  private static class TestGrantedAuthority implements OuroborosGrantedAuthority {
    private final String authority;

    private TestGrantedAuthority(String authority) {
      this.authority = authority;
    }

    @Override
    public String getAuthority() {
      return authority;
    }

    @Override
    public Authority getAuthorityObject() {
      return null;
    }

    @Override
    public com.ouroboros.security.authority.accesscontrol.AccessControlParams getAccessControlParams() {
      return com.ouroboros.security.authority.accesscontrol.AccessControlParams.empty();
    }
  }

  private static class TestEmployee implements Employee {
    private final String id;
    private final Department department;
    private final Company company;

    private TestEmployee(String id, Department department, Company company) {
      this.id = id;
      this.department = department;
      this.company = company;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return id;
    }

    @Override
    public String getGender() {
      return null;
    }

    @Override
    public String getMobile() {
      return null;
    }

    @Override
    public String getEmail() {
      return null;
    }

    @Override
    public String getBirthday() {
      return null;
    }

    @Override
    public Department getDepartmentDetail() {
      return department;
    }

    @Override
    public Company getCompanyDetail() {
      return company;
    }
  }

  private abstract static class AbstractOrganizationNode implements OrganizationNode {
    private final String id;

    private AbstractOrganizationNode(String id) {
      this.id = id;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return id;
    }

    @Override
    public String getLeaderEmployee() {
      return null;
    }

    @Override
    public Employee getLeaderEmployeeDetail() {
      return null;
    }

    @Override
    public String getDescription() {
      return null;
    }

    @Override
    public String getParent() {
      return null;
    }

    @Override
    public OrganizationNode getParentDetail() {
      return null;
    }
  }

  private static class TestDepartment extends AbstractOrganizationNode implements Department {
    private TestDepartment(String id) {
      super(id);
    }

    @Override
    public String getType() {
      return "department";
    }

    @Override
    public Company getCompanyDetail() {
      return null;
    }
  }

  private static class TestCompany extends AbstractOrganizationNode implements Company {
    private TestCompany(String id) {
      super(id);
    }

    @Override
    public String getType() {
      return "company";
    }
  }
}
