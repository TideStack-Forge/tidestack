package com.ouroboros.security.context;

import java.io.IOException;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthorizationDecisionContextHolderLifecycleTest {

  @AfterEach
  void clearContext() {
    AuthorizationDecisionContextHolder.clearCurrentRequest();
  }

  @Test
  void setCurrentRequestThenFindReturnsCurrentRequest() {
    AuthorizationDecisionContextHolder.setCurrentRequest("GET", "/api/orders");

    Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
        AuthorizationDecisionContextHolder.findCurrentRequest();

    assertTrue(currentRequestOptional.isPresent());
    assertEquals("GET", currentRequestOptional.get().getMethod());
    assertEquals("/api/orders", currentRequestOptional.get().getPath());
  }

  @Test
  void clearCurrentRequestRemovesCurrentRequest() {
    AuthorizationDecisionContextHolder.setCurrentRequest("GET", "/api/orders");

    AuthorizationDecisionContextHolder.clearCurrentRequest();

    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent());
  }

  @Test
  void withCurrentRequestClearsContextAfterSuccess() {
    String value = AuthorizationDecisionContextHolder.withCurrentRequest("POST", "/api/orders", () -> {
      Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
          AuthorizationDecisionContextHolder.findCurrentRequest();
      assertTrue(currentRequestOptional.isPresent());
      assertEquals("POST", currentRequestOptional.get().getMethod());
      assertEquals("/api/orders", currentRequestOptional.get().getPath());
      return "ok";
    });

    assertEquals("ok", value);
    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent());
  }

  @Test
  void withCurrentRequestClearsContextAfterFailure() {
    assertThrows(IllegalStateException.class,
        () -> AuthorizationDecisionContextHolder.withCurrentRequest("DELETE", "/api/orders/1", () -> {
          throw new IllegalStateException("boom");
        }));

    assertFalse(AuthorizationDecisionContextHolder.findCurrentRequest().isPresent());
  }

  @Test
  void withCurrentRequestRestoresPreviousContextWhenNestedContextExists() {
    AuthorizationDecisionContextHolder.setCurrentRequest("PATCH", "/api/orders/outer");

    String value = AuthorizationDecisionContextHolder.withCurrentRequest("POST", "/api/orders/inner", () -> {
      Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
          AuthorizationDecisionContextHolder.findCurrentRequest();
      assertTrue(currentRequestOptional.isPresent());
      assertEquals("POST", currentRequestOptional.get().getMethod());
      assertEquals("/api/orders/inner", currentRequestOptional.get().getPath());
      return "ok";
    });

    assertEquals("ok", value);
    Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
        AuthorizationDecisionContextHolder.findCurrentRequest();
    assertTrue(currentRequestOptional.isPresent());
    assertEquals("PATCH", currentRequestOptional.get().getMethod());
    assertEquals("/api/orders/outer", currentRequestOptional.get().getPath());
  }

  @Test
  void withCurrentRequestThrowingRestoresPreviousContextAndPropagatesCheckedException() {
    AuthorizationDecisionContextHolder.setCurrentRequest("PATCH", "/api/orders/outer");

    IOException thrown = assertThrows(IOException.class,
        () -> AuthorizationDecisionContextHolder.withCurrentRequestThrowing("POST", "/api/orders/inner", () -> {
          Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
              AuthorizationDecisionContextHolder.findCurrentRequest();
          assertTrue(currentRequestOptional.isPresent());
          assertEquals("POST", currentRequestOptional.get().getMethod());
          assertEquals("/api/orders/inner", currentRequestOptional.get().getPath());
          throw new IOException("boom");
        }));

    assertEquals("boom", thrown.getMessage());

    Optional<AuthorizationDecisionContextHolder.CurrentRequest> currentRequestOptional =
        AuthorizationDecisionContextHolder.findCurrentRequest();
    assertTrue(currentRequestOptional.isPresent());
    assertEquals("PATCH", currentRequestOptional.get().getMethod());
    assertEquals("/api/orders/outer", currentRequestOptional.get().getPath());
  }
}
