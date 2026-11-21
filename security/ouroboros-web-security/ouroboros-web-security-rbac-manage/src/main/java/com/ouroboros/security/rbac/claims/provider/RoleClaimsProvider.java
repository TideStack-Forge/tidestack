package com.ouroboros.security.rbac.claims.provider;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Priority;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsContext;
import com.ouroboros.security.claims.ClaimsProvider;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserRoleCenter;

/**
 * 从角色系统提取 Claims
 */
@Priority(150)
public class RoleClaimsProvider implements ClaimsProvider {

  @Override
  public Claims provide(User user, ClaimsContext context) {
    String userIdentity = user.getUserIdentity();
    List<Role> roles = UserRoleCenter.instance().getRolesByUser(userIdentity);
    
    List<Map<String, Object>> roleList = roles.stream()
        .map(role -> {
          Map<String, Object> map = new LinkedHashMap<>();
          map.put("id", role.getId());
          map.put("name", role.getName());
          return map;
        })
        .collect(Collectors.toList());

    return Claims.builder()
        .claim(StandardClaims.ROLES, roleList)
        .build();
  }

  @Override
  public Set<String> getProvidedClaimTypes() {
    return Collections.singleton(StandardClaims.ROLES);
  }
}
