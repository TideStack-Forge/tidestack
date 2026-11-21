package com.ouroboros.statistics.reporting.runtime.execution;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.AppContext;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.OrganizationNode;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DefaultDatasetExecutionContextResolverTest {

  @AfterEach
  void tearDown() {
    CurrentSecurityContextHolder.clearDelegate();
    OrganizationContextHolder.clear();
  }

  @Test
  void shouldBuildTrustedExecutionContextFromRuntimeState() throws Exception {
    Path appHome = Files.createTempDirectory("statistics-reporting-app");
    Files.write(
        appHome.resolve("app.metadata.json"),
        Collections.singletonList("{\"name\":\"demo-app\"}"),
        StandardCharsets.UTF_8
    );

    TestAuthentication authentication = new TestAuthentication(
        "user-1",
        new TestUser(
            "user-1",
            Claims.builder()
                .claim(StandardClaims.POSITION_ID, "position-1")
                .claim("tenantId", "tenant-a")
                .build(),
            Collections.singletonList(new TestRole("report.viewer"))
        )
    );
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<com.ouroboros.security.identity.SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(authentication);
      }
    });
    OrganizationContextHolder.setContext(new TestOrganizationContext(
        new TestEmployee("employee-1", "Alice"),
        new TestDepartment("dept-1", "North"),
        new TestCompany("org-1", "Ouroboros"),
        Arrays.<OrganizationNode>asList(
            new TestDepartment("dept-1", "North"),
            new TestCompany("org-1", "Ouroboros")
        )
    ));

    DefaultDatasetExecutionContextResolver resolver = new DefaultDatasetExecutionContextResolver(new AppContext() {
      @Override
      public String getHomePath() {
        return appHome.toString();
      }

      @Override
      public Boolean isDebug() {
        return true;
      }
    });

    DatasetExecutionRequest request = new DatasetExecutionRequest();
    Map<String, Object> untrustedExecutionContext = new LinkedHashMap<String, Object>();
    untrustedExecutionContext.put("currentUserIdentity", "intruder");
    untrustedExecutionContext.put("principal", "intruder");
    untrustedExecutionContext.put("tenantId", "tenant-b");
    untrustedExecutionContext.put("roles", Collections.singletonList("guest"));
    request.setExecutionContext(untrustedExecutionContext);

    Map<String, Object> executionContext = resolver.resolve(request);

    assertEquals("user-1", executionContext.get("currentUser"));
    assertEquals("user-1", executionContext.get("currentUserIdentity"));
    assertEquals("user-1", executionContext.get("userIdentity"));
    assertEquals("user-1", executionContext.get("principal"));
    assertEquals("tenant-a", executionContext.get("tenantId"));
    assertEquals("dept-1", executionContext.get("departmentId"));
    assertEquals("position-1", executionContext.get("positionId"));
    assertEquals("org-1", executionContext.get("organizationId"));
    assertEquals(Collections.singletonList("report.viewer"), executionContext.get("roles"));
    assertEquals("demo-app", executionContext.get("appName"));
    assertEquals("debug", executionContext.get("environment"));
    assertEquals(Boolean.TRUE, executionContext.get("debug"));
    assertEquals(Arrays.asList("dept-1", "org-1"), executionContext.get("orgChainIds"));
  }

  @Test
  void shouldIgnoreClientExecutionContextAndReturnFreshTrustedMap() {
    DefaultDatasetExecutionContextResolver resolver = new DefaultDatasetExecutionContextResolver(() -> "/tmp/demo-app");
    DatasetExecutionRequest request = new DatasetExecutionRequest();
    request.setExecutionContext(Collections.<String, Object>singletonMap("roles", Collections.singletonList("guest")));

    Map<String, Object> resolved = resolver.resolve(request);

    assertFalse(((List<?>) resolved.getOrDefault("roles", Collections.emptyList())).contains("guest"));
    assertNull(resolved.get("currentUser"));
    assertNull(resolved.get("currentUserIdentity"));
    assertNull(resolved.get("userIdentity"));
    assertNull(resolved.get("principal"));
    assertNull(resolved.get("authenticated"));
    assertEquals("demo-app", resolved.get("appName"));
  }

  private static class TestAuthentication implements OuroborosAuthentication {
    private final String principal;
    private final TestUser user;

    private TestAuthentication(String principal, TestUser user) {
      this.principal = principal;
      this.user = user;
    }

    @Override
    public String getPrincipal() {
      return principal;
    }

    @Override
    public User getUserDetail() {
      return user;
    }

    @Override
    public List<? extends Role> getRoles() {
      return user.getRoles();
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return user;
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return Collections.emptyList();
    }

    @Override
    public boolean isAuthenticated() {
      return true;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
    }

    @Override
    public String getName() {
      return principal;
    }
  }

  private static class TestUser implements User {
    private final String userIdentity;
    private final Claims claims;
    private final List<Role> roles;

    private TestUser(String userIdentity, Claims claims, List<Role> roles) {
      this.userIdentity = userIdentity;
      this.claims = claims;
      this.roles = roles;
    }

    @Override
    public String getUserIdentity() {
      return userIdentity;
    }

    @Override
    public Claims getClaims() {
      return claims;
    }

    @Override
    public List<Role> getRoles() {
      return roles;
    }
  }

  private static class TestRole implements Role {
    private final String id;

    private TestRole(String id) {
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
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return Collections.emptyList();
    }
  }

  private static class TestOrganizationContext implements OrganizationContext {
    private final Employee employee;
    private final Department department;
    private final Company company;
    private final List<OrganizationNode> orgChain;

    private TestOrganizationContext(Employee employee,
                                    Department department,
                                    Company company,
                                    List<OrganizationNode> orgChain) {
      this.employee = employee;
      this.department = department;
      this.company = company;
      this.orgChain = orgChain;
    }

    @Override
    public Optional<Employee> getEmployee() {
      return Optional.ofNullable(employee);
    }

    @Override
    public Optional<Department> getDepartment() {
      return Optional.ofNullable(department);
    }

    @Override
    public Optional<Company> getCompany() {
      return Optional.ofNullable(company);
    }

    @Override
    public List<OrganizationNode> getOrgChain() {
      return orgChain;
    }
  }

  private static class TestEmployee implements Employee {
    private final String id;
    private final String name;

    private TestEmployee(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
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
    public Company getCompanyDetail() {
      return null;
    }
  }

  private abstract static class AbstractOrganizationNode implements OrganizationNode {
    private final String id;
    private final String name;

    private AbstractOrganizationNode(String id, String name) {
      this.id = id;
      this.name = name;
    }

    @Override
    public String getId() {
      return id;
    }

    @Override
    public String getName() {
      return name;
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
    private TestDepartment(String id, String name) {
      super(id, name);
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
    private TestCompany(String id, String name) {
      super(id, name);
    }

    @Override
    public String getType() {
      return "company";
    }
  }
}
