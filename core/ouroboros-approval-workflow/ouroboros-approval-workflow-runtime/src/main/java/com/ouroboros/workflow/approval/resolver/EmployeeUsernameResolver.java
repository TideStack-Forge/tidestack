package com.ouroboros.workflow.approval.resolver;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.organizationstructure.Employee;
import com.ouroboros.organizationstructure.EmployeeAggregator;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.workflow.approval.web.user.UsernameResolver;

/**
 * @author liansz
 */
@Component
public class EmployeeUsernameResolver implements UsernameResolver {

  @Autowired(required = false)
  private EmployeeAggregator employeeAggregator;

  @Autowired(required = false)
  private UserCenter userCenter;

  @Override
  public Optional<String> resolve(String userId) {
    return resolveUserIdentity(userId)
        .or(() -> resolveLegacyEmployeeIdentity(userId));
  }

  @Override
  public Map<String, String> resolve(List<String> users) {
    if (users == null || users.isEmpty()) {
      return Map.of();
    }

    Map<String, String> resolved = new LinkedHashMap<>();
    if (userCenter != null) {
      userCenter.getUsers(users).stream()
          .filter(user -> user != null && StringUtils.isNotBlank(user.getUserIdentity()))
          .forEach(user -> resolved.put(user.getUserIdentity(), user.getUserIdentity()));
    }
    if (employeeAggregator == null) {
      return resolved;
    }

    List<String> unresolved = users.stream()
        .filter(StringUtils::isNotBlank)
        .filter(user -> !resolved.containsKey(user))
        .collect(Collectors.toList());
    employeeAggregator.getByIds(unresolved).stream()
        .forEach(employee -> resolved.put(
            employee.getId(),
            StringUtils.defaultIfBlank(employee.getUser(), employee.getId())));
    return resolved;
  }

  private Optional<String> resolveUserIdentity(String userId) {
    if (userCenter == null || StringUtils.isBlank(userId)) {
      return Optional.empty();
    }
    return userCenter.getUser(userId)
        .map(User::getUserIdentity)
        .filter(StringUtils::isNotBlank);
  }

  private Optional<String> resolveLegacyEmployeeIdentity(String employeeId) {
    if (employeeAggregator == null || StringUtils.isBlank(employeeId)) {
      return Optional.empty();
    }
    return employeeAggregator.getById(employeeId)
        .map(Employee::getUser)
        .filter(StringUtils::isNotBlank);
  }
}
