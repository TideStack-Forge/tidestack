package com.ouroboros.security.authentication.filter;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import io.vavr.control.Either;
import io.vavr.control.Try;

import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.exception.TokenException;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenPair;
import com.ouroboros.security.token.TokenService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenAuthenticationFilterSupplierAuthorizationDecisionContextDelegationTest {

  @AfterEach
  void cleanup() {
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_delegatesDecisionLookupToRequestContextHolder_insteadOfRetainingRequestOnAuthentication() throws Exception {
    AccessControlParams expectedParams = buildNonEmptyAccessControlParams();
    User user = buildUser("user-1", Collections.singletonList(buildRole(
        "role-1",
        Collections.singletonList(buildGrantedAuthority(
            "orders.read",
            buildAuthority("orders.read", Collections.singletonList("GET /api/orders")),
            expectedParams)))));

    TokenAuthenticationFilterSupplier supplier = new TokenAuthenticationFilterSupplier();
    ReflectionTestUtils.setField(supplier, "authorizeCheckFilterSupplier",
        new com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier());
    ReflectionTestUtils.setField(supplier, "tokenService", new StubTokenService(new TokenInfo(
        "user-1",
        "session-1",
        Collections.emptyMap(),
        System.currentTimeMillis() + 60_000L)));
    ReflectionTestUtils.setField(supplier, "userCenter", new StubUserCenter(user));

    AuthenticationFilter filter = supplier.getAuthenticationFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    request.addHeader("Authorization", "Bearer access-token");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<DefaultAuthentication> authenticationInChain = new AtomicReference<DefaultAuthentication>();
    AtomicReference<AccessControlParams> paramsInChain = new AtomicReference<AccessControlParams>(AccessControlParams.empty());

    var authority = user.getRoles().getFirst().getAuthorities().getFirst().getAuthorityObject();
    try (var definitions = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class)) {
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAllAuthority()).thenReturn(Map.of("orders.read", authority));
      definitions.when(() -> com.ouroboros.security.authority.AuthorityCenter.getAuthority("orders.read")).thenReturn(Optional.of(authority));
      filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      assertTrue(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
          "decision context should be populated while request is in-flight");
      DefaultAuthentication authentication = (DefaultAuthentication) OuroborosAuthenticationHolder
          .adapt(SecurityContextHolder.getContext().getAuthentication());
      authenticationInChain.set(authentication);
      paramsInChain.set(authentication.getAccessControlParams());
      });
    }

    assertNotNull(authenticationInChain.get(), "token runtime flow should set DefaultAuthentication");
    org.junit.jupiter.api.Assertions.assertEquals(expectedParams.getFieldConstraint(), paramsInChain.get().getFieldConstraint(),
        "request-time decision should still resolve via holder-backed method/path");
    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
        "request context holder should be cleared after filter completion");

    org.junit.jupiter.api.Assertions.assertThrows(com.ouroboros.security.exception.AuthorizationDeniedException.class,
        () -> authenticationInChain.get().getAccessControlParams(),
        "missing decision context must fail closed after request cleanup");
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

  private static Authority buildAuthority(String fullName, List<String> apis) {
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
        return apis;
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
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return Collections.singletonList("id");
      }

      @Override
      public List<String> getDisallowedFields() {
        return Collections.emptyList();
      }

      @Override
      public Map<String, Object> getFilters() {
        return Collections.emptyMap();
      }
    };
  }

  private static final class StubTokenService implements TokenService {
    private final TokenInfo tokenInfo;

    private StubTokenService(TokenInfo tokenInfo) {
      this.tokenInfo = tokenInfo;
    }

    @Override
    public TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, Map<String, Object> refreshTokenClaims) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public TokenPair buildTokenPair(Map<String, Object> accessTokenClaims, long accessTokenExpireSeconds,
                                    Map<String, Object> refreshTokenClaims, long refreshTokenExpireSeconds) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Either<TokenException, TokenInfo> verifyAccessToken(String token) {
      return Either.right(tokenInfo);
    }

    @Override
    public Either<TokenException, TokenInfo> verifyRefreshToken(String token) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Try<Boolean> revokeAccessToken(String token) {
      throw new UnsupportedOperationException("not used in this test");
    }

    @Override
    public Try<Boolean> revokeRefreshToken(String token) {
      throw new UnsupportedOperationException("not used in this test");
    }
  }

  private static final class StubUserCenter implements UserCenter {
    private final User user;

    private StubUserCenter(User user) {
      this.user = user;
    }

    @Override
    public Optional<User> getUser(String userIdentity) {
      return Optional.of(user);
    }

    @Override
    public List<User> getUsers(Collection<String> userIdentities) {
      return Collections.singletonList(user);
    }

    @Override
    public List<User> getAllUsers() {
      return Collections.singletonList(user);
    }
  }
}
