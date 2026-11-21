package com.ouroboros.businessmodel.security;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

/** Uses the platform identity and Authority context for business behavior data access. */
public final class BusinessBehaviorSecurity {
  private BusinessBehaviorSecurity() {
  }

  public static boolean hasAuthority(String authorityName) {
    Optional<OuroborosAuthentication> authentication = CurrentSecurityContextHolder.findAuthentication();
    if (authentication.isPresent()) {
      return authentication.get().isAuthenticated() && authentication.get().getAuthorities().stream()
          .anyMatch(authority -> authorityName.equals(authority.getAuthority()));
    }
    return CurrentSecurityContextHolder.findIdentity()
        .map(identity -> com.ouroboros.security.authorize.AuthorizationEvaluator.standard()
            .evaluate(identity, com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API, authorityName)
            instanceof com.ouroboros.security.authorize.AuthorizationDecision.Permit)
        .orElse(false);
  }

  public static <T> T withAuthority(String authorityName, java.util.function.Supplier<T> supplier) {
    return withAuthority(authorityName, null, supplier);
  }

  public static <T> T withAuthority(String authorityName, String modelFullName, java.util.function.Supplier<T> supplier) {
    Optional<SecurityIdentity> identity = CurrentSecurityContextHolder.findIdentity();
    Optional<OuroborosAuthentication> authentication = CurrentSecurityContextHolder.findAuthentication();
    CurrentSecurityContext scoped = new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return identity;
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return authentication.<OuroborosAuthentication>map(current -> new ScopedAuthentication(current, authorityName, modelFullName))
            .or(() -> identity.<OuroborosAuthentication>map(current -> new ScopedAuthentication(current, authorityName, modelFullName)));
      }
    };
    return CurrentSecurityContextHolder.withContext(scoped, supplier);
  }

  public static AccessControlParams accessControlParams(String authorityName) {
    return withAuthority(authorityName, () -> CurrentSecurityContextHolder.findAuthentication()
        .map(authentication -> authentication.getAccessControlParams(authorityName))
        .orElseGet(AccessControlParams::empty));
  }

  private static final class ScopedAuthentication implements OuroborosAuthentication {
    private final OuroborosAuthentication delegate;
    private final SecurityIdentity identity;
    private final String authorityName;
    private final String modelFullName;

    private ScopedAuthentication(OuroborosAuthentication delegate, String authorityName, String modelFullName) {
      this.delegate = delegate;
      this.identity = null;
      this.authorityName = authorityName;
      this.modelFullName = modelFullName;
    }

    private ScopedAuthentication(SecurityIdentity identity, String authorityName, String modelFullName) {
      this.delegate = null;
      this.identity = identity;
      this.authorityName = authorityName;
      this.modelFullName = modelFullName;
    }

    @Override
    public Optional<AccessControlParams> findScopedAccessControlParams(String dataAuthorityName) {
      if (modelFullName == null || List.of("search", "add", "update", "delete").stream()
          .noneMatch(action -> (modelFullName + "." + action).equalsIgnoreCase(dataAuthorityName))) {
        return Optional.empty();
      }
      return Optional.of(getAccessControlParams(authorityName));
    }

    @Override
    public SecurityIdentity getIdentity() {
      return delegate == null ? identity : delegate.getIdentity();
    }

    @Override
    public Optional<String> getPlatformSessionId() {
      return delegate == null ? Optional.empty() : delegate.getPlatformSessionId();
    }

    @Override
    public String getPrincipal() {
      return delegate == null ? identity.getPrincipal() : delegate.getPrincipal();
    }

    @Override
    public Object getCredentials() {
      return delegate == null ? null : delegate.getCredentials();
    }

    @Override
    public Object getDetails() {
      return delegate == null ? identity.getUserDetail() : delegate.getDetails();
    }

    @Override
    public boolean isAuthenticated() {
      return delegate == null || delegate.isAuthenticated();
    }

    @Override
    public void setAuthenticated(boolean authenticated) {
      if (delegate != null) {
        delegate.setAuthenticated(authenticated);
      }
    }

    @Override
    public User getUserDetail() {
      return delegate == null ? identity.getUserDetail() : delegate.getUserDetail();
    }

    @Override
    public List<? extends Role> getRoles() {
      return delegate == null ? identity.getRoles() : delegate.getRoles();
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return delegate == null
          ? getAccessControlParams(authorityName)
          : delegate.getAccessControlParams(authorityName);
    }

    @Override
    public AccessControlParams getAccessControlParams(String permissionName) {
      return delegate == null
          ? OuroborosAuthentication.super.getAccessControlParams(permissionName)
          : delegate.getAccessControlParams(permissionName);
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return delegate == null
          ? OuroborosAuthentication.super.getAuthorities()
          : delegate.getAuthorities();
    }
  }
}
