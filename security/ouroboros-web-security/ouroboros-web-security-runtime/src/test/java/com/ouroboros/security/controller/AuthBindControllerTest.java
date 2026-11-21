package com.ouroboros.security.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.externalauth.BindingRateLimiter;
import com.ouroboros.security.externalauth.ExternalAuthService;
import com.ouroboros.security.externalauth.ExternalIdentity;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.UserIdResolver;
import com.ouroboros.security.rbac.UserPasswordVerifier;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.service.UserSsoService;
import com.ouroboros.web.WebApiResult;

@ExtendWith(MockitoExtension.class)
class AuthBindControllerTest {

  @Mock
  private ExternalAuthService externalAuthService;

  @Mock
  private BindingRateLimiter bindingRateLimiter;

  @Mock
  private UserIdResolver userIdResolver;

  @Mock
  private UserPasswordVerifier userPasswordVerifier;

  @Mock
  private UserSsoService userSsoService;

  @Mock
  private HttpServletRequest httpServletRequest;

  private AuthBindController controller;

  @BeforeEach
  void setUp() {
    controller = new AuthBindController();
    ReflectionTestUtils.setField(controller, "externalAuthService", externalAuthService);
    ReflectionTestUtils.setField(controller, "bindingRateLimiter", bindingRateLimiter);
    ReflectionTestUtils.setField(controller, "userIdResolver", userIdResolver);
    ReflectionTestUtils.setField(controller, "userPasswordVerifier", userPasswordVerifier);
    ReflectionTestUtils.setField(controller, "userSsoService", userSsoService);
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void shouldApplyCrossTokenRateLimitBeforeResolvingUserIdentity() {
    AuthBindController.BindRequest request = new AuthBindController.BindRequest();
    request.setBindingToken("binding-token");
    request.setUsername("alice");
    request.setPassword("secret");

    ExternalIdentity identity = ExternalIdentity.of(
        "cas-main",
        "external-001",
        "Alice",
        "alice@example.com",
        Collections.emptyMap()
    );

    when(bindingRateLimiter.checkTokenDedup("binding-token")).thenReturn(Optional.empty());
    when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
    when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
    when(externalAuthService.validateBindingToken("binding-token")).thenReturn(Optional.of(identity));
    when(bindingRateLimiter.checkCrossTokenLimits("cas-main", "external-001", "alice", "127.0.0.1"))
        .thenReturn(Optional.of("Rate limit exceeded for username"));

    WebApiResult<?> result = controller.bind(request, httpServletRequest);

    assertEquals(Integer.valueOf(42901), result.getCode());
    verify(userIdResolver, never()).getUserId(anyString(), anyString());
  }

  @Test
  void rejectsAConnectionProofRevokedWhileThePlatformPasswordIsBeingChecked() {
    var request = new AuthBindController.BindRequest();
    request.setBindingToken("pending");
    request.setUsername("alice");
    request.setPassword("secret");
    var identity = ExternalIdentity.of("wecom-main", "member-1", "Alice", null, Collections.emptyMap());
    when(externalAuthService.validateBindingToken("pending")).thenReturn(Optional.of(identity), Optional.empty());
    when(userIdResolver.getUserId("username", "alice")).thenReturn(Optional.of("alice-id"));
    when(userPasswordVerifier.verify("alice-id", "secret")).thenReturn(true);

    var result = controller.bind(request, httpServletRequest);

    assertEquals(Integer.valueOf(40103), result.getCode());
    verifyNoInteractions(userSsoService);
    verify(externalAuthService, never()).consumeBindingToken(anyString());
  }

  @Test
  void listMyBindingsShouldPreferCurrentSecurityContextIdentityOverHolderAuthentication() {
    installConflictingSecurityIdentities();

    UserSsoEntity binding = buildBinding("binding-1", "seam-user");
    lenient().when(userSsoService.findByUserIdentity(eq("legacy-holder-user")))
        .thenThrow(new AssertionError("listMyBindings should not use legacy holder authentication"));
    when(userSsoService.findByUserIdentity(eq("seam-user")))
        .thenReturn(Collections.singletonList(binding));

    WebApiResult<?> result = controller.listMyBindings();

    assertEquals(Integer.valueOf(0), result.getCode());
    assertEquals(1, ((List<?>) result.getData()).size());
    verify(userSsoService).findByUserIdentity("seam-user");
  }

  @Test
  void deleteMyBindingShouldPreferCurrentSecurityContextIdentityForOwnershipCheck() {
    installConflictingSecurityIdentities();

    lenient().when(userSsoService.findByUserIdentity(eq("legacy-holder-user")))
        .thenThrow(new AssertionError("deleteMyBinding should not use legacy holder authentication"));
    when(userSsoService.findByUserIdentity(eq("seam-user")))
        .thenReturn(Collections.singletonList(buildBinding("binding-1", "seam-user")));

    WebApiResult<?> result = controller.deleteMyBinding("binding-1");

    assertEquals(Integer.valueOf(0), result.getCode());
    verify(userSsoService).findByUserIdentity("seam-user");
    verify(userSsoService).deleteBinding("binding-1");
  }

  private void installConflictingSecurityIdentities() {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "seam-user")));
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-holder-user", null));
  }

  private UserSsoEntity buildBinding(String id, String userIdentity) {
    UserSsoEntity entity = new UserSsoEntity();
    entity.setId(id);
    entity.setUserIdentity(userIdentity);
    entity.setSsoChannel("cas-main");
    entity.setSsoUserIdentity("external-001");
    entity.setExternalDisplayName("Alice");
    entity.setExternalEmail("alice@example.com");
    entity.setCreatedAt(LocalDateTime.of(2026, 3, 27, 12, 0));
    return entity;
  }
}
