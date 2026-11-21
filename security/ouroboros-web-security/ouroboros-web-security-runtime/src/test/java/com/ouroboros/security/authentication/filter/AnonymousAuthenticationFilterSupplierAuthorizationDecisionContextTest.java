package com.ouroboros.security.authentication.filter;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.authentication.AuthenticationFilter;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnonymousAuthenticationFilterSupplierAuthorizationDecisionContextTest {

  @AfterEach
  void cleanup() {
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    SecurityContextHolder.clearContext();
  }

  @Test
  void doFilter_populatesDecisionContextInAnonymousFlow_andClearsAfterCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    AuthenticationFilter filter = new AnonymousAuthenticationFilterSupplier().getAuthenticationFilter();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest());
      assertSame(AnonymousAuthentication.getInstance(),
          OuroborosAuthenticationHolder.adapt(SecurityContextHolder.getContext().getAuthentication()),
          "anonymous runtime flow should set anonymous authentication");
    });

    assertTrue(inChain.get().isPresent(),
        "anonymous runtime flow should populate decision context");
    assertEquals("POST", inChain.get().get().getMethod());
    assertEquals("/api/public/info", inChain.get().get().getPath());

    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
        "decision context must be cleared after anonymous request flow completes");
  }

  @Test
  void doFilter_restoresPreviousDecisionContextAfterCompletion_whenNestedContextExists() throws Exception {
    AuthorizationDecisionContextHolder.setCurrentRequest("PATCH", "/api/outer");

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    AuthenticationFilter filter = new AnonymousAuthenticationFilterSupplier().getAuthenticationFilter();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
      inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest());
      assertSame(AnonymousAuthentication.getInstance(),
          OuroborosAuthenticationHolder.adapt(SecurityContextHolder.getContext().getAuthentication()),
          "anonymous runtime flow should set anonymous authentication");
    });

    assertTrue(inChain.get().isPresent(),
        "nested anonymous runtime flow should expose current request context while executing");
    assertEquals("POST", inChain.get().get().getMethod());
    assertEquals("/api/public/info", inChain.get().get().getPath());

    Optional<AuthorizationDecisionContextHolder.CurrentRequest> restored =
        AuthorizationDecisionContextHolder.findCurrentRequest();
    assertTrue(restored.isPresent(),
        "after nested anonymous filter completion, outer decision context should be restored");
    assertEquals("PATCH", restored.get().getMethod());
    assertEquals("/api/outer", restored.get().getPath());
  }

  @Test
  void doFilter_restoresSameOuterDecisionContextInstance_afterNestedCompletion() throws Exception {
    AuthorizationDecisionContextHolder.setCurrentRequest("PATCH", "/api/outer");
    AuthorizationDecisionContextHolder.CurrentRequest outerBefore =
        AuthorizationDecisionContextHolder.findCurrentRequest().orElseThrow(IllegalStateException::new);

    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/public/info");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AuthenticationFilter filter = new AnonymousAuthenticationFilterSupplier().getAuthenticationFilter();

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
    });

    AuthorizationDecisionContextHolder.CurrentRequest outerAfter =
        AuthorizationDecisionContextHolder.findCurrentRequest().orElseThrow(IllegalStateException::new);
    assertSame(outerBefore, outerAfter,
        "nested anonymous runtime boundary should restore the exact outer context instance");
  }
}
