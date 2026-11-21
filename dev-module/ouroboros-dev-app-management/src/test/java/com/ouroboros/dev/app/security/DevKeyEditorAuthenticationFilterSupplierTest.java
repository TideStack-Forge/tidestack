package com.ouroboros.dev.app.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import io.vavr.control.Try;

import com.ouroboros.dev.app.model.AuthorizeDebugProfileDTO;
import com.ouroboros.dev.app.model.DebugKeyMaterial;
import com.ouroboros.dev.app.service.runtime.DebugProfileAuthorizationService;
import com.ouroboros.dev.app.service.runtime.DevKeyStore;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserCenter;

class DevKeyEditorAuthenticationFilterSupplierTest {
  private final DebugProfileAuthorizationService authorization = mock(DebugProfileAuthorizationService.class);
  private final DevKeyStore keys = mock(DevKeyStore.class);
  private final UserCenter users = mock(UserCenter.class);
  private final DevKeyEditorAuthenticationFilterSupplier supplier = new DevKeyEditorAuthenticationFilterSupplier(authorization, keys, users);

  @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

  private User authorize(String appName) {
    var result = new AuthorizeDebugProfileDTO();
    result.setAuthorized(true);
    when(authorization.authorize(appName, "key")).thenReturn(Try.success(result));
    when(keys.findByDevKey(appName, "key")).thenReturn(DebugKeyMaterial.builder().userIdentity("alice").build());
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("alice");
    when(user.isEnabled()).thenReturn(true);
    when(users.getUser("alice")).thenReturn(Optional.of(user));
    return user;
  }

  @ParameterizedTest
  @CsvSource({
      "POST,/api/dev/demo/code-intelligence/context", "GET,/api/dev/demo/code-intelligence/environment",
      "POST,/api/dev/demo/code-intelligence/sessions", "POST,/api/dev/demo/code-intelligence/sessions/id-1/messages",
      "POST,/api/dev/demo/code-intelligence/sessions/id-1/context", "GET,/api/dev/demo/code-intelligence/sessions/id-1/notifications",
      "DELETE,/api/dev/demo/code-intelligence/sessions/id-1", "GET,/api/dev/demo/logicflow/node-define",
      "GET,/api/app/demo/field-types/normal", "GET,/api/app/demo/field-types/primary",
      "GET,/api/app/demo/field-types/%E6%96%87%E6%9C%AC/ui-schema", "GET,/api/dev/demo/approval-workflow/candidate",
      "GET,/api/dev/demo/approval-workflow/candidate/User/items", "GET,/api/dev/demo/approval-workflow/candidate/User/category",
      "GET,/api/dev/demo/approval-workflow/form-fields"
  })
  void authenticatesOnlyEditorOperationsAndRestoresContext(String method, String path) throws Exception {
    var user = authorize("demo");
    var roles = List.<Role>of(mock(Role.class));
    when(user.getRoles()).thenReturn(roles);
    var previous = SecurityContextHolder.getContext();
    var request = new MockHttpServletRequest(method, "/platform" + path);
    request.setContextPath("/platform");
    request.addHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER, "key");
    var continued = new AtomicBoolean();
    supplier.getAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> {
      var authentication = OuroborosAuthenticationHolder.getAuthentication();
      assertEquals("alice", authentication.getPrincipal());
      assertSame(user, authentication.getUserDetail());
      assertEquals(roles, authentication.getRoles());
      continued.set(true);
    });
    assertTrue(continued.get());
    assertSame(previous, SecurityContextHolder.getContext());
    assertNull(previous.getAuthentication());
    verify(authorization).authorize("demo", "key");
  }

  @ParameterizedTest
  @CsvSource({"POST,/api/dev/demo/deploy", "GET,/api/app/container", "POST,/api/dev/demo/authorize",
      "PUT,/api/dev/demo/code-intelligence/context", "DELETE,/api/dev/demo/logicflow/node-define",
      "GET,/api/dev/demo/../other/logicflow/node-define", "GET,/api/dev/demo/approval-workflow/candidate/%2e%2e/items",
      "GET,/api/app/demo/field-types/%252f/ui-schema", "GET,/api/dev/demo/logicflow/node-define;other"})
  void rejectsNonEditorOperationsBeforeLookingUpKeys(String method, String path) throws Exception {
    var request = new MockHttpServletRequest(method, path);
    request.addHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER, "key");
    assertRejected(request, 403);
    verifyNoInteractions(authorization, keys, users);
  }

  @Test void rejectsMixedOrMultipleCredentials() throws Exception {
    for (String extra : List.of("Authorization", DevKeyEditorAuthenticationFilterSupplier.HEADER)) {
      var request = request();
      request.addHeader(extra, "other");
      assertRejected(request, 401);
    }
    var blank = request();
    blank.removeHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER);
    blank.addHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER, " ");
    assertRejected(blank, 401);
    verifyNoInteractions(authorization, keys, users);
  }

  @Test void leavesExistingAuthenticationUnchangedWithoutAKey() throws Exception {
    var called = new AtomicBoolean();
    supplier.getAuthenticationFilter().doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), (req, res) -> called.set(true));
    assertTrue(called.get());
    verifyNoInteractions(authorization, keys, users);
  }

  @Test void revalidatesKeysOnEveryRequestAndDoesNotCacheRevokedCredentials() throws Exception {
    authorize("demo");
    var response = new MockHttpServletResponse();
    supplier.getAuthenticationFilter().doFilter(request(), response, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));
    assertEquals(204, response.getStatus());
    when(authorization.authorize("demo", "key")).thenReturn(Try.failure(new BusinessException("expired or revoked")));
    assertRejected(request(), 401);
    verify(authorization, times(2)).authorize("demo", "key");
  }

  @Test void rejectsKeysForAnotherApplication() throws Exception {
    when(authorization.authorize("other", "key")).thenReturn(Try.failure(new BusinessException("not found")));
    var request = new MockHttpServletRequest("GET", "/api/dev/other/logicflow/node-define");
    request.addHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER, "key");
    assertRejected(request, 401);
    verifyNoInteractions(keys, users);
  }

  @Test void rejectsAnUnsuccessfulAuthorizationResult() throws Exception {
    when(authorization.authorize("demo", "key")).thenReturn(Try.success(new AuthorizeDebugProfileDTO()));
    assertRejected(request(), 401);
    verifyNoInteractions(keys, users);
  }

  @Test void continuesThroughTheExistingAuthorizationFilter() throws Exception {
    authorize("demo");
    var permissions = new com.ouroboros.security.authorize.filter.AuthorizeCheckFilterSupplier();
    org.springframework.test.util.ReflectionTestUtils.setField(permissions, "servletContext", new org.springframework.mock.web.MockServletContext());
    org.springframework.test.util.ReflectionTestUtils.setField(permissions, "anyAuthenticationApiProviders",
        List.<com.ouroboros.security.authority.accesscontrol.api.AnyAuthenticationApiProvider>of(() -> List.of("GET /api/dev/**")));
    var previous = com.ouroboros.security.context.CurrentSecurityContextHolder.getDelegate();
    com.ouroboros.security.context.CurrentSecurityContextHolder.setDelegate(new com.ouroboros.security.context.SpringSecurityCurrentSecurityContext());
    try {
      var controller = new AtomicBoolean();
      supplier.getAuthenticationFilter().doFilter(request(), new MockHttpServletResponse(), (req, res) ->
          permissions.getAuthorizeFilter().doFilter(req, res, (allowed, output) -> controller.set(true)));
      assertTrue(controller.get());
      org.springframework.test.util.ReflectionTestUtils.setField(permissions, "anyAuthenticationApiProviders", List.of());
      assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
          supplier.getAuthenticationFilter().doFilter(request(), new MockHttpServletResponse(), (req, res) ->
              permissions.getAuthorizeFilter().doFilter(req, res, (allowed, output) -> fail("Authorization was bypassed"))));
    } finally {
      com.ouroboros.security.context.CurrentSecurityContextHolder.setDelegate(previous);
    }
  }

  @Test void preservesErrorDispatchWithoutAuthenticatingTheErrorHandler() throws Exception {
    var request = request();
    request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
    var controller = new AtomicBoolean();
    supplier.getAuthenticationFilter().doFilter(request, new MockHttpServletResponse(), (req, res) -> controller.set(true));
    assertTrue(controller.get());
    verifyNoInteractions(authorization, keys, users);
  }

  @Test void rejectsDisabledOrDeletedAccountsAndKeysRemovedDuringValidation() throws Exception {
    var user = authorize("demo");
    when(user.isEnabled()).thenReturn(false);
    assertRejected(request(), 401);
    when(users.getUser("alice")).thenReturn(Optional.empty());
    assertRejected(request(), 401);
    when(keys.findByDevKey("demo", "key")).thenReturn(null);
    assertRejected(request(), 401);
  }

  @Test void propagatesInfrastructureFailuresAndRestoresContextOnControllerFailure() throws Exception {
    var failure = new IllegalStateException("database unavailable");
    when(authorization.authorize("demo", "key")).thenReturn(Try.failure(failure));
    assertSame(failure, assertThrows(IllegalStateException.class, () -> assertRejected(request(), 401)));
    authorize("demo");
    var previous = SecurityContextHolder.getContext();
    assertThrows(IllegalStateException.class, () -> supplier.getAuthenticationFilter().doFilter(request(), new MockHttpServletResponse(), (req, res) -> { throw failure; }));
    assertSame(previous, SecurityContextHolder.getContext());
    assertNull(previous.getAuthentication());
  }

  private MockHttpServletRequest request() {
    var request = new MockHttpServletRequest("GET", "/api/dev/demo/logicflow/node-define");
    request.addHeader(DevKeyEditorAuthenticationFilterSupplier.HEADER, "key");
    return request;
  }

  private void assertRejected(MockHttpServletRequest request, int status) throws Exception {
    var response = new MockHttpServletResponse();
    supplier.getAuthenticationFilter().doFilter(request, response, (req, res) -> fail("Rejected credentials reached the controller"));
    assertEquals(status, response.getStatus());
  }
}
