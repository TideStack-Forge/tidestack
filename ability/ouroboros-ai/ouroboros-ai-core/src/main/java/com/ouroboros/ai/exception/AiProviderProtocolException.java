package com.ouroboros.ai.exception;

public class AiProviderProtocolException extends AiInvocationException {

  public AiProviderProtocolException(String message) {
    super(message);
  }

  public AiProviderProtocolException(String message, Throwable cause) {
    super(message, cause);
  }
}
