package com.ouroboros.security.authorize;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.util.Json;

/** The single role-union and credential-intersection algorithm. No user-specific decision cache. */
public final class AuthorizationEvaluator {
  public static final String PLATFORM_API = "platform-api";
  private static final AuthorizationEvaluator STANDARD = new AuthorizationEvaluator(AuthorityCenter::getAuthority);
  private final Function<String, Optional<Authority>> authorities;

  public AuthorizationEvaluator(Function<String, Optional<Authority>> authorities) {
    this.authorities = Objects.requireNonNull(authorities);
  }

  public static AuthorizationEvaluator standard() { return STANDARD; }

  public AuthorizationDecision evaluate(SecurityIdentity identity, String resource, String authorityName) {
    if (identity == null) { return deny(AuthorizationDecision.Reason.MISSING_IDENTITY); }
    Authority definition = authorities.apply(authorityName).orElse(null);
    if (definition == null) { return deny(AuthorizationDecision.Reason.UNKNOWN_AUTHORITY); }
    var grants = identity.getRoles().stream().flatMap(role -> role.getAuthorities().stream())
        .filter(grant -> Objects.equals(authorityName, grant.getAuthority()))
        .map(grant -> grant.getAccessControlParams()).reduce(AccessControlParams::or);
    if (grants.isEmpty()) { return deny(AuthorizationDecision.Reason.NOT_GRANTED); }
    var boundary = identity.getAuthorizationBoundary();
    if (boundary.mode() == AuthorizationBoundary.Mode.UNRESTRICTED) {
      return new AuthorizationDecision.Permit(ResolvedAccessControlParams.snapshot(grants.get(), definition.getAccessControlDefine()));
    }
    if (!Objects.equals(resource, boundary.resource())
        || !Objects.equals(resource, identity.getCredentialContext().resource())) {
      return deny(AuthorizationDecision.Reason.RESOURCE_MISMATCH);
    }
    if (!definition.isDelegable()) { return deny(AuthorizationDecision.Reason.NOT_DELEGABLE); }
    AccessControlParams limit = boundary.permissions().get(authorityName);
    if (limit == null) { return deny(AuthorizationDecision.Reason.BOUNDARY_EXCLUDED); }
    return new AuthorizationDecision.Permit(ResolvedAccessControlParams.snapshot(
        AccessControlParams.and(grants.get(), limit), definition.getAccessControlDefine()));
  }

  public Map<String, AccessControlParams> effectiveAuthorities(SecurityIdentity identity, String resource) {
    Map<String, AccessControlParams> result = new LinkedHashMap<>();
    if (identity == null) { return result; }
    identity.getRoles().stream().flatMap(role -> role.getAuthorities().stream())
        .map(grant -> grant.getAuthority()).distinct().forEach(name -> {
          if (evaluate(identity, resource, name) instanceof AuthorizationDecision.Permit permit) {
            result.put(name, permit.params());
          }
        });
    return java.util.Collections.unmodifiableMap(result);
  }

  /** Route entry and data parameters are distinct: platform routes retain their original any-grant rule. */
  public boolean permitsRequest(SecurityIdentity identity, String resource, Collection<String> names) {
    if (identity == null || names == null) { return false; }
    if (identity.getCredentialContext().kind() == CredentialContext.Kind.DELEGATED) {
      return evaluateRequest(identity, resource, names) instanceof AuthorizationDecision.Permit;
    }
    return names.stream().anyMatch(name -> evaluate(identity, resource, name) instanceof AuthorizationDecision.Permit);
  }

  public AuthorizationDecision evaluateRequest(SecurityIdentity identity, String resource, Collection<String> names) {
    if (names == null || names.isEmpty()) { return deny(AuthorizationDecision.Reason.MISSING_DECISION_TARGET); }
    AccessControlParams combined = null;
    Authority target = null;
    for (String name : names) {
      if (evaluate(identity, resource, name) instanceof AuthorizationDecision.Permit permit) {
        Authority definition = authorities.apply(name).orElseThrow();
        if (target != null && (!Objects.equals(target.getParent(), definition.getParent())
            || !Json.toJsonString(target.getAccessControlDefine()).equals(Json.toJsonString(definition.getAccessControlDefine())))) {
          return deny(AuthorizationDecision.Reason.AMBIGUOUS_DECISION_TARGET);
        }
        target = definition;
        combined = combined == null ? permit.params() : AccessControlParams.or(combined, permit.params());
      }
    }
    return combined == null ? deny(AuthorizationDecision.Reason.NOT_GRANTED)
        : new AuthorizationDecision.Permit(ResolvedAccessControlParams.snapshot(combined, target.getAccessControlDefine()));
  }

  private AuthorizationDecision deny(AuthorizationDecision.Reason reason) { return new AuthorizationDecision.Deny(reason); }
}
