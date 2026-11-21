package com.ouroboros.statistics.reporting.runtime.execution;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.ouroboros.AppContext;
import com.ouroboros.organizationstructure.Company;
import com.ouroboros.organizationstructure.Department;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.context.OrganizationContext;
import com.ouroboros.organizationstructure.context.OrganizationContextHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

public class DefaultDatasetExecutionContextResolver implements DatasetExecutionContextResolver {
  private final AppContext appContext;

  public DefaultDatasetExecutionContextResolver(AppContext appContext) {
    this.appContext = Objects.requireNonNull(appContext, "appContext is required");
  }

  @Override
  public Map<String, Object> resolve(DatasetExecutionRequest request) {
    Map<String, Object> resolved = new LinkedHashMap<String, Object>();
    OuroborosAuthentication authentication = CurrentSecurityContextHolder.findAuthentication().orElse(null);
    mergeClaims(resolved, authentication == null ? null : authentication.getUserDetail());
    mergeAuthentication(resolved, authentication);
    mergeOrganizationContext(resolved);
    mergeAppContext(resolved);
    return resolved;
  }

  private void mergeClaims(Map<String, Object> resolved, User user) {
    if (user == null) {
      return;
    }
    Claims claims = user.getClaims();
    if (claims == null) {
      return;
    }
    Map<String, Object> claimMap = claims.asMap();
    if (claimMap != null) {
      claimMap.forEach((key, value) -> {
        if (!isBlank(key) && value != null) {
          resolved.put(key, value);
        }
      });
    }
    aliasClaim(resolved, claims, StandardClaims.DEPARTMENT_ID, "departmentId");
    aliasClaim(resolved, claims, StandardClaims.POSITION_ID, "positionId");
    aliasClaim(resolved, claims, StandardClaims.ORGANIZATION_ID, "organizationId");
  }

  private void mergeAuthentication(Map<String, Object> resolved, OuroborosAuthentication authentication) {
    if (authentication == null) {
      return;
    }

    User user = authentication.getUserDetail();
    String principal = authentication.getPrincipal();
    String userIdentity = user == null ? null : user.getUserIdentity();
    if (isBlank(userIdentity)) {
      userIdentity = principal;
    }

    putIfPresent(resolved, "currentUser", userIdentity);
    putIfPresent(resolved, "currentUserIdentity", userIdentity);
    putIfPresent(resolved, "userIdentity", userIdentity);
    putIfPresent(resolved, "principal", principal);
    resolved.put("authenticated", Boolean.valueOf(authentication.isAuthenticated()));

    List<String> roleIds = new ArrayList<String>();
    for (Role role : authentication.getRoles()) {
      if (role != null && !isBlank(role.getId())) {
        roleIds.add(role.getId());
      }
    }
    if (!roleIds.isEmpty()) {
      resolved.put("roles", roleIds);
    }
  }

  private void mergeOrganizationContext(Map<String, Object> resolved) {
    OrganizationContextHolder.getContext().ifPresent(context -> {
      mergeEmployee(resolved, context);
      mergeDepartment(resolved, context);
      mergeCompany(resolved, context);
      List<String> orgChainIds = context.getOrgChainIds();
      if (orgChainIds != null && !orgChainIds.isEmpty()) {
        resolved.put("orgChainIds", new ArrayList<String>(orgChainIds));
      }
    });
  }

  private void mergeEmployee(Map<String, Object> resolved, OrganizationContext context) {
    if (context == null) {
      return;
    }
    Employee employee = context.getEmployee().orElse(null);
    if (employee == null) {
      return;
    }
    putIfPresent(resolved, "employeeId", employee.getId());
    putIfPresent(resolved, "employeeName", employee.getName());
  }

  private void mergeDepartment(Map<String, Object> resolved, OrganizationContext context) {
    if (context == null) {
      return;
    }
    Department department = context.getDepartment().orElse(null);
    if (department == null) {
      return;
    }
    putIfPresent(resolved, StandardClaims.DEPARTMENT_ID, department.getId());
    putIfPresent(resolved, "departmentId", department.getId());
    putIfPresent(resolved, "departmentName", department.getName());
  }

  private void mergeCompany(Map<String, Object> resolved, OrganizationContext context) {
    if (context == null) {
      return;
    }
    Company company = context.getCompany().orElse(null);
    if (company == null) {
      return;
    }
    putIfPresent(resolved, StandardClaims.ORGANIZATION_ID, company.getId());
    putIfPresent(resolved, "organizationId", company.getId());
    putIfPresent(resolved, "organizationName", company.getName());
  }

  private void mergeAppContext(Map<String, Object> resolved) {
    putIfPresent(resolved, "appName", resolveAppName());
    boolean debug = Boolean.TRUE.equals(appContext.isDebug());
    resolved.put("debug", Boolean.valueOf(debug));
    resolved.put("environment", debug ? "debug" : "runtime");
  }

  private String resolveAppName() {
    String appName = appContext.getAppInfo()
        .map(com.ouroboros.model.AppInfo::getName)
        .orElse(null);
    if (!isBlank(appName)) {
      return appName;
    }

    String homePath = appContext.getHomePath();
    if (isBlank(homePath)) {
      return null;
    }
    Path appHome = Paths.get(homePath);
    Path fileName = appHome.getFileName();
    return fileName == null ? homePath : fileName.toString();
  }

  private void aliasClaim(Map<String, Object> resolved,
                          Claims claims,
                          String claimName,
                          String contextKey) {
    if (claims == null) {
      return;
    }
    putIfPresent(resolved, contextKey, claims.getString(claimName).orElse(null));
  }

  private void putIfPresent(Map<String, Object> target, String key, Object value) {
    if (isBlank(key) || value == null) {
      return;
    }
    if (value instanceof String && isBlank((String) value)) {
      return;
    }
    target.put(key, value);
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
