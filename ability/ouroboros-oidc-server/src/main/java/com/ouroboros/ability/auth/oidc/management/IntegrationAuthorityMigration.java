package com.ouroboros.ability.auth.oidc.management;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.Transactional;

import com.ouroboros.exception.InternalException;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;

/** Maintenance-only, in-place rename through RBAC; grants and their restrictions are never recomputed. */
public class IntegrationAuthorityMigration {
  private static final Map<String, String> RENAMES = Map.of(
      "systemSetting.authClients.security", ClientManagementAccess.SECURITY,
      "systemSetting.authClients.manage", ClientManagementAccess.MANAGE,
      "systemSetting.authServer.settings", ClientManagementAccess.SETTINGS);
  private final RoleService roles;
  private final RoleAuthorityService authorities;

  public IntegrationAuthorityMigration(RoleService roles, RoleAuthorityService authorities) {
    this.roles = roles; this.authorities = authorities;
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  @Transactional
  public void migrate() {
    CurrentSecurityContextHolder.withIdentity(SystemSecurityIdentities.system(), () -> {
      for (var role : roles.searchAll()) {
        for (var existing : authorities.getByRoleId(role.getId())) {
          var params = new LinkedHashMap<>(existing.getAccessControlParams());
          boolean changed = false;
          if (params.get("filters") instanceof Map<?, ?> filters && filters.get("grantableAuthorityNames") instanceof List<?> names) {
            var migrated = names.stream().map(value -> value instanceof String name ? rename(name) : value).toList();
            if (!migrated.equals(names)) {
              Map<String, Object> updated = new LinkedHashMap<>();
              filters.forEach((key, value) -> updated.put(String.valueOf(key), value));
              updated.put("grantableAuthorityNames", new ArrayList<>(migrated));
              params.put("filters", updated); changed = true;
            }
          }
          String renamed = rename(existing.getAuthority());
          if (changed || !renamed.equals(existing.getAuthority())) {
            var replacement = new RoleAuthorityMapEntity();
            replacement.setId(existing.getId()); replacement.setRole(existing.getRole());
            replacement.setAuthority(renamed); replacement.setAccessControlParams(params);
            if (!authorities.update(existing.getId(), replacement)) {
              throw new InternalException("角色权限迁移未写入，停止切换: " + existing.getId());
            }
          }
        }
      }
      return null;
    });
  }

  private String rename(String name) {
    if (RENAMES.containsKey(name)) { return RENAMES.get(name); }
    if (name.startsWith("systemSetting.authClients.") || name.startsWith("systemSetting.authServer.")) {
      throw new InternalException("旧命名空间含有无法等价迁移的权限引用，请在维护窗口核对: " + name);
    }
    return name;
  }
}
