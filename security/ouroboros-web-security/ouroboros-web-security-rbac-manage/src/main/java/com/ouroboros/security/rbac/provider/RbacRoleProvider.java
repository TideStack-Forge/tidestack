package com.ouroboros.security.rbac.provider;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleProvider;
import com.ouroboros.security.rbac.model.RbacRoleModel;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;

@Component
public class RbacRoleProvider implements RoleProvider {

  @Autowired
  private RoleService roleService;

  @Autowired
  private RoleAuthorityService roleAuthorityService;

  @Override
  public Optional<Role> getRole(String id) {
    return roleService.getById(id)
        .map(roleEntity -> new RbacRoleModel(roleEntity, roleAuthorityService.getByRoleId(id)));
  }

  @Override
  public Map<String, Role> getAllRole() {
    return roleService.searchAll()
        .stream()
        .map(roleEntity -> new RbacRoleModel(roleEntity, roleAuthorityService.getByRoleId(roleEntity.getId())))
        .collect(Collectors.toMap(
            Role::getId,
            r -> r,
            (v1, v2) -> v2
        ));
  }
}
