package com.ouroboros.organizationstructure.directory;

import java.util.Optional;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.identity.mapping.*;
import com.ouroboros.security.rbac.UserSubjectCenter;

/** Organization associations contribute member addresses; they never authenticate a user. */
public class DirectoryMemberResolver implements EnterpriseIdentityMappingContributor {
  private final DirectoryStore store;
  private final UserSubjectCenter subjects;
  public DirectoryMemberResolver(DirectoryStore store, UserSubjectCenter subjects) { this.store = store; this.subjects = subjects; }

  public Optional<DirectoryBinding> forEmployee(String connectionId, String employeeId) {
    var matches = store.bindingsForLocal("MEMBER", employeeId).stream()
        .filter(item -> connectionId.equals(item.getConnectionId()) && item.getExternalRouteKey() != null).toList();
    if (matches.size() > 1) { throw new ValidationException("员工的企业成员关联不唯一"); }
    return matches.stream().findFirst().map(DirectoryMemberResolver::requireActive);
  }

  @Override public Optional<PlatformPrincipalRef> toPlatform(String connectionId, String memberId) {
    var matches = store.bindings(connectionId).stream().filter(item -> item.getExternalRouteKey() != null
        && "MEMBER".equals(item.getObjectType()) && memberId.equals(item.getExternalId())).toList();
    if (matches.size() > 1) { throw new ValidationException("企业成员的组织关联不唯一"); }
    return matches.stream().findFirst().map(DirectoryMemberResolver::requireActive).flatMap(binding -> {
      var users = subjects.getAll().stream().filter(item -> "employee".equals(item.getSubjectType())
          && binding.getLocalId().equals(item.getSubjectId())).map(item -> item.getUserIdentity()).distinct().toList();
      if (users.size() > 1) { throw new ValidationException("员工关联了多个平台账号"); }
      return users.stream().findFirst().map(PlatformPrincipalRef::new);
    });
  }

  @Override public Optional<String> toExternalMember(String connectionId, PlatformPrincipalRef principal) {
    var employees = subjects.getAllByUser(principal.userIdentity()).stream()
        .filter(item -> "employee".equals(item.getSubjectType())).map(item -> item.getSubjectId()).distinct().toList();
    if (employees.size() > 1) { throw new ValidationException("平台账号关联了多个员工"); }
    return employees.stream().findFirst().flatMap(employee -> forEmployee(connectionId, employee)).map(DirectoryBinding::getExternalId);
  }

  private static DirectoryBinding requireActive(DirectoryBinding binding) {
    if (!Boolean.TRUE.equals(binding.getActive())) { throw new ValidationException("企业成员关联已失效，请管理员处理"); }
    return binding;
  }
}
