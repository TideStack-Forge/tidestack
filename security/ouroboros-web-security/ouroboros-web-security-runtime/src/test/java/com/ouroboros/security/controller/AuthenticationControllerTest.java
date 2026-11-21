package com.ouroboros.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.context.SpringSecurityCurrentSecurityContext;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;
import com.ouroboros.security.rbac.UserIdResolver;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.session.TokenBasedUserSession;
import com.ouroboros.security.session.UserSession;
import com.ouroboros.security.session.UserSessionManager;
import com.ouroboros.security.token.TokenService;
import com.ouroboros.web.WebApiResult;

import io.vavr.control.Either;
import io.vavr.control.Try;
import com.ouroboros.exception.InternalException;
import com.ouroboros.security.exception.InvalidTokenException;
import com.ouroboros.security.exception.SessionException;
import com.ouroboros.security.token.TokenInfo;

@ExtendWith(MockitoExtension.class)
class AuthenticationControllerTest {

  @Mock
  private UserIdResolver userIdResolver;

  @Mock
  private UserPasswordVerifier userPasswordVerifier;

  @Mock
  private UserCenter userCenter;

  @Mock
  private TokenService tokenService;

  @Mock
  private UserSessionManager<UserSession> userSessionManager;

  private TestableAuthenticationController controller;

  @BeforeEach
  void setUp() {
    controller = new TestableAuthenticationController();
    ReflectionTestUtils.setField(controller, "userIdResolver", userIdResolver);
    ReflectionTestUtils.setField(controller, "userPasswordVerifier", userPasswordVerifier);
    ReflectionTestUtils.setField(controller, "userCenter", userCenter);
    ReflectionTestUtils.setField(controller, "tokenService", tokenService);
    ReflectionTestUtils.setField(controller, "userSessionManager", userSessionManager);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void shouldRejectLocalPasswordLoginWhenDisabled() {
    controller.setLocalPasswordLoginEnabled(false);

    AuthenticationController.LoginDTO request = buildLoginRequest("username", "alice", "secret");
    WebApiResult<?> result = controller.login(request);

    assertEquals(Integer.valueOf(40301), result.getCode());
    assertEquals("本地账号密码登录已禁用", result.getMsg());
    verify(userIdResolver, never()).getUserId(anyString(), anyString());
  }

  @Test
  void shouldKeepExistingPasswordLoginFlowWhenEnabled() {
    stubSuccessfulLogin();

    AuthenticationController.LoginDTO request = buildLoginRequest("username", "alice", "secret");
    WebApiResult<?> result = controller.login(request);

    assertEquals(Integer.valueOf(0), result.getCode());
    verify(userIdResolver).getUserId(eq("username"), eq("alice"));
    verify(userPasswordVerifier).verify("user-1", "secret");
    verify(userCenter).getUser("user-1");
  }

  @Test
  void shouldReturnEmptyAuthoritiesWhenCurrentSecurityContextHasNoAuthenticatedAuthentication() {
    CurrentSecurityContextHolder.setDelegate(new SpringSecurityCurrentSecurityContext());

    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        "alice",
        "secret",
        Collections.singletonList(new SimpleGrantedAuthority("perm:read"))
    );
    authentication.setAuthenticated(false);
    SecurityContextHolder.getContext().setAuthentication(authentication);

    WebApiResult<Map<String, OuroborosGrantedAuthority>> result = controller.getUserAuthorities();

    assertEquals(Integer.valueOf(0), result.getCode());
    assertEquals(Collections.emptyMap(), result.getData());
  }

  @Test
  void failedUserReadIsNotReportedAsWrongPassword() {
    var failure = new InternalException("PRIVATE_SQL_DIAGNOSTIC");
    when(userIdResolver.getUserId("username", "alice")).thenThrow(failure);
    assertSame(failure, assertThrows(InternalException.class,
        () -> controller.login(buildLoginRequest("username", "alice", "secret"))));
  }

  @Test
  void invalidRefreshTokenRemainsAnAuthenticationFailure() {
    when(tokenService.verifyRefreshToken("refresh")).thenReturn(Either.left(new InvalidTokenException("PRIVATE_TOKEN_DIAGNOSTIC")));
    assertEquals(401, controller.refreshToken("refresh").getCode());
    assertEquals("登录已过期，请重新登录", controller.refreshToken("refresh").getMsg());
  }

  @Test
  void refreshInfrastructureFailurePropagatesWithCauseInsteadOfBecoming401() {
    var cause = new IllegalStateException("PRIVATE_SQL_DIAGNOSTIC");
    when(tokenService.verifyRefreshToken("refresh"))
        .thenReturn(Either.right(new TokenInfo("user-1", "session-1", Map.of(), 0)));
    when(userSessionManager.refreshSession("session-1")).thenReturn(Try.failure(cause));
    assertSame(cause, assertThrows(InternalException.class, () -> controller.refreshToken("refresh")).getCause());
  }

  @Test
  void missingSessionRequiresAuthenticationAgain() {
    when(tokenService.verifyRefreshToken("refresh"))
        .thenReturn(Either.right(new TokenInfo("user-1", "session-1", Map.of(), 0)));
    when(userSessionManager.refreshSession("session-1")).thenReturn(Try.failure(new SessionException("会话不存在")));
    assertEquals(401, controller.refreshToken("refresh").getCode());
  }

  private void stubSuccessfulLogin() {
    when(userIdResolver.getUserId("username", "alice")).thenReturn(Optional.of("user-1"));
    when(userPasswordVerifier.verify("user-1", "secret")).thenReturn(true);
    when(userCenter.getUser("user-1")).thenReturn(Optional.of((User) () -> "user-1"));

    TokenBasedUserSession session = new TokenBasedUserSession("session-1", "user-1", "UNKNOWN", new HashMap<String, String>());
    session.setAttribute("accessToken", Collections.singletonMap("token", "access-1"));
    session.setAttribute("refreshToken", Collections.singletonMap("token", "refresh-1"));

    when(userSessionManager.createSession(org.mockito.ArgumentMatchers.any(User.class), eq("UNKNOWN")))
        .thenReturn(Either.right(session));
  }

  private AuthenticationController.LoginDTO buildLoginRequest(String principalType, String principal, String credentials) {
    AuthenticationController.LoginDTO request = new AuthenticationController.LoginDTO();
    ReflectionTestUtils.setField(request, "principalType", principalType);
    ReflectionTestUtils.setField(request, "principal", principal);
    ReflectionTestUtils.setField(request, "credentials", credentials);
    return request;
  }

  private OuroborosGrantedAuthority buildAuthority(String authority) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authority;
      }

      @Override
      public Authority getAuthorityObject() {
        return null;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return AccessControlParams.empty();
      }
    };
  }

  private static class TestableAuthenticationController extends AuthenticationController {
    private boolean localPasswordLoginEnabled = true;

    public void setLocalPasswordLoginEnabled(boolean localPasswordLoginEnabled) {
      this.localPasswordLoginEnabled = localPasswordLoginEnabled;
    }

    protected boolean isLocalPasswordLoginEnabled() {
      return localPasswordLoginEnabled;
    }
  }
}
