package com.ouroboros.upload;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TimerCleanUpSchedulerTest {
  @Test void timerContinuesTriggeringAfterItsFirstTickAndStopsWhenClosed() throws Exception {
    var runs = new AtomicInteger(); var twice = new CountDownLatch(2);
    try (var scheduler = new TimerCleanUpScheduler(Duration.ofMillis(10))) {
      Runnable task = new Runnable() {
        @Override public void run() { runs.incrementAndGet(); twice.countDown(); scheduler.addCleanUpTask("repeat", this, Duration.ZERO); }
      };
      scheduler.addCleanUpTask("repeat", task, Duration.ZERO);
      assertTrue(twice.await(3, TimeUnit.SECONDS)); scheduler.close(); int stopped = runs.get();
      scheduler.runAllCleanUpTask(); assertEquals(stopped, runs.get());
    }
  }
  @Test void keyedSchedulingSupportsDeduplicationJoinUpdateAndRemoval() throws Exception {
    var executed = new CountDownLatch(3); var unexpected = new AtomicInteger();
    try (var scheduler = new TimerCleanUpScheduler()) {
      assertTrue(scheduler.addCleanUpTask("one", executed::countDown, Duration.ofDays(1)));
      assertFalse(scheduler.addCleanUpTask("one", unexpected::incrementAndGet, Duration.ZERO));
      assertTrue(scheduler.joinCleanUpTask("one", executed::countDown));
      assertTrue(scheduler.joinCleanUpTask("one", executed::countDown, Duration.ZERO));
      assertTrue(scheduler.updateCleanUpTask("one", Duration.ZERO));
      assertFalse(scheduler.updateCleanUpTask("missing", Duration.ZERO));
      scheduler.addCleanUpTask("remove", List.of(unexpected::incrementAndGet), Duration.ZERO);
      scheduler.removeCleanUpTask("remove"); scheduler.runCleanUpTask("one");
      assertTrue(executed.await(3, TimeUnit.SECONDS)); assertEquals(0, unexpected.get());
      scheduler.addCleanUpTask("all", unexpected::incrementAndGet, Duration.ZERO); scheduler.removeAllCleanUpTask(); scheduler.trigger();
    }
  }
  @Test void oneFailingTaskDoesNotPreventOtherCleanupTasks() throws Exception {
    var completed = new CountDownLatch(1);
    try (var scheduler = new TimerCleanUpScheduler()) {
      scheduler.addCleanUpTask("failure", () -> { throw new IllegalStateException("injected cleanup failure"); }, Duration.ZERO);
      scheduler.addCleanUpTask("success", completed::countDown, Duration.ZERO); scheduler.runAllCleanUpTask();
      assertTrue(completed.await(3, TimeUnit.SECONDS));
    }
  }
}
