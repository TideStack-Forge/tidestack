package com.ouroboros.security.context;

import java.util.Optional;
import java.util.function.Supplier;

public final class AuthorizationDecisionContextHolder {

  private static final ThreadLocal<CurrentRequest> CURRENT_REQUEST = new ThreadLocal<>();

  private AuthorizationDecisionContextHolder() {
  }

  public static void setCurrentRequest(String method, String path) {
    CURRENT_REQUEST.set(new CurrentRequest(method, path));
  }

  public static Optional<CurrentRequest> findCurrentRequest() {
    return Optional.ofNullable(CURRENT_REQUEST.get());
  }

  public static void clearCurrentRequest() {
    CURRENT_REQUEST.remove();
  }

  public static <T> T withCurrentRequest(String method, String path, Supplier<T> supplier) {
    CurrentRequest previousRequest = CURRENT_REQUEST.get();
    setCurrentRequest(method, path);
    try {
      return supplier.get();
    } finally {
      if (previousRequest == null) {
        clearCurrentRequest();
      } else {
        CURRENT_REQUEST.set(previousRequest);
      }
    }
  }

  public static <T, E extends Exception> T withCurrentRequestThrowing(
      String method,
      String path,
      ThrowingSupplier<T, E> supplier) throws E {
    CurrentRequest previousRequest = CURRENT_REQUEST.get();
    setCurrentRequest(method, path);
    try {
      return supplier.get();
    } finally {
      if (previousRequest == null) {
        clearCurrentRequest();
      } else {
        CURRENT_REQUEST.set(previousRequest);
      }
    }
  }

  @FunctionalInterface
  public interface ThrowingSupplier<T, E extends Exception> {
    T get() throws E;
  }

  public static final class CurrentRequest {
    private final String method;
    private final String path;

    private CurrentRequest(String method, String path) {
      this.method = method;
      this.path = path;
    }

    public String getMethod() {
      return method;
    }

    public String getPath() {
      return path;
    }
  }
}
