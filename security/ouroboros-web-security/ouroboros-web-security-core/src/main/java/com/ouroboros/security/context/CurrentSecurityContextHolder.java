package com.ouroboros.security.context;

import java.util.Optional;
import java.util.Objects;
import java.util.function.Supplier;

import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.identity.SecurityIdentity;

public final class CurrentSecurityContextHolder {

  private static final CurrentSecurityContext EMPTY_CONTEXT = new CurrentSecurityContext() {
    @Override
    public Optional<SecurityIdentity> findIdentity() {
      return Optional.empty();
    }
  };

  private static final ThreadLocal<CurrentSecurityContext> CURRENT_CONTEXT = new ThreadLocal<>();
  private static volatile CurrentSecurityContext delegate = EMPTY_CONTEXT;

  private CurrentSecurityContextHolder() {
  }

  public static CurrentSecurityContext getDelegate() {
    return delegate;
  }

  public static synchronized void setDelegate(CurrentSecurityContext newDelegate) {
    if (newDelegate == null) {
      throw new IllegalArgumentException("CurrentSecurityContext delegate cannot be null");
    }
    delegate = newDelegate;
  }

  public static synchronized void clearDelegate() {
    delegate = EMPTY_CONTEXT;
  }

  public static <T> T withContext(CurrentSecurityContext context, Supplier<T> supplier) {
    Objects.requireNonNull(supplier, "CurrentSecurityContext supplier cannot be null");
    return withContextThrowing(context, supplier::get);
  }

  public static <T, E extends Exception> T withContextThrowing(
      CurrentSecurityContext context,
      ThrowingSupplier<T, E> supplier) throws E {
    Objects.requireNonNull(context, "CurrentSecurityContext cannot be null");
    Objects.requireNonNull(supplier, "CurrentSecurityContext supplier cannot be null");

    CurrentSecurityContext previousContext = CURRENT_CONTEXT.get();
    CURRENT_CONTEXT.set(context);
    try {
      return supplier.get();
    } finally {
      if (previousContext == null) {
        CURRENT_CONTEXT.remove();
      } else {
        CURRENT_CONTEXT.set(previousContext);
      }
    }
  }

  public static <T> T withIdentity(SecurityIdentity identity, Supplier<T> supplier) {
    Objects.requireNonNull(identity, "SecurityIdentity cannot be null");
    return withContext(() -> Optional.of(identity), supplier);
  }

  public static <T, E extends Exception> T withIdentityThrowing(
      SecurityIdentity identity,
      ThrowingSupplier<T, E> supplier) throws E {
    Objects.requireNonNull(identity, "SecurityIdentity cannot be null");
    return withContextThrowing(() -> Optional.of(identity), supplier);
  }

  public static Optional<SecurityIdentity> findIdentity() {
    return currentContext().findIdentity();
  }

  public static Optional<OuroborosAuthentication> findAuthentication() {
    return currentContext().findAuthentication();
  }

  public static String getClientType() {
    return currentContext().findClientType().or(() -> delegate.findClientType()).orElse("UNKNOWN");
  }

  public static SecurityIdentity getRequiredIdentity() {
    return currentContext().getRequiredIdentity();
  }

  private static CurrentSecurityContext currentContext() {
    CurrentSecurityContext currentContext = CURRENT_CONTEXT.get();
    return currentContext == null ? delegate : currentContext;
  }

  @FunctionalInterface
  public interface ThrowingSupplier<T, E extends Exception> {
    T get() throws E;
  }
}
