package com.ouroboros.security.contributor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

class SecurityContextContributorCompatibilityTest {

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void authContext_namedPermission_usesAccessControlParamsFromAdaptedSpringAuthenticationRoles() {
    AccessControlParams expectedParams = buildNonEmptyAccessControlParams();
    Authority authority = buildAuthority("orders.read");
    OuroborosGrantedAuthority grantedAuthority = buildGrantedAuthority("orders.read", authority, expectedParams);
    User user = buildUser("user-1", Collections.singletonList(buildRole("role-1", Collections.singletonList(grantedAuthority))));

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-1", "secret", Collections.emptyList());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    SecurityContextContributor.AuthContext authContext = (SecurityContextContributor.AuthContext) new SecurityContextContributor()
        .getBindings()
        .get("AuthContext");

    assertParams(expectedParams, authContext.getAccessControlParams("orders.read"),
        "named-permission lookup should preserve ACL params when Spring Authentication is adapted via holder");
  }

  @Test
  void authContext_namedPermission_usesAccessControlParamsFromCurrentSecurityIdentityRoles() {
    AccessControlParams expectedParams = buildNonEmptyAccessControlParams();
    Authority authority = buildAuthority("orders.read");
    OuroborosGrantedAuthority grantedAuthority = buildGrantedAuthority("orders.read", authority, expectedParams);
    User user = buildUser("user-2", Collections.singletonList(buildRole("role-2", Collections.singletonList(grantedAuthority))));

    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(new DefaultSecurityIdentity(user)));

    SecurityContextContributor.AuthContext authContext = (SecurityContextContributor.AuthContext) new SecurityContextContributor()
        .getBindings()
        .get("AuthContext");

    assertParams(expectedParams, authContext.getAccessControlParams("orders.read"),
        "named-permission lookup should preserve ACL params when holder adapts a current security identity");
  }

  @Test
  void authContext_namedPermission_authorizationDecisionAlias_matchesLegacyLookupFromAdaptedSpringAuthenticationRoles() {
    AccessControlParams expectedParams = buildNonEmptyAccessControlParams();
    Authority authority = buildAuthority("orders.read");
    OuroborosGrantedAuthority grantedAuthority = buildGrantedAuthority("orders.read", authority, expectedParams);
    User user = buildUser("user-3", Collections.singletonList(buildRole("role-3", Collections.singletonList(grantedAuthority))));

    UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken("user-3", "secret", Collections.emptyList());
    authentication.setDetails(user);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    SecurityContextContributor.AuthContext authContext = (SecurityContextContributor.AuthContext) new SecurityContextContributor()
        .getBindings()
        .get("AuthContext");

    Method getAuthorizationDecision = assertDoesNotThrow(
        () -> authContext.getClass().getMethod("getAuthorizationDecision", String.class),
        "AuthContext should expose the alias helper seam for named authorization decisions");

    Object aliasDecision = assertDoesNotThrow(
        () -> getAuthorizationDecision.invoke(authContext, "orders.read"),
        "authorization-decision alias should be invocable from the adapted Spring authentication path");

    assertEquals(authContext.getAccessControlParams("orders.read"), aliasDecision,
        "authorization-decision alias should delegate to the legacy named ACL lookup during the pilot window");
  }

  @Test
  void authContext_prefersIdentitySeamForIdentityDetail_andAuthenticationSeamForAuthorizationHelpers() {
    AccessControlParams expectedCurrentParams = buildAccessControlParams(
        Collections.singletonList("id"),
        Collections.singletonList("password"),
        Collections.singletonMap("tenantId", "tenant-auth"));
    AccessControlParams expectedNamedParams = buildAccessControlParams(
        Collections.singletonList("amount"),
        Collections.singletonList("secretNote"),
        Collections.singletonMap("status", "APPROVED"));
    AccessControlParams identityNamedParams = buildAccessControlParams(
        Collections.singletonList("identity-only"),
        Collections.emptyList(),
        Collections.singletonMap("status", "IDENTITY"));

    User seamIdentityUser = buildUser("identity-user",
        Collections.singletonList(buildRole("identity-role",
            Collections.singletonList(buildGrantedAuthority("orders.read", buildAuthority("orders.read"), identityNamedParams)))));
    User authenticationUser = buildUser("authentication-user",
        Collections.singletonList(buildRole("auth-role",
            Collections.singletonList(buildGrantedAuthority("orders.read", buildAuthority("orders.read"), expectedNamedParams)))));

    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.of(new DefaultSecurityIdentity(seamIdentityUser));
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(buildAuthentication("authentication-user", authenticationUser, expectedCurrentParams));
      }
    });
    SecurityContextHolder.clearContext();

    SecurityContextContributor.AuthContext authContext = (SecurityContextContributor.AuthContext) new SecurityContextContributor()
        .getBindings()
        .get("AuthContext");

    assertEquals("identity-user", authContext.getUserId(),
        "identity helper should resolve from CurrentSecurityContextHolder.findIdentity() before any authentication fallback");
    assertSame(seamIdentityUser, authContext.getUserDetail(),
        "user-detail helper should prefer the current security identity seam over an auth-only fallback");

    assertSame(expectedCurrentParams, authContext.getAuthorizationDecision(),
        "current authorization decision should still come from the current-security-context authentication seam");
    assertSame(expectedCurrentParams, authContext.getAccessControlParams(),
        "legacy current ACL alias should delegate to getAuthorizationDecision()");
    assertEquals(expectedCurrentParams.getAllowedFields(), authContext.getAllowedFields(),
        "allowed-field helper should continue delegating to the current authorization decision");
    assertEquals(expectedCurrentParams.getDisallowedFields(), authContext.getDisallowedFields(),
        "disallowed-field helper should continue delegating to the current authorization decision");
    assertEquals(expectedCurrentParams.getFilters(), authContext.getFilters(),
        "filters helper should continue delegating to the current authorization decision");

    assertParams(expectedNamedParams, authContext.getAuthorizationDecision("orders.read"),
        "named authorization decision should continue using authentication ACL semantics through the seam");
    assertParams(expectedNamedParams, authContext.getAccessControlParams("orders.read"),
        "legacy named ACL alias should delegate to getAuthorizationDecision(String)");
  }

  @Test
  void authContext_defaultMethods_doNotDirectlyReadThroughCompatHolder() throws Exception {
    assertFalse(readSecurityContextContributorSource().contains("OuroborosAuthenticationHolder.getAuthentication()"),
        "AuthContext default methods should source identity/detail/authz through the current-security-context seam instead of direct holder reads");
  }

  private static User buildUser(String identity, List<Role> roles) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return identity;
      }

      @Override
      public List<Role> getRoles() {
        return roles;
      }
    };
  }

  private static void assertParams(AccessControlParams expected, AccessControlParams actual, String message) {
    assertEquals(expected.getFieldConstraint(), actual.getFieldConstraint(), message);
    assertEquals(expected.getRowConstraint(), actual.getRowConstraint(), message);
  }

  @Test
  void identityOnlyContextPreservesDelegatedBoundaryWithoutSpringAuthentication() {
    var params = buildAccessControlParams(List.of("id", "amount"), List.of(), Map.of());
    var user = buildUser("delegated-user", List.of(buildRole("reader", List.of(
        buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)))));
    var limit = buildAccessControlParams(List.of("id"), List.of(), Map.of());
    var identity = new DefaultSecurityIdentity(user,
        com.ouroboros.security.identity.AuthorizationBoundary.restricted("platform-api", Map.of("orders.read", limit)),
        new com.ouroboros.security.identity.CredentialContext(
            com.ouroboros.security.identity.CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api"));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(identity));
    SecurityContextHolder.clearContext();
    var context = (SecurityContextContributor.AuthContext) new SecurityContextContributor().getBindings().get("AuthContext");

    assertEquals("delegated-user", context.getUserId());
    assertEquals(List.of("id"), context.getAuthorizationDecision("orders.read").getAllowedFields());
    assertFalse(SecurityContextHolder.getContext().getAuthentication() != null);
  }

  private static Role buildRole(String id, List<? extends OuroborosGrantedAuthority> authorities) {
    return new Role() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getName() {
        return id;
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return authorities;
      }
    };
  }

  private static Authority buildAuthority(String fullName) {
    return new Authority() {
      @Override
      public String getFullName() {
        return fullName;
      }

      @Override
      public String getTitle() {
        return fullName;
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public List<String> getApis() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getUiViews() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getMenuItems() {
        return Collections.emptyList();
      }
    };
  }

  private static OuroborosGrantedAuthority buildGrantedAuthority(
      String authorityName,
      Authority authorityObject,
      AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authorityName;
      }

      @Override
      public Authority getAuthorityObject() {
        return authorityObject;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }

  private static AccessControlParams buildNonEmptyAccessControlParams() {
    return buildAccessControlParams(Collections.singletonList("id"), Collections.emptyList(), Collections.emptyMap());
  }

  private static AccessControlParams buildAccessControlParams(
      List<String> allowedFields,
      List<String> disallowedFields,
      Map<String, Object> filters) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return allowedFields;
      }

      @Override
      public List<String> getDisallowedFields() {
        return disallowedFields;
      }

      @Override
      public Map<String, Object> getFilters() {
        return filters;
      }
    };
  }

  private static OuroborosAuthentication buildAuthentication(
      String principal,
      User user,
      AccessControlParams currentParams) {
    return new OuroborosAuthentication() {
      @Override
      public String getName() {
        return principal;
      }

      @Override
      public Object getCredentials() {
        return null;
      }

      @Override
      public Object getDetails() {
        return user;
      }

      @Override
      public boolean isAuthenticated() {
        return true;
      }

      @Override
      public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      }

      @Override
      public String getPrincipal() {
        return principal;
      }

      @Override
      public User getUserDetail() {
        return user;
      }

      @Override
      public List<? extends Role> getRoles() {
        return user.getRoles();
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return currentParams;
      }
    };
  }

  private static String readSecurityContextContributorSource() throws Exception {
    Path moduleRelativePath = Paths.get("src", "main", "java", "com", "ouroboros", "security", "contributor",
        "SecurityContextContributor.java");
    Path repoRelativePath = Paths.get("security", "ouroboros-web-security", "ouroboros-web-security-runtime", "src",
        "main", "java", "com", "ouroboros", "security", "contributor", "SecurityContextContributor.java");
    Path sourcePath = Files.exists(moduleRelativePath) ? moduleRelativePath : repoRelativePath;
    return new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
  }
}
