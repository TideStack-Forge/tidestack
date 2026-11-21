package com.ouroboros.security.rbac.provider;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleProvider;
import com.ouroboros.security.rbac.store.BuiltinRoleDefinitionStore;

@Component
@Order(50)
public class BuiltinRoleProvider implements RoleProvider {

  private final BuiltinRoleDefinitionStore builtinRoleDefinitionStore;

  public BuiltinRoleProvider(BuiltinRoleDefinitionStore builtinRoleDefinitionStore) {
    this.builtinRoleDefinitionStore = builtinRoleDefinitionStore;
  }

  @Override
  public Optional<Role> getRole(String id) {
    return builtinRoleDefinitionStore.findById(id).map(role -> (Role) role);
  }

  @Override
  public Map<String, Role> getAllRole() {
    return builtinRoleDefinitionStore.loadAll().stream()
        .collect(Collectors.toMap(Role::getId, role -> role, (left, right) -> right));
  }
}
