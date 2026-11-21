package com.ouroboros.security.identity;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;

/** Immutable credential limit. An empty restricted map grants no authorities. */
public record AuthorizationBoundary(Mode mode, String resource, Map<String, ResolvedAccessControlParams> permissions) {
  public enum Mode { UNRESTRICTED, RESTRICTED }

  public AuthorizationBoundary {
    java.util.Objects.requireNonNull(mode);
    permissions = Map.copyOf(permissions);
    if (mode == Mode.UNRESTRICTED && (resource != null || !permissions.isEmpty())) {
      throw new IllegalArgumentException("无限制边界不能包含限制载荷");
    }
    if (mode == Mode.RESTRICTED && (resource == null || resource.isBlank())) {
      throw new IllegalArgumentException("受限边界必须指定资源");
    }
    if (permissions.keySet().stream().anyMatch(name -> name.isBlank() || name.contains("*"))) {
      throw new IllegalArgumentException("边界必须使用精确权限全名");
    }
  }

  public static AuthorizationBoundary unrestricted() {
    return new AuthorizationBoundary(Mode.UNRESTRICTED, null, Map.of());
  }

  public static AuthorizationBoundary restricted(String resource, Map<String, ? extends AccessControlParams> permissions) {
    Map<String, ResolvedAccessControlParams> copied = new LinkedHashMap<>();
    permissions.forEach((name, params) -> copied.put(name, ResolvedAccessControlParams.snapshot(params)));
    return new AuthorizationBoundary(Mode.RESTRICTED, resource, copied);
  }
}
