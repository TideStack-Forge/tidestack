package com.ouroboros.ai.exception;

public class AiPolicyViolationException extends AiInvocationException {

  public AiPolicyViolationException(String message) {
    super(message);
  }

  public AiPolicyViolationException(String message, Throwable cause) {
    super(message, cause);
  }
}
