package com.ouroboros.logicflow.debug;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class DebugSnapshotterTest {
  private final DebugSnapshotter snapshotter = new DebugSnapshotter(DebugSnapshotter.Limits.defaults());

  @Test
  void nestedSecretsAreRemovedBeforeValuesLeaveTheCaptureThread() {
    var source = new LinkedHashMap<String, Object>();
    source.put("password", "never-send");
    source.put("nested", List.of(Map.of("access_token", "never-send", "name", "visible")));

    var snapshot = snapshotter.capture(source);

    assertThat(snapshot.value().toString()).doesNotContain("never-send").contains("visible", "redacted");
    assertThat(snapshot.truncated()).isFalse();
  }

  @Test
  void laterMutationCannotRewriteCapturedHistory() {
    var values = new ArrayList<Object>();
    values.add("before");
    var snapshot = snapshotter.capture(values);
    values.set(0, "after");

    assertThat(snapshot.value()).isEqualTo(List.of("before"));
    assertThat(snapshotter.capture(null).value()).isNull();
  }

  @Test
  void cyclesAndHugeStringsHaveExplicitBoundedRepresentations() {
    var values = new ArrayList<Object>();
    values.add(values);
    values.add("x".repeat(20_000));

    var snapshot = snapshotter.capture(values);

    assertThat(snapshot.truncated()).isTrue();
    assertThat(snapshot.reasons()).contains("cycle", "string-limit");
    assertThat(snapshot.value().toString().length()).isLessThan(10_000);
  }

  @Test
  void arbitraryGettersAndStringConversionsAreNeverExecuted() {
    var calls = new AtomicInteger();
    Object unsupported = new Object() {
      public Object getSecret() { calls.incrementAndGet(); throw new AssertionError(); }
      @Override public String toString() { calls.incrementAndGet(); throw new AssertionError(); }
    };

    var snapshot = snapshotter.capture(unsupported);

    assertThat(calls.get()).isZero();
    assertThat(snapshot.value().toString()).contains("unsupported");
  }

  @Test
  void customCollectionsCannotExecuteApplicationCodeDuringCapture() {
    var calls = new AtomicInteger();
    var values = new ArrayList<Object>() {
      @Override public java.util.Iterator<Object> iterator() { calls.incrementAndGet(); throw new AssertionError(); }
    };

    assertThat(snapshotter.capture(values).value().toString()).contains("unsupported");
    assertThat(calls.get()).isZero();
  }

  @Test
  void totalTraversalBudgetBoundsWideNestedGraphs() {
    var limits = new DebugSnapshotter.Limits(3, 2, 20, 8, 40, 2, 2, 1);
    var bounded = new DebugSnapshotter(limits);

    var snapshot = bounded.capture(List.of(List.of("a".repeat(80), "b".repeat(80)), List.of(List.of(List.of("deep"))), "extra"));

    assertThat(snapshot.truncated()).isTrue();
    assertThat(snapshot.value().toString().length()).isLessThan(300);
  }

  @Test
  void exceptionsKeepFramesAndBoundCauseAndSuppressedCycles() {
    var root = new IllegalStateException("root");
    var cause = new IllegalArgumentException("cause");
    root.initCause(cause);
    cause.initCause(root);
    root.addSuppressed(cause);

    var snapshot = snapshotter.captureException(root);

    assertThat(snapshot.value().toString()).contains("IllegalStateException", "IllegalArgumentException", "frames", "cycle");
    assertThat(snapshot.truncated()).isTrue();
  }
}
