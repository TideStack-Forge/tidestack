package com.ouroboros.security.authorize.filter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.util.pattern.PatternParseException;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.UnauthenticatedException;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizeCheckFilterSupplierPathMatchCharacterizationTest {
  private org.mockito.MockedStatic<com.ouroboros.security.authority.AuthorityCenter> registry;

  @org.junit.jupiter.api.BeforeEach
  void registerDefinitions() {
    registry = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class);
  }

  @AfterEach
  void cleanup() {
    registry.close();
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void authorityApi_allowsExactPath() throws Exception {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis("GET /api/orders");

    assertAllowed(supplier, request("GET", "/api/orders"));
  }

  @Test
  void authenticatedWithoutIdentityIsRejectedBeforeAuthorization() {
    var authentication = org.mockito.Mockito.mock(OuroborosAuthentication.class);
    org.mockito.Mockito.when(authentication.isAuthenticated()).thenReturn(true);
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      public Optional<SecurityIdentity> findIdentity() { return Optional.empty(); }
      public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(authentication); }
    });
    assertThrows(UnauthenticatedException.class, () -> assertAllowed(
        supplier(List.of(), List.of("GET /api/orders")), request("GET", "/api/orders")));
  }

  @Test
  void authorityApi_allowsPathPatternVariable() throws Exception {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis("GET /api/orders/{id}");

    assertAllowed(supplier, request("GET", "/api/orders/42"));
  }

  @Test
  void authorityApi_deniesMethodMismatch() {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis("GET /api/orders/{id}");

    assertThrows(AccessDeniedException.class,
        () -> assertAllowed(supplier, request("POST", "/api/orders/42")));
  }

  @Test
  void approvalCenterAuthorityApisAllowAdvancedActionSelectorPaths() throws Exception {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis(
        "GET /api/approval-workflow/task/{taskId}/transfer-targets",
        "GET /api/approval-workflow/task/{taskId}/add-sign-targets",
        "GET /api/approval-workflow/task/{taskId}/free-reject-targets",
        "GET /api/approval-workflow/task/{taskId}/free-approval-targets");

    assertAllowed(supplier, request("GET", "/api/approval-workflow/task/task-1/transfer-targets"));
    assertAllowed(supplier, request("GET", "/api/approval-workflow/task/task-1/add-sign-targets"));
    assertAllowed(supplier, request("GET", "/api/approval-workflow/task/task-1/free-reject-targets"));
    assertAllowed(supplier, request("GET", "/api/approval-workflow/task/task-1/free-approval-targets"));
  }

  @Test
  void approvalCenterAuthorityApisStillDenyWrongMethodOrMissingAuthority() {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis("GET /api/approval-workflow/task/{taskId}/transfer-targets");

    assertThrows(AccessDeniedException.class,
        () -> assertAllowed(supplier, request("POST", "/api/approval-workflow/task/task-1/transfer-targets")));

    authenticateWithoutAuthorities();
    assertThrows(AccessDeniedException.class,
        () -> assertAllowed(supplier, request("GET", "/api/approval-workflow/task/task-1/transfer-targets")));
  }

  @Test
  void authorityApi_propagatesUnsupportedMiddleWildcardParseFailure() {
    AuthorizeCheckFilterSupplier supplier = supplier(Collections.emptyList(), Collections.emptyList());
    authenticateWithApis("GET /api/**/detail");

    assertThrows(PatternParseException.class,
        () -> assertAllowed(supplier, request("GET", "/api/orders/42/detail")));
  }

  @Test
  void anonymousApi_usesAntFallbackForMiddleWildcard() throws Exception {
    AuthorizeCheckFilterSupplier supplier =
        supplier(Collections.singletonList("GET /public/**/status"), Collections.emptyList());
    CurrentSecurityContextHolder.setDelegate(() -> Optional.empty());

    assertAllowed(supplier, request("GET", "/public/orders/42/status"));
  }

  @Test
  void anonymousApi_fallbackStillRequiresConfiguredMethod() {
    AuthorizeCheckFilterSupplier supplier =
        supplier(Collections.singletonList("GET /public/**/status"), Collections.emptyList());
    CurrentSecurityContextHolder.setDelegate(() -> Optional.empty());

    assertThrows(UnauthenticatedException.class,
        () -> assertAllowed(supplier, request("POST", "/public/orders/42/status")));
  }

  @Test
  void anyAuthenticationApi_usesAntFallbackForMiddleWildcard() throws Exception {
    AuthorizeCheckFilterSupplier supplier =
        supplier(Collections.emptyList(), Collections.singletonList("GET /account/**/profile"));
    authenticateWithoutAuthorities();

    assertAllowed(supplier, request("GET", "/account/orders/42/profile"));
  }

  @Test
  void anyAuthenticationApi_fallbackStillRequiresConfiguredMethod() {
    AuthorizeCheckFilterSupplier supplier =
        supplier(Collections.emptyList(), Collections.singletonList("GET /account/**/profile"));
    authenticateWithoutAuthorities();

    assertThrows(AccessDeniedException.class,
        () -> assertAllowed(supplier, request("POST", "/account/orders/42/profile")));
  }

  private static AuthorizeCheckFilterSupplier supplier(List<String> anonymousApis, List<String> anyAuthenticationApis) {
    AuthorizeCheckFilterSupplier supplier = new AuthorizeCheckFilterSupplier();
    AnonymousApiProvider anonymousApiProvider = () -> anonymousApis;
    AnyAuthenticationApiProvider anyAuthenticationApiProvider = () -> anyAuthenticationApis;
    ReflectionTestUtils.setField(supplier, "servletContext", new org.springframework.mock.web.MockServletContext());
    ReflectionTestUtils.setField(
        supplier,
        "anonymousApiProviders",
        anonymousApis.isEmpty() ? Collections.emptyList() : Collections.singletonList(anonymousApiProvider)
    );
    ReflectionTestUtils.setField(
        supplier,
        "anyAuthenticationApiProviders",
        anyAuthenticationApis.isEmpty()
            ? Collections.emptyList()
            : Collections.singletonList(anyAuthenticationApiProvider)
    );
    return supplier;
  }

  private static MockHttpServletRequest request(String method, String path) {
    return new MockHttpServletRequest(method, path);
  }

  private static void assertAllowed(
      AuthorizeCheckFilterSupplier supplier,
      MockHttpServletRequest request
  ) throws Exception {
    AtomicBoolean invoked = new AtomicBoolean();
    supplier.getAuthorizeFilter().doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> invoked.set(true)
    );
    assertTrue(invoked.get(), "matching request should reach the filter chain");
  }

  private void authenticateWithApis(String... apis) {
    BaseAuthority authority = new BaseAuthority();
    authority.setFullName("test.authority");
    authority.setApis(Arrays.asList(apis));
    registry.when(com.ouroboros.security.authority.AuthorityCenter::getAllAuthority)
        .thenReturn(java.util.Map.of(authority.getFullName(), authority));
    registry.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority(authority.getFullName()))
        .thenReturn(Optional.of(authority));
    OuroborosGrantedAuthority grantedAuthority = new TestGrantedAuthority(authority);
    setAuthentication(Collections.singletonList(grantedAuthority));
  }

  private static void authenticateWithoutAuthorities() {
    setAuthentication(Collections.emptyList());
  }

  private static void setAuthentication(List<? extends OuroborosGrantedAuthority> authorities) {
    OuroborosAuthentication authentication = new TestAuthentication(authorities);
    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(authentication);
      }
    });
  }

  private static final class TestGrantedAuthority implements OuroborosGrantedAuthority {

    private final Authority authority;

    private TestGrantedAuthority(Authority authority) {
      this.authority = authority;
    }

    @Override
    public String getAuthority() {
      return authority.getFullName();
    }

    @Override
    public Authority getAuthorityObject() {
      return authority;
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }
  }

  private static final class TestAuthentication implements OuroborosAuthentication {

    private final List<? extends OuroborosGrantedAuthority> authorities;
    private boolean authenticated = true;

    private TestAuthentication(List<? extends OuroborosGrantedAuthority> authorities) {
      this.authorities = authorities;
    }

    @Override
    public String getPrincipal() {
      return "test-user";
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getDetails() {
      return null;
    }

    @Override
    public boolean isAuthenticated() {
      return authenticated;
    }

    @Override
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      this.authenticated = isAuthenticated;
    }

    @Override
    public User getUserDetail() {
      return new User() {
        public String getUserIdentity() { return "test-user"; }
        public List<Role> getRoles() { return List.copyOf(TestAuthentication.this.getRoles()); }
      };
    }

    @Override
    public List<? extends Role> getRoles() {
      return List.of(new Role() {
        public String getId() { return "test-role"; }
        public String getName() { return "test-role"; }
        public List<? extends OuroborosGrantedAuthority> getAuthorities() { return authorities; }
      });
    }

    @Override
    public AccessControlParams getAccessControlParams() {
      return AccessControlParams.empty();
    }

    @Override
    public List<? extends OuroborosGrantedAuthority> getAuthorities() {
      return authorities;
    }
  }
}
