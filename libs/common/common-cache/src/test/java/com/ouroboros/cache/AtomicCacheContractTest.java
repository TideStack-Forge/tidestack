package com.ouroboros.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class AtomicCacheContractTest {
  private static final Duration LEASE = Duration.ofSeconds(30);

  @Test
  void concurrentClaimHasExactlyOneWinner() throws Exception {
    var manager = new MemoryCacheManager();
    var start = new CountDownLatch(1);
    var winners = new AtomicInteger();
    var requests = new ArrayList<CompletableFuture<Void>>();
    try (var workers = java.util.concurrent.Executors.newFixedThreadPool(16)) {
      for (int index = 0; index < 100; index++) {
        String owner = "capture-" + index;
        requests.add(CompletableFuture.runAsync(() -> {
          try {
            start.await();
          } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
          }
          if (manager.putIfAbsent("captures", "target", owner, LEASE)) {
            winners.incrementAndGet();
          }
        }, workers));
      }
      start.countDown();
      CompletableFuture.allOf(requests.toArray(CompletableFuture<?>[]::new)).get(5, java.util.concurrent.TimeUnit.SECONDS);
    }

    assertEquals(1, winners.get());
    assertTrue(manager.get("captures", "target").isPresent());
    assertTrue(manager.capabilities().atomicUpdates());
    assertFalse(manager.capabilities().shared());
  }

  @Test
  void staleUpdateAndReleaseCannotAffectTheNewOwner() {
    var manager = new MemoryCacheManager();
    assertTrue(manager.putIfAbsent("captures", "target", "old", LEASE));
    assertTrue(manager.compareAndSet("captures", "target", "old", "new", LEASE));

    assertFalse(manager.compareAndSet("captures", "target", "old", "late", LEASE));
    assertFalse(manager.evictIfEquals("captures", "target", "old"));
    assertEquals("new", manager.get("captures", "target").orElseThrow());
    assertTrue(manager.evictIfEquals("captures", "target", "new"));
    assertTrue(manager.get("captures", "target").isEmpty());
  }

  @Test
  void expiryIsEnforcedByTheOperationEvenBeforeCleanupRuns() {
    var clock = new AtomicLong(1_000);
    var manager = new MemoryCacheManager(clock::get);
    assertTrue(manager.putIfAbsent("captures", "target", "old", LEASE));
    clock.addAndGet(LEASE.toNanos() + 1);

    assertFalse(manager.compareAndSet("captures", "target", "old", "revived", LEASE));
    assertTrue(manager.get("captures", "target").isEmpty());
    assertTrue(manager.putIfAbsent("captures", "target", "new", LEASE));
  }

  @Test
  void invalidTtlDoesNotCreateOrReplaceAValue() {
    var manager = new MemoryCacheManager();
    assertThrows(IllegalArgumentException.class, () -> manager.putIfAbsent("captures", "target", "owner", Duration.ZERO));
    assertTrue(manager.get("captures", "target").isEmpty());
    assertTrue(manager.putIfAbsent("captures", "target", "owner", LEASE));
    assertThrows(IllegalArgumentException.class, () -> manager.compareAndSet("captures", "target", "owner", "changed", Duration.ofMillis(-1)));
    assertEquals("owner", manager.get("captures", "target").orElseThrow());
  }
}
