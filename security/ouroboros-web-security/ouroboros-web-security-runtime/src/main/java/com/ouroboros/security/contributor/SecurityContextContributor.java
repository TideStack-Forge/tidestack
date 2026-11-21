package com.ouroboros.security.contributor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.ouroboros.expression.ExpressionContextContributor;
import com.ouroboros.script.ScriptContextContributor;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.User;

public class SecurityContextContributor implements ExpressionContextContributor, ScriptContextContributor {
  private final Map<String, Object> bindings = new HashMap<>();

  public SecurityContextContributor() {
    bindings.put("AuthenticationHolder", OuroborosAuthenticationHolder.class);
    bindings.put("AuthContext", new AuthContext() {
    });
  }

  @Override
  public Map<String, Object> getBindings() {
    return bindings;
  }

  public interface AuthContext {
    default String getUserId() {
      Optional<SecurityIdentity> identity = findIdentity();
      if (identity.isPresent()) {
        return identity.get().getUserIdentity();
      }

      Optional<OuroborosAuthentication> authentication = findAuthentication();
      if (authentication.isPresent()) {
        return authentication.get().getPrincipal();
      }

      return AnonymousAuthentication.getInstance().getPrincipal();
    }

    default User getUserDetail() {
      Optional<SecurityIdentity> identity = findIdentity();
      if (identity.isPresent()) {
        return identity.get().getUserDetail();
      }

      Optional<OuroborosAuthentication> authentication = findAuthentication();
      if (authentication.isPresent()) {
        return authentication.get().getUserDetail();
      }

      return AnonymousAuthentication.getInstance().getUserDetail();
    }

    default AccessControlParams getAuthorizationDecision() {
      return currentAuthentication().getAccessControlParams();
    }

    default AccessControlParams getAuthorizationDecision(String authKey) {
      return currentAuthentication().getAccessControlParams(authKey);
    }

    private OuroborosAuthentication currentAuthentication() {
      return findAuthentication().orElseGet(() -> findIdentity()
          .<OuroborosAuthentication>map(com.ouroboros.security.authentication.DefaultAuthentication::new)
          .orElse(AnonymousAuthentication.getInstance()));
    }

    default AccessControlParams getAccessControlParams() {
      return getAuthorizationDecision();
    }


    default AccessControlParams getAccessControlParams(String authKey) {
      return getAuthorizationDecision(authKey);
    }

    default List<String> getAllowedFields() {
      return getAccessControlParams().getAllowedFields();
    }

    default List<String> getDisallowedFields() {
      return getAccessControlParams().getDisallowedFields();
    }

    default Map<String, Object> getFilters() {
      return getAccessControlParams().getFilters();
    }

    static Optional<SecurityIdentity> findIdentity() {
      Optional<SecurityIdentity> identity = CurrentSecurityContextHolder.findIdentity();
      if (identity.isPresent()) {
        return identity;
      }

      return new SpringSecurityCurrentSecurityContext().findIdentity();
    }

    static Optional<OuroborosAuthentication> findAuthentication() {
      Optional<OuroborosAuthentication> authentication = CurrentSecurityContextHolder.findAuthentication();
      if (authentication.isPresent()) {
        return authentication;
      }

      return new SpringSecurityCurrentSecurityContext().findAuthentication();
    }

  }
}
