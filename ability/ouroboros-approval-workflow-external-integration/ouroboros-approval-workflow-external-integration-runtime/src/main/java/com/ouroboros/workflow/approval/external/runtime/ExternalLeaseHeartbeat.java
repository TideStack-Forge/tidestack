package com.ouroboros.workflow.approval.external.runtime;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Renews a fencing lease while a bounded remote operation is in flight. */
public final class ExternalLeaseHeartbeat implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(ExternalLeaseHeartbeat.class);
  private static final ScheduledExecutorService SCHEDULER =
      Executors.newScheduledThreadPool(1, runnable -> {
        Thread thread = new Thread(runnable, "external-lease-heartbeat");
        thread.setDaemon(true);
        return thread;
      });

  private final ScheduledFuture<?> future;

  private ExternalLeaseHeartbeat(ScheduledFuture<?> future) {
    this.future = future;
  }

  public static ExternalLeaseHeartbeat start(BooleanSupplier renew, int leaseSeconds) {
    if (renew == null) {
      throw new IllegalArgumentException("lease renew callback 不能为空");
    }
    long period = Math.max(1L, Math.max(leaseSeconds, 1) / 3L);
    AtomicReference<ScheduledFuture<?>> reference = new AtomicReference<>();
    ScheduledFuture<?> future = SCHEDULER.scheduleAtFixedRate(() -> {
      try {
        if (!renew.getAsBoolean()) {
          ScheduledFuture<?> scheduled = reference.get();
          if (scheduled != null) {
            scheduled.cancel(false);
          }
        }
      } catch (RuntimeException ex) {
        logger.warn("外部操作 lease 续租失败，停止续租并交由 fencing/迟到响应流程处理", ex);
        ScheduledFuture<?> scheduled = reference.get();
        if (scheduled != null) {
          scheduled.cancel(false);
        }
      }
    }, period, period, TimeUnit.SECONDS);
    reference.set(future);
    return new ExternalLeaseHeartbeat(future);
  }

  @Override
  public void close() {
    future.cancel(false);
  }
}
