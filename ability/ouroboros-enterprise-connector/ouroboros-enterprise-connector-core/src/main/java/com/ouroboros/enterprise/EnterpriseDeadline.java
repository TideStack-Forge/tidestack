package com.ouroboros.enterprise;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** A monotonic budget shared by token acquisition, waiting and the business request. */
public final class EnterpriseDeadline {
  private static final ExecutorService HTTP_EXECUTOR = Executors.newThreadPerTaskExecutor(
      Thread.ofVirtual().name("enterprise-http-", 0).factory());
  private final long startedAt = System.nanoTime();
  private final long budgetNanos;

  private EnterpriseDeadline(long timeoutMillis) {
    if (timeoutMillis < 1) {
      throw new IllegalArgumentException("Enterprise call timeout must be positive");
    }
    budgetNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
  }

  public static EnterpriseDeadline afterMillis(long timeoutMillis) {
    return new EnterpriseDeadline(timeoutMillis);
  }

  public long remainingNanos() throws TimeoutException {
    long remaining = budgetNanos - (System.nanoTime() - startedAt);
    if (remaining <= 0) {
      throw new TimeoutException("Enterprise call deadline exceeded");
    }
    return remaining;
  }

  public <T> T execute(Callable<T> request) throws Exception {
    remainingNanos();
    var future = HTTP_EXECUTOR.submit(() -> {
      remainingNanos();
      return request.call();
    });
    try {
      return future.get(remainingNanos(), TimeUnit.NANOSECONDS);
    } catch (TimeoutException exception) {
      future.cancel(true);
      throw exception;
    } catch (InterruptedException exception) {
      future.cancel(true);
      Thread.currentThread().interrupt();
      throw exception;
    } catch (ExecutionException exception) {
      if (exception.getCause() instanceof Exception cause) {
        throw cause;
      }
      if (exception.getCause() instanceof Error cause) {
        throw cause;
      }
      throw new IllegalStateException("Enterprise request failed", exception.getCause());
    }
  }
}
