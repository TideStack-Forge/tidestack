package com.ouroboros.security.authentication.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.authority.accesscontrol.api.AnonymousApiProvider;
import com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;
import com.ouroboros.security.exception.UnauthenticatedException;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.token.TokenInfo;
import com.ouroboros.security.token.TokenIdentityResolver;
import com.ouroboros.security.token.TokenService;

import io.vavr.control.Either;

class TokenAuthenticationFilterSupplierPublicAccessTest {
  @AfterEach
  void cleanup() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @ParameterizedTest
  @MethodSource("publicRequests")
  void publicRoutesRemainAccessibleWithoutValidCredentials(String method, String path, String contextPath, String header) throws Exception {
    var tokens = mock(TokenService.class);
    when(tokens.verifyAccessToken(anyString())).thenReturn(Either.left(new com.ouroboros.security.exception.InvalidTokenException("invalid")));
    var users = mock(UserCenter.class);
    var resolver = mock(TokenIdentityResolver.class);
    var authorize = authorization(contextPath);
    var authenticate = authentication(tokens, users, resolver, authorize);
    var request = new MockHttpServletRequest(method, contextPath + path);
    request.setContextPath(contextPath);
    if (header != null) { request.addHeader("Authorization", header); }
    if ("duplicate".equals(header)) { request.addHeader("Authorization", "Bearer other"); }
    var response = new MockHttpServletResponse();
    var reached = new AtomicBoolean();
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());

    // Exercise the real authentication and authorization filters in their runtime order.
    new AnonymousAuthenticationFilterSupplier().getAuthenticationFilter().doFilter(request, response,
        (req, res) -> authenticate.getAuthenticationFilter().doFilter(req, res,
            (authenticatedRequest, authenticatedResponse) -> authorize.getAuthorizeFilter().doFilter(
                authenticatedRequest, authenticatedResponse, (allowedRequest, allowedResponse) -> reached.set(true))));

    assertThat(reached).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
    assertThat(CurrentSecurityContextHolder.findAuthentication()).isEmpty();
    assertThat(CurrentSecurityContextHolder.findIdentity()).isEmpty();
    verifyNoInteractions(users);
    if (header == null || !header.startsWith("Bearer ")) {
      verifyNoInteractions(tokens, resolver);
    } else {
      verify(tokens).verifyAccessToken("expired");
    }
  }

  @ParameterizedTest
  @MethodSource("publicTargets")
  void publicRoutesExposeTheUserFromAValidPlatformToken(String method, String path, String contextPath) throws Exception {
    var tokens = mock(TokenService.class);
    when(tokens.verifyAccessToken("valid")).thenReturn(Either.right(new TokenInfo("alice", "session-a", Map.of(), Long.MAX_VALUE)));
    var users = mock(UserCenter.class);
    User alice = () -> "alice";
    when(users.getUser("alice")).thenAnswer(invocation -> Optional.of(alice));
    var resolver = mock(TokenIdentityResolver.class);
    var authorize = authorization(contextPath);
    var authenticate = authentication(tokens, users, resolver, authorize);
    var request = new MockHttpServletRequest(method, contextPath + path);
    request.setContextPath(contextPath);
    request.addHeader("Authorization", "Bearer valid");
    var response = new MockHttpServletResponse();
    var reached = new AtomicBoolean();
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());

    new AnonymousAuthenticationFilterSupplier().getAuthenticationFilter().doFilter(request, response,
        (req, res) -> authenticate.getAuthenticationFilter().doFilter(req, res,
            (authenticatedRequest, authenticatedResponse) -> authorize.getAuthorizeFilter().doFilter(
                authenticatedRequest, authenticatedResponse, (allowedRequest, allowedResponse) -> {
                  assertThat(OuroborosAuthenticationHolder.getCurrentUserIdentity()).isEqualTo("alice");
                  var identity = CurrentSecurityContextHolder.findIdentity().orElseThrow();
                  assertThat(identity.getCredentialContext().kind()).isEqualTo(CredentialContext.Kind.PLATFORM);
                  assertThat(CurrentSecurityContextHolder.findAuthentication().orElseThrow().getPlatformSessionId()).contains("session-a");
                  reached.set(true);
                })));

    assertThat(reached).isTrue();
    assertThat(response.getStatus()).isEqualTo(200);
    verifyNoInteractions(resolver);
  }

  static Stream<Arguments> publicTargets() {
    return Stream.of(
        Arguments.of("GET", "/public/info", ""),
        Arguments.of("POST", "/login", ""),
        Arguments.of("DELETE", "/common/info", ""),
        Arguments.of("GET", "/api/provider/callback", ""),
        Arguments.of("GET", "/public/info", "/platform"));
  }

  @Test
  void publicRoutesPreserveDelegatedIdentityWithoutGrantingAPlatformSession() throws Exception {
    var tokens = mock(TokenService.class);
    when(tokens.verifyAccessToken("delegated")).thenReturn(Either.left(new com.ouroboros.security.exception.InvalidTokenException("not platform")));
    var users = mock(UserCenter.class);
    var resolver = mock(TokenIdentityResolver.class);
    var identity = new DefaultSecurityIdentity(() -> "alice", AuthorizationBoundary.restricted("platform-api", Map.of()),
        new CredentialContext(CredentialContext.Kind.DELEGATED, "client-a", "grant-a", "platform-api"));
    when(resolver.resolve("delegated")).thenReturn(Optional.of(identity));
    var authorize = authorization("");
    AnyAuthenticationApiProvider authenticatedApis = () -> List.of("GET /private/info");
    ReflectionTestUtils.setField(authorize, "anyAuthenticationApiProviders", List.of(authenticatedApis));
    var authenticate = authentication(tokens, users, resolver, authorize);
    var request = new MockHttpServletRequest("GET", "/public/info");
    request.addHeader("Authorization", "Bearer delegated");
    var response = new MockHttpServletResponse();
    var reached = new AtomicBoolean();
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());

    authenticate.getAuthenticationFilter().doFilter(request, response,
        (req, res) -> authorize.getAuthorizeFilter().doFilter(req, res, (allowedRequest, allowedResponse) -> {
          assertThat(OuroborosAuthenticationHolder.getCurrentUserIdentity()).isEqualTo("alice");
          assertThat(CurrentSecurityContextHolder.findIdentity()).containsSame(identity);
          assertThat(CurrentSecurityContextHolder.findAuthentication().orElseThrow().getPlatformSessionId()).isEmpty();
          reached.set(true);
        }));
    assertThat(reached).isTrue();
    verifyNoInteractions(users);

    try (var authorities = org.mockito.Mockito.mockStatic(com.ouroboros.security.authority.AuthorityCenter.class)) {
      authorities.when(com.ouroboros.security.authority.AuthorityCenter::getAllAuthority).thenReturn(Map.of());
      assertThatThrownBy(() -> authorize.getAuthorizeFilter().doFilter(
          new MockHttpServletRequest("GET", "/private/info"), new MockHttpServletResponse(),
          (req, res) -> { throw new AssertionError("Delegated identity entered a platform-only endpoint"); }))
          .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
  }

  @ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(strings = {"token", "user", "resolver"})
  void publicRoutesDoNotHideAuthenticationInfrastructureFailures(String failingDependency) {
    var failure = new IllegalStateException("identity storage unavailable");
    var tokens = mock(TokenService.class);
    var users = mock(UserCenter.class);
    var resolver = mock(TokenIdentityResolver.class);
    when(tokens.verifyAccessToken("valid")).thenReturn(Either.right(new TokenInfo("alice", "session-a", Map.of(), Long.MAX_VALUE)));
    when(users.getUser("alice")).thenAnswer(invocation -> Optional.<User>of(() -> "alice"));
    switch (failingDependency) {
      case "token" -> when(tokens.verifyAccessToken("valid")).thenThrow(failure);
      case "user" -> when(users.getUser("alice")).thenThrow(failure);
      case "resolver" -> {
        when(tokens.verifyAccessToken("valid")).thenReturn(Either.left(new com.ouroboros.security.exception.InvalidTokenException("not platform")));
        when(resolver.resolve("valid")).thenThrow(failure);
      }
      default -> throw new IllegalArgumentException(failingDependency);
    }
    var authenticate = authentication(tokens, users, resolver, authorization(""));
    var request = new MockHttpServletRequest("GET", "/public/info");
    request.addHeader("Authorization", "Bearer valid");

    assertThatThrownBy(() -> authenticate.getAuthenticationFilter().doFilter(request, new MockHttpServletResponse(),
        (req, res) -> { throw new AssertionError("Infrastructure failure was hidden"); })).isSameAs(failure);
  }

  @Test
  void invalidPublicCredentialsCannotRetainAnEarlierPlatformIdentity() throws Exception {
    var tokens = mock(TokenService.class);
    when(tokens.verifyAccessToken("expired")).thenThrow(new com.ouroboros.security.exception.InvalidTokenException("expired"));
    var authenticate = authentication(tokens, mock(UserCenter.class), mock(TokenIdentityResolver.class), authorization(""));
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    SecurityContextHolder.getContext().setAuthentication(
        OuroborosAuthenticationHolder.toSpringAuthentication(new DefaultAuthentication((User) () -> "other-user")));
    var request = new MockHttpServletRequest("GET", "/public/info");
    request.addHeader("Authorization", "Bearer expired");
    var reached = new AtomicBoolean();

    authenticate.getAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
      assertThat(CurrentSecurityContextHolder.findIdentity()).isEmpty();
      reached.set(true);
    });
    assertThat(reached).isTrue();
  }

  static Stream<Arguments> publicRequests() {
    return Stream.of(
        Arguments.of("GET", "/public/info", "", null),
        Arguments.of("GET", "/public/info", "", "Bearer expired"),
        Arguments.of("POST", "/login", "", "Bearer expired"),
        Arguments.of("GET", "/public/info", "", "Basic invalid"),
        Arguments.of("GET", "/public/info", "", "duplicate"),
        Arguments.of("DELETE", "/common/info", "", "Bearer expired"),
        Arguments.of("GET", "/api/provider/callback", "", "Bearer expired"),
        Arguments.of("GET", "/public/info", "/platform", "Bearer expired"));
  }

  @ParameterizedTest
  @MethodSource("protectedRequests")
  void invalidCredentialsCannotEnterProtectedRoutes(String method, String path, String header) throws Exception {
    var tokens = mock(TokenService.class);
    when(tokens.verifyAccessToken(anyString())).thenReturn(Either.left(new com.ouroboros.security.exception.InvalidTokenException("invalid")));
    var authenticate = authentication(tokens, mock(UserCenter.class), mock(TokenIdentityResolver.class), authorization(""));
    var request = new MockHttpServletRequest(method, path);
    request.addHeader("Authorization", header);
    var response = new MockHttpServletResponse();
    var reached = new AtomicBoolean();

    authenticate.getAuthenticationFilter().doFilter(request, response, (req, res) -> reached.set(true));

    assertThat(reached).isFalse();
    assertThat(response.getStatus()).isEqualTo(401);
  }

  static Stream<Arguments> protectedRequests() {
    return Stream.of(
        Arguments.of("GET", "/private/info", "Bearer expired"),
        Arguments.of("POST", "/public/info", "Bearer expired"),
        Arguments.of("GET", "/publicity/info", "Bearer expired"),
        Arguments.of("GET", "/private/info", "Basic invalid"));
  }

  @org.junit.jupiter.api.Test
  void missingCredentialsStillCannotEnterProtectedRoutes() throws Exception {
    var authorize = authorization("");
    var authenticate = authentication(mock(TokenService.class), mock(UserCenter.class), mock(TokenIdentityResolver.class), authorize);
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());
    var reached = new AtomicBoolean();
    assertThatThrownBy(() -> authenticate.getAuthenticationFilter().doFilter(
        new MockHttpServletRequest("GET", "/private/info"), new MockHttpServletResponse(),
        (req, res) -> authorize.getAuthorizeFilter().doFilter(req, res, (allowedReq, allowedRes) -> reached.set(true))))
        .isInstanceOf(UnauthenticatedException.class);
    assertThat(reached).isFalse();
  }

  private AuthorizeCheckFilterSupplier authorization(String contextPath) {
    var supplier = new AuthorizeCheckFilterSupplier();
    var servlet = new MockServletContext();
    servlet.setContextPath(contextPath);
    ReflectionTestUtils.setField(supplier, "servletContext", servlet);
    AnonymousApiProvider publicApis = () -> List.of("GET /public/**", "POST /login", "* /common/**", "GET /api/**/callback");
    ReflectionTestUtils.setField(supplier, "anonymousApiProviders", List.of(publicApis));
    return supplier;
  }

  private TokenAuthenticationFilterSupplier authentication(TokenService tokens, UserCenter users,
      TokenIdentityResolver resolver, AuthorizeCheckFilterSupplier authorize) {
    var supplier = new TokenAuthenticationFilterSupplier();
    ReflectionTestUtils.setField(supplier, "tokenService", tokens);
    ReflectionTestUtils.setField(supplier, "userCenter", users);
    ReflectionTestUtils.setField(supplier, "tokenIdentityResolvers", List.of(resolver));
    ReflectionTestUtils.setField(supplier, "authorizeCheckFilterSupplier", authorize);
    return supplier;
  }
}
