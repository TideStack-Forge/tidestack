package com.ouroboros.dev.componentlib.exception;

import com.ouroboros.exception.InternalException;

/**
 * 平台组件库相关异常
 */
public class PlatformComponentLibException extends InternalException {
  private static final Integer DEFAULT_ERROR_CODE = -5100;

  public PlatformComponentLibException(Integer errorCode, String message) {
    super(errorCode, message);
  }

  public PlatformComponentLibException(Integer errorCode, String message, Throwable cause) {
    super(errorCode, message, cause);
  }

  public PlatformComponentLibException(String message, Throwable cause) {
    super(DEFAULT_ERROR_CODE, message, cause);
  }

  public PlatformComponentLibException(String message) {
    super(DEFAULT_ERROR_CODE, message);
  }

  public PlatformComponentLibException(Throwable cause) {
    super(DEFAULT_ERROR_CODE, cause.getMessage(), cause);
  }
}
