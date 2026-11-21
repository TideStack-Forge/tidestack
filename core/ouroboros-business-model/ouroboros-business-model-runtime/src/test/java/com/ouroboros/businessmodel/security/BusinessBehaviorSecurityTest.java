package com.ouroboros.businessmodel.security;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BusinessBehaviorSecurityTest {
  @Test
  void actionScopePreservesDelegatedIdentityAndIntersectsItsPermissionForBothContextForms() {
    var permission = definition();
    var role = mock(Role.class);
    doReturn(List.of(new OuroborosAuthentication.EffectiveAuthority(permission, ResolvedAccessControlParams.all())))
        .when(role).getAuthorities();
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("delegate");
    when(user.getRoles()).thenReturn(List.of(role));
    var limit = new ResolvedAccessControlParams(FieldConstraint.only(Set.of("id", "status")),
        RowConstraint.filter(Map.of("id", "42")));
    var credential = new CredentialContext(CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api");

    try (var definitions = mockStatic(AuthorityCenter.class)) {
      definitions.when(() -> AuthorityCenter.getAuthority("orders.workflow")).thenReturn(Optional.of(permission));
      for (boolean authenticated : List.of(false, true)) {
        for (boolean permitted : List.of(false, true)) {
          var boundary = AuthorizationBoundary.restricted("platform-api", permitted ? Map.of("orders.workflow", limit) : Map.of());
          var identity = new DefaultSecurityIdentity(user, boundary, credential);
          OuroborosAuthentication authentication = new OuroborosAuthentication() {
            public String getPrincipal() { return "delegate"; }
            public Object getCredentials() { return null; }
            public Object getDetails() { return user; }
            public boolean isAuthenticated() { return true; }
            public void setAuthenticated(boolean value) { }
            public User getUserDetail() { return user; }
            public List<Role> getRoles() { return List.of(role); }
            public ResolvedAccessControlParams getAccessControlParams() { return limit; }
            public SecurityIdentity getIdentity() { return identity; }
          };
          var context = new CurrentSecurityContext() {
            public Optional<SecurityIdentity> findIdentity() { return Optional.of(identity); }
            public Optional<OuroborosAuthentication> findAuthentication() {
              return authenticated ? Optional.of(authentication) : Optional.empty();
            }
          };
          CurrentSecurityContextHolder.withContext(context, () -> {
            assertEquals(permitted, BusinessBehaviorSecurity.hasAuthority("orders.workflow"));
            return BusinessBehaviorSecurity.withAuthority("orders.workflow", "orders", () -> {
              var scoped = CurrentSecurityContextHolder.findAuthentication().orElseThrow();
              assertSame(identity, scoped.getIdentity());
              assertTrue(scoped.findScopedAccessControlParams("other.search").isEmpty());
              if (permitted) {
                var params = scoped.findScopedAccessControlParams("orders.search").orElseThrow();
                assertEquals(limit.getFieldConstraint(), params.getFieldConstraint());
                assertEquals(limit.getRowConstraint(), params.getRowConstraint());
              } else {
                assertThrows(AuthorizationDeniedException.class, () -> scoped.findScopedAccessControlParams("orders.search"));
              }
              return null;
            });
          });
        }
      }
    }
  }

  private BaseAuthority definition() {
    var definition = new BaseAuthority();
    definition.setFullName("orders.workflow");
    definition.setDelegable(true);
    return definition;
  }
}
