package com.ouroboros.logicflow.diagnostic;

import java.util.Objects;

/** A logical TEST_APP slot and the exact process that answered its probe. */
public record FlowDebugTarget(String appName, String profile, String runtimeId, long generation,
                              String helperInstanceId, long helperStartedAtEpochMillis, String appIncarnationId) {
  public FlowDebugTarget {
    for (String value : new String[] {appName, profile, runtimeId, helperInstanceId, appIncarnationId}) {
      if (Objects.requireNonNull(value).isBlank() || value.length() > 256) {
        throw new IllegalArgumentException("Invalid debug target identity");
      }
    }
    if (generation < 0 || helperStartedAtEpochMillis <= 0) {
      throw new IllegalArgumentException("Invalid debug process generation");
    }
  }
}
