package com.ouroboros.security.authorize.filter;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizeCheckFilterSupplierAuthorizationDecisionContextTest {
  private org.mockito.MockedStatic<com.ouroboros.security.authority.AuthorityCenter> registry;

  @org.junit.jupiter.api.BeforeEach
  void registerAuthority() {
    Authority definition = buildAuthentication("GET /api/orders").getAuthorities().getFirst().getAuthorityObject();
    registry = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class);
    registry.when(com.ouroboros.security.authority.AuthorityCenter::getAllAuthority)
        .thenReturn(java.util.Map.of(definition.getFullName(), definition));
    registry.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority(definition.getFullName()))
        .thenReturn(Optional.of(definition));
  }


  @AfterEach
  void cleanup() {
    registry.close();
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_treatsUninitializedMatcherListsAsEmpty_atRuntimeBoundary() throws Exception {
    AuthorizeCheckFilterSupplier supplier = new AuthorizeCheckFilterSupplier();
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());

    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    SecurityContextHolder.getContext().setAuthentication(
        OuroborosAuthenticationHolder.toSpringAuthentication(buildAuthentication("GET /api/orders")));

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    supplier.getAuthorizeFilter().doFilter(request, response, (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest()));

    assertTrue(inChain.get().isPresent(),
        "runtime authorize boundary should still execute when matcher lists are not initialized");
    assertEquals("GET", inChain.get().get().getMethod());
    assertEquals("/api/orders", inChain.get().get().getPath());
    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
        "decision context should still be cleaned after boundary completion");
  }

  @Test
  void doFilter_allowsErrorEndpointFromAnonymousProviderBeforeLifecycleRefresh() throws Exception {
    AuthorizeCheckFilterSupplier supplier = new AuthorizeCheckFilterSupplier();
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());
    AnonymousApiProvider errorAnonymousApiProvider = () -> Collections.singletonList("* /error");
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());
    ReflectionTestUtils.setField(
        supplier,
        "anonymousApiProviders",
        Collections.singletonList(errorAnonymousApiProvider)
    );
    ReflectionTestUtils.setField(supplier, "anyAuthenticationApiProviders", Collections.emptyList());

    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    SecurityContextHolder.clearContext();

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    supplier.getAuthorizeFilter().doFilter(request, response, (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest()));

    assertTrue(inChain.get().isPresent(),
        "bootstrap-safe anonymous provider should allow /error before matcher lists are refreshed");
    assertEquals("GET", inChain.get().get().getMethod());
    assertEquals("/error", inChain.get().get().getPath());
    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
        "decision context should still be cleaned after anonymous /error request");
  }

  @Test
  void doFilter_publishesCurrentRequestInAuthorizationBoundary_andRestoresOuterDecisionContext() throws Exception {
    AuthorizeCheckFilterSupplier supplier = new AuthorizeCheckFilterSupplier();
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());
    ReflectionTestUtils.setField(supplier, "anonymousApiRequestMatcherList", Collections.emptyList());
    ReflectionTestUtils.setField(supplier, "anyAuthenticationApiRequestMatcherList", Collections.emptyList());

    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    SecurityContextHolder.getContext().setAuthentication(
        OuroborosAuthenticationHolder.toSpringAuthentication(buildAuthentication("GET /api/orders")));
    AuthorizationDecisionContextHolder.setCurrentRequest("PATCH", "/api/outer");

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    supplier.getAuthorizeFilter().doFilter(request, response, (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest()));

    assertTrue(inChain.get().isPresent(),
        "authorize runtime boundary should publish request-scoped decision context");
    assertEquals("GET", inChain.get().get().getMethod());
    assertEquals("/api/orders", inChain.get().get().getPath());

    Optional<AuthorizationDecisionContextHolder.CurrentRequest> restored =
        AuthorizationDecisionContextHolder.findCurrentRequest();
    assertTrue(restored.isPresent(),
        "after nested authorization boundary exits, outer decision context should be restored");
    assertEquals("PATCH", restored.get().getMethod());
    assertEquals("/api/outer", restored.get().getPath());
  }

  @Test
  void doFilter_usesCurrentSecurityContextAuthentication_withoutCompatHolderSourceOfTruth() throws Exception {
    AuthorizeCheckFilterSupplier supplier = new AuthorizeCheckFilterSupplier();
    ReflectionTestUtils.setField(supplier, "servletContext", new MockServletContext());
    ReflectionTestUtils.setField(supplier, "anonymousApiRequestMatcherList", Collections.emptyList());
    ReflectionTestUtils.setField(supplier, "anyAuthenticationApiRequestMatcherList", Collections.emptyList());

    CurrentSecurityContextHolder.setDelegate(new CurrentSecurityContext() {
      @Override
      public Optional<SecurityIdentity> findIdentity() {
        return Optional.empty();
      }

      @Override
      public Optional<OuroborosAuthentication> findAuthentication() {
        return Optional.of(buildAuthentication("GET /api/orders"));
      }
    });
    SecurityContextHolder.clearContext();

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    supplier.getAuthorizeFilter().doFilter(request, response, (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest()));

    assertTrue(inChain.get().isPresent(),
        "authorize filter should accept authentication supplied by CurrentSecurityContextHolder");
    assertFalse(SecurityContextHolder.getContext().getAuthentication() != null,
        "the request must not require a compatibility Spring authentication");
  }

  private static OuroborosAuthentication buildAuthentication(String api) {
    Authority authority = new Authority() {
      @Override
      public String getFullName() {
        return "orders.read";
      }

      @Override
      public String getTitle() {
        return "orders.read";
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public List<String> getApis() {
        return Collections.singletonList(api);
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

    OuroborosGrantedAuthority grantedAuthority = new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return "orders.read";
      }

      @Override
      public Authority getAuthorityObject() {
        return authority;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return AccessControlParams.empty();
      }
    };

    Role role = new Role() {
      public String getId() { return "orders-reader"; }
      public String getName() { return "orders-reader"; }
      public List<? extends OuroborosGrantedAuthority> getAuthorities() { return List.of(grantedAuthority); }
    };

    return new OuroborosAuthentication() {
      @Override
      public String getPrincipal() {
        return "user-1";
      }

      @Override
      public User getUserDetail() {
        return new User() {
          public String getUserIdentity() { return "user-1"; }
          public List<Role> getRoles() { return List.of(role); }
        };
      }

      @Override
      public List<? extends Role> getRoles() {
        return List.of(role);
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return AccessControlParams.empty();
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return Collections.singletonList(grantedAuthority);
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
        return true;
      }

      @Override
      public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
      }

      @Override
      public String getName() {
        return "user-1";
      }
    };
  }
}
