package com.ouroboros.ability.auth.oidc.management;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.SecurityIdentity;

public final class ClientManagementAccess {
  public static final String SECURITY = "systemSetting.integrationManagement.applications.security";
  public static final String MANAGE = "systemSetting.integrationManagement.applications.manage";
  public static final String SETTINGS = "systemSetting.integrationManagement.applications.authServer.settings";
  public static final Set<String> MANAGEABLE_FIELDS = Set.of("clientName", "oidc.scopes", "apiAuthorization.allowedBoundary.permissions");
  private final SecurityIdentity identity;
  private final boolean security;
  private final List<AccessControlParams> grants;

  public ClientManagementAccess() {
    identity = CurrentSecurityContextHolder.findIdentity().orElse(null);
    security = permits(SECURITY);
    grants = permits(MANAGE) ? identity.getRoles().stream().flatMap(role -> role.getAuthorities().stream())
        .filter(grant -> MANAGE.equals(grant.getAuthority())).map(grant -> grant.getAccessControlParams()).toList() : List.of();
  }

  public boolean permits(String name) {
    return identity != null && identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM
        && AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, name)
            instanceof AuthorizationDecision.Permit;
  }

  public boolean security() { return security; }
  public void requireSecurity() { if (!security) { throw denied(); } }
  public void requireSettings() { if (!permits(SETTINGS)) { throw denied(); } }

  public boolean visible(String clientId) {
    return security || grants.stream().anyMatch(grant -> strings(grant.getFilters().get("clientIds")).contains(clientId));
  }

  public boolean mayChange(String clientId, String field, Collection<String> addedPermissions) {
    if (security) { return true; }
    return MANAGEABLE_FIELDS.contains(field) && grants.stream().anyMatch(grant ->
        strings(grant.getFilters().get("clientIds")).contains(clientId)
        && grant.getFieldConstraint().mode() == FieldConstraint.Mode.ONLY && grant.getFieldConstraint().allows(field)
        && (!field.equals("apiAuthorization.allowedBoundary.permissions")
            || strings(grant.getFilters().get("grantableAuthorityNames")).containsAll(addedPermissions)));
  }

  private static List<String> strings(Object value) {
    if (!(value instanceof Collection<?> values) || values.stream().anyMatch(item -> !(item instanceof String))) { return List.of(); }
    return values.stream().map(String.class::cast).filter(item -> !item.contains("*") && !item.isBlank()).toList();
  }

  public static AuthorizationDeniedException denied() {
    return new AuthorizationDeniedException("[CONFIGURATION_ACCESS_DENIED] 无权管理此认证配置");
  }
}
