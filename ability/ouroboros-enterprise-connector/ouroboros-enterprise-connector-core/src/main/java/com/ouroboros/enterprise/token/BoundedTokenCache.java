package com.ouroboros.enterprise.token;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;

import com.ouroboros.enterprise.EnterpriseDeadline;

/**
 * 企业应用 token 缓存：有界、惰性淘汰和有期限的刷新合并。
 *
 * <p>各渠道的 token 缓存原本是"只增不减"的 {@code ConcurrentHashMap}（含每键一个锁对象）：
 * 凭据轮换或连接删除后旧条目永远驻留，且键里带连接标识，长期运行会持续增长。这里提供统一的
 * 有界实现，各渠道只负责给出条目的过期时刻。
 *
 * <p>缓存由供应商 Bean 在本进程的各能力间共享，不承接跨节点协调。
 *
 * @param <V> 条目类型
 */
public final class BoundedTokenCache<V> {

  /**
   * 各渠道共用的缓存条目：token 值与过期时刻。放在公共模块避免三个渠道各写一份同构 record。
   */
  public record CachedToken(String token, long expiresAt) {
    @Override
    public String toString() { return "CachedToken[token=****, expiresAt=" + expiresAt + "]"; }
  }

  /** 以 {@link CachedToken} 为条目的缓存，过期判定统一取 {@code expiresAt}。 */
  public static BoundedTokenCache<CachedToken> forTokens() {
    return new BoundedTokenCache<>(CachedToken::expiresAt, DEFAULT_MAX_ENTRIES,
        System::currentTimeMillis);
  }

  /** 上限按连接/凭据组合估算：单实例的连接数远小于该值，触顶即说明有凭据泄漏或异常轮换。 */
  private static final int DEFAULT_MAX_ENTRIES = 512;

  private final Map<String, V> values = new ConcurrentHashMap<>();
  private final ReentrantLock[] locks = new ReentrantLock[64];
  private final ExpiryOf<V> expiryOf;
  private final LongSupplier clock;
  private final int maxEntries;

  BoundedTokenCache(ExpiryOf<V> expiryOf, int maxEntries, LongSupplier clock) {
    this.expiryOf = expiryOf;
    this.maxEntries = Math.max(maxEntries, 1);
    this.clock = clock;
    for (int index = 0; index < locks.length; index++) {
      locks[index] = new ReentrantLock();
    }
  }

  /** 读取未过期条目；已过期则移除并返回 null。 */
  public V get(String key) {
    V value = values.get(key);
    if (value == null) {
      return null;
    }
    if (expiryOf.expiresAt(value) <= clock.getAsLong()) {
      values.remove(key, value);
      return null;
    }
    return value;
  }

  public void put(String key, V value) {
    values.put(key, value);
    evictIfOversized();
  }

  /** 固定条带锁不随失败的换取或删除增长，也不会在刷新期间被更换。 */
  public ReentrantLock lockFor(String key) {
    return locks[Math.floorMod(key.hashCode(), locks.length)];
  }

  /** 显式移除（凭据轮换、连接删除）。 */
  public void remove(String key) {
    values.remove(key);
  }

  /** Bounded lock waiting and one exchange per cache partition. Failed loads are never cached. */
  public V getOrLoad(String key, EnterpriseDeadline deadline, Callable<V> loader) throws Exception {
    var lock = lockFor(key);
    try {
      if (!lock.tryLock(deadline.remainingNanos(), TimeUnit.NANOSECONDS)) {
        throw new TimeoutException("Enterprise token refresh deadline exceeded");
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw exception;
    }
    try {
      deadline.remainingNanos();
      V cached = get(key);
      if (cached != null) { return cached; }
      V loaded = loader.call();
      deadline.remainingNanos();
      put(key, loaded);
      return loaded;
    } finally {
      lock.unlock();
    }
  }

  public boolean removeIf(String key, java.util.function.Predicate<V> rejected) {
    V current = values.get(key);
    return current != null && rejected.test(current) && values.remove(key, current);
  }

  int size() {
    return values.size();
  }

  /** 超限时清理已过期条目；仍超限则清空，宁可重新换取也不让进程无界增长。 */
  private void evictIfOversized() {
    if (values.size() <= maxEntries) {
      return;
    }
    long now = clock.getAsLong();
    values.entrySet().removeIf(entry -> expiryOf.expiresAt(entry.getValue()) <= now);
    if (values.size() > maxEntries) {
      values.clear();
    }
  }

  /** 判定条目的过期时刻（epoch millis）。 */
  @FunctionalInterface
  public interface ExpiryOf<V> {
    long expiresAt(V value);
  }
}
