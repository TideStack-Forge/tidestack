package com.ouroboros.dev.app.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import com.ouroboros.dev.app.model.AppEditScopeEntity;
import com.ouroboros.dev.app.model.AppEditScopeSubjectType;
import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.dev.app.service.AppEditScopeService;
import com.ouroboros.dev.app.service.AppOwnerService;
import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.UserRoleCenter;

@Service
public class AppEditAccessEvaluatorImpl implements AppEditAccessEvaluator {

  private final AppEditScopeService appEditScopeService;
  private final UserRoleCenter userRoleCenter;
  private final EmployeeAggregator employeeAggregator;
  private final AppOwnerService appOwnerService;

  public AppEditAccessEvaluatorImpl(AppEditScopeService appEditScopeService,
                                    UserRoleCenter userRoleCenter,
                                    EmployeeAggregator employeeAggregator,
                                    AppOwnerService appOwnerService) {
    this.appEditScopeService = appEditScopeService;
    this.userRoleCenter = userRoleCenter;
    this.employeeAggregator = employeeAggregator;
    this.appOwnerService = appOwnerService;
  }

  @Override
  public boolean canView(String appName, String userIdentity) {
    return canManageApp(appName, userIdentity) || canAccess(appName, userIdentity);
  }

  @Override
  public boolean canEdit(String appName, String userIdentity) {
    return canAccess(appName, userIdentity);
  }

  @Override
  public boolean isRestricted(String appName) {
    return appEditScopeService.isRestricted(appName);
  }

  @Override
  public boolean canManageApp(String appName, String userIdentity) {
    return isGlobalManager(userIdentity) || isOwner(appName, userIdentity);
  }

  @Override
  public boolean canManageEditAccess(String appName, String userIdentity) {
    return canManageApp(appName, userIdentity);
  }

  private boolean isGlobalManager(String userIdentity) {
    if (StringUtils.isBlank(userIdentity)) {
      return false;
    }
    return getAuthorityNames(userIdentity).contains(EDIT_ACCESS_MANAGEMENT_AUTHORITY);
  }

  private boolean isOwner(String appName, String userIdentity) {
    if (StringUtils.isBlank(appName) || StringUtils.isBlank(userIdentity) || appOwnerService == null) {
      return false;
    }
    try {
      return appOwnerService.isOwner(appName, userIdentity);
    } catch (Exception ownerLookupFailure) {
      return false;
    }
  }

  private boolean canAccess(String appName, String userIdentity) {
    if (StringUtils.isBlank(userIdentity)) {
      return false;
    }

    List<AppEditScopeEntity> scopes = appEditScopeService.getByAppName(appName);
    if (scopes.isEmpty()) {
      return true;
    }

    Set<String> roleIds = getRoleIds(userIdentity);
    Set<String> organizationIds = getOrganizationIds(userIdentity);

    for (AppEditScopeEntity scope : scopes) {
      if (scope == null || StringUtils.isBlank(scope.getSubjectType()) || StringUtils.isBlank(scope.getSubjectId())) {
        continue;
      }

      AppEditScopeSubjectType subjectType = parseSubjectType(scope.getSubjectType());
      if (subjectType == null) {
        continue;
      }

      if (subjectType == AppEditScopeSubjectType.USER && StringUtils.equals(userIdentity, scope.getSubjectId())) {
        return true;
      }
      if (subjectType == AppEditScopeSubjectType.ROLE && roleIds.contains(scope.getSubjectId())) {
        return true;
      }
      if (subjectType == AppEditScopeSubjectType.ORG && organizationIds.contains(scope.getSubjectId())) {
        return true;
      }
    }

    return false;
  }

  private Set<String> getRoleIds(String userIdentity) {
    try {
      return userRoleCenter.getRolesByUser(userIdentity).stream()
          .filter(Objects::nonNull)
          .map(Role::getId)
          .filter(StringUtils::isNotBlank)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (Exception roleLookupFailure) {
      return Collections.emptySet();
    }
  }

  private Set<String> getAuthorityNames(String userIdentity) {
    try {
      return userRoleCenter.getRolesByUser(userIdentity).stream()
          .filter(Objects::nonNull)
          .map(Role::getAuthorities)
          .filter(Objects::nonNull)
          .flatMap(Collection::stream)
          .filter(Objects::nonNull)
          .map(OuroborosGrantedAuthority::getAuthority)
          .filter(StringUtils::isNotBlank)
          .collect(Collectors.toCollection(LinkedHashSet::new));
    } catch (Exception authorityLookupFailure) {
      return Collections.emptySet();
    }
  }

  private Set<String> getOrganizationIds(String userIdentity) {
    try {
      return employeeAggregator.getByUser(userIdentity)
          .map(this::getOrganizationIds)
          .orElseGet(Collections::emptySet);
    } catch (Exception employeeLookupFailure) {
      return Collections.emptySet();
    }
  }

  private Set<String> getOrganizationIds(Employee employee) {
    Set<String> organizationIds = new LinkedHashSet<>();
    if (employee.getDepartmentDetail() != null && StringUtils.isNotBlank(employee.getDepartmentDetail().getId())) {
      organizationIds.add(employee.getDepartmentDetail().getId());
    }
    if (employee.getCompanyDetail() != null && StringUtils.isNotBlank(employee.getCompanyDetail().getId())) {
      organizationIds.add(employee.getCompanyDetail().getId());
    }
    return organizationIds;
  }

  private AppEditScopeSubjectType parseSubjectType(String subjectType) {
    try {
      return AppEditScopeSubjectType.valueOf(subjectType);
    } catch (IllegalArgumentException parseFailure) {
      return null;
    }
  }
}
