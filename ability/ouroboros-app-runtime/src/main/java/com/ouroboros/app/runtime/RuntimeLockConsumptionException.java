package com.ouroboros.app.runtime;

public class RuntimeLockConsumptionException extends RuntimeException {
  public RuntimeLockConsumptionException(String message) {
    super(message);
  }

  public RuntimeLockConsumptionException(String message, Throwable cause) {
    super(message, cause);
  }
}
