package com.ouroboros.security;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

public class OuroborosAuthenticationHolder {

  public static OuroborosAuthentication getAuthentication() {
    Optional<OuroborosAuthentication> seamAuthentication = CurrentSecurityContextHolder.findAuthentication();
    if (seamAuthentication.isPresent()) {
      return seamAuthentication.get();
    }

    Optional<SecurityIdentity> seamIdentity = CurrentSecurityContextHolder.findIdentity();
    if (seamIdentity.isPresent()) { return adapt(seamIdentity.get()); }

    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication != null) { return adapt(authentication); }

    return AnonymousAuthentication.getInstance();
  }

  public static Authentication toSpringAuthentication(OuroborosAuthentication authentication) {
    if (authentication == null) {
      return null;
    }

    if (authentication instanceof Authentication) {
      return (Authentication) authentication;
    }

    return new SpringWrappedAuthentication(authentication);
  }

  public static OuroborosAuthentication adapt(Authentication authentication) {
    if (authentication instanceof OuroborosAuthentication ouroborosAuthentication) {
      return ouroborosAuthentication;
    }

    if (authentication instanceof SpringWrappedAuthentication) {
      return ((SpringWrappedAuthentication) authentication).delegate;
    }

    return new OuroborosAuthentication() {

      @Override
      public String getName() {
        return authentication.getName();
      }

      @Override
      public SecurityIdentity getIdentity() {
        if (authentication.getDetails() instanceof SecurityIdentity identity) { return identity; }
        if (authentication.getPrincipal() instanceof SecurityIdentity identity) { return identity; }
        return OuroborosAuthentication.super.getIdentity();
      }

      @Override
      public Object getCredentials() {
        return authentication.getCredentials();
      }

      @Override
      public Object getDetails() {
        return authentication.getDetails();
      }

      @Override
      public User getUserDetail() {
        if (authentication.getDetails() instanceof SecurityIdentity identity) { return identity.getUserDetail(); }
        if (authentication.getPrincipal() instanceof SecurityIdentity identity) { return identity.getUserDetail(); }
        if (authentication.getDetails() instanceof User user) { return user; }
        return authentication.getPrincipal() instanceof User user ? user : null;
      }

      @Override
      public List<? extends Role> getRoles() {
        if (authentication.getDetails() instanceof SecurityIdentity identity) { return identity.getRoles(); }
        if (authentication.getPrincipal() instanceof SecurityIdentity identity) { return identity.getRoles(); }
        User user = getUserDetail();
        return user == null ? List.of() : user.getRoles();
      }

      @Override
      public String getPrincipal() {
        Object principal = authentication.getPrincipal();
        if (principal == null) {
          return null;
        }

        if (principal instanceof String principalString) {
          return principalString;
        }

        return principal.toString();
      }

      @Override
      public boolean isAuthenticated() {
        return authentication.isAuthenticated();
      }

      @Override
      public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        authentication.setAuthenticated(isAuthenticated);
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        if (!isAuthenticated() || getIdentity() == null) {
          throw new com.ouroboros.security.exception.AuthorizationDeniedException("MISSING_IDENTITY");
        }
        return new com.ouroboros.security.authentication.DefaultAuthentication(getIdentity()).getAccessControlParams();
      }
    };
  }

  public static OuroborosAuthentication adapt(SecurityIdentity identity) {
    return new com.ouroboros.security.authentication.DefaultAuthentication(identity);
  }

  public static String getPrincipal() {
    return getAuthentication().getPrincipal();
  }

  public static String getCurrentUserIdentity() {
    return getPrincipal();
  }

  private static final class SpringWrappedAuthentication implements Authentication {

    private final OuroborosAuthentication delegate;

    private SpringWrappedAuthentication(OuroborosAuthentication delegate) {
      this.delegate = delegate;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return delegate.getAuthorities().stream().map(authority -> {
        if (authority instanceof GrantedAuthority) {
          return (GrantedAuthority) authority;
        }
        return (GrantedAuthority) authority::getAuthority;
      }).collect(Collectors.toList());
    }

    @Override
    public Object getCredentials() {
      return delegate.getCredentials();
    }

    @Override
    public Object getDetails() {
      return delegate.getDetails();
    }

    @Override
    public Object getPrincipal() {
      return delegate.getPrincipal();
    }

    @Override
    public boolean isAuthenticated() {
      return delegate.isAuthenticated();
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      delegate.setAuthenticated(isAuthenticated);
    }

    @Override
    public String getName() {
      return delegate.getName();
    }
  }
}
