package com.ouroboros.security.rbac.provider;

import java.util.*;

import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleProvider;
import com.ouroboros.security.rbac.model.AdministratorRoleModel;

@Component
public class AdministratorRoleProvider implements RoleProvider {
  private final AdministratorRoleModel administratorRole = new AdministratorRoleModel();

  public String getAdministratorRoleId() {
    return administratorRole.getId();
  }

  @Override
  public Optional<Role> getRole(String id) {
    return Optional.ofNullable(Objects.equals(id, administratorRole.getId()) ? administratorRole : null);
  }

  @Override
  public Map<String, Role> getAllRole() {
    return Collections.singletonMap(administratorRole.getId(), administratorRole);
  }
}
