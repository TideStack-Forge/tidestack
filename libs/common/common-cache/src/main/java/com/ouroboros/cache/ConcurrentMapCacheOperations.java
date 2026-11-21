package com.ouroboros.cache;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.LongSupplier;
import org.springframework.cache.support.NullValue;

/** Local atomic operations use the Spring cache's own map, including logical expiry. */
final class ConcurrentMapCacheOperations {
  private static final ScheduledThreadPoolExecutor CLEANER = cleaner();
  private final ConcurrentMap<Object, Object> entries;
  private final LongSupplier clock;

  ConcurrentMapCacheOperations(ConcurrentMap<Object, Object> entries, LongSupplier clock) {
    this.entries = entries;
    this.clock = clock;
  }

  Optional<Object> get(String key) {
    return Optional.ofNullable(value(entries.computeIfPresent(key, (ignored, current) -> live(current))));
  }

  Optional<Object> take(String key) {
    Object current = entries.remove(key);
    Object result = value(live(current));
    cancel(current);
    return Optional.ofNullable(result);
  }

  void put(String key, Object replacement, Duration ttl) {
    Object next = wrap(replacement, ttl);
    entries.compute(key, (ignored, current) -> {
      cancel(current);
      return next;
    });
    schedule(key, next, ttl);
  }

  boolean update(String key, String expected, String replacement, Duration ttl, boolean create) {
    positiveNanos(ttl);
    Objects.requireNonNull(replacement, "replacement");
    Object next = wrap(replacement, ttl);
    var changed = new AtomicBoolean();
    entries.compute(key, (ignored, current) -> {
      Object active = live(current);
      if ((create && active == null) || (!create && active != null && Objects.equals(value(active), expected))) {
        cancel(current);
        changed.set(true);
        return next;
      }
      return active;
    });
    if (changed.get()) {
      schedule(key, next, ttl);
    }
    return changed.get();
  }

  boolean remove(String key, String expected) {
    var changed = new AtomicBoolean();
    entries.computeIfPresent(key, (ignored, current) -> {
      Object active = live(current);
      if (active != null && Objects.equals(value(active), expected)) {
        cancel(active);
        changed.set(true);
        return null;
      }
      return active;
    });
    return changed.get();
  }

  void renew(String key, Duration ttl) {
    positiveNanos(ttl);
    entries.computeIfPresent(key, (ignored, current) -> {
      Object active = live(current);
      if (active == null) {
        return null;
      }
      Object next = wrap(value(active), ttl);
      cancel(active);
      schedule(key, next, ttl);
      return next;
    });
  }

  void clear() {
    entries.keySet().forEach(key -> cancel(entries.remove(key)));
  }

  private Object wrap(Object replacement, Duration ttl) {
    Object stored = replacement == null ? NullValue.INSTANCE : replacement;
    return ttl == null ? stored : new ExpiringValue(stored, clock.getAsLong(), positiveNanos(ttl));
  }

  private Object live(Object current) {
    if (current instanceof ExpiringValue expiring && clock.getAsLong() - expiring.createdAt >= expiring.ttlNanos) {
      expiring.cancel();
      return null;
    }
    return current;
  }

  private static Object value(Object current) {
    Object value = current instanceof ExpiringValue expiring ? expiring.value : current;
    return value == NullValue.INSTANCE ? null : value;
  }

  private static void cancel(Object current) {
    if (current instanceof ExpiringValue expiring) {
      expiring.cancel();
    }
  }

  private void schedule(String key, Object next, Duration ttl) {
    if (next instanceof ExpiringValue expiring) {
      expiring.timer(CLEANER.schedule(() -> entries.remove(key, expiring), positiveNanos(ttl), TimeUnit.NANOSECONDS));
    }
  }

  private static long positiveNanos(Duration ttl) {
    long nanos = Objects.requireNonNull(ttl, "ttl").toNanos();
    if (nanos <= 0) {
      throw new IllegalArgumentException("Cache TTL must be positive");
    }
    return nanos;
  }

  private static ScheduledThreadPoolExecutor cleaner() {
    var executor = new ScheduledThreadPoolExecutor(1, task -> {
      var thread = new Thread(task, "cache-expiry");
      thread.setDaemon(true);
      return thread;
    });
    executor.setRemoveOnCancelPolicy(true);
    return executor;
  }

  private static final class ExpiringValue {
    private final Object value;
    private final long createdAt;
    private final long ttlNanos;
    private ScheduledFuture<?> timer;
    private boolean cancelled;

    private ExpiringValue(Object value, long createdAt, long ttlNanos) {
      this.value = value;
      this.createdAt = createdAt;
      this.ttlNanos = ttlNanos;
    }

    private synchronized void timer(ScheduledFuture<?> next) {
      timer = next;
      if (cancelled) {
        next.cancel(false);
      }
    }

    private synchronized void cancel() {
      cancelled = true;
      if (timer != null) {
        timer.cancel(false);
      }
    }
  }
}
