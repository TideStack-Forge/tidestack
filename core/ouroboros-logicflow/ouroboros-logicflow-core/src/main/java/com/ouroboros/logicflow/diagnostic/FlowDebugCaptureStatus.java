package com.ouroboros.logicflow.diagnostic;

import java.util.List;

/** Bounded control response travels through the existing helper command channel. */
public record FlowDebugCaptureStatus(String captureId, FlowDebugTarget target, boolean acceptingRuns,
                                     long leaseRemainingMillis, long highestSequence, long earliestAvailableSequence,
                                     long acknowledgedThrough, long queuedBytes, long completedRuns, long rejectedRuns, int activeInvocations,
                                     List<Loss> losses, String transportError) {
  public FlowDebugCaptureStatus { losses = List.copyOf(losses); }
  public record Loss(String runId, long captureSeq, long runSeq, long lastCaptureSeq, long lastRunSeq, String reason) {
    public Loss(String runId, long captureSeq, long runSeq, String reason) {
      this(runId, captureSeq, runSeq, captureSeq, runSeq, reason);
    }
  }
}
