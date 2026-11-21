package com.ouroboros.security.authentication.filter;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.exception.InternalException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AbstractAuthenticationFilterAuthorizationDecisionContextTest {

  @Test
  void internalFailureStopsTheRequestInsteadOfContinuingAnonymously() {
    var failure = new InternalException("database unavailable");
    var filter = new AbstractAuthenticationFilter(request -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        throw failure;
      }
    };
    var continued = new java.util.concurrent.atomic.AtomicBoolean();
    assertSame(failure, assertThrows(InternalException.class, () -> filter.doFilter(
        new MockHttpServletRequest("GET", "/api/orders"), new MockHttpServletResponse(),
        (request, response) -> continued.set(true))));
    assertFalse(continued.get());
    assertTrue(AuthorizationDecisionContextHolder.findCurrentRequest().isEmpty());
  }

  @Test
  void requestNamedErrorStillRunsAuthentication() {
    var failure = new InternalException("database unavailable");
    var filter = new AbstractAuthenticationFilter(request -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        throw failure;
      }
    };
    assertSame(failure, assertThrows(InternalException.class, () -> filter.doFilter(
        new MockHttpServletRequest("GET", "/error"), new MockHttpServletResponse(),
        (request, response) -> { throw new AssertionError("must not enter the business chain"); })));
  }

  @Test
  void errorDispatchDoesNotRepeatAuthentication() throws Exception {
    var filter = new AbstractAuthenticationFilter(request -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        throw new InternalException("must not retry failed authentication during error dispatch");
      }
    };
    var request = new MockHttpServletRequest("GET", "/error");
    request.setDispatcherType(DispatcherType.ERROR);
    var continued = new java.util.concurrent.atomic.AtomicBoolean();
    filter.doFilter(request, new MockHttpServletResponse(), (r, s) -> continued.set(true));
    assertTrue(continued.get());
  }

  @AfterEach
  void cleanup() {
    AuthorizationDecisionContextHolder.clearCurrentRequest();
  }

  @Test
  void doFilter_populatesDecisionContextInRuntimeFlow_andClearsAfterCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inAuthentication =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    AbstractAuthenticationFilter filter = new AbstractAuthenticationFilter(httpServletRequest -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        inAuthentication.set(AuthorizationDecisionContextHolder.findCurrentRequest());
        return Optional.empty();
      }
    };

    FilterChain filterChain = (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest());

    filter.doFilter(request, response, filterChain);

    assertTrue(inAuthentication.get().isPresent(),
        "runtime authentication flow should populate decision context");
    assertEquals("GET", inAuthentication.get().get().getMethod());
    assertEquals("/api/orders", inAuthentication.get().get().getPath());

    assertTrue(inChain.get().isPresent(),
        "downstream chain should observe populated decision context during request");
    assertEquals("GET", inChain.get().get().getMethod());
    assertEquals("/api/orders", inChain.get().get().getPath());

    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent(),
        "decision context must be cleared after request flow completes");
  }

  @Test
  void doFilter_restoresPreviousDecisionContextAfterCompletion_whenNestedContextExists() throws Exception {
    AuthorizationDecisionContextHolder.setCurrentRequest("PUT", "/api/outer");

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>> inChain =
        new AtomicReference<Optional<AuthorizationDecisionContextHolder.CurrentRequest>>(Optional.empty());

    AbstractAuthenticationFilter filter = new AbstractAuthenticationFilter(httpServletRequest -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest request) {
        return Optional.empty();
      }
    };

    FilterChain filterChain = (servletRequest, servletResponse) ->
        inChain.set(AuthorizationDecisionContextHolder.findCurrentRequest());

    filter.doFilter(request, response, filterChain);

    assertTrue(inChain.get().isPresent(),
        "nested runtime flow should expose current request context while filter is executing");
    assertEquals("GET", inChain.get().get().getMethod());
    assertEquals("/api/orders", inChain.get().get().getPath());

    Optional<AuthorizationDecisionContextHolder.CurrentRequest> restored =
        AuthorizationDecisionContextHolder.findCurrentRequest();
    assertTrue(restored.isPresent(),
        "after nested filter completion, outer decision context should be restored");
    assertEquals("PUT", restored.get().getMethod());
    assertEquals("/api/outer", restored.get().getPath());
  }

  @Test
  void doFilter_restoresSameOuterDecisionContextInstance_afterNestedCompletion() throws Exception {
    AuthorizationDecisionContextHolder.setCurrentRequest("PUT", "/api/outer");
    AuthorizationDecisionContextHolder.CurrentRequest outerBefore =
        AuthorizationDecisionContextHolder.findCurrentRequest().orElseThrow(IllegalStateException::new);

    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/orders");
    MockHttpServletResponse response = new MockHttpServletResponse();

    AbstractAuthenticationFilter filter = new AbstractAuthenticationFilter(httpServletRequest -> true) {
      @Override
      protected Optional<OuroborosAuthentication> authentication(HttpServletRequest ignored) {
        return Optional.empty();
      }
    };

    filter.doFilter(request, response, (servletRequest, servletResponse) -> {
    });

    AuthorizationDecisionContextHolder.CurrentRequest outerAfter =
        AuthorizationDecisionContextHolder.findCurrentRequest().orElseThrow(IllegalStateException::new);
    assertSame(outerBefore, outerAfter,
        "nested runtime boundary should restore the exact outer context instance");
  }
}
