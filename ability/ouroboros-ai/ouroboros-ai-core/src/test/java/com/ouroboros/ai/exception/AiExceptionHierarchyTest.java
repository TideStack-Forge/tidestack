package com.ouroboros.ai.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AiExceptionHierarchyTest {

  @Test
  void aiInvocationExceptionPreservesMessageAndCause() {
    IllegalStateException cause = new IllegalStateException("root cause");

    AiInvocationException exception = new AiInvocationException("failed", cause);

    assertEquals("failed", exception.getMessage());
    assertSame(cause, exception.getCause());
  }

  @Test
  void specializedExceptionsExtendInvocationException() {
    AiPolicyViolationException policyViolationException = new AiPolicyViolationException("blocked");
    AiProviderProtocolException providerProtocolException = new AiProviderProtocolException("bad response");

    assertEquals("blocked", policyViolationException.getMessage());
    assertEquals("bad response", providerProtocolException.getMessage());
  }
}
