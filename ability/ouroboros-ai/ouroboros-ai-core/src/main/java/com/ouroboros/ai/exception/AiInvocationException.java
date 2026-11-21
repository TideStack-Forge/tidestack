package com.ouroboros.ai.exception;

import com.ouroboros.exception.PlatformException;

public class AiInvocationException extends PlatformException {

  public AiInvocationException(String message) {
    super(message);
  }

  public AiInvocationException(String message, Throwable cause) {
    super(message, cause);
  }
}
