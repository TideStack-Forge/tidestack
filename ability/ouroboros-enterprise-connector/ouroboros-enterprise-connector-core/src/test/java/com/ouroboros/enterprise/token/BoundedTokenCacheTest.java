package com.ouroboros.enterprise.token;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import com.ouroboros.enterprise.EnterpriseDeadline;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.ouroboros.enterprise.token.BoundedTokenCache.CachedToken;

class BoundedTokenCacheTest {

  private final AtomicLong now = new AtomicLong(1_000L);

  @Test
  void returnsCachedTokenUntilItExpires() {
    BoundedTokenCache<CachedToken> cache = cache(8);
    cache.put("conn-a", new CachedToken("token-a", 2_000L));

    assertThat(cache.get("conn-a").token()).isEqualTo("token-a");

    now.set(2_001L);
    assertThat(cache.get("conn-a")).isNull();
    // 过期条目在读取时被移除，不再占用容量。
    assertThat(cache.size()).isZero();
  }

  @Test
  void keepsPerKeyLocksStableSoCallersCanSerializeRefreshes() {
    BoundedTokenCache<CachedToken> cache = cache(8);

    Object first = cache.lockFor("conn-a");
    Object second = cache.lockFor("conn-a");
    Object other = cache.lockFor("conn-b");

    assertThat(first).isSameAs(second);
    assertThat(first).isNotSameAs(other);
  }

  @Test
  void explicitRemovalDropsValueAndKeepsRefreshSerialized() {
    BoundedTokenCache<CachedToken> cache = cache(8);
    cache.put("conn-a", new CachedToken("token-a", 9_999L));
    Object lock = cache.lockFor("conn-a");

    cache.remove("conn-a");

    assertThat(cache.get("conn-a")).isNull();
    assertThat(cache.lockFor("conn-a")).isSameAs(lock);
  }

  @Test
  void lateRejectionCannotEvictReplacement() {
    BoundedTokenCache<CachedToken> cache = cache(8);
    cache.put("main", new CachedToken("new", 9_999L));
    assertThat(cache.removeIf("main", entry -> entry.token().equals("old"))).isFalse();
    assertThat(cache.get("main").token()).isEqualTo("new");
    assertThat(cache.removeIf("main", entry -> entry.token().equals("new"))).isTrue();
    assertThat(cache.get("main")).isNull();
  }

  @Test
  void concurrentConsumersShareOneLoadAndWaitWithinTheirOwnBudget() throws Exception {
    BoundedTokenCache<CachedToken> cache = cache(8);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var callers = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = callers.submit(() -> cache.getOrLoad("main", EnterpriseDeadline.afterMillis(3000), () -> {
        entered.countDown();
        if (!release.await(2, TimeUnit.SECONDS)) { throw new IllegalStateException("test release missing"); }
        return new CachedToken("shared", 9_999);
      }));
      assertThat(entered.await(1, TimeUnit.SECONDS)).isTrue();
      try {
        assertThatThrownBy(() -> cache.getOrLoad("main", EnterpriseDeadline.afterMillis(40),
            () -> { throw new AssertionError("waiter must not exchange a second token"); }))
            .isInstanceOf(TimeoutException.class);
      } finally {
        release.countDown();
      }
      assertThat(first.get(1, TimeUnit.SECONDS).token()).isEqualTo("shared");
      assertThat(cache.getOrLoad("main", EnterpriseDeadline.afterMillis(1000),
          () -> { throw new AssertionError("cached value must be reused"); }).token()).isEqualTo("shared");
    }
  }

  @Test
  void failedLoadDoesNotPoisonTheNextExchange() throws Exception {
    BoundedTokenCache<CachedToken> cache = cache(8);
    assertThatThrownBy(() -> cache.getOrLoad("main", EnterpriseDeadline.afterMillis(1000),
        () -> { throw new IllegalStateException("exchange failed"); }))
        .isInstanceOf(IllegalStateException.class);
    assertThat(cache.getOrLoad("main", EnterpriseDeadline.afterMillis(1000),
        () -> new CachedToken("recovered", 9_999)).token()).isEqualTo("recovered");
  }

  @Test
  void oversizedCachePrunesExpiredEntriesInsteadOfGrowingForever() {
    BoundedTokenCache<CachedToken> cache = cache(3);
    cache.put("expired", new CachedToken("old", 500L));
    cache.put("live-1", new CachedToken("t1", 9_999L));
    cache.put("live-2", new CachedToken("t2", 9_999L));
    // 第 4 个键触发超限清理：过期条目被移除，仍在有效期内的条目保留。
    cache.put("live-3", new CachedToken("t3", 9_999L));

    assertThat(cache.get("expired")).isNull();
    assertThat(cache.get("live-1").token()).isEqualTo("t1");
    assertThat(cache.get("live-2").token()).isEqualTo("t2");
    assertThat(cache.get("live-3").token()).isEqualTo("t3");
    assertThat(cache.size()).isEqualTo(3);
  }

  @Test
  void oversizedCacheWithNoExpiredEntriesIsClearedRatherThanGrowing() {
    BoundedTokenCache<CachedToken> cache = cache(2);
    cache.put("a", new CachedToken("ta", 9_999L));
    cache.put("b", new CachedToken("tb", 9_999L));
    cache.put("c", new CachedToken("tc", 9_999L));

    // 没有任何条目可回收时宁可整体清空（重新换取 token），也不让键与锁对象无界增长。
    assertThat(cache.size()).isLessThanOrEqualTo(2);
  }

  private BoundedTokenCache<CachedToken> cache(int maxEntries) {
    return new BoundedTokenCache<>(CachedToken::expiresAt, maxEntries, now::get);
  }
}
